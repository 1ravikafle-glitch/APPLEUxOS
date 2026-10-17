package com.appleux.design.components

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.appleux.design.tokens.ColorTokens

/**
 * iOS 27 Now-Playing card for Control Center + Lock Screen.
 * Artwork slot, title/artist, prev/play/next + AirPlay. All callbacks,
 * no media code — the service owns MediaSession.
 */
class AppleuxMediaCard @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppleuxCard(context, attrs, defStyleAttr) {

    private val title = AppleuxText(context)
    private val artist = AppleuxText(context)
    private val row = LinearLayout(context)
    private val prev = mediaButton("system_previous", "previous track")
    private val play = mediaButton("system_play", "play or pause")
    private val next = mediaButton("system_next", "next track")

    var onPrev: (() -> Unit)? = null
    var onToggle: (() -> Unit)? = null
    var onNext: (() -> Unit)? = null

    init {
        applyLiquidGlassRim()
        val col = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        title.apply {
            applyTypography(com.appleux.design.tokens.TypographyTokens.subheadlineEmphasized)
            setTextColor(ColorTokens.label); maxLines = 1
            text = "Not Playing"
        }
        artist.apply {
            applyTypography(com.appleux.design.tokens.TypographyTokens.subheadline)
            setTextColor(ColorTokens.secondaryLabel); maxLines = 1
            text = "iPhone"
        }
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER
        for (v in listOf(prev, play, next)) {
            v.setPadding(dp(18), dp(6), dp(18), dp(6))
            v.isClickable = true; v.isFocusable = true
            row.addView(v)
        }
        prev.setOnClickListener { onPrev?.invoke() }
        play.setOnClickListener { onToggle?.invoke() }
        next.setOnClickListener { onNext?.invoke() }
        col.addView(title); col.addView(artist); col.addView(row)
        addView(col)
    }

    fun bind(titleText: String?, artistText: String?, playing: Boolean) {
        title.text = titleText ?: "Not Playing"
        artist.text = artistText ?: "iPhone"
        play.iconName = if (playing) "system_pause" else "system_play"
        contentDescription = "${title.text} ${artist.text}"
    }

    private fun mediaButton(icon: String, desc: String) = AppleuxIcon(context).apply {
        iconName = icon
        iconSize = 22f
        contentDescription = desc
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
