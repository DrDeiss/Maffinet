package io.maffinet.android.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.maffinet.android.MainActivity
import io.maffinet.android.core.connection.ConnectionCoordinator
import io.maffinet.android.core.debug.AppDebugManager as Log

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

        val isBoot = intent.action == Intent.ACTION_BOOT_COMPLETED ||
                intent.action == Intent.ACTION_REBOOT ||
                intent.action == "android.intent.action.QUICKBOOT_POWERON"

        try { ConnectionCoordinator.recover(appCtx, boot = isBoot) }
        catch (error: Exception) { Log.e("BootReceiver", "Mode recovery failed", error) }
    }
}
