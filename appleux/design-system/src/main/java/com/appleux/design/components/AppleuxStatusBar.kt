package com.appleux.design.components

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.appleux.design.tokens.ColorTokens
import com.appleux.design.tokens.IconTokens
import com.appleux.design.tokens.SpacingTokens
import com.appleux.design.tokens.TypographyTokens

/**
 * The iOS status bar: carrier text on the leading edge, status icons on the
 * trailing edge.
 *
 * ## ⚠ ROM-STAGE COMPONENT — read before wiring this up
 *
 * **This cannot be installed as the system status bar on a stock image.**
 * Drawing over the platform status bar, or replacing it, requires the
 * `android.permission.STATUS_BAR` (and to suppress the system bar,
 * `INTERNAL_SYSTEM_WINDOW`) — both are **signature|privileged**, so only a
 * platform-signed build gets them. `scripts/build-emulator-apks.sh` signs
 * every module with `tools/debug.keystore`, which carries neither.
 *
 * On an emulator or any non-platform-signed build, attempting this yields a
 * `SecurityException`, not a degraded-but-working status bar.
 *
 * What *is* usable today:
 *  - inside our own overlays — [AppleuxControlCenter] and
 *    [AppleuxNotificationShade] render one of these in their own header, which
 *    is how it is meant to be used until the ROM lands; and
 *  - as a plain in-app status strip inside any of our surfaces.
 *
 * `AppleuxSystemUIService` imports this type but does not instantiate it, and
 * must keep not instantiating it until the build is platform-signed.
 *
 * ## What was wrong before
 *
 * Every field was dead. `setIcons` and `setCarrierText` populated lists that
 * nothing ever read; `onDraw` painted a flat background rect and a separator
 * line and stopped. The result was an empty grey strip with no content.
 */
class AppleuxStatusBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val carrier = AppleuxText(context)
    private val iconRow = LinearLayout(context)
    private val items = mutableListOf<StatusBarIcon>()

    /**
     * A trailing icon. [resId] is one of this module's `sf_*` vectors, or any
     * drawable the caller can resolve.
     */
    data class StatusBarIcon(
        @DrawableRes val resId: Int,
        /** Announced instead of [resId] when the vector carries no label. */
        val contentDescription: String = ""
    )

    private val iconSize = LayoutUtils.dpToPx(IconTokens.SIZE_STATUS_BAR, context)
    private val iconGap = LayoutUtils.dpToPx(SpacingTokens.INSETS_SM, context)

    private val clockHandler = android.os.Handler(android.os.Looper.getMainLooper())
    var showClock: Boolean = true
    private var carrierOverride: String? = null
    private val clockTick = object : Runnable {
        override fun run() {
            if (showClock) {
                carrier.text = carrierPrefix() + android.text.format.DateFormat.format("h:mm", System.currentTimeMillis())
                carrier.visibility = VISIBLE
            }
            clockHandler.postDelayed(this, 20_000L)
        }
    }
    private fun carrierPrefix(): String =
        if (carrierOverride.isNullOrEmpty()) "" else "${carrierOverride}  "

    init {
        outlineProvider = ViewOutlineProvider.BACKGROUND
        // iOS 27: transparent over wallpaper/glass, never an opaque strip.
        setBackgroundColor(android.graphics.Color.TRANSPARENT)

        addView(carrier, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            marginStart = LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, context)
        })
        addView(iconRow, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            marginEnd = LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, context)
        })

        carrier.apply {
            applyTypography(TypographyTokens.caption2Emphasized)
            visibility = GONE
        }
        iconRow.orientation = LinearLayout.HORIZONTAL
        iconRow.gravity = Gravity.CENTER_VERTICAL

        applyTheme()
        // Live clock: updates once a minute like the lock screen.
        clockHandler.post(clockTick)
    }

    /** Live bind: carrier + battery/wifi/cellular icons from platform state. */
    fun bindSystemState(carrierText: String? = null, batteryPercent: Int = -1, charging: Boolean = false) {
        if (carrierText != null) carrierOverride = carrierText
        // Keep at least time visible; icons resolved by caller via setIcons.
        if (showClock) {
            carrier.text = carrierPrefix() + android.text.format.DateFormat.format("h:mm", System.currentTimeMillis())
            carrier.visibility = VISIBLE
        }
        contentDescription = "Status bar ${carrier.text} battery $batteryPercent percent"
    }

    /** Replaces the trailing icon set. */
    fun setIcons(icons: List<StatusBarIcon>) {
        items.clear()
        items.addAll(icons)
        renderIcons()
    }

    fun addIcon(icon: StatusBarIcon) {
        items.add(icon)
        renderIcons()
    }

    fun clearIcons() {
        items.clear()
        renderIcons()
    }

    /** Leading-edge text. An empty or null string hides the slot. */
    fun setCarrierText(text: CharSequence?) {
        carrier.text = text ?: ""
        carrier.visibility = if (text.isNullOrEmpty()) GONE else VISIBLE
    }

    /** Re-resolves the background and label colours from the shared tokens. */
    fun applyTheme() {
        // Transparent glass host: white content over wallpaper/shade.
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        carrier.setTextColor(android.graphics.Color.WHITE)
        for (i in 0 until iconRow.childCount) {
            (iconRow.getChildAt(i) as? ImageView)?.setColorFilter(android.graphics.Color.WHITE)
        }
        invalidate()
    }

    /** White-on-glass variant for lock/shade/CC overlays. */
    fun applyOverlayTheme(lightContent: Boolean = true) {
        val fg = if (lightContent) android.graphics.Color.WHITE else ColorTokens.label
        carrier.setTextColor(fg)
        for (i in 0 until iconRow.childCount) {
            (iconRow.getChildAt(i) as? ImageView)?.setColorFilter(fg)
        }
        invalidate()
    }

    private fun renderIcons() {
        iconRow.removeAllViews()
        for (item in items) {
            val iv = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply {
                    marginStart = iconGap
                }
                setImageDrawable(ContextCompat.getDrawable(context, item.resId))
                setColorFilter(ColorTokens.label)
                contentDescription = item.contentDescription
            }
            iconRow.addView(iv)
        }
        iconRow.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
    }
}