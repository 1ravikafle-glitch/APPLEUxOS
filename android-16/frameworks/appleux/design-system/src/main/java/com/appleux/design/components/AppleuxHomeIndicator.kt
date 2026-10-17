package com.appleux.design.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/** iOS home indicator: thin white pill centred above the bottom edge. */
class AppleuxHomeIndicator @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
    override fun onDraw(c: Canvas) {
        val w = (134 * resources.displayMetrics.density).coerceAtMost(width.toFloat())
        val h = 5 * resources.displayMetrics.density
        val l = (width - w) / 2f; val t = height - h - 8 * resources.displayMetrics.density
        c.drawRoundRect(l, t, l + w, t + h, h / 2f, h / 2f, p)
    }
    override fun onMeasure(w: Int, h: Int) {
        setMeasuredDimension(
            MeasureSpec.getSize(w),
            (21 * resources.displayMetrics.density).toInt()
        )
    }
}
