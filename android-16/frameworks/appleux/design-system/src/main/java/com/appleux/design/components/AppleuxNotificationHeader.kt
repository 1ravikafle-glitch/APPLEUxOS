package com.appleux.design.components

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import com.appleux.design.tokens.ColorTokens
import com.appleux.design.tokens.SpacingTokens

/**
 * Lock-screen style header for the notification shade: time + date.
 *
 * Draws NO background of its own. On iOS the header sits directly on the
 * frosted shade, so painting an opaque fill here (as this view used to) hid
 * the window's FLAG_BLUR_BEHIND behind the clock. The shade owns the glass
 * fill; this view only lays out type.
 */
class AppleuxNotificationHeader @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val timeText: AppleuxText
    private val dateText: AppleuxText

    init {
        orientation = VERTICAL
        // Matches the card side inset the shade applies to notification cards,
        // so the clock lines up with the left edge of the cards beneath it.
        val padH = (CARD_ALIGN_INSET_DP * resources.displayMetrics.density).toInt()
        setPadding(
            padH,
            SpacingTokens.PADDING_MEDIUM,
            padH,
            SpacingTokens.PADDING_SMALL
        )

        timeText = AppleuxText(context).apply {
            textSize = 34f
            setTextStyle(AppleuxText.TextStyle.SEMIBOLD)
            setTextColor(ColorTokens.label)
            text = java.time.LocalTime.now().format(HH_MM)
        }

        dateText = AppleuxText(context).apply {
            textSize = 17f
            setTextStyle(AppleuxText.TextStyle.REGULAR)
            setTextColor(ColorTokens.secondaryLabel)
            text = java.time.LocalDate.now().format(EEE_MMM_D)
        }

        addView(LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            addView(timeText)
            addView(dateText, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.END
                marginStart = SpacingTokens.MARGIN_SMALL
            })
        })
    }

    private companion object {
        /** Keeps the clock aligned with the left edge of the cards below. */
        const val CARD_ALIGN_INSET_DP = 16f

        val HH_MM = java.time.format.DateTimeFormatter.ofPattern("h:mm")
        val EEE_MMM_D = java.time.format.DateTimeFormatter.ofPattern("EEE, MMM d")
    }
}