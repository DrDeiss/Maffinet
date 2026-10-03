package io.maffinet.android.core.dpibypass


import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.VpnService.SERVICE_INTERFACE
import android.os.Build
import android.os.ParcelFileDescriptor
import io.maffinet.android.core.debug.AppDebugManager as Log
import androidx.lifecycle.lifecycleScope
import io.maffinet.android.MainActivity
import io.maffinet.android.R
import io.maffinet.android.data.AppStatus
import io.maffinet.android.data.Mode
import io.maffinet.android.data.ServiceStatus
import io.maffinet.android.data.Sender
import io.maffinet.android.data.START_ACTION
import io.maffinet.android.data.STOP_ACTION
import io.maffinet.android.data.RESUME_ACTION
import io.maffinet.android.data.PAUSE_ACTION
import io.maffinet.android.data.STARTED_BROADCAST
import io.maffinet.android.data.STOPPED_BROADCAST
import io.maffinet.android.data.FAILED_BROADCAST
import io.maffinet.android.data.SENDER
import io.maffinet.android.data.setStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.NonCancellable
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import java.io.File
import io.maffinet.android.core.services.ApplicationRouting
import io.maffinet.android.core.services.ServiceCatalog
import io.maffinet.android.data.settings.MaffinetSettingsRepository

class ByeDpiVpnService : LifecycleVpnService() {
    private val byeDpiProxy = ByeDpiProxy()
    @Volatile private var proxyThread: Thread? = null
    private var proxyStopping = AtomicBoolean(false)
    private var tunFd: ParcelFileDescriptor? = null
    private var tunConfig: File? = null
    private val mutex = Mutex()
    private var wakeLock = null as android.os.PowerManager.WakeLock?
    private var networkCallback: android.net.ConnectivityManager.NetworkCallback? = null
    private val isRunning: Boolean
        get() = status == ServiceStatus.Connected

    companion object {
        private val TAG: String = ByeDpiVpnService::class.java.simpleName
        private const val FOREGROUND_SERVICE_ID: Int = 1
        private const val PAUSE_NOTIFICATION_ID: Int = 3
        private const val NOTIFICATION_CHANNEL_ID: String = "ByeDPIVpn"
        @Volatile private var status: ServiceStatus = ServiceStatus.Disconnected
        @Volatile private var nativeProxyActive = false
        @Volatile private var tunnelActive = false
        @Volatile private var starting = false
        val isVpnActive: Boolean get() = status == ServiceStatus.Connected
        /** Remains true until the native worker actually exits and the TUN closes. */
        val hasProxyResources: Boolean get() = starting || nativeProxyActive || tunnelActive
    }

    private fun acquireWakeLock() {
    }

    private fun releaseWakeLock() {
    }

