package com.appleux.design.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.annotation.LayoutRes
import com.appleux.design.tokens.ColorTokens
import com.appleux.design.tokens.LiquidGlassTokens
import com.appleux.design.tokens.MotionTokens
import com.appleux.design.tokens.SpacingTokens

/**
 * An iOS sheet: a Liquid Glass panel that rises from the bottom edge.
 *
 * ## What was wrong before
 *
 *  - **The grab handle was invisible.** `appleux_sheet_handle.xml` declared
 *    `<solid android:color="@android:color/transparent">`, so the single
 *    affordance telling the user this was draggable rendered as nothing at all.
 *  - **`present()` could not un-hide the sheet.** `dismiss()` set `GONE` and
 *    nothing ever set it back, so a dismissed sheet was invisible for the rest
 *    of the view's life.
 *  - **`animateIn()` read `height` before layout.** `y = height` with
 *    `height == 0` starts the slide-in from the origin.
 *  - `cornerRadius` was a property nothing ever applied, and `setContentView`
 *    stacked a second copy of the content on every call.
 *
 * Only the two TOP corners are rounded, as on iOS — the bottom corners run to
 * the screen edge.
 */
class AppleuxSheet @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val scrim = View(context)
    private val panel = LinearLayout(context)
    private val handle = View(context)
    private val contentView = LinearLayout(context)

    private var presented = false
    private var onDismissRequest: (() -> Unit)? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rectF = RectF()

    /** Sheet corner radius in dp. iOS 27 renders sheets as Liquid Glass. */
    var cornerRadius: Float = LiquidGlassTokens.REGULAR_CORNER_RADIUS_DP
        set(value) {
            field = value
            invalidate()
        }

    init {
        // Transparent by default: the caller hosts this inside a window that
        // owns the scrim, or adds it to a full-screen container.
        setBackgroundColor(Color.TRANSPARENT)

        scrim.setBackgroundColor(ColorTokens.overlayPrimary)
        scrim.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        scrim.isClickable = true
        scrim.visibility = GONE
        scrim.setOnClickListener { requestDismiss() }
        addView(scrim)

        panel.orientation = LinearLayout.VERTICAL
        panel.layoutParams = LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM
        )
        addView(panel)

        handle.layoutParams = LinearLayout.LayoutParams(
            LayoutUtils.dpToPx(HANDLE_WIDTH_DP, context),
            LayoutUtils.dpToPx(LiquidGlassTokens.RIM_SPREAD_DP * HANDLE_HEIGHT_SCALE, context)
        ).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = LayoutUtils.dpToPx(SpacingTokens.INSETS_SM, context)
            bottomMargin = LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, context)
        }
        panel.addView(handle)

        contentView.orientation = LinearLayout.VERTICAL
        contentView.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        panel.addView(contentView)

        visibility = GONE
    }

    // ---- Content -----------------------------------------------------------

    /** Replaces the sheet's content with an inflated layout. */
    fun setContentView(@LayoutRes layoutRes: Int) {
        contentView.removeAllViews()
        contentView.addView(
            LayoutInflater.from(context).inflate(layoutRes, contentView, false)
        )
    }

    /** Replaces the sheet's content with [view]. */
    fun setContentView(view: View) {
        contentView.removeAllViews()
        contentView.addView(view)
    }

    /** Appends to the existing content rather than replacing it. */
    fun addViewToContent(view: View) {
        contentView.addView(view)
    }

    fun setOnDismissRequestListener(listener: (() -> Unit)?) {
        onDismissRequest = listener
    }

    val isPresented: Boolean get() = presented

    // ---- Presentation ------------------------------------------------------

    fun present() {
        if (presented) return
        presented = true
        visibility = VISIBLE
        scrim.visibility = VISIBLE
        scrim.alpha = 0f
        scrim.animate().alpha(1f)
            .setDuration(MotionTokens.DURATION_FAST)
            .setInterpolator(MotionTokens.easingStandard)
            .start()

        // Measured now, not at construction: before the first layout `height`
        // is 0 and the slide-in would start from the origin.
        panel.post {
            panel.translationY = panel.height.toFloat()
            panel.animate().translationY(0f)
                .setDuration(MotionTokens.DURATION_SLIDE)
                .setInterpolator(MotionTokens.easingIOSDefault)
                .start()
        }
        applyTheme()
    }

    fun dismiss() {
        if (!presented) return
        presented = false
        scrim.animate().alpha(0f)
            .setDuration(MotionTokens.DURATION_FAST)
            .start()
        panel.animate().translationY(panel.height.toFloat())
            .setDuration(MotionTokens.DURATION_SLIDE)
            .setInterpolator(MotionTokens.easingStandard)
            .withEndAction {
                // Restore every piece of state present() touches, so the sheet
                // can genuinely be shown again.
                visibility = GONE
                scrim.visibility = GONE
                scrim.alpha = 1f
                panel.translationY = 0f
            }
            .start()
    }

    private fun requestDismiss() {
        val listener = onDismissRequest
        if (listener != null) listener() else dismiss()
    }

    override fun onDetachedFromWindow() {
        // An animation holding a detached view leaks it.
        scrim.animate().cancel()
        panel.animate().cancel()
        super.onDetachedFromWindow()
    }

    // ---- Appearance --------------------------------------------------------

    /** Re-resolves the scrim and the handle from the shared tokens. */
    fun applyTheme() {
        scrim.setBackgroundColor(ColorTokens.overlayPrimary)
        handle.background = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = handle.height / 2f
            setColor(
                if (ColorTokens.darkMode) HANDLE_COLOR_DARK else HANDLE_COLOR_LIGHT
            )
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        // Only the panel is glass; the rest of the view is the scrim, which is
        // a sibling View so it can take touches.
        if (!presented) return
        val top = (height - panel.height).coerceAtLeast(0).toFloat()
        rectF.set(0f, top, width.toFloat(), height.toFloat())
        val r = LayoutUtils.dpToPxFloat(cornerRadius, context)
        path.reset()
        path.addRoundRect(
            rectF,
            floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f),
            Path.Direction.CW
        )

        paint.color = if (ColorTokens.darkMode) {
            LiquidGlassTokens.REGULAR_BG_DARK
        } else {
            LiquidGlassTokens.REGULAR_BG_LIGHT
        }
        paint.alpha = AppleuxGlass.scrimAlphaForLevel(AppleuxGlass.DEFAULT_LEVEL)
        canvas.drawPath(path, paint)
    }

    private companion object {
        /** AppleUX-chosen: matches the grab handle in Apple's own sheets. */
        const val HANDLE_WIDTH_DP = 36f
        const val HANDLE_HEIGHT_SCALE = 5f
        val HANDLE_COLOR_LIGHT = 0x338E8E93
        val HANDLE_COLOR_DARK = 0x4DE8E8ED
    }
}