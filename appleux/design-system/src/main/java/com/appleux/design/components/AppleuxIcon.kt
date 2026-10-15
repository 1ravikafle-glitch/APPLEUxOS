package com.appleux.design.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import androidx.annotation.DrawableRes
import androidx.annotation.Px
import com.appleux.design.tokens.ColorTokens
import com.appleux.design.tokens.IconTokens
import androidx.core.content.ContextCompat

class AppleuxIcon @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : androidx.appcompat.widget.AppCompatImageView(context, attrs, defStyleAttr) {

    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    var iconName: String = ""
        set(value) {
            field = value
            // Prefer real Apple vector geometry; fall back to the hand-drawn
            // canvas path (clearing any previous drawable so it shows).
            iconRes = AppleuxIconRegistry.getDrawableRes(value)
            invalidate()
        }

    @DrawableRes
    var iconRes: Int? = null
        set(value) {
            field = value
            if (value != null) {
                setImageDrawable(ContextCompat.getDrawable(context, value))
            } else {
                setImageDrawable(null)
            }
        }

    var iconTint: Int = ColorTokens.label
        set(value) {
            field = value
            imageTintList = android.content.res.ColorStateList.valueOf(value)
        }

    var iconSize: Float = IconTokens.SIZE_MEDIUM
        set(value) {
            field = value
            val px = dip(value).toInt()
            // layoutParams is null until the view is attached; programmatic
            // construction (tiles, cells) sets size before that.
            val lp = layoutParams
            if (lp != null) {
                lp.width = px
                lp.height = px
            } else {
                layoutParams = android.view.ViewGroup.LayoutParams(px, px)
            }
        }

    var iconState: Int = IconTokens.States.NORMAL
        set(value) {
            field = value
            updateStateTint()
        }

    init {
        iconTint = ColorTokens.secondaryLabel
        scaleType = ScaleType.CENTER
        updateStateTint()
    }

    private fun updateStateTint() {
        iconTint = when (iconState) {
            IconTokens.States.DISABLED -> ColorTokens.tertiaryLabel
            IconTokens.States.ACTIVE -> ColorTokens.systemBlue
            IconTokens.States.SELECTED -> ColorTokens.systemBlue
            IconTokens.States.PRESSED -> ColorTokens.systemBluePressed
            IconTokens.States.TRANSITIONING -> ColorTokens.systemBlue
            else -> ColorTokens.label
        }
    }

    private fun dip(value: Float): Float =
        value * context.resources.displayMetrics.density

    override fun onDraw(canvas: Canvas) {
        if (iconRes == null) {
            drawVectorIcon(canvas)
        }
        super.onDraw(canvas)
    }

    private fun drawVectorIcon(canvas: Canvas) {
        if (iconName.isEmpty()) return
        val iconPath = AppleuxIconRegistry.getPath(iconName) ?: return
        path.reset()
        path.addPath(iconPath)
        iconPaint.color = iconTint
        iconPaint.style = Paint.Style.FILL
        canvas.drawPath(path, iconPaint)
    }
}
