package com.appleux.launcher.ui

import android.content.Context
import android.graphics.Outline
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.appleux.design.components.AppleuxHaptics
import com.appleux.launcher.LauncherTokens
import com.appleux.launcher.R
import com.appleux.performance.quality.AppleuxQualityLevel

class AppIconView @JvmOverloads constructor(
    context: Context,
    attrs: android.util.AttributeSet? = null,
    defStyle: Int = 0
) : FrameLayout(context, attrs, defStyle) {

    private val iconView: ImageView
    private val labelView: TextView
    private val haptics = AppleuxHaptics.getInstance(context)

    /** Set by the hosting adapter so a tap opens the app. */
    var onItemClick: ((AppIconItem) -> Unit)? = null

    /**
     * The item currently shown, published so the launcher's root-level
     * long-press can identify which icon was pressed without re-querying the
     * adapter. Null until the first [bind].
     */
    var boundItem: AppIconItem? = null
        private set

    /** True while the finger is down on this cell; drives the press scale. */
    private var fingerDown = false

    init {
        LayoutInflater.from(context).inflate(R.layout.app_icon_view, this, true)
        iconView = findViewById(R.id.icon)
        labelView = findViewById(R.id.label)
        isClickable = true
        isFocusable = true
        // Apple's REAL iOS 27 squircle, not an approximation. The previous
        // rounded-rect at ~28% radius was eyeballed and measurably wrong:
        // the true outline is a continuous-curvature superellipse (n~4.4) and
        // differs from a circular corner by ~38% more than it should.
        // Path is scaled from Apple's 1024pt grid, so it is exact at any size.
        iconView.clipToOutline = true
        iconView.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                AppleIconSquircle.outline(view.width, view.height, outline)
            }
        }
        // iOS press feedback: the whole cell (icon AND label) springs down a
        // little and fires a light haptic. Without this the grid is the only
        // surface in the ROM with no press feedback at all.
        //
        // PROVENANCE: prototype-derived — IOSLikeROM `theme/LiquidGlass.kt`
        // `Modifier.pressable` scales to 0.94 on a spring(damping 0.6,
        // stiffness 500) and fires `HapticFeedbackType.TextHandleMove`. Apple's
        // own icon-press scale is not published.
        attachPressFeedback()
    }

    private fun attachPressFeedback() {
        val slop = ViewConfiguration.get(context).scaledTouchSlop
        setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> setFingerDown(true)
                MotionEvent.ACTION_MOVE -> {
                    // Sliding off the cell releases the scale, matching iOS.
                    // event.x/y are already in this view's coordinate space.
                    if (event.x < -slop || event.x > v.width + slop ||
                        event.y < -slop || event.y > v.height + slop
                    ) {
                        setFingerDown(false)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> setFingerDown(false)
            }
            // Observe only: the click still has to fire.
            false
        }
        setOnClickListener {
            haptics.lightImpact()
            onItemClick?.invoke(boundItem ?: return@setOnClickListener)
        }
    }

    private fun setFingerDown(value: Boolean) {
        if (fingerDown == value) return
        fingerDown = value
        val target = if (value) LauncherTokens.PRESS_SCALE else 1f
        animate().cancel()
        animate()
            .scaleX(target)
            .scaleY(target)
            .setDuration(if (value) PRESS_DOWN_MS else PRESS_UP_MS)
            .start()
    }

    /**
     * @param iconSizeDp icon edge in dp. Defaults to the home-screen token, and
     *   the dock passes [LauncherTokens.DOCK_ICON_SIZE_DP] so its cell width and
     *   its icon agree and the gap comes only from the cell margin.
     * @param showLabel false for the dock, which carries icons only.
     */
    fun bind(
        item: AppIconItem,
        scale: Float,
        quality: AppleuxQualityLevel,
        iconSizeDp: Float = LauncherTokens.HOME_ICON_SIZE_DP,
        showLabel: Boolean = true
    ) {
        boundItem = item
        iconView.setImageDrawable(item.icon)
        labelView.text = item.label
        labelView.visibility = if (showLabel) View.VISIBLE else View.GONE

        // Tokens are expressed in dp like every other design-system size, so
        // they have to be converted before being used as a pixel layout size.
        val density = resources.displayMetrics.density
        val size = (iconSizeDp * density * scale).toInt()
        val lp = iconView.layoutParams
        lp.width = size
        lp.height = size
        iconView.layoutParams = lp

        iconView.setLayerType(
            if (quality.shouldSimplifyIcons()) View.LAYER_TYPE_HARDWARE else View.LAYER_TYPE_NONE,
            null
        )
        // Recycled holders come back with whatever scale the last holder had.
        if (!fingerDown) {
            animate().cancel()
            scaleX = 1f
            scaleY = 1f
        }
    }

    private companion object {
        const val PRESS_DOWN_MS = 90L
        const val PRESS_UP_MS = 160L
    }
}