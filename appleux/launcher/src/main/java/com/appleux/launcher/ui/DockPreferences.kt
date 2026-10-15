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
 *     (ACTION_DIAL / ACTION_SENDTO smsto: / ACTION_VIEW https / camera),
 *     which is what IOSLikeROM's `AppRepo.dock()` does and is still not
 *     positional;
 *  4. only if all three fail, the first remaining app — and that is recorded
 *     so the choice is stable from then on.
 *
 * Once resolved, the package names are written to SharedPreferences and are
 * the single source of truth forever after. Resolution never runs again
 * unless the user explicitly resets, so an app being uninstalled later cannot
 * reshuffle the dock behind their back: the slot is emptied, not refilled.
 *
 * PROVENANCE: the four default slots and the role intents are **prototype-
 * derived** from IOSLikeROM `launcher/AppRepo.kt` (`dock()`), whose README
 * states "Dock = your default phone, messages, browser and camera apps". The
 * package allowlists and the persistence mechanism are ours.
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
            ROLE_CAMERA -> Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
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
        const val ROLE_MESSAGE = 1
        const val ROLE_BROWSER = 2
        const val ROLE_CAMERA = 3

        /**
         * PROVENANCE: the four roles are prototype-derived (see the class
         * header). The package allowlists are AppleUX-chosen and ordered by
         * confidence: the AOSP/Google defaults first, then the common
         * OEM-replacement packages. They are matched as whole package names —
         * never as a prefix, and never by list position.
         */
        private val DEFAULT_SLOTS = listOf(
            SlotSpec(
                ROLE_DIAL, "Phone",
                listOf(
                    "com.android.dialer",
                    "com.google.android.dialer",
                    "com.android.server.telecom",
                    "com.s.android.dialer",
                    "com.miui.player",
                    "com.android.incallui"
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
                ROLE_CAMERA, "Camera",
                listOf(
                    "com.android.camera",
                    "com.android.camera2",
                    "com.miui.camera",
                    "com.huawei.camera",
                    "com.oneplus.camera"
                )
            )
        )
    }
}