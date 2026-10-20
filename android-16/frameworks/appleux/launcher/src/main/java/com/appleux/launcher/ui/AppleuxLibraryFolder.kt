package com.appleux.launcher.ui

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.appleux.design.components.AppleuxText
import com.appleux.performance.quality.AppleuxQualityLevel

/**
 * iOS 27 App Library folder tile (ground truth: design-reference/iphone11-ios27/
 * drive/applibrary-light-p1.png).
 *
 * - A square glass card that FILLS its grid cell (was a hardcoded 170dp box,
 *   which overflowed the old 3-column slots and made every row overlap).
 * - Up to 4 icon slots: <=4 apps -> all big; >4 -> 3 big + a 2x2 mini-cluster
 *   of the next four in the 4th slot (the exact iOS pattern in the reference:
 *   Social = Messenger/Facebook/TikTok + a 4-mini cluster with the 175 badge).
 * - Badges render on every mini (AppleuxBadgeStore), white category label with
 *   a soft shadow centered below the card.
 *
 * The tile is square regardless of cell width (see [box]'s onMeasure) and icons
 * are (re)laid out from FRACTIONS of the actual width in [reflowIcons], because
 * RecyclerView binds before layout — a bind-time width is always 0.
 */
class AppleuxLibraryFolder @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : LinearLayout(context, attrs, defStyle) {

    private var boundApps: List<AppIconItem> = emptyList()
    private var onApp: ((AppIconItem) -> Unit)? = null
    private var reflowNeeded = false

    private val box = object : FrameLayout(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            val w = measuredWidth
            // Square tile: iOS folder cards are as tall as they are wide.
            if (w > 0 && measuredHeight != w) setMeasuredDimension(w, w)
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            if (w > 0) reflowIcons()
        }
    }

    private val label = AppleuxText(context)
    var onOpen: (() -> Unit)? = null

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        // iOS 27 glass folder: translucent white over the blurred wallpaper.
        box.background = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = 30f * resources.displayMetrics.density
            setColor((0x59 shl 24) or 0xFFFFFF)
        }
        val d = resources.displayMetrics.density
        box.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        label.apply {
            textSize = 11f
            // White with a soft shadow: the label sits straight on the wallpaper
            // (ColorTokens.label is near-black in light mode and vanished on it).
            // 0xFFFFFFFF / 0x99000000 exceed Int.MAX: Kotlin infers Long and
            // setTextColor/setShadowLayer take Int — .toInt() reinterprets the bits.
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            setShadowLayer(4f * d, 0f, 1f * d, 0x99000000.toInt())
            setPadding(0, (8 * d).toInt(), 0, (4 * d).toInt())
        }
        addView(box)
        addView(label)
        isClickable = true
        isFocusable = true
        setOnClickListener { onOpen?.invoke() }
    }

    fun bind(category: String, apps: List<AppIconItem>, onApp: (AppIconItem) -> Unit) {
        label.text = category
        boundApps = apps
        this.onApp = onApp
        reflowNeeded = true
        // A12 (B verdict: tiles render labels but no icons — uiautomator
        // shows no icon nodes, i.e. never laid out, not invisible). The
        // RecyclerView binds BEFORE the layout pass, and box can reach its
        // final size WITHOUT onSizeChanged firing in the bind->measure
        // window (a 0->0 or equal-size transition is silent). Classic
        // bind-before-layout trap. The post{} runs after this frame's
        // measure+layout, when width is guaranteed real; reflowIcons is
        // idempotent (reflowNeeded guard + removeAllViews), so overlap with
        // the onSizeChanged path is harmless. Kept: the recycled-holder
        // immediate path and the onSizeChanged path.
        if (box.width > 0) reflowIcons()
        else box.post { reflowIcons() }
    }

    /** Places the icons at fractions of the tile's measured width. */
    private fun reflowIcons() {
        if (!reflowNeeded) return
        val w = box.width
        if (w <= 0) return
        reflowNeeded = false
        box.removeAllViews()
        val d = resources.displayMetrics.density

        fun place(app: AppIconItem, xPx: Int, yPx: Int, sizePx: Int) {
            val v = AppIconView(context)
            v.onItemClick = { launched -> onApp?.invoke(launched) }
            v.bind(app, 1f, AppleuxQualityLevel.QUALITY_100, sizePx / d, false)
            v.setBadgeCount(AppleuxBadgeStore.countFor(context, app.packageName))
            v.layoutParams = FrameLayout.LayoutParams(
                sizePx, sizePx, Gravity.TOP or Gravity.START
            ).apply {
                leftMargin = xPx
                topMargin = yPx
            }
            box.addView(v)
        }

        val size = (w * 0.42f).toInt()
        val c1 = (w * 0.06f).toInt()
        val c2 = (w * 0.54f).toInt()
        if (boundApps.size > 4) {
            // 3 big icons + the 4th slot as a 2x2 mini-cluster of apps 4-7.
            place(boundApps[0], c1, c1, size)
            place(boundApps[1], c2, c1, size)
            place(boundApps[2], c1, c2, size)
            val mini = (w * 0.19f).toInt()
            val gap = (w * 0.04f).toInt()
            for (i in 3 until minOf(7, boundApps.size)) {
                val k = i - 3
                place(boundApps[i], c2 + (k % 2) * (mini + gap), c2 + (k / 2) * (mini + gap), mini)
            }
        } else {
            boundApps.forEachIndexed { i, app ->
                place(app, if (i % 2 == 0) c1 else c2, if (i < 2) c1 else c2, size)
            }
        }
    }
}
