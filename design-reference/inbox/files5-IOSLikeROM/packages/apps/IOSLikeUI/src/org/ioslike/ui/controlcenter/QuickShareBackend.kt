package org.ioslike.ui.controlcenter

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent

/**
 * Quick Share replaces AirDrop. It is a Google Play Services feature, so it exists only if your ROM ships GMS.
 * Intents (from Google's open "nearby" docs, verify on your build):
 *   com.google.android.gms.SHARE_NEARBY      - presence check
 *   com.google.android.gms.RECEIVE_NEARBY    - receive / visibility UI (any MIME type)
 *   com.google.android.gms.settings.SHARING  - Quick Share settings
 * Sending is done through the normal ACTION_SEND share sheet. Without GMS, plug in your own NearbyShareBackend.
 */
class QuickShareBackend(private val ctx: Context) : NearbyShareBackend {
    override val available: Boolean
        get() = ctx.packageManager.queryIntentActivities(Intent("com.google.android.gms.SHARE_NEARBY"), 0).isNotEmpty()

    // Visibility state is not exposed by a public API, so the tile is a launcher, not a switch.
    override fun isEnabled() = false
    override fun setEnabled(on: Boolean) { openUi() }

    override fun openUi() {
        val receive = Intent("com.google.android.gms.RECEIVE_NEARBY").setType("*/*")
        val settings = Intent("com.google.android.gms.settings.SHARING")
        val bluetooth = Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)   // no Google services: fall back to Bluetooth sharing
        for (i in listOf(receive, settings, bluetooth)) {
            try { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return } catch (_: ActivityNotFoundException) {}
        }
    }
}
