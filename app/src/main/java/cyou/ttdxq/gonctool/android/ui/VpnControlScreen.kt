package cyou.ttdxq.gonctool.android.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cyou.ttdxq.gonctool.android.R
import cyou.ttdxq.gonctool.android.data.SettingsStore
import cyou.ttdxq.gonctool.android.data.VpnState
import cyou.ttdxq.gonctool.android.data.VpnStatus
import cyou.ttdxq.gonctool.android.util.CidrValidationResult
import cyou.ttdxq.gonctool.android.util.CidrValidator
import kotlinx.coroutines.launch

private const val CIDR_PRESET_GLOBAL = "0.0.0.0/0\n::/0"
private const val CIDR_PRESET_LAN = "10.0.0.0/8\n172.16.0.0/12\n192.168.0.0/16"

private enum class CidrPreset {
    GLOBAL, LAN, CUSTOM
}

private fun normalizeCidrs(value: String): String {
    return value
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString("\n")
}

@Composable
fun StatusCard(status: VpnStatus, errorMessage: String?) {
    val statusColors = getStatusColors()

    val targetContainerColor = when (status) {
        VpnStatus.CONNECTED -> statusColors.successContainer
        VpnStatus.CONNECTING -> statusColors.warningContainer
        VpnStatus.STOPPING -> statusColors.warningContainer
        VpnStatus.ERROR -> statusColors.errorContainer
        VpnStatus.DISCONNECTED -> MaterialTheme.colorScheme.surfaceVariant
    }

    val targetStatusColor = when (status) {
        VpnStatus.DISCONNECTED -> MaterialTheme.colorScheme.onSurfaceVariant
        VpnStatus.CONNECTING -> statusColors.warning
        VpnStatus.STOPPING -> statusColors.warning
        VpnStatus.CONNECTED -> statusColors.success
        VpnStatus.ERROR -> statusColors.error
    }

    val animatedContainerColor: Color by animateColorAsState(
        targetValue = targetContainerColor,
        animationSpec = tween(durationMillis = 300),
        label = "containerColor"
    )
    val animatedStatusColor: Color by animateColorAsState(
        targetValue = targetStatusColor,
        animationSpec = tween(durationMillis = 300),
        label = "statusColor"
    )

    val isPulsing = status == VpnStatus.CONNECTING || status == VpnStatus.STOPPING
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val icon = when (status) {
        VpnStatus.DISCONNECTED -> "⚪"
        VpnStatus.CONNECTING -> "🟡"
        VpnStatus.STOPPING -> "🟡"
        VpnStatus.CONNECTED -> "🟢"
        VpnStatus.ERROR -> "🔴"
    }
    val title = when (status) {
        VpnStatus.DISCONNECTED -> stringResource(R.string.status_disconnected)
        VpnStatus.CONNECTING -> stringResource(R.string.status_connecting)
        VpnStatus.STOPPING -> stringResource(R.string.status_stopping)
        VpnStatus.CONNECTED -> stringResource(R.string.status_connected)
        VpnStatus.ERROR -> stringResource(R.string.status_error)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = animatedContainerColor
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = icon,
                style = MaterialTheme.typography.headlineLarge,
                color = animatedStatusColor,
                modifier = Modifier.graphicsLayer {
                    alpha = if (isPulsing) pulseAlpha else 1f
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = animatedStatusColor
            )
            AnimatedVisibility(
                visible = status == VpnStatus.ERROR && !errorMessage.isNullOrBlank(),
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = statusColors.errorText
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VpnControlScreen(
    settingsStore: SettingsStore,
    onStartVpn: () -> Unit,
    onStopVpn: () -> Unit,
    onMenuClick: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val p2pSecret by settingsStore.p2pSecret.collectAsState(initial = "")
    val routeCidrs by settingsStore.routeCidrs.collectAsState(initial = "")
    val vpnStatus by VpnState.status.collectAsState()
    val errorMessage by VpnState.errorMessage.collectAsState()

    var secretInput by remember { mutableStateOf(p2pSecret) }
    var cidrsInput by remember { mutableStateOf(routeCidrs) }
    var cidrValidationError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(p2pSecret) { secretInput = p2pSecret }
    LaunchedEffect(routeCidrs) { cidrsInput = routeCidrs }

    LaunchedEffect(secretInput, p2pSecret) {
        if (secretInput != p2pSecret) {
            delay(500)
            settingsStore.setP2pSecret(secretInput)
        }
    }
    LaunchedEffect(cidrsInput, routeCidrs) {
        if (cidrsInput != routeCidrs) {
            delay(300)
        }
        if (cidrsInput.isNotBlank()) {
            val result = CidrValidator.validateCidrs(cidrsInput)
            cidrValidationError = if (result is CidrValidationResult.Invalid) result.error else null
        } else {
            cidrValidationError = null
        }
    }
    LaunchedEffect(cidrsInput, routeCidrs) {
        if (cidrsInput != routeCidrs) {
            delay(300)
            settingsStore.setRouteCidrs(cidrsInput)
        }
    }

    val activePreset = when {
        normalizeCidrs(cidrsInput) == normalizeCidrs(CIDR_PRESET_GLOBAL) -> CidrPreset.GLOBAL
        normalizeCidrs(cidrsInput) == normalizeCidrs(CIDR_PRESET_LAN) -> CidrPreset.LAN
        else -> CidrPreset.CUSTOM
    }

    val isStartEnabled =
        (vpnStatus == VpnStatus.DISCONNECTED || vpnStatus == VpnStatus.ERROR) &&
            cidrValidationError == null &&
            secretInput.isNotBlank()
    val isStopEnabled =
        vpnStatus == VpnStatus.CONNECTED ||
            vpnStatus == VpnStatus.CONNECTING ||
            vpnStatus == VpnStatus.STOPPING

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_title)) },
                navigationIcon = {
                    AppNavigationMenuButton(
                        onMenuClick = onMenuClick,
                    )
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StatusCard(vpnStatus, errorMessage)

            OutlinedTextField(
                value = secretInput,
                onValueChange = {
                    secretInput = it
                },
                label = { Text(stringResource(R.string.p2p_secret_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        cidrsInput = CIDR_PRESET_GLOBAL
                        scope.launch { settingsStore.setRouteCidrs(CIDR_PRESET_GLOBAL) }
                    },
                    modifier = Modifier.weight(1f),
                    colors = if (activePreset == CidrPreset.GLOBAL) {
                        ButtonDefaults.outlinedButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    } else {
                        ButtonDefaults.outlinedButtonColors()
                    }
                ) {
                    Text(stringResource(R.string.cidr_preset_global))
                }
                OutlinedButton(
                    onClick = {
                        cidrsInput = CIDR_PRESET_LAN
                        scope.launch { settingsStore.setRouteCidrs(CIDR_PRESET_LAN) }
                    },
                    modifier = Modifier.weight(1f),
                    colors = if (activePreset == CidrPreset.LAN) {
                        ButtonDefaults.outlinedButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    } else {
                        ButtonDefaults.outlinedButtonColors()
                    }
                ) {
                    Text(stringResource(R.string.cidr_preset_lan))
                }
                OutlinedButton(
                    onClick = {
                        cidrsInput = ""
                        scope.launch { settingsStore.setRouteCidrs("") }
                    },
                    modifier = Modifier.weight(1f),
                    colors = if (activePreset == CidrPreset.CUSTOM) {
                        ButtonDefaults.outlinedButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    } else {
                        ButtonDefaults.outlinedButtonColors()
                    }
                ) {
                    Text(stringResource(R.string.cidr_preset_custom))
                }
            }

            OutlinedTextField(
                value = cidrsInput,
                onValueChange = {
                    cidrsInput = it
                },
                label = { Text(stringResource(R.string.route_cidrs_label)) },
                supportingText = {
                    Column {
                        Text(stringResource(R.string.route_cidrs_hint))
                        cidrValidationError?.let { error ->
                            Text(
                                text = error,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                },
                isError = cidrValidationError != null,
                modifier = Modifier.fillMaxWidth().height(180.dp),
                maxLines = 10
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onStartVpn,
                    enabled = isStartEnabled,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.start_vpn))
                }
                Button(
                    onClick = onStopVpn,
                    enabled = isStopEnabled,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.stop_vpn))
                }
            }
        }
    }
}
