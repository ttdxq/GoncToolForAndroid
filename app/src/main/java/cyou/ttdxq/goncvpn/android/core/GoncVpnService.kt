package cyou.ttdxq.goncvpn.android.core

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import cyou.ttdxq.goncvpn.android.R
import cyou.ttdxq.goncvpn.android.BuildConfig
import cyou.ttdxq.goncvpn.android.ui.MainActivity
import cyou.ttdxq.goncvpn.android.data.LogRepository
import cyou.ttdxq.goncvpn.android.data.LogLevel
import cyou.ttdxq.goncvpn.android.data.VpnStatus
import cyou.ttdxq.goncvpn.android.data.VpnState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import gobridge.Gobridge
import gobridge.Logger
import java.lang.reflect.Proxy

class GoncVpnService : VpnService() {
    companion object {
        const val ACTION_START = "cyou.ttdxq.goncvpn.android.START"
        const val ACTION_STOP = "cyou.ttdxq.goncvpn.android.STOP"
        const val EXTRA_P2P_SECRET = "p2p_secret"
        const val EXTRA_ROUTE_CIDRS = "route_cidrs" // Newline separated
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "gonc_vpn_channel"
        private const val TAG = "GoncVpnService"
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var vpnInterface: ParcelFileDescriptor? = null
    @Volatile private var goncThread: Thread? = null
    @Volatile private var stopInProgress = false
    @Volatile private var tunnelReady = false
    
    // JNI Logger implementation
    private val goLogger = object : Logger {
        override fun log(message: String?) {
            message?.let { LogRepository.log("GoJNI", it) }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Gobridge.setLogger(goLogger)
        registerGoStatusListener()
        VpnState.setStatus(VpnStatus.DISCONNECTED)
    }

    private fun registerGoStatusListener() {
        try {
            val listenerClass = Class.forName("gobridge.StatusListener")
            val proxy = Proxy.newProxyInstance(
                listenerClass.classLoader,
                arrayOf(listenerClass)
            ) { _, method, args ->
                if (method.name == "onStatusChanged" && args?.isNotEmpty() == true) {
                    val status = args[0] as? String
                    if (status == "connected") {
                        serviceScope.launch(Dispatchers.Main) {
                            if (!stopInProgress && vpnInterface != null) {
                                tunnelReady = true
                                updateNotification("Connected")
                                VpnState.setStatus(VpnStatus.CONNECTED)
                            }
                        }
                    }
                }
                null
            }
            val setStatusListener = Gobridge::class.java.getMethod("setStatusListener", listenerClass)
            setStatusListener.invoke(null, proxy)
            LogRepository.info(TAG, "Registered Go status listener")
        } catch (e: ReflectiveOperationException) {
            LogRepository.warn(TAG, "Go status listener unavailable in current AAR: ${e.message}")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        LogRepository.info(TAG, "onStartCommand received action: ${intent?.action}")
        when (intent?.action) {
            ACTION_START -> {
                startForeground(NOTIFICATION_ID, createNotification("Starting..."))
                val secret = intent.getStringExtra(EXTRA_P2P_SECRET) ?: ""
                val cidrs = intent.getStringExtra(EXTRA_ROUTE_CIDRS) ?: ""
                startVpn(secret, cidrs)
            }
            ACTION_STOP -> stopVpn()
            else -> {
                // Service被系统重启，恢复断开状态
                VpnState.setStatus(VpnStatus.DISCONNECTED)
            }
        }
        return START_STICKY
    }

    private fun startVpn(secret: String, cidrs: String) {
        if (secret.isBlank()) {
            VpnState.setError("P2P Secret不能为空")
            stopSelf()
            return
        }

        if (goncThread?.isAlive == true || stopInProgress) {
            LogRepository.warn(TAG, "VPN start ignored because another session is still active")
            return
        }

        tunnelReady = false
        
        VpnState.setStatus(VpnStatus.CONNECTING)

        serviceScope.launch(Dispatchers.IO) {
            try {
                // 1. Setup VPN Interface
                val builder = Builder()
                    .addAddress("10.0.0.2", 32)
                    .addAddress("fd00::2", 128)
                    .addDisallowedApplication(packageName)
                    .setMtu(1400)
                    .setSession("GoncVPN")

                cidrs.lines().filter { it.isNotBlank() }.forEach { cidr ->
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
                        Log.e(TAG, errorMsg, e)
                        VpnState.setError(errorMsg)
                        throw e
                    }
                }

                vpnInterface = builder.establish()
                if (vpnInterface == null) {
                    val errorMsg = "无法建立VPN接口，请检查VPN权限"
                    Log.e(TAG, errorMsg)
                    VpnState.setError(errorMsg)
                    stopSelf()
                    return@launch
                }

                val fd = vpnInterface!!.fd
                LogRepository.info(TAG, "VPN interface established. FD: $fd")

                // 2. Start Tun2Socks (Non-blocking usually, but allows packet processing)
                // We use socks5://127.0.0.1:1080 as the proxy target for tun2socks
                // Logic: tun2socks reads form TUN -> forwards to SOCKS5 (gonc)
                // Go 'int' maps to Java 'long', so we must cast fd and mtu.
                val tunLogLevel = resolveTun2SocksLogLevel()
                Gobridge.startTun2Socks(fd.toLong(), "socks5://127.0.0.1:1080", "tun0", 1400L, tunLogLevel)
                LogRepository.info(TAG, "Tun2Socks started with log level: $tunLogLevel")

                // 3. Start Gonc (Blocking, so run in separate thread)
                // Args: -p2p <SECRET> -link 1080;none
                val goncArgs = "-p2p $secret -link 1080;none"
                val worker = Thread {
                    LogRepository.info(TAG, "Starting Gonc...")
                    try {
                        Gobridge.startGonc(goncArgs)
                    } catch (e: Exception) {
                        LogRepository.error(TAG, "Gonc exited with error: ${e.message}")
                    }
                    LogRepository.info(TAG, "Gonc thread exited")
                    val wasReady = tunnelReady
                    if (goncThread === Thread.currentThread()) {
                        goncThread = null
                    }
                    if (!stopInProgress) {
                        serviceScope.launch(Dispatchers.Main) {
                            if (!stopInProgress && vpnInterface != null) {
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
        if (stopInProgress) {
            LogRepository.warn(TAG, "Stop already in progress")
            return
        }
        stopInProgress = true
        tunnelReady = false
        VpnState.setStatus(VpnStatus.STOPPING)
        
        serviceScope.launch(Dispatchers.IO) {
            try {
                try {
                    updateNotification("Stopping...")
                    Gobridge.stopGonc()
                } catch (e: Exception) {
                    Log.e(TAG, "Error stopping gonc", e)
                }

                try {
                    goncThread?.join(1500)
                } catch (e: Exception) {
                    Log.e(TAG, "Error waiting for gonc thread", e)
                }
                goncThread = null

                try {
                    vpnInterface?.close()
                } catch (e: Exception) {
                    Log.e(TAG, "Error closing VPN interface", e)
                }
                vpnInterface = null

                try {
                    Gobridge.stopTun2Socks()
                } catch (e: Exception) {
                    Log.e(TAG, "Error stopping tun2socks", e)
                }
                
                stopForeground(true)
                stopSelf()
                
                // 延迟更新状态，确保通知消失后再更新
                VpnState.setStatus(VpnStatus.DISCONNECTED)
            } finally {
                stopInProgress = false
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        // Ensure cleanup
        try {
            tunnelReady = false
            VpnState.setStatus(VpnStatus.STOPPING)
            Gobridge.stopGonc()
            goncThread?.join(1500)
            Gobridge.stopTun2Socks()
            vpnInterface?.close()
        } catch(_: Exception){}
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
            .setContentTitle("Gonc VPN")
            .setContentText(status)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
    
    private fun updateNotification(status: String) {
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
