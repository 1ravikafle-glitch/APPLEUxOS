package com.appleux.design.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.appleux.design.tokens.ColorTokens
import com.appleux.design.tokens.IconTokens
import com.appleux.design.tokens.LiquidGlassTokens
import com.appleux.design.tokens.MotionTokens
import com.appleux.design.tokens.SpacingTokens
import com.appleux.design.tokens.SizingTokens
import android.view.ViewGroup

class AppleuxControlTile @JvmOverloads constructor(
    context: Context,
    val tile: AppleuxControlCenter.ControlTile,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppleuxSurface(context, attrs, defStyleAttr) {

    private val iconView: AppleuxIcon
    private val labelView: AppleuxText
    private val stateText: AppleuxText
    private val container: LinearLayout

    private var isActive: Boolean = false

    init {
        // iOS renders an INACTIVE control as a dark translucent circle, not a
        // white one. The previous near-white fill vanished against the white
        // glass sheet (measured from Apple's screenshot: inactive controls read
        // as dark grey discs, active ones as solid colour).
        surfaceColor = if (ColorTokens.darkMode) INACTIVE_FILL_DARK else INACTIVE_FILL_LIGHT
        cornerRadius = SizingTokens.CORNER_RADIUS_ROUND
        rimColor = if (ColorTokens.darkMode) {
            LiquidGlassTokens.RIM_DARK
        } else {
            LiquidGlassTokens.RIM_LIGHT
        }
        rimWidthDp = LiquidGlassTokens.RIM_SPREAD_DP
        surfaceElevation = 0f

        container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(SpacingTokens.PADDING_SMALL, SpacingTokens.PADDING_SMALL, SpacingTokens.PADDING_SMALL, SpacingTokens.PADDING_SMALL)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }

        iconView = AppleuxIcon(context).apply {
            // Fills most of the circle; iOS insets the glyph from the rim.
            iconSize = (CONTROL_DIAMETER_DP * 0.52f)
            if (tile.iconRes != null) {
                iconRes = tile.iconRes
            } else {
                iconName = tile.iconName
            }
            iconState = if (isActive) IconTokens.States.ACTIVE else IconTokens.States.NORMAL
        }

        labelView = AppleuxText(context).apply {
            text = tile.name
            textSize = 12f
            setTextStyle(AppleuxText.TextStyle.REGULAR)
            setTextColor(ColorTokens.secondaryLabel)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            gravity = Gravity.CENTER_HORIZONTAL
        }

        stateText = AppleuxText(context).apply {
            text = ""
            textSize = 10f
            setTextStyle(AppleuxText.TextStyle.MEDIUM)
            setTextColor(ColorTokens.systemBlue)
            visibility = GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            gravity = Gravity.CENTER_HORIZONTAL
        }

        container.addView(iconView)
        // iOS Control Center controls carry NO text label. Measured from
        // Apple's own screenshot (scripts/measure-cc-from-screenshot.py):
        // circular controls are 16.1% of screen width. 0.161 x 1080 px =
        // 173.9 px -> 64.7 dp at the MEASURED effective density 2.6875
        // (was 70 dp, derived at an assumed 2.625).
        //
        // The label is kept in the tree but hidden so it stays available as
        // an accessibility label rather than being drawn where iOS has none.
        labelView.visibility = GONE
        labelView.contentDescription = tile.name
        container.addView(labelView)
        container.addView(stateText)
        addView(container)

        val size = (CONTROL_DIAMETER_DP * resources.displayMetrics.density).toInt()
        layoutParams = FrameLayout.LayoutParams(size, size)
    }

    companion object {
        /** 16.1% of a 1080px screen, expressed in dp at density 2.6875. */
        const val CONTROL_DIAMETER_DP = 65f

        /**
         * Inactive control fill. Dark translucent, matching Apple's own
         * screenshot where inactive controls read as dark grey discs against
         * the blurred sheet. A near-white fill was invisible on light glass.
         */
        const val INACTIVE_FILL_LIGHT = 0x59000000
        const val INACTIVE_FILL_DARK = 0x40FFFFFF
    }

    /**
     * Press feedback only. This deliberately does NOT flip [isActive]:
     * the service owns state and calls [setActive] from the real platform
     * read-back. Flipping locally made every control render the inverse of
     * its true state, because setActive(realState) and this toggle raced.
     */
    fun animateToggle() {
        animate().scaleX(0.92f).scaleY(0.92f).setDuration(60)
            .withEndAction {
                animate().scaleX(1f).scaleY(1f).setDuration(120).start()
            }.start()
    }

    /** Single source of truth for a control's appearance. Called by the service. */
    fun setActive(active: Boolean) {
        isActive = active
        surfaceColor = if (active) ColorTokens.systemBlue
        else if (ColorTokens.darkMode) INACTIVE_FILL_DARK else INACTIVE_FILL_LIGHT
        iconView.iconState = if (isActive) IconTokens.States.ACTIVE else IconTokens.States.NORMAL
        labelView.setTextColor(if (isActive) ColorTokens.systemBlue else ColorTokens.secondaryLabel)
        stateText.visibility = if (isActive) VISIBLE else GONE
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
    }

    private fun Int.toFloat(): Float = this.toFloat()
}
