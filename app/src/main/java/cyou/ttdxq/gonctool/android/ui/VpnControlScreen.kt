package cyou.ttdxq.gonctool.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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

@Composable
fun StatusCard(status: VpnStatus, errorMessage: String?) {
    val (title, color, icon) = when (status) {
        VpnStatus.DISCONNECTED -> Triple(stringResource(R.string.status_disconnected), Color.Gray, "⚪")
        VpnStatus.CONNECTING -> Triple(stringResource(R.string.status_connecting), Color(0xFFFFA500), "🟡")
        VpnStatus.STOPPING -> Triple(stringResource(R.string.status_stopping), Color(0xFFFFA500), "🟡")
        VpnStatus.CONNECTED -> Triple(stringResource(R.string.status_connected), Color(0xFF4CAF50), "🟢")
        VpnStatus.ERROR -> Triple(stringResource(R.string.status_error), Color(0xFFF44336), "🔴")
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(
            containerColor = when (status) {
                VpnStatus.CONNECTED -> Color(0xFFE8F5E9)
                VpnStatus.CONNECTING -> Color(0xFFFFF8E1)
                VpnStatus.STOPPING -> Color(0xFFFFF8E1)
                VpnStatus.ERROR -> Color(0xFFFFEBEE)
                VpnStatus.DISCONNECTED -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = icon, style = MaterialTheme.typography.headlineLarge)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = color
            )
            errorMessage?.let { error ->
                if (status == VpnStatus.ERROR) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFB71C1C)
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

    LaunchedEffect(p2pSecret) { if (secretInput.isBlank()) secretInput = p2pSecret }
    LaunchedEffect(routeCidrs) { if (cidrsInput.isBlank()) cidrsInput = routeCidrs }

    LaunchedEffect(cidrsInput) {
        if (cidrsInput.isNotBlank()) {
            val result = CidrValidator.validateCidrs(cidrsInput)
            cidrValidationError = if (result is CidrValidationResult.Invalid) result.error else null
        } else {
            cidrValidationError = null
        }
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
                    scope.launch { settingsStore.setP2pSecret(it) }
                },
                label = { Text(stringResource(R.string.p2p_secret_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = cidrsInput,
                onValueChange = {
                    cidrsInput = it
                    scope.launch { settingsStore.setRouteCidrs(it) }
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
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = onStartVpn,
                    enabled = isStartEnabled,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.start_vpn))
                }
                Spacer(modifier = Modifier.width(8.dp))
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
