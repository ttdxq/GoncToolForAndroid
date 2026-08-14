package cyou.ttdxq.gonctool.android.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import gobridge.DiagnosticsListener
import gobridge.Gobridge
import java.net.Inet4Address
import java.net.Inet6Address
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class DiagnosticsRunStatus {
    IDLE,
    RUNNING,
    CANCELLING,
    COMPLETE,
    PARTIAL,
    FAILED,
    CANCELLED,
    BLOCKED,
}

data class NetworkEnvironment(
    val transport: String,
    val hasIPv4: Boolean,
    val hasIPv6: Boolean,
    val validated: Boolean,
)

data class StunDiagnosticResult(
    val server: String,
    val network: String,
    val localAddress: String,
    val publicAddress: String,
    val elapsedMillis: Long,
    val success: Boolean,
    val errorCode: String,
    val errorMessage: String,
)

data class NatDiagnosticResult(
    val network: String,
    val natType: String,
    val localAddress: String,
    val publicAddress: String,
)

data class MqttDiagnosticResult(
    val server: String,
    val elapsedMillis: Long,
    val success: Boolean,
    val errorCode: String,
    val errorMessage: String,
)

data class LanDiagnosticResult(
    val peerFound: Boolean,
    val localAddress: String,
    val peerAddress: String,
    val transport: String,
    val errorCode: String,
    val errorMessage: String,
)

data class NetworkDiagnosticsState(
    val runId: Long = 0,
    val status: DiagnosticsRunStatus = DiagnosticsRunStatus.IDLE,
    val environment: NetworkEnvironment? = null,
    val phases: Map<String, String> = emptyMap(),
    val stunResults: List<StunDiagnosticResult> = emptyList(),
    val natResults: List<NatDiagnosticResult> = emptyList(),
    val mqttResults: List<MqttDiagnosticResult> = emptyList(),
    val lanResult: LanDiagnosticResult? = null,
    val succeeded: Long = 0,
    val total: Long = 0,
    val messageCode: String? = null,
)

object NetworkDiagnosticsRepository {
    private const val DIAGNOSTIC_TIMEOUT_MILLIS = 6_000L
    private val runIds = AtomicLong(0)
    private val _state = MutableStateFlow(NetworkDiagnosticsState())
    val state: StateFlow<NetworkDiagnosticsState> = _state.asStateFlow()

    private var listener: DiagnosticsListener? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    fun start(
        context: Context,
        stunServers: String,
        mqttServers: String,
        lanSecret: String,
    ) {
        val appContext = context.applicationContext
        val runId: Long
        val callback: DiagnosticsListener
        val effectiveLanSecret: String
        val networkMode: String
        synchronized(this) {
            if (_state.value.status == DiagnosticsRunStatus.RUNNING ||
                _state.value.status == DiagnosticsRunStatus.CANCELLING
            ) {
                return
            }
            if (VpnState.status.value != VpnStatus.DISCONNECTED) {
                _state.value = NetworkDiagnosticsState(
                    runId = runIds.incrementAndGet(),
                    status = DiagnosticsRunStatus.BLOCKED,
                    messageCode = "VPN_ACTIVE",
                )
                return
            }

            val environment = readNetworkEnvironment(appContext)
            runId = runIds.incrementAndGet()
            if (environment == null) {
                _state.value = NetworkDiagnosticsState(
                    runId = runId,
                    status = DiagnosticsRunStatus.FAILED,
                    messageCode = "NO_NETWORK",
                )
                return
            }

            releaseMulticastLock()
            if (environment.transport == "wifi") {
                multicastLock = runCatching {
                    val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                    wifiManager.createMulticastLock("gonctool-network-diagnostics").apply {
                        setReferenceCounted(false)
                        acquire()
                    }
                }.getOrNull()
            }

            _state.value = NetworkDiagnosticsState(
                runId = runId,
                status = DiagnosticsRunStatus.RUNNING,
                environment = environment,
            )
            callback = createListener(runId)
            listener = callback
            effectiveLanSecret = if (environment.transport == "wifi" || environment.transport == "ethernet") {
                lanSecret
            } else {
                ""
            }
            networkMode = when {
                environment.hasIPv4 && environment.hasIPv6 -> "any"
                environment.hasIPv6 -> "any6"
                else -> "any4"
            }
        }
        runCatching {
            Gobridge.startDiagnostics(
                stunServers,
                mqttServers,
                effectiveLanSecret,
                networkMode,
                DIAGNOSTIC_TIMEOUT_MILLIS,
                callback,
            )
        }.onFailure {
            finishLocally(runId, "START_FAILED")
        }
    }

    fun stop() {
        synchronized(this) {
            if (_state.value.status != DiagnosticsRunStatus.RUNNING) {
                return
            }
            _state.value = _state.value.copy(status = DiagnosticsRunStatus.CANCELLING)
        }
        runCatching { Gobridge.stopDiagnostics() }
            .onFailure { finishLocally(_state.value.runId, "CANCEL_FAILED") }
    }

    private fun createListener(runId: Long) = object : DiagnosticsListener {
        override fun diagnosticPhase(name: String?, status: String?) {
            update(runId) { current ->
                when (status) {
                    "vpn_active" -> current.copy(
                        status = DiagnosticsRunStatus.BLOCKED,
                        messageCode = "VPN_ACTIVE",
                        phases = current.phases + (name.orEmpty() to status),
                    )
                    "failed" -> current.copy(
                        messageCode = if (name == "setup") "START_FAILED" else current.messageCode,
                        phases = current.phases + (name.orEmpty() to status),
                    )
                    else -> current.copy(phases = current.phases + (name.orEmpty() to status.orEmpty()))
                }
            }
        }

        override fun stunResult(
            server: String?,
            network: String?,
            localAddress: String?,
            publicAddress: String?,
            elapsedMillis: Long,
            success: Boolean,
            errorCode: String?,
            errorMessage: String?,
        ) {
            val result = StunDiagnosticResult(
                server.orEmpty(),
                network.orEmpty(),
                localAddress.orEmpty(),
                publicAddress.orEmpty(),
                elapsedMillis,
                success,
                errorCode.orEmpty(),
                errorMessage.orEmpty(),
            )
            update(runId) { current -> current.copy(stunResults = current.stunResults + result) }
        }

        override fun natResult(
            network: String?,
            natType: String?,
            localAddress: String?,
            publicAddress: String?,
        ) {
            val result = NatDiagnosticResult(
                network.orEmpty(),
                natType.orEmpty(),
                localAddress.orEmpty(),
                publicAddress.orEmpty(),
            )
            update(runId) { current ->
                val withoutSameNetwork = current.natResults.filterNot { it.network == result.network }
                current.copy(natResults = withoutSameNetwork + result)
            }
        }

        override fun mqttResult(
            server: String?,
            elapsedMillis: Long,
            success: Boolean,
            errorCode: String?,
            errorMessage: String?,
        ) {
            val result = MqttDiagnosticResult(
                server.orEmpty(),
                elapsedMillis,
                success,
                errorCode.orEmpty(),
                errorMessage.orEmpty(),
            )
            update(runId) { current -> current.copy(mqttResults = current.mqttResults + result) }
        }

        override fun lanResult(
            peerFound: Boolean,
            localAddress: String?,
            peerAddress: String?,
            transport: String?,
            errorCode: String?,
            errorMessage: String?,
        ) {
            val result = LanDiagnosticResult(
                peerFound,
                localAddress.orEmpty(),
                peerAddress.orEmpty(),
                transport.orEmpty(),
                errorCode.orEmpty(),
                errorMessage.orEmpty(),
            )
            update(runId) { current -> current.copy(lanResult = result) }
        }

        override fun diagnosticsFinished(cancelled: Boolean, succeeded: Long, total: Long) {
            update(runId) { current ->
                val status = if (current.status == DiagnosticsRunStatus.BLOCKED) {
                    DiagnosticsRunStatus.BLOCKED
                } else {
                    diagnosticsCompletionStatus(cancelled, succeeded, total)
                }
                current.copy(status = status, succeeded = succeeded, total = total)
            }
            clearRunResources(runId)
        }
    }

    @Synchronized
    private fun update(
        runId: Long,
        transform: (NetworkDiagnosticsState) -> NetworkDiagnosticsState,
    ) {
        if (_state.value.runId != runId) {
            return
        }
        _state.value = transform(_state.value)
    }

    @Synchronized
    private fun finishLocally(runId: Long, messageCode: String) {
        if (_state.value.runId != runId) {
            return
        }
        _state.value = _state.value.copy(
            status = DiagnosticsRunStatus.FAILED,
            messageCode = messageCode,
        )
        clearRunResources(runId)
    }

    @Synchronized
    private fun clearRunResources(runId: Long) {
        if (_state.value.runId != runId) {
            return
        }
        listener = null
        releaseMulticastLock()
    }

    private fun releaseMulticastLock() {
        multicastLock?.let { lock ->
            if (lock.isHeld) {
                runCatching { lock.release() }
            }
        }
        multicastLock = null
    }

    private fun readNetworkEnvironment(context: Context): NetworkEnvironment? {
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
        val network = connectivityManager.activeNetwork ?: return null
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return null
        val linkProperties = connectivityManager.getLinkProperties(network) ?: return null
        val transport = when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            else -> "other"
        }
        return NetworkEnvironment(
            transport = transport,
            hasIPv4 = linkProperties.linkAddresses.any { it.address is Inet4Address },
            hasIPv6 = linkProperties.linkAddresses.any { it.address is Inet6Address && !it.address.isLinkLocalAddress },
            validated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
        )
    }
}

internal fun diagnosticsCompletionStatus(
    cancelled: Boolean,
    succeeded: Long,
    total: Long,
): DiagnosticsRunStatus = when {
    cancelled -> DiagnosticsRunStatus.CANCELLED
    total > 0 && succeeded == total -> DiagnosticsRunStatus.COMPLETE
    succeeded > 0 -> DiagnosticsRunStatus.PARTIAL
    else -> DiagnosticsRunStatus.FAILED
}
