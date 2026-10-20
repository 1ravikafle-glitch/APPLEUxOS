package com.appleux.design.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.appleux.design.tokens.ColorTokens
import com.appleux.design.tokens.IconTokens
import com.appleux.design.tokens.SizingTokens
import com.appleux.design.tokens.SpacingTokens
import com.appleux.design.tokens.TypographyTokens

/**
 * One row of a grouped Settings-style list.
 *
 * ## What was wrong before
 *
 * This row rendered as an **empty grey bar**. `setTitle` stored a String in
 * `title`, `setSubtitle` stored a String in `subtitle`, `setIcon` loaded a
 * drawable into `iconDrawable` — and none of them were ever drawn. The four
 * `TextPaint`s and the icon `ImageView` were never touched by `onDraw`, which
 * meant a caller could add ten thousand of these to a list and see ten
 * thousand blank rows.
 *
 * It also drew its background *over* its children (`onDraw` calling
 * `super.dispatchDraw`), and only when `cornerRadius > 0` — which it never
 * was by default — so it had no background at all out of the box.
 *
 * It is now a real row: icon, title, subtitle, accessory, a hairline divider,
 * and light/dark resolved on every theme change rather than frozen at
 * construction.
 */
class AppleuxListItem @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val iconView = ImageView(context)
    private val textColumn = LinearLayout(context)
    private val titleText = AppleuxText(context)
    private val subtitleText = AppleuxText(context)
    private val accessorySlot = FrameLayout(context)

    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var backgroundColor: Int? = null
    private var cornerRadius: Float = 0f
    private var showDivider: Boolean = false
    private var dividerColor: Int? = null

    private val insetH = LayoutUtils.dpToPx(SpacingTokens.INSET_XL, context)
    private val insetV = LayoutUtils.dpToPx(SpacingTokens.INSETS_SM, context)
    private val iconSize = LayoutUtils.dpToPx(IconTokens.SIZE_LARGE, context)
    private val gap = LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, context)

    init {
        // Else onDraw (dividers) never runs on this LinearLayout.
        setWillNotDraw(false)
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(insetH, insetV, insetH, insetV)
        isClickable = true
        isFocusable = true
        minimumHeight = LayoutUtils.dpToPx(SizingTokens.TOUCH_TARGET_MIN, context)

        iconView.layoutParams = LayoutParams(iconSize, iconSize).apply {
            marginEnd = gap
        }
        iconView.scaleType = ImageView.ScaleType.FIT_CENTER
        iconView.visibility = GONE
        addView(iconView)

        textColumn.orientation = VERTICAL
        textColumn.gravity = Gravity.CENTER_VERTICAL
        textColumn.layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)

        titleText.apply {
            applyTypography(TypographyTokens.body)
            maxLines = 1
        }
        subtitleText.apply {
            applyTypography(TypographyTokens.subheadline)
            maxLines = 2
            visibility = GONE
        }
        textColumn.addView(titleText)
        textColumn.addView(subtitleText)
        addView(textColumn)

        accessorySlot.visibility = GONE
        addView(accessorySlot)

        applyTheme()
    }

    // ---- Content -----------------------------------------------------------

    fun setTitle(title: CharSequence?) {
        titleText.text = title ?: ""
        titleText.visibility = if (title.isNullOrEmpty()) GONE else VISIBLE
        requestLayout()
    }

    fun setSubtitle(subtitle: CharSequence?) {
        subtitleText.text = subtitle ?: ""
        subtitleText.visibility = if (subtitle.isNullOrEmpty()) GONE else VISIBLE
        requestLayout()
    }

    fun setIcon(@DrawableRes resId: Int) {
        if (resId == 0) {
            setIconDrawable(null)
            return
        }
        iconView.setImageDrawable(ContextCompat.getDrawable(context, resId))
        iconView.visibility = VISIBLE
    }

    fun setIconDrawable(drawable: android.graphics.drawable.Drawable?) {
        iconView.setImageDrawable(drawable)
        iconView.visibility = if (drawable == null) GONE else VISIBLE
    }

    /** Places a view (a chevron, a toggle, a value label) in the trailing slot. */
    fun setAccessoryView(view: View?) {
        accessorySlot.removeAllViews()
        if (view == null) {
            accessorySlot.visibility = GONE
            return
        }
        accessorySlot.addView(view)
        accessorySlot.visibility = VISIBLE
    }

    // ---- Appearance --------------------------------------------------------

    /**
     * Rounds the row's background. Pass 0 for a row inside a card that rounds
     * only its first and last rows.
     */
    fun setRoundCorners(radius: Float) {
        cornerRadius = radius
        applyTheme()
    }

    fun setBackgroundColorInt(@ColorInt color: Int) {
        backgroundColor = color
        applyTheme()
    }

    /** Clears an explicit background so the row follows the theme again. */
    fun clearBackgroundOverride() {
        backgroundColor = null
        applyTheme()
    }

    fun setDividerVisible(visible: Boolean) {
        showDivider = visible
        invalidate()
    }

    fun setDividerColor(@ColorInt color: Int) {
        dividerColor = color
        invalidate()
    }

    /**
     * Re-resolves every colour from the shared tokens.
     *
     * Must be called after a dark-mode flip. Call it from `onResume` in any
     * surface that builds a list once and reuses it, or the rows keep the
     * palette they were constructed with.
     */
    fun applyTheme() {
        val bg = backgroundColor ?: ColorTokens.systemBackgroundElevated
        val r = LayoutUtils.dpToPxFloat(cornerRadius, context)
        background = if (cornerRadius > 0f) {
            GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = r
                setColor(bg)
            }
        } else {
            GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(bg)
            }
        }

        titleText.setTextColor(ColorTokens.label)
        subtitleText.setTextColor(ColorTokens.secondaryLabel)
        dividerColor?.let { dividerPaint.color = it }
            ?: run { dividerPaint.color = ColorTokens.separator }
        invalidate()
    }

    // ---- Measurement -------------------------------------------------------

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Honour the incoming specs. The old version overrode the height with
        // a hard EXACTLY, so a row inside a weighted list silently ignored the
        // parent's intent.
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        val minHeight = LayoutUtils.dpToPx(SizingTokens.TOUCH_TARGET_MIN, context)
        val twoLine = LayoutUtils.dpToPx(SizingTokens.TOUCH_TARGET_MIN, context) + gap
        val desired = if (subtitleText.visibility == VISIBLE) twoLine else minHeight
        if (measuredHeight < desired) {
            setMeasuredDimension(measuredWidth, desired)
        }
    }

    override fun onDraw(canvas: Canvas) {
        // LinearLayout.onDraw runs before children, which is where a divider
        // belongs: it must never be painted over a row's own content.
        super.onDraw(canvas)
        if (!showDivider) return
        val h = dividerHeightPx()
        canvas.drawRect(
            insetH.toFloat(),
            height - h,
            (width - insetH).toFloat(),
            height.toFloat(),
            dividerPaint
        )
    }

    private fun dividerHeightPx(): Float = resources.displayMetrics.density
}