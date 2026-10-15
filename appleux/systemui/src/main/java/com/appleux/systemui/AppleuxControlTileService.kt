package com.appleux.systemui

import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService

/**
 * Quick Settings entry point for AppleUX Control Center.
 *
 * Top-edge swipe zones belong to the platform shade on stock system images,
 * so the in-activity gesture cannot reliably claim them. A QS tile is the
 * platform-sanctioned trigger: it works on any image, needs no privilege,
 * and the tap counts as user interaction (foreground-service start allowed).
 */
class AppleuxControlTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.let { tile ->
            tile.label = "AppleUX"
            tile.contentDescription = "AppleUX Control Center"
            tile.state = android.service.quicksettings.Tile.STATE_INACTIVE
            try {
                tile.icon = android.graphics.drawable.Icon.createWithResource(
                    this, com.appleux.design.R.drawable.sf_settings
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
                android.util.Log.w(TAG, "cannot toggle CC: ${e.message}")
            }
        }
    }

    companion object {
        private const val TAG = "AppleuxQSTile"
        const val ACTION_TOGGLE = "com.appleux.systemui.TOGGLE_CONTROL_CENTER"
    }
}
