package com.appleux.design.components

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.widget.LinearLayout
import com.appleux.design.tokens.ColorTokens

/** lock-screen-unavailable-timer: "iPhone Unavailable, try again in N minutes". */
class AppleuxUnavailableTimer @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {
    private val sub = AppleuxText(context)
    init {
        orientation = VERTICAL; gravity = Gravity.CENTER
        addView(AppleuxText(context).apply {
            text = "iPhone Unavailable"; textSize = 22f
            setTextColor(android.graphics.Color.WHITE); gravity = Gravity.CENTER
        })
        sub.apply { textSize = 15f; setTextColor(ColorTokens.secondaryLabel); gravity = Gravity.CENTER }
        addView(sub)
        addView(AppleuxHomeIndicator(context))
    }
    fun bind(retryMinutes: Int) {
        sub.text = if (retryMinutes > 0) "try again in $retryMinutes minutes" else "try again later"
    }
}
