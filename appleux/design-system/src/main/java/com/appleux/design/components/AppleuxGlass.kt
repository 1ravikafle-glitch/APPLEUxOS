package com.appleux.design.components

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.View
import com.appleux.design.tokens.LiquidGlassTokens

/**
 * Liquid-Glass-style material engine (AppleUX interpretation).
 *
 * iOS 27 renders floating surfaces (dock, Control Center, notifications,
 * sheets) as refractive glass over live content.
 *
 * PROVENANCE, precisely:
 *  - Fill, corner radius, shadow and rim values come from
 *    [LiquidGlassTokens], transcribed from the official iOS 27 Figma kit's
 *    own layers. Apple publishes no numeric spec for this material, so even
 *    those are a designer's approximation — see that file's header.
 *  - Blur radius in px, and the scaling applied for the 3 GB engineering
 *    budget, remain **AppleUX-chosen**. The kit has no counterpart for them.
 *
 * Strategy: real-time [RenderEffect] blur (API 31+) composited with a theme
 * tint, gated per-surface. Below API 31 the tint alone applies (documented
 * graceful fallback, not a second code path to maintain). Blur is applied
 * only to small floating surfaces (dock, tiles, cards, sheets) and the
 * library scrim — never to fullscreen scrolling content.
 *
 * Intensity is user-adjustable 0-100 (Settings slider, shared flag
 * `appleux_glass_intensity`), mapped onto the iOS 27 transparency slider.
 * 0 disables blur entirely (equivalent of iOS Reduce Transparency);
 * 100 is maximum clarity.
 */
object AppleuxGlass {

    const val MIN_LEVEL = 0
    const val MAX_LEVEL = 100

    /** iOS 27 system default for the transparency slider is 0.5. */
    const val DEFAULT_LEVEL =
        (LiquidGlassTokens.TRANSPARENCY_DEFAULT * MAX_LEVEL).toInt()

    /**
     * Maximum blur radius in px at level 100. AppleUX-chosen: large enough to
     * read as glass over wallpaper, small enough that RenderEffect stays cheap
     * on low-end GPUs. Re-tune only with frame measurements on target hardware.
     */
    const val MAX_BLUR_RADIUS_PX = 22f

    fun isBlurSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    fun level01(level: Int): Float = level.coerceIn(MIN_LEVEL, MAX_LEVEL) / MAX_LEVEL.toFloat()

    /**
     * Level 0-100 mapped to the iOS 27 transparency slider. Note the
     * inversion: more transparency means MORE see-through, so a higher level
     * yields a LOWER fill alpha and a LARGER blur radius.
     */
    fun transparency(level: Int): Float = LiquidGlassTokens.TRANSPARENCY_DEFAULT +
        (level01(level) - LiquidGlassTokens.TRANSPARENCY_DEFAULT) * 0.5f

    fun blurRadiusForLevel(level: Int): Float =
        (level01(level) * LiquidGlassTokens.blurScale(transparency(level)) *
            MAX_BLUR_RADIUS_PX).coerceIn(0f, MAX_BLUR_RADIUS_PX)

    /**
     * Applies (or clears) background blur on [view]. Level 0 or an
     * unsupported platform clears any effect. Safe to call on every resume.
     */
    fun applyBlur(view: View, level: Int) {
        // NOTE: blur-budget gating lives in SystemUIService (which sees the
        // performance module). design-system compiles standalone, so it must
        // not reference AdaptiveRenderPolicy here (unresolved at kotlinc).
        if (!isBlurSupported() || level <= MIN_LEVEL) {
            view.setRenderEffect(null)
            return
        }
        val r = blurRadiusForLevel(level)
        if (r <= 0.5f) {
            view.setRenderEffect(null)
            return
        }
        view.setRenderEffect(
            RenderEffect.createBlurEffect(r, r, Shader.TileMode.CLAMP)
        )
    }

    /**
     * Fill alpha (0-255) for a glass surface at [level], from the kit's
     * Regular fill (0.70) scaled by the transparency slider's opacity curve.
     * Replaces an invented 64+level*96 ramp.
     */
    fun scrimAlphaForLevel(level: Int): Int =
        ((LiquidGlassTokens.REGULAR_BG_LIGHT_ALPHA *
            LiquidGlassTokens.opacityScale(transparency(level))) * 255f)
            .toInt().coerceIn(0, 255)

    /** Corner radius for a glass surface, from the kit's Regular variant. */
    fun cornerRadiusPx(view: View, clear: Boolean = false): Float {
        val dp = if (clear) {
            LiquidGlassTokens.CLEAR_CORNER_RADIUS_DP
        } else {
            LiquidGlassTokens.REGULAR_CORNER_RADIUS_DP
        }
        return dp * view.resources.displayMetrics.density
    }
}
