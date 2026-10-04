package io.maffinet.android.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller

class UpdateStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "io.maffinet.android.action.INSTALL_STATUS") {
            val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
            if (status == PackageInstaller.STATUS_SUCCESS) {
                // A broadcast cannot automatically bring an Activity to the
                // foreground on modern Android. Retain the result for next open.
                context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE)
                    .edit().putBoolean("show_update_installed_message", true).apply()
            }
        }
    }
}
