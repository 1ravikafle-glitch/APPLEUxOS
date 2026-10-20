package com.appleux.settings.detail

import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.provider.Settings
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import com.appleux.design.components.AppleuxListItem
import com.appleux.design.components.AppleuxSlider
import com.appleux.design.components.AppleuxToggle
import com.appleux.settings.R

/**
 * Owned Sounds & Haptics page (GT: no drive photo — built from the iOS
 * grouped-list pattern, rule 10).
 *
 * RINGER AND ALERTS slider (STREAM_RING), MEDIA slider (STREAM_MUSIC),
 * the real default-ringtone title, and the platform haptic-feedback
 * switch — every control writes the live platform value it reads.
 */
class SoundsDetailActivity : SettingsDetailActivity() {

    private val audio: AudioManager? by lazy {
        runCatching {
            getSystemService(AUDIO_SERVICE) as? AudioManager
        }.getOrNull()
    }

    override val titleText get() = getString(R.string.sounds)

    override fun onContentReady() {
        DetailKit.body(this) { col -> render(col) }
    }

    private fun render(col: android.widget.LinearLayout) {
        val manager = audio

        // ---- RINGER AND ALERTS ----
        col.addView(DetailKit.sectionLabel(this, getString(R.string.ringer_alerts)))
        val (rCard, rList) = DetailKit.listCard(this)
        rList.addView(sliderGroup(manager, AudioManager.STREAM_RING))
        col.addView(rCard)

        // ---- Ringtone (real default; terminal row) ----
        val (tCard, tList) = DetailKit.listCard(this)
        val title = runCatching {
            val uri = RingtoneManager.getActualDefaultRingtoneUri(
                this, RingtoneManager.TYPE_RINGTONE
            )
            uri?.let { u -> Ringtone.getTitle(this@SoundsDetailActivity, u) }
        }.getOrNull() ?: getString(R.string.ringtone_none)
        tList.addView(DetailKit.valueRow(this, getString(R.string.ringtone), title, false))
        col.addView(tCard)

        // ---- MEDIA ----
        col.addView(DetailKit.sectionLabel(this, getString(R.string.media_volume)))
        val (mCard, mList) = DetailKit.listCard(this)
        mList.addView(sliderGroup(manager, AudioManager.STREAM_MUSIC))
        col.addView(mCard)

        // ---- HAPTICS (Settings.System; WRITE_SETTINGS appop guarded) ----
        col.addView(DetailKit.sectionLabel(this, getString(R.string.haptics)))
        val (hCard, hList) = DetailKit.listCard(this)
        val haptic = Settings.System.getInt(
            contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1
        ) == 1
        hList.addView(
            AppleuxListItem(this).apply {
                setTitle(getString(R.string.haptics))
                setDividerVisible(false)
                setAccessoryView(
                    AppleuxToggle(this@SoundsDetailActivity).apply {
                        setChecked(haptic, animate = false)
                        setOnCheckedChangeListener { on ->
                            runCatching {
                                Settings.System.putInt(
                                    contentResolver,
                                    Settings.System.HAPTIC_FEEDBACK_ENABLED,
                                    if (on) 1 else 0
                                )
                            }.onFailure {
                                // Platform refused (no WRITE_SETTINGS appop):
                                // snap back to the real value, never lie.
                                render(col)
                            }
                        }
                    }
                )
            }
        )
        col.addView(hCard)
    }

    /** Full-width iOS slider card bound to one audio stream. */
    private fun sliderGroup(manager: AudioManager?, stream: Int): LinearLayout {
        val pad = DetailKit.dp(this, 16)
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            if (manager == null) {
                addView(
                    TextView(this@SoundsDetailActivity).apply {
                        text = getString(R.string.bluetooth_unavailable)
                        textSize = 17f
                        setTextColor(getColor(R.color.secondary_label))
                    }
                )
            } else {
                val max = runCatching {
                    manager.getStreamMaxVolume(stream)
                }.getOrDefault(100)
                val current = runCatching {
                    manager.getStreamVolume(stream)
                }.getOrDefault(0)
                addView(
                    AppleuxSlider(this@SoundsDetailActivity).apply {
                        this.max = max
                        progress = current
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                        setOnSeekBarChangeListener(
                            object : SeekBar.OnSeekBarChangeListener {
                                override fun onProgressChanged(
                                    sb: SeekBar?, p: Int, fromUser: Boolean
                                ) {
                                    if (!fromUser) return
                                    runCatching {
                                        manager.setStreamVolume(stream, p, 0)
                                    }
                                }

                                override fun onStartTrackingTouch(sb: SeekBar?) = Unit
                                override fun onStopTrackingTouch(sb: SeekBar?) = Unit
                            }
                        )
                    }
                )
            }
        }
    }
}
