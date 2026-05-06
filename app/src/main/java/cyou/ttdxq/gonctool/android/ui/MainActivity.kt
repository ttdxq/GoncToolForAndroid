package cyou.ttdxq.gonctool.android.ui

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.zIndex
import cyou.ttdxq.gonctool.android.R
import cyou.ttdxq.gonctool.android.core.GoncToolVpnService
import cyou.ttdxq.gonctool.android.data.SettingsStore
import cyou.ttdxq.gonctool.android.util.CidrValidationResult
import cyou.ttdxq.gonctool.android.util.CidrValidator
import cyou.ttdxq.gonctool.android.util.isValidIpAddressInput
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val vpnPrepareLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            startVpnService()
        } else {
            Toast.makeText(this, getString(R.string.vpn_permission_denied), Toast.LENGTH_LONG).show()
        }
    }

    private lateinit var settingsStore: SettingsStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsStore = SettingsStore(this)

        setContent {
            GoncToolTheme {
                Surface(modifier = Modifier, color = MaterialTheme.colorScheme.background) {
                    AppRoot(
                        settingsStore = settingsStore,
                        onStartVpn = { prepareVpn() },
                        onStopVpn = { stopVpnService() }
                    )
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
            val p2pSecret = settingsStore.p2pSecret.firstOrNull() ?: ""
            val routeCidrs = settingsStore.routeCidrs.firstOrNull() ?: ""
            val splitTunnelMode = settingsStore.splitTunnelMode.firstOrNull() ?: "all"
            val splitTunnelApps = settingsStore.splitTunnelApps.firstOrNull() ?: "[]"
            val useCustomDns = settingsStore.useCustomDns.firstOrNull() ?: false
            val customDnsAddress = settingsStore.customDnsAddress.firstOrNull() ?: ""
            val dnsThroughTunnel = settingsStore.dnsThroughTunnel.firstOrNull() ?: true
            val linkGoncDns = settingsStore.linkGoncDns.firstOrNull() ?: false
            val customStunServers = settingsStore.customStunServers.firstOrNull() ?: ""
            val customMqttServers = settingsStore.customMqttServers.firstOrNull() ?: ""
            val expertModeEnabled = settingsStore.expertModeEnabled.firstOrNull() ?: false
            val expertModeRawArgs = settingsStore.expertModeRawArgs.firstOrNull() ?: ""
            val kcpEnabled = settingsStore.kcpEnabled.firstOrNull() ?: false

            if (p2pSecret.isBlank()) {
                Toast.makeText(this@MainActivity, getString(R.string.p2p_secret_required), Toast.LENGTH_LONG).show()
                return@launch
            }

            if (useCustomDns && !isValidIpAddressInput(customDnsAddress)) {
                Toast.makeText(this@MainActivity, getString(R.string.dns_address_invalid), Toast.LENGTH_LONG).show()
                return@launch
            }

            val cidrValidation = CidrValidator.validateCidrs(routeCidrs)
            if (cidrValidation is CidrValidationResult.Invalid) {
                Toast.makeText(this@MainActivity, getString(R.string.cidr_validation_failed, cidrValidation.error), Toast.LENGTH_LONG).show()
                return@launch
            }

            val intent = Intent(this@MainActivity, GoncToolVpnService::class.java).apply {
                action = GoncToolVpnService.ACTION_START
                putExtra(GoncToolVpnService.EXTRA_P2P_SECRET, p2pSecret)
                putExtra(GoncToolVpnService.EXTRA_ROUTE_CIDRS, routeCidrs)
                putExtra(GoncToolVpnService.EXTRA_SPLIT_TUNNEL_MODE, splitTunnelMode)
                putExtra(GoncToolVpnService.EXTRA_SPLIT_TUNNEL_APPS, splitTunnelApps)
                putExtra(GoncToolVpnService.EXTRA_USE_CUSTOM_DNS, useCustomDns)
                putExtra(GoncToolVpnService.EXTRA_CUSTOM_DNS_ADDRESS, customDnsAddress)
                putExtra(GoncToolVpnService.EXTRA_DNS_THROUGH_TUNNEL, dnsThroughTunnel)
                putExtra(GoncToolVpnService.EXTRA_LINK_GONC_DNS, linkGoncDns)
                putExtra(GoncToolVpnService.EXTRA_CUSTOM_STUN_SERVERS, customStunServers)
                putExtra(GoncToolVpnService.EXTRA_CUSTOM_MQTT_SERVERS, customMqttServers)
                putExtra(GoncToolVpnService.EXTRA_EXPERT_MODE_ENABLED, expertModeEnabled)
                putExtra(GoncToolVpnService.EXTRA_EXPERT_MODE_RAW_ARGS, expertModeRawArgs)
                putExtra(GoncToolVpnService.EXTRA_KCP_ENABLED, kcpEnabled)
            }
            try {
                startForegroundService(intent)
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, getString(R.string.start_vpn_failed, e.message ?: getString(R.string.unknown_error)), Toast.LENGTH_LONG).show()
                e.printStackTrace()
            }
        }
    }

    private fun stopVpnService() {
        val intent = Intent(this, GoncToolVpnService::class.java).apply {
            action = GoncToolVpnService.ACTION_STOP
        }
        startService(intent)
    }
}

