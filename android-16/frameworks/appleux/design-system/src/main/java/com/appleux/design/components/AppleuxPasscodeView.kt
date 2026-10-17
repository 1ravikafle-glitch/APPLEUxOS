package com.appleux.design.components

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.widget.GridLayout
import android.widget.LinearLayout
import com.appleux.design.tokens.ColorTokens
import com.appleux.design.tokens.SpacingTokens

/**
 * iOS numeric passcode pad (Emergency / Cancel + 0-9 + backspace).
 * Emits digits, never authenticates — the host owns verification.
 *
 * Layout: circular glass buttons in a 3-column grid, with dot indicators
 * above showing entered digit count. Button sizing follows the design
 * system's spacing scale for consistency with the rest of the UI.
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
        // Transparent over wallpaper, same as lock screen
        setBackgroundColor(android.graphics.Color.TRANSPARENT)

        // Dot indicators row
        val dotRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
        }
        repeat(6) {
            val d = AppleuxText(context).apply {
                text = "○"; textSize = 18f
                setTextColor(android.graphics.Color.WHITE)
                setPadding(dp(8), 0, dp(8), 0)
            }
            dots.add(d); dotRow.addView(d)
        }
        addView(dotRow, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(24)
        })

        // Numeric keypad grid
        val grid = GridLayout(context).apply {
            columnCount = 3
        }
        // Row 1: 1, 2, 3
        for (n in 1..3) {
            grid.addView(circularButton("$n") { onDigit?.invoke(n); pushDot() })
        }
        // Row 2: 4, 5, 6
        for (n in 4..6) {
            grid.addView(circularButton("$n") { onDigit?.invoke(n); pushDot() })
        }
        // Row 3: 7, 8, 9
        for (n in 7..9) {
            grid.addView(circularButton("$n") { onDigit?.invoke(n); pushDot() })
        }
        // Row 4: Emergency, 0, Backspace
        grid.addView(textButton("Emergency") { onEmergency?.invoke() })
        grid.addView(circularButton("0") { onDigit?.invoke(0); pushDot() })
        grid.addView(circularButton("⌫") { onBackspace?.invoke(); popDot() })
        addView(grid)

        // Cancel button
        val cancel = AppleuxText(context).apply {
            text = "Cancel"; textSize = 16f
            setTextColor(android.graphics.Color.WHITE)
            gravity = Gravity.CENTER
            isClickable = true
            setOnClickListener { onCancel?.invoke() }
            setPadding(0, dp(16), 0, dp(8))
        }
        addView(cancel)
    }

    fun clear() { entered = 0; render() }
    private fun pushDot() { entered = (entered + 1).coerceAtMost(6); render() }
    private fun popDot() { entered = (entered - 1).coerceAtLeast(0); render() }
    private fun render() {
        dots.forEachIndexed { i, d -> d.text = if (i < entered) "●" else "○" }
    }

    /**
     * Circular glass button for digits — matches the lock screen's
     * GlassButton pattern: translucent fill, white glyph, circular shape.
     */
    private fun circularButton(s: String, fn: () -> Unit) = AppleuxText(context).apply {
        text = s; textSize = 28f
        setTextColor(android.graphics.Color.WHITE)
        gravity = Gravity.CENTER
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0x3DFFFFFF) // Same alpha as call screen glass buttons
        }
        val size = dp(72)
        layoutParams = GridLayout.LayoutParams().apply {
            width = size; height = size
            setMargins(dp(10), dp(10), dp(10), dp(10))
        }
        isClickable = true
        setOnClickListener { fn() }
    }

    /**
     * Text-only button for Emergency — smaller, no circular background,
     * matching the design system's text button pattern.
     */
    private fun textButton(s: String, fn: () -> Unit) = AppleuxText(context).apply {
        text = s; textSize = 14f
        setTextColor(android.graphics.Color.WHITE)
        gravity = Gravity.CENTER
        setPadding(dp(20), dp(14), dp(20), dp(14))
        isClickable = true
        setOnClickListener { fn() }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
