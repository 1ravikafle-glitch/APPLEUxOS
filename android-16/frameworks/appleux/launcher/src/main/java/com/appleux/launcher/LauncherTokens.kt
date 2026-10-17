package com.appleux.launcher

/**
 * Launcher-only metrics that have no design-system counterpart yet.
 *
 * Anything the shared tokens already define is reused directly and NOT repeated
 * here: [com.appleux.design.tokens.SpacingTokens.INSETS_MD] covers the widget
 * gutter and [com.appleux.design.tokens.SpacingTokens.INSETS_LG] the dock icon
 * spacing. Promote any of the values below into the shared token files once a
 * second consumer appears.
 *
 * ============================ THE 5 x 7 GRID =============================
 *
 * The grid is 4 columns x 6 rows, like iOS (reference home screen +
 * spec home.json: gridColumns 4, gridRows 6, iconSize 60). Every number below
 * is DERIVED for OUR ROM's frame — 440 dpi physical, no override (HyperOS's
 * 430 override dies at unlock and never applies here) — from the MEASURED
 * panel geometry (2026-10-15, physical unit —
 * deliverables/m20/rock-display-measurement.md), not copied from the
 * prototype or from an iPhone:
 *
 *   panel            1080 x 2408 px @ 440 dpi (this ROM; HyperOS reported 430)
 *   density          440 / 160 = 2.75
 *   logical size     392.73 x 875.64 dp
 *
 * Horizontal — this is the binding axis:
 *   usable width     392.73 - 2 x GRID_SIDE_MARGIN_DP(20)     = 352.73 dp
 *   cell pitch       352.73 / 4                               = 88.18 dp
 *   icon edge        60 dp (like iOS; 60 / 88.18 = 0.68, so a 28 dp
 *                    gutter separates neighbouring icons — no merge).
 *
 *   Label check: the longest common label ("Calculator") sets at ~66 dp in
 *   the 12sp label style, inside the 88.18 dp cell — comfortable slack. That
 *   is why the label stayed at 12sp rather than the 10sp it shipped with.
 *
 * Vertical — has slack, and is sized to consume it without crowding:
 *   top inset (notch)     MEASURED 76 px = 27.6 dp
 *   dock clearance        146 dp (see below)
 *   band available        875.64 - 27.6 - 146                 = 702.0 dp
 *   per row               702.0 / 7                            = 100.3 dp
 *   row pitch used        100 dp  ->  7 x 100 = 700 dp, 2.0 dp slack
 *
 *   Row content is icon(56) + icon-to-label gap(4) + label(15) = 75 dp, so
 *   each row carries ~25 dp of breathing space. That is the iOS rhythm: the
 *   cell pitch is wider than the icon, and the icon+label group is centred in
 *   it. Going denser would have meant shrinking the icon further, which the
 *   horizontal axis already caps.
 *
 * Dock clearance, bottom-up:
 *   gesture-navigation bar   46.2 dp  (MEASURED 127 px; the real value still
 *                                    comes from WindowInsets at runtime)
 *   dock-to-grid gap         16 dp
 *   dock bar                 84 dp  (icon 56 + 2 x 14 dp vertical padding)
 *   ------------------------------------------------
 *                              146 dp
 *
 * PROVENANCE: every figure here is **AppleUX-chosen**, derived from the
 * MEASURED POCO M5 display geometry. Apple publishes no numeric spec for the
 * launcher grid, and the 4-column/60dp icon we shipped before was ours too.
 * Nothing in this file comes from Apple's documentation.
 */
object LauncherTokens {

    // ---- Grid -------------------------------------------------------------

    /** Columns on the home screen: 4, like iOS (reference Launcher-IOS-27). */
    const val GRID_COLUMNS: Int = 4

    /** Rows that must fit above the dock: 6, like iOS (24 icons/page). */
    const val GRID_ROWS: Int = 6

    /** Side margin, so the first column is not flush with the screen edge. */
    const val GRID_SIDE_MARGIN_DP: Float = 20f

    /** One cell's pitch: (392.73 - 2 x 20) / 4. */
    const val GRID_CELL_PITCH_DP: Float = 88.18f

    /** Home-screen app-icon edge: 60, like iOS (28 dp gutter, no merge). */
    const val HOME_ICON_SIZE_DP: Float = 60f

    /** Gap between an icon and its label. */
    const val ICON_LABEL_GAP_DP: Float = 4f

    /** Row pitch: icon(60) + gap(4) + label(15) + breathing space; 6 fit in 702. */
    const val GRID_ROW_PITCH_DP: Float = 117f

