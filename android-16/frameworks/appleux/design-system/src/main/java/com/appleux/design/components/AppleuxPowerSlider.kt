package com.appleux.design.components

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.SeekBar
import com.appleux.design.tokens.ColorTokens

/** power-off-slider + restart-slider: slide-to-power-off / restart rows. */
class AppleuxPowerSlider @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {
    var onPowerOff: (() -> Unit)? = null
    var onRestart: (() -> Unit)? = null
    init {
        orientation = VERTICAL
        setPadding(24, 24, 24, 24)
        // Power OFF is wired to null by default and is expected to stay that
        // way: PowerManager.shutdown() is @SystemApi and absent from the public
        // SDK, so there is no honest action to bind here. Leaving the row out
        // entirely would be better; it is kept so the component matches the iOS
        // control it was modelled on, and so a future ROM that exposes a public
        // shutdown path has somewhere to connect.
        addView(row("slide to power off") { onPowerOff?.invoke() })
        addView(row("slide to restart") { onRestart?.invoke() })
    }
    private fun row(label: String, done: () -> Unit): LinearLayout {
        val r = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val sb = AppleuxSlider(context).apply { max = 100 }
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) {
                if (u && p >= 100) { done(); s?.progress = 0 }
            }
            override fun onStartTrackingTouch(s: SeekBar?) = Unit
            override fun onStopTrackingTouch(s: SeekBar?) { if ((s?.progress ?: 0) < 100) s?.progress = 0 }
        })
        r.addView(AppleuxText(context).apply {
            text = label; textSize = 14f
            setTextColor(ColorTokens.label)
        }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        r.addView(sb, LayoutParams(0, LayoutParams.WRAP_CONTENT, 2f))
        return r
    }
}
