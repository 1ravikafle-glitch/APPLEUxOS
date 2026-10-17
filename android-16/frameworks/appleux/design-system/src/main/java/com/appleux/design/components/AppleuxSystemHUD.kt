package com.appleux.design.components

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.appleux.design.tokens.ColorTokens

/** volume-hud + brightness-hud: centered pill with icon + level bar, auto-hide. */
class AppleuxSystemHUD @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : AppleuxCard(context, attrs, defStyleAttr) {
    enum class Kind { VOLUME, BRIGHTNESS }
    private val h = Handler(Looper.getMainLooper())
    private val glyph = AppleuxText(context)
    private val bar = android.widget.ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal)
    private val hide = Runnable { visibility = GONE }
    init {
        applyLiquidGlassRim()
        visibility = GONE
        val col = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(28, 20, 28, 20)
        }
        glyph.apply { textSize = 24f; setTextColor(ColorTokens.label); gravity = Gravity.CENTER }
        col.addView(glyph); col.addView(bar)
        addView(col)
    }
    fun flash(kind: Kind, level01: Float) {
        glyph.text = if (kind == Kind.VOLUME) "\uD83D\uDD0A" else "\u2600"
        bar.max = 100; bar.progress = (level01 * 100).toInt()
        visibility = VISIBLE
        h.removeCallbacks(hide); h.postDelayed(hide, 1200)
    }
    companion object {
        fun host(parent: FrameLayout, context: Context): AppleuxSystemHUD {
            val hud = AppleuxSystemHUD(context)
            parent.addView(hud, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER
            ))
            return hud
        }
    }
}
