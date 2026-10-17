package com.appleux.settings.display

import android.provider.Settings
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import com.appleux.design.components.AppleuxCard
import com.appleux.design.components.AppleuxListItem
import com.appleux.design.components.AppleuxToggle
import com.appleux.settings.R
import com.appleux.settings.detail.SettingsDetailActivity
import com.appleux.design.components.AppleuxToast

/**
 * Owned Display & Brightness page.
 *
 * Replaces a stub whose only action was `ACTION_DISPLAY_SETTINGS` (stock).
 * Brightness is a real platform setting, so the slider writes
 * `Settings.System.SCREEN_BRIGHTNESS` directly and auto-brightness writes
 * `SCREEN_BRIGHTNESS_MODE` — the two values the old page could never show.
 *
 * Writing SCREEN_* needs the user-granted WRITE_SETTINGS appop; without it the
 * platform refuses the write, which we surface instead of silently snapping
 * the slider back.
 */
class DisplaySettingsActivity : SettingsDetailActivity() {

    private lateinit var card: AppleuxCard
    private lateinit var list: android.widget.LinearLayout

    override val titleText: String get() = getString(R.string.display)

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
        if (!::list.isInitialized) return
        list.removeAllViews()

        val auto = Settings.System.getInt(
            contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, -1
        ) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
        val level = Settings.System.getInt(
            contentResolver, Settings.System.SCREEN_BRIGHTNESS, DEFAULT_LEVEL
        ).coerceIn(1, MAX_LEVEL)

        list.addView(
            AppleuxListItem(this).apply {
                setTitle(getString(R.string.display_auto_brightness_label))
                setDividerVisible(true)
                val toggle = AppleuxToggle(this@DisplaySettingsActivity).apply {
                    setChecked(auto, animate = false)
                    setOnCheckedChangeListener { on -> setAutoBrightness(on) }
                }
                setAccessoryView(toggle)
            }
        )

        list.addView(brightnessRow(level, auto))
    }

    /**
     * The slider is disabled while auto-brightness owns the value, which is
     * what iOS does: a manual level and an automatic one cannot both apply.
     */
    private fun brightnessRow(level: Int, auto: Boolean): AppleuxListItem {
        val label = TextView(this).apply {
            text = getString(R.string.display_brightness_label)
            textSize = 17f
            setPadding(
                (16 * resources.displayMetrics.density).toInt(),
                (12 * resources.displayMetrics.density).toInt(),
                (16 * resources.displayMetrics.density).toInt(),
                0
            )
        }
        val seek = SeekBar(this).apply {
            max = MAX_LEVEL
            progress = level
            isEnabled = !auto
            alpha = if (auto) DISABLED_ALPHA else 1f
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    if (fromUser) writeBrightness(p)
                }

                override fun onStartTrackingTouch(sb: SeekBar?) = Unit
                override fun onStopTrackingTouch(sb: SeekBar?) = Unit
            })
        }

        val column = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            addView(label)
            addView(seek)
        }
        return AppleuxListItem(this).apply {
            setDividerVisible(false)
            val holder = android.widget.FrameLayout(this@DisplaySettingsActivity)
            holder.addView(column)
            addView(holder)
        }
    }

    private fun setAutoBrightness(on: Boolean) {
        writeSetting(
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            if (on) Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
            else Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
        )
        render()
    }

    private fun writeBrightness(level: Int) {
        writeSetting(Settings.System.SCREEN_BRIGHTNESS, level.coerceIn(1, MAX_LEVEL))
    }

    /** Applies with no appop of its own; WRITE_SETTINGS does the authorising. */
    private fun writeSetting(key: String, value: Int) {
        runCatching {
            Settings.System.putInt(contentResolver, key, value)
        }.onFailure {
            AppleuxToast.show(this, getString(R.string.write_settings_required))
        }
    }

    private companion object {
        const val MIN_LEVEL = 1
        const val MAX_LEVEL = 255
        const val DEFAULT_LEVEL = 128
        const val DISABLED_ALPHA = 0.4f
    }
}