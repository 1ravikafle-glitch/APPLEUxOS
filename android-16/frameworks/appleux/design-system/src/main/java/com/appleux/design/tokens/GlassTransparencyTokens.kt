package com.appleux.design.tokens

/**
 * The iOS 27 **Transparency slider** — the system-wide control that replaces
 * iOS 26's binary Reduce Transparency toggle.
 *
 * PROVENANCE — read before changing any number here.
 *
 * Apple's iOS 27 UI Kit (27.0.2) publishes the model's values in its
 * `materials.json > transparency` block, measured from the official Figma
 * file (`1he05dQDLN4F7TH3bAB3uk`) via the MIT-licensed transcription in
 * `design-reference/ios27/ios27-ds/` — and the clone in
 * `design-reference/ios27-design-system/` agrees with it value for value.
 * The harness checks the five presets against the kit
 * (`invariant.glassTransparencyPresetsMatchKit`).
 *
 * WHAT IT IS: a **continuous, system-wide** slider running from **fully
 * tinted** (0, opaque chrome) to **ultra clear** (1, maximum see-through),
 * and it scales every glass surface's **opacity** and **blur** together. It
 * is a *personalization* control, not an accessibility one.
 *
 * ACCESSIBILITY SITS ON TOP: Reduce Transparency and Increase Contrast remain
 * **separate settings** and must still be honoured — the slider does **not**
 * absorb them. (Apple documents that they have to be switched off before the
 * Liquid Glass appearance can be changed at all; the kit's web reproduction
 * matches that.)
 *
 * HOW A SURFACE USES IT: the surface multiplies **its own** opacity by
 * [opacityScaleFor] and **its own** blur by [blurScaleFor] — never a
 * hard-coded opacity or blur. Two rules C holds:
 *  1. a surface that passes its contrast check at the default can still fail
 *     at ultra-clear or fully-tinted, so the contrast read covers **both
 *     ends** of the slider, not just the default;
 *  2. the icon gyroscopic shimmer is gone in iOS 27, so no icon may depend on
 *     motion to stay legible.
 *
 * This file is the token layer and therefore the single source of truth for
 * these values. There is deliberately **no XML dimen** for them: the glass
 * surfaces read them from Kotlin, so a second copy in resources would be a
 * second source of truth.
 */
object GlassTransparencyTokens {

    /** 0 = fully tinted (opaque), 1 = ultra clear (maximum transparency). */
    const val TRANSPARENCY_MIN = 0f
    const val TRANSPARENCY_MAX = 1f

    /** The system's default position on the slider. */
    const val TRANSPARENCY_DEFAULT = 0.5f

    // ---- The continuous derivation -------------------------------------
    // The kit's own `_derive` block, in the kit's own form: a base plus a
    // signed slope times the slider position. The opacity scale therefore
    // FALLS 1.6 -> 0.5 as the slider rises, and the blur scale CLIMBS
    // 0.5 -> 1.3.

    const val OPACITY_SCALE_BASE = 1.6f
    const val OPACITY_SCALE_SLOPE = -1.1f
    const val BLUR_SCALE_BASE = 0.5f
    const val BLUR_SCALE_SLOPE = 0.8f

    /** The opacity multiplier for a slider position (kit `_derive`). */
    fun opacityScaleFor(transparency: Float): Float =
        OPACITY_SCALE_BASE + OPACITY_SCALE_SLOPE * transparency.coerceIn(TRANSPARENCY_MIN, TRANSPARENCY_MAX)

    /** The blur multiplier for a slider position (kit `_derive`). */
    fun blurScaleFor(transparency: Float): Float =
        BLUR_SCALE_BASE + BLUR_SCALE_SLOPE * transparency.coerceIn(TRANSPARENCY_MIN, TRANSPARENCY_MAX)

    // ---- The five preset classes ---------------------------------------
    // The kit's measured pairs. They sit slightly OFF the derived line, so
    // the PRESET values are the authority for a preset position and the
    // derivation above is for the positions in between.

    /** Fully tinted: opaque chrome, the nearest thing to iOS 26's look. */
    const val PRESET_FULLY_TINTED_VALUE = 0f
    const val PRESET_FULLY_TINTED_OPACITY_SCALE = 1.6f
    const val PRESET_FULLY_TINTED_BLUR_SCALE = 0.5f

    /** Tinted. */
    const val PRESET_TINTED_VALUE = 0.25f
    const val PRESET_TINTED_OPACITY_SCALE = 1.325f
    const val PRESET_TINTED_BLUR_SCALE = 0.75f

    /** The system default: already more diffused than iOS 26's default. */
    const val PRESET_DEFAULT_VALUE = 0.5f
    const val PRESET_DEFAULT_OPACITY_SCALE = 1.05f
    const val PRESET_DEFAULT_BLUR_SCALE = 1.0f

    /** Clear. */
    const val PRESET_CLEAR_VALUE = 0.75f
    const val PRESET_CLEAR_OPACITY_SCALE = 0.775f
    const val PRESET_CLEAR_BLUR_SCALE = 1.15f

    /**
     * Ultra clear: maximum transparency. The kit's own note — *"Verify
     * foreground contrast per surface"* — is the rule C turned into an
     * invariant.
     */
    const val PRESET_ULTRA_CLEAR_VALUE = 1f
    const val PRESET_ULTRA_CLEAR_OPACITY_SCALE = 0.5f
    const val PRESET_ULTRA_CLEAR_BLUR_SCALE = 1.3f
}
