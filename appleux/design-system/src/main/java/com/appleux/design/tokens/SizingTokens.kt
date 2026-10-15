package com.appleux.design.tokens

/**
 * Sizes and corner radii.
 *
 * PROVENANCE — read before adding a number here.
 *
 * Two groups:
 *
 *  - **Transcribed from the iOS 27 Figma kit or from published UIKit values.**
 *    These carry their own inline comment naming the source. Corner radii for
 *    Liquid Glass surfaces are NOT here: they belong to
 *    [LiquidGlassTokens], which carries the kit provenance for the whole
 *    material.
 *  - **AppleUX-chosen.** Everything else. Apple publishes no numeric spec for
 *    the launcher grid, the dock, app-icon sizes, search bars or the Control
 *    Center, so those are our measurements and our judgement. They are
 *    labelled inline.
 *
 * Everything here is **dp**, not px.
 */
object SizingTokens {

    // ---- iOS UISwitch ------------------------------------------------------
    // PROVENANCE: Apple publishes no numeric spec for UISwitch. These are the
    // widely-published UIKit values (51x31pt body, 27pt thumb, 15.5pt radius)
    // that iOS 27 still uses, expressed in dp because 1pt maps to 1dp on the
    // devices this ROM targets. AppleUX-transcribed, not Apple-documented.
    const val SWITCH_WIDTH_DP: Float = 51f
    const val SWITCH_HEIGHT_DP: Float = 31f
    const val SWITCH_THUMB_DIAMETER_DP: Float = 27f
    const val SWITCH_CORNER_RADIUS_DP: Float = 15.5f

    /** Horizontal travel of the thumb, i.e. width minus thumb. */
    const val SWITCH_THUMB_TRAVEL_DP: Float = SWITCH_WIDTH_DP - SWITCH_THUMB_DIAMETER_DP

    // ---- Corner radii (AppleUX-chosen, on a 4pt grid) ---------------------
    // Liquid Glass surfaces use LiquidGlassTokens instead; these are for the
    // opaque card/sheet chrome.
    const val CORNER_RADIUS_SMALL: Float = 8f
    const val CORNER_RADIUS_MEDIUM: Float = 12f
    const val CORNER_RADIUS_LARGE: Float = 16f
    const val CORNER_RADIUS_LARGE_TITLE: Float = 20f
    const val CORNER_RADIUS_EXTRA_LARGE: Float = 24f
    const val CORNER_RADIUS_ROUND: Float = 9999f
    const val CORNER_RADIUS_XLARGE: Float = 24f
    const val INSET_XL: Float = 16f
    const val CORNER_RADIUS_SHEET: Float = 22f
    const val CORNER_RADIUS_CARD: Float = 16f

    // ---- Controls (AppleUX-chosen) -----------------------------------------
    // Apple's own 44pt minimum touch target IS published, via HIG
    // Accessibility → minimum hit targets; CONTROL_SIZE_* are ours.
    const val CONTROL_SIZE_SM: Float = 24f
    const val CONTROL_SIZE_MD: Float = 32f
    const val CONTROL_SIZE_LG: Float = 40f
    const val CONTROL_SIZE_XL: Float = 44f
    const val CONTROL_SIZE_XXL: Float = 48f

    /** HIG: 44x44pt minimum hit target. */
    const val TOUCH_TARGET_MIN: Float = 44f

    // ---- App icons (AppleUX-chosen) ----------------------------------------
    // Measured off iOS launcher screenshots at 1x, scaled for a 4-row grid.
    const val APP_ICON_SM: Float = 58f
    const val APP_ICON_MD: Float = 68f
    const val APP_ICON_LG: Float = 86f

    // ---- System chrome (AppleUX-chosen) ------------------------------------
    // These are FALLBACKS. The real insets come from WindowInsets at runtime;
    // reading them from a token is what makes a view mis-measure on a device
    // with a different cutout. Use them only when insets are unavailable.
    const val STATUS_BAR_HEIGHT: Float = 24f
    const val NAVIGATION_BAR_HEIGHT: Float = 44f
    const val HOME_INDICATOR_HEIGHT: Float = 36f

    // ---- Surfaces (AppleUX-chosen) -----------------------------------------
    const val NOTIFICATION_WIDTH: Float = 340f
    const val NOTIFICATION_MAX_WIDTH: Float = 512f
    const val CONTROL_CENTER_WIDTH: Float = 360f
    const val SEARCH_BAR_HEIGHT: Float = 44f
    const val SEARCH_BAR_WIDTH_COMPACT: Float = 340f
    const val SEARCH_BAR_WIDTH_FULL: Float = 506f
    const val DOCK_HEIGHT: Float = 90f
    const val DOCK_ICON_SIZE: Float = 60f
    const val SHELF_HEIGHT: Float = 180f

    data class CornerRadiusSet(
        val small: Float,
        val medium: Float,
        val large: Float,
        val extraLarge: Float,
        val round: Float
    )

    val standardRadii: CornerRadiusSet = CornerRadiusSet(
        small = CORNER_RADIUS_SMALL,
        medium = CORNER_RADIUS_MEDIUM,
        large = CORNER_RADIUS_LARGE,
        extraLarge = CORNER_RADIUS_EXTRA_LARGE,
        round = CORNER_RADIUS_ROUND
    )
}
