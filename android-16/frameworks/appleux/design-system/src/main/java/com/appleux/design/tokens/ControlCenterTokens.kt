package com.appleux.design.tokens

/**
 * The Control Center's **tile grid** — the rhythm of the 4-column module
 * grid and the metrics of a tile in it.
 *
 * PROVENANCE — **measured 2026-10-20 (C) off the GT Control Center frame**
 * (`cc-dark-full.png`, 828×1792 @2x): the saturated tile glyphs were located
 * by saturation and the white ink under each row gave the label bands.
 *
 * ```
 * columns                     4
 * column pitch (cell to cell) 168 px = 84 pt
 * row pitch                   168 px = 84 pt
 * visible tile glyph          138 px = 69 pt      (the gap around it ≈ 15 dp)
 * tile label ink height       10-11 px = 5.0-5.5 pt (an x-height)
 * grid side margin            x 92..733 -> ~46 pt
 * ```
 *
 * **The label size (C, 2026-10-21).** HIG → Typography, "Ensuring
 * legibility", publishes iOS's **minimum text size as 11 pt** and says
 * plainly to *follow the recommended default and minimum text sizes for each
 * platform* — that is a **size** rule, separate from the contrast rule the
 * Control Center spec previously leaned on. The GT's own tile label measures
 * an x-height of 5.0–5.5 pt, which is a **9.6–11.0 pt** face at San
 * Francisco's 0.5 x-height ratio — so the photo **straddles** the floor and
 * cannot settle it. The HIG settles it: **11 pt**, which is also our ramp's
 * smallest step (`caption2`) and exactly Apple's minimum ✓. So the label is
 * **11 sp**, not the 10 sp the ROM hard-coded.
 *
 * **The tiles are CIRCULAR** in iOS 27 (the glyph is a filled circle), not
 * rounded rectangles — so the glass corner radius does not apply to them.
 *
 * These tokens are the **named target**, not a change: adopting a different
 * pitch is visible across all 55 Control Center modules, so **@A** decides
 * whether the grid is rebuilt on them and **@B** verifies against the frame.
 */
object ControlCenterTokens {

    /** The grid's cell-to-cell pitch (both axes). */
    const val CC_TILE_PITCH_DP: Float = 84f

    /** The visible glyph inside a cell (the tile's circle). */
    const val CC_TILE_GLYPH_DP: Float = 69f

    /** The tile's label — the HIG's iOS minimum text size (11 pt). */
    const val CC_TILE_LABEL_DP: Float = 11f

    /** The grid's side margin. */
    const val CC_TILE_SIDE_MARGIN_DP: Float = 46f
}
