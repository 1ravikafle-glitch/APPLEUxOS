package com.appleux.settings.detail

import android.app.Activity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.appleux.design.components.AppleuxCard
import com.appleux.settings.R

/**
 * Shared chrome for owned iOS 27 detail sub-pages. GT binding rule 10:
 * back-circle chevron (scaffold), hero card where GT shows one, grouped
 * cards with 44dp-class rows, section labels above cards, value text
 * right-aligned via accessory, blue/destructive rows.
 */
object DetailKit {

    fun dp(activity: Activity, v: Float): Int =
        (activity.resources.displayMetrics.density * v).toInt()

    /** Int-literal convenience — same conversion, no call-site noise. */
    fun dp(activity: Activity, v: Int): Int = dp(activity, v.toFloat())

    /** iOS section label: 13sp secondary label, 16dp side insets. */
    fun sectionLabel(activity: Activity, text: CharSequence): TextView =
        TextView(activity).apply {
            this.text = text
            textSize = 13f
            setTextColor(activity.getColor(R.color.secondary_label))
            val pad = dp(activity, 16)
            setPadding(pad, dp(activity, 16), pad, dp(activity, 7))
        }

    /** Card carrying a vertical row list — the standard GT group. */
    fun listCard(activity: Activity): Pair<AppleuxCard, LinearLayout> {
        val list = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
        }
        val card = AppleuxCard(activity).apply {
            applyLiquidGlassRim()
            addView(list)
        }
        return card to list
    }

    /**
     * Terminal value row (iOS About-class): label left, live value
     * right-aligned gray. No chevron — nothing to navigate to.
     */
    fun valueRow(
        activity: Activity,
        title: CharSequence,
        value: CharSequence,
        divider: Boolean
    ): com.appleux.design.components.AppleuxListItem =
        com.appleux.design.components.AppleuxListItem(activity).apply {
            setTitle(title)
            setAccessoryView(
                TextView(activity).apply {
                    text = value
                    textSize = 17f
                    setTextColor(activity.getColor(R.color.secondary_label))
                }
            )
            setDividerVisible(divider)
        }

    /** Scroll body: every owned detail page attaches the same way. */
    fun body(activity: SettingsDetailActivity, build: (LinearLayout) -> Unit) {
        val column = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
        }
        build(column)
        column.setPadding(0, dp(activity, 20), 0, dp(activity, 24))
        activity.content.addView(
            ScrollView(activity).apply { addView(column) },
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }
}
