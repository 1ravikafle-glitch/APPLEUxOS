package com.appleux.design.tokens

/**
 * Spacing ramp.
 *
 * PROVENANCE — **AppleUX-chosen.** Apple publishes exactly two layout
 * numbers: a 4pt margin and an 8pt gutter for the standard iPhone layout grid
 * (`developers.apple.com/design/human-interface-guidelines/layout`). There is
 * no published spacing scale beyond that.
 *
 * [INSETS_MD] and [INSETS_LG] are therefore Apple's 8 and 12pt, and
 * [HALF_GRID] / [QUARTER_GRID] are Apple's 4pt margin and its half. The rest of
 * the ramp is Apple's 4pt/8pt grid extended upwards in whole steps because
 * components need 16, 20, 24 and so on.
 *
 * Note the two units in this file, which used to be conflated:
 *  - `INSETS_*`, `GRID` and friends are **dp** and are multiplied by density
 *    before use;
 *  - `PADDING_*` and `MARGIN_*` are raw **pixel** Int constants, because
 *    `View.setPadding` and `MarginLayoutParams` take Int. Prefer the dp
 *    constants through `LayoutUtils.dpToPx`; the Int ones exist for the
 *    call sites that genuinely cannot.
 */
object SpacingTokens {
    const val INSETS_NONE: Float = 0f
    const val INSETS_XS: Float = 2f
    const val INSETS_SM: Float = 4f
    const val INSETS_MD: Float = 8f
    const val INSETS_LG: Float = 12f
    const val INSETS_XL: Float = 16f
    const val INSETS_XXL: Float = 20f
    const val INSETS_XXXL: Float = 24f
    const val INSETS_XXXL2: Float = 28f
    const val INSETS_XXXL3: Float = 32f
    const val INSETS_XXXL4: Float = 36f
    const val INSETS_XXXL5: Float = 40f
    const val INSETS_XXXL6: Float = 44f
    const val INSETS_XXXL7: Float = 48f
    const val INSETS_XXXL8: Float = 52f
    const val INSETS_XXXL9: Float = 56f
    const val INSETS_XXXL10: Float = 60f
    const val INSETS_XXXL11: Float = 64f
    const val INSETS_XXXL12: Float = 68f
    const val INSETS_XXXL13: Float = 72f
    const val INSETS_XXXL14: Float = 76f
    const val INSETS_XXXL15: Float = 80f
    const val INSETS_XXXL16: Float = 84f
    const val INSETS_XXXL17: Float = 88f
    const val INSETS_XXXL18: Float = 92f
    const val INSETS_XXXL19: Float = 96f
    const val INSETS_XXXL20: Float = 100f

    const val GRID: Float = 8f
    const val HALF_GRID: Float = 4f
    const val QUARTER_GRID: Float = 2f

    // Shorthand aliases matching component call sites.
    const val INSET_MD: Float = INSETS_MD
    const val INSET_LG: Float = INSETS_LG
    const val INSET_XL: Float = INSETS_XL

    // Pixel-scale paddings and margins, used directly by View.setPadding
    // and MarginLayoutParams, so these are Int rather than dp Float.
    const val PADDING_TINY: Int = 2
    const val PADDING_SMALL: Int = 4
    const val PADDING_MEDIUM: Int = 8
    const val PADDING_LARGE: Int = 16

    const val MARGIN_SMALL: Int = 8
    const val MARGIN_MEDIUM: Int = 16
    const val MARGIN_LARGE: Int = 24

    val screenPadding: ScreenPadding = ScreenPadding(
        horizontal = INSETS_XL,
        vertical = INSETS_XL
    )

    val contentPadding: ContentPadding = ContentPadding(
        horizontal = INSETS_XL,
        vertical = INSETS_MD
    )

    val sectionSpacing: Float = INSETS_XXL
    val rowSpacing: Float = INSETS_MD
    val cellSpacing: Float = INSETS_SM

    data class ScreenPadding(val horizontal: Float, val vertical: Float)
    data class ContentPadding(val horizontal: Float, val vertical: Float)
}
