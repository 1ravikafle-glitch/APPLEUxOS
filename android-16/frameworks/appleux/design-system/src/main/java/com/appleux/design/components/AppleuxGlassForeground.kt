package com.appleux.design.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Shader
import android.graphics.drawable.Drawable
import com.appleux.design.tokens.ColorTokens
import com.appleux.design.tokens.LiquidGlassTokens

/**
 * The two Liquid Glass details the fill alone cannot carry: the rim ring and
 * the dark inner edge. Values are Apple's own, read from the official iOS 27
 * Figma kit's Regular material (provenance in [LiquidGlassTokens]: rim rings
 * #DBDBDB light / #A6A6A6 dark as spread-shadow rings; inner dark edge
 * #282828 light / #1A1A1A dark, blur 10, offset ±40, spread −40).
 *
 * Two deliberate approximations, both marked:
 *  1. The kit builds the rim from THREE spread-shadow rings (spread 0.5 and
 *     ±1.25/−0.75). This draws ONE 1dp stroke in the same gray. At phone
 *     sizes the triple ring resolves to a hairline anyway.
 *  2. The kit's inner shadow is a true blurred offset shadow confined by a
 *     −40 spread to a hairline. This draws 16dp top/bottom gradient bands at
 *     25% of the kit color. Same read (edges go dark), none of the blur cost
 *     — a real BlurMaskFilter pass per glass surface is not affordable on the
 *     3 GB budget.
 *
 * Curvature is NOT approximated: rim and bands clip to [AppleuxSuperellipse],
 * the same continuous-curvature path as the backgrounds beneath them, so the
 * ring hugs the glass instead of cutting across it.
 *
 * Set as a view foreground (drawn after content — the iOS look is content
 * dimming at the glass edge). No bitmaps, no blur passes, shaders rebuilt
 * only on bounds change. Use [AppleuxGlass.applyForeground] so callers do
 * not reimplement the dark-mode lookup.
 */
class AppleuxGlassForeground(
    context: Context,
    cornerRadiusDp: Float,
    private val dark: Boolean
) : Drawable() {

    private val density = context.resources.displayMetrics.density
    private val radiusPx = cornerRadiusDp * density
    private val strokePx = 1f * density
    private val bandPx = INNER_BAND_DP * density

    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = strokePx
        color = if (dark) LiquidGlassTokens.RIM_DARK else LiquidGlassTokens.RIM_LIGHT
    }
    private val shadeColor =
        if (dark) LiquidGlassTokens.INNER_SHADOW_DARK else LiquidGlassTokens.INNER_SHADOW_LIGHT

    private val clipPath = Path()
    private val rimPath = Path()
    private var topShade: LinearGradient? = null
    private var bottomShade: LinearGradient? = null
    private val shadePaint = Paint()

    override fun onBoundsChange(bounds: android.graphics.Rect) {
        super.onBoundsChange(bounds)
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        if (w <= 0f || h <= 0f) return
        clipPath.set(AppleuxSuperellipse.path(w.toInt(), h.toInt(), radiusPx))
        // Inset half the stroke so the full hairline lands inside the glass.
        val inset = strokePx / 2f
        rimPath.set(
            AppleuxSuperellipse.path(
                w.toInt(), h.toInt(),
                (radiusPx - inset).coerceAtLeast(0f)
            )
        )
        // Bands hug the same curvature via the clip; the rects themselves are
        // plain because only their faded tails show.
        topShade = LinearGradient(
            0f, 0f, 0f, bandPx,
            shadeColorAt(SHADE_ALPHA), android.graphics.Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )
        bottomShade = LinearGradient(
            0f, h, 0f, h - bandPx,
            shadeColorAt(SHADE_ALPHA), android.graphics.Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )
    }

    override fun draw(canvas: Canvas) {
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        if (w <= 0f || h <= 0f) return
        // Paths are built in 0..w coordinates; the bounds origin is arbitrary.
        val save = canvas.save()
        canvas.translate(-bounds.left.toFloat(), -bounds.top.toFloat())
        canvas.clipPath(clipPath)
        shadePaint.shader = topShade
        canvas.drawRect(0f, 0f, w, bandPx, shadePaint)
        shadePaint.shader = bottomShade
        canvas.drawRect(0f, h - bandPx, w, h, shadePaint)
        canvas.drawPath(rimPath, rimPaint)
        canvas.restoreToCount(save)
    }

    private fun shadeColorAt(alphaFraction: Float): Int {
        val base = shadeColor
        val a = ((android.graphics.Color.alpha(base) * alphaFraction)).toInt()
            .coerceIn(0, 255)
        return (a shl 24) or (base and 0x00FFFFFF)
    }

    override fun setAlpha(alpha: Int) {
        rimPaint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?): Unit = Unit

    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    companion object {
        /** Height of each inner-shade band. AppleUX-chosen (see class docs). */
        const val INNER_BAND_DP = 16f

        /** Fraction of the kit inner-shadow color the bands carry. */
        const val SHADE_ALPHA = 0.25f
    }
}
