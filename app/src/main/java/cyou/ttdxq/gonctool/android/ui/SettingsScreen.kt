package cyou.ttdxq.gonctool.android.ui

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private const val SETTINGS_ENTER_ANIMATION_MS = 180
private const val SETTINGS_EXIT_ANIMATION_MS = 120
private const val RELAY_PERSIST_DELAY_MS = 220L

private class PendingJob {
    var value: Job? = null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsStore: SettingsStore,
    persistenceScope: CoroutineScope,
    onMenuClick: () -> Unit,
) {
    val persistedUseCustomDns by settingsStore.useCustomDns.collectAsState(initial = false)
    val customDnsAddress by settingsStore.customDnsAddress.collectAsState(initial = null)
    val persistedDnsThroughTunnel by settingsStore.dnsThroughTunnel.collectAsState(initial = true)
    val persistedLinkGoncDns by settingsStore.linkGoncDns.collectAsState(initial = false)
    val customStunServers by settingsStore.customStunServers.collectAsState(initial = null)
    val customMqttServers by settingsStore.customMqttServers.collectAsState(initial = null)
    val persistedExpertModeEnabled by settingsStore.expertModeEnabled.collectAsState(initial = false)
    val expertModeRawArgs by settingsStore.expertModeRawArgs.collectAsState(initial = null)
    val persistedKcpEnabled by settingsStore.kcpEnabled.collectAsState(initial = false)
    val persistedAutoP2pLan by settingsStore.autoP2pLan.collectAsState(initial = true)
    val persistedRelayEnabled by settingsStore.relayEnabled.collectAsState(initial = false)
    val relayModeValue by settingsStore.relayMode.collectAsState(initial = RelayMode.FALLBACK.value)
    val relayHost by settingsStore.relayHost.collectAsState(initial = null)
    val relayPort by settingsStore.relayPort.collectAsState(initial = null)
    val persistedRelayTlsEnabled by settingsStore.relayTlsEnabled.collectAsState(initial = false)
    val relayPsk by settingsStore.relayPsk.collectAsState(initial = null)
    val persistedRelayAuthEnabled by settingsStore.relayAuthEnabled.collectAsState(initial = false)
    val relayUsername by settingsStore.relayUsername.collectAsState(initial = null)
    val relayPassword by settingsStore.relayPassword.collectAsState(initial = null)
    var showExpertModeDialog by rememberSaveable { mutableStateOf(false) }

    var useCustomDns by remember(persistedUseCustomDns) { mutableStateOf(persistedUseCustomDns) }
    var dnsThroughTunnel by remember(persistedDnsThroughTunnel) { mutableStateOf(persistedDnsThroughTunnel) }
    var linkGoncDns by remember(persistedLinkGoncDns) { mutableStateOf(persistedLinkGoncDns) }
    var expertModeEnabled by remember(persistedExpertModeEnabled) { mutableStateOf(persistedExpertModeEnabled) }
    var kcpEnabled by remember(persistedKcpEnabled) { mutableStateOf(persistedKcpEnabled) }
    var autoP2pLan by remember(persistedAutoP2pLan) { mutableStateOf(persistedAutoP2pLan) }
    var relayEnabled by remember(persistedRelayEnabled) { mutableStateOf(persistedRelayEnabled) }
    var relayDetailsVisible by remember { mutableStateOf(relayEnabled) }
    val relayPersistenceJob = remember { PendingJob() }
    var relayTlsEnabled by remember(persistedRelayTlsEnabled) { mutableStateOf(persistedRelayTlsEnabled) }
    var relayAuthEnabled by remember(persistedRelayAuthEnabled) { mutableStateOf(persistedRelayAuthEnabled) }

    var inputsInitialized by rememberSaveable { mutableStateOf(false) }
    var dnsInput by rememberSaveable { mutableStateOf("") }
    var stunInput by rememberSaveable { mutableStateOf("") }
    var mqttInput by rememberSaveable { mutableStateOf("") }
    var expertArgsInput by rememberSaveable { mutableStateOf("") }
    var relayHostInput by rememberSaveable { mutableStateOf("") }
    var relayPortInput by rememberSaveable { mutableStateOf("1080") }
    var relayPskInput by rememberSaveable { mutableStateOf("") }
    var relayUsernameInput by rememberSaveable { mutableStateOf("") }
    var relayPasswordInput by rememberSaveable { mutableStateOf("") }
    var relayPskVisible by rememberSaveable { mutableStateOf(false) }
    var relayPasswordVisible by rememberSaveable { mutableStateOf(false) }
    var isDnsInputInvalid by remember { mutableStateOf(false) }

    LaunchedEffect(
        customDnsAddress,
        customStunServers,
        customMqttServers,
        expertModeRawArgs,
        relayHost,
        relayPort,
        relayPsk,
        relayUsername,
        relayPassword,
    ) {
        if (!inputsInitialized &&
            customDnsAddress != null &&
            customStunServers != null &&
            customMqttServers != null &&
            expertModeRawArgs != null &&
            relayHost != null &&
            relayPort != null &&
            relayPsk != null &&
            relayUsername != null &&
            relayPassword != null
        ) {
            dnsInput = customDnsAddress.orEmpty()
            stunInput = customStunServers.orEmpty()
            mqttInput = customMqttServers.orEmpty()
            expertArgsInput = expertModeRawArgs.orEmpty()
            relayHostInput = relayHost.orEmpty()
            relayPortInput = relayPort ?: "1080"
            relayPskInput = relayPsk.orEmpty()
            relayUsernameInput = relayUsername.orEmpty()
            relayPasswordInput = relayPassword.orEmpty()
            inputsInitialized = true
        }
    }

    val flushPendingInputs = rememberUpdatedState {
        if (inputsInitialized) {
            relayPersistenceJob.value?.cancel()
            persistenceScope.launch {
                if (relayEnabled != persistedRelayEnabled) settingsStore.setRelayEnabled(relayEnabled)
                if (dnsInput != customDnsAddress) settingsStore.setCustomDnsAddress(dnsInput)
                if (stunInput != customStunServers) settingsStore.setCustomStunServers(stunInput)
                if (mqttInput != customMqttServers) settingsStore.setCustomMqttServers(mqttInput)
                if (expertArgsInput != expertModeRawArgs) settingsStore.setExpertModeRawArgs(expertArgsInput)
                if (relayHostInput != relayHost) settingsStore.setRelayHost(relayHostInput)
                if (relayPortInput != relayPort) settingsStore.setRelayPort(relayPortInput)
                if (relayPskInput != relayPsk) settingsStore.setRelayPsk(relayPskInput)
                if (relayUsernameInput != relayUsername) settingsStore.setRelayUsername(relayUsernameInput)
                if (relayPasswordInput != relayPassword) settingsStore.setRelayPassword(relayPasswordInput)
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose { flushPendingInputs.value() }
    }

    LaunchedEffect(relayEnabled) {
        if (relayEnabled) {
            repeat(2) { withFrameNanos { } }
        }
        relayDetailsVisible = relayEnabled
    }

    LaunchedEffect(dnsInput, customDnsAddress) {
        if (inputsInitialized && dnsInput != customDnsAddress) {
            delay(400)
            settingsStore.setCustomDnsAddress(dnsInput)
        }
    }
    LaunchedEffect(stunInput, customStunServers) {
        if (inputsInitialized && stunInput != customStunServers) {
            delay(400)
            settingsStore.setCustomStunServers(stunInput)
        }
    }
    LaunchedEffect(mqttInput, customMqttServers) {
        if (inputsInitialized && mqttInput != customMqttServers) {
            delay(400)
            settingsStore.setCustomMqttServers(mqttInput)
        }
    }
    LaunchedEffect(expertArgsInput, expertModeRawArgs) {
        if (inputsInitialized && expertArgsInput != expertModeRawArgs) {
            delay(400)
            settingsStore.setExpertModeRawArgs(expertArgsInput)
        }
    }
    LaunchedEffect(relayHostInput, relayHost) {
        if (inputsInitialized && relayHostInput != relayHost) {
            delay(400)
            settingsStore.setRelayHost(relayHostInput)
        }
    }
    LaunchedEffect(relayPortInput, relayPort) {
        if (inputsInitialized && relayPortInput != relayPort) {
            delay(400)
            settingsStore.setRelayPort(relayPortInput)
        }
    }
    LaunchedEffect(relayPskInput, relayPsk) {
        if (inputsInitialized && relayPskInput != relayPsk) {
            delay(400)
            settingsStore.setRelayPsk(relayPskInput)
        }
    }
    LaunchedEffect(relayUsernameInput, relayUsername) {
        if (inputsInitialized && relayUsernameInput != relayUsername) {
            delay(400)
            settingsStore.setRelayUsername(relayUsernameInput)
        }
    }
    LaunchedEffect(relayPasswordInput, relayPassword) {
        if (inputsInitialized && relayPasswordInput != relayPassword) {
            delay(400)
            settingsStore.setRelayPassword(relayPasswordInput)
        }
    }

    val canBypassDns = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    LaunchedEffect(useCustomDns, dnsInput, customDnsAddress) {
        if (inputsInitialized && dnsInput != customDnsAddress) {
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
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
                                kcpEnabled = enabled
                                persistenceScope.launch { settingsStore.setKcpEnabled(enabled) }
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
                                autoP2pLan = enabled
                                persistenceScope.launch { settingsStore.setAutoP2pLan(enabled) }
                            }
                        )
                    }
                }
                }
            }

            item {
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
                                relayEnabled = enabled
                                relayPersistenceJob.value?.cancel()
                                relayPersistenceJob.value = persistenceScope.launch {
                                    delay(RELAY_PERSIST_DELAY_MS)
                                    settingsStore.setRelayEnabled(enabled)
                                }
                            }
                        )
                    }

                    AnimatedVisibility(
                        visible = relayDetailsVisible,
                        enter = expandVertically(
                            animationSpec = tween(SETTINGS_ENTER_ANIMATION_MS),
                            expandFrom = Alignment.Top,
                        ) + fadeIn(tween(SETTINGS_ENTER_ANIMATION_MS)),
                        exit = shrinkVertically(
                            animationSpec = tween(SETTINGS_EXIT_ANIMATION_MS),
                            shrinkTowards = Alignment.Top,
                        ) + fadeOut(tween(SETTINGS_EXIT_ANIMATION_MS)),
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
                                            persistenceScope.launch { settingsStore.setRelayMode(mode.value) }
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
                                        relayTlsEnabled = enabled
                                        persistenceScope.launch { settingsStore.setRelayTlsEnabled(enabled) }
                                    }
                                )
                            }
                            AnimatedVisibility(
                                visible = relayTlsEnabled,
                                enter = fadeIn(tween(SETTINGS_ENTER_ANIMATION_MS)),
                                exit = fadeOut(tween(SETTINGS_EXIT_ANIMATION_MS)),
                            ) {
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
                                        relayAuthEnabled = enabled
                                        persistenceScope.launch { settingsStore.setRelayAuthEnabled(enabled) }
                                    }
                                )
                            }
                            AnimatedVisibility(
                                visible = relayAuthEnabled,
                                enter = fadeIn(tween(SETTINGS_ENTER_ANIMATION_MS)),
                                exit = fadeOut(tween(SETTINGS_EXIT_ANIMATION_MS)),
                            ) {
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
            }

            item {
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
                                useCustomDns = enabled
                                persistenceScope.launch { settingsStore.setUseCustomDns(enabled) }
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
                                dnsThroughTunnel = if (canBypassDns) enabled else true
                                persistenceScope.launch {
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
                                linkGoncDns = enabled
                                persistenceScope.launch { settingsStore.setLinkGoncDns(enabled) }
                            },
                            enabled = useCustomDns
                        )
                    }
                }
                }
            }

            item {
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
            }

            item {
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
                                    expertModeEnabled = false
                                    persistenceScope.launch { settingsStore.setExpertModeEnabled(false) }
                                }
                            }
                        )
                    }

                    AnimatedVisibility(
                        visible = expertModeEnabled,
                        enter = fadeIn(tween(SETTINGS_ENTER_ANIMATION_MS)),
                        exit = fadeOut(tween(SETTINGS_EXIT_ANIMATION_MS))
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
                            expertModeEnabled = true
                            persistenceScope.launch { settingsStore.setExpertModeEnabled(true) }
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
