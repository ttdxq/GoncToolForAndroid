package cyou.ttdxq.goncvpn.android.ui

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cyou.ttdxq.goncvpn.android.core.GoncVpnService
import cyou.ttdxq.goncvpn.android.data.SettingsStore
import cyou.ttdxq.goncvpn.android.data.LogRepository
import cyou.ttdxq.goncvpn.android.data.VpnState
import cyou.ttdxq.goncvpn.android.data.VpnStatus
import cyou.ttdxq.goncvpn.android.util.CidrValidator
import cyou.ttdxq.goncvpn.android.util.CidrValidationResult
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.firstOrNull
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState

class MainActivity : ComponentActivity() {

    private val vpnPrepareLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            startVpnService()
        } else {
            Toast.makeText(this, "VPN permission denied", Toast.LENGTH_LONG).show()
        }
    }

    private lateinit var settingsStore: SettingsStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsStore = SettingsStore(this)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    VpnControlScreen(settingsStore, onStartVpn = {
                        prepareVpn()
                    }, onStopVpn = {
                        stopVpnService()
                    })
                }
            }
        }
    }

    private fun prepareVpn() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            vpnPrepareLauncher.launch(intent)
        } else {
            startVpnService()
        }
    }

    private fun startVpnService() {
        val scope = kotlinx.coroutines.MainScope()
        scope.launch {
            // Use first() to get current value once, instead of collect() which keeps listening
            val p2pSecret = settingsStore.p2pSecret.firstOrNull() ?: ""
            val routeCidrs = settingsStore.routeCidrs.firstOrNull() ?: ""

            // 验证P2P Secret
            if (p2pSecret.isBlank()) {
                Toast.makeText(this@MainActivity, "P2P Secret不能为空", Toast.LENGTH_LONG).show()
                return@launch
            }
            
            // 验证CIDR
            val cidrValidation = CidrValidator.validateCidrs(routeCidrs)
            if (cidrValidation is CidrValidationResult.Invalid) {
                Toast.makeText(this@MainActivity, "CIDR验证失败: ${cidrValidation.error}", Toast.LENGTH_LONG).show()
                return@launch
            }

            val intent = Intent(this@MainActivity, GoncVpnService::class.java).apply {
                action = GoncVpnService.ACTION_START
                putExtra(GoncVpnService.EXTRA_P2P_SECRET, p2pSecret)
                putExtra(GoncVpnService.EXTRA_ROUTE_CIDRS, routeCidrs)
            }
            try {
                // In Android 12+, we must catch potential exceptions if startForegroundService 
                // is called from background (though here it is UI click).
                startForegroundService(intent) 
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "启动VPN失败: ${e.message}", Toast.LENGTH_LONG).show()
                e.printStackTrace()
            }
        }
    }

    private fun stopVpnService() {
        val intent = Intent(this, GoncVpnService::class.java).apply {
            action = GoncVpnService.ACTION_STOP
        }
        startService(intent)
    }
}

/**
 * VPN状态卡片组件
 */
@Composable
fun StatusCard(status: VpnStatus, errorMessage: String?) {
    val (title, color, icon) = when (status) {
        VpnStatus.DISCONNECTED -> Triple("未连接", Color.Gray, "⚪")
        VpnStatus.CONNECTING -> Triple("连接中...", Color(0xFFFFA500), "🟡")
        VpnStatus.STOPPING -> Triple("停止中...", Color(0xFFFFA500), "🟡")
        VpnStatus.CONNECTED -> Triple("已连接", Color(0xFF4CAF50), "🟢")
        VpnStatus.ERROR -> Triple("错误", Color(0xFFF44336), "🔴")
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
            Text(
                text = icon,
                style = MaterialTheme.typography.headlineLarge
            )
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

@Composable
fun VpnControlScreen(settingsStore: SettingsStore, onStartVpn: () -> Unit, onStopVpn: () -> Unit) {
    val scope = rememberCoroutineScope()
    val p2pSecret by settingsStore.p2pSecret.collectAsState(initial = "")
    val routeCidrs by settingsStore.routeCidrs.collectAsState(initial = "")
    val logs = remember { mutableStateListOf<String>() }
    val vpnStatus by VpnState.status.collectAsState()
    val errorMessage by VpnState.errorMessage.collectAsState()
    
    LaunchedEffect(Unit) {
        LogRepository.logs.collect { log ->
            logs.add(0, log) // Add to top
            if (logs.size > 200) logs.removeRange(200, logs.size)
        }
    }
    
    var secretInput by remember { mutableStateOf(p2pSecret) }
    var cidrsInput by remember { mutableStateOf(routeCidrs) }
    var cidrValidationError by remember { mutableStateOf<String?>(null) }
    
    // Update local state when flow emits new values (initial load)
    LaunchedEffect(p2pSecret) { if (secretInput.isBlank()) secretInput = p2pSecret }
    LaunchedEffect(routeCidrs) { if (cidrsInput.isBlank()) cidrsInput = routeCidrs }
    
    // CIDR实时验证
    LaunchedEffect(cidrsInput) {
        if (cidrsInput.isNotBlank()) {
            val result = CidrValidator.validateCidrs(cidrsInput)
            cidrValidationError = if (result is CidrValidationResult.Invalid) {
                result.error
            } else {
                null
            }
        } else {
            cidrValidationError = null
        }
    }
    
    // Determine if buttons should be enabled
    val isStartEnabled = (vpnStatus == VpnStatus.DISCONNECTED || vpnStatus == VpnStatus.ERROR) && 
                         cidrValidationError == null &&
                         secretInput.isNotBlank()
    val isStopEnabled = vpnStatus == VpnStatus.CONNECTED || vpnStatus == VpnStatus.CONNECTING || vpnStatus == VpnStatus.STOPPING

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Status Card
        StatusCard(vpnStatus, errorMessage)
        
        Text("Gonc VPN", style = MaterialTheme.typography.headlineMedium)

        OutlinedTextField(
            value = secretInput,
            onValueChange = { 
                secretInput = it
                scope.launch { settingsStore.setP2pSecret(it) }
            },
            label = { Text("P2P Secret Key") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = cidrsInput,
            onValueChange = { 
                cidrsInput = it
                scope.launch { settingsStore.setRouteCidrs(it) }
            },
            label = { Text("Route CIDRs (one per line)") },
            supportingText = {
                Column {
                    Text("例如: 0.0.0.0/0 或 ::/0 表示全局代理")
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
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = onStartVpn,
                enabled = isStartEnabled,
                modifier = Modifier.weight(1f)
            ) {
                Text("Start VPN")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onStopVpn,
                enabled = isStopEnabled,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.weight(1f)
            ) {
                Text("Stop VPN")
            }
        }
        
        Text("Logs:", style = MaterialTheme.typography.titleMedium)
        SelectionContainer(modifier = Modifier.weight(1f)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                logs.forEach { log ->
                    Text(text = log, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 2.dp))
                }
            }
        }
    }
}
