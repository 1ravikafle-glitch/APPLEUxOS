package com.appleux.design.components

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.widget.GridLayout
import android.widget.LinearLayout
import com.appleux.design.tokens.ColorTokens

/**
 * iOS numeric passcode pad (Emergency / Cancel + 0-9 + backspace).
 * Emits digits, never authenticates — the host owns verification.
 */
class AppleuxPasscodeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    var onDigit: ((Int) -> Unit)? = null
    var onBackspace: (() -> Unit)? = null
    var onEmergency: (() -> Unit)? = null
    var onCancel: (() -> Unit)? = null

    private val dots = mutableListOf<AppleuxText>()
    var entered: Int = 0
        private set

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        val dotRow = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER }
        repeat(6) {
            val d = AppleuxText(context).apply {
                text = "\u25CB"; textSize = 18f
                setTextColor(android.graphics.Color.WHITE)
                setPadding(dp(6), 0, dp(6), 0)
            }
            dots.add(d); dotRow.addView(d)
        }
        addView(dotRow)
        val grid = GridLayout(context).apply { columnCount = 3 }
        for (n in 1..9) grid.addView(numBtn("$n") { onDigit?.invoke(n); pushDot() })
        grid.addView(textBtn("Emergency") { onEmergency?.invoke() })
        grid.addView(numBtn("0") { onDigit?.invoke(0); pushDot() })
        grid.addView(textBtn("\u232B") { onBackspace?.invoke(); popDot() })
        addView(grid)
        val cancel = AppleuxText(context).apply {
            text = "Cancel"; textSize = 16f
            setTextColor(android.graphics.Color.WHITE); gravity = Gravity.CENTER
            isClickable = true; setOnClickListener { onCancel?.invoke() }
            setPadding(0, dp(8), 0, 0)
        }
        addView(cancel)
    }

    fun clear() { entered = 0; render() }
    private fun pushDot() { entered = (entered + 1).coerceAtMost(6); render() }
    private fun popDot() { entered = (entered - 1).coerceAtLeast(0); render() }
    private fun render() {
        dots.forEachIndexed { i, d -> d.text = if (i < entered) "\u25CF" else "\u25CB" }
    }

    private fun numBtn(s: String, fn: () -> Unit) = AppleuxText(context).apply {
        text = s; textSize = 28f
        setTextColor(android.graphics.Color.WHITE); gravity = Gravity.CENTER
        setPadding(dp(26), dp(10), dp(26), dp(10))
        isClickable = true; setOnClickListener { fn() }
    }
    private fun textBtn(s: String, fn: () -> Unit) = AppleuxText(context).apply {
        text = s; textSize = 14f
        setTextColor(android.graphics.Color.WHITE); gravity = Gravity.CENTER
        setPadding(dp(26), dp(14), dp(26), dp(14))
        isClickable = true; setOnClickListener { fn() }
    }
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
