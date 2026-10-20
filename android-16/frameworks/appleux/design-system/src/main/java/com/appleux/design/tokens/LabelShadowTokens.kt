package com.appleux.design.tokens

/**
 * Legibility shadows for labels that sit **straight on the wallpaper**.
 *
 * PROVENANCE — **AppleUX-chosen.** iOS publishes no shadow geometry for a
 * home-screen label, and our GT frames carry none (the home widget card
 * measures with **no** drop shadow below its edge — the wallpaper resumes at
 * the card's own edge, checked at x=150/300 of `home-light-alt.png`). So this
 * is our own convention, stated here so it is a rule and not a guess.
 *
 * WHY A SHADOW AT ALL: the labels are **white** on a wallpaper that can be
 * pale (and in light mode `ColorTokens.label` is near-black and vanished on
 * it), so the shadow is what carries the contrast. It is a **text** shadow —
 * `setShadowLayer` on the TextView — not a card or tile shadow.
 *
 * THE RULE: the shadow scales with the label's **own text size**:
 * ```
 * blur    ~= 0.31 x the text size (dp)
 * offsetY ~= 0.08 x the text size (dp)
 * ```
 * and the unit is **dp** everywhere. `setShadowLayer` takes **pixels**, so
 * every call site must scale by the display density — a raw `8f` there is
 * 8 px, i.e. 2.9 dp on our panel, not 8 dp.
 *
 * The two named pairs below are the two labels on the launcher surface that
 * need it (the App Library's folder title and its category label). They are
 * two DIFFERENT labels at two DIFFERENT sizes — which is why their values
 * differ; they are not the same element measured two ways.
 */
object LabelShadowTokens {

    /** The App Library's folder title — 26 sp, bold, on the wallpaper. */
    const val APP_LIBRARY_TITLE_SHADOW_BLUR_DP = 8f
    const val APP_LIBRARY_TITLE_SHADOW_OFFSET_Y_DP = 2f

    /** The App Library's category label — 11 sp, on the wallpaper. */
    const val APP_LIBRARY_CATEGORY_SHADOW_BLUR_DP = 4f
    const val APP_LIBRARY_CATEGORY_SHADOW_OFFSET_Y_DP = 1f
}
