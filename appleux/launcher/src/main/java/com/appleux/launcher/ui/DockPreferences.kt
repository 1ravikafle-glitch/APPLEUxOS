package com.appleux.launcher.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore

/**
 * The user's dock: four slots, persisted across restarts, never recomputed.
 *
 * ================================ WHY THIS EXISTS ==========================
 *
 * The dock used to be `apps.take(4)` off a list sorted by label. That is not
 * "first four alphabetically" by accident — it is first-four-alphabetically
 * *by construction*, so Phone/Messages/Chrome/Camera only appeared there on a
 * device that happened to have those apps and no A–C app. It also silently
 * reset on every launch, so there was nothing for the user to change even if
 * the UI had let them.
 *
 * ================================ RESOLUTION ===============================
 *
 * Defaults are resolved by EXPLICIT IDENTITY, never by position:
 *
 *  1. an explicit package-name allowlist per slot, in preference order;
 *  2. an exact, case-insensitive app-LABEL match;
 *  3. the platform's own "user's default app" resolution for that role
 *     (ACTION_DIAL / ACTION_VIEW https / ACTION_SENDTO smsto: / ACTION_VIEW
 *     audio), which is what IOSLikeROM's `AppRepo.dock()` does and is still
 *     not positional;
 *  4. only if all three fail, the first remaining app — and that is recorded
 *     so the choice is stable from then on.
 *
 * Once resolved, the package names are written to SharedPreferences and are
 * the single source of truth forever after. Resolution never runs again
 * unless the user explicitly resets, so an app being uninstalled later cannot
 * reshuffle the dock behind their back: the slot is emptied, not refilled.
 *
 * PROVENANCE: slot ORDER (dial, browser, message, music) and the music slot
 * are Apple-measured — SB_DefaultIconState~iphone.plist `buttonBar`. The
 * resolve-by-identity mechanism is prototype-derived (IOSLikeROM
 * `launcher/AppRepo.kt` `dock()`); the package allowlists and persistence
 * are ours.
 */
