package com.appleux.design.components

import android.content.Context
import android.net.Uri
import android.util.AttributeSet
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import com.appleux.design.tokens.ColorTokens

/**
 * iOS-style screenshot toolbar: thumbnail + Markup / Share / Delete.
 * Bitmap loading stays with the host (it owns the Uri grant); this is pure UI.
 */
class AppleuxScreenshotToolbar @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : AppleuxCard(context, attrs, defStyleAttr) {
    private val thumb = ImageView(context)
    var onMarkup: (() -> Unit)? = null
    var onShare: (() -> Unit)? = null
    var onDelete: (() -> Unit)? = null
    var onDismiss: (() -> Unit)? = null
    init {
        applyLiquidGlassRim()
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16, 16, 16, 16)
        }
        thumb.layoutParams = LinearLayout.LayoutParams(112, 200)
        thumb.scaleType = ImageView.ScaleType.CENTER_CROP
        row.addView(thumb)
        val col = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        for ((label, fn) in listOf(
            "Markup" to { onMarkup?.invoke() },
            "Share" to { onShare?.invoke() },
            "Delete" to { onDelete?.invoke() },
            "Done" to { onDismiss?.invoke() }
        )) {
            col.addView(AppleuxText(context).apply {
                text = label; textSize = 15f
                setTextColor(if (label == "Delete") ColorTokens.systemRed else ColorTokens.systemBlue)
                setPadding(24, 12, 24, 12); isClickable = true
                setOnClickListener { fn() }
            })
        }
        row.addView(col)
        addView(row)
    }
    fun bind(image: android.graphics.drawable.Drawable?) { thumb.setImageDrawable(image) }
}
