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
        // Reduce Motion consumes the flag at last: no press-scale animation.
        // Haptics stay (not motion).
        if (com.appleux.design.runtime.AppleuxRuntimeConfig.isReduceMotionEnabled) {
            animate().cancel()
            scaleX = 1f
            scaleY = 1f
            return
        }
        val target = if (value) LauncherTokens.PRESS_SCALE else 1f
        animate().cancel()
        animate()
            .scaleX(target)
            .scaleY(target)
            .setDuration(if (value) PRESS_DOWN_MS else PRESS_UP_MS)
            .start()
    }

    /**
     * Rasterizes [drawable] to a [size]x[size] bitmap masked by Apple's
     * squircle. Adaptive icons are drawn layer-by-layer full-bleed
     * (background, then foreground) so no platform mask can leak a circle
     * through; anything else draws normally. Returns null only when there is
     * nothing to draw or the size is degenerate — the caller then keeps any
     * previously bound image instead of showing an empty cell.
     */
    private fun rasterizeSquircle(
        drawable: android.graphics.drawable.Drawable?,
        size: Int
    ): android.graphics.drawable.Drawable? {
        if (drawable == null || size <= 0) return null
        val bmp = android.graphics.Bitmap.createBitmap(
            size, size, android.graphics.Bitmap.Config.ARGB_8888
        )
        val c = android.graphics.Canvas(bmp)
        c.clipPath(AppleIconSquircle.path(size, size))
        if (drawable is android.graphics.drawable.AdaptiveIconDrawable) {
            drawable.background?.let { bg ->
                bg.setBounds(0, 0, size, size)
                bg.draw(c)
            }
            drawable.foreground?.let { fg ->
                fg.setBounds(0, 0, size, size)
                fg.draw(c)
            }
        } else {
            drawable.setBounds(0, 0, size, size)
            drawable.draw(c)
        }
        return android.graphics.drawable.BitmapDrawable(resources, bmp)
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

        // iOS ships pre-masked icon assets; we rasterize to the squircle here
        // instead of relying on view clipping. Clip-based masking proved
        // unreliable across drawable types (adaptive-icon layers arrived
        // circular on-device): drawing the raw layers full-bleed through our
        // own squircle path guarantees the outline for every app.
        val packed = AppleuxIconPack.resolve(context, item.packageName)
        val squircle = iconView as? SquircleImageView
        val drawn = AppleuxIconPack.drawnKind(item.packageName)
        if (squircle != null) squircle.glyphKind = 0
        if (drawn != 0 && squircle != null) {
            squircle.setGlyphTile(drawn)
        } else if (packed != null && squircle != null) {
            if (item.packageName.lowercase().contains("calendar")) {
                val now = java.util.Calendar.getInstance()
                val wd = now.getDisplayName(
                    java.util.Calendar.DAY_OF_WEEK,
                    java.util.Calendar.SHORT, java.util.Locale.getDefault()
                ) ?: ""
                squircle.setCalendarArt(packed, wd, now.get(java.util.Calendar.DAY_OF_MONTH).toString())
            } else {
                squircle.setPackArt(packed)
            }
        } else {
            squircle?.setPackArt(null)
            iconView.setImageDrawable(rasterizeSquircle(item.icon, size))
        }

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