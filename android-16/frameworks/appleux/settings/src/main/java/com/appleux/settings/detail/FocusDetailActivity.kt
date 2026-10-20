package com.appleux.settings.detail

import android.app.NotificationManager
import android.widget.TextView
import com.appleux.design.components.AppleuxListItem
import com.appleux.design.components.AppleuxToggle
import com.appleux.settings.R

/**
 * Owned Focus page. Do Not Disturb through the real interruption
 * filter. Without notification-policy access the toggle is shown in
 * its true disabled state with an explanation — no stock handoff, no
 * fake control.
 */
class FocusDetailActivity : SettingsDetailActivity() {

    private val nm: NotificationManager? by lazy {
        runCatching {
            getSystemService(NOTIFICATION_SERVICE) as? NotificationManager
        }.getOrNull()
    }

    override val titleText get() = getString(R.string.focus)

    override fun onContentReady() {
        DetailKit.body(this) { col ->
            col.addView(DetailKit.sectionLabel(this, getString(R.string.focus)))
            val (card, list) = DetailKit.listCard(this)
            val manager = nm
            if (manager == null) {
                list.addView(
                    AppleuxListItem(this).apply {
                        setTitle(getString(R.string.focus_dnd))
                        setSubtitle(getString(R.string.bluetooth_unavailable))
                        setDividerVisible(false)
                    }
                )
            } else {
                val granted = runCatching {
                    manager.isNotificationPolicyAccessGranted
                }.getOrDefault(false)
                val active = runCatching {
                    val f = manager.currentInterruptionFilter
                    f != NotificationManager.INTERRUPTION_FILTER_ALL &&
                        f != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
                }.getOrDefault(false)
                list.addView(
                    AppleuxListItem(this).apply {
                        setTitle(getString(R.string.focus_dnd))
                        setDividerVisible(false)
                        setAccessoryView(
                            AppleuxToggle(this@FocusDetailActivity).apply {
                                setChecked(active && granted, animate = false)
                                if (granted) {
                                    setOnCheckedChangeListener { on ->
                                        runCatching {
                                            manager.setInterruptionFilter(
                                                if (on) {
                                                    NotificationManager.INTERRUPTION_FILTER_PRIORITY
                                                } else {
                                                    NotificationManager.INTERRUPTION_FILTER_ALL
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        )
                    }
                )
                if (!granted) {
                    val pad = DetailKit.dp(this@FocusDetailActivity, 16)
                    list.addView(
                        TextView(this@FocusDetailActivity).apply {
                            text = getString(R.string.focus_policy_hint)
                            textSize = 13f
                            setTextColor(getColor(R.color.secondary_label))
                            setPadding(pad, 0, pad, pad)
                        }
                    )
                }
            }
            col.addView(card)
        }
    }
}
