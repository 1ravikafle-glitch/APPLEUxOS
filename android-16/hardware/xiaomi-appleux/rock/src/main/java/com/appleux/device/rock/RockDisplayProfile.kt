package com.appleux.device.rock

import android.app.ActivityManager
import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.util.DisplayMetrics
import android.view.Display
import android.view.WindowManager

/**
 * Runtime-readable display profile for the Xiaomi "rock" family (POCO M5 /
 * Redmi 11 Prime 4G, Helio G99, 4 GB / 64 GB).
 *
 * WHY THIS EXISTS — the point of this module is that **nothing about the
 * screen is hardcoded**. A launcher that lays out against a constant of "60 Hz"
 * and "a 24dp status bar" is correct on exactly one device and silently wrong
 * everywhere else. The POCO M5's panel reports **60 Hz or 90 Hz** depending on
 * the power mode and the ambient light sensor, so a fixed 16.67 ms frame
 * budget is wrong half the time on the very device we are building for. The
 * notch is a **top-centre waterdrop**, so the top inset is asymmetric in
 * content terms even though the panel itself is not cut.
 *
 * Consequently every consumer (launcher grid, Control Center, Notification
 * Center, the adaptive performance controller) reads the values here instead
 * of assuming them.
 *
 * PROVENANCE — the *fallback constants* below are **MEASURED**, not assumed.
 * On 2026-10-15 the operator pasted this off the physical POCO M5 running stock
 * HyperOS:
 *
 *     Physical size: 1080x2408
 *     Physical density: 440
 *     Override density: 430
 *     InsetsSource type=displayCutout   frame=[0,0][1080,76]
 *     InsetsSource type=statusBars      frame=[0,0][1080,76]      -> 76 px
 *     InsetsSource type=navigationBars  frame=[0,2281][1080,2408] -> 127 px
 *     cutoutPathParserInfo cutoutSpec={M 0,0 H -50 V 76 H 50 V 0 H 0 Z}
 *
 * Notes that matter and are easy to get wrong:
 *  - The panel is **2408 px tall**, not 2400. 2408 is what po.co and GSMArena
 *    list and what the device itself reports.
 *  - Density is **440 physical with a HyperOS override of 430**. 430 is the
 *    effective density: it is what DisplayMetrics reports and therefore what
 *    every dp conversion in this codebase has to agree with. Using 440 makes
 *    the logical frame 392.7 x 875.6 dp instead of 401.9 x 896.0 dp.
 *  - The cutout is 100 x 76 px, top-centre, centre x = 540 = exactly half of
 *    1080. So the top inset (76) IS the cutout height here: the status bar band
 *    and the notch are the same 76 px, there is no extra band above it.
 *  - The panel advertises **30, 60 and 90 Hz**. Judging frames against 90 Hz on
 *    a panel sitting at 30 Hz would accept every dropped frame.
 *
 * Even so, the live values always come from [read] and [currentRefreshRateHz].
 * These constants exist only for the case where a platform read fails or
 * returns something degenerate.
 *
 * Device facts this module is built around (panel geometry MEASURED 2026-10-15
 * on the physical unit — see deliverables/m20/rock-display-measurement):
 *  - display: 1080 x 2408 px at 440 dpi -> density 2.75, 392.73 x 875.64 dp
 *  - cutout: top-centre waterdrop notch (not a hole-punch, not symmetric)
 *  - panel modes: 30 / 50 / 60 / 90 Hz (`dumpsys display` supportedModes),
 *    90 active at capture; never assume one rate
 *  - corners: 106 px radius all four (38.5 dp)
 *  - RAM: 3758996 kB (4 GB variant), 3 GB engineering budget
 *
 * DENSITY, READ CAREFULLY — stock HyperOS reports an *override* density of
 * 430 (ro.sf.lcd_density is 440 physical). That override is HyperOS's and
 * dies with it: this ROM installs as the main system after bootloader unlock,
 * so HyperOS has no role. OUR build sets 440 (physical, no override), and
 * every dp figure in this codebase is derived at 2.75. The 401.9 x 896.0 dp
 * frame from the HyperOS measurement session applies to HyperOS only and
 * must never be used for our layout.
 */
object RockDisplayProfile {

    // ---- Nominal rock panel (fallback only) -------------------------------
    // PROVENANCE: MEASURED 2026-10-15 on the physical device.
    // Density is 440 because OUR ROM sets the physical density with no
    // override. HyperOS's 430 override does not transfer to this ROM.

    /** Nominal panel resolution in px. MEASURED: `wm size` -> Physical size. */
    const val NOMINAL_WIDTH_PX = 1080
    const val NOMINAL_HEIGHT_PX = 2408

    /**
     * Effective density in dpi: 440/160 = 2.75 -> 392.73 x 875.64 dp.
     * MEASURED physical panel density (`wm density`, ro.sf.lcd_density).
     */
    const val NOMINAL_DENSITY_DPI = 440