    override fun onCreate() {
        super.onCreate()
        registerNotificationChannel(
            this,
            NOTIFICATION_CHANNEL_ID,
            R.string.vpn_channel_name
        )
        val connectivityManager = getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val request = android.net.NetworkRequest.Builder()
            .addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()
        val callback = object : android.net.ConnectivityManager.NetworkCallback() {
            private var lastNetwork: android.net.Network? = null
            private var networkLost = false
            private val availableNetworks = linkedSetOf<android.net.Network>()
            override fun onAvailable(network: android.net.Network) {
                super.onAvailable(network)
                availableNetworks.add(network)
                if (networkLost || (lastNetwork != null && lastNetwork != network)) {
                    if (isRunning) {
                        val recoveryRequest = ServiceManager.currentStartRequest()
                        lifecycleScope.launch {
                            mutex.withLock {
                                if (!isRunning || !recoveryIsCurrent(recoveryRequest)) return@withLock
                                starting = true
                                try {
                                    updateStatus(ServiceStatus.Disconnected)
                                    stopTun2Socks()
                                    stopProxy()
                                    if (!recoveryIsCurrent(recoveryRequest)) return@withLock
                                    startProxy()
                                    if (!recoveryIsCurrent(recoveryRequest)) {
                                        cleanupPipeline()
                                        return@withLock
                                    }
                                    startTun2Socks()
                                    updateStatus(ServiceStatus.Connected)
                                } catch (e: Exception) {
                                    Log.e(TAG, "Watchdog reconnect failed", e)
                                    cleanupPipeline()
                                    updateStatus(ServiceStatus.Failed)
                                    stopSelf()
                                } finally {
                                    starting = false
                                }
                            }
                        }
                    }
                }
                lastNetwork = network
                networkLost = false
            }
            override fun onLost(network: android.net.Network) {
                super.onLost(network)
                availableNetworks.remove(network)
                if (network == lastNetwork || isRunning) {
                    // Retain the baseline so the next physical network reconnects.
                    networkLost = true
                    // Mobile can already be available before Wi-Fi is lost.
                    availableNetworks.firstOrNull()?.let { onAvailable(it) }
                }
            }
        }
        networkCallback = callback
        try {
            connectivityManager.registerNetworkCallback(request, callback)
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        networkCallback?.let {
            val connectivityManager = getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            try {
                connectivityManager.unregisterNetworkCallback(it)
            } catch (_: Exception) {}
        }
        // Native JNI blocks outside lifecycleScope; canceling the scope cannot stop it.
        // Always close both transports, including partially failed startup.
        val failed = status == ServiceStatus.Failed
        stopTun2Socks()
        stopProxyBlocking()
        updateStatus(if (failed) ServiceStatus.Failed else ServiceStatus.Disconnected)
        releaseWakeLock()
        val prefs = getSharedPreferences(packageName + "_preferences", android.content.Context.MODE_PRIVATE)
        if (!failed && !hasProxyResources && ServiceManager.canStartVpn() && prefs.getBoolean("service_enabled", false)) {
            val intent = Intent(this, io.maffinet.android.service.WatchdogReceiver::class.java).apply {
                action = "io.maffinet.android.action.RESTART_SERVICE"
            }
            sendBroadcast(intent)
        }
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        startForeground()

        return when (val action = intent?.action) {
            START_ACTION -> {
                lifecycleScope.launch {
                    if (ServiceManager.isStartRequestCurrent(intent) && ServiceManager.canStartVpn()) start(intent)
                }
                START_STICKY
            }

            STOP_ACTION -> {
                if (!ServiceManager.isStopRequestCurrent(intent)) return START_NOT_STICKY
                if (intent?.getBooleanExtra(ServiceManager.EXTRA_KEEP_DESIRED_STATE, false) != true &&
                    !ServiceManager.handleUserStopIntent(this, intent)) {
                    return START_NOT_STICKY
                }
                lifecycleScope.launch {
                    stop(intent, startId)
                }
                START_NOT_STICKY
            }

            RESUME_ACTION -> {
                getPreferences().edit().putBoolean("service_enabled", true).apply()
                ServiceManager.onVpnResumed()
                val resumeRequest = ServiceManager.currentStartRequest()
                lifecycleScope.launch {
                    if (ServiceManager.canStartVpn() && prepare(this@ByeDpiVpnService) == null) {
                        start(resumeRequest)
                    }
                }
                START_STICKY
            }

            PAUSE_ACTION -> {
                ServiceManager.onVpnPaused()
                lifecycleScope.launch {
                    pause()
                }
                START_STICKY
            }

            SERVICE_INTERFACE -> {
                Log.i(TAG, "Started by Android")
                ServiceManager.start(this, Mode.VPN)

                START_STICKY
            }

            null -> {
                val desired = getPreferences().getBoolean("service_enabled", false)
                if (desired && ServiceManager.canStartVpn() && prepare(this) == null) {
                    val recoveryRequest = ServiceManager.currentStartRequest()
                    lifecycleScope.launch { start(recoveryRequest) }
                    START_STICKY
                } else {
                    stopSelf()
                    START_NOT_STICKY
                }
            }
            else -> {
                Log.w(TAG, "Unknown action: $action")
                START_NOT_STICKY
            }
        }
    }

    override fun onRevoke() {
        Log.i(TAG, "VPN revoked")
        ServiceManager.onUserStop(this)
        lifecycleScope.launch { stop() }
    }

    private fun recoveryIsCurrent(request: Intent): Boolean =
        ServiceManager.canStartVpn() && ServiceManager.isStartRequestCurrent(request) &&
            getPreferences().getBoolean("service_enabled", false)

    private suspend fun start(request: Intent?) {
        Log.i(TAG, "Starting")

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(PAUSE_NOTIFICATION_ID)

        mutex.withLock {
          if (!ServiceManager.canStartVpn() || !ServiceManager.isStartRequestCurrent(request) || status == ServiceStatus.Connected) return@withLock
          starting = true
          try {
            val prefs = getSharedPreferences(packageName + "_preferences", android.content.Context.MODE_PRIVATE)
            val economMode = prefs.getBoolean("econom_mode", false)
            if (!economMode) {
                acquireWakeLock()
                io.maffinet.android.service.WatchdogWorker.schedulePeriodicWork(this)
            }
            startProxy()
            if (!ServiceManager.canStartVpn() || !ServiceManager.isStartRequestCurrent(request)) {
                cleanupPipeline()
                return@withLock
            }
            startTun2Socks()
            updateStatus(ServiceStatus.Connected)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start VPN", e)
            cleanupPipeline()
            releaseWakeLock()
            io.maffinet.android.service.WatchdogWorker.cancelPeriodicWork(this)
            updateStatus(ServiceStatus.Failed)
            @Suppress("DEPRECATION")
            stopForeground(true)
            stopSelf()
          } finally {
            starting = false
          }
        }
    }

    private fun startForeground() {
        val notification: Notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                FOREGROUND_SERVICE_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(FOREGROUND_SERVICE_ID, notification)
        }
    }

