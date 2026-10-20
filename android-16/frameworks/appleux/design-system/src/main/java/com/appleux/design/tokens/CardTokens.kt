package com.appleux.design.tokens

/**
 * The corner geometry of a **card** — the grouped list cards and the profile
 * (account) card of the Settings root.
 *
 * PROVENANCE — **measured, 2026-10-21 (C), off the GT home frame**
 * `settings-root-light-top.png` (828×1792 @2x, iPhone 11).
 *
 * **THE METHOD MATTERS, because the earlier fits were wrong.** Fitting a
 * circular arc to the corner gave two irreconcilable answers — 13.0–14.0 pt
 * from two corners of the profile card and 24.5–25.5 pt from one corner of a
 * lower card — and the difference was the **assumed card top** the fit was
 * anchored to. The shape is not a circle, so a circle fit moves with its
 * anchor.
 *
 * The measurement that does not assume anything is the **extent**: trace the
 * card's actual boundary (the first pixel at or above the card's fill
 * luminance) row by row from the card's top, and fit a **symmetric
 * superellipse** `|x−xc|ⁿ + |y−yc|ⁿ = aⁿ` to the traced points, with the
 * straight side and the top edge taken from the trace itself. Three cards,
 * six corners, one answer:
 * ```
 * profile card (y 308..487)  a = 69.0 px, b = 68.5 px, n = 3.2   RMS 1.23 px
 * list card A  (y 558..827)  a = 69.0 px, b = 68.5 px, n = 3.2   RMS 1.34 px
 * list card D  (y 1288..1395) a = 69.0 px, b = 68.5 px, n = 3.2   RMS 1.34 px
 * ```
 * So the GT's Settings card corner is **one shape everywhere**:
 * a symmetric superellipse of **radius 69 px = 34.5 pt = 32.7 dp** and
 * **exponent ≈ 3.2**.
 *
 * **The values.** 32.7 dp lands on the kit's own **`radius.glass` = 34**
 * (the iOS 27 kit's Regular glass radius — the same 34 the glass material
 * uses), so the card corner is the **glass radius**, and the exponent is
 * **3.2** — between the icon's 4.4 squircle and a plain circle, which is
 * what a 34 pt card corner on a 2× screen actually looks like.
 *
 * **What was wrong in the ROM:** the row groups drew **26 dp** and the
 * account card **12 dp** — two radii for one thing, and neither is the GT's.
 * One radius now, and one radius for every Settings card.
 *
 * Note the exponent is part of the fact: a 34 dp corner drawn with the
 * icon's n=4.4 squircle is visibly squarer than the GT's, so the exponent
 * has to travel with the radius.
 */
object CardTokens {

    /** The card corner radius — the kit's `radius.glass` (34). */
    const val CARD_CORNER_RADIUS_DP: Float = 34f

    /** The card corner's superellipse exponent (measured 3.2). */
    const val CARD_CORNER_EXPONENT: Float = 3.2f
}
