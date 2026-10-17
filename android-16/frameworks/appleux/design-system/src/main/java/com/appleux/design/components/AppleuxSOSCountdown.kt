package com.appleux.design.components

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.Gravity
import android.widget.LinearLayout
import com.appleux.design.tokens.ColorTokens

/** lock-screen-sos-countdown: Emergency SOS 3-2-1 countdown + alarm affordance. */
class AppleuxSOSCountdown @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {
    private val h = Handler(Looper.getMainLooper())
    private val num = AppleuxText(context)
    private var left = 3
    private var tick: Runnable? = null
    var onCountdownFinished: (() -> Unit)? = null
    var onCancel: (() -> Unit)? = null
    init {
        orientation = VERTICAL; gravity = Gravity.CENTER
        val title = AppleuxText(context).apply {
            text = "Emergency SOS"; textSize = 22f
            setTextColor(ColorTokens.systemRed); gravity = Gravity.CENTER
        }
        num.apply { textSize = 64f; setTextColor(android.graphics.Color.WHITE); gravity = Gravity.CENTER }
        val cancel = AppleuxText(context).apply {
            text = "Cancel"; textSize = 16f
            setTextColor(android.graphics.Color.WHITE); gravity = Gravity.CENTER
            isClickable = true; setOnClickListener { stop(); onCancel?.invoke() }
        }
        addView(title); addView(num); addView(cancel)
    }
    fun start(seconds: Int = 3) {
        left = seconds; render()
        tick = object : Runnable {
            override fun run() {
                left--
                if (left <= 0) { onCountdownFinished?.invoke(); return }
                render(); h.postDelayed(this, 1000)
            }
        }
        h.postDelayed(tick!!, 1000)
    }
    fun stop() { tick?.let { h.removeCallbacks(it) }; tick = null }
    private fun render() { num.text = left.toString() }
    override fun onDetachedFromWindow() { stop(); super.onDetachedFromWindow() }
}
