package org.ioslike.ui.theme

import android.content.Context
import android.view.WindowInsets
import android.view.WindowManager
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Status-bar / cutout height and gesture-nav height in dp. Every screen reads this instead of assuming a phone size.
 * Defaults come from the device profile (Tokens.Space.defaultTop / defaultBottom) and are replaced by real values at runtime.
 */
data class SafeInsets(val top: Dp, val bottom: Dp)

val LocalSafeInsets = staticCompositionLocalOf { SafeInsets(Tokens.Space.defaultTop, Tokens.Space.defaultBottom) }

private fun Context.sysDimenPx(name: String): Int {
    val id = resources.getIdentifier(name, "dimen", "android")
    return if (id > 0) resources.getDimensionPixelSize(id) else 0
}

/** Window insets when available, else the framework's own status_bar_height / navigation_bar_gesture_height, else the profile defaults. */
fun Context.readSafeInsets(): SafeInsets {
    val d = resources.displayMetrics.density
    var top = 0
    var bottom = 0
    try {
        val ins = getSystemService(WindowManager::class.java).currentWindowMetrics.windowInsets
        top = ins.getInsetsIgnoringVisibility(WindowInsets.Type.statusBars() or WindowInsets.Type.displayCutout()).top
        bottom = ins.getInsetsIgnoringVisibility(WindowInsets.Type.navigationBars()).bottom
    } catch (_: Exception) {}
    if (top == 0) top = sysDimenPx("status_bar_height")
    if (bottom == 0) bottom = sysDimenPx("navigation_bar_gesture_height")
    val t = if (top > 0) (top / d).dp else Tokens.Space.defaultTop
    val b = if (bottom > 0) (bottom / d).dp else Tokens.Space.defaultBottom
    return SafeInsets(t, b)
}
