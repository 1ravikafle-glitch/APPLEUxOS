package com.appleux.design.components

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.widget.LinearLayout
import com.appleux.design.tokens.ColorTokens

/** notification-expanded: long-press expanded card with full text + actions. */
class AppleuxNotificationExpanded @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : AppleuxCard(context, attrs, defStyleAttr) {
    private val title = AppleuxText(context)
    private val body = AppleuxText(context)
    private val actions = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END }
    init {
        applyLiquidGlassRim()
        val col = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        title.apply { textSize = 15f; setTextColor(ColorTokens.label) }
        body.apply { textSize = 15f; setTextColor(ColorTokens.secondaryLabel) }
        col.addView(title); col.addView(body); col.addView(actions)
        addView(col)
    }
    fun bind(t: String, b: String, buttons: List<Pair<String, () -> Unit>>) {
        title.text = t; body.text = b
        actions.removeAllViews()
        buttons.forEach { (label, fn) ->
            actions.addView(AppleuxText(context).apply {
                text = label; textSize = 15f
                setTextColor(ColorTokens.systemBlue); gravity = Gravity.CENTER
                setPadding(16, 8, 16, 8); isClickable = true
                setOnClickListener { fn() }
            })
        }
    }
}
