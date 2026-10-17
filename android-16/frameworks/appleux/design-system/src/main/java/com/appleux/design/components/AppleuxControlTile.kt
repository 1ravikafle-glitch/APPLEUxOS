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
) : LinearLayout(context, attrs, defStyleAttr) {

    private val disc: AppleuxSurface
    private val iconView: AppleuxIcon
    private val labelView: AppleuxText
    private val stateText: AppleuxText

    private var isActive: Boolean = false

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        isClickable = true
        isFocusable = true

        // iOS disc: dark translucent when idle, solid colour when active
        // (Drive set rule 7: tiny labels sit UNDER the discs).
        disc = AppleuxSurface(context).apply {
            surfaceColor = if (ColorTokens.darkMode) INACTIVE_FILL_DARK else INACTIVE_FILL_LIGHT
            cornerRadius = SizingTokens.CORNER_RADIUS_ROUND
            rimColor = if (ColorTokens.darkMode) {
                LiquidGlassTokens.RIM_DARK
            } else {
                LiquidGlassTokens.RIM_LIGHT
            }
            rimWidthDp = LiquidGlassTokens.RIM_SPREAD_DP
            surfaceElevation = 0f
        }
        val px = (CONTROL_DIAMETER_DP * resources.displayMetrics.density).toInt()
        disc.layoutParams = LayoutParams(px, px)

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
        disc.addView(iconView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        ))
        addView(disc)

        labelView = AppleuxText(context).apply {
            text = tile.name
            textSize = 10f
            setTextStyle(AppleuxText.TextStyle.REGULAR)
            setTextColor(ColorTokens.secondaryLabel)
            gravity = Gravity.CENTER_HORIZONTAL
            maxLines = 1
            // iOS tiles carry no caption: kept for accessibility only.
            visibility = GONE
        }
        labelView.contentDescription = tile.name
        addView(labelView, LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.WRAP_CONTENT
        ))

        stateText = AppleuxText(context).apply {
            text = ""
            textSize = 10f
            setTextStyle(AppleuxText.TextStyle.MEDIUM)
            setTextColor(ColorTokens.systemBlue)
            visibility = GONE
            gravity = Gravity.CENTER_HORIZONTAL
        }
        addView(stateText, LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.WRAP_CONTENT
        ))

        contentDescription = tile.name
    }

    companion object {
        /** 16.1% of a 1080px screen, expressed in dp at density 2.75. */
        const val CONTROL_DIAMETER_DP = 63f

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
        val rm = runCatching { com.appleux.design.runtime.AppleuxRuntimeConfig.isReduceMotionEnabled }.getOrDefault(false)
        if (rm) return
        animate().scaleX(0.92f).scaleY(0.92f).setDuration(60)
            .withEndAction {
                animate().scaleX(1f).scaleY(1f).setDuration(120).start()
            }.start()
    }

    /** Single source of truth for a control's appearance. Called by the service. */
    fun setActive(active: Boolean) {
        isActive = active
        disc.surfaceColor = if (active) tile.activeColor
        else if (ColorTokens.darkMode) INACTIVE_FILL_DARK else INACTIVE_FILL_LIGHT
        iconView.iconState = if (isActive) IconTokens.States.ACTIVE else IconTokens.States.NORMAL
        labelView.setTextColor(if (isActive) tile.activeColor else ColorTokens.secondaryLabel)
        stateText.visibility = if (isActive) VISIBLE else GONE
        invalidate()
    }

}
