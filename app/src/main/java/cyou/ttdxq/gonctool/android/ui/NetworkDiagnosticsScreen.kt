package cyou.ttdxq.gonctool.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cyou.ttdxq.gonctool.android.R
import cyou.ttdxq.gonctool.android.data.DiagnosticsRunStatus
import cyou.ttdxq.gonctool.android.data.MqttDiagnosticResult
import cyou.ttdxq.gonctool.android.data.NetworkDiagnosticsRepository
import cyou.ttdxq.gonctool.android.data.NetworkDiagnosticsState
import cyou.ttdxq.gonctool.android.data.P2PConnectionReport
import cyou.ttdxq.gonctool.android.data.SettingsStore
import cyou.ttdxq.gonctool.android.data.StunDiagnosticResult
import cyou.ttdxq.gonctool.android.data.VpnState
import cyou.ttdxq.gonctool.android.data.VpnStatus
import cyou.ttdxq.gonctool.android.data.VpnTraffic
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkDiagnosticsScreen(
    settingsStore: SettingsStore,
    onMenuClick: () -> Unit,
) {
    val state by NetworkDiagnosticsRepository.state.collectAsState()
    val vpnStatus by VpnState.status.collectAsState()
    val connectionReport by VpnState.connectionReport.collectAsState()
    val traffic by VpnState.traffic.collectAsState()
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val isBusy = state.status == DiagnosticsRunStatus.RUNNING ||
        state.status == DiagnosticsRunStatus.CANCELLING

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.diagnostics_title)) },
                navigationIcon = { AppNavigationMenuButton(onMenuClick) },
                actions = {
                    if (isBusy) {
                        IconButton(
                            onClick = NetworkDiagnosticsRepository::stop,
                            enabled = state.status == DiagnosticsRunStatus.RUNNING,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.diagnostics_cancel),
                            )
                        }
                    } else {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    NetworkDiagnosticsRepository.start(
                                        context = context,
                                        stunServers = settingsStore.customStunServers.first(),
                                        mqttServers = settingsStore.customMqttServers.first(),
                                        lanSecret = settingsStore.p2pSecret.first(),
                                    )
                                }
                            },
                            enabled = vpnStatus == VpnStatus.DISCONNECTED,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = stringResource(R.string.diagnostics_run),
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        DiagnosticsContent(
            state = state,
            vpnStatus = vpnStatus,
            connectionReport = connectionReport,
            traffic = traffic,
            modifier = Modifier.padding(innerPadding),
        )
    }
}

