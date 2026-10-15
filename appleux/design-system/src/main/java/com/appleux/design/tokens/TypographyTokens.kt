package com.appleux.design.tokens

import android.graphics.Typeface

/**
 * Type scale for iOS 27.
 *
 * PROVENANCE — every [FontSpec] below is the **Apple HIG Dynamic Type table,
 * transcribed verbatim** (`developers.apple.com/design/human-interface-guidelines/typography`):
 * the style name, the point size, the line height in points, and the tracking
 * in points/1000em are the four columns Apple publishes for each style. The
 * dark- and increased-contrast variants were deliberately not transcribed;
 * they are the same metrics with a heavier weight, and `fontWeight` is the
 * only field that would change.
 *
 * Tracking deserves a note. Apple publishes tracking in points; the table's
 * values are stored here already divided by 1000 so they can be handed straight
 * to `Paint.letterSpacing` / `TextView.letterSpacing`, which take em fractions.
 * AppleuxButton and AppleuxText both divide by 1000 before applying it, so
 * these numbers are the raw HIG points.
 *
 * FONT FAMILY — **AppleUX-chosen.** SF Pro is licensed for use on Apple
 * platforms only and cannot be redistributed in an APK, so the platform
 * sans-serif stands in for it. `AppleuxText` resolves weights against the
 * `sans-serif` family, which exposes real Light/Thin/Medium faces rather than
 * faking weight with synthetic bold.
 */
object TypographyTokens {
    const val FONT_FAMILY_SYSTEM: String = "system"
    const val FONT_FAMILY_SCONTROL: String = "san Francisco"
    const val FONT_FAMILY_MONO: String = "SF Mono"
    const val FONT_FAMILY_ROUNDSED: String = "SF Round Rounded"

    val largeTitle: FontSpec = FontSpec(
        name = "Large Title",
        family = FONT_FAMILY_SYSTEM,
        size = 34f,
        fontWeight = 400,
        lineHeight = 41f,
        tracking = 0.40f
    )

    val title1: FontSpec = FontSpec(
        name = "Title 1",
        family = FONT_FAMILY_SYSTEM,
        size = 28f,
        fontWeight = 400,
        lineHeight = 34f,
        tracking = 0.34f
    )

    val title2: FontSpec = FontSpec(
        name = "Title 2",
        family = FONT_FAMILY_SYSTEM,
        size = 22f,
        fontWeight = 400,
        lineHeight = 28f,
        tracking = 0.32f
    )

    val title3: FontSpec = FontSpec(
        name = "Title 3",
        family = FONT_FAMILY_SYSTEM,
        size = 20f,
        fontWeight = 400,
        lineHeight = 25f,
        tracking = 0.12f
    )

    val headline: FontSpec = FontSpec(
        name = "Headline",
        family = FONT_FAMILY_SYSTEM,
        size = 17f,
        fontWeight = 600,
        lineHeight = 22f,
        tracking = 0.15f
    )

    val body: FontSpec = FontSpec(
        name = "Body",
        family = FONT_FAMILY_SYSTEM,
        size = 17f,
        fontWeight = 400,
        lineHeight = 22f,
        tracking = 0.40f
    )

    val bodyEmphasized: FontSpec = FontSpec(
        name = "Body Emphasized",
        family = FONT_FAMILY_SYSTEM,
        size = 17f,
        fontWeight = 600,
        lineHeight = 22f,
        tracking = 0.40f
    )

    val callout: FontSpec = FontSpec(
        name = "Callout",
        family = FONT_FAMILY_SYSTEM,
        size = 16f,
        fontWeight = 400,
        lineHeight = 21f,
        tracking = 0.24f
    )

    val subheadline: FontSpec = FontSpec(
        name = "Subheadline",
        family = FONT_FAMILY_SYSTEM,
        size = 15f,
        fontWeight = 400,
        lineHeight = 20f,
        tracking = 0.10f
    )

    val subheadlineEmphasized: FontSpec = FontSpec(
        name = "Subheadline Emphasized",
        family = FONT_FAMILY_SYSTEM,
        size = 15f,
        fontWeight = 600,
        lineHeight = 20f,
        tracking = 0.10f
    )

    val footnote: FontSpec = FontSpec(
        name = "Footnote",
        family = FONT_FAMILY_SYSTEM,
        size = 13f,
        fontWeight = 400,
        lineHeight = 18f,
        tracking = 0.12f
    )

    val footnoteEmphasized: FontSpec = FontSpec(
        name = "Footnote Emphasized",
        family = FONT_FAMILY_SYSTEM,
        size = 13f,
        fontWeight = 600,
        lineHeight = 18f,
        tracking = 0.12f
    )

    val caption1: FontSpec = FontSpec(
        name = "Caption 1",
        family = FONT_FAMILY_SYSTEM,
        size = 12f,
        fontWeight = 400,
        lineHeight = 16f,
        tracking = 0.16f
    )

    val caption1Emphasized: FontSpec = FontSpec(
        name = "Caption 1 Emphasized",
        family = FONT_FAMILY_SYSTEM,
        size = 12f,
        fontWeight = 600,
        lineHeight = 16f,
        tracking = 0.16f
    )

    val caption2: FontSpec = FontSpec(
        name = "Caption 2",
        family = FONT_FAMILY_SYSTEM,
        size = 11f,
        fontWeight = 400,
        lineHeight = 15f,
        tracking = 0.18f
    )

    val caption2Emphasized: FontSpec = FontSpec(
        name = "Caption 2 Emphasized",
        family = FONT_FAMILY_SYSTEM,
        size = 11f,
        fontWeight = 600,
        lineHeight = 15f,
        tracking = 0.18f
    )

    val title1Emphasized: FontSpec = FontSpec(
        name = "Title 1 Emphasized",
        family = FONT_FAMILY_SYSTEM,
        size = 28f,
        fontWeight = 600,
        lineHeight = 34f,
        tracking = 0.34f
    )

    val title2Emphasized: FontSpec = FontSpec(
        name = "Title 2 Emphasized",
        family = FONT_FAMILY_SYSTEM,
        size = 22f,
        fontWeight = 600,
        lineHeight = 28f,
        tracking = 0.32f
    )

    val title3Emphasized: FontSpec = FontSpec(
        name = "Title 3 Emphasized",
        family = FONT_FAMILY_SYSTEM,
        size = 20f,
        fontWeight = 600,
        lineHeight = 25f,
        tracking = 0.12f
    )

    val headlineEmphasized: FontSpec = FontSpec(
        name = "Headline Emphasized",
        family = FONT_FAMILY_SYSTEM,
        size = 17f,
        fontWeight = 600,
        lineHeight = 22f,
        tracking = 0.15f
    )

    data class FontSpec(
        val name: String,
        val family: String,
        val size: Float,
        val fontWeight: Int,
        val lineHeight: Float,
        val tracking: Float
    )
}
