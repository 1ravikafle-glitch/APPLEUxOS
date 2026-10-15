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
    private val line1: AppleuxText
    private val line2: AppleuxText

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
        addView(chargeDot, LayoutParams(dotPx, dotPx, Gravity.CENTER).apply {
            // Right of centre, clear of the camera beneath: the real waterdrop
            // lens sits at exact centre, so nothing is drawn over it.
            marginStart = (24f * density).toInt()
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
        if (expanded) renderExpanded()
    }

    private fun renderExpanded() {
        when (slots.topKind()) {
            LiveSlots.Kind.CALL -> {
                line1.text = "📞 ${slots.callText}"
                line2.text = "Tap to return to call"
            }
            LiveSlots.Kind.TIMER -> {
                line1.text = "⏱ ${slots.timerText}"
                line2.text = "Timer"
            }
            LiveSlots.Kind.MUSIC -> {
                line1.text =
                    (if (slots.musicPlaying) "▶ " else "⏸ ") + slots.musicText
                line2.text = "Now Playing"
            }
            LiveSlots.Kind.BATTERY -> {
                line1.text =
                    if (slots.charging) "⚡ ${slots.batteryPercent}%" else "${slots.batteryPercent}%"
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
