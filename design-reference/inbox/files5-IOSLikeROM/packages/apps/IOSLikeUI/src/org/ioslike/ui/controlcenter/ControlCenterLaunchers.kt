package org.ioslike.ui.controlcenter

import android.content.Intent
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings

private fun ControlCenterController.go(i: Intent) {
    try { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
}

fun ControlCenterController.openTimer() = go(Intent(AlarmClock.ACTION_SHOW_TIMERS))
fun ControlCenterController.openCalculator() =
    go(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALCULATOR))
fun ControlCenterController.openCamera() = go(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
fun ControlCenterController.openCast() = go(Intent(Settings.ACTION_CAST_SETTINGS))
fun ControlCenterController.openHotspot() = try {
    go(Intent("android.settings.TETHER_SETTINGS"))
} catch (_: Exception) { go(Intent(Settings.ACTION_WIRELESS_SETTINGS)) }
