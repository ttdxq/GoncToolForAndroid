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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cyou.ttdxq.gonctool.android.R
import cyou.ttdxq.gonctool.android.data.SettingsStore
import cyou.ttdxq.gonctool.android.util.RelayConfig
import cyou.ttdxq.gonctool.android.util.RelayConfigError
import cyou.ttdxq.gonctool.android.util.RelayMode
import cyou.ttdxq.gonctool.android.util.isValidIpAddressInput
import cyou.ttdxq.gonctool.android.util.validateRelayConfig
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
    val autoP2pLan by settingsStore.autoP2pLan.collectAsState(initial = true)
    val relayEnabled by settingsStore.relayEnabled.collectAsState(initial = false)
    val relayModeValue by settingsStore.relayMode.collectAsState(initial = RelayMode.FALLBACK.value)
    val relayHost by settingsStore.relayHost.collectAsState(initial = "")
    val relayPort by settingsStore.relayPort.collectAsState(initial = "1080")
    val relayTlsEnabled by settingsStore.relayTlsEnabled.collectAsState(initial = false)
    val relayPsk by settingsStore.relayPsk.collectAsState(initial = "")
    val relayAuthEnabled by settingsStore.relayAuthEnabled.collectAsState(initial = false)
    val relayUsername by settingsStore.relayUsername.collectAsState(initial = "")
    val relayPassword by settingsStore.relayPassword.collectAsState(initial = "")
    var showExpertModeDialog by remember { mutableStateOf(false) }

    var dnsInput by remember { mutableStateOf(customDnsAddress) }
    var stunInput by remember { mutableStateOf(customStunServers) }
    var mqttInput by remember { mutableStateOf(customMqttServers) }
    var expertArgsInput by remember { mutableStateOf(expertModeRawArgs) }
    var relayHostInput by remember { mutableStateOf(relayHost) }
    var relayPortInput by remember { mutableStateOf(relayPort) }
    var relayPskInput by remember { mutableStateOf(relayPsk) }
    var relayUsernameInput by remember { mutableStateOf(relayUsername) }
    var relayPasswordInput by remember { mutableStateOf(relayPassword) }
    var relayPskVisible by remember { mutableStateOf(false) }
    var relayPasswordVisible by remember { mutableStateOf(false) }
    var isDnsInputInvalid by remember { mutableStateOf(false) }

    LaunchedEffect(customDnsAddress) { dnsInput = customDnsAddress }
    LaunchedEffect(customStunServers) { stunInput = customStunServers }
    LaunchedEffect(customMqttServers) { mqttInput = customMqttServers }
    LaunchedEffect(expertModeRawArgs) { expertArgsInput = expertModeRawArgs }
    LaunchedEffect(relayHost) { relayHostInput = relayHost }
    LaunchedEffect(relayPort) { relayPortInput = relayPort }
    LaunchedEffect(relayPsk) { relayPskInput = relayPsk }
    LaunchedEffect(relayUsername) { relayUsernameInput = relayUsername }
    LaunchedEffect(relayPassword) { relayPasswordInput = relayPassword }

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
    LaunchedEffect(relayHostInput, relayHost) {
        if (relayHostInput != relayHost) {
            delay(400)
            settingsStore.setRelayHost(relayHostInput)
        }
    }
    LaunchedEffect(relayPortInput, relayPort) {
        if (relayPortInput != relayPort) {
            delay(400)
            settingsStore.setRelayPort(relayPortInput)
        }
    }
    LaunchedEffect(relayPskInput, relayPsk) {
        if (relayPskInput != relayPsk) {
            delay(400)
            settingsStore.setRelayPsk(relayPskInput)
        }
    }
    LaunchedEffect(relayUsernameInput, relayUsername) {
        if (relayUsernameInput != relayUsername) {
            delay(400)
            settingsStore.setRelayUsername(relayUsernameInput)
        }
    }
    LaunchedEffect(relayPasswordInput, relayPassword) {
        if (relayPasswordInput != relayPassword) {
            delay(400)
            settingsStore.setRelayPassword(relayPasswordInput)
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
    val relayMode = RelayMode.fromValue(relayModeValue)
    val relayValidationError = validateRelayConfig(
        RelayConfig(
            enabled = relayEnabled,
            mode = relayMode,
            host = relayHostInput,
            port = relayPortInput.toIntOrNull() ?: 0,
            tlsEnabled = relayTlsEnabled,
            psk = relayPskInput,
            authEnabled = relayAuthEnabled,
            username = relayUsernameInput,
            password = relayPasswordInput,
        )
    )

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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.auto_p2p_lan))
                            Text(
                                text = stringResource(R.string.auto_p2p_lan_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = autoP2pLan,
                            onCheckedChange = { enabled ->
                                scope.launch { settingsStore.setAutoP2pLan(enabled) }
                            }
                        )
                    }
                }
            }

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
                        text = stringResource(R.string.relay_section_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.relay_enabled))
                            Text(
                                text = stringResource(R.string.relay_enabled_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = relayEnabled,
                            onCheckedChange = { enabled ->
                                scope.launch { settingsStore.setRelayEnabled(enabled) }
                            }
                        )
                    }

                    AnimatedVisibility(
                        visible = relayEnabled,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(
                                text = stringResource(R.string.relay_mode_label),
                                style = MaterialTheme.typography.labelLarge
                            )
                            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                val modes = listOf(RelayMode.FALLBACK, RelayMode.FORCE)
                                modes.forEachIndexed { index, mode ->
                                    SegmentedButton(
                                        selected = relayMode == mode,
                                        onClick = {
                                            scope.launch { settingsStore.setRelayMode(mode.value) }
                                        },
                                        shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                                        label = {
                                            Text(
                                                if (mode == RelayMode.FALLBACK) {
                                                    stringResource(R.string.relay_mode_fallback)
                                                } else {
                                                    stringResource(R.string.relay_mode_force)
                                                }
                                            )
                                        }
                                    )
                                }
                            }
                            Text(
                                text = if (relayMode == RelayMode.FALLBACK) {
                                    stringResource(R.string.relay_mode_fallback_hint)
                                } else {
                                    stringResource(R.string.relay_mode_force_hint)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                OutlinedTextField(
                                    value = relayHostInput,
                                    onValueChange = { relayHostInput = it },
                                    label = { Text(stringResource(R.string.relay_host_label)) },
                                    supportingText = {
                                        Text(
                                            when (relayValidationError) {
                                                RelayConfigError.HOST_REQUIRED -> stringResource(R.string.relay_host_required)
                                                RelayConfigError.HOST_INVALID -> stringResource(R.string.relay_host_invalid)
                                                else -> stringResource(R.string.relay_host_hint)
                                            }
                                        )
                                    },
                                    isError = relayValidationError == RelayConfigError.HOST_REQUIRED ||
                                        relayValidationError == RelayConfigError.HOST_INVALID,
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = relayPortInput,
                                    onValueChange = { value ->
                                        relayPortInput = value.filter(Char::isDigit).take(5)
                                    },
                                    label = { Text(stringResource(R.string.relay_port_label)) },
                                    supportingText = {
                                        if (relayValidationError == RelayConfigError.PORT_INVALID) {
                                            Text(stringResource(R.string.relay_port_invalid))
                                        }
                                    },
                                    isError = relayValidationError == RelayConfigError.PORT_INVALID,
                                    modifier = Modifier.weight(0.45f),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(stringResource(R.string.relay_tls_enabled))
                                    Text(
                                        text = stringResource(R.string.relay_tls_hint),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = relayTlsEnabled,
                                    onCheckedChange = { enabled ->
                                        scope.launch { settingsStore.setRelayTlsEnabled(enabled) }
                                    }
                                )
                            }
                            AnimatedVisibility(visible = relayTlsEnabled) {
                                RelaySecretField(
                                    value = relayPskInput,
                                    onValueChange = { relayPskInput = it },
                                    label = stringResource(R.string.relay_psk_label),
                                    error = if (relayValidationError == RelayConfigError.PSK_REQUIRED) {
                                        stringResource(R.string.relay_psk_required)
                                    } else {
                                        null
                                    },
                                    visible = relayPskVisible,
                                    onVisibilityChange = { relayPskVisible = !relayPskVisible }
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(stringResource(R.string.relay_auth_enabled))
                                    Text(
                                        text = stringResource(R.string.relay_auth_hint),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = relayAuthEnabled,
                                    onCheckedChange = { enabled ->
                                        scope.launch { settingsStore.setRelayAuthEnabled(enabled) }
                                    }
                                )
                            }
                            AnimatedVisibility(visible = relayAuthEnabled) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = relayUsernameInput,
                                        onValueChange = { relayUsernameInput = it },
                                        label = { Text(stringResource(R.string.relay_username_label)) },
                                        isError = relayValidationError == RelayConfigError.CREDENTIALS_REQUIRED &&
                                            relayUsernameInput.isBlank(),
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )
                                    RelaySecretField(
                                        value = relayPasswordInput,
                                        onValueChange = { relayPasswordInput = it },
                                        label = stringResource(R.string.relay_password_label),
                                        error = if (relayValidationError == RelayConfigError.CREDENTIALS_REQUIRED &&
                                            relayPasswordInput.isBlank()
                                        ) {
                                            stringResource(R.string.relay_credentials_required)
                                        } else {
                                            null
                                        },
                                        visible = relayPasswordVisible,
                                        onVisibilityChange = { relayPasswordVisible = !relayPasswordVisible }
                                    )
                                }
                            }
                        }
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

@Composable
private fun RelaySecretField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: String?,
    visible: Boolean,
    onVisibilityChange: () -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        supportingText = error?.let { message -> { Text(message) } },
        isError = error != null,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        visualTransformation = if (visible) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        trailingIcon = {
            TextButton(onClick = onVisibilityChange) {
                Text(stringResource(if (visible) R.string.hide_secret else R.string.show_secret))
            }
        }
    )
}
