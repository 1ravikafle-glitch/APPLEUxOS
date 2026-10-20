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
     * Measured height of a home icon's label line in the 12sp label style.
     *
     * The header above derives the rock row pitch from icon(60) + gap(4) +
     * label(15) = 79 dp of row *content*, plus the two [SpacingTokens.INSETS_LG]
     * margins the cell carries, giving 103 dp. That 103 is the number the grid
     * actually has to fit, so it is named here rather than being re-derived at
     * each call site.
     */
    const val ICON_LABEL_HEIGHT_DP: Float = 15f

    /**
     * Top inset of the home grid: the MEASURED 76 px waterdrop cutout = 27.6 dp.
     *
     * Held as a fallback only. At runtime [ui.HomescreenGrid.applyNotchInset]
     * replaces the grid's real top padding with the window's own cutout inset,
     * which is authoritative; this value only sizes the row count before the
     * first layout pass.
     */
    const val HOME_TOP_INSET_DP: Float = 27.6f

    /**
     * The height one grid row actually occupies, in dp.
     *
     * icon + icon-to-label gap + label + the cell's top and bottom margins.
     * Nothing in the layout code reads [GRID_ROW_PITCH_DP]; the rows are laid
     * out at this natural height, and [GRID_ROW_PITCH_DP] is the *designed*
     * pitch for the rock frame only. Use this for fit arithmetic.
     */
    val naturalRowPitchDp: Float
        get() = HOME_ICON_SIZE_DP + ICON_LABEL_GAP_DP + ICON_LABEL_HEIGHT_DP +
            2f * com.appleux.design.tokens.SpacingTokens.INSETS_LG

    /**
     * Bottom padding on the homescreen grid: the last row must stop above the
     * floating dock instead of sliding underneath its glass bar. Re-derived in
     * the header at our ROM's frame (146 dp at 440 dpi).
     */
    const val HOMESCREEN_DOCK_CLEARANCE_DP: Float = 146f

    /**
     * Gap the search pill keeps above the dock.
     *
     * The pill lives INSIDE the dock clearance band — it is drawn over the
     * wallpaper between the last grid row and the dock, which is where iOS puts
     * it. Both the pill and the page dots used to sit at frozen offsets (196 dp
     * and 100 dp) that only clear the dock on the rock panel; on a shorter frame
     * the translucent pill showed through the grid icons behind it. Deriving
     * both from the clearance keeps them in the band on every frame.
     */
    const val SEARCH_PILL_GAP_DP: Float = 16f

    /** Page dots sit between the pill and the dock, as on iOS. */
    const val PAGE_DOTS_GAP_DP: Float = 8f

    /**
     * Bottom offset of the search pill, measured up from the window bottom.
     *
     * The pill is drawn over the wallpaper inside the dock clearance band, which
     * is where iOS puts it, so it must clear the dock on every frame. Derived
     * rather than frozen: the shipped 196 dp happened to work on the rock panel
     * only because the grid's own padding already reserved 146 dp underneath it,
     * and on a shorter frame the translucent capsule showed through the icons.
     */
    const val SEARCH_PILL_BOTTOM_DP: Float = HOMESCREEN_DOCK_CLEARANCE_DP + SEARCH_PILL_GAP_DP

    /**
     * Height of the search pill capsule.
     *
     * Part of [HOMESCREEN_BOTTOM_RESERVE_DP] because the last grid row has to
     * stop above the pill, not merely above the dock.
     */
    const val SEARCH_PILL_HEIGHT_DP: Float = 44f

    /**
     * Band the grid must leave clear below its last row.
     *
     * This is the ONE reservation. It used to be applied twice — once as the
     * grid's LayoutParams bottomMargin and again as the grid's own padding, both
     * [HOMESCREEN_DOCK_CLEARANCE_DP] — so 292 dp of a 640 dp screen went to the
     * dock and the grid could only fit two rows, with a dead gap above the pill.
     * The grid now pads by this value once, and carries no bottom margin.
     *
     * It covers the search pill, because the pill is drawn between the last grid
     * row and the dock exactly as on iOS and must not sit on top of an icon.
     */
    const val HOMESCREEN_BOTTOM_RESERVE_DP: Float =
        SEARCH_PILL_BOTTOM_DP + SEARCH_PILL_HEIGHT_DP + 8f

    /**
     * Bottom offset of the page dots: just above the search pill, matching iOS.
     *
     * Derived, not literal. The previous value was
     * `HOMESCREEN_DOCK_CLEARANCE_DP - PAGE_DOTS_GAP_DP - 60f` = 78 dp, which
     * contradicted the intent stated in this very comment: the dock bar
     * occupies the top 84 dp of the clearance band (62 dp..146 dp from the
     * window bottom), so 78 dp put the dots INSIDE the dock band. B measured
     * the consequence on device: the dots existed at 21x30 px, y2166..2196,
     * while the dock glass band is y2106..2337 — the dock is drawn after them
     * and covered them completely, so the home screen showed no page
     * indicator at all.
     *
     * The dots belong in the gap between the app grid and the search pill, so
     * they are derived from the pill: pill bottom + pill height + the same
     * 8 dp gap the pill keeps. That equals [HOMESCREEN_BOTTOM_RESERVE_DP],
     * which is why the grid reserve already accounts for them.
     */
    const val PAGE_DOTS_BOTTOM_DP: Float =
        SEARCH_PILL_BOTTOM_DP + SEARCH_PILL_HEIGHT_DP + PAGE_DOTS_GAP_DP

    /**
     * Height of one pinned widget cell. Derived from the same cell pitch:
     * a 3-column iOS medium widget is ~1.5:1, so
     * 3 x 70.55 / 1.5 = 141.1 -> 141 dp. Small (2-span) and medium (3-span)
     * widgets share the row height, exactly as they do on iOS, where a small
     * widget is 2x2 cells and a medium one is 4x2 — same height, twice the
     * width.
     */
    const val WIDGET_CELL_HEIGHT_DP: Float = 150f

    /** Widget spans, expressed in the 4-column grid (medium = full width). */
    const val WIDGET_SPAN_MEDIUM: Int = 4
    const val WIDGET_SPAN_SMALL: Int = 2

    /** iOS shows the App Library as a 3-column grid, never a single-column list. */
    // iOS 27 App Library is a TWO-column folder grid (drive/applibrary-light-p1:
    // two ~170dp glass tiles per row). At 3 the 170dp tiles overflowed their
    // 124dp slots and every row overlapped its neighbours.
    const val APP_LIBRARY_COLUMNS: Int = 2

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

    /** C3 (measured): light-mode dock scrim at full glass — the GT dock
     *  band reads ~8% darker than the wallpaper (222,179,106 vs
     *  240,194,117). 20/255 = 7.8%. */
    const val DOCK_LIGHT_SCRIM_ALPHA: Int = 20

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