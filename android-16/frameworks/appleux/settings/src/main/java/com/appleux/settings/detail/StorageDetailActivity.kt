package com.appleux.settings.detail

import android.os.Environment
import android.os.StatFs
import android.text.format.Formatter
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import com.appleux.design.components.AppleuxListItem
import com.appleux.settings.R

/**
 * Owned iPhone-Storage page. Real StatFs reads on the data partition,
 * a two-segment bar (used = system blue, free = tertiary fill) and the
 * live used/available/total values iOS shows. Per-app size breakdowns
 * need package usage stats grants and are omitted rather than faked.
 */
class StorageDetailActivity : SettingsDetailActivity() {

    override val titleText get() = getString(R.string.storage_title)

    override fun onContentReady() {
        DetailKit.body(this) { col ->
            val stat = StatFs(Environment.getDataDirectory().absolutePath)
            val total = stat.totalBytes
            val avail = stat.availableBytes
            val used = (total - avail).coerceAtLeast(0)

            // ---- Summary card with the storage bar ----
            val (barCard, barList) = DetailKit.listCard(this)
            val pad = DetailKit.dp(this, 16)
            barList.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(pad, pad, pad, pad)
                val barHeight = DetailKit.dp(this@StorageDetailActivity, 10)
                addView(LinearLayout(this@StorageDetailActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, barHeight
                    )
                    if (total > 0) {
                        addView(
                            android.view.View(this@StorageDetailActivity).apply {
                                layoutParams = LinearLayout.LayoutParams(
                                    0, ViewGroup.LayoutParams.MATCH_PARENT, used.toFloat()
                                ).apply { marginEnd = DetailKit.dp(this@StorageDetailActivity, 2) }
                                setBackgroundColor(
                                    getColor(R.color.system_blue)
                                )
                            }
                        )
                        addView(
                            android.view.View(this@StorageDetailActivity).apply {
                                layoutParams = LinearLayout.LayoutParams(
                                    0, ViewGroup.LayoutParams.MATCH_PARENT, avail.toFloat()
                                )
                                setBackgroundColor(
                                    getColor(R.color.tertiary_system_grouped_background)
                                )
                            }
                        )
                    }
                })
                addView(TextView(this@StorageDetailActivity).apply {
                    text = getString(
                        R.string.storage_summary,
                        Formatter.formatShortFileSize(this@StorageDetailActivity, used),
                        Formatter.formatShortFileSize(this@StorageDetailActivity, total)
                    )
                    textSize = 13f
                    setTextColor(getColor(R.color.secondary_label))
                    setPadding(0, DetailKit.dp(this@StorageDetailActivity, 8), 0, 0)
                })
            })
            col.addView(barCard)

            // ---- Values group ----
            val (card, list) = DetailKit.listCard(this)
            list.addView(
                DetailKit.valueRow(
                    this, getString(R.string.storage_used),
                    Formatter.formatShortFileSize(this, used), true
                )
            )
            list.addView(
                DetailKit.valueRow(
                    this, getString(R.string.storage_available),
                    Formatter.formatShortFileSize(this, avail), true
                )
            )
            list.addView(
                DetailKit.valueRow(
                    this, getString(R.string.storage_capacity),
                    Formatter.formatShortFileSize(this, total), false
                )
            )
            col.addView(card)
        }
    }
}
