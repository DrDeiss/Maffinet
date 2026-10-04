package io.maffinet.android.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.maffinet.android.core.connection.ConnectionCoordinator
import io.maffinet.android.core.debug.AppDebugManager as Log

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val appCtx = context.applicationContext

        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            // Package replacement permits foreground-service recovery, not an
            // unsolicited Activity launch. Show the update result on next open.
            appCtx.getSharedPreferences(appCtx.packageName + "_preferences", Context.MODE_PRIVATE)
                .edit().putBoolean("show_update_installed_message", true).apply()
        }

        val isBoot = intent.action == Intent.ACTION_BOOT_COMPLETED ||
                intent.action == Intent.ACTION_REBOOT ||
                intent.action == "android.intent.action.QUICKBOOT_POWERON"

        try { ConnectionCoordinator.recover(appCtx, boot = isBoot) }
        catch (error: Exception) { Log.e("BootReceiver", "Mode recovery failed", error) }
    }
}
