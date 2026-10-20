package com.appleux.design.components

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import androidx.annotation.DrawableRes
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

    /**
     * Holds a caller-supplied view or an item list.
     *
     * Added 2026-10-17: this is what blocked replacing android.app.AlertDialog
     * wholesale. Of the 26 AlertDialog.Builder call sites in the shells, 12 use
     * setView() and 9 use setItems(), and this dialog could express neither —
     * which is precisely why it had zero consumers while the stock dialog had
     * 26.
     */
    private val customContent = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        visibility = GONE
    }

    private var showing = false

    private var onDismissed: (() -> Unit)? = null
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
        content.addView(customContent)
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
     * Custom content, replacing title + message.
     *
     * Added 2026-10-17 — see [customContent]. The supplied view joins the same
     * content column, so it inherits the panel padding and the button row
     * beneath it. Title and message are hidden, so a caller that forgets to
     * clear them cannot end up with stacked content.
     */
    fun setContent(view: android.view.View?) {
        customContent.removeAllViews()
        if (view == null) {
            customContent.visibility = GONE
            return
        }
        // The caller may reuse this view, so detach it from any previous parent
        // and drop the layout params it arrived with; this column measures it.
        (view.parent as? android.view.ViewGroup)?.removeView(view)
        (view.layoutParams as? android.widget.LinearLayout.LayoutParams)
            ?.let { view.layoutParams = null }
        customContent.addView(
            view,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        customContent.visibility = VISIBLE
        titleText.visibility = GONE
        messageText.visibility = GONE
    }

    /**
     * A vertical list of choices: the iOS action-sheet / picker body.
     *
     * Added 2026-10-17 — 9 of the 26 AlertDialog sites use setItems(). Rows are
     * HIG Body 17 in the label colour, full-width, with the iOS press fill
     * rather than a Material ripple.
     */
    fun setItems(
        items: List<CharSequence>,
        onPick: (index: Int) -> Unit,
    ) {
        customContent.removeAllViews()
        customContent.visibility = VISIBLE
        titleText.visibility = GONE
        messageText.visibility = GONE
        val padV = LayoutUtils.dpToPx(SpacingTokens.INSETS_SM, context)
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        )
        items.forEachIndexed { index, label ->
            val row = AppleuxText(context).apply {
                text = label
                applyTypography(TypographyTokens.body)
                setTextColor(ColorTokens.label)
                gravity = Gravity.CENTER_VERTICAL
                isClickable = true
                isFocusable = true
                setPadding(0, padV, 0, padV)
                background = context.getDrawable(
                    com.appleux.design.R.drawable.appleux_pressed
                )
                setOnClickListener {
                    onPick(index)
                    dismiss()
                }
            }
            customContent.addView(row, lp)
        }
    }

    /**
     * Checkbox rows. iOS `.actionSheet` with multiple selection, and the reason
     * two "add to group" / "remove from group" dialogs could not be converted:
     * `setMultiChoiceItems` had no equivalent here, and the alternative —
     * replacing it with a single-select list — silently changes what the control
     * does.
     *
     * @param onChange fires on every toggle so the caller can read [checked]
     *   back, exactly as AlertDialog's listener did.
     */
    fun setMultiChoiceItems(
        items: List<CharSequence>,
        checked: BooleanArray,
        onChange: (index: Int, isChecked: Boolean) -> Unit = { _, _ -> },
    ) {
        customContent.removeAllViews()
        customContent.visibility = VISIBLE
        titleText.visibility = GONE
        messageText.visibility = GONE
        val padV = LayoutUtils.dpToPx(SpacingTokens.INSETS_SM, context)
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        )
        items.forEachIndexed { index, label ->
            val row = AppleuxText(context).apply {
                text = label
                applyTypography(TypographyTokens.body)
                setTextColor(ColorTokens.label)
                gravity = Gravity.CENTER_VERTICAL
                isClickable = true
                isFocusable = true
                setPadding(0, padV, 0, padV)
                // Checkbox glyph on the trailing edge, iOS table style. A text
                // prefix would reflow the label when the row is read aloud, so
                // the mark is drawn, not typed.
                setCompoundDrawablesWithIntrinsicBounds(
                    0, 0, markDrawableFor(checked.getOrElse(index) { false }), 0,
                )
                compoundDrawablePadding = LayoutUtils.dpToPx(SpacingTokens.INSETS_SM, context)
                background = context.getDrawable(
                    com.appleux.design.R.drawable.appleux_pressed
                )
                setOnClickListener {
                    val now = !checked.getOrElse(index) { false }
                    if (index in checked.indices) checked[index] = now
                    setCompoundDrawablesWithIntrinsicBounds(
                        0, 0, markDrawableFor(now), 0,
                    )
                    onChange(index, now)
                }
            }
            customContent.addView(row, lp)
        }
    }

    /**
     * The checkbox mark for a row: filled tick inside a disc when checked, the
     * bare ring when not.
     *
     * One helper rather than an `if` at each call site, because there are two and
     * they must not drift — a row that draws the checked glyph when unchecked is
     * exactly the kind of thing that ships because nobody compared the two.
     */
    @DrawableRes
    private fun markDrawableFor(isChecked: Boolean): Int =
        if (isChecked) {
            com.appleux.design.R.drawable.sf_checkmark_circle
        } else {
            com.appleux.design.R.drawable.sf_circle
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
            .withEndAction {
                visibility = GONE
                // Fires after the fade, not on request, so a host that removes
                // this view on dismissal does not yank it out mid-animation.
                onDismissed?.invoke()
            }
            .start()
    }

    /**
     * Notified once the dismiss animation has finished.
     *
     * Added with [AppleuxDialogs]: the dialog has no window of its own, so its
     * host has to be told when to detach it. This is why the class had zero
     * consumers before — nothing could host it, and nothing could clean it up.
     */
    fun setOnDismissedListener(listener: (() -> Unit)?) {
        onDismissed = listener
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