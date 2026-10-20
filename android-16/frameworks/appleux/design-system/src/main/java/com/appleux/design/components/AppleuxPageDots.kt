package com.appleux.design.components

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.widget.LinearLayout

/** iOS home page dots. */
class AppleuxPageDots @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {
    private val dots = mutableListOf<AppleuxText>()
    init { orientation = HORIZONTAL; gravity = Gravity.CENTER }
    fun setPages(count: Int, active: Int) {
        removeAllViews(); dots.clear()
        for (i in 0 until count.coerceIn(1, 9)) {
            val d = AppleuxText(context).apply {
                text = if (i == active) "●" else "○"; textSize = 8f
                setTextColor(android.graphics.Color.WHITE)
                setPadding(4, 0, 4, 0)
            }
            dots.add(d); addView(d)
        }
    }
}
