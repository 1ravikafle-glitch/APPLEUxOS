package com.appleux.design.tokens

import android.graphics.Color

/**
 * The **Increase Contrast** appearance — the third system appearance, next to
 * light and dark.
 *
 * PROVENANCE — read before changing any number here.
 *
 * **The published half is Apple's.** HIG → Color ("System colors" and "iOS,
 * iPadOS system gray colors") publishes an **"Increased contrast (light)"** and
 * **"Increased contrast (dark)"** column for every palette colour and every
 * step of the gray ramp, with exact RGBs in the swatch alt-text. Those values
 * are transcribed here verbatim, and the harness checks them against the
 * harvested page (`invariant.increaseContrastValuesMatchHig`).
 *
 * **The semantic half is OURS, and is labelled as such.** Apple publishes **no
 * increased-contrast values for the semantic colours** — the label family,
 * the backgrounds, the fills and the separators. The system builds those
 * internally, and only publishes the *mechanism* the HIG states: *"With the
 * Increase Contrast setting turned on, the color differences become far more
 * apparent."* So the values for those are **AppleUX-chosen**, and C's rule is
 * written down rather than guessed at each call site:
 *
 * > **In increased contrast, every step of the label ramp moves twenty
 * > points toward the opaque end, the fills and the separators go to full
 * > opacity, and the backgrounds do not change** (they are already at their
 * > extremes).
 *
 * The twenty points come from the published gray ramp's own behaviour: in
 * increased contrast **each gray steps one step toward the extreme** (the
 * light gray 142 → 108, 174 → 142, 199 → 174 … and the dark ramp likewise
 * lighter), so the label ramp takes the same one-rung step.
 *
 * HOW IT IS USED: this is a **third appearance**, not a fourth palette. The
 * surfaces ask the token layer which appearance they are in and read the
 * matching value — the same way the light/dark pair already resolves. The
 * resolution mechanism is application code; the values are here.
 *
 * ACCESSIBILITY SITS ON TOP OF THE TRANSPARENCY SLIDER: the iOS 27 slider
 * (see `GlassTransparencyTokens`) and this setting are **separate**, and both
 * must be honoured — the slider does not absorb Increase Contrast, and neither
 * does Increase Contrast absorb the slider.
 */
object IncreaseContrastTokens {

    // ---- The published increased-contrast palette (HIG -> Color) ---------
    // The HIG publishes these as "Increased contrast (light)" and
    // "Increased contrast (dark)"; they are Apple's, not ours.

    val systemRedIncreasedLight: Int = Color.parseColor("#FFE9152D")
    val systemRedIncreasedDark: Int = Color.parseColor("#FFFF6165")
    val systemOrangeIncreasedLight: Int = Color.parseColor("#FFC55300")
    val systemOrangeIncreasedDark: Int = Color.parseColor("#FFFFA056")
    val systemYellowIncreasedLight: Int = Color.parseColor("#FFA16A00")
    val systemYellowIncreasedDark: Int = Color.parseColor("#FFFEDF43")
    val systemGreenIncreasedLight: Int = Color.parseColor("#FF008932")
    val systemGreenIncreasedDark: Int = Color.parseColor("#FF4AD968")
    val systemMintIncreasedLight: Int = Color.parseColor("#FF008575")
    val systemMintIncreasedDark: Int = Color.parseColor("#FF54DFCB")
    val systemTealIncreasedLight: Int = Color.parseColor("#FF008198")
    val systemTealIncreasedDark: Int = Color.parseColor("#FF3BDDEC")
    val systemCyanIncreasedLight: Int = Color.parseColor("#FF007EAE")
    val systemCyanIncreasedDark: Int = Color.parseColor("#FF6DD9FF")
    val systemBlueIncreasedLight: Int = Color.parseColor("#FF1E6EF4")
    val systemBlueIncreasedDark: Int = Color.parseColor("#FF5CB8FF")
    val systemIndigoIncreasedLight: Int = Color.parseColor("#FF564ADE")
    val systemIndigoIncreasedDark: Int = Color.parseColor("#FFA7AAFF")
    val systemPurpleIncreasedLight: Int = Color.parseColor("#FFB02FC2")
    val systemPurpleIncreasedDark: Int = Color.parseColor("#FFEA8DFF")
    val systemPinkIncreasedLight: Int = Color.parseColor("#FFE7124D")
    val systemPinkIncreasedDark: Int = Color.parseColor("#FFFF8AC4")
    val systemBrownIncreasedLight: Int = Color.parseColor("#FF956D51")
    val systemBrownIncreasedDark: Int = Color.parseColor("#FFDBA679")

    // ---- The published increased-contrast gray ramp (HIG -> Color) -------
    // The HIG's "Gray" .. "Gray (6)" rows. Note the ramp's ENDS: the light
    // values step darker and the dark values step lighter - that one-rung
    // step is the pattern C's rule for the label ramp is built on.

    val systemGray1IncreasedLight: Int = Color.parseColor("#FF6C6C70")
    val systemGray1IncreasedDark: Int = Color.parseColor("#FFAEAEB2")
    val systemGray2IncreasedLight: Int = Color.parseColor("#FF8E8E93")
    val systemGray2IncreasedDark: Int = Color.parseColor("#FF7C7C80")
    val systemGray3IncreasedLight: Int = Color.parseColor("#FFAEAEB2")
    val systemGray3IncreasedDark: Int = Color.parseColor("#FF545456")
    val systemGray4IncreasedLight: Int = Color.parseColor("#FFBCBCC0")
    val systemGray4IncreasedDark: Int = Color.parseColor("#FF444446")
    val systemGray5IncreasedLight: Int = Color.parseColor("#FFD8D8DC")
    val systemGray5IncreasedDark: Int = Color.parseColor("#FF363638")
    val systemGray6IncreasedLight: Int = Color.parseColor("#FFEBEBF0")
    val systemGray6IncreasedDark: Int = Color.parseColor("#FF242426")

    // ---- C's rule for the semantic ramp (AppleUX-chosen) -----------------
    // Apple publishes no increased-contrast values for the semantic colours,
    // so the rule is written down here instead of being re-guessed at every
    // call site. It follows the published gray ramp's own one-rung step.

    /** How far each label-ramp step moves toward the opaque end. */
    const val LABEL_ALPHA_STEP_INCREASED: Int = 20

    /**
     * The increased-contrast alpha for a label-ramp step: one rung toward
     * the opaque end, never past it.
     */
    fun increasedLabelAlpha(baseAlpha: Int): Int =
        (baseAlpha + LABEL_ALPHA_STEP_INCREASED).coerceIn(0, 255)
}
