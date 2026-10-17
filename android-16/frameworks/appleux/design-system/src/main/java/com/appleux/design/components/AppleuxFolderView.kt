package com.appleux.design.components

import android.content.Context
import android.util.AttributeSet
import android.widget.GridLayout
import com.appleux.design.tokens.ColorTokens

/** iOS folder popup: 3x3 mini-icon grid in a glass card. */
class AppleuxFolderView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : AppleuxCard(context, attrs, defStyleAttr) {
    private val grid = GridLayout(context).apply { columnCount = 3 }
    private val title = AppleuxText(context).apply { textSize = 15f; setTextColor(ColorTokens.label); gravity = android.view.Gravity.CENTER }
    init {
        applyLiquidGlassRim()
        val col = android.widget.LinearLayout(context).apply { orientation = android.widget.LinearLayout.VERTICAL }
        col.addView(title); col.addView(grid)
        addView(col)
    }
    private var titleLongClick: (() -> Unit)? = null
    fun setOnTitleLongClickListener(listener: (() -> Unit)?) {
        titleLongClick = listener
    }
    fun bind(name: String, apps: List<String>, onOpen: (String) -> Unit) {
        title.isLongClickable = true
        title.setOnLongClickListener { titleLongClick?.invoke(); true }
        title.text = name
        grid.removeAllViews()
        for (a in apps.take(9)) {
            val v = AppleuxText(context).apply {
                text = a.take(1).uppercase(); textSize = 18f
                setTextColor(ColorTokens.label); gravity = android.view.Gravity.CENTER
                setPadding(18, 18, 18, 18); isClickable = true
                setOnClickListener { onOpen(a) }
            }
            grid.addView(v)
        }
    }
}
