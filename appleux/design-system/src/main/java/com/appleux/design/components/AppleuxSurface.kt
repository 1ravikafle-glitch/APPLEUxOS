package com.appleux.design.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import androidx.annotation.ColorInt
import com.appleux.design.tokens.ColorTokens
import com.appleux.design.tokens.ElevationTokens
import com.appleux.design.tokens.LiquidGlassTokens
import com.appleux.design.tokens.SizingTokens

/**
 * The base every opaque or translucent AppleUX surface is built on.
 *
 * ## Elevation
 *
 * [surfaceElevation] maps onto the platform's real `View.elevation`, so the
 * shadow is drawn by the compositor rather than faked in `onDraw`. The previous
 * implementation painted a translucent black rounded rect offset down-right
 * *on top of* the surface fill — which on [AppleuxDialog] meant a dark smear
 * across the bottom-right corner of every alert.
 *
 * ## Material and colour
 *
 * [surfaceColor] is resolved when it is read, not when it is assigned. Every
 * `ColorTokens` member is a getter that depends on the current theme, so a
 * property initialised once at construction keeps its value across a dark-mode
 * flip — that was a live bug: toggling dark mode in Settings repainted the
 * launcher but left every already-constructed surface in the old palette.
 */
open class AppleuxSurface @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    protected val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    protected val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    /** Base fill. Re-read on every draw so a theme flip is picked up. */
    var surfaceColor: Int = ColorTokens.systemBackgroundElevated
        set(value) {
            field = value
            invalidate()
        }

    var cornerRadius: Float = SizingTokens.CORNER_RADIUS_LARGE
        set(value) {
            field = value
            updateOutline()
            invalidate()
        }

    /** Elevation level; see [ElevationTokens] for the scale. */
    var surfaceElevation: Float = ElevationTokens.ELEVATION_NONE
        set(value) {
            field = value
            // Real platform shadow. Values are dp, which is what View.elevation
            // consumes.
            elevation = value * resources.displayMetrics.density
            invalidate()
        }

    var borderWidth: Float = 0f
        set(value) {
            field = value
            invalidate()
        }

    var borderColor: Int = ColorTokens.separator
        set(value) {
            field = value
            invalidate()
        }

    /**
     * The Liquid Glass rim: the hairline that separates a glass surface from
     * the content beneath it. Values come from the iOS 27 kit — see
     * [LiquidGlassTokens.RIM_LIGHT] / [LiquidGlassTokens.RIM_DARK] and
     * [LiquidGlassTokens.RIM_SPREAD_DP].
     *
     * When null (the default) the rim is off unless [useLiquidGlassRim] is set,
     * in which case the appearance-correct token colour is resolved per draw.
     */
    var rimColor: Int? = null
        set(value) {
            field = value
            invalidate()
        }

    /** Rim thickness in **dp**, converted with the display density at draw time. */
    var rimWidthDp: Float = 0f
        set(value) {
            field = value
            invalidate()
        }

    /**
     * Turns the rim on with the appearance-correct kit colour and the kit's own
     * thickness, instead of making every call site remember four constants.
     */
    var useLiquidGlassRim: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    /**
     * Material treatment. [MaterialType.SOLID] paints [surfaceColor] verbatim;
     * the Liquid Glass variants source their fill from [LiquidGlassTokens] so
     * the material matches the kit instead of an invented alpha ladder.
     */
    var material: MaterialType = MaterialType.SOLID
        set(value) {
            field = value
            invalidate()
        }

    private val path = Path()
    private val rectF = RectF()

    init {
        // FrameLayout defaults willNotDraw=true, which SILENTLY SKIPS onDraw:
        // every surface fill, tile disc, toggle track and card vanished while
        // children kept drawing (the "floating glyphs" bug). Must stay false.
        setWillNotDraw(false)
        outlineProvider = ViewOutlineProvider.BACKGROUND
        isClickable = true
        isFocusable = true
    }

    enum class MaterialType {
        /** No translucency: [surfaceColor] is painted as-is. */
        SOLID,

        /** Liquid Glass Regular over content. */
        REGULAR_MATERIAL,

        /** Liquid Glass Regular with the thicker shadow, for modal surfaces. */
        THICK_MATERIAL,

        /** Liquid Glass Clear: only over media-rich content. */
        CLEAR_MATERIAL,

        /** Tinted system chrome. */
        SYSTEM_MATERIAL,

        /** Always-light tinted chrome. */
        SYSTEM_MATERIAL_LIGHT
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateOutline()
    }

    protected fun updateOutline() {
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: android.graphics.Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, cornerRadius)
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawSurface(canvas)
    }

    /**
     * Paints fill, border and rim. Split out so subclasses that draw their own
     * content — [AppleuxToggle] above all — can suppress the surface fill
     * without having to reimplement [onDraw] and lose the outline.
     */
    protected open fun drawSurface(canvas: Canvas) {
        rectF.set(0f, 0f, width.toFloat(), height.toFloat())
        path.reset()
        path.addRoundRect(rectF, cornerRadius, cornerRadius, Path.Direction.CW)

        bgPaint.color = resolveFill()
        bgPaint.style = Paint.Style.FILL
        canvas.drawPath(path, bgPaint)

        if (borderWidth > 0f) {
            borderPaint.color = borderColor
            borderPaint.style = Paint.Style.STROKE
            borderPaint.strokeWidth = borderWidth
            canvas.drawPath(path, borderPaint)
        }

        drawRim(canvas)
    }

    /**
     * Draws the glass rim.
     *
     * The stroke is **inset by half its own width** so the stroke's outer edge
     * lands exactly on the surface outline. Without that inset a half-pixel of
     * the rim would be clipped away by the parent's bounds, and the hairline
     * would render at half weight on one side only — which is precisely the
     * "thicker on the left" artefact the iOS 27 rim is supposed to avoid.
     *
     * The corner radius is reduced by the same half-width so the inner edge
     * stays concentric with the outer one instead of cutting the corners.
     */
    private fun drawRim(canvas: Canvas) {
        val color = resolveRimColor() ?: return
        if (rimWidthDp <= 0f) return

        val w = rimWidthDp * resources.displayMetrics.density
        if (w <= 0f) return

        rimPaint.color = color
        rimPaint.strokeWidth = w
        rectF.set(w / 2f, w / 2f, width - w / 2f, height - w / 2f)
        val r = (cornerRadius - w / 2f).coerceAtLeast(0f)
        path.reset()
        path.addRoundRect(rectF, r, r, Path.Direction.CW)
        canvas.drawPath(path, rimPaint)
    }

    private fun resolveRimColor(): Int? = when {
        rimColor != null -> rimColor
        useLiquidGlassRim -> if (ColorTokens.darkMode) {
            LiquidGlassTokens.RIM_DARK
        } else {
            LiquidGlassTokens.RIM_LIGHT
        }
        else -> null
    }

    private fun resolveFill(): Int {
        val dark = ColorTokens.darkMode
        return when (material) {
            MaterialType.SOLID -> surfaceColor
            MaterialType.REGULAR_MATERIAL, MaterialType.THICK_MATERIAL ->
                if (dark) LiquidGlassTokens.REGULAR_BG_DARK else LiquidGlassTokens.REGULAR_BG_LIGHT
            MaterialType.CLEAR_MATERIAL ->
                if (dark) LiquidGlassTokens.CLEAR_BASE else LiquidGlassTokens.REGULAR_BG_LIGHT
            MaterialType.SYSTEM_MATERIAL ->
                if (dark) SYSTEM_FILL_DARK else SYSTEM_FILL_LIGHT
            MaterialType.SYSTEM_MATERIAL_LIGHT -> SYSTEM_FILL_LIGHT
        }
    }

    fun setBackgroundColorInt(@ColorInt color: Int) {
        surfaceColor = color
        invalidate()
    }

    fun setElevationCompat(elevationValue: Float) {
        surfaceElevation = elevationValue
    }

    fun setBorder(width: Float, @ColorInt color: Int) {
        borderWidth = width
        borderColor = color
        invalidate()
    }

    /**
     * Switches between a solid fill and Liquid Glass.
     *
     * Note what this does *not* do: apply a `RenderEffect`. Android's blur
     * effect blurs a view's own drawing, not what is behind it, so there is no
     * per-view backdrop blur without a window-level `FLAG_BLUR_BEHIND`. The
     * old signature took a `radius` that was silently ignored and defaulted to
     * a *corner* radius, which is how a blur radius ended up meaning something
     * it did not.
     */
    fun setBlurEnabled(enabled: Boolean) {
        material = if (enabled) MaterialType.REGULAR_MATERIAL else MaterialType.SOLID
    }

    /** Enables the Liquid Glass rim with the kit's own colour and thickness. */
    fun applyLiquidGlassRim() {
        useLiquidGlassRim = true
        rimWidthDp = LiquidGlassTokens.RIM_SPREAD_DP * 2f
        invalidate()
    }

    fun getPath(): Path {
        rectF.set(0f, 0f, width.toFloat(), height.toFloat())
        path.reset()
        path.addRoundRect(rectF, cornerRadius, cornerRadius, Path.Direction.CW)
        return path
    }

    private companion object {
        /** iOS systemGray6 chrome fill. */
        const val SYSTEM_FILL_DARK = 0xFF1C1C1E.toInt()
        const val SYSTEM_FILL_LIGHT = 0xFFF2F2F7.toInt()
    }
}