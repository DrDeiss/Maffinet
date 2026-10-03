package io.maffinet.android.service

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import io.maffinet.android.core.dpibypass.ServiceManager
import io.maffinet.android.core.tgproxy.TgProxyController
import io.maffinet.android.core.tgproxy.TgProxyService
import io.maffinet.android.data.Mode
import io.maffinet.android.data.STOP_ACTION
import io.maffinet.android.data.START_ACTION
import io.maffinet.android.MainActivity

class MaffinetTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val prefs = getSharedPreferences(packageName + "_preferences", Context.MODE_PRIVATE)
        val isEnabled = prefs.getBoolean("service_enabled", false)

        if (isEnabled) {
            prefs.edit().putBoolean("service_enabled", false).apply()
            ServiceManager.stop(this)
            
            val intent = Intent(this, TgProxyService::class.java).apply {
                action = STOP_ACTION
            }
            startService(intent)
            TgProxyController.stop()
            
            updateTileState()
        } else {
            val vpnPrepareIntent = VpnService.prepare(this)
            if (vpnPrepareIntent != null) {
                prefs.edit().putBoolean("service_enabled", true).apply()
                val startAppIntent = Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivityAndCollapse(startAppIntent)
            } else {
                prefs.edit().putBoolean("service_enabled", true).apply()
                val wantsYoutube = prefs.getBoolean("wants_youtube_bypass", true)
                val wantsTelegram = prefs.getBoolean("telegram_proxy_enabled_by_user", true)

                if (wantsYoutube) {
                    ServiceManager.start(this, Mode.VPN)
                }

                if (wantsTelegram) {
                    val intent = Intent(this, TgProxyService::class.java).apply {
                        action = START_ACTION
                        putExtra("open_tg", false)
                    }
                    try {
                        startForegroundService(intent)
                    } catch (_: Exception) {
                        startService(intent)
                    }
                }
                updateTileState()
            }
        }
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val prefs = getSharedPreferences(packageName + "_preferences", Context.MODE_PRIVATE)
        val isEnabled = prefs.getBoolean("service_enabled", false)

        if (isEnabled) {
            tile.state = Tile.STATE_ACTIVE
            tile.label = "Maffinet"
        } else {
            tile.state = Tile.STATE_INACTIVE
            tile.label = "Maffinet"
        }
        tile.updateTile()
    }
}
