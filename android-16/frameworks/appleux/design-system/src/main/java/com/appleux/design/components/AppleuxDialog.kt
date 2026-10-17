package com.appleux.design.components

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.annotation.ColorInt
import com.appleux.design.tokens.ColorTokens
import com.appleux.design.tokens.ElevationTokens
import com.appleux.design.tokens.MotionTokens
import com.appleux.design.tokens.SpacingTokens
import com.appleux.design.tokens.TypographyTokens

/**
 * An iOS alert: scrim, centred Liquid Glass panel, title, message and a
 * trailing button row.
 *
 * ## What was wrong before
 *
 *  - The paddings came from `SpacingTokens.PADDING_*`, which are raw **pixel**
 *    constants, so the inset was 16px on a 3x screen instead of 16dp.
 *  - `addButton` ended with a bare `Gravity.END` statement — a no-op line that
 *    looked like it was doing something.
 *  - `AppleuxSurface` faked its shadow by painting a translucent black rect
 *    *over* the fill, which put a dark smear across the bottom-right of every
 *    alert. The surface now uses the platform's real elevation.
 *  - `dimView` was an inert View that swallowed nothing and drew nothing.
 *
 * The type now comes from [TypographyTokens] instead of hardcoded 17f/15f, and
 * every colour is re-resolved on [show] so a dark-mode flip is picked up.
 */
class AppleuxDialog @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val dialogContainer = AppleuxSurface(context)
    private val content = LinearLayout(context)
    private val titleText = AppleuxText(context)
    private val messageText = AppleuxText(context)
    private val buttonContainer = LinearLayout(context)

    private var showing = false
    private var cancelable = true
    private var onDismissRequest: (() -> Unit)? = null

    private val marginH = LayoutUtils.dpToPx(SpacingTokens.INSETS_XXL, context)
    private val pad = LayoutUtils.dpToPx(SpacingTokens.INSETS_XXL, context)

    init {
        // The scrim is this view's own background; the previous implementation
        // added an inert sibling View to stand in for it.
        setBackgroundColor(ColorTokens.overlayPrimary)

        isClickable = true
        isFocusable = true
        setOnClickListener { if (cancelable) requestDismiss() }

        dialogContainer.apply {
            cornerRadius = CORNER_RADIUS_DP * resources.displayMetrics.density
            surfaceElevation = ElevationTokens.ELEVATION_XL
            applyLiquidGlassRim()
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            ).apply {
                marginStart = marginH
                marginEnd = marginH
            }
        }

        content.orientation = LinearLayout.VERTICAL
        content.setPadding(pad, pad, pad, LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, context))

        titleText.apply {
            applyTypography(TypographyTokens.headline)
            gravity = Gravity.CENTER
            visibility = GONE
        }
        messageText.apply {
            applyTypography(TypographyTokens.subheadline)
            gravity = Gravity.CENTER
            visibility = GONE
        }

        buttonContainer.orientation = LinearLayout.HORIZONTAL
        buttonContainer.gravity = Gravity.END or Gravity.CENTER_VERTICAL
        buttonContainer.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, context)
        }

        content.addView(titleText)
        content.addView(messageText)
        content.addView(buttonContainer)
        dialogContainer.addView(content)
        addView(dialogContainer)

        visibility = GONE
    }

    // ---- Content -----------------------------------------------------------

    fun setTitle(title: CharSequence?) {
        titleText.text = title ?: ""
        titleText.visibility = if (title.isNullOrEmpty()) GONE else VISIBLE
    }

    fun setMessage(message: CharSequence?) {
        messageText.text = message ?: ""
        messageText.visibility = if (message.isNullOrEmpty()) GONE else VISIBLE
    }

    /**
     * Adds a trailing button. iOS orders destructive actions left of the
     * cancel action; call in that order.
     */
    fun addButton(
        label: String,
        @ColorInt color: Int = ColorTokens.systemBlue,
        onClick: () -> Unit
    ) {
        val btn = AppleuxButton(context).apply {
            setText(label)
            // PLAIN is the iOS alert action: no fill. The colour is applied
            // after the style, because applyStyle resets it.
            setButtonStyle(AppleuxButton.Style.PLAIN)
            setTextColor(color)
            setSize(AppleuxButton.Size.MEDIUM)
            setOnClickListener {
                onClick()
                dismiss()
            }
        }
        buttonContainer.addView(btn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            marginStart = LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, context)
        })
    }

    /** Convenience for the common destructive/cancel pair. */
    fun setActions(
        confirmLabel: String,
        cancelLabel: String,
        destructive: Boolean = false,
        onConfirm: () -> Unit
    ) {
        buttonContainer.removeAllViews()
        addButton(cancelLabel, ColorTokens.systemBlue) {}
        addButton(
            confirmLabel,
            if (destructive) ColorTokens.systemRed else ColorTokens.systemBlue,
            onConfirm
        )
    }

    fun setCancelable(cancelable: Boolean) {
        this.cancelable = cancelable
    }

    /** Scrim tap, back press and [dismiss] all route through here. */
    fun setOnDismissRequestListener(listener: (() -> Unit)?) {
        onDismissRequest = listener
    }

    val isShowing: Boolean get() = showing

    // ---- Presentation ------------------------------------------------------

    fun show() {
        if (showing) return
        showing = true
        applyTheme()
        visibility = VISIBLE
        alpha = 0f
        animate().alpha(1f)
            .setDuration(MotionTokens.DURATION_STANDARD)
            .setInterpolator(MotionTokens.easingStandard)
            .start()
    }

    fun dismiss() {
        if (!showing) return
        showing = false
        animate().alpha(0f)
            .setDuration(MotionTokens.DURATION_FAST)
            .setInterpolator(MotionTokens.easingStandard)
            .withEndAction { visibility = GONE }
            .start()
    }

    private fun requestDismiss() {
        val listener = onDismissRequest
        if (listener != null) listener() else dismiss()
    }

    /** Back press: an alert owns the gesture while it is on screen. */
    fun onBackPressed(): Boolean {
        if (!showing || !cancelable) return false
        requestDismiss()
        return true
    }

    /** Re-resolves the scrim, panel and text from the shared tokens. */
    fun applyTheme() {
        setBackgroundColor(ColorTokens.overlayPrimary)
        dialogContainer.surfaceColor = if (ColorTokens.darkMode) {
            DIALOG_BG_DARK
        } else {
            DIALOG_BG_LIGHT
        }
        titleText.setTextColor(ColorTokens.label)
        messageText.setTextColor(ColorTokens.secondaryLabel)
    }

    override fun onDetachedFromWindow() {
        animate().cancel()
        super.onDetachedFromWindow()
    }

    private companion object {
        /**
         * PROVENANCE: AppleUX-chosen, on the [SizingTokens] radius scale. iOS
         * alerts use the device corner radius, which varies per model; a fixed
         * value is the only option that behaves the same on every panel.
         */
        const val CORNER_RADIUS_DP = 14f

        /** iOS alert background: systemBackgroundElevated, both appearances. */
        val DIALOG_BG_LIGHT = 0xFFF2F2F7.toInt()
        val DIALOG_BG_DARK = 0xFF1C1C1E.toInt()
    }
}