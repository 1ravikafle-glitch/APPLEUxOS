package com.appleux.launcher

/**
 * Launcher-only metrics that have no design-system counterpart yet.
 *
 * Anything the shared tokens already define is reused directly and NOT repeated
 * here: [com.appleux.design.tokens.SizingTokens.DOCK_ICON_SIZE],
 * [com.appleux.design.tokens.SpacingTokens.INSETS_MD] and
 * [com.appleux.design.tokens.SpacingTokens.INSETS_LG] cover the dock icon, the
 * widget gutter and the dock icon spacing.
 *
 * The values below are specific to the launcher surface — dock clearance,
 * widget cell height, the dock glass bar's own radius and alpha, and the
 * launcher's gesture thresholds — so they live in the launcher module rather
 * than in appleux/design-system/, which every other module also builds. Promote
 * any of them into the shared token files once a second consumer appears.
 */
object LauncherTokens {

    // ---- Layout (dp) -------------------------------------------------------

    /**
     * Bottom padding on the homescreen grid: the last row must stop above the
     * floating dock instead of sliding underneath its glass bar.
     */
    const val HOMESCREEN_DOCK_CLEARANCE_DP: Float = 164f

    /** Height of one pinned widget cell (the "S" span on a 4-column grid). */
    const val WIDGET_CELL_HEIGHT_DP: Float = 154f

    /** iOS shows the App Library as a 3-column grid, never a single-column list. */
    const val APP_LIBRARY_COLUMNS: Int = 3

    // ---- Liquid glass ------------------------------------------------------

    /**
     * Corner radius of the dock glass bar. Not a LiquidGlassTokens value: that
     * file carries transcribed iOS 27 *material* radii, and the dock bar is an
     * AppleUX-tuned launcher surface with no Figma counterpart.
     */
    const val DOCK_CORNER_RADIUS_DP: Float = 28f

    /** Dock bar fill alpha at glass level 0, and the extra alpha added at level 1. */
    const val DOCK_GLASS_ALPHA_BASE: Int = 64
    const val DOCK_GLASS_ALPHA_RANGE: Int = 64

    // ---- Gesture thresholds ------------------------------------------------
    // MotionTokens holds the shared transition/velocity set; these are the
    // launcher's own recognition thresholds and stay pixel values because they
    // are compared against raw MotionEvent coordinates.

    /** Minimum |dy| in px before a drag is recognised as a swipe at all. */
    const val SWIPE_DISTANCE_PX: Float = 120f

    /** A press held longer than this is a long-press, not a tap. */
    const val LONG_PRESS_DURATION_MS: Long = 500L

    /** Movement budget in px during a long-press before it becomes a drag. */
    const val LONG_PRESS_SLOP_PX: Float = 24f

    /** A swipe that takes longer than this is treated as a scroll/hold. */
    const val SWIPE_MAX_DURATION_MS: Long = 800L
}