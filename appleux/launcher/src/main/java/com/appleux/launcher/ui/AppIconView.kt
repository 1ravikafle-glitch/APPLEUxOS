package com.appleux.launcher.ui

import android.content.Context
import android.graphics.Outline
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.appleux.design.tokens.IconTokens
import com.appleux.launcher.R
import com.appleux.performance.quality.AppleuxQualityLevel

class AppIconView @JvmOverloads constructor(
    context: Context,
    attrs: android.util.AttributeSet? = null,
    defStyle: Int = 0
) : FrameLayout(context, attrs, defStyle) {

    private val iconView: ImageView
    private val labelView: TextView

    /** Set by the hosting adapter so a tap opens the app. */
    var onItemClick: ((AppIconItem) -> Unit)? = null

    /**
     * The item currently shown, published so the launcher's root-level
     * long-press can identify which icon was pressed without re-querying the
     * adapter. Null until the first [bind].
     */
    var boundItem: AppIconItem? = null
        private set

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
    }

    /**
     * @param iconSizeDp icon edge in dp. Defaults to the home-screen icon
     *   token; the dock passes [com.appleux.design.tokens.SizingTokens.DOCK_ICON_SIZE]
     *   so its cell width and its icon agree and the gap comes only from the
     *   cell margin.
     */
    fun bind(
        item: AppIconItem,
        scale: Float,
        quality: AppleuxQualityLevel,
        iconSizeDp: Float = IconTokens.SYSTEM_ICON_SIZE_HOME_SCREEN
    ) {
        boundItem = item
        iconView.setImageDrawable(item.icon)
        labelView.text = item.label
        setOnClickListener { onItemClick?.invoke(item) }

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
    }
}
