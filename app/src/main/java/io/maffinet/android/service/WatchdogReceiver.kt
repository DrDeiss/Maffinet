package io.maffinet.android.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import io.maffinet.android.core.debug.AppDebugManager as Log
import io.maffinet.android.core.connection.ConnectionCoordinator
import io.maffinet.android.data.settings.MaffinetSettingsRepository

class WatchdogReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val appCtx = context.applicationContext
        val prefs = appCtx.getSharedPreferences(appCtx.packageName + "_preferences", Context.MODE_PRIVATE)

        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            prefs.edit().putBoolean("show_update_installed_message", true).apply()
        }

        val isBoot = intent.action == Intent.ACTION_BOOT_COMPLETED ||
                intent.action == Intent.ACTION_REBOOT ||
                intent.action == "android.intent.action.QUICKBOOT_POWERON"

        try { ConnectionCoordinator.recover(appCtx, boot = isBoot) }
        catch (error: Exception) { Log.e("WatchdogReceiver", "Mode recovery could not start", error) }

        if (intent.action == "io.maffinet.android.action.WATCHDOG_PING" &&
            MaffinetSettingsRepository(appCtx).anyModeRequested() && !prefs.getBoolean("econom_mode", false)) {
            scheduleWatchdogAlarm(appCtx)
        }
    }

    companion object {
        fun scheduleWatchdogAlarm(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, WatchdogReceiver::class.java).apply {
                action = "io.maffinet.android.action.WATCHDOG_PING"
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                1001,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val triggerAtMillis = System.currentTimeMillis() + 5 * 60 * 1000
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
        }

        fun cancelWatchdogAlarm(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, WatchdogReceiver::class.java).apply {
                action = "io.maffinet.android.action.WATCHDOG_PING"
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                1001,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        }
    }
}