    private suspend fun pause() {
        Log.i(TAG, "Pausing")

        releaseWakeLock()
        // The shared watchdog may still be needed by the independent Telegram proxy.

        mutex.withLock {
            cleanupPipeline()
            updateStatus(ServiceStatus.Disconnected)
        }

        val pausedNotification = createPauseNotification(
            this,
            NOTIFICATION_CHANNEL_ID,
            R.string.notification_title,
            R.string.service_paused_text,
            ByeDpiVpnService::class.java
        )
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(FOREGROUND_SERVICE_ID, pausedNotification)
    }

    private suspend fun stop(request: Intent? = null, commandStartId: Int? = null) {
        Log.i(TAG, "Stopping")

        releaseWakeLock()
        if (!getPreferences().getBoolean("service_enabled", false)) {
            io.maffinet.android.service.WatchdogWorker.cancelPeriodicWork(this)
        }

        mutex.withLock {
            // A newer START can arrive while an earlier STOP waits for startup.
            if (!ServiceManager.isStopRequestCurrent(request)) return
            cleanupPipeline()
            updateStatus(ServiceStatus.Disconnected)
        }
        if (!ServiceManager.isStopRequestCurrent(request)) return
        if (commandStartId != null && !stopSelfResult(commandStartId)) return
        @Suppress("DEPRECATION")
        stopForeground(true)
        if (commandStartId == null) stopSelf()
    }

    private suspend fun cleanupPipeline() = withContext(NonCancellable + Dispatchers.IO) {
        stopTun2Socks()
        stopProxyBlocking()
    }

