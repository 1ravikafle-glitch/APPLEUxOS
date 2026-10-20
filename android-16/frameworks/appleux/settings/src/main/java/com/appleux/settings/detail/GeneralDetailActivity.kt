package com.appleux.settings.detail

import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.appleux.design.components.AppleuxIconRegistry
import com.appleux.design.components.AppleuxListItem
import com.appleux.settings.R

/**
 * Owned General page — GT: settings-general-top-hero.
 *
 * Layout per ground truth: hero card (gear icon, "General", description),
 * then the grouped About / Storage rows. GT rows with no honest backend
 * yet (AirPlay, CarPlay, TV Provider, VPN, Date & Time...) are omitted
 * per the no-fake-rows law; they return as their real backends land.
 */
class GeneralDetailActivity : SettingsDetailActivity() {

    override val titleText get() = getString(R.string.general)

    override fun onContentReady() {
        DetailKit.body(this) { col ->
            // ---- Hero card (GT: gear + title + description) ----
            val (heroCard, heroList) = DetailKit.listCard(this)
            heroList.gravity = Gravity.CENTER_HORIZONTAL
            heroList.addView(ImageView(this).apply {
                AppleuxIconRegistry.getDrawableRes("system_settings")
                    ?.let(::setImageResource)
                val s = DetailKit.dp(this@GeneralDetailActivity, 56)
                layoutParams = LinearLayout.LayoutParams(s, s).apply {
                    topMargin = DetailKit.dp(this@GeneralDetailActivity, 22)
                }
            })
            heroList.addView(TextView(this).apply {
                text = getString(R.string.general)
                textSize = 21f
                gravity = Gravity.CENTER
                setTextColor(getColor(R.color.label))
                setPadding(0, DetailKit.dp(this@GeneralDetailActivity, 10), 0, 0)
            })
            heroList.addView(TextView(this).apply {
                text = getString(R.string.general_hero_summary)
                textSize = 13f
                gravity = Gravity.CENTER
                setTextColor(getColor(R.color.secondary_label))
                val side = DetailKit.dp(this@GeneralDetailActivity, 24)
                setPadding(
                    side,
                    DetailKit.dp(this@GeneralDetailActivity, 6),
                    side,
                    DetailKit.dp(this@GeneralDetailActivity, 22)
                )
            })
            col.addView(heroCard)

            // ---- About / Storage group (real destinations only) ----
            val (card, list) = DetailKit.listCard(this)
            list.addView(AppleuxListItem(this).apply {
                setTitle(getString(R.string.about))
                setDividerVisible(true)
                setOnClickListener {
                    runCatching {
                        startActivity(
                            android.content.Intent(
                                this@GeneralDetailActivity,
                                AboutDetailActivity::class.java
                            )
                        )
                    }
                }
            })
            list.addView(AppleuxListItem(this).apply {
                setTitle(getString(R.string.storage_title))
                setDividerVisible(false)
                setOnClickListener {
                    runCatching {
                        startActivity(
                            android.content.Intent(
                                this@GeneralDetailActivity,
                                StorageDetailActivity::class.java
                            )
                        )
                    }
                }
            })
            col.addView(card)
        }
    }
}
