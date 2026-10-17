package com.appleux.design.components

    // android.R.drawable -> vendored iOS vector (2026-10-17):
    //   com.appleux.design.R.drawable.sf_app_library           -> il_ic_grid       placeholder for an app with no artwork
import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout

/**
 * iOS 27 incoming-alert banner: slides in from the LEFT edge (iOS 27
 * changed this from top-drop), parks top-centre below the cutout, and
 * auto-dismisses. Tap opens the notification's content intent.
 *
 * Owned by the SystemUI service as an overlay window (same overlayParams as
 * the shade/CC). No receivers, no platform reads — everything is pushed in.
 */
class AppleuxNotificationBanner @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    var onOpen: (() -> Unit)? = null
    var onExpired: (() -> Unit)? = null

    private val icon = ImageView(context)
    private val title = AppleuxText(context)
    private val text = AppleuxText(context)
    private val hide = Runnable { hide(animated = true) }

    init {
        val d = resources.displayMetrics.density
        background = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = 28f * d
            setColor((0xE6 shl 24) or 0x3C3C43)
        }
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((16 * d).toInt(), (14 * d).toInt(), (16 * d).toInt(), (14 * d).toInt())
        }
        icon.layoutParams = LinearLayout.LayoutParams((40 * d).toInt(), (40 * d).toInt())
        row.addView(icon)
        val col = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((12 * d).toInt(), 0, 0, 0)
        }
        title.apply {
            applyTypography(com.appleux.design.tokens.TypographyTokens.subheadlineEmphasized)
            setTextColor(android.graphics.Color.WHITE)
            maxLines = 1
        }
        text.apply {
            applyTypography(com.appleux.design.tokens.TypographyTokens.subheadline)
            setTextColor(0xFFC7C7CC.toInt())
            maxLines = 2
        }
        col.addView(title)
        col.addView(text)
        row.addView(col, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        isClickable = true
        isFocusable = true
        setOnClickListener { onOpen?.invoke(); hide(animated = false) }
    }

    fun bind(
        appLabel: String,
        body: String,
        iconDrawable: android.graphics.drawable.Drawable?
    ) {
        title.text = appLabel
        text.text = body
        if (iconDrawable != null) icon.setImageDrawable(iconDrawable)
        else icon.setImageResource(com.appleux.design.R.drawable.il_ic_grid)
        contentDescription = "$appLabel: $body"
    }

    /** iOS 27 entry: from the left edge. Reduce Motion: fade instead. */
    fun show(animated: Boolean = true) {
        visibility = VISIBLE
        removeCallbacks(hide)
        val reduceMotion = runCatching {
            com.appleux.design.runtime.AppleuxRuntimeConfig.isReduceMotionEnabled
        }.getOrDefault(false)
        if (!animated || reduceMotion) {
            translationX = 0f
            alpha = 1f
        } else {
            // Post: width is 0 before first layout, which would park the card
            // off-screen forever instead of animating it in.
            post {
                translationX = -width.toFloat()
                alpha = 1f
                animate().translationX(0f).setDuration(320L)
                    .setInterpolator(android.view.animation.DecelerateInterpolator(1.2f))
                    .start()
            }
        }
        postDelayed(hide, BANNER_LINGER_MS)
    }

    fun hide(animated: Boolean = true) {
        removeCallbacks(hide)
        val reduceMotion = runCatching {
            com.appleux.design.runtime.AppleuxRuntimeConfig.isReduceMotionEnabled
        }.getOrDefault(false)
        if (!animated || reduceMotion) {
            visibility = GONE
            onExpired?.invoke()
            return
        }
        animate().translationX(-width.toFloat()).setDuration(220L)
            .withEndAction {
                visibility = GONE
                translationX = 0f
                onExpired?.invoke()
            }.start()
    }

    private companion object {
        const val BANNER_LINGER_MS = 4500L
    }
}
