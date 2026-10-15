package com.appleux.design.tokens

/**
 * Liquid Glass material constants for iOS 27.
 *
 * PROVENANCE — read before changing any number here.
 *
 * Apple publishes **no numeric spec** for the Liquid Glass material. What
 * follows is transcribed from the official iOS 27 Figma UI Kit's own layer
 * values (fills, drop shadows, inner shadows, background blurs, corner
 * radii), via the MIT-licensed `ios27-design-system` transcription in
 * `design-reference/ios27/ios27-ds/`.
 *
 * The Figma kit renders the material with a GLASS effect whose parameters the
 * Figma REST API does not expose, and whether the kit's construction matches
 * how iOS composites it at runtime is unverified. Treat these as *the designer's
 * values* — which is what a design hand-off actually contains — not as Apple's
 * runtime parameters.
 *
 * Everything NOT in this file (blur radius in px, saturation, the adaptive
 * scaling for the 3 GB budget) stays AppleUX-tuned, because the kit has no
 * counterpart for it. Those are marked AppleUX in [AppleuxGlass].
 */
object LiquidGlassTokens {

    // ---- Liquid Glass - Regular (Large / Medium) -------------------------
    // Measured identical in 27.0.2: same fills, shadows and 34pt radius.

    const val REGULAR_CORNER_RADIUS_DP = 34f

    /** Fill behind the blurred content, light appearance: #FFFFFF @ a=0.70. */
    const val REGULAR_BG_LIGHT = 0xB2FFFFFF.toInt()
    const val REGULAR_BG_LIGHT_ALPHA = 0.70f

    /** Fill behind the blurred content, dark appearance: #1A1A1A @ a=0.70. */
    const val REGULAR_BG_DARK = 0xB21A1A1A.toInt()
    const val REGULAR_BG_DARK_ALPHA = 0.70f

    /** Secondary light-mode overlay (#BFBFBF @ 0.10). */
    const val REGULAR_OVERLAY_LIGHT = 0x1ABFBFBF

    /** Drop shadow beneath a glass surface. */
    const val SHADOW_BLUR_DP = 48f
    const val SHADOW_OFFSET_Y_DP = 8f
    const val SHADOW_ALPHA_LIGHT = 0.25f
    const val SHADOW_ALPHA_DARK = 0.45f

    // ---- Liquid Glass - Clear -------------------------------------------
    // Only over media-rich or colourful content; add a scrim if legibility
    // is at risk. The kit uses a much tighter radius than Regular.

    const val CLEAR_CORNER_RADIUS_DP = 23f
    const val CLEAR_BASE = 0xFF101010.toInt()
    const val CLEAR_OVERLAY = 0x0AFFFFFF // #FFFFFF @ a=0.04
    const val CLEAR_SHADOW_BLUR_DP = 15f
    const val CLEAR_SHADOW_ALPHA = 0.02f

    // ---- Rim (the outline around a glass surface) ------------------------
    // This is what separates glass from the content beneath. Modelled as
    // zero-blur drop shadows with positive spread.

    const val RIM_SPREAD_DP = 0.5f
    const val RIM_SIDE_OFFSET_DP = 1.25f
    const val RIM_SIDE_SPREAD_DP = -0.75f
    const val RIM_LIGHT = 0xFFDBDBDB.toInt()
    const val RIM_DARK = 0xFFA6A6A6.toInt()

    // ---- Inner shadow (reads as the lit rim on device) -------------------
    // Applied twice, at +40 and -40 on Y.

    const val INNER_SHADOW_BLUR_DP = 10f
    const val INNER_SHADOW_OFFSET_Y_DP = 40f
    const val INNER_SHADOW_SPREAD_DP = -40f
    const val INNER_SHADOW_LIGHT = 0xFF282828.toInt()
    const val INNER_SHADOW_DARK = 0xFF1A1A1A.toInt()

    // ---- Transparency slider (new in iOS 27) -----------------------------
    // Replaces the binary Reduce Transparency toggle. t = 0 fully tinted
    // (opaque), t = 1 ultra clear. Default 0.5.
    //
    // The two curves below are the kit transcription's OWN model, not Apple
    // values: the slider is a runtime system setting with no kit
    // representation. They were validated against a native screenshot
    // (at t=0.5 the model yields alpha 0.735 vs 0.729 measured, 0.8% off).

    const val TRANSPARENCY_DEFAULT = 0.5f

    fun opacityScale(t: Float): Float = 1.6f + (-1.1f) * t.coerceIn(0f, 1f)

    fun blurScale(t: Float): Float = 0.5f + 0.8f * t.coerceIn(0f, 1f)
}