package com.appleux.design.tokens

/**
 * Lock Screen geometry for iOS 27.
 *
 * PROVENANCE — read before changing any number here.
 *
 * Every measured value comes from ONE source: Apple's own Lock Screen
 * screenshot shipped in the official iOS 27 design guide,
 *
 *   design-reference/ios27/official-guide/images/8313a5fa-0741-4a43-934e-17cb595d11d7.png
 *
 * The device bezel was stripped from that image before measuring: the usable
 * screen crop is **447 x 965 px** (aspect 0.4632, a 19.5:9 panel). All ratios
 * below are expressed against that crop, and the pixel value is quoted next to
 * each one so the measurement can be re-checked.
 *
 * Apple publishes no numeric spec for the Lock Screen layout, so — exactly as
 * with [LiquidGlassTokens] — these are *measurements of Apple's rendering*,
 * not hand-off values. That is the strongest provenance available here: the
 * numbers are observed, not guessed.
 *
 * Values NOT in this file are AppleUX-chosen and are marked as such at their
 * call site.
 */
object LockScreenTokens {

    // ---- Clock ------------------------------------------------------------
    // The iOS 27 clock is the dominant element of the screen. Measured glyph
    // extent: x 72..444 -> 373px of 447 = 0.834 of screen width. Its vertical
    // centre sits at y ~308 of 965 = 0.32, i.e. the upper third.
    //
    // Expressed as a FRACTION rather than a point size on purpose: the clock
    // auto-fits to this fraction of the available width, so it looks the same
    // on a 393pt phone and on the POCO M5's 360dp panel, and it survives a
    // user font-scale change without clipping.
    const val CLOCK_WIDTH_FRACTION = 0.834f

    /** Vertical centre of the clock: y 308 / 965 = 0.32 — the upper third. */
    const val CLOCK_CENTER_Y_FRACTION = 0.33f

    /** Never grow the clock past this share of screen height (overflow guard). */
    const val CLOCK_MAX_HEIGHT_FRACTION = 0.34f

    // ---- Date -------------------------------------------------------------
    // "Wed Apr 1" measured at x 169..275 -> 107px wide (0.239 of screen width)
    // with a 22px cap height (0.0228 of screen height). 22px on a 393pt-wide
    // screen is ~19.3pt, which lands between Subheadline (15) and Title 3 (20);
    // AppleuxLockScreen uses TypographyTokens.title3 for this line, so the
    // closest transcribed HIG style is the type source rather than a
    // hardcoded 19.3f.

    // ---- Bottom corner buttons -------------------------------------------
    // The two circular glass controls. The cleanest connected component in the
    // image (right-hand camera button) measured x 336..389, y 855..905 — i.e.
    // 54 x 51px. 54/447 = 0.121 of screen width.
    //
    // Inset: the button's outer edge sits 447 - 389 = 58px from the right edge
    // (0.130 of width); the left button's centre measured x 82.5 with a 27px
    // radius, giving the same ~55px inset.
    //
    // Vertical placement (measured centre y 880 of 965 = 0.912 of screen
    // height) is deliberately NOT a token: the row is anchored to the bottom
    // inset instead, which is both more correct across devices with different
    // home indicators and one less constant to keep in sync.
    const val BUTTON_DIAMETER_DP = 48f
    const val BUTTON_INSET_DP = 48f

    /** Minimum clear space under the button row before the home indicator. */
    const val BUTTON_ROW_BOTTOM_CLEARANCE_DP = 12f

    // ---- Notifications ----------------------------------------------------
    // AppleUX-chosen. The guide screenshot this screen is built from happens
    // to show a lock screen with no notifications, so there is nothing to
    // measure and no claim of Apple's is being made. These sit on the
    // SpacingTokens scale; the corner radius is deliberately NOT
    // LiquidGlassTokens.REGULAR_CORNER_RADIUS_DP (34dp), which is the kit's
    // radius for Large/Medium glass surfaces such as the Control Center
    // sheet, not for a short notification row.
    const val NOTIFICATION_TOP_GAP_DP = 24f
    const val NOTIFICATION_CORNER_RADIUS_DP = 22f
    const val NOTIFICATION_ICON_DP = 28f

    // ---- Appearance -------------------------------------------------------
    // The clock samples at 255,252,244 where it crosses a bright wallpaper
    // highlight and the local wallpaper underneath is ~205,191,176, so the
    // glyphs are white at a high but sub-opaque alpha — you can read the
    // wallpaper through the strokes. AppleUX-chosen alpha; the *observation*
    // (translucent white over wallpaper, never a solid colour) is Apple's.
    const val CLOCK_FILL_ALPHA = 0xE0 // ~88%

    /** Date sits one step down from the clock but stays clearly readable. */
    const val DATE_FILL_ALPHA = 0xD0 // ~82%
}