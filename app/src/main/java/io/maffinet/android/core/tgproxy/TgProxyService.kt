package io.maffinet.android.core.tgproxy

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import io.maffinet.android.R
import io.maffinet.android.core.connection.ConnectionCoordinator
import io.maffinet.android.core.connection.ModeConnectionState
import io.maffinet.android.core.dpibypass.createConnectionNotification
import io.maffinet.android.core.dpibypass.registerNotificationChannel
import io.maffinet.android.core.debug.AppDebugManager as Log
import io.maffinet.android.data.*
import io.maffinet.android.data.settings.MaffinetSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Telegram always owns its foreground service, including while VPN is also connected. */
class TgProxyService : LifecycleService() {
    companion object {
        private const val FOREGROUND_SERVICE_ID = 100
        private const val CHANNEL = "TgProxyServiceChannel"
        private const val GENERATION = "io.maffinet.android.telegram_generation"
        private var generation = 0L

        @Synchronized private fun isCurrent(intent: Intent?): Boolean =
            intent?.hasExtra(GENERATION) != true || intent.getLongExtra(GENERATION, -1L) == generation

        @Synchronized fun requestStart(context: Context, openTelegram: Boolean = false,
            preserveDesiredStateOnFailure: Boolean = false) {
            val app = context.applicationContext
            val settings = MaffinetSettingsRepository(app)
            if (!settings.telegramEnabled()) return
            settings.setTelegramRequested(true)
            if (TgProxyController.status.value in setOf(ModeConnectionState.Starting, ModeConnectionState.Running)) return
            val token = ++generation
            try {
                TgProxyController.markStartRequested()
                ContextCompat.startForegroundService(app, Intent(app, TgProxyService::class.java).apply {
                    action = START_ACTION
                    putExtra(GENERATION, token)
                    putExtra("open_tg", openTelegram)
                })
            } catch (error: Exception) {
                // Android 12+ may defer a background watchdog's FGS start. Keep
                // recovery requests so the visible Activity can resume them.
                if (!preserveDesiredStateOnFailure) settings.setTelegramRequested(false)
                TgProxyController.stop()
                throw error
            }
            ConnectionCoordinator.refreshWatchdog(app)
        }

        @Synchronized fun ensureStarted(context: Context) {
            val settings = MaffinetSettingsRepository(context)
            if (settings.telegramRequested() && settings.telegramEnabled() &&
                TgProxyController.status.value !in ConnectionCoordinator.activeStates)
                requestStart(context, preserveDesiredStateOnFailure = true)
        }

        @Synchronized fun requestStop(context: Context) {
            val app = context.applicationContext
            MaffinetSettingsRepository(app).setTelegramRequested(false)
            val token = ++generation
            // Cancel readiness even if a queued START has not reached the service yet.
            TgProxyController.stop()
            try {
                ContextCompat.startForegroundService(app, Intent(app, TgProxyService::class.java).apply {
                    action = STOP_ACTION
                    putExtra(GENERATION, token)
                })
            } catch (error: Exception) {
                Log.e("TgProxyService", "Telegram STOP dispatch failed; stopping service directly", error)
                app.stopService(Intent(app, TgProxyService::class.java))
            }
            ConnectionCoordinator.refreshWatchdog(app)
        }

        @Synchronized private fun onDirectStop(context: Context) {
            generation++
            MaffinetSettingsRepository(context).setTelegramRequested(false)
            ConnectionCoordinator.refreshWatchdog(context)
        }
    }

    private var wakeLock: android.os.PowerManager.WakeLock? = null
    private var lastRequest: Intent? = null

