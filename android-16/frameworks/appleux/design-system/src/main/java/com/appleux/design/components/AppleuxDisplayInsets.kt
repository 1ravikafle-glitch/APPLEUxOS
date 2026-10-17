package com.appleux.design.components

import android.content.Context
import android.view.View
import com.appleux.device.rock.RockDisplayProfile

/**
 * Applies the panel's real top inset (the top-centre notch on the POCO M5) to
 * any full-bleed surface, so no content is ever laid out under the cutout.
 *
 * WHY THIS IS NOT A TOKEN — [com.appleux.design.tokens.SizingTokens] carries
 * fallback system-bar heights and says so. Those fallbacks are wrong on a cut
 * panel: the emulator reports a 191 px inset and a waterdrop notch on the
 * rock panel reports something else again, and neither is knowable from a
 * constant. So the value is read at runtime through
 * [RockDisplayProfile] and this object is the single place that applies it.
 *
 * PROVENANCE — **AppleUX-chosen.** The inset VALUE comes from the platform
 * (`Display.getCutout`, API 29+), not from Apple. The extra breathing room
 * added on top of it is ours, and is expressed in dp because it is a design
 * decision rather than a measurement.
 */
object AppleuxDisplayInsets {

    /**
     * Content clearance below the cutout, in dp. iOS keeps the first row of
     * home-screen content clearly below the notch rather than flush with it;
     * 8 dp is the smallest margin on that device's 4pt grid that still reads
     * as deliberate.
     */
    const val TOP_CONTENT_GUTTER_DP = 8f

    /** Raw cutout top inset for this display, in px. 0 when there is no cut. */
    @JvmStatic
    fun topInsetPx(context: Context): Int = RockDisplayProfile.topInsetPx(context)

    /**
     * Total top padding a full-bleed panel needs: the cutout inset plus
     * [TOP_CONTENT_GUTTER_DP].
     */
    @JvmStatic
    fun topContentPaddingPx(context: Context): Int {
        val density = context.resources.displayMetrics.density
        return topInsetPx(context) +
            Math.round(TOP_CONTENT_GUTTER_DP * density)
    }

    /**
     * Pads [view] by the cutout inset plus the gutter, preserving whatever
     * left/right/bottom padding it already had.
     *
     * @param extraTopDp additional clearance the surface wants above the
     *   gutter (e.g. a panel header's own spacing). 0 for most surfaces.
     */
    @JvmStatic
    @JvmOverloads
    fun applyTopInset(context: Context, view: View, extraTopDp: Float = 0f) {
        val density = context.resources.displayMetrics.density
        val top = topContentPaddingPx(context) + Math.round(extraTopDp * density)
        if (view.paddingTop == top) return
        view.setPadding(view.paddingLeft, top, view.paddingRight, view.paddingBottom)
        view.requestLayout()
    }

    /**
     * The live refresh rate, re-read on every call because the panel switches
     * between 60 and 90 Hz at runtime. See [RockDisplayProfile].
     */
    @JvmStatic
    fun refreshRateHz(context: Context): Float =
        RockDisplayProfile.currentRefreshRateHz(context)
}