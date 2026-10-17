package com.appleux.systemui.chrome

/**
 * iOS 27 navigation-bar geometry.
 *
 * PROVENANCE — these are HIG Metrics, the "Indicators" row and the
 * "Home indicator" size in the HIG Layout/Controls table, plus the
 * home-indicator geometry measured from the operator's real iPhone 11 iOS 27
 * screenshots in `design-reference/iphone11-ios27/`.
 *
 * ## Why there is a nav bar at all on a device with no nav buttons
 *
 * The framework still allocates a navigation-bar window, because the gesture
 * navigation surface lives there and `config_navBarInteractionMode = 2`
 * switches what it *draws* to the Home indicator only. iOS has the same
 * arrangement: no buttons, but a persistent pill marking the gesture origin.
 *
 * ## Why this is a bare View and not a layout
 *
 * The pill is the only thing the navigation bar draws. Inflating a layout for
 * one shape would mean an extra View, a measure pass and a layout pass per
 * frame for something that is a single rounded rectangle. This view measures
 * itself from its token geometry, draws once, and never invalidates itself.
 */
object AppleuxChromeTokens {

    /**
     * Pill width, dp.
     *
     * Measured from the iOS 27 Home indicator on the operator's iPhone 11
     * screenshots: roughly 140 pt wide. HIG lists the home indicator as
     * "approximately 140 x 5 pt" on a 5.5" device, which is consistent.
     */
    const val HOME_INDICATOR_WIDTH_DP = 139f

    /**
     * Pill height, dp.
     *
     * HIG: 5 pt. This is the one dimension where "dp" and "pt" coincide here
     * - the pill is a fixed-size affordance, not text, so it does not scale
     * with Dynamic Type.
     */
    const val HOME_INDICATOR_HEIGHT_DP = 5f

    /**
     * Full corner radius, dp — i.e. height / 2, which makes the pill a
     * stadium rather than a rounded rectangle. iOS draws a true capsule.
     */
    val homeIndicatorRadiusDp: Float get() = HOME_INDICATOR_HEIGHT_DP / 2f

    /**
     * Distance from the bottom edge of the navigation bar's own bounds to the
     * bottom of the pill, dp.
     *
     * iOS 27 places the indicator in the home-indicator safe area, which is
     * 34 pt on a notched iPhone. The framework already reserves the gesture
     * area as the nav bar window's height, so the pill only has to clear the
     * gesture deadzone the framework handles itself - 8 dp is the AOSP deadzone
     * floor and is what keeps the pill clear of the home swipe's first
     * few millimetres of travel.
     */
    const val HOME_INDICATOR_BOTTOM_MARGIN_DP = 8f

    /**
     * Pill opacity.
     *
     * iOS draws the indicator at roughly 60% white in light appearance and
     * ~80% in dark, so it reads as a hint rather than a control. A single
     * constant is used here because this view lives in SystemUI, whose theme
     * is deliberately appearance-independent: the stock SystemUI draws one
     * appearance over every app, and inverting the pill on a per-app basis
     * would need a window-level colour that AOSP's nav bar does not carry.
     */
    const val HOME_INDICATOR_ALPHA = 0x9C // ~61%
}