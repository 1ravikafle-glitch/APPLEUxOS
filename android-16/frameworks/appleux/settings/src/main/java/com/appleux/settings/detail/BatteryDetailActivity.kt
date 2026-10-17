package com.appleux.settings.detail

import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.widget.ScrollView
import com.appleux.design.components.AppleuxCard
import com.appleux.design.components.AppleuxListItem
import com.appleux.settings.R

/**
 * Owned Battery detail page: live level, charge state and health.
 *
 * Replaces a row that fired `ACTION_BATTERY_SAVER_SETTINGS` (a stock screen).
 * The battery intent that ships with the platform already carries the
 * percentage and health as extras, so this reads one broadcast rather than
 * polling — no battery sysfs, no per-frame work.
 */
class BatteryDetailActivity : SettingsDetailActivity() {

    private val main = Handler(Looper.getMainLooper())
    private lateinit var card: AppleuxCard
    private lateinit var list: android.widget.LinearLayout

    private var lastSticky = Sticky()

    override val titleText: String get() = getString(R.string.battery)

    override fun onContentReady() {
        list = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
        }
        card = AppleuxCard(this).apply {
            applyLiquidGlassRim()
            addView(list)
        }
        content.addView(
            ScrollView(this).apply { addView(card) },
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val sticky = readSticky()
        lastSticky = sticky
        if (!::list.isInitialized) return
        list.removeAllViews()

        list.addView(row(
            getString(R.string.battery_level),
            if (sticky.level in 0..100) "${sticky.level}%" else getString(R.string.battery_unknown)
        ))
        list.addView(row(getString(R.string.battery_state), sticky.stateLabel(this)))
        list.addView(row(getString(R.string.battery_health), sticky.healthLabel(this), last = true))
    }

    private fun row(title: String, value: String, last: Boolean = false) =
        AppleuxListItem(this).apply {
            setTitle(title)
            setSubtitle(value)
            setDividerVisible(!last)
            // Non-interactive readout: the page informs, it does not toggle.
            isClickable = false
            isFocusable = false
        }

    private fun readSticky(): Sticky {
        val intent = runCatching {
            registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull() ?: return Sticky()

        val raw = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val level = if (raw >= 0 && scale > 0) raw * 100 / scale else -1

        return Sticky(
            level = level,
            status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1),
            plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0),
            health = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1),
            present = intent.getBooleanExtra(BatteryManager.EXTRA_PRESENT, true)
        )
    }

    private data class Sticky(
        val level: Int = -1,
        val status: Int = -1,
        val plugged: Int = 0,
        val health: Int = -1,
        val present: Boolean = true
    ) {
        fun stateLabel(ctx: Context): String = when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING ->
                ctx.getString(R.string.battery_charging)
            BatteryManager.BATTERY_STATUS_FULL ->
                ctx.getString(R.string.battery_full)
            BatteryManager.BATTERY_STATUS_DISCHARGING ->
                if (level in 1..15) ctx.getString(R.string.battery_low)
                else ctx.getString(R.string.battery_discharging)
            else -> ctx.getString(R.string.battery_unknown)
        }

        fun healthLabel(ctx: Context): String = when (health) {
            BatteryManager.BATTERY_HEALTH_GOOD -> ctx.getString(R.string.battery_health_good)
            BatteryManager.BATTERY_HEALTH_COLD -> ctx.getString(R.string.battery_health_cold)
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> ctx.getString(R.string.battery_health_overheat)
            BatteryManager.BATTERY_HEALTH_DEAD -> ctx.getString(R.string.battery_health_dead)
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE ->
                ctx.getString(R.string.battery_health_over_voltage)
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE ->
                ctx.getString(R.string.battery_health_failure)
            else -> ctx.getString(R.string.battery_unknown)
        }
    }
}