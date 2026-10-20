package com.appleux.design.components

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.BatteryManager
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.appleux.design.tokens.ColorTokens
import com.appleux.design.tokens.NotchTokens

/**
 * Dynamic Island on the emulated iPhone 11 notch.
 *
 * Idle, this IS the notch: a black 202x29 dp shape ([NotchTokens]) that covers
 * the waterdrop beneath it inside our overlay windows. On tap it expands into
 * a live pill; the only live state v1 binds is battery/charging, pushed by the
 * owner exactly like [AppleuxControlCenterHeader] (this view registers no
 * receivers and cannot leak).
 *
 * ROM-stage note: this draws inside OUR windows (Control Center today). It
 * cannot replace the system status bar or cover the real cutout system-wide
 * without a platform signature — same constraint as [AppleuxStatusBar].
 */
class AppleuxDynamicIsland @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    /** Idle notch or expanded live pill. No intermediate pill: the expanded
     * card shows the single highest-priority live slot (call > timer >
     * music > battery), so a third size would carry nothing extra. */
    enum class IslandState { IDLE, EXPANDED }

    /**
     * One refresh of everything the island can show. The view stores and
     * prioritises; the owner (service) only reads platform state. All fields
     * nullable: an absent slot hides, never shows a placeholder.
     */
    data class LiveSlots(
        val charging: Boolean = false,
        val batteryPercent: Int = 0,
        val chargeText: String? = null,
        /** e.g. "Mom · 02:14" or "Incoming call". Null = no call. */
        val callText: String? = null,
        /** e.g. "04:32 remaining". Null = no timer. Pushed by our Clock app. */
        val timerText: String? = null,
        /** e.g. "Blinding Lights — The Weeknd". Null = nothing playing. */
        val musicText: String? = null,
        val musicPlaying: Boolean = false
    ) {
        /** call > timer > music > battery. Battery always exists. */
        fun topKind(): Kind = when {
            callText != null -> Kind.CALL
            timerText != null -> Kind.TIMER
            musicText != null -> Kind.MUSIC
            else -> Kind.BATTERY
        }
        enum class Kind { CALL, TIMER, MUSIC, BATTERY }
        fun hasLiveActivity(): Boolean =
            callText != null || timerText != null || musicText != null
    }

    var islandState: IslandState = IslandState.IDLE
        private set

    private val density = context.resources.displayMetrics.density
    private val frameWidthPx = context.resources.displayMetrics.widthPixels

    private val notchBack = View(context)
    private val chargeDot = View(context)
    private val expandedBox: LinearLayout
    private val glyph: AppleuxIcon
    private val line1: AppleuxText
    private val line2: AppleuxText

    /**
     * Live content drawn INSIDE the notch, in its right shoulder.
     *
     * ## Why the shoulder and not the middle
     *
     * The POCO M5's waterdrop is a hole in the panel: the centre 100 px have no
     * display pixels at all, which is the whole reason the stock cutout exists.
     * Drawing there would be drawing on nothing. The mask around it, however, is
     * real pixels — 0.502 x frame wide, so roughly 221 px of usable shoulder on
     * each side of the hole, inside the notch's rounded end.
     *
     * That shoulder is what makes the notch behave like an island: a charging
     * percentage, a timer or a track name can live in the black area and read as
     * an iOS live activity, without changing the notch's iPhone 11 outline by a
     * single pixel. Content never crosses into the hole.
     */
    private val shoulderBox: LinearLayout
    private val shoulderIcon: AppleuxIcon
    private val shoulderText: AppleuxText

    /** Last text handed to [bindNotchContent], so an expand/collapse round trip
     *  does not silently drop the live activity. */
    private var notchContentText: String? = null

    private var slots = LiveSlots()

    /**
     * Pushes live state. The view registers no receivers and reads no
     * platform APIs (except the passive battery query in [chargeTimeText],
     * which the owner calls); everything arrives here.
     */
    fun bindSlots(next: LiveSlots) {
        slots = next
        val dotOn = (slots.hasLiveActivity() || slots.charging) &&
            islandState == IslandState.IDLE
        chargeDot.visibility = if (dotOn) VISIBLE else GONE
        refreshShoulder()
        if (islandState == IslandState.EXPANDED) renderExpanded()
        contentDescription = when (slots.topKind()) {
            LiveSlots.Kind.CALL -> "Dynamic Island: ${slots.callText}"
            LiveSlots.Kind.TIMER -> "Dynamic Island, timer: ${slots.timerText}"
            LiveSlots.Kind.MUSIC ->
                "Dynamic Island: ${slots.musicText}" +
                    if (slots.musicPlaying) ", playing" else ", paused"
            LiveSlots.Kind.BATTERY -> if (slots.charging) {
                "Dynamic Island: charging, ${slots.batteryPercent} percent"
            } else {
                "Dynamic Island: battery ${slots.batteryPercent} percent"
            }
        }
    }

    /**
     * Pushes the only live state v1 binds. [chargeText] is a preformatted
     * "≈ 40 min to full" line or null; formatting lives with the caller.
     */
    fun bindBattery(charging: Boolean, batteryPercent: Int, chargeText: String?) {
        bindSlots(
            slots.copy(
                charging = charging,
                batteryPercent = batteryPercent,
                chargeText = chargeText
            )
        )
    }

    init {
        isClickable = true
        isFocusable = true
        contentDescription = "Dynamic Island"

        val notchW = (frameWidthPx * NotchTokens.NOTCH_WIDTH_FRACTION).toInt()
        val notchH = (NotchTokens.NOTCH_HEIGHT_DP * density).toInt()
        val radius = NotchTokens.NOTCH_CORNER_RADIUS_DP * density

        // Solid black, top edge straight (screen edge), bottom rounded. A
        // circular arc is used deliberately here instead of the superellipse:
        // on a uniform black fill with no rim light there is no curvature
        // highlight to distort, so the continuous-curvature path buys nothing.
        notchBack.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, radius, radius, radius, radius)
            setColor(ColorTokens.black)
        }
        addView(notchBack, LayoutParams(notchW, notchH, Gravity.TOP or Gravity.CENTER_HORIZONTAL))

        val dotPx = (NotchTokens.NOTCH_DOT_SIZE_DP * density).toInt()
        chargeDot.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(ColorTokens.systemGreen)
        }
        chargeDot.visibility = GONE

        // ---- In-notch live content (the "island" half of the notch) -------
        //
        // Anchored to the notch's RIGHT end, marginEnd clear of the rounded
        // corner. It used to be added at Gravity.CENTER with a 24 dp margin,
        // which put it in the middle of the notch — and the middle of this
        // notch is the physical waterdrop, where the panel has no pixels at
        // all, so the dot could not be seen on this device no matter what it
        // was bound to. The shoulder is the part of the mask that is real
        // pixels.
        shoulderIcon = AppleuxIcon(context).apply {
            iconSize = 12f
            iconTint = ColorTokens.systemGreen
            visibility = GONE
        }
        shoulderText = AppleuxText(context).apply {
            textSize = 12f
            setTextStyle(AppleuxText.TextStyle.SEMIBOLD)
            setTextColor(ColorTokens.label)
            gravity = Gravity.CENTER_VERTICAL
        }
        shoulderBox = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = GONE
            addView(chargeDot, LayoutParams(dotPx, dotPx).apply {
                marginEnd = (4f * density).toInt()
            })
            addView(shoulderIcon, LayoutParams(
                (12f * density).toInt(), (12f * density).toInt()
            ).apply {
                marginEnd = (3f * density).toInt()
            })
            addView(shoulderText, LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT
            ))
        }
        addView(shoulderBox, LayoutParams(
            LayoutParams.WRAP_CONTENT, notchH, Gravity.TOP or Gravity.END
        ).apply {
            marginEnd = (14f * density).toInt()
        })

        expandedBox = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            visibility = GONE
            setPadding(
                (16f * density).toInt(), (20f * density).toInt(),
                (16f * density).toInt(), (20f * density).toInt()
            )
        }
        line1 = AppleuxText(context).apply {
            textSize = 22f
            setTextStyle(AppleuxText.TextStyle.SEMIBOLD)
            setTextColor(ColorTokens.label)
            gravity = Gravity.CENTER
        }
        // Leading SF-symbol glyph for the expanded card (call/timer/music/
        // battery). Emoji prefixes used to live in the strings below; glyphs
        // come from the icon registry like every other surface.
        glyph = AppleuxIcon(context).apply {
            iconSize = 30f
        }
        expandedBox.addView(glyph)
        line2 = AppleuxText(context).apply {
            textSize = 14f
            setTextStyle(AppleuxText.TextStyle.REGULAR)
            setTextColor(ColorTokens.secondaryLabel)
            gravity = Gravity.CENTER
        }
        expandedBox.addView(line1)
        expandedBox.addView(line2)
        addView(expandedBox, LayoutParams(
            (frameWidthPx * NotchTokens.ISLAND_EXPANDED_WIDTH_FRACTION).toInt(),
            LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL
        ).apply {
            topMargin = (8f * density).toInt()
        })

        setOnClickListener { setExpanded(islandState == IslandState.IDLE) }
    }

    fun setExpanded(expanded: Boolean) {
        islandState =
            if (expanded) IslandState.EXPANDED else IslandState.IDLE
        val densityPx = density
        val expH = (NotchTokens.ISLAND_EXPANDED_MIN_HEIGHT_DP * densityPx).toInt()
        val notchR = NotchTokens.NOTCH_CORNER_RADIUS_DP * densityPx
        val pillR = NotchTokens.ISLAND_EXPANDED_RADIUS_DP * densityPx
        notchBack.layoutParams.height =
            if (expanded) expH else (NotchTokens.NOTCH_HEIGHT_DP * densityPx).toInt()
        // Idle: straight top edge (screen edge), rounded bottom. Expanded:
        // fully rounded pill.
        notchBack.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadii = if (expanded) {
                floatArrayOf(pillR, pillR, pillR, pillR, pillR, pillR, pillR, pillR)
            } else {
                floatArrayOf(0f, 0f, 0f, 0f, notchR, notchR, notchR, notchR)
            }
            setColor(ColorTokens.black)
        }
        notchBack.requestLayout()
        // Kit rim + inner edge over the expanded pill. Cleared when idle: the
        // notch is uniform black with no rim light to model. dark=true always:
        // the pill is black in both appearances (like the iOS island), so the
        // dark-context rim/shade values are the matching ones.
        notchBack.foreground = if (expanded) {
            AppleuxGlassForeground(
                context = context,
                cornerRadiusDp = NotchTokens.ISLAND_EXPANDED_RADIUS_DP,
                dark = true
            )
        } else {
            null
        }
        expandedBox.visibility = if (expanded) VISIBLE else GONE
        val dotOn = (slots.hasLiveActivity() || slots.charging) && !expanded
        chargeDot.visibility = if (dotOn) VISIBLE else GONE
        // The shoulder is the notch's own content area, so it exists only
        // while the notch is the thing on screen: an expanded pill covers the
        // same pixels and would show the live text twice.
        refreshShoulder()
        if (expanded) renderExpanded()
    }

    /**
     * Binds content to the notch's own black area — the "island" behaviour.
     *
     * This is additive and off by default: a null [text] hides the row
     * entirely, so every existing caller (the launcher home surface, the shade)
     * keeps drawing a plain iPhone 11 notch, unchanged.
     *
     * @param text short string shown in the right shoulder, e.g. "82%" or
     *   "4:32". Keep it under ~6 characters: the shoulder is about 221 px on
     *   this panel and it must never reach the waterdrop hole.
     * @param iconName an [AppleuxIconRegistry] key, or null for text only. An
     *   unknown key draws nothing rather than throwing — the registry returns
     *   null and the icon view no-ops.
     * @param tint icon colour; null keeps the charging-green default.
     */
    fun bindNotchContent(
        text: String?,
        iconName: String? = null,
        tint: Int? = null,
    ) {
        notchContentText = text
        if (text.isNullOrEmpty()) {
            shoulderBox.visibility = GONE
            return
        }
        shoulderText.text = text
        shoulderIcon.iconName = iconName ?: ""
        if (tint != null) shoulderIcon.iconTint = tint
        shoulderIcon.visibility = if (iconName != null) VISIBLE else GONE
        refreshShoulder()
    }

    /**
     * One place that decides whether the notch shows content.
     *
     * The dot lives inside the shoulder rather than beside it — a dot outside
     * would have to sit left of the notch's centre, which is the waterdrop
     * again — so the shoulder's visibility depends on the dot's state too, and
     * two separate call sites setting it independently would disagree. This is
     * that decision, in one function, called from all three mutators.
     */
    private fun refreshShoulder() {
        val hasContent =
            !notchContentText.isNullOrEmpty() || chargeDot.visibility == VISIBLE
        shoulderBox.visibility =
            if (hasContent && islandState == IslandState.IDLE) VISIBLE else GONE
    }

    private fun renderExpanded() {
        val white = android.graphics.Color.WHITE
        when (slots.topKind()) {
            LiveSlots.Kind.CALL -> {
                glyph.iconName = "system_phone"
                glyph.iconTint = white
                line1.text = slots.callText
                line2.text = "Tap to return to call"
            }
            LiveSlots.Kind.TIMER -> {
                glyph.iconName = "system_hourglass"
                glyph.iconTint = white
                line1.text = slots.timerText
                line2.text = "Timer"
            }
            LiveSlots.Kind.MUSIC -> {
                glyph.iconName =
                    if (slots.musicPlaying) "system_play" else "system_pause"
                glyph.iconTint = white
                line1.text = slots.musicText
                line2.text = "Now Playing"
            }
            LiveSlots.Kind.BATTERY -> {
                glyph.iconName = "system_battery"
                glyph.iconTint = if (slots.charging) ColorTokens.systemGreen
                else white
                line1.text =
                    if (slots.charging) "${slots.batteryPercent}%" else "${slots.batteryPercent}%"
                line2.text = when {
                    slots.charging && slots.chargeText != null -> slots.chargeText
                    slots.charging -> "Charging"
                    else -> "Not charging"
                }
            }
        }
    }

    companion object {
        /**
         * "≈ 40 min to full" or null when it cannot be computed.
         * [BatteryManager.computeChargeTimeRemaining] is public API 28+, needs
         * no permission, and returns -1/unknown on failure — never throws into
         * the caller's refresh path.
         */
        fun chargeTimeText(context: Context): String? = runCatching {
            val bm = context.applicationContext
                .getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
                ?: return null
            val ms = bm.computeChargeTimeRemaining()
            if (ms <= 0) return null
            val mins = (ms / 60_000L).toInt().coerceAtLeast(1)
            if (mins >= 60) "≈ ${mins / 60} h ${mins % 60} min to full"
            else "≈ $mins min to full"
        }.getOrNull()
    }
}