    private suspend fun startProxy() {
        Log.i(TAG, "Starting proxy")

        check(proxyThread?.isAlive != true && !nativeProxyActive) { "Previous proxy is still stopping" }

        val preferences = getByeDpiPreferences()
        val (configuredIp, port) = getPreferences().getProxyIpAndPort()
        val listenerIp = when (configuredIp) {
            "0.0.0.0" -> "127.0.0.1"
            "::" -> "::1"
            else -> configuredIp
        }
        withContext(Dispatchers.IO) {
            val occupied = try {
                Socket().use { it.connect(InetSocketAddress(listenerIp, port.toInt()), 100) }
                true
            } catch (_: java.io.IOException) { false }
            check(!occupied) { "The configured SOCKS port is already occupied" }
        }

        val stopping = AtomicBoolean(false)
        proxyStopping = stopping
        nativeProxyActive = true
        val worker = Thread({
            try {
                val code = byeDpiProxy.startProxy(preferences)
                if (!stopping.get()) Log.e(TAG, "Proxy exited unexpectedly with code $code")
            } catch (e: Exception) {
                Log.e(TAG, "Native proxy failed", e)
            } finally {
                nativeProxyActive = false
                if (!stopping.get()) lifecycleScope.launch {
                    mutex.withLock {
                        if (!stopping.get() && isRunning) {
                            cleanupPipeline()
                            updateStatus(ServiceStatus.Failed)
                            stopSelf()
                        }
                    }
                }
            }
        }, "Maffinet-VPN-ByeDPI").apply { isDaemon = true }
        proxyThread = worker
        worker.start()

        withContext(Dispatchers.IO) {
            repeat(40) {
                check(worker.isAlive) { "ByeDPI exited before its SOCKS listener became ready" }
                try {
                    Socket().use { it.connect(InetSocketAddress(listenerIp, port.toInt()), 100) }
                    return@withContext
                } catch (_: java.io.IOException) {
                    delay(50)
                }
            }
            error("ByeDPI SOCKS listener did not become ready")
        }
        Log.i(TAG, "Proxy listener ready")
    }

    private suspend fun stopProxy() = withContext(NonCancellable + Dispatchers.IO) { stopProxyBlocking() }

