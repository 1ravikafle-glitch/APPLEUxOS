package com.appleux.launcher.ui

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.appleux.launcher.R

/**
 * iOS 27 icon pack: stock icons are deleted from the home surface — every
 * mapped package renders vendored iOS art (Solid-Glass 264px + Delta),
 * everything else falls back to the squircle software tile. Sources:
 * design-reference/Solid-Glass (Light), Delta-Icons-ios (Delta Icons).
 */
object AppleuxIconPack {
    /** package substring -> drawable, first match wins (order matters). */
    private val MAP = listOf(
        "youtube.music" to R.drawable.ios_ytmusic,
        "youtube" to R.drawable.ios_youtube,
        "dialer" to R.drawable.ios_phone,
        "messaging" to R.drawable.ios_messages,
        "mms" to R.drawable.ios_messages,
        "chrome" to R.drawable.ios_browser,
        "browser" to R.drawable.ios_browser,
        "camera" to R.drawable.ios_camera,
        "photos" to R.drawable.ios_photos,
        "gallery" to R.drawable.ios_photos,
        "calendar" to R.drawable.ios_calendar,
        "contacts" to R.drawable.ios_contacts,
        "documentsui" to R.drawable.ios_files,
        "myfiles" to R.drawable.ios_files,
        "gm" to R.drawable.ios_mail,
        "mail" to R.drawable.ios_mail,
        "maps" to R.drawable.ios_maps,
        "drive" to R.drawable.ios_drive,
        "docs" to R.drawable.ios_drive,
        "quicksearchbox" to R.drawable.ios_google,
        "deskclock" to R.drawable.ios_clock,
        "clock" to R.drawable.ios_clock,
        "settings" to R.drawable.ios_settings
    )

    /** Hand-drawn iOS tile kind: 0 none, 1 YouTube, 2 Safety. */
    fun drawnKind(packageName: String): Int {
        val pkg = packageName.lowercase()
        if (pkg.startsWith("com.appleux.")) return 0
        if (pkg.contains("youtube")) return 1
        if (pkg.contains("safetyhub") || pkg.contains("safety")) return 2
        return 0
    }

    /** Vendored iOS art for [packageName], or null (caller falls back). */
    fun resolve(context: Context, packageName: String): Drawable? {
        val pkg = packageName.lowercase()
        // com.appleux.* own apps keep their glyph tiles, not Safari clones.
        if (pkg.startsWith("com.appleux.")) return null
        for ((sub, res) in MAP) {
            if (pkg.contains(sub)) {
                return runCatching { ContextCompat.getDrawable(context, res) }.getOrNull()
            }
        }
        return null
    }
}
