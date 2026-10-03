package io.maffinet.android.core.tgproxy

import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import io.maffinet.android.core.debug.AppDebugManager as Log
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import io.maffinet.android.R
import io.maffinet.android.core.dpibypass.createConnectionNotification
import io.maffinet.android.core.dpibypass.registerNotificationChannel
import io.maffinet.android.data.AppStatus
import io.maffinet.android.data.Mode
import io.maffinet.android.data.START_ACTION
import io.maffinet.android.data.STOP_ACTION
import io.maffinet.android.data.STARTED_BROADCAST
import io.maffinet.android.data.STOPPED_BROADCAST
import io.maffinet.android.data.FAILED_BROADCAST
import io.maffinet.android.data.SENDER
import io.maffinet.android.data.Sender
import io.maffinet.android.data.setStatus
import kotlinx.coroutines.launch

class TgProxyService : LifecycleService() {

    companion object {
        private val TAG = TgProxyService::class.java.simpleName
        private const val FOREGROUND_SERVICE_ID = 100
        private const val NOTIFICATION_CHANNEL_ID = "TgProxyServiceChannel"
    }

    private var wakeLock: android.os.PowerManager.WakeLock? = null

    private fun acquireWakeLock() {
        val prefs = getSharedPreferences(packageName + "_preferences", MODE_PRIVATE)
        if (!prefs.getBoolean("tg_proxy_wakelock_enabled", false)) return
        if (wakeLock == null) {
            val powerManager = getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
            wakeLock = powerManager.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "Maffinet::TgProxyWakeLock").apply {
                acquire()
            }
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {
        } finally {
            wakeLock = null
        }
    }

    override fun onCreate() {
        super.onCreate()
        registerNotificationChannel(
            this,
            NOTIFICATION_CHANNEL_ID,
            R.string.proxy_channel_name
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        val action = intent?.action
        if (action == START_ACTION) {
            Log.i(TAG, "Служба TgProxyService: получен запрос на запуск")
            acquireWakeLock()
            startForeground()
            val openTg = intent.getBooleanExtra("open_tg", false)
            lifecycleScope.launch {
                TgProxyController.startAsync(
                    context = this@TgProxyService,
                    onSuccess = {
                        Log.i(TAG, "Служба TgProxyService успешно запущена")
                        setStatus(AppStatus.Running, Mode.Proxy)
                        val broadcastIntent = Intent(STARTED_BROADCAST).apply {
                            putExtra(SENDER, Sender.Proxy.ordinal)
                        }
                        sendBroadcast(broadcastIntent)
                        
                        val prefs = getSharedPreferences(packageName + "_preferences", MODE_PRIVATE)
                        val alreadyConfigured = prefs.getBoolean("tg_proxy_configured", false)
                        if (openTg && !alreadyConfigured) {
                            val port = TgProxyController.getPort(this@TgProxyService)
                            val secret = TgProxyController.getOrGenerateSecret(this@TgProxyService)
                            val url = TgProxyController.getTgProxyUrl(
                                TgProxyController.DEFAULT_BIND_IP,
                                port,
                                secret
                            )
                            val tgIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            try {
                                startActivity(tgIntent)
                                prefs.edit().putBoolean("tg_proxy_configured", true).apply()
                            } catch (_: Exception) {}
                        }
                    },
                    onError = {
                        Log.e(TAG, "Служба TgProxyService завершилась с ошибкой запуска")
                        setStatus(AppStatus.Halted, Mode.Proxy)
                        val broadcastIntent = Intent(FAILED_BROADCAST).apply {
                            putExtra(SENDER, Sender.Proxy.ordinal)
                        }
                        sendBroadcast(broadcastIntent)
                        stopSelf()
                    }
                )
            }
            return START_STICKY
        } else if (action == STOP_ACTION) {
            Log.i(TAG, "Служба TgProxyService: получен запрос на остановку")
            releaseWakeLock()
            TgProxyController.stop()
            setStatus(AppStatus.Halted, Mode.Proxy)
            val broadcastIntent = Intent(STOPPED_BROADCAST).apply {
                putExtra(SENDER, Sender.Proxy.ordinal)
            }
            sendBroadcast(broadcastIntent)
            stopSelf()
            return START_NOT_STICKY
        }

        return START_NOT_STICKY
    }

    private fun startForeground() {
        val notification: Notification = createConnectionNotification(
            this,
            NOTIFICATION_CHANNEL_ID,
            R.string.notification_title,
            R.string.vpn_notification_content,
            TgProxyService::class.java
        )
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
}
