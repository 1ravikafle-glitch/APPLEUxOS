package com.appleux.design.components

import android.content.Context
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.telephony.TelephonyManager
import android.util.AttributeSet
import android.view.Gravity
import android.widget.LinearLayout
import com.appleux.design.tokens.ColorTokens
import com.appleux.design.tokens.SpacingTokens

/**
 * Control Center header: the iOS top block showing connectivity summary,
 * date, and battery. Data is pushed in by the SystemUI service (which owns
 * the platform BroadcastReceivers) so this view stays free of listeners and
 * does not leak.
 */
class AppleuxControlCenterHeader @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val connectivityText: AppleuxText
    private val dateText: AppleuxText
    private val timeText: AppleuxText
    private val batteryText: AppleuxText

    init {
        orientation = VERTICAL
        setPadding(
            SpacingTokens.PADDING_MEDIUM,
            SpacingTokens.PADDING_MEDIUM,
            SpacingTokens.PADDING_MEDIUM,
            0
        )

        // Row 1: connectivity summary, left; battery, right.
        val topRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            )
        }

        connectivityText = AppleuxText(context).apply {
            textSize = 15f
            setTextStyle(AppleuxText.TextStyle.SEMIBOLD)
            setTextColor(ColorTokens.label)
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        }

        batteryText = AppleuxText(context).apply {
            textSize = 15f
            setTextStyle(AppleuxText.TextStyle.REGULAR)
            setTextColor(ColorTokens.secondaryLabel)
            gravity = Gravity.END
        }

        topRow.addView(connectivityText)
        topRow.addView(batteryText)
        addView(topRow)

        // Row 2: compact date + time (iOS CC has NO huge clock — that is
        // Notification Center. 15/28sp keeps clear of the status bar).
        dateText = AppleuxText(context).apply {
            textSize = 15f
            setTextStyle(AppleuxText.TextStyle.SEMIBOLD)
            setTextColor(ColorTokens.label)
            setPadding(0, SpacingTokens.PADDING_SMALL, 0, 0)
        }
        addView(dateText)

        timeText = AppleuxText(context).apply {
            textSize = 28f
            setTextStyle(AppleuxText.TextStyle.MEDIUM)
            setTextColor(ColorTokens.label)
            setPadding(0, 0, 0, SpacingTokens.PADDING_SMALL)
        }
        addView(timeText)
    }

    /**
     * @param activeRadios names of enabled transports (e.g. "Wi-Fi", "Cellular")
     * @param airplaneMode true when airplane mode is on (shows the label)
     * @param charging true when the device is plugged in
     * @param batteryPercent 0..100
     */
    /** iOS mode: summary line only — CC never shows date/time. */
    fun setCompact(compact: Boolean) {
        dateText.visibility = if (compact) GONE else VISIBLE
        timeText.visibility = if (compact) GONE else VISIBLE
    }

    fun bind(
        activeRadios: List<String>,
        airplaneMode: Boolean,
        charging: Boolean,
        batteryPercent: Int
    ) {
        connectivityText.text = buildString {
            if (airplaneMode) {
                append("Airplane Mode")
                if (activeRadios.isNotEmpty()) append("  ·  ")
            }
            append(activeRadios.joinToString("  ·  ").ifEmpty { "No Service" })
        }

        batteryText.text = buildString {
            append(if (charging) "⚡ " else "")
            append(batteryPercent)
            append("%")
        }

        dateText.text = android.text.format.DateFormat.format("EEEE, MMMM d", now())
        timeText.text = android.text.format.DateFormat.format("h:mm", now())
    }

    private fun now(): Long = System.currentTimeMillis()

    companion object {
        /**
         * Derives the iOS-style connectivity summary from Android service
         * state. Reads are advisory only: a service can report available but
         * not actually connected.
         */
        fun activeRadioNames(context: Context): List<String> {
            val names = mutableListOf<String>()
            val app = context.applicationContext
            @Suppress("DEPRECATION")
            val wifi = app.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            if (runCatching { wifi?.isWifiEnabled == true }.getOrDefault(false)) {
                names.add("Wi-Fi")
            }

            val telephony =
                app.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            if (telephony?.phoneType != TelephonyManager.PHONE_TYPE_NONE) {
                names.add("Cellular")
            }

            @Suppress("DEPRECATION")
            val bt = app.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            if (runCatching { bt?.adapter?.isEnabled == true }.getOrDefault(false)) {
                names.add("Bluetooth")
            }
            return names
        }

        fun isAirplaneMode(context: Context): Boolean = runCatching {
            android.provider.Settings.Global.getInt(
                context.contentResolver,
                android.provider.Settings.Global.AIRPLANE_MODE_ON,
                0
            ) == 1
        }.getOrDefault(false)

        /** @return percent to [percent] with [isCharging] to the caller. */
        fun batteryState(context: Context): Pair<Int, Boolean> {
            val bm = context.applicationContext.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )
            val level = bm?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = bm?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val status = bm?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val pct = if (level >= 0 && scale > 0) level * 100 / scale else 0
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
            return pct to charging
        }
    }
}