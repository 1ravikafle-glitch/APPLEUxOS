package com.appleux.design.components

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.ColorInt
import com.appleux.design.tokens.ColorTokens
import com.appleux.design.tokens.ElevationTokens
import com.appleux.design.tokens.SizingTokens

/**
 * A grouped-list card: the rounded container Settings-style rows sit inside.
 *
 * The surface is a child rather than this view's own background so callers can
 * add content with plain `addView` and have it layer over the fill, and so the
 * Liquid Glass rim can be drawn without fighting the platform background.
 *
 * Colour is resolved at draw time by [AppleuxSurface]; assigning [cardColor]
 * only ever overrides the default, so a theme flip is still picked up by every
 * card that did not set an explicit colour.
 */
open class AppleuxCard @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val surface = AppleuxSurface(context).apply {
        layoutParams = LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.MATCH_PARENT
        )
        // Defaults are applied here, not left to property-initializer order.
        surfaceColor = ColorTokens.systemBackgroundElevated
        cornerRadius = SizingTokens.CORNER_RADIUS_CARD
        surfaceElevation = ElevationTokens.ELEVATION_NONE
    }

    /** Explicit fill. `null` (the default) follows [ColorTokens] per draw. */
    var cardColor: Int? = null
        set(value) {
            field = value
            surface.surfaceColor = value ?: ColorTokens.systemBackgroundElevated
            invalidate()
        }

    var cardCornerRadius: Float = SizingTokens.CORNER_RADIUS_CARD
        set(value) {
            field = value
            surface.cornerRadius = value
            invalidate()
        }

    var cardElevation: Float = ElevationTokens.ELEVATION_NONE
        set(value) {
            field = value
            surface.surfaceElevation = value
            invalidate()
        }

    init {
        addView(surface)
        setBackground(null)
        clipToOutline = true
        isClickable = true
        isFocusable = true
    }

    /** Replaces the card's content with [view]. */
    fun setContentView(view: View) {
        removeAllViews()
        addView(view)
    }

    fun setBorder(width: Float, @ColorInt color: Int) {
        surface.setBorder(width, color)
    }

    /** Switches the fill between opaque and Liquid Glass Regular. */
    fun setBlurEnabled(enabled: Boolean) {
        surface.setBlurEnabled(enabled)
        invalidate()
    }

    /** Adds the kit's Liquid Glass rim around the card. */
    fun applyLiquidGlassRim() {
        surface.applyLiquidGlassRim()
        invalidate()
    }
}