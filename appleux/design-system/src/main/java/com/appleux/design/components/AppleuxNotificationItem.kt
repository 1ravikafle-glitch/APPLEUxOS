package com.appleux.design.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.appleux.design.tokens.ColorTokens
import com.appleux.design.tokens.LiquidGlassTokens
import com.appleux.design.tokens.SpacingTokens

/**
 * A single notification, rendered the way iOS 27 does it: a rounded glass
 * CARD floating over the frosted shade, with side insets and vertical gaps
 * between rows — never a full-bleed band.
 *
 * PROVENANCE — radius and insets were measured off Apple's own guide
 * screenshot `design-reference/ios27/official-guide/images/9f9c87b1-*.png`
 * (the "Oooh / Kehlani" lock-screen card). Fitting the corner arc against
 * that image gives r ~= 26.7px on a 447px-wide screen, i.e. ~6% of screen
 * width ~= 24dp. That is deliberately NOT
 * [LiquidGlassTokens.REGULAR_CORNER_RADIUS_DP] (34dp), which is the kit's
 * radius for Large/Medium glass surfaces such as the Control Center sheet,
 * not for a ~90dp-tall notification card.
 */
class AppleuxNotificationItem @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density

    private fun dp(v: Float): Float = v * density

    private fun dpInt(v: Float): Int = dp(v).toInt()

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val path = Path()
    private val rectF = RectF()

    /** iOS 27 notification cards: ~24dp, measured off Apple's screenshot. */
    private val cornerRadius: Float = dp(NOTIFICATION_CORNER_RADIUS_DP)

    /** Side inset of the card from the shade edges (this view's own padding). */
    private val cardInset: Int = dpInt(CARD_INSET_DP)

    private var glassLevel: Int = AppleuxGlass.DEFAULT_LEVEL

    private val groupHeader: AppleuxText
    private val titleText: AppleuxText
    private val subtitleText: AppleuxText
    private val timestampText: AppleuxText
    private val iconView: ImageView
    private val actionRow: LinearLayout

    init {
        // LinearLayout skips onDraw unless cleared (see AppleuxSurface).
        setWillNotDraw(false)
        orientation = VERTICAL

        // Outer padding IS the card geometry: [cardInset, gapTop] is the strip
        // where the blurred shade shows through before the card starts.
        setPadding(cardInset, dpInt(CARD_GAP_DP), cardInset, dpInt(CARD_GAP_DP))

        val content = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(
                dpInt(CARD_CONTENT_INSET_DP),
                dpInt(CARD_CONTENT_PADDING_DP),
                dpInt(CARD_CONTENT_INSET_DP),
                dpInt(CARD_CONTENT_PADDING_SMALL_DP)
            )
        }
        addView(content)

        // App label for the head of a same-app run. GONE unless the adapter
        // groups two or more consecutive notifications from one package.
        groupHeader = AppleuxText(context).apply {
            textSize = 13f
            setTextStyle(AppleuxText.TextStyle.SEMIBOLD)
            setTextColor(ColorTokens.secondaryLabel)
            visibility = GONE
        }
        content.addView(groupHeader)

        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        content.addView(row)

        // Fixed 40dp badge, top-aligned with the title, matching the iOS row.
        // A fixed size keeps a missing or huge small icon from shifting the
        // whole text column.
        iconView = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(dpInt(40f), dpInt(40f))
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        row.addView(iconView)

        val textContainer = LinearLayout(context).apply {
            orientation = VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                weight = 1f
                marginStart = SpacingTokens.MARGIN_SMALL
            }
        }

        titleText = AppleuxText(context).apply {
            textSize = 16f
            setTextStyle(AppleuxText.TextStyle.SEMIBOLD)
            setTextColor(ColorTokens.label)
        }

        subtitleText = AppleuxText(context).apply {
            textSize = 14f
            setTextStyle(AppleuxText.TextStyle.REGULAR)
            setTextColor(ColorTokens.secondaryLabel)
        }

        timestampText = AppleuxText(context).apply {
            textSize = 12f
            setTextStyle(AppleuxText.TextStyle.REGULAR)
            setTextColor(ColorTokens.tertiaryLabel)
        }

        textContainer.addView(titleText)
        textContainer.addView(subtitleText)
        textContainer.addView(timestampText)

        row.addView(textContainer)

        // iOS-style inline action buttons (up to 3). Hidden when empty.
        actionRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            visibility = GONE
        }
        content.addView(actionRow)
    }

    fun setIcon(@DrawableRes iconRes: Int) {
        iconView.setImageDrawable(ContextCompat.getDrawable(context, iconRes))
        iconView.visibility = VISIBLE
    }

    /** Posting app badge. Null hides the slot entirely (no empty gap). */
    fun setIconDrawable(drawable: android.graphics.drawable.Drawable?) {
        if (drawable == null) {
            iconView.visibility = GONE
            return
        }
        iconView.visibility = VISIBLE
        iconView.setImageDrawable(drawable)
    }

    fun setTitle(title: String) {
        titleText.text = title
    }

    fun setSubtitle(subtitle: String) {
        subtitleText.visibility = if (subtitle.isEmpty()) GONE else VISIBLE
        subtitleText.text = subtitle
    }

    fun setTimestamp(timestamp: String) {
        timestampText.text = timestamp
    }

    /**
     * iOS 27 draws notification actions as blue tinted CAPSULES, not as bare
     * borderless text (see the notification page screenshots in
     * `design-reference/ios27/official-guide/images/`).
     */
    fun setActions(
        actions: List<AppleuxNotificationShade.NotificationAction>,
        onTap: (AppleuxNotificationShade.NotificationAction) -> Unit,
        onReply: ((AppleuxNotificationShade.NotificationAction, String) -> Unit)? = null
    ) {
        actionRow.removeAllViews()
        if (actions.isEmpty()) {
            actionRow.visibility = GONE
            return
        }
        actionRow.visibility = VISIBLE
        // Q5 rule: free-form RemoteInput -> inline reply field; else capsule button.
        actions.firstOrNull { it.hasInlineReply }?.let { reply ->
            val box = android.widget.EditText(context).apply {
                hint = reply.title; isSingleLine = true
                textSize = 14f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val send = Button(context, null, android.R.attr.borderlessButtonStyle).apply {
                text = "Send"; isAllCaps = false; textSize = 14f
                setTextColor(ColorTokens.systemBlue)
                setOnClickListener {
                    onReply?.invoke(reply, box.text.toString())
                }
            }
            val replyRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(box); addView(send)
            }
            actionRow.addView(replyRow, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT))
            return
        }
        val padH = dpInt(ACTION_PILL_PADDING_H_DP)
        val padV = dpInt(ACTION_PILL_PADDING_V_DP)
        for (action in actions.take(3)) {
            val button = Button(context, null, android.R.attr.borderlessButtonStyle).apply {
                text = action.title
                isAllCaps = false
                textSize = 14f
                setTextColor(ColorTokens.systemBlue)
                gravity = Gravity.CENTER
                minWidth = 0
                minimumWidth = 0
                minHeight = 0
                minimumHeight = 0
                includeFontPadding = false
                background = capsuleBackground()
                stateListAnimator = null
                setPadding(padH, padV, padH, padV)
                setOnClickListener { onTap(action) }
            }
            actionRow.addView(button, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = SpacingTokens.PADDING_MEDIUM
            })
        }
    }

    private fun capsuleBackground(): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        // A radius past half the shorter side collapses to a true pill.
        cornerRadius = dp(CORNER_RADIUS_ROUND_DP)
        setColor(withAlpha(ColorTokens.systemBlue, ACTION_PILL_FILL_ALPHA))
        setStroke(
            dpInt(ACTION_PILL_RIM_DP),
            withAlpha(ColorTokens.systemBlue, ACTION_PILL_RIM_ALPHA)
        )
    }

    /**
     * Group decoration applied by the shade's adapter.
     *
     * @param groupLabel app name for the head of a same-app run; null hides
     *   the header.
     * @param stacked true when the row above came from the same package — the
     *   gap tightens so the run reads as one stack, as on iOS.
     */
    fun setGroupDecoration(groupLabel: String?, stacked: Boolean) {
        if (groupLabel.isNullOrEmpty()) {
            groupHeader.visibility = GONE
        } else {
            groupHeader.visibility = VISIBLE
            groupHeader.text = groupLabel
        }
        setPadding(
            cardInset,
            dpInt(if (stacked) CARD_STACKED_GAP_DP else CARD_GAP_DP),
            cardInset,
            paddingBottom
        )
    }

    /**
     * Liquid Glass card fill + rim, drawn as an inset rounded rect. The old
     * full-width `drawRect` is what made every notification a flat band.
     */
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val left = paddingLeft.toFloat()
        val top = paddingTop.toFloat()
        val right = (width - paddingRight).toFloat()
        val bottom = (height - paddingBottom).toFloat()
        if (right <= left || bottom <= top) return

        rectF.set(left, top, right, bottom)
        val r = minOf(cornerRadius, minOf(rectF.width(), rectF.height()) / 2f)
        path.reset()
        path.addRoundRect(rectF, r, r, Path.Direction.CW)

        // Same translucent fill the Control Center sheet uses, so the window's
        // FLAG_BLUR_BEHIND reads through rather than being painted over.
        bgPaint.color = if (ColorTokens.darkMode) {
            LiquidGlassTokens.REGULAR_BG_DARK
        } else {
            LiquidGlassTokens.REGULAR_BG_LIGHT
        }
        bgPaint.alpha = AppleuxGlass.scrimAlphaForLevel(glassLevel)
        canvas.drawPath(path, bgPaint)

        // Rim: the hairline that separates the glass from what is beneath it.
        rimPaint.color = if (ColorTokens.darkMode) {
            LiquidGlassTokens.RIM_DARK
        } else {
            LiquidGlassTokens.RIM_LIGHT
        }
        rimPaint.alpha = RIM_ALPHA
        rimPaint.strokeWidth = dp(LiquidGlassTokens.RIM_SPREAD_DP * 2f).coerceAtLeast(1f)
        canvas.drawPath(path, rimPaint)
    }

    private companion object {
        /** Measured off Apple's guide screenshot; see the class comment. */
        const val NOTIFICATION_CORNER_RADIUS_DP = 24f
        const val CARD_INSET_DP = 16f
        const val CARD_GAP_DP = 8f
        const val CARD_STACKED_GAP_DP = 2f
        const val CARD_CONTENT_INSET_DP = 14f
        const val CARD_CONTENT_PADDING_DP = 12f
        const val CARD_CONTENT_PADDING_SMALL_DP = 10f

        const val CORNER_RADIUS_ROUND_DP = 9999f
        const val ACTION_PILL_PADDING_H_DP = 14f
        const val ACTION_PILL_PADDING_V_DP = 6f
        const val ACTION_PILL_RIM_DP = 1f
        const val ACTION_PILL_FILL_ALPHA = 0x24 // ~14% blue wash
        const val ACTION_PILL_RIM_ALPHA = 0x59 // ~35% blue hairline

        const val RIM_ALPHA = 0x80

        fun withAlpha(color: Int, alpha: Int): Int =
            (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)
    }
}