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
 * Tracking deserves a note, because this comment used to contradict itself.
 * Apple publishes tracking in points and that is what is stored here: Body is
 * 0.40, not 0.0004. `Paint.letterSpacing` / `TextView.letterSpacing` take em
 * FRACTIONS, so a consumer must divide by 1000 — which is exactly what
 * `AppleuxText:86` and `AppleuxButton:141` do
 * (`spec.tracking / 1000f`). The previous wording claimed the table was
 * pre-divided and that consumers divide again, which is wrong twice over and
 * would have produced 1000x-too-tight tracking in any new call site.
 *
 * ============================================================================
 * FONT FAMILY — DECIDED 2026-10-17. Read this before changing it.
 * ============================================================================
 * The text face is the platform `sans-serif` family. That was not a shrug; it
 * is what the evidence forces.
 *
 * WHAT THE IPSW ACTUALLY CONTAINS. The six font files harvested from the iOS 27
 * IPSW were identified by reading their `name` tables directly (not by guessing
 * from the filename):
 *
 *   sfui.ttf               -> family " S F UI", subfamily "Regular"    7.9 MB
 *   sfuiitalic.ttf         -> family " S F UI", "Regular Italic"       1.1 MB
 *   symbol.ttf             -> family "Symbol"                            27 KB
 *   applesymbols.ttf       -> family "Apple Symbols"                   895 KB
 *   sfcamera.ttf           -> family ".SF Camera"                      118 KB
 *   sfuisymbols_regular.otf-> family ".SF UI Symbols"                   3 KB
 *
 * NOTE WHAT IS ABSENT: there is no SF Pro Text and no SF Pro Display. Modern
 * iOS (14+) uses SF Pro; the files above carry the older "SF UI" naming, which
 * is the compatibility shell Apple shipped for system and third-party use. So
 * the real iOS 27 text face is simply not in this ROM, and nothing extracted
 * can stand in for it honestly.
 *
 * WHY THE SF UI FILES WERE DELETED RATHER THAN WIRED UP. `sfui.ttf` is
 * Regular-only: one weight, no Medium, no Semibold, no Light. The HIG Dynamic
 * Type table below spans five weights — Large Title is Light/Regular, Headline
 * and Body Emphasized are Semibold. Mapping every one of those onto a
 * single Regular face would flatten the type ramp that is most of what makes
 * iOS look like iOS, and it would do so while LOOKING like we had Apple's real
 * font. Roboto, by contrast, exposes real Light / Regular / Medium / Black
 * faces, so the ramp survives.
 *
 * They were also 8.5 MB per APK with zero consumers: `res/font/` from a library
 * is merged into every APK that links it, so four APKs carried ~34 MB of font
 * that nothing referenced.
 *
 * WHAT IS KEPT. The four symbol faces remain. They are small, they are
 * genuinely symbol/dingbat faces rather than text, and the Control Center may
 * want them. They currently have NO consumer either — the icon path is the 123
 * hand-written VectorDrawables (`sf_*`, `il_ic_*`), which are themeable per
 * appearance and carry no licensing exposure.
 *
 * WHAT TO DO IF YOU WANT THE REAL THING. A redistributable variable font with
 * the same metrics is the only honest route (Inter or Roboto Flex, both
 * open-licensed, are the usual candidates — see the licensing note in
 * AGENTS.md). That is a token-value change in one place plus a
 * `res/font/<name>.ttf` addition; nothing else has to move.
 * ============================================================================
 */
object TypographyTokens {
    const val FONT_FAMILY_SYSTEM: String = "system"
    // Unused legacy aliases, kept spelled correctly so nobody wires them up
    // thinking they name real families. There is no file-level evidence for
    // an "SF Pro"-named family in the IPSW harvest (only older SFUI/SFCompact
    // shells), and we ship system sans in any case — see the header above.
    const val FONT_FAMILY_SCONTROL: String = "San Francisco"
    const val FONT_FAMILY_MONO: String = "SF Mono"
    const val FONT_FAMILY_ROUNDSED: String = "SF Rounded"

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
        tracking = 0.26f
    )

    val title3: FontSpec = FontSpec(
        name = "Title 3",
        family = FONT_FAMILY_SYSTEM,
        size = 20f,
        fontWeight = 400,
        lineHeight = 25f,
        tracking = 0.24f
    )

    val headline: FontSpec = FontSpec(
        name = "Headline",
        family = FONT_FAMILY_SYSTEM,
        size = 17f,
        fontWeight = 600,
        lineHeight = 22f,
        tracking = -0.43f
    )

    val body: FontSpec = FontSpec(
        name = "Body",
        family = FONT_FAMILY_SYSTEM,
        size = 17f,
        fontWeight = 400,
        lineHeight = 22f,
        tracking = -0.43f
    )

    val bodyEmphasized: FontSpec = FontSpec(
        name = "Body Emphasized",
        family = FONT_FAMILY_SYSTEM,
        size = 17f,
        fontWeight = 600,
        lineHeight = 22f,
        tracking = -0.43f
    )

    val callout: FontSpec = FontSpec(
        name = "Callout",
        family = FONT_FAMILY_SYSTEM,
        size = 16f,
        fontWeight = 400,
        lineHeight = 21f,
        tracking = -0.32f
    )

    val subheadline: FontSpec = FontSpec(
        name = "Subheadline",
        family = FONT_FAMILY_SYSTEM,
        size = 15f,
        fontWeight = 400,
        lineHeight = 20f,
        tracking = -0.23f
    )

    val subheadlineEmphasized: FontSpec = FontSpec(
        name = "Subheadline Emphasized",
        family = FONT_FAMILY_SYSTEM,
        size = 15f,
        fontWeight = 600,
        lineHeight = 20f,
        tracking = -0.23f
    )

    val footnote: FontSpec = FontSpec(
        name = "Footnote",
        family = FONT_FAMILY_SYSTEM,
        size = 13f,
        fontWeight = 400,
        lineHeight = 18f,
        tracking = -0.09f
    )

    val footnoteEmphasized: FontSpec = FontSpec(
        name = "Footnote Emphasized",
        family = FONT_FAMILY_SYSTEM,
        size = 13f,
        fontWeight = 600,
        lineHeight = 18f,
        tracking = -0.09f
    )

    val caption1: FontSpec = FontSpec(
        name = "Caption 1",
        family = FONT_FAMILY_SYSTEM,
        size = 12f,
        fontWeight = 400,
        lineHeight = 16f,
        tracking = 0f
    )

    val caption1Emphasized: FontSpec = FontSpec(
        name = "Caption 1 Emphasized",
        family = FONT_FAMILY_SYSTEM,
        size = 12f,
        fontWeight = 600,
        lineHeight = 16f,
        tracking = 0f
    )

    val caption2: FontSpec = FontSpec(
        name = "Caption 2",
        family = FONT_FAMILY_SYSTEM,
        size = 11f,
        fontWeight = 400,
        lineHeight = 13f,
        tracking = 0.07f
    )

    val caption2Emphasized: FontSpec = FontSpec(
        name = "Caption 2 Emphasized",
        family = FONT_FAMILY_SYSTEM,
        size = 11f,
        fontWeight = 600,
        lineHeight = 13f,
        tracking = 0.07f
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
        tracking = 0.26f
    )

    val title3Emphasized: FontSpec = FontSpec(
        name = "Title 3 Emphasized",
        family = FONT_FAMILY_SYSTEM,
        size = 20f,
        fontWeight = 600,
        lineHeight = 25f,
        tracking = 0.24f
    )

    val headlineEmphasized: FontSpec = FontSpec(
        name = "Headline Emphasized",
        family = FONT_FAMILY_SYSTEM,
        size = 17f,
        fontWeight = 600,
        lineHeight = 22f,
        tracking = -0.43f
    )

    data class FontSpec(
        val name: String,
        val family: String,
        val size: Float,
        val fontWeight: Int,
        val lineHeight: Float,
        /**
         * Apple publishes tracking in POINTS at this style's point size.
         *
         * Kept in points so the table matches Apple's own spec, which is the
         * thing worth copying. Do not read this as an em value — see
         * [emTracking] for that.
         */
        val tracking: Float
    ) {
        /**
         * Tracking as Android wants it: a multiple of the text size.
         *
         * `Paint.letterSpacing` and `android:letterSpacing` are both in **em**.
         * Apple publishes **points at a given size**, so the conversion is
         * `points / size` — there is no other correct answer, and it is size-
         * dependent, which is why it must be a computed property in the class
         * BODY and not a stored constant or a constructor argument.
         *
         * THE BUG THIS REPLACES. The previous code did `tracking / 1000f`,
         * which produced 0.0001–0.0004 em for every style in the table — visually
         * zero, i.e. tracking was silently not applied at all. The hand-written
         * XML in this project used -0.02, which is the correct figure for
         * Headline, so the token engine and the layouts disagreed by ~50x.
         *
         * Worked example: Headline is 17pt with -0.43pt tracking, so
         * -0.43 / 17 = -0.0253 em. Body is the same. Caption 1 is 12pt with 0.0
         * tracking, so 0.0 em.
         */
        val emTracking: Float
            get() = if (size <= 0f) 0f else tracking / size
    }
}
