package com.appleux.settings.ui

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.appleux.design.components.AppleuxCard
import com.appleux.design.components.AppleuxListItem
import com.appleux.design.components.AppleuxText
import com.appleux.design.components.AppleuxToggle
import com.appleux.design.runtime.AppleuxRuntimeConfig
import com.appleux.design.theme.AppleuxTheme
import com.appleux.design.tokens.ColorTokens
import com.appleux.settings.R
import com.appleux.settings.pref.AppleuxPreferences
import com.appleux.settings.pref.AppleuxSettingsStore

/**
 * AppleUX Customization — a separate Settings page grouping every
 * AppleUX-specific toggle so the iOS 27 settings menu above stays clean.
 *
 * Uses the same component APIs as SettingsListActivity:
 * - AppleuxToggle: setChecked(value, animate) + setOnCheckedChangeListener
 * - SeekBar (stock): setOnSeekBarChangeListener with progress tracking
 * - AppleuxSettingsStore: getInt/setInt/getBoolean/setBoolean
 */
class CustomizationActivity : AppCompatActivity() {

    private lateinit var store: AppleuxSettingsStore
    private lateinit var prefs: AppleuxPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppleuxTheme.applyToActivity(this)
        store = AppleuxSettingsStore(this)
        prefs = AppleuxPreferences.getInstance(this)

        val scroll = ScrollView(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(40))
        }

        // Header
        col.addView(AppleuxText(this).apply {
            text = getString(R.string.customization_header)
            textSize = 22f
            setTextColor(ColorTokens.label)
            setPadding(0, 0, 0, dp(4))
        })
        col.addView(AppleuxText(this).apply {
            text = getString(R.string.customization_header_summary)
            textSize = 13f
            setTextColor(ColorTokens.secondaryLabel)
            setPadding(0, 0, 0, dp(20))
        })

        // ---- Display section ----
        col.addView(sectionHeader(getString(R.string.display_section)))

        col.addView(AppleuxCard(this).apply {
            addView(toggleRow(
                getString(R.string.customization_dark_mode),
                getString(R.string.customization_dark_mode_summary),
                AppleuxRuntimeConfig.KEY_DARK_MODE, false
            ))
        })

        col.addView(AppleuxCard(this).apply {
            addView(seekRow(
                getString(R.string.customization_glass),
                getString(R.string.customization_glass_summary),
                AppleuxRuntimeConfig.KEY_GLASS_INTENSITY,
                AppleuxRuntimeConfig.DEFAULT_GLASS_INTENSITY
            ))
        })

        col.addView(AppleuxCard(this).apply {
            addView(seekRow(
                getString(R.string.customization_brightness),
                "",
                AppleuxPreferences.KEY_BRIGHTNESS,
                AppleuxRuntimeConfig.DEFAULT_BRIGHTNESS
            ))
        })

        col.addView(AppleuxCard(this).apply {
            addView(toggleRow(
                getString(R.string.customization_auto_brightness),
                "",
                AppleuxPreferences.KEY_AUTO_BRIGHTNESS, true
            ))
        })

        // ---- Performance section ----
        col.addView(sectionHeader(getString(R.string.perf_section)))

        col.addView(AppleuxCard(this).apply {
            addView(toggleRow(
                getString(R.string.customization_adaptive),
                getString(R.string.customization_adaptive_summary),
                AppleuxRuntimeConfig.KEY_ADAPTIVE_PERFORMANCE, true
            ))
        })

        col.addView(AppleuxCard(this).apply {
            addView(toggleRow(
                getString(R.string.customization_low_power),
                getString(R.string.customization_low_power_summary),
                AppleuxRuntimeConfig.KEY_LOW_POWER_MODE, false
            ))
        })

        col.addView(AppleuxCard(this).apply {
            addView(toggleRow(
                getString(R.string.customization_reduce_motion),
                getString(R.string.customization_reduce_motion_summary),
                AppleuxRuntimeConfig.KEY_REDUCE_MOTION, false
            ))
        })

        scroll.addView(col)
        setContentView(scroll)
    }

    private fun sectionHeader(title: String): TextView = AppleuxText(this).apply {
        text = title
        textSize = 13f
        setTextColor(ColorTokens.secondaryLabel)
        setPadding(0, dp(20), 0, dp(8))
        isAllCaps = true
    }

    private fun toggleRow(
        title: String, summary: String, key: String, default: Boolean
    ): View {
        val item = AppleuxListItem(this)
        item.setTitle(title)
        if (summary.isNotEmpty()) item.setSubtitle(summary)
        val tgl = AppleuxToggle(this)
        tgl.setChecked(store.getBoolean(key, default), animate = false)
        tgl.setTrackColorOn(ColorTokens.systemGreen)
        tgl.setTrackColorOff(ColorTokens.systemFillSecondary)
        tgl.setThumbColorOn(ColorTokens.white)
        tgl.setThumbColorOff(ColorTokens.white)
        tgl.setOnCheckedChangeListener { checked ->
            store.setBoolean(key, checked)
        }
        item.addView(tgl)
        return item
    }

    private fun seekRow(
        title: String, summary: String, key: String, default: Int
    ): View {
        val item = AppleuxListItem(this)
        item.setTitle(title)
        if (summary.isNotEmpty()) item.setSubtitle(summary)
        val seek = SeekBar(this)
        seek.max = 100
        seek.progressTintList = android.content.res.ColorStateList.valueOf(ColorTokens.systemBlue)
        seek.thumbTintList = android.content.res.ColorStateList.valueOf(ColorTokens.systemBlue)
        val stored = store.getInt(key, default)
        seek.progress = (stored / 255f * 100).toInt().coerceIn(0, 100)
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                if (fromUser) {
                    store.setInt(key, (p / 100f * 255).toInt().coerceIn(0, 255))
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) = Unit
            override fun onStopTrackingTouch(sb: SeekBar?) = Unit
        })
        item.addView(seek)
        return item
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
