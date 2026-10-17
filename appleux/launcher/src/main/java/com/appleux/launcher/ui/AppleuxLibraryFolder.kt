package com.appleux.launcher.ui

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.appleux.design.components.AppleuxText
import com.appleux.design.tokens.ColorTokens

/**
 * iOS 27 App Library folder: glass card with 2x2 equal-sized mini icons
 * and a category label below. Tap opens the full category grid.
 *
 * Design matches the iOS 27 mockup: 30dp corner radius, translucent white
 * background with blur, 13dp mini icon radius, 12dp gap between minis.
 */
class AppleuxLibraryFolder @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : LinearLayout(context, attrs, defStyle) {

    private val box = FrameLayout(context)
    private val label = AppleuxText(context)
    var onOpen: (() -> Unit)? = null

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        // iOS 27 glass folder: 30dp radius, translucent white with blur
        box.background = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = 30f * resources.displayMetrics.density
            setColor((0x59 shl 24) or 0xFFFFFF)
        }
        val d = resources.displayMetrics.density
        box.layoutParams = LayoutParams((170 * d).toInt(), (170 * d).toInt())
        label.apply {
            textSize = 11f
            setTextColor(ColorTokens.label)
            gravity = Gravity.CENTER
            setPadding(0, (8 * d).toInt(), 0, (8 * d).toInt())
        }
        addView(box)
        addView(label)
        isClickable = true
        isFocusable = true
        setOnClickListener { onOpen?.invoke() }
    }

    /** iOS 27 pattern: 2x2 equal-sized minis with 12dp gap, 13dp icon radius. */
    fun bind(category: String, apps: List<AppIconItem>, onApp: (AppIconItem) -> Unit) {
        label.text = category
        box.removeAllViews()
        val d = resources.displayMetrics.density
        val minis = apps.take(4)
        minis.forEachIndexed { i, app ->
            val v = SquircleImageView(context).apply {
                scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                isClickable = true
                setOnClickListener { onApp(app) }
            }
            val art = AppleuxIconPack.resolve(context, app.packageName)
            if (art != null) v.setPackArt(art)
            else v.setImageDrawable(app.icon)
            // iOS 27: 2x2 equal-sized minis with 12dp gap
            val sizeFrac = 0.42f  // each mini is 42% of the folder width
            val gapFrac = 0.06f   // 6% gap between minis
            val paddingFrac = 0.06f  // 6% padding around the grid
            val xFrac = if (i % 2 == 0) paddingFrac else paddingFrac + sizeFrac + gapFrac
            val yFrac = if (i < 2) paddingFrac else paddingFrac + sizeFrac + gapFrac
            val bw = (170 * d * sizeFrac).toInt()
            val bh = (170 * d * sizeFrac).toInt()
            v.layoutParams = FrameLayout.LayoutParams(bw, bh, Gravity.TOP or Gravity.START).apply {
                leftMargin = (170 * d * xFrac).toInt()
                topMargin = (170 * d * yFrac).toInt()
            }
            box.addView(v)
        }
    }
}