class DockPreferences(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * The dock as stored, resolving defaults on first run.
     *
     * @param allApps every launchable app, in any order; the resolver searches
     *   it by package and by label.
     * @return the dock slots, left-padded with nulls when a slot is empty
     *   because its app was uninstalled. An empty slot is a valid state and is
     *   rendered as a gap — silently back-filling it is what made the old dock
     *   move around.
     */
    fun resolve(allApps: List<AppIconItem>): List<AppIconItem?> {
        val stored = readStored()
        if (stored != null) {
            return stored.map { pkg -> allApps.firstOrNull { it.packageName == pkg } }
        }
        val defaults = resolveDefaults(allApps)
        write(defaults.map { it?.packageName })
        return defaults
    }

    /** Persists [dock]; a null entry stores an empty slot rather than a guess. */
    fun save(dock: List<AppIconItem?>) {
        write(dock.map { it?.packageName })
    }

    /** The raw stored package names, or null when defaults were never written. */
    private fun readStored(): List<String?>? {
        if (!prefs.contains(KEY_DOCK)) return null
        // A set cannot express "slot 2 is empty", so it is stored as an
        // ordered, delimiter-encoded string: order is the dock's order.
        return prefs.getString(KEY_DOCK, null)
            ?.split(SEP)
            ?.map { if (it == NONE) null else it }
    }

    private fun write(dock: List<String?>) {
        prefs.edit()
            .putString(KEY_DOCK, dock.joinToString(SEP) { it ?: NONE })
            .apply()
    }

    /** Clears the stored dock so the next [resolve] re-derives the defaults. */
    fun reset() {
        prefs.edit().remove(KEY_DOCK).apply()
    }

    private fun resolveDefaults(allApps: List<AppIconItem>): List<AppIconItem?> {
        val used = mutableSetOf<String>()
        return DEFAULT_SLOTS.map { spec ->
            pick(spec, allApps, used)?.also { used.add(it.packageName) }
        }
    }

    private fun pick(
        spec: SlotSpec,
        allApps: List<AppIconItem>,
        used: Set<String>
    ): AppIconItem? {
        fun firstOrNull(pred: (AppIconItem) -> Boolean): AppIconItem? =
            allApps.firstOrNull { it.packageName !in used && pred(it) }

        // 1. explicit package allowlist.
        for (pkg in spec.packages) {
            val hit = allApps.firstOrNull { it.packageName == pkg && it.packageName !in used }
            if (hit != null) return hit
        }
        // 2. exact label match, so a vendor build with a different package
        //    (or a user-installed dialer) still lands in the right slot.
        firstOrNull { it.label.equals(spec.label, ignoreCase = true) }?.let { return it }
        // 3. the platform's own default-app resolution for this role.
        resolveRolePackage(spec.role)?.let { rolePkg ->
            val hit = allApps.firstOrNull { it.packageName == rolePkg && it.packageName !in used }
            if (hit != null) return hit
        }
        // 4. nothing identifies this role on this device.
        return null
    }

    private fun resolveRolePackage(role: Int): String? = try {
        val pm = appContext.packageManager
        val intent = when (role) {
            ROLE_DIAL -> Intent(Intent.ACTION_DIAL)
            ROLE_MESSAGE -> Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"))
            ROLE_BROWSER -> Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
            ROLE_MUSIC -> Intent(
                Intent.ACTION_VIEW,
                android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            )
            else -> null
        }
        intent?.let {
            pm.resolveActivity(it, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        }
    } catch (_: Exception) {
        null
    }

    private class SlotSpec(
        val role: Int,
        val label: String,
        val packages: List<String>
    )

    companion object {
        private const val PREFS_NAME = "appleux_launcher_dock"
        private const val KEY_DOCK = "dock_v2"
        private const val SEP = "\u0001"
        private const val NONE = "\u0000"

        const val ROLE_DIAL = 0
        const val ROLE_BROWSER = 1
        const val ROLE_MESSAGE = 2
        const val ROLE_MUSIC = 3

        /**
         * PROVENANCE: slot order AND slot 4 are Apple-measured, from
         * SB_DefaultIconState~iphone.plist (`buttonBar`): com.apple.mobilephone,
         * com.apple.mobilesafari, com.apple.MobileSMS, com.apple.Music. An
         * earlier revision had message/browser swapped and a CAMERA slot;
         * iOS carries no camera in the dock (camera is a lock-screen
         * shortcut, which we also have). The package allowlists remain
         * AppleUX-chosen (AOSP/Google defaults, OEM replacements, whole-name
         * matches only).
         */
        private val DEFAULT_SLOTS = listOf(
            SlotSpec(
                ROLE_DIAL, "Phone",
                listOf(
                    "com.android.dialer",
                    "com.google.android.dialer",
                    "com.android.server.telecom",
                    "com.s.android.dialer",
                    "com.android.incallui"
                )
            ),
            SlotSpec(
                // Chrome is the Safari equivalent: the AOSP/Google default
                // browser and the one the brief names explicitly.
                ROLE_BROWSER, "Chrome",
                listOf(
                    "com.android.chrome",
                    "com.chrome.beta",
                    "com.chrome.dev",
                    "com.google.android.browser",
                    "com.android.browser",
                    "org.mozilla.firefox"
                )
            ),
            SlotSpec(
                ROLE_MESSAGE, "Messages",
                listOf(
                    "com.google.android.apps.messaging",
                    "com.android.mms",
                    "com.android.mms.service",
                    "com.samsung.android.messaging",
                    "com.miui.msghandle"
                )
            ),
            // Music, not Camera: Apple's fourth dock slot is com.apple.Music.
            SlotSpec(
                ROLE_MUSIC, "Music",
                listOf(
                    "com.android.music",
                    "com.google.android.music",
                    "com.google.android.apps.youtube.music",
                    "com.spotify.music",
                    "com.samsung.android.music",
                    "com.miui.player",
                    "org.videolan.vlc"
                )
            )
        )
    }
}