    private fun stopProxyBlocking() {
        val worker = proxyThread ?: return
        proxyStopping.set(true)
        if (!worker.isAlive) {
            proxyThread = null
            nativeProxyActive = false
            return
        }
        try {
            byeDpiProxy.stopProxy()
            worker.join(2000)
            if (worker.isAlive) {
                byeDpiProxy.jniForceClose()
                worker.join(1000)
            }
            if (worker.isAlive) {
                // Retain the flag and worker: no second JNI singleton may start.
                Log.e(TAG, "Native proxy did not terminate; restart is blocked")
            } else {
                proxyThread = null
                nativeProxyActive = false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop native proxy", e)
        }
    }

    private fun startTun2Socks() {
        Log.i(TAG, "Starting tun2socks")

        if (tunFd != null) {
            Log.w(TAG, "VPN field not null")
            throw IllegalStateException("VPN field not null")
        }

        val sharedPreferences = getPreferences()
        val (ip, port) = sharedPreferences.getProxyIpAndPort()

        val customDnsPreset = sharedPreferences.getString("custom_dns_preset", "Стандартный (Отключено)") ?: "Стандартный (Отключено)"
        val dnsIps: List<String> = when (customDnsPreset) {
            "Cloudflare Secure DNS" -> listOf("1.1.1.1", "1.0.0.1")
            "Google Public DNS" -> listOf("8.8.8.8", "8.8.4.4")
            "AdGuard DNS (Блокировка рекламы)" -> listOf("94.140.14.14", "94.140.15.15")
            "Xbox DNS (xbox-dns.ru / ChatGPT / Brawl)" -> listOf("176.99.11.11", "176.99.11.22")
            "Supercell Xbox DNS (supercell.xbox-dns.ru)" -> listOf("176.99.11.11", "176.99.11.22")
            "NullsProxy DNS (dns.nullsproxy.com)" -> listOf("176.99.11.11", "176.99.11.22")
            "Comss.one DNS (dns.comss.one)" -> listOf("76.76.2.22", "76.76.10.22")
            "Geohide DNS (dns.geohide.ru)" -> listOf("176.99.11.11", "176.99.11.22")
            else -> emptyList()
        }
        val ipv6 = sharedPreferences.getBoolean("ipv6_enable", false)

        val tun2socksConfig = buildString {
            appendLine("tunnel:")
            appendLine("  mtu: 1500")

            appendLine("misc:")
            appendLine("  task-stack-size: 81920")

            appendLine("socks5:")
            appendLine("  address: $ip")
            appendLine("  port: $port")
            appendLine("  udp: udp")
        }

        val configPath = try {
            File.createTempFile("config", "tmp", cacheDir).apply {
                writeText(tun2socksConfig)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create config file", e)
            throw e
        }

        val isSmartTv = sharedPreferences.getBoolean("is_smart_tv", false)
        tunConfig = configPath

        val fd = createBuilder(dnsIps, ipv6, isSmartTv).establish()
            ?: throw IllegalStateException("VPN connection failed")

        this.tunFd = fd
        tunnelActive = true

        TProxyService.TProxyStartService(configPath.absolutePath, fd.fd, isSmartTv)

        Log.i(TAG, "Tun2Socks started. ip: $ip port: $port")
    }

    private fun stopTun2Socks() {
        Log.i(TAG, "Stopping tun2socks")

        try {
            if (tunFd != null) TProxyService.TProxyStopService()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop TProxyService", e)
        }

        try {
            tunConfig?.delete()
            tunConfig = null
        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to delete config file", e)
        }

        try {
            tunFd?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to close tunFd", e)
        } finally {
            tunFd = null
            tunnelActive = false
        }

        Log.i(TAG, "Tun2socks stopped")
    }

    private fun getByeDpiPreferences(): ByeDpiProxyPreferences =
        ByeDpiProxyPreferences.fromSharedPreferences(getPreferences(), this)

    private fun updateStatus(newStatus: ServiceStatus) {
        Log.d(TAG, "VPN status changed from $status to $newStatus")

        status = newStatus

        setStatus(
            when (newStatus) {
                ServiceStatus.Connected -> AppStatus.Running

                ServiceStatus.Disconnected,
                ServiceStatus.Failed -> {
                    AppStatus.Halted
                }
            },
            Mode.VPN
        )

        val intent = Intent(
            when (newStatus) {
                ServiceStatus.Connected -> STARTED_BROADCAST
                ServiceStatus.Disconnected -> STOPPED_BROADCAST
                ServiceStatus.Failed -> FAILED_BROADCAST
            }
        )
        intent.putExtra(SENDER, Sender.VPN.ordinal)
        sendBroadcast(intent)
    }

    private fun createNotification(): Notification =
        createConnectionNotification(
            this,
            NOTIFICATION_CHANNEL_ID,
            R.string.notification_title,
            R.string.vpn_notification_content,
            ByeDpiVpnService::class.java
        )

    private fun createNotificationPause() {
        val notification = createPauseNotification(
            this,
            NOTIFICATION_CHANNEL_ID,
            R.string.notification_title,
            R.string.service_paused_text,
            ByeDpiVpnService::class.java
        )

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(PAUSE_NOTIFICATION_ID, notification)
    }

    private fun createBuilder(dnsIps: List<String>, ipv6: Boolean, isSmartTv: Boolean): Builder {
        Log.d(TAG, "DNS: $dnsIps")
        val builder = Builder()
        val preferences = getPreferences()
        builder.setSession("Maffinet")
        builder.setConfigureIntent(
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE
            )
        )

        builder.addAddress("10.10.10.10", 32)
            .addRoute("0.0.0.0", 0)
            .setMtu(1500)

        if (ipv6) {
            builder.addAddress("fd00::1", 128)
                .addRoute("::", 0)
        } else if (isSmartTv) {
            try {
                builder.addAddress("fd00::1", 128)
                    .addRoute("::", 0)
            } catch (e: Exception) {
            }
        }

        dnsIps.forEach { ip ->
            builder.addDnsServer(ip)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
        }

        val settings = MaffinetSettingsRepository(this)
        val listedApps = ApplicationRouting.selectedPackages(
            ServiceCatalog.profiles, settings.enabledServiceIds(), settings.manualApplications(), packageName
        )
        val installed = listedApps.filterTo(linkedSetOf()) { candidate ->
            try {
                @Suppress("DEPRECATION")
                packageManager.getApplicationInfo(candidate, 0)
                true
            } catch (_: PackageManager.NameNotFoundException) {
                false
            }
        }
        val routed = ApplicationRouting.installedPackages(listedApps, installed, packageName)
        var allowedCount = 0
        for (packageName in routed) {
            try {
                builder.addAllowedApplication(packageName)
                allowedCount++
            } catch (e: Exception) {
                Log.e(TAG, "Failed to whitelist app $packageName", e)
            }
        }
        check(allowedCount > 0) { "No selected application could be routed through Maffinet" }

        return builder
    }
}
