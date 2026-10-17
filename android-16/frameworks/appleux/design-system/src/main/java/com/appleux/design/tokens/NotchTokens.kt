package com.appleux.design.tokens

/**
 * iPhone 11-style notch emulated on the rock panel, and the Dynamic Island
 * that lives on it.
 *
 * WHAT THIS IS — the POCO M5 has a 100x76 px waterdrop, not an iPhone notch,
 * and hardware cannot be changed. What CAN change is what our own overlay
 * windows draw: [AppleuxDynamicIsland] paints a black iPhone-11-shaped notch
 * at the top-centre of our surfaces (which fully covers the waterdrop beneath
 * it) and expands it into a live pill. This is emulation inside our UI, not a
 * system cutout override — the platform still reports the waterdrop, and
 * [RockDisplayProfile] insets remain the source of truth for layout.
 *
 * PROVENANCE — **AppleUX-mapped, not Apple-measured.** Apple publishes no
 * notch dimensions. The fractions come from the iPhone 11 geometry
 * (414x896 pt, notch ~208x30 pt → width fraction 0.502, ~30 pt tall on the
 * 44 pt status bar) applied to OUR measured frame (401.9 dp wide). Points and
 * dp are both density-independent units, so the mapping is 1:1 by design, and
 * the width scales with the frame while the height stays fixed. If Apple ever
 * publishes real numbers, they replace these.
 *
 * The island states are ours too: iPhone 11 never had a Dynamic Island (the
 * island replaced the notch on the 14 Pro). The hybrid — notch at rest, live
 * pill on demand — is an AppleUX design decision, documented as such.
 */
object NotchTokens {

    /** iPhone 11 notch width as a fraction of frame width: 208/414. */
    const val NOTCH_WIDTH_FRACTION: Float = 0.502f

    /** Notch height in dp. Fits inside the 28.3 dp status band. */
    const val NOTCH_HEIGHT_DP: Float = 29f

    /** Bottom-corner radius of the notch. Top edge is straight (screen edge). */
    const val NOTCH_CORNER_RADIUS_DP: Float = 10f

    /** Charging dot shown on the idle notch: 6 dp, iOS green. */
    const val NOTCH_DOT_SIZE_DP: Float = 6f

    /** Expanded pill corner radius: Liquid Glass Regular, like all sheets. */
    const val ISLAND_EXPANDED_RADIUS_DP: Float =
        LiquidGlassTokens.REGULAR_CORNER_RADIUS_DP

    /** Minimum expanded height: room for two lines of live content. */
    const val ISLAND_EXPANDED_MIN_HEIGHT_DP: Float = 150f

    /** Expanded pill width as a fraction of frame width. */
    const val ISLAND_EXPANDED_WIDTH_FRACTION: Float = 0.9f
}
