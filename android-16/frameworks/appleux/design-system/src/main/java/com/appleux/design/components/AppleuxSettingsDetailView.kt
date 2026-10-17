package com.appleux.design.components

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout

/**
 * Generic iOS settings-detail page (wifi-detail, bluetooth, battery,
 * privacy-security, general, ...): grouped card + live rows. Hosts supply
 * rows; platform reads stay with the host activity.
 */
class AppleuxSettingsDetailView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {
    data class Row(
        val title: String,
        val subtitle: String? = null,
        val checked: Boolean? = null,
        val onToggle: ((Boolean) -> Unit)? = null,
        val onClick: (() -> Unit)? = null
    )
    init { orientation = VERTICAL }
    fun submit(rows: List<Row>) {
        removeAllViews()
        val card = AppleuxCard(context).apply { applyLiquidGlassRim() }
        val col = LinearLayout(context).apply { orientation = VERTICAL }
        rows.forEachIndexed { i, r ->
            val item = AppleuxListItem(context).apply {
                setTitle(r.title); setSubtitle(r.subtitle)
                setDividerVisible(i < rows.size - 1)
                if (r.checked != null) {
                    val t = AppleuxToggle(context).apply {
                        setChecked(r.checked, animate = false)
                        setOnCheckedChangeListener { r.onToggle?.invoke(it) }
                    }
                    setAccessoryView(t)
                } else if (r.onClick != null) {
                    isClickable = true; setOnClickListener { r.onClick() }
                }
            }
            col.addView(item)
        }
        card.addView(col)
        addView(card)
    }
}
