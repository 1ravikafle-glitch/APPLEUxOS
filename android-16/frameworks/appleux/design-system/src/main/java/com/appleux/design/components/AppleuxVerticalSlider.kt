package com.appleux.design.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import androidx.core.content.ContextCompat
import com.appleux.design.tokens.LiquidGlassTokens
import kotlin.math.roundToInt

/**
 * iOS 27 vertical capsule slider (Brightness / Volume).
 *
 * PROVENANCE: Apple's guide screenshot 7511c37e + Figma Regular material
 * (34dp radius, #FFF@0.70, rim). Tall capsule, icon at bottom, white fill
 * rising from the bottom proportional to [level01]. Touch/drag sets level.
 */
class AppleuxVerticalSlider @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var label: String = ""
        set(v) {
            field = v
            // contentDescription was frozen at init ("") — announce the label.
            contentDescription = v
            invalidate()
        }
    var iconRes: Int = 0
        set(v) { field = v; invalidate() }

    var level01: Float = 0.5f
        set(v) { field = v.coerceIn(0f, 1f); invalidate() }

    var onLevelChanged: ((Float) -> Unit)? = null

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val levelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val path = Path()
    private val bounds = RectF()
    private val levelBounds = RectF()

    init {
        isClickable = true
        isFocusable = true
        contentDescription = label
    }

    fun setLevel(v: Float, notify: Boolean = false) {
        level01 = v.coerceIn(0f, 1f)
        if (notify) onLevelChanged?.invoke(level01)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val frac = 1f - (e.y / height.coerceAtLeast(1).toFloat())
                setLevel(frac, notify = true)
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return super.onTouchEvent(e)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0 || h <= 0) return
        val r = (com.appleux.design.tokens.LiquidGlassTokens.REGULAR_CORNER_RADIUS_DP * resources.displayMetrics.density)
            .coerceAtMost(w / 2f)
        bounds.set(0f, 0f, w, h)
        path.reset()
        path.addRoundRect(bounds, r, r, Path.Direction.CW)
        fill.color = INACTIVE_FILL
        canvas.drawPath(path, fill)
        // White level rising from bottom (iOS behaviour).
        val lh = h * level01
        if (lh > 0) {
            levelBounds.set(0f, h - lh, w, h)
            val lp = Path()
            // Clip level to capsule: intersect by re-using capsule path.
            canvas.save()
            canvas.clipPath(path)
            levelPaint.color = 0xFFFFFFFF.toInt()
            canvas.drawRect(levelBounds, levelPaint)
            canvas.restore()
        }
        rim.color = (if (com.appleux.design.tokens.ColorTokens.darkMode)
            com.appleux.design.tokens.LiquidGlassTokens.RIM_DARK
        else com.appleux.design.tokens.LiquidGlassTokens.RIM_LIGHT)
        rim.strokeWidth = (1f * resources.displayMetrics.density).coerceAtLeast(1f)
        canvas.drawPath(path, rim)
        // Glyph: bottom-anchored sun / speaker drawn as tinted icon if provided.
        if (iconRes != 0) {
            runCatching {
                val d = ContextCompat.getDrawable(context, iconRes) ?: return
                val s = (20 * resources.displayMetrics.density).toInt()
                val l = (w / 2f - s / 2f).roundToInt()
                val t = (h - s - 10 * resources.displayMetrics.density).roundToInt()
                d.setBounds(l, t.coerceAtLeast(0), l + s, t + s)
                d.setTint(if (level01 > 0.15f) 0xFF8E8E93.toInt() else 0xFFFFFFFF.toInt())
                d.draw(canvas)
            }
        }
    }

    private companion object {
        const val INACTIVE_FILL = 0x59000000
    }
}