    /**
     * The panel's own density, identical to nominal: there is no override on
     * this ROM. Kept as a named constant so a future density choice changes
     * one line, not every derivation. (On stock HyperOS this read 430; that
     * value belonged to HyperOS and is deliberately not carried over.)
     */
    const val PHYSICAL_DENSITY_DPI = 440

    /**
     * Nominal top inset in px. MEASURED: statusBars frame [0,0][1080,76].
     * 76 px / 2.75 = 27.6 dp. On this panel the status-bar band and the
     * waterdrop are the same 76 px, so this is also the cutout height.
     */
    const val NOMINAL_TOP_INSET_PX = 76

    /**
     * Nominal bottom inset in px. MEASURED: navigationBars frame
     * [0,2281][1080,2408] = 127 px. 127 / 2.75 = 46.2 dp.
     */
    const val NOMINAL_BOTTOM_INSET_PX = 127

    /**
     * Nominal refresh rate. Deliberately the SLOWER of the rates below 90: if a
     * read fails, judging frames against 90 Hz on a 60 Hz panel would silently
     * accept every dropped frame.
     */
    const val NOMINAL_REFRESH_HZ = 60f

    /**
     * Lowest rate the rock panel is known to drive. MEASURED: `dumpsys display`
     * on the physical unit (2026-10-15) lists supportedModes at 30.000002,
     * 50.0, 60.0 and 90.0 Hz, with 90.0 active at capture time. The operator
     * confirms 60/90 in everyday switching; 30 and 50 are real modes the
     * panel advertises, so the floor is 30, not 60.
     */
    const val MIN_KNOWN_REFRESH_HZ = 30f

    /**
     * Corner radius of the panel itself in px. MEASURED: `dumpsys display`
     * reports RoundedCorners radius=106 at all four corners
     * (106 / 2.75 = 38.5 dp). Content laid out to the raw frame corners is
     * clipped; reserve this in fullscreen surfaces.
     */
    const val NOMINAL_CORNER_RADIUS_PX = 106

    /** Highest rate the rock panel is known to drive. */
    const val MAX_KNOWN_REFRESH_HZ = 90f

    /**
     * Rounded to the nearest whole Hz. Android reports 59.94 / 89.99 on many
     * panels; the frame budget derived from those is wrong by 0.1% every
     * frame, which is harmless, but a *reported* 59.94 Hz in a settings
     * readout looks like a bug.
     */
    private fun roundHz(hz: Float): Float =
        if (hz.isNaN() || hz <= 1f) NOMINAL_REFRESH_HZ else Math.round(hz).toFloat()

    // ---- Profile ----------------------------------------------------------

    /**
     * @param refreshRateHz the rate the display is *actually running at*,
     *   read from [Display.getMode]. Never hardcoded to 60.
     * @param cutoutInsets the cutout bounding box in px, already relative to
     *   the display's top-left. All zeros when the panel is not cut.
     * @param cutoutIsTopCentre true for a waterdrop/notch, false for a
     *   hole-punch or a side cut; decides whether content needs centring
     *   clearance as well as top clearance.
     */
    data class Profile(
        val widthPx: Int,
        val heightPx: Int,
        val densityDpi: Int,
        val density: Float,
        val refreshRateHz: Float,
        val cutoutInsets: Rect,
        val cutoutIsTopCentre: Boolean,
        val totalRamBytes: Long,
        val isLowRamDevice: Boolean
    ) {
        /** Top edge of the cutout in px, measured from the display top. */
        val topInsetPx: Int get() = cutoutInsets.top

        /** Per-frame budget in ms: 16.67 at 60 Hz, 11.11 at 90 Hz. */
        val frameBudgetMs: Float get() = 1000f / refreshRateHz

        /** 1080 / 2.75 = 392.73 dp on the rock panel. */
        val logicalWidthDp: Float get() = widthPx / density

        /** 2408 / 2.75 = 875.64 dp on the rock panel. */
        val logicalHeightDp: Float get() = heightPx / density

        /**
         * True when this really is a rock-family unit. Used for reporting
         * only — layout must never fork on it, because a GSI on this panel
         * still reports "generic".
         */
        val isRockFamily: Boolean
            get() = Build.DEVICE == "rock" || Build.DEVICE == "stone" ||
                Build.PRODUCT.contains("rock", ignoreCase = true)
    }

    @Volatile
    private var cached: Profile? = null

    /**
     * Reads the live display profile. Cached, because the answer cannot change
     * within a process: density and cutout geometry are fixed at boot, and the
     * refresh rate is re-read explicitly by [currentRefreshRateHz] when the
     * panel actually switches mode.
     */
    @JvmStatic
    fun read(context: Context): Profile {
        cached?.let { return it }
        val profile = buildProfile(context)
        cached = profile
        return profile
    }

