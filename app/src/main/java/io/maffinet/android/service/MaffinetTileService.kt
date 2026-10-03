package io.maffinet.android.service

import android.content.Intent
import android.net.VpnService
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import io.maffinet.android.MainActivity
import io.maffinet.android.core.connection.ConnectionCoordinator
import io.maffinet.android.data.settings.MaffinetSettingsRepository

class MaffinetTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val settings = MaffinetSettingsRepository(this)
        if (settings.anyModeRequested() || ConnectionCoordinator.isConfigurationLocked()) {
            ConnectionCoordinator.stopAll(this)
        } else if (!settings.selectedModes().any ||
            (settings.applicationsEnabled() && VpnService.prepare(this) != null)) {
            // Consent is required only for Applications; Telegram-only starts immediately.
            startActivityAndCollapse(Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra("maffinet_connect_selected", true)
            })
        } else {
            val result = ConnectionCoordinator.startSelected(this)
            if (result.errors.isNotEmpty()) startActivityAndCollapse(Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        updateTileState()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        tile.state = if (MaffinetSettingsRepository(this).anyModeRequested() ||
            ConnectionCoordinator.isConfigurationLocked()) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "Maffinet"
        tile.updateTile()
    }
}