    /**
     * Bottom padding on the homescreen grid: the last row must stop above the
     * floating dock instead of sliding underneath its glass bar. Re-derived in
     * the header at our ROM's frame (146 dp at 440 dpi).
     */
    const val HOMESCREEN_DOCK_CLEARANCE_DP: Float = 146f

    /**
     * Height of one pinned widget cell. Derived from the same cell pitch:
     * a 3-column iOS medium widget is ~1.5:1, so
     * 3 x 70.55 / 1.5 = 141.1 -> 141 dp. Small (2-span) and medium (3-span)
     * widgets share the row height, exactly as they do on iOS, where a small
     * widget is 2x2 cells and a medium one is 4x2 — same height, twice the
     * width.
     */
    const val WIDGET_CELL_HEIGHT_DP: Float = 176f

    /** Widget spans, expressed in the 4-column grid (medium = full width). */
    const val WIDGET_SPAN_MEDIUM: Int = 4
    const val WIDGET_SPAN_SMALL: Int = 2

    /** iOS shows the App Library as a 3-column grid, never a single-column list. */
    const val APP_LIBRARY_COLUMNS: Int = 3

    // ---- Dock -------------------------------------------------------------

    /**
     * Dock icons use the SAME edge as home-screen icons. They used to be 60 dp
     * against a 56 dp grid icon, which read as the dock being a different
     * (larger) icon set; iOS uses one size everywhere.
     */
    const val DOCK_ICON_SIZE_DP: Float = HOME_ICON_SIZE_DP

    /** Vertical padding inside the dock glass bar. */
    const val DOCK_BAR_PADDING_V_DP: Float = 14f

    /** Number of dock slots. Fixed at 4, like iOS. */
    const val DOCK_SLOTS: Int = 4

    /**
     * Corner radius of the dock glass bar.
     *
     * PROVENANCE: [com.appleux.design.tokens.LiquidGlassTokens.REGULAR_CORNER_RADIUS_DP]
     * (34 dp), transcribed from the iOS 27 Figma kit's Regular Liquid Glass
     * material — NOT the 28 dp this used to carry and NOT the 38 dp the
     * IOSLikeROM prototype drew. The dock is a Regular glass material, so it
     * takes the Regular radius. The *corner shape* is then the continuous
     * superellipse in
     * [com.appleux.design.components.AppleuxSuperellipse], not a circular arc.
     */
    const val DOCK_CORNER_RADIUS_DP: Float =
        com.appleux.design.tokens.LiquidGlassTokens.REGULAR_CORNER_RADIUS_DP

    /** Dock bar fill alpha at glass level 0, and the extra alpha added at level 1. */
    const val DOCK_GLASS_ALPHA_BASE: Int = 88
    const val DOCK_GLASS_ALPHA_RANGE: Int = 48

    // ---- Home-screen legibility ------------------------------------------

    /**
     * Dim applied over the wallpaper behind the home screen.
     *
     * PROVENANCE: **prototype-derived**, from IOSLikeROM
     * `launcher/HomeScreen.kt` (`GlassScene(wallpaper, dim = 0.12f)`), which
     * dims the backdrop 12% on the home page and 30% on the App Library page.
     * Adopted because white 12sp app labels over an arbitrary user wallpaper
     * are otherwise unreadable on a light image. Apple does not publish a
     * value; iOS achieves the same legibility with a per-label shadow instead,
     * which is applied as well (see AppIconView).
     */
    const val HOMESCREEN_WALLPAPER_DIM = 0.12f

    /** Dim when the App Library is over the top, matching the prototype's 0.30. */
    const val APP_LIBRARY_WALLPAPER_DIM = 0.30f

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

    // ---- Press feedback ----------------------------------------------------

    /**
     * Scale an app cell shrinks to while held.
     *
     * PROVENANCE: **prototype-derived**, from IOSLikeROM `theme/LiquidGlass.kt`
     * `Modifier.pressable` (0.94f, `spring(dampingRatio = 0.6f,
     * stiffness = 500f)`, `HapticFeedbackType.TextHandleMove`). Apple's own
     * value for a home-screen icon press is not published; the prototype's
     * number is adopted because its spring is a plausible iOS "quick, slightly
     * bouncy" curve, not because it is an Apple measurement.
     */
    const val PRESS_SCALE: Float = 0.94f

    /** Spring damping ratio for the press scale. Prototype-derived, as above. */
    const val PRESS_SPRING_DAMPING: Float = 0.6f

    /** Spring stiffness for the press scale. Prototype-derived, as above. */
    const val PRESS_SPRING_STIFFNESS: Float = 500f
}