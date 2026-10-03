package io.maffinet.android.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import io.maffinet.android.MainActivity
import io.maffinet.android.core.dpibypass.ServiceManager
import io.maffinet.android.core.tgproxy.TgProxyController
import io.maffinet.android.data.Mode

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val appCtx = context.applicationContext

        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val mainIntent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("showUpdateInstalledMessage", true)
            }
            context.startActivity(mainIntent)
            return
        }

        val prefs = appCtx.getSharedPreferences(appCtx.packageName + "_preferences", Context.MODE_PRIVATE)
        val serviceEnabled = prefs.getBoolean("service_enabled", false)
        val autostart = prefs.getBoolean("autostart", false)

        val isBoot = intent.action == Intent.ACTION_BOOT_COMPLETED ||
                intent.action == Intent.ACTION_REBOOT ||
                intent.action == "android.intent.action.QUICKBOOT_POWERON"

        if (serviceEnabled || (isBoot && autostart)) {
            val wantsYoutube = prefs.getBoolean("wants_youtube_bypass", true)
            val wantsTelegram = prefs.getBoolean("telegram_proxy_enabled_by_user", true)
            if (wantsYoutube && VpnService.prepare(appCtx) == null) {
                ServiceManager.start(appCtx, Mode.VPN)
            }
            if (wantsTelegram) {
                if (wantsYoutube) {
                    TgProxyController.startAsync(appCtx, {}, {})
                } else {
                    val intent = Intent(appCtx, io.maffinet.android.core.tgproxy.TgProxyService::class.java).apply {
                        action = io.maffinet.android.data.START_ACTION
                        putExtra("open_tg", false)
                    }
                    androidx.core.content.ContextCompat.startForegroundService(appCtx, intent)
                }
            }
            WatchdogWorker.schedulePeriodicWork(appCtx)
        }
    }
}
