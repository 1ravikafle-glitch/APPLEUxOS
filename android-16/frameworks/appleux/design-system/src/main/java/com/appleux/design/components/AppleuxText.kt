package com.appleux.design.components

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.widget.TextView
import com.appleux.design.tokens.TypographyTokens

/**
 * The single text view of the design system.
 *
 * Its whole point is that no caller gets to invent a type scale. Every
 * AppleUX surface should reach for [applyTypography] with a
 * [TypographyTokens.FontSpec] — the transcribed HIG Dynamic Type table — or
 * for one of the [TextStyle] presets below, which map onto the same table.
 *
 * Weights resolve against the platform `sans-serif` family because SF Pro
 * cannot be redistributed in an APK; see TypographyTokens' header. Before this
 * was centralised, `LIGHT`, `MEDIUM` and `REGULAR` all resolved to
 * `Typeface.NORMAL`, so the "lightweight lock screen clock" this design system
 * is named for was impossible to draw.
 *
 * Sizes are applied in **sp**, not px, so the platform font-scale
 * accessibility setting still works.
 */
class AppleuxText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : TextView(context, attrs, defStyleAttr) {

    /**
     * Coarse weight presets. Prefer [applyTypography] with a
     * [TypographyTokens.FontSpec] when a full HIG style is wanted; these exist
     * for call sites that only care about how heavy the text looks.
     */
    enum class TextStyle(val fontWeight: Int) {
        /** HIG Body, 17pt regular. */
        REGULAR(400),

        /** HIG Body Emphasized, 17pt semibold. */
        MEDIUM(600),

        /** HIG Body Emphasized, 17pt semibold. */
        SEMIBOLD(600),

        /** HIG Headline, 17pt bold. */
        BOLD(700),

        /**
         * HIG Large Title at the platform's Light weight. AppleUX-chosen:
         * SF Pro Display Light has no named slot in the HIG table, it is the
         * weight the Lock Screen clock uses.
         */
        LIGHT(300),

        /** HIG Large Title, 34pt. */
        LARGE_TITLE(400),

        /** HIG Title 1, 28pt. */
        TITLE1(400),

        /** HIG Subheadline, 15pt regular. */
        SUBHEADLINE(400),

        /** HIG Footnote, 13pt regular. */
        FOOTNOTE(400),

        /** HIG Caption 1, 12pt regular. */
        CAPTION(400)
    }

    init {
        // Baseline from the shared scale, so an AppleuxText with no explicit
        // styling is still on-system rather than on TextView's 14sp default.
        applyTypography(TypographyTokens.body)
    }

    /**
     * Applies a transcribed HIG style: size (sp), tracking and weight.
     * This is the API new code should use.
     */
    fun applyTypography(spec: TypographyTokens.FontSpec) {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, spec.size)
        letterSpacing = spec.tracking / 1000f
        typeface = typefaceForWeight(spec.fontWeight)
    }

    /**
     * Sets the weight only.
     *
     * Deliberately does not touch the size. Several call sites set `textSize`
     * explicitly and then call this; making it resize the text would silently
     * override them.
     */
    fun setTextStyle(style: TextStyle) {
        typeface = typefaceForWeight(style.fontWeight)
    }

    /** Puts a leading SF-Symbol-style glyph before the text. */
    fun setTextIcon(iconRes: Int) {
        setCompoundDrawablesWithIntrinsicBounds(iconRes, 0, 0, 0)
    }

    companion object {
        // Real faces from the platform sans-serif family. Using these instead
        // of Typeface.NORMAL is what makes a 400-weight clock look like a clock.
        private const val FAMILY_THIN = "sans-serif-thin"
        private const val FAMILY_LIGHT = "sans-serif-light"
        private const val FAMILY_MEDIUM = "sans-serif-medium"
        private const val FAMILY_REGULAR = "sans-serif"

        /**
         * Maps a CSS-style numeric weight onto the closest face the platform
         * actually ships. Shared with [AppleuxButton], which paints its own
         * text rather than hosting an [AppleuxText].
         *
         * AppleUX-chosen mapping — see this file's header.
         */
        @JvmStatic
        fun typefaceForWeight(fontWeight: Int): Typeface = when {
            fontWeight >= 700 -> Typeface.create(FAMILY_REGULAR, Typeface.BOLD)
            fontWeight >= 500 -> Typeface.create(FAMILY_MEDIUM, Typeface.NORMAL)
            fontWeight <= 300 -> Typeface.create(FAMILY_THIN, Typeface.NORMAL)
            fontWeight <= 400 -> Typeface.create(FAMILY_LIGHT, Typeface.NORMAL)
            else -> Typeface.create(FAMILY_REGULAR, Typeface.NORMAL)
        }
    }
}