@Composable
private fun AppRoot(
    settingsStore: SettingsStore,
    onStartVpn: () -> Unit,
    onStopVpn: () -> Unit,
) {
    var currentPage by rememberSaveable { mutableStateOf(MainPage.HOME.name) }
    var prewarmedPages by remember { mutableStateOf(setOf(MainPage.HOME)) }
    val isSubPage = currentPage != MainPage.HOME.name
    val currentMainPage = MainPage.valueOf(currentPage)
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val splitTunnelMode by settingsStore.splitTunnelMode.collectAsState(initial = SplitTunnelMode.ALL.value)

    LaunchedEffect(splitTunnelMode) {
        val warmedPages = mutableSetOf(MainPage.HOME, MainPage.SETTINGS, MainPage.LOGS)
        if (splitTunnelMode != SplitTunnelMode.ALL.value) {
            warmedPages += MainPage.APP_SELECTOR
        }
        prewarmedPages = prewarmedPages + warmedPages
    }

    LaunchedEffect(currentMainPage) {
        prewarmedPages = prewarmedPages + currentMainPage
    }

    BackHandler(enabled = isSubPage || drawerState.isOpen) {
        if (drawerState.isOpen) {
            scope.launch { drawerState.close() }
        } else {
            currentPage = MainPage.HOME.name
        }
    }

    val onNavigate: (MainPage) -> Unit = { targetPage ->
        scope.launch {
            drawerState.close()
            currentPage = targetPage.name
        }
    }

    val onMenuClick: () -> Unit = {
        scope.launch { drawerState.open() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawerContent(
                currentPage = currentMainPage,
                onNavigate = onNavigate,
            )
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (MainPage.HOME in prewarmedPages) {
                AppPageLayer(visible = currentMainPage == MainPage.HOME) {
                    VpnControlScreen(
                        settingsStore = settingsStore,
                        onStartVpn = onStartVpn,
                        onStopVpn = onStopVpn,
                        onMenuClick = onMenuClick,
                    )
                }
            }

            if (MainPage.SETTINGS in prewarmedPages) {
                AppPageLayer(visible = currentMainPage == MainPage.SETTINGS) {
                    SettingsScreen(
                        settingsStore = settingsStore,
                        onMenuClick = onMenuClick,
                    )
                }
            }

            if (MainPage.APP_SELECTOR in prewarmedPages) {
                AppPageLayer(visible = currentMainPage == MainPage.APP_SELECTOR) {
                    AppSelectorScreen(
                        settingsStore = settingsStore,
                        onMenuClick = onMenuClick,
                        shouldLoadApps = splitTunnelMode != SplitTunnelMode.ALL.value,
                    )
                }
            }

            if (MainPage.LOGS in prewarmedPages) {
                AppPageLayer(visible = currentMainPage == MainPage.LOGS) {
                    LogsScreen(
                        onMenuClick = onMenuClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun AppPageLayer(
    visible: Boolean,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(if (visible) 1f else 0f)
            .zIndex(if (visible) 1f else 0f)
    ) {
        content()
    }
}
