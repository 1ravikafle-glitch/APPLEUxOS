package com.appleux.design.components

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.widget.LinearLayout
import com.appleux.design.tokens.ColorTokens

/** lock-screen-customize + wallpaper-picker + widget-editor: one sheet, three rows. */
class AppleuxLockCustomizeSheet @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {
    var onWallpaper: (() -> Unit)? = null
    var onWidgets: (() -> Unit)? = null
    var onClockStyle: (() -> Unit)? = null
    init {
        orientation = VERTICAL
        setPadding(24, 24, 24, 24)
        addView(AppleuxText(context).apply {
            text = "Customize Lock Screen"; textSize = 17f
            setTextColor(ColorTokens.label); gravity = Gravity.CENTER
        })
        for ((label, fn) in listOf(
            "Wallpaper" to { onWallpaper?.invoke() },
            "Widgets" to { onWidgets?.invoke() },
            "Clock style" to { onClockStyle?.invoke() }
        )) {
            val row = AppleuxListItem(context).apply {
                setTitle(label); setDividerVisible(false)
                isClickable = true; setOnClickListener { fn() }
            }
            addView(row)
        }
    }
}