    override fun onCreate() {
        super.onCreate()
        registerNotificationChannel(this, CHANNEL, R.string.proxy_channel_name)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        startForeground()
        if (!isCurrent(intent)) {
            if (!MaffinetSettingsRepository(this).telegramRequested()) stopSelfResult(startId)
            return START_NOT_STICKY
        }
        if (intent?.action == STOP_ACTION && !intent.hasExtra(GENERATION)) onDirectStop(this)
        // Sticky recovery and notification actions also need an immutable token so
        // a later STOP/START can invalidate callbacks queued by this command.
        val request = intent?.takeIf { it.hasExtra(GENERATION) }
            ?: synchronized(Companion) { Intent(intent ?: Intent()).putExtra(GENERATION, generation) }
        lastRequest = request
        return when (intent?.action) {
            STOP_ACTION -> {
                releaseWakeLock()
                TgProxyController.stop {
                    lifecycleScope.launch(Dispatchers.Main) {
                        if (!isCurrent(request)) return@launch
                        publish(STOPPED_BROADCAST, AppStatus.Halted)
                        stopSelfResult(startId)
                    }
                }
                START_NOT_STICKY
            }
            START_ACTION, null -> {
                val settings = MaffinetSettingsRepository(this)
                if (!settings.telegramRequested() || !settings.telegramEnabled()) {
                    stopSelfResult(startId)
                    return START_NOT_STICKY
                }
                acquireWakeLock()
                TgProxyController.startAsync(this, onSuccess = {
                    lifecycleScope.launch(Dispatchers.Main) {
                        if (!isCurrent(request) || !MaffinetSettingsRepository(this@TgProxyService).telegramRequested()) return@launch
                        publish(STARTED_BROADCAST, AppStatus.Running)
                        if (intent?.getBooleanExtra("open_tg", false) == true) openTelegram()
                    }
                }, onError = {
                    lifecycleScope.launch(Dispatchers.Main) {
                        if (!isCurrent(request)) return@launch
                        publish(FAILED_BROADCAST, AppStatus.Halted)
                        releaseWakeLock()
                        stopSelfResult(startId)
                    }
                })
                START_STICKY
            }
            else -> { stopSelfResult(startId); START_NOT_STICKY }
        }
    }

    override fun onDestroy() {
        releaseWakeLock()
        // STOP already owns native cleanup. Allocating another STOP here would
        // invalidate its completion and briefly publish Stopping after Stopped.
        if (isCurrent(lastRequest) && (TgProxyController.status.value in
                setOf(ModeConnectionState.Starting, ModeConnectionState.Running) ||
                (TgProxyController.status.value == ModeConnectionState.Failed && TgProxyController.hasResources)))
            TgProxyController.stop(preserveFailure = true)
        super.onDestroy()
    }

    private fun publish(action: String, status: AppStatus) {
        isTgProxyRunningGlobal = status == AppStatus.Running
        if (status == AppStatus.Running) startForeground()
        else stopForeground(STOP_FOREGROUND_REMOVE)
        // Legacy appStatus is used by the VPN strategy wizard. Telegram has its own
        // StateFlow and sender broadcasts and must not overwrite the VPN status.
        // Android 14+ filters implicit intents to non-exported runtime receivers.
        sendBroadcast(Intent(action).setPackage(packageName).putExtra(SENDER, Sender.Proxy.ordinal))
    }

    private fun acquireWakeLock() {
        val prefs = getSharedPreferences(packageName + "_preferences", MODE_PRIVATE)
        if (prefs.getBoolean("tg_proxy_wakelock_enabled", false) && wakeLock == null) {
            val power = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            wakeLock = power.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "Maffinet::TgProxyWakeLock").apply { acquire() }
        }
    }
    private fun releaseWakeLock() {
        try { if (wakeLock?.isHeld == true) wakeLock?.release() }
        finally { wakeLock = null }
    }

    private fun openTelegram() {
        val prefs = getSharedPreferences(packageName + "_preferences", MODE_PRIVATE)
        if (prefs.getBoolean("tg_proxy_configured", false)) return
        val url = TgProxyController.getTgProxyUrl(TgProxyController.DEFAULT_BIND_IP,
            TgProxyController.getPort(this), TgProxyController.getOrGenerateSecret(this))
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            prefs.edit().putBoolean("tg_proxy_configured", true).apply()
        } catch (error: Exception) { Log.w("TgProxyService", "Telegram link could not open", error) }
    }

    private fun startForeground() {
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(FOREGROUND_SERVICE_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else startForeground(FOREGROUND_SERVICE_ID, notification)
    }

    private fun createNotification(): Notification = createConnectionNotification(this, CHANNEL,
        R.string.notification_title, R.string.vpn_notification_content, TgProxyService::class.java,
        allowPause = false,
        statusText = when (TgProxyController.status.value) {
            ModeConnectionState.Stopped -> "Telegram-прокси остановлен"
            ModeConnectionState.Starting -> "Telegram-прокси запускается"
            ModeConnectionState.Running -> "Telegram-прокси работает • ${TgProxyController.DEFAULT_BIND_IP}:${TgProxyController.getPort(this)}"
            ModeConnectionState.Stopping -> "Telegram-прокси останавливается"
            ModeConnectionState.Failed -> "Ошибка Telegram-прокси"
        })
}
