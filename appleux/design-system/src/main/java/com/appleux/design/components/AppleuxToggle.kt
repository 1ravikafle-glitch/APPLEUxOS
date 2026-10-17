package com.appleux.design.components

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.accessibility.AccessibilityNodeInfo
import com.appleux.design.tokens.ColorTokens
import com.appleux.design.tokens.MotionTokens
import com.appleux.design.tokens.SizingTokens

/**
 * An iOS `UISwitch`.
 *
 * ## What was wrong before
 *
 * This view compiled and rendered, and it was completely broken in three ways
 * that a screenshot would have caught immediately:
 *
 *  1. **The pill was squashed into the top half.** It drew a track of
 *     `min(width, height) / 2`, so a 51x31 body rendered a 51x15 pill sitting
 *     on the first 15dp of a 31dp-tall view, with 16dp of dead space below.
 *  2. **There was no animation at all.** `animationProgress` was computed and
 *     never read by `onDraw`, and `animateToggle()` returned immediately when
 *     animating *to off* — so the switch snapped in both directions and the
 *     entire animation block was dead code. `setChecked`'s `animate` parameter
 *     was ignored outright.
 *  3. **The superclass fill drew behind it.** [AppleuxSurface.onDraw] painted
 *     a rounded `systemBackgroundElevated` rect first, so the switch sat on a
 *     grey slab.
 *
 * It is now a real animated control with keyboard and accessibility support,
 * on the shared type/colour tokens, with light and dark resolved per draw.
 */
class AppleuxToggle @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppleuxSurface(context, attrs, defStyleAttr) {

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val trackPath = Path()
    private val trackRect = RectF()

    /** 0 = off, 1 = on. Drives the thumb position. */
    private var progress = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    private var checked = false
    private var animator: ValueAnimator? = null
    private var listener: ((Boolean) -> Unit)? = null

    private val thumbDiameter = resources.displayMetrics.density.times(SizingTokens.SWITCH_THUMB_DIAMETER_DP)
    private val trackRadius = resources.displayMetrics.density.times(SizingTokens.SWITCH_CORNER_RADIUS_DP)

    init {
        // The switch paints its own track and thumb; the inherited surface fill
        // would put a grey slab behind them.
        cornerRadius = trackRadius
        surfaceElevation = 0f
        isClickable = true
        isFocusable = true
        updateOutlineProvider()
    }

