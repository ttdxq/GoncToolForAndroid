package cyou.ttdxq.gonctool.android.ui

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cyou.ttdxq.gonctool.android.R
import cyou.ttdxq.gonctool.android.data.SettingsStore
import cyou.ttdxq.gonctool.android.util.isValidIpAddressInput
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsStore: SettingsStore,
    onMenuClick: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val useCustomDns by settingsStore.useCustomDns.collectAsState(initial = false)
    val customDnsAddress by settingsStore.customDnsAddress.collectAsState(initial = "")
    val dnsThroughTunnel by settingsStore.dnsThroughTunnel.collectAsState(initial = true)
    val linkGoncDns by settingsStore.linkGoncDns.collectAsState(initial = false)
    val customStunServers by settingsStore.customStunServers.collectAsState(initial = "")
    val customMqttServers by settingsStore.customMqttServers.collectAsState(initial = "")
    val expertModeEnabled by settingsStore.expertModeEnabled.collectAsState(initial = false)
    val expertModeRawArgs by settingsStore.expertModeRawArgs.collectAsState(initial = "")
    val kcpEnabled by settingsStore.kcpEnabled.collectAsState(initial = false)
    var showExpertModeDialog by remember { mutableStateOf(false) }

    var dnsInput by remember { mutableStateOf(customDnsAddress) }
    var stunInput by remember { mutableStateOf(customStunServers) }
    var mqttInput by remember { mutableStateOf(customMqttServers) }
    var expertArgsInput by remember { mutableStateOf(expertModeRawArgs) }
    var isDnsInputInvalid by remember { mutableStateOf(false) }

    LaunchedEffect(customDnsAddress) { dnsInput = customDnsAddress }
    LaunchedEffect(customStunServers) { stunInput = customStunServers }
    LaunchedEffect(customMqttServers) { mqttInput = customMqttServers }
    LaunchedEffect(expertModeRawArgs) { expertArgsInput = expertModeRawArgs }

    LaunchedEffect(dnsInput, customDnsAddress) {
        if (dnsInput != customDnsAddress) {
            delay(400)
            settingsStore.setCustomDnsAddress(dnsInput)
        }
    }
    LaunchedEffect(stunInput, customStunServers) {
        if (stunInput != customStunServers) {
            delay(400)
            settingsStore.setCustomStunServers(stunInput)
        }
    }
    LaunchedEffect(mqttInput, customMqttServers) {
        if (mqttInput != customMqttServers) {
            delay(400)
            settingsStore.setCustomMqttServers(mqttInput)
        }
    }
    LaunchedEffect(expertArgsInput, expertModeRawArgs) {
        if (expertArgsInput != expertModeRawArgs) {
            delay(400)
            settingsStore.setExpertModeRawArgs(expertArgsInput)
        }
    }

    val canBypassDns = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    LaunchedEffect(useCustomDns, dnsInput, customDnsAddress) {
        if (dnsInput != customDnsAddress) {
            delay(300)
        }
        isDnsInputInvalid = useCustomDns && !isValidIpAddressInput(dnsInput)
    }
    val dnsValidationError = if (isDnsInputInvalid) {
        stringResource(R.string.dns_address_validation)
    } else {
        null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    AppNavigationMenuButton(
                        onMenuClick = onMenuClick,
                    )
                },
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 📦 传输协议
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.transport_section_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.kcp_enabled))
                            Text(
                                text = stringResource(R.string.kcp_enabled_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = kcpEnabled,
                            onCheckedChange = { enabled ->
                                scope.launch { settingsStore.setKcpEnabled(enabled) }
                            }
                        )
                    }
                }
            }

            // 🌐 网络设置
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.network_settings_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.use_custom_dns))
                        Switch(
                            checked = useCustomDns,
                            onCheckedChange = { enabled ->
                                scope.launch { settingsStore.setUseCustomDns(enabled) }
                            }
                        )
                    }

                    OutlinedTextField(
                        value = dnsInput,
                        onValueChange = { dnsInput = it },
                        label = { Text(stringResource(R.string.dns_address_label)) },
                        supportingText = {
                            Text(dnsValidationError ?: stringResource(R.string.dns_address_example))
                        },
                        isError = dnsValidationError != null,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = useCustomDns,
                        singleLine = true
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.dns_through_tunnel))
                            Text(
                                text = if (canBypassDns) {
                                    stringResource(R.string.dns_through_tunnel_enabled_hint)
                                } else {
                                    stringResource(R.string.dns_through_tunnel_disabled_hint)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = dnsThroughTunnel,
                            onCheckedChange = { enabled ->
                                scope.launch {
                                    settingsStore.setDnsThroughTunnel(if (canBypassDns) enabled else true)
                                }
                            },
                            enabled = useCustomDns && canBypassDns
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.link_gonc_dns))
                            Text(
                                text = stringResource(R.string.link_gonc_dns_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = linkGoncDns,
                            onCheckedChange = { enabled ->
                                scope.launch { settingsStore.setLinkGoncDns(enabled) }
                            },
                            enabled = useCustomDns
                        )
                    }
                }
            }

            // 📡 P2P 辅助服务器
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.p2p_network_section_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedTextField(
                        value = stunInput,
                        onValueChange = { stunInput = it },
                        label = { Text(stringResource(R.string.custom_stun_servers_label)) },
                        supportingText = {
                            Text(stringResource(R.string.custom_stun_servers_hint))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                    )

                    OutlinedTextField(
                        value = mqttInput,
                        onValueChange = { mqttInput = it },
                        label = { Text(stringResource(R.string.custom_mqtt_servers_label)) },
                        supportingText = {
                            Text(stringResource(R.string.custom_mqtt_servers_hint))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                    )
                }
            }

            // ⚙️ 专家模式
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.expert_mode_section_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.expert_mode_switch_label))
                        Switch(
                            checked = expertModeEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    showExpertModeDialog = true
                                } else {
                                    scope.launch { settingsStore.setExpertModeEnabled(false) }
                                }
                            }
                        )
                    }

                    AnimatedVisibility(
                        visible = expertModeEnabled,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        OutlinedTextField(
                            value = expertArgsInput,
                            onValueChange = { expertArgsInput = it },
                            label = { Text(stringResource(R.string.expert_mode_raw_args_label)) },
                            supportingText = {
                                Text(stringResource(R.string.expert_mode_raw_args_hint))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                        )
                    }
                }
            }

            if (showExpertModeDialog) {
                AlertDialog(
                    onDismissRequest = { showExpertModeDialog = false },
                    title = { Text(stringResource(R.string.expert_mode_dialog_title)) },
                    text = { Text(stringResource(R.string.expert_mode_dialog_message)) },
                    confirmButton = {
                        TextButton(onClick = {
                            showExpertModeDialog = false
                            scope.launch { settingsStore.setExpertModeEnabled(true) }
                        }) {
                            Text(stringResource(R.string.expert_mode_dialog_confirm))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showExpertModeDialog = false }) {
                            Text(stringResource(R.string.expert_mode_dialog_cancel))
                        }
                    }
                )
            }
        }
    }
}