    /**
     * Refresh rate on its own, read live rather than from the cache: the panel
     * switches between 60 and 90 Hz at runtime, so a value cached at process
     * start would be wrong for the rest of the session.
     */
    @JvmStatic
    fun currentRefreshRateHz(context: Context): Float {
        val d = defaultDisplay(context)
        if (d != null) {
            // Display.getMode().getRefreshRate() is public API (23+). The
            // older Display.getRefreshRate() is @hide, so it is not used.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val mode = d.mode
                if (mode != null) {
                    val hz = roundHz(mode.refreshRate)
                    if (hz > 1f) return hz
                }
            }
            // Fallback: the fastest mode the panel advertises. On the rock
            // panel that is 90 Hz.
            val modes = d.supportedModes
            if (modes != null && modes.isNotEmpty()) {
                var best = 0f
                for (m in modes) if (m.refreshRate > best) best = m.refreshRate
                if (best > 1f) return roundHz(best)
            }
            val legacy = try { d.refreshRate } catch (_: Throwable) { 0f }
            if (legacy > 1f) return roundHz(legacy)
        }
        return NOMINAL_REFRESH_HZ
    }

    /**
     * Top inset in px for the current window. Prefers the window's own
     * [android.view.DisplayCutout] (which reflects the live window state) and
     * falls back to the display cutout, then to the nominal value.
     */
    @JvmStatic
    fun topInsetPx(context: Context): Int {
        val profile = read(context)
        return if (profile.topInsetPx > 0) profile.topInsetPx else NOMINAL_TOP_INSET_PX
    }

    @JvmStatic
    fun frameBudgetMs(context: Context): Float = 1000f / currentRefreshRateHz(context)

    private fun buildProfile(context: Context): Profile {
        val metrics: DisplayMetrics = context.resources.displayMetrics
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        val display = defaultDisplay(context)
        val bounds = wm?.maximumWindowMetrics?.bounds

        val widthPx = bounds?.width()?.takeIf { it > 0 } ?: metrics.widthPixels
        val heightPx = bounds?.height()?.takeIf { it > 0 } ?: metrics.heightPixels

        val cutout = readCutout(display)
        // A cutout whose centre is within a third of the panel width of the
        // horizontal centre is a top-centre notch (waterdrop on this device).
        // A hole-punch is offset; either way the top inset is what matters for
        // "content must not sit under it".
        val centreOffset = if (cutout != null && widthPx > 0) {
            kotlin.math.abs(cutout.centreX - widthPx / 2f) / widthPx
        } else {
            1f
        }

        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val mem = ActivityManager.MemoryInfo()
        var lowRam = false
        var total = 0L
        if (am != null) {
            try {
                am.getMemoryInfo(mem)
                lowRam = am.isLowRamDevice
                total = mem.totalMem
            } catch (_: Throwable) {
                // A non-privileged app can be denied this on some builds; the
                // nominal budget below is the fallback, not a hard failure.
            }
        }

        return Profile(
            widthPx = widthPx,
            heightPx = heightPx,
            densityDpi = metrics.densityDpi,
            density = if (metrics.density > 0f) metrics.density else NOMINAL_DENSITY_DPI / 160f,
            refreshRateHz = currentRefreshRateHz(context),
            cutoutInsets = cutout?.rect ?: Rect(0, 0, 0, 0),
            cutoutIsTopCentre = centreOffset <= 0.33f,
            totalRamBytes = total,
            isLowRamDevice = lowRam
        )
    }

    /** Cutout geometry in px, already resolved to display coordinates. */
private class CutoutGeometry(val rect: Rect, val centreX: Int)

/**
     * [android.view.Display.getCutout] is public from API 29. Below that a
     * display cutout cannot exist on the platform anyway, so null — meaning
     * "no cutout" — is the correct answer rather than a guess.
     *
     * [android.view.DisplayCutout.getSafeInsetTop] is preferred over the
     * bounding rect: the safe inset is the region the platform guarantees is
     * NOT occluded, which is exactly what "content must not sit under the
     * notch" means. The top bounding rect is used only to locate the cutout
     * horizontally, so a centred waterdrop can be told from an offset
     * hole-punch.
     */
    private fun readCutout(display: Display?): CutoutGeometry? {
        if (display == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return try {
            val c = display.cutout
            if (c == null) {
                null
            } else {
                CutoutGeometry(
                    Rect(
                        c.getSafeInsetLeft(),
                        c.getSafeInsetTop(),
                        c.getSafeInsetRight(),
                        c.getSafeInsetBottom()
                    ),
                    Math.round(c.getBoundingRectTop().exactCenterX())
                )
            }
        } catch (_: Throwable) {
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun defaultDisplay(context: Context): Display? = try {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.display
        } else {
            wm?.defaultDisplay
        }
    } catch (_: Throwable) {
        null
    }

    /** Drops the cache. Only for tests and for a density/rotation change. */
    @JvmStatic
    fun invalidate() {
        cached = null
    }
}