@Composable
private fun DiagnosticsContent(
    state: NetworkDiagnosticsState,
    vpnStatus: VpnStatus,
    connectionReport: P2PConnectionReport?,
    traffic: VpnTraffic?,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        item {
            DiagnosticSummary(state, vpnStatus)
        }

        connectionReport?.let { report ->
            item {
                DiagnosticSectionTitle(stringResource(R.string.diagnostics_current_connection))
                DiagnosticValueRow(
                    label = stringResource(R.string.diagnostics_connection_stage),
                    value = connectionStatusLabel(report.status),
                )
                if (report.mode.isNotBlank()) {
                    DiagnosticValueRow(
                        label = stringResource(R.string.diagnostics_connection_path),
                        value = listOf(report.mode, report.network)
                            .filter { it.isNotBlank() }
                            .joinToString(" · ") { it.uppercase() },
                    )
                }
                if (report.peer.isNotBlank()) {
                    DiagnosticValueRow(
                        label = stringResource(R.string.diagnostics_connection_peer),
                        value = report.peer,
                    )
                }
                if (report.reasonCode.isNotBlank()) {
                    DiagnosticValueRow(
                        label = stringResource(R.string.diagnostics_connection_reason),
                        value = diagnosticErrorLabel(report.reasonCode),
                    )
                }
                traffic?.let { snapshot ->
                    DiagnosticValueRow(
                        label = stringResource(R.string.diagnostics_connection_rate),
                        value = stringResource(
                            R.string.diagnostics_rate_value,
                            formatRate(snapshot.inBps),
                            formatRate(snapshot.outBps),
                        ),
                    )
                    DiagnosticValueRow(
                        label = stringResource(R.string.diagnostics_connection_count),
                        value = snapshot.connectionCount.toString(),
                    )
                }
            }
        }

        state.environment?.let { environment ->
            item {
                DiagnosticSectionTitle(stringResource(R.string.diagnostics_network_environment))
                DiagnosticValueRow(
                    label = stringResource(R.string.diagnostics_transport),
                    value = transportLabel(environment.transport),
                )
                DiagnosticValueRow(
                    label = "IPv4",
                    value = availabilityLabel(environment.hasIPv4),
                )
                DiagnosticValueRow(
                    label = "IPv6",
                    value = availabilityLabel(environment.hasIPv6),
                )
                DiagnosticValueRow(
                    label = stringResource(R.string.diagnostics_internet_validation),
                    value = availabilityLabel(environment.validated),
                )
            }
        }

        if (state.natResults.isNotEmpty()) {
            item { DiagnosticSectionTitle(stringResource(R.string.diagnostics_nat_public)) }
            itemsIndexed(state.natResults) { _, result ->
                DiagnosticEndpointRow(
                    title = result.network.ifBlank { stringResource(R.string.diagnostics_unknown) },
                    subtitle = stringResource(
                        R.string.diagnostics_nat_result,
                        natTypeLabel(result.natType),
                        result.publicAddress,
                    ),
                    success = true,
                )
            }
        }

        if (state.stunResults.isNotEmpty() || state.phases["stun"] == "running") {
            item {
                DiagnosticSectionTitle(
                    stringResource(R.string.diagnostics_stun),
                    state.phases["stun"] == "running",
                )
            }
            itemsIndexed(state.stunResults) { _, result -> StunResultRow(result) }
        }

        if (state.mqttResults.isNotEmpty() || state.phases["mqtt"] == "running") {
            item {
                DiagnosticSectionTitle(
                    stringResource(R.string.diagnostics_mqtt),
                    state.phases["mqtt"] == "running",
                )
            }
            itemsIndexed(state.mqttResults) { _, result -> MqttResultRow(result) }
        }

        if (state.lanResult != null || state.phases["lan"] == "running") {
            item {
                DiagnosticSectionTitle(
                    stringResource(R.string.diagnostics_lan),
                    state.phases["lan"] == "running",
                )
                state.lanResult?.let { result ->
                    DiagnosticEndpointRow(
                        title = if (result.peerFound) {
                            stringResource(R.string.diagnostics_lan_peer_found)
                        } else {
                            stringResource(R.string.diagnostics_lan_peer_not_found)
                        },
                        subtitle = if (result.peerFound) {
                            "${result.transport.uppercase()} · ${result.peerAddress}"
                        } else {
                            diagnosticErrorLabel(result.errorCode)
                        },
                        success = result.peerFound,
                        neutral = result.errorCode == "LAN_PEER_NOT_FOUND",
                    )
                }
            }
        }
    }
}

@Composable
private fun DiagnosticSummary(state: NetworkDiagnosticsState, vpnStatus: VpnStatus) {
    val title = when (state.status) {
        DiagnosticsRunStatus.IDLE -> stringResource(R.string.diagnostics_not_run)
        DiagnosticsRunStatus.RUNNING -> stringResource(R.string.diagnostics_running)
        DiagnosticsRunStatus.CANCELLING -> stringResource(R.string.diagnostics_cancelling)
        DiagnosticsRunStatus.COMPLETE -> stringResource(R.string.diagnostics_complete)
        DiagnosticsRunStatus.PARTIAL -> stringResource(R.string.diagnostics_partial)
        DiagnosticsRunStatus.FAILED -> stringResource(R.string.diagnostics_failed)
        DiagnosticsRunStatus.CANCELLED -> stringResource(R.string.diagnostics_cancelled)
        DiagnosticsRunStatus.BLOCKED -> stringResource(R.string.diagnostics_blocked)
    }
    val subtitle = when {
        vpnStatus != VpnStatus.DISCONNECTED -> stringResource(R.string.diagnostics_vpn_active)
        state.messageCode != null -> diagnosticErrorLabel(state.messageCode)
        state.total > 0 -> stringResource(
            R.string.diagnostics_summary_count,
            state.succeeded,
            state.total,
        )
        else -> null
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        subtitle?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    HorizontalDivider()
}

@Composable
private fun DiagnosticSectionTitle(title: String, loading: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        }
    }
}

@Composable
private fun DiagnosticValueRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(0.4f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier
                .weight(0.6f)
                .padding(start = 12.dp),
        )
    }
}