    private fun updateOutlineProvider() {
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: android.graphics.Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, trackRadius)
            }
        }
    }

    // ---- Draw --------------------------------------------------------------

    /** No inherited fill: the track below is the surface. */
    override fun drawSurface(canvas: Canvas) = Unit

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        trackRect.set(0f, 0f, w, h)
        trackPath.reset()
        trackPath.addRoundRect(trackRect, trackRadius, trackRadius, Path.Direction.CW)

        trackPaint.style = Paint.Style.FILL
        trackPaint.color = blend(resolveTrackOff(), resolveTrackOn(), progress)
        canvas.drawPath(trackPath, trackPaint)

        // Thumb centre travels from radius to width - radius, so it stays
        // exactly a radius away from either end at every intermediate frame.
        val inset = (h - thumbDiameter) / 2f
        val centreX = inset + progress * (w - thumbDiameter - inset * 2f)

        thumbPaint.style = Paint.Style.FILL
        thumbPaint.color = blend(resolveThumbOff(), resolveThumbOn(), progress)
        canvas.drawCircle(centreX, h / 2f, thumbDiameter / 2f, thumbPaint)
    }

    // ---- State -------------------------------------------------------------

    /**
     * Sets the state.
     *
     * @param animate false for the initial bind and for state restored from
     *   storage, where snapping is correct and animating would look like a
     *   glitch. Programmatic changes deliberately do **not** fire
     *   [setOnCheckedChangeListener]: the listener is for user intent, the
     *   same contract `CompoundButton` uses.
     */
    fun setChecked(value: Boolean, animate: Boolean = true) {
        if (checked == value && progress == if (value) 1f else 0f) return
        checked = value
        val rm = runCatching { com.appleux.design.runtime.AppleuxRuntimeConfig.isReduceMotionEnabled }.getOrDefault(false)
        if (animate && !rm) {
            animateTo(if (value) 1f else 0f)
        } else {
            animator?.cancel()
            animator = null
            progress = if (value) 1f else 0f
        }
    }

    fun isChecked(): Boolean = checked

    fun setOnCheckedChangeListener(listener: ((Boolean) -> Unit)?) {
        this.listener = listener
    }

    /**
     * Track and thumb colours. `null` means "follow the theme", resolved per
     * draw — a colour captured at construction stays stale across a dark-mode
     * flip, which is exactly the bug this indirection removes.
     */
    private var trackColorOn: Int? = null
    private var trackColorOff: Int? = null
    private var thumbColorOn: Int? = null
    private var thumbColorOff: Int? = null

    /** iOS UISwitch: systemGreen when on, a system fill when off. */
    private fun resolveTrackOn() = trackColorOn ?: ColorTokens.systemGreen
    private fun resolveTrackOff() = trackColorOff ?: ColorTokens.systemFillSecondary
    private fun resolveThumbOn() = thumbColorOn ?: ColorTokens.white
    private fun resolveThumbOff() = thumbColorOff ?: ColorTokens.white

    fun setTrackColorOn(@androidx.annotation.ColorInt color: Int) {
        trackColorOn = color
        invalidate()
    }

    fun setTrackColorOff(@androidx.annotation.ColorInt color: Int) {
        trackColorOff = color
        invalidate()
    }

    fun setThumbColorOn(@androidx.annotation.ColorInt color: Int) {
        thumbColorOn = color
        invalidate()
    }

    fun setThumbColorOff(@androidx.annotation.ColorInt color: Int) {
        thumbColorOff = color
        invalidate()
    }

    private fun toggle() {
        if (!isEnabled) return
        checked = !checked
        animateTo(if (checked) 1f else 0f)
        listener?.invoke(checked)
        announceState()
    }

    private fun animateTo(target: Float) {
        animator?.cancel()
        if (!isEnabled) {
            progress = target
            return
        }
        animator = ValueAnimator.ofFloat(progress, target).apply {
            duration = MotionTokens.DURATION_FAST
            interpolator = MotionTokens.easingIOSDefault
            addUpdateListener { progress = it.animatedValue as Float }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        // An animator holding a detached view is a leak on a 3 GB budget.
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }

    // ---- Input -------------------------------------------------------------

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        when (event?.actionMasked) {
            MotionEvent.ACTION_DOWN -> if (isEnabled) animate().alpha(PRESSED_ALPHA).setDuration(
                MotionTokens.TOUCH_RESPONSE_DURATION_MS
            ).start()

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                animate().alpha(1f).setDuration(MotionTokens.TOUCH_RESPONSE_DURATION_MS).start()
        }
        return super.onTouchEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (!isEnabled) return super.onKeyDown(keyCode, event)
        // Space and Enter toggle, as every platform switch does.
        if (keyCode == KeyEvent.KEYCODE_SPACE || keyCode == KeyEvent.KEYCODE_ENTER ||
            keyCode == KeyEvent.KEYCODE_DPAD_CENTER
        ) {
            toggle()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = LayoutUtils.dpToPx(SizingTokens.SWITCH_WIDTH_DP, context)
        val desiredHeight = LayoutUtils.dpToPx(SizingTokens.SWITCH_HEIGHT_DP, context)

        val width = resolveSize(desiredWidth, widthMeasureSpec)
        val height = resolveSize(desiredHeight, heightMeasureSpec)

        // Never smaller than the platform minimum touch target, even though
        // the iOS control itself is only 31dp tall.
        val minTouch = LayoutUtils.dpToPx(SizingTokens.TOUCH_TARGET_MIN, context)
        setMeasuredDimension(
            maxOf(width, if (isEnabled) minTouch else width),
            maxOf(height, if (isEnabled) minTouch else height)
        )
        updateOutlineProvider()
    }

    // ---- Accessibility -----------------------------------------------------

    /**
     * Announced only on a user-initiated toggle. Announcing a programmatic
     * setChecked would make TalkBack narrate every state restore.
     */
    private fun announceState() {
        announceForAccessibility(if (checked) STATE_ON else STATE_OFF)
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = android.widget.Switch::class.java.name
        info.isCheckable = true
        info.isChecked = checked
    }

    // ---- Helpers -----------------------------------------------------------

    /** Interpolates between two colours; both are read per draw, not cached. */
    private fun blend(from: Int, to: Int, t: Float): Int {
        if (t <= 0f) return from
        if (t >= 1f) return to
        val fr = (from shr 16 and 0xFF).toFloat()
        val fg = (from shr 8 and 0xFF).toFloat()
        val fb = (from and 0xFF).toFloat()
        val tr = (to shr 16 and 0xFF).toFloat()
        val tg = (to shr 8 and 0xFF).toFloat()
        val tb = (to and 0xFF).toFloat()
        val r = (fr + (tr - fr) * t).toInt()
        val g = (fg + (tg - fg) * t).toInt()
        val b = (fb + (tb - fb) * t).toInt()
        // Preserve the source alpha; these fills are opaque.
        val a = from ushr 24
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    private companion object {
        const val PRESSED_ALPHA = 0.7f
        const val STATE_ON = "On"
        const val STATE_OFF = "Off"
    }
}