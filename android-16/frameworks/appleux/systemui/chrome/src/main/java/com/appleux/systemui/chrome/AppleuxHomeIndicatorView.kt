package com.appleux.systemui.chrome

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * The iOS 27 Home indicator: one capsule, bottom-centre, nothing else.
 *
 * ## What this replaces
 *
 * The stock navigation bar drew three Material buttons — back, home and
 * overview — as inflated views with key-icon drawables and button dispatchers,
 * over an opaque `#ff000000` background. `config_navBarInteractionMode = 2`
 * stops the buttons being inflated at all; this view is what remains.
 *
 * ## Performance
 *
 * This is the cheapest possible implementation of the affordance:
 *
 *  * one [Paint], reused; no shader, no layer, no `setLayerType`
 *  * no blur and no `RenderEffect` — an iOS 27 Home indicator is a flat
 *    capsule, so there is nothing to blur. A blurred nav bar would cost a
 *    full-width offscreen pass per frame on a Mali-G57-class GPU.
 *  * no ripple and no `StateListAnimator`: the indicator is not interactive.
 *    It is a positional hint, not a button, and it never receives touches, so
 *    there is no pressed state to render and no animator to tick.
 *  * no background drawable: painting the capsule directly avoids the
 *    View->Drawable indirection and the extra invalidation rectangle it costs.
 *  * `onMeasure` is overridden so the view is exactly the size it draws, so
 *    the parent does not run a second measure/layout pass to discover it.
 *  * `onDraw` computes nothing per frame beyond the two `dpToPx` factors
 *    hoisted into fields in `onSizeChanged`.
 *
 * ## Touch
 *
 * Deliberately not clickable and not focusable. The navigation-bar window is
 * transparent to touches except in the gesture area handled by
 * `NavigationBarView` / `EdgeBackGestureHandler`; making this view clickable
 * would sit a new touch target between the user and the home swipe.
 */
class AppleuxHomeIndicatorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        // A capsule needs no stroke.
        style = Paint.Style.FILL
        color = HOME_INDICATOR_COLOR
        alpha = AppleuxChromeTokens.HOME_INDICATOR_ALPHA
    }

    private val pill = RectF()

    private var pillWidthPx = 0f
    private var pillHeightPx = 0f
    private var bottomMarginPx = 0f

    init {
        // Not a control. Both flags matter: clickable=false keeps the frame's
        // touch dispatch falling straight through to the gesture handler,
        // and notFocusable keeps it out of the focus order entirely.
        isClickable = false
        isFocusable = false
        isFocusableInTouchMode = false
        // Screen readers should announce the gesture affordance once, from the
        // navigation bar itself, not once per decorative shape.
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO

        val density = resources.displayMetrics.density
        pillWidthPx = AppleuxChromeTokens.HOME_INDICATOR_WIDTH_DP * density
        pillHeightPx = AppleuxChromeTokens.HOME_INDICATOR_HEIGHT_DP * density
        bottomMarginPx = AppleuxChromeTokens.HOME_INDICATOR_BOTTOM_MARGIN_DP * density
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Exactly the size drawn. The navigation bar's onLayout will still run,
        // but there is no second measure pass to discover the size, and no
        // WRAP_CONTENT remeasure when the bar is resized for the gesture area.
        setMeasuredDimension(
            pillWidthPx.toInt().coerceAtLeast(1),
            (pillHeightPx + bottomMarginPx).toInt().coerceAtLeast(1),
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Hoisted out of onDraw: the capsule position depends only on size, and
        // recomputing four floats per frame for a shape that has not moved is
        // exactly the kind of avoidable work this view exists to avoid.
        val inset = (w - pillWidthPx) / 2f
        pill.set(
            inset,
            h - bottomMarginPx - pillHeightPx,
            inset + pillWidthPx,
            h - bottomMarginPx,
        )
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRoundRect(pill, pillHeightPx / 2f, pillHeightPx / 2f, paint)
    }

    private companion object {
        /** iOS draws the indicator in white over any content. */
        const val HOME_INDICATOR_COLOR = 0xFFFFFFFF.toInt()
    }
}