@Composable
private fun DiagnosticEndpointRow(
    title: String,
    subtitle: String,
    success: Boolean,
    neutral: Boolean = false,
) {
    val color = when {
        neutral -> MaterialTheme.colorScheme.onSurfaceVariant
        success -> getStatusColors().success
        else -> getStatusColors().error
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyMedium, color = color)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StunResultRow(result: StunDiagnosticResult) {
    val subtitle = if (result.success) {
        stringResource(
            R.string.diagnostics_probe_success_detail,
            result.network,
            result.publicAddress,
            result.elapsedMillis,
        )
    } else {
        stringResource(
            R.string.diagnostics_probe_failed_detail,
            result.network,
            diagnosticErrorLabel(result.errorCode),
            result.elapsedMillis,
        )
    }
    DiagnosticEndpointRow(result.server, subtitle, result.success)
}

@Composable
private fun MqttResultRow(result: MqttDiagnosticResult) {
    val subtitle = if (result.success) {
        stringResource(R.string.diagnostics_mqtt_success_detail, result.elapsedMillis)
    } else {
        stringResource(
            R.string.diagnostics_mqtt_failed_detail,
            diagnosticErrorLabel(result.errorCode),
            result.elapsedMillis,
        )
    }
    DiagnosticEndpointRow(result.server, subtitle, result.success)
}

@Composable
private fun availabilityLabel(available: Boolean): String =
    stringResource(if (available) R.string.diagnostics_available else R.string.diagnostics_unavailable)

@Composable
private fun transportLabel(transport: String): String = when (transport) {
    "wifi" -> stringResource(R.string.diagnostics_transport_wifi)
    "cellular" -> stringResource(R.string.diagnostics_transport_cellular)
    "ethernet" -> stringResource(R.string.diagnostics_transport_ethernet)
    else -> stringResource(R.string.diagnostics_transport_other)
}

@Composable
private fun natTypeLabel(type: String): String = when (type.lowercase()) {
    "easy" -> stringResource(R.string.diagnostics_nat_easy)
    "hard" -> stringResource(R.string.diagnostics_nat_hard)
    "symm" -> stringResource(R.string.diagnostics_nat_symmetric)
    else -> stringResource(R.string.diagnostics_unknown)
}

@Composable
private fun diagnosticErrorLabel(code: String): String = when (code) {
    "VPN_ACTIVE" -> stringResource(R.string.diagnostics_vpn_active)
    "NO_NETWORK" -> stringResource(R.string.diagnostics_no_network)
    "TIMEOUT", "STUN_FAILED", "MQTT_FAILED" -> stringResource(R.string.diagnostics_timeout_or_unreachable)
    "DNS_RESOLVE_FAILED" -> stringResource(R.string.diagnostics_dns_failed)
    "TLS_HANDSHAKE_FAILED" -> stringResource(R.string.diagnostics_tls_failed)
    "MQTT_AUTH_FAILED" -> stringResource(R.string.diagnostics_auth_failed)
    "CONNECTION_REFUSED" -> stringResource(R.string.diagnostics_connection_refused)
    "LAN_MULTICAST_UNAVAILABLE" -> stringResource(R.string.diagnostics_lan_unavailable)
    "LAN_PEER_NOT_FOUND" -> stringResource(R.string.diagnostics_lan_peer_not_found)
    "RELAY_SELECTED" -> stringResource(R.string.diagnostics_relay_selected)
    "P2P_SIGNAL_FAILED" -> stringResource(R.string.diagnostics_p2p_signal_failed)
    "P2P_PUNCH_FAILED" -> stringResource(R.string.diagnostics_p2p_punch_failed)
    "RELAY_CONNECT_FAILED" -> stringResource(R.string.diagnostics_relay_failed)
    "P2P_TIMEOUT" -> stringResource(R.string.diagnostics_p2p_timeout)
    "P2P_CONNECTION_FAILED" -> stringResource(R.string.diagnostics_p2p_failed)
    "CANCELLED" -> stringResource(R.string.diagnostics_cancelled)
    "START_FAILED", "CANCEL_FAILED" -> stringResource(R.string.diagnostics_start_failed)
    else -> stringResource(R.string.diagnostics_unknown_error)
}

@Composable
private fun connectionStatusLabel(status: String): String = when (status.lowercase()) {
    "wait", "discovering" -> stringResource(R.string.diagnostics_stage_discovering)
    "connecting" -> stringResource(R.string.diagnostics_stage_connecting)
    "negotiating" -> stringResource(R.string.diagnostics_stage_negotiating)
    "connected" -> stringResource(R.string.diagnostics_stage_connected)
    "disconnected" -> stringResource(R.string.diagnostics_stage_disconnected)
    "error" -> stringResource(R.string.diagnostics_stage_error)
    else -> status.ifBlank { stringResource(R.string.diagnostics_unknown) }
}

private fun formatRate(bytesPerSecond: Double): String {
    return when {
        bytesPerSecond >= 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f MiB/s", bytesPerSecond / (1024 * 1024))
        bytesPerSecond >= 1024 -> String.format(Locale.getDefault(), "%.1f KiB/s", bytesPerSecond / 1024)
        else -> String.format(Locale.getDefault(), "%.0f B/s", bytesPerSecond)
    }
}
