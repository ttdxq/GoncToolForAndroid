package cyou.ttdxq.gonctool.android.core

import android.app.Service.STOP_FOREGROUND_REMOVE
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.IpPrefix
import android.net.wifi.WifiManager
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import cyou.ttdxq.gonctool.android.R
import cyou.ttdxq.gonctool.android.BuildConfig
import cyou.ttdxq.gonctool.android.ui.MainActivity
import cyou.ttdxq.gonctool.android.data.LogRepository
import cyou.ttdxq.gonctool.android.data.LogLevel
import cyou.ttdxq.gonctool.android.data.P2PConnectionReport
import cyou.ttdxq.gonctool.android.data.VpnStatus
import cyou.ttdxq.gonctool.android.data.VpnState
import cyou.ttdxq.gonctool.android.data.VpnTraffic
import cyou.ttdxq.gonctool.android.util.normalizeIpAddressInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import gobridge.Gobridge
import gobridge.Logger
import gobridge.TunnelListener
import java.net.InetAddress

class GoncToolVpnService : VpnService() {
    companion object {
        const val ACTION_START = "cyou.ttdxq.gonctool.android.START"
        const val ACTION_STOP = "cyou.ttdxq.gonctool.android.STOP"
        const val EXTRA_P2P_SECRET = "p2p_secret"
        const val EXTRA_ROUTE_CIDRS = "route_cidrs" // Newline separated
        const val EXTRA_SPLIT_TUNNEL_MODE = "split_tunnel_mode"
        const val EXTRA_SPLIT_TUNNEL_APPS = "split_tunnel_apps"
        const val EXTRA_USE_CUSTOM_DNS = "use_custom_dns"
        const val EXTRA_CUSTOM_DNS_ADDRESS = "custom_dns_address"
        const val EXTRA_DNS_THROUGH_TUNNEL = "dns_through_tunnel"
        const val EXTRA_LINK_GONC_DNS = "link_gonc_dns"
        const val EXTRA_CUSTOM_STUN_SERVERS = "custom_stun_servers"
        const val EXTRA_CUSTOM_MQTT_SERVERS = "custom_mqtt_servers"
        const val EXTRA_EXPERT_MODE_ENABLED = "expert_mode_enabled"
        const val EXTRA_EXPERT_MODE_RAW_ARGS = "expert_mode_raw_args"
        const val EXTRA_KCP_ENABLED = "kcp_enabled"
        const val EXTRA_AUTO_P2P_LAN = "auto_p2p_lan"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "gonctool_vpn_channel"
        private const val TAG = "GoncToolVpnService"
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private val interfaceLock = Any()
    private val multicastLockGuard = Any()
    private var vpnInterface: ParcelFileDescriptor? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    @Volatile private var goncThread: Thread? = null
    @Volatile private var stopInProgress = false
    @Volatile private var tunnelReady = false
    @Volatile private var tunnelActivationInProgress = false
    @Volatile private var currentProxyEndpoint = ""
    @Volatile private var dnsActivatedAfterTunnel = false
    @Volatile private var currentRouteCidrs: String = ""
    @Volatile private var currentCustomDnsAddress: String? = null
    @Volatile private var currentDnsThroughTunnel: Boolean = true
    @Volatile private var currentSplitTunnelMode: String = "all"
    @Volatile private var currentSplitTunnelApps: List<String> = emptyList()

    private fun takeVpnInterface(): ParcelFileDescriptor? = synchronized(interfaceLock) {
        vpnInterface.also { vpnInterface = null }
    }

    private fun closeVpnInterface() {
        takeVpnInterface()?.let { descriptor ->
            runCatching { descriptor.close() }
                .onFailure { LogRepository.error(TAG, "Error closing VPN interface", it) }
        }
    }

    /**
     * tun2socks owns and closes the fd passed through fd://. Give it a detached
     * duplicate so the Android VpnService descriptor remains independently
     * owned by this service and can be closed exactly once.
     */
    private fun duplicateTunFd(): Long? = synchronized(interfaceLock) {
        val descriptor = vpnInterface ?: return@synchronized null
        runCatching {
            ParcelFileDescriptor.dup(descriptor.fileDescriptor).detachFd().toLong()
        }.onFailure {
            LogRepository.error(TAG, "Unable to duplicate VPN interface fd", it)
        }.getOrNull()
    }

    private fun acquireMulticastLock() {
        synchronized(multicastLockGuard) {
            if (multicastLock?.isHeld == true) return
            runCatching {
                val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                wifiManager.createMulticastLock("gonctool-p2p-lan-race").apply {
                    setReferenceCounted(false)
                    acquire()
                }
            }.onSuccess { lock ->
                multicastLock = lock
                LogRepository.info(TAG, "LAN discovery multicast lock acquired")
            }.onFailure { error ->
                LogRepository.warn(TAG, "Unable to acquire LAN discovery multicast lock: ${error.message}")
            }
        }
    }

    private fun releaseMulticastLock() {
        synchronized(multicastLockGuard) {
            multicastLock?.let { lock ->
                runCatching {
                    if (lock.isHeld) lock.release()
                }.onFailure { error ->
                    LogRepository.warn(TAG, "Unable to release LAN discovery multicast lock: ${error.message}")
                }
            }
            multicastLock = null
        }
    }
    
    // JNI Logger implementation
    private val goLogger = object : Logger {
        override fun log(message: String?) {
            message?.let { LogRepository.log("GoJNI", it) }
        }
    }

    private val tunnelListener = object : TunnelListener {
        override fun event(level: String?, message: String?) {
            message?.trim()?.takeIf { it.isNotEmpty() }?.let { LogRepository.log("Gonc", it) }
        }

        override fun p2PReport(
            topic: String?,
            side: String?,
            status: String?,
            network: String?,
            mode: String?,
            peer: String?,
            timestamp: Long,
            pid: Long,
            reasonCode: String?,
            reasonMessage: String?,
        ) {
            VpnState.setConnectionReport(
                P2PConnectionReport(
                    status = status.orEmpty(),
                    network = network.orEmpty(),
                    mode = mode.orEmpty(),
                    peer = peer.orEmpty(),
                    reasonCode = reasonCode.orEmpty(),
                    reasonMessage = reasonMessage.orEmpty(),
                    timestamp = timestamp,
                )
            )
        }

        override fun traffic(
            side: String?,
            inBytes: Long,
            outBytes: Long,
            inBps: Double,
            outBps: Double,
            elapsed: Long,
            connCount: Long,
            final: Boolean,
        ) {
            VpnState.setTraffic(VpnTraffic(inBytes, outBytes, inBps, outBps, connCount))
        }

        override fun ready(endpoint: String?) {
            val proxyEndpoint = endpoint?.takeIf { it.isNotBlank() } ?: return
            serviceScope.launch(Dispatchers.IO) { activateTunnel(proxyEndpoint) }
        }

        override fun stopped(exitCode: Long) {
            LogRepository.info(TAG, "Gonc session stopped with exit code $exitCode")
        }

        override fun error(message: String?) {
            if (!stopInProgress) {
                val errorMessage = message?.takeIf { it.isNotBlank() } ?: "Gonc tunnel failed"
                VpnState.setError(errorMessage)
                updateNotification("Error: tunnel setup failed")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Gobridge.setLogger(goLogger)
        VpnState.setStatus(VpnStatus.DISCONNECTED)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        LogRepository.info(TAG, "onStartCommand received action: ${intent?.action}")
        when (intent?.action) {
            ACTION_START -> {
                startForeground(NOTIFICATION_ID, createNotification("Starting..."))
                val secret = intent.getStringExtra(EXTRA_P2P_SECRET) ?: ""
                val cidrs = intent.getStringExtra(EXTRA_ROUTE_CIDRS) ?: ""
                val splitTunnelMode = intent.getStringExtra(EXTRA_SPLIT_TUNNEL_MODE) ?: "all"
                val splitTunnelApps = intent.getStringExtra(EXTRA_SPLIT_TUNNEL_APPS) ?: "[]"
                val useCustomDns = intent.getBooleanExtra(EXTRA_USE_CUSTOM_DNS, false)
                val customDnsAddress = intent.getStringExtra(EXTRA_CUSTOM_DNS_ADDRESS) ?: ""
                val dnsThroughTunnel = intent.getBooleanExtra(EXTRA_DNS_THROUGH_TUNNEL, true)
                val linkGoncDns = intent.getBooleanExtra(EXTRA_LINK_GONC_DNS, false)
                val customStunServers = intent.getStringExtra(EXTRA_CUSTOM_STUN_SERVERS) ?: ""
                val customMqttServers = intent.getStringExtra(EXTRA_CUSTOM_MQTT_SERVERS) ?: ""
                val expertModeEnabled = intent.getBooleanExtra(EXTRA_EXPERT_MODE_ENABLED, false)
                val expertModeRawArgs = intent.getStringExtra(EXTRA_EXPERT_MODE_RAW_ARGS) ?: ""
                val kcpEnabled = intent.getBooleanExtra(EXTRA_KCP_ENABLED, false)
                val autoP2pLan = intent.getBooleanExtra(EXTRA_AUTO_P2P_LAN, true)
                startVpn(secret, cidrs, splitTunnelMode, splitTunnelApps, useCustomDns, customDnsAddress, dnsThroughTunnel, linkGoncDns, customStunServers, customMqttServers, expertModeEnabled, expertModeRawArgs, kcpEnabled, autoP2pLan)
            }
            ACTION_STOP -> stopVpn()
            else -> {
                // Service被系统重启，恢复断开状态
                VpnState.setStatus(VpnStatus.DISCONNECTED)
            }
        }
        return START_STICKY
    }

    private fun startVpn(
        secret: String,
        cidrs: String,
        splitTunnelMode: String,
        splitTunnelApps: String,
        useCustomDns: Boolean,
        customDnsAddress: String,
        dnsThroughTunnel: Boolean,
        linkGoncDns: Boolean,
        customStunServers: String,
        customMqttServers: String,
        expertModeEnabled: Boolean,
        expertModeRawArgs: String,
        kcpEnabled: Boolean,
        autoP2pLan: Boolean,
    ) {
        if (secret.isBlank()) {
            VpnState.setError("P2P Secret不能为空")
            stopSelf()
            return
        }

        val normalizedDns = normalizeIpAddressInput(customDnsAddress)
        val normalizedStunServers = customStunServers.trim()
        val normalizedMqttServers = customMqttServers.trim()
        if (useCustomDns && normalizedDns == null) {
            VpnState.setError("DNS 地址无效")
            stopSelf()
            return
        }

        if (useCustomDns && !dnsThroughTunnel && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            VpnState.setError("Android 13 以下系统无法可靠地实现 DNS 绕过隧道")
            stopSelf()
            return
        }

        if (goncThread?.isAlive == true || stopInProgress) {
            LogRepository.warn(TAG, "VPN start ignored because another session is still active")
            return
        }

        val selectedDns = if (useCustomDns) normalizedDns else null
        tunnelReady = false
        tunnelActivationInProgress = false
        currentProxyEndpoint = ""
        dnsActivatedAfterTunnel = false
        currentRouteCidrs = cidrs
        currentCustomDnsAddress = selectedDns
        currentDnsThroughTunnel = dnsThroughTunnel
        currentSplitTunnelMode = splitTunnelMode
        currentSplitTunnelApps = parseSplitTunnelApps(splitTunnelApps)
        VpnState.resetConnectionDiagnostics()
        VpnState.setStatus(VpnStatus.CONNECTING)

        serviceScope.launch(Dispatchers.IO) {
            try {
                if (autoP2pLan) {
                    acquireMulticastLock()
                }
                val extraArgs = buildString {
                    if (autoP2pLan) {
                        append("-p2p-with-lan")
                    }
                    if (kcpEnabled) {
                        if (isNotEmpty()) append(' ')
                        append("-kcp")
                    }
                    if (selectedDns != null && linkGoncDns) {
                        if (isNotEmpty()) append(' ')
                        append("-dns ")
                        append(selectedDns)
                    }
                    if (normalizedStunServers.isNotBlank()) {
                        if (isNotEmpty()) append(' ')
                        append("-stunsrv ")
                        append('"')
                        append(normalizedStunServers)
                        append('"')
                    }
                    if (normalizedMqttServers.isNotBlank()) {
                        if (isNotEmpty()) append(' ')
                        append("-mqttsrv ")
                        append('"')
                        append(normalizedMqttServers)
                        append('"')
                    }
                    if (expertModeEnabled && expertModeRawArgs.isNotBlank()) {
                        if (isNotEmpty()) append(' ')
                        append(expertModeRawArgs.trim())
                    }
                }
                val worker = Thread {
                    LogRepository.info(TAG, "Starting Gonc...")
                    try {
                        Gobridge.startP2PTunnel(secret, false, "1080;none", extraArgs, tunnelListener)
                    } catch (e: Exception) {
                        LogRepository.error(TAG, "Gonc exited with error: ${e.message}")
                    }
                    LogRepository.info(TAG, "Gonc thread exited")
                    releaseMulticastLock()
                    val wasReady = tunnelReady
                    if (goncThread === Thread.currentThread()) {
                        goncThread = null
                    }
                    if (!stopInProgress) {
                        serviceScope.launch(Dispatchers.Main) {
                            if (!stopInProgress && VpnState.status.value != VpnStatus.ERROR) {
                                if (wasReady) {
                                    VpnState.setError("Gonc tunnel disconnected")
                                    updateNotification("Error: Gonc tunnel disconnected")
                                } else {
                                    VpnState.setError("Gonc failed before tunnel establishment")
                                    updateNotification("Error: tunnel setup failed")
                                }
                            }
                        }
                    }
                }
                goncThread = worker
                worker.start()

                updateNotification("Connecting...")
            } catch (e: Exception) {
                releaseMulticastLock()
                val errorMsg = "启动VPN失败: ${e.message}"
                LogRepository.error(TAG, "FATAL: $errorMsg")
                VpnState.setError(errorMsg)
                stopVpn()
            }
        }
    }

    /**
     * 更新通知的运行状态文本
     */
    private fun updateStatusNotification() {
        val status = when (VpnState.status.value) {
            VpnStatus.CONNECTING -> "Connecting..."
            VpnStatus.STOPPING -> "Stopping..."
            VpnStatus.CONNECTED -> "Connected"
            VpnStatus.ERROR -> "Error: ${VpnState.errorMessage.value}"
            VpnStatus.DISCONNECTED -> "Disconnected"
        }
        updateNotification(status)
    }

    private fun stopVpn() {
        synchronized(this) {
            if (stopInProgress) {
                LogRepository.warn(TAG, "Stop already in progress")
                return
            }
            stopInProgress = true
            tunnelReady = false
            tunnelActivationInProgress = false
            dnsActivatedAfterTunnel = false
            VpnState.setStatus(VpnStatus.STOPPING)
        }

        serviceScope.launch(Dispatchers.IO) {
            try {
                // Stop the fd consumer first so cancellation does not wait on
                // a goroutine blocked in the TUN read path.
                runCatching { Gobridge.stopTun2Socks() }
                    .onFailure { LogRepository.error(TAG, "Error stopping tun2socks", it) }
                closeVpnInterface()

                try {
                    updateNotification("Stopping...")
                    Gobridge.stopGonc()
                } catch (e: Exception) {
                    LogRepository.error(TAG, "Error stopping gonc", e)
                }

                try {
                    goncThread?.join(1500)
                } catch (e: Exception) {
                    LogRepository.error(TAG, "Error waiting for gonc thread", e)
                }
                goncThread = null
                currentProxyEndpoint = ""
                
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                
                // 延迟更新状态，确保通知消失后再更新
                VpnState.setStatus(VpnStatus.DISCONNECTED)
            } finally {
                releaseMulticastLock()
                stopInProgress = false
            }
        }
    }

    private fun addRouteForAddress(builder: Builder, address: String) {
        builder.addRoute(address, if (address.contains(":")) 128 else 32)
    }

    private fun createVpnBuilder(enableDns: Boolean): Builder {
        val builder = Builder()
            .addAddress("10.0.0.2", 32)
            .addAddress("fd00::2", 128)
            .setMtu(1400)
            .setSession(getString(R.string.vpn_session_name))

        applySplitTunnelConfiguration(builder)

        currentCustomDnsAddress?.let { dnsAddress ->
            if (enableDns) {
                builder.addDnsServer(dnsAddress)
                if (currentDnsThroughTunnel) {
                    addRouteForAddress(builder, dnsAddress)
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    builder.excludeRoute(
                        IpPrefix(
                            InetAddress.getByName(dnsAddress),
                            if (dnsAddress.contains(":")) 128 else 32
                        )
                    )
                }
                LogRepository.info(TAG, "Custom DNS enabled: $dnsAddress (throughTunnel=$currentDnsThroughTunnel)")
            } else if (currentDnsThroughTunnel) {
                LogRepository.info(TAG, "Delay custom DNS activation until tunnel is ready: $dnsAddress")
            }
        }

        currentRouteCidrs.lines().filter { it.isNotBlank() }.forEach { cidr ->
            try {
                val parts = cidr.trim().split("/")
                val address = parts[0]
                val prefixLength = if (parts.size > 1) {
                    parts[1].toInt()
                } else {
                    if (address.contains(":")) 128 else 32
                }
                builder.addRoute(address, prefixLength)
            } catch (e: Exception) {
                val errorMsg = "Invalid route: $cidr"
                LogRepository.error(TAG, errorMsg, e)
                VpnState.setError(errorMsg)
                throw e
            }
        }

        return builder
    }

    private fun applySplitTunnelConfiguration(builder: Builder) {
        when (currentSplitTunnelMode) {
            "allow" -> {
                val allowedPackages = currentSplitTunnelApps
                    .filter { it.isNotBlank() && it != packageName }
                    .distinct()
                if (allowedPackages.isEmpty()) {
                    // No apps selected in allow mode → behaves like "all" mode
                    addApplicationSafely(packageName, isAllowed = false) { builder.addDisallowedApplication(it) }
                } else {
                    // Only selected apps go through VPN; our app's traffic stays direct
                    allowedPackages.forEach { pkg ->
                        addApplicationSafely(pkg, isAllowed = true) { builder.addAllowedApplication(it) }
                    }
                }
            }

            "deny" -> {
                addApplicationSafely(packageName, isAllowed = false) { builder.addDisallowedApplication(it) }
                currentSplitTunnelApps
                    .filter { it.isNotBlank() && it != packageName }
                    .distinct()
                    .forEach { pkg ->
                        addApplicationSafely(pkg, isAllowed = false) { builder.addDisallowedApplication(it) }
                    }
            }

            else -> {
                addApplicationSafely(packageName, isAllowed = false) { builder.addDisallowedApplication(it) }
            }
        }
    }

    private fun activateTunnel(proxyEndpoint: String) {
        synchronized(this) {
            if (stopInProgress || tunnelReady || tunnelActivationInProgress) {
                return
            }
            tunnelActivationInProgress = true
        }
        try {
            if (!establishVpnInterface(enableDns = currentCustomDnsAddress != null && !currentDnsThroughTunnel)) {
                throw IllegalStateException("无法建立VPN接口，请检查VPN权限")
            }
            if (stopInProgress) {
                closeVpnInterface()
                return
            }
            val fd = duplicateTunFd() ?: throw IllegalStateException("VPN interface has no file descriptor")
            currentProxyEndpoint = proxyEndpoint
            val tunLogLevel = resolveTun2SocksLogLevel()
            Gobridge.startTun2Socks(fd, proxyEndpoint, "tun0", 1400L, tunLogLevel)
            tunnelReady = true
            activateDnsAfterTunnelReadyIfNeeded()
            updateNotification("Connected")
            VpnState.setStatus(VpnStatus.CONNECTED)
            LogRepository.info(TAG, "VPN tunnel activated with $proxyEndpoint")
        } catch (e: Exception) {
            if (stopInProgress) {
                return
            }
            val message = "启动 VPN 数据通道失败: ${e.message}"
            LogRepository.error(TAG, message)
            VpnState.setError(message)
            updateNotification("Error: tunnel activation failed")
            Gobridge.stopGonc()
        } finally {
            tunnelActivationInProgress = false
        }
    }

    private fun addApplicationSafely(
        packageName: String,
        isAllowed: Boolean,
        action: (String) -> Unit,
    ) {
        try {
            action(packageName)
        } catch (e: PackageManager.NameNotFoundException) {
            LogRepository.warn(
                TAG,
                "Skipping ${if (isAllowed) "allowed" else "disallowed"} package $packageName: ${e.message}"
            )
        }
    }

    private fun parseSplitTunnelApps(splitTunnelApps: String): List<String> {
        return runCatching {
            Gson().fromJson(splitTunnelApps, Array<String>::class.java)
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?.distinct()
                ?: emptyList()
        }.getOrDefault(emptyList())
    }

    private fun establishVpnInterface(enableDns: Boolean): Boolean {
        val builder = createVpnBuilder(enableDns)
        val newInterface = builder.establish() ?: return false
        val oldInterface = synchronized(interfaceLock) {
            vpnInterface.also { vpnInterface = newInterface }
        }
        oldInterface?.close()
        return true
    }

    private fun restartTun2Socks() {
        val fd = duplicateTunFd() ?: return
        val proxyEndpoint = currentProxyEndpoint.takeIf { it.isNotBlank() } ?: return
        try {
            Gobridge.stopTun2Socks()
        } catch (_: Exception) {
        }
        val tunLogLevel = resolveTun2SocksLogLevel()
        Gobridge.startTun2Socks(fd, proxyEndpoint, "tun0", 1400L, tunLogLevel)
        LogRepository.info(TAG, "Tun2Socks started with log level: $tunLogLevel")
    }

    private fun activateDnsAfterTunnelReadyIfNeeded() {
        val dnsAddress = currentCustomDnsAddress ?: return
        if (!currentDnsThroughTunnel || dnsActivatedAfterTunnel || stopInProgress || !tunnelReady) {
            return
        }
        synchronized(this) {
            if (!currentDnsThroughTunnel || dnsActivatedAfterTunnel || stopInProgress || !tunnelReady) {
                return
            }
            if (!establishVpnInterface(enableDns = true)) {
                throw IllegalStateException("无法在隧道建立后重新配置 DNS")
            }
            restartTun2Socks()
            dnsActivatedAfterTunnel = true
            LogRepository.info(TAG, "Custom DNS activated after tunnel ready: $dnsAddress")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        // Ensure cleanup
        try {
            tunnelReady = false
            tunnelActivationInProgress = false
            VpnState.setStatus(VpnStatus.STOPPING)
            // When stopVpn() called stopSelf(), its IO cleanup is already in
            // progress. Repeating the native stop calls here can re-enter the
            // bridge and add several seconds to teardown.
            if (!stopInProgress) {
                Gobridge.stopTun2Socks()
                closeVpnInterface()
                Gobridge.stopGonc()
                goncThread?.join(500)
            } else {
                closeVpnInterface()
            }
            currentProxyEndpoint = ""
        } catch(_: Exception) {
        } finally {
            releaseMulticastLock()
        }
        goncThread = null
        stopInProgress = false
        
        VpnState.setStatus(VpnStatus.DISCONNECTED)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "VPN Status",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(status: String, isError: Boolean = false): android.app.Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(status)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
    
    private fun updateNotification(status: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, createNotification(status))
    }

    private fun resolveTun2SocksLogLevel(): String {
        return if (BuildConfig.DEBUG) {
            "info"
        } else {
            "warn"
        }
    }
}
