package com.appleux.systemui

import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService

/**
 * Quick Settings entry point for the AppleUX notification shade.
 * (Companion to AppleuxControlTileService for Control Center.)
 */
class AppleuxShadeTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.let { tile ->
            tile.label = "Alerts"
            tile.contentDescription = "AppleUX Notifications"
            tile.state = android.service.quicksettings.Tile.STATE_INACTIVE
            try {
                tile.icon = android.graphics.drawable.Icon.createWithResource(
                    this, com.appleux.design.R.drawable.sf_bell
                )
            } catch (e: Exception) {}
            tile.updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        unlockAndRun {
            val intent = Intent(ACTION_TOGGLE).apply {
                component = android.content.ComponentName(
                    packageName,
                    "com.appleux.systemui.AppleuxSystemUIService"
                )
            }
            try {
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent)
                else @Suppress("DEPRECATION") startService(intent)
            } catch (e: Exception) {
                android.util.Log.w(TAG, "cannot toggle shade: ${e.message}")
            }
        }
    }

    companion object {
        private const val TAG = "AppleuxQSTileShade"
        const val ACTION_TOGGLE = "com.appleux.systemui.TOGGLE_NOTIFICATIONS"
    }
}
