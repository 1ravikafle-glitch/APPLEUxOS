package com.appleux.design.tokens

import android.graphics.Color
import java.util.concurrent.ConcurrentHashMap

/**
 * Shadow levels.
 *
 * PROVENANCE — **AppleUX-chosen, and not from Apple at all.** iOS does not
 * have a Material-style elevation scale: a shadow on iOS is a property of a
 * specific component (a sheet casts y=8 blur=48; a card casts nothing), not a
 * level on a ramp. The alphas below are Material Design 3's, kept because they
 * are a sane ramp, and the levels map onto
 * [com.appleux.design.components.AppleuxSurface.surfaceElevation], which feeds
 * the platform's real `View.elevation` so the compositor draws the shadow.
 *
 * Do not present these as iOS values in any deliverable.
 */
object ElevationTokens {
    const val ELEVATION_NONE: Float = 0f
    const val ELEVATION_XS: Float = 1f
    const val ELEVATION_SM: Float = 2f
    const val ELEVATION_MD: Float = 4f
    const val ELEVATION_LG: Float = 8f
    const val ELEVATION_XL: Float = 12f
    const val ELEVATION_XXL: Float = 24f
    const val ELEVATION_XXXL: Float = 48f

    val shadowColor: Int = Color.parseColor("#000000")
    val shadowColorSurface: Int = Color.parseColor("#000000")

    val surfaceShadowAlphaXS: Float = 0.04f
    val surfaceShadowAlphaSM: Float = 0.06f
    val surfaceShadowAlphaMD: Float = 0.08f
    val surfaceShadowAlphaLG: Float = 0.10f
    val surfaceShadowAlphaXL: Float = 0.12f
    val surfaceShadowAlphaXXL: Float = 0.16f
    val surfaceShadowAlphaXXXL: Float = 0.22f

    /**
     * Memoised shadow specs.
     *
     * A `ConcurrentHashMap` rather than the `HashMap` this used to hold: the
     * old map was written from [getShadow] on every draw, from whichever
     * thread drew, with no synchronisation. It also had no bound — a caller
     * passing arbitrary elevations would grow it forever. `getShadow` now
     * quantises the elevation onto the eight declared levels first, so the
     * cache holds at most eight entries by construction.
     */
    private val shadowCache = ConcurrentHashMap<Int, ShadowSpec>()

    data class ShadowSpec(
        val elevation: Float,
        val shadowColor: Int,
        val alpha: Float,
        val radius: Float,
        val offsetX: Float,
        val offsetY: Float
    )

    /** Snaps an arbitrary elevation onto the nearest declared level. */
    fun quantise(elevation: Float): Float = when {
        elevation <= ELEVATION_NONE -> ELEVATION_NONE
        elevation <= ELEVATION_XS -> ELEVATION_XS
        elevation <= ELEVATION_SM -> ELEVATION_SM
        elevation <= ELEVATION_MD -> ELEVATION_MD
        elevation <= ELEVATION_LG -> ELEVATION_LG
        elevation <= ELEVATION_XL -> ELEVATION_XL
        elevation <= ELEVATION_XXL -> ELEVATION_XXL
        else -> ELEVATION_XXXL
    }

    fun getShadow(elevation: Float): ShadowSpec {
        val level = quantise(elevation)
        shadowCache[level.toInt()]?.let { return it }

        val alpha = when (level) {
            ELEVATION_NONE -> 0f
            ELEVATION_XS -> surfaceShadowAlphaXS
            ELEVATION_SM -> surfaceShadowAlphaSM
            ELEVATION_MD -> surfaceShadowAlphaMD
            ELEVATION_LG -> surfaceShadowAlphaLG
            ELEVATION_XL -> surfaceShadowAlphaXL
            ELEVATION_XXL -> surfaceShadowAlphaXXL
            else -> surfaceShadowAlphaXXXL
        }

        val spec = ShadowSpec(
            elevation = level,
            shadowColor = shadowColor,
            alpha = alpha,
            radius = level * 2f,
            offsetX = 0f,
            offsetY = level * 1.5f
        )
        shadowCache[level.toInt()] = spec
        return spec
    }
}
