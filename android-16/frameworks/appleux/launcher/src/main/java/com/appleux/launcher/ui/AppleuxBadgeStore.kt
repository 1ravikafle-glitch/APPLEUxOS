package com.appleux.launcher.ui

import android.content.Context

/**
 * Per-package unread counts for the home-screen icon badges.
 *
 * WHY A STORE: the notification listener that owns the real counts lives in
 * SystemUI, a different process from the launcher. This object is the seam —
 * the same cross-process pattern as AppleuxConfigProvider, minus the
 * provider: counts are written by a namespaced broadcast (SystemUI side)
 * and read here on rebind.
 *
 * The publish action is `com.appleux.launchui.BADGE_COUNTS` carrying the
 * package -> count map as extras (String pkg, Int count pairs under
 * `counts`). Until SystemUI's listener sends it, every count is 0 and the
 * badges simply do not render — an honest empty state, not a fake one.
 *
 * PROVENANCE: the badge visual is iOS (see AppIconView); the storage shape
 * (a tiny per-package int map) is ours.
 */
object AppleuxBadgeStore {

    private const val PREFS = "appleux_badges"
    private const val KEY_PREFIX = "count_"

    /** Current unread count for a package; 0 when nothing published. */
    fun countFor(context: Context, packageName: String): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_PREFIX + packageName, 0)

    /** Overwrite one package's count; 0 removes it. */
    fun setCount(context: Context, packageName: String, count: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_PREFIX + packageName, count.coerceAtLeast(0))
            .apply()
    }

    /** Clear all — used when notifications are dismissed globally. */
    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }
}
