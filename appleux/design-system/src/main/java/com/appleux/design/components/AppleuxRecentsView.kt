package com.appleux.design.components

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.appleux.design.tokens.ColorTokens

/** iOS app switcher: horizontal card carousel, tap to open, X to dismiss. */
class AppleuxRecentsView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    data class Task(val label: String, val packageName: String, val tint: Int = 0, val taskId: Int = -1)
    private val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }

    var onOpen: ((Task) -> Unit)? = null
    var onDismiss: ((Task) -> Unit)? = null
    var onCloseAll: (() -> Unit)? = null
    init {
        // iOS carousel: ~2 cards visible, horizontal scroll for the rest.
        // A bare LinearLayout squeezed all 12 cards into unreadable strips.
        val scroller = android.widget.HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            clipToPadding = false
        }
        scroller.addView(row, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        addView(scroller, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        addView(AppleuxHomeIndicator(context), LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        // Clear-all entry. iOS dismisses per card, but the explicit action
        // was requested: it sits above the carousel, never in the way.
        addView(AppleuxText(context).apply {
            text = "Clear All"; textSize = 13f
            setTextColor(ColorTokens.secondaryLabel); gravity = Gravity.CENTER
            isClickable = true; setOnClickListener { onCloseAll?.invoke() }
        }, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
            topMargin = (56 * resources.displayMetrics.density).toInt()
        })
    }
    fun submit(tasks: List<Task>) {
        row.removeAllViews()
        for (t in tasks.take(12)) {
            val card = AppleuxCard(context).apply {
                applyLiquidGlassRim()
                if (t.tint != 0) setBorder(2f, t.tint)
            }
            val col = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(24, 24, 24, 24) }
            val icon = android.widget.ImageView(context).apply {
                runCatching {
                    setImageDrawable(context.packageManager.getApplicationIcon(t.packageName))
                }
                layoutParams = LinearLayout.LayoutParams(96, 96).apply { gravity = Gravity.CENTER }
            }
            val title = AppleuxText(context).apply { text = t.label; textSize = 14f; setTextColor(ColorTokens.label); gravity = Gravity.CENTER; maxLines = 1 }
            val close = AppleuxText(context).apply { text = "✕"; textSize = 14f; setTextColor(ColorTokens.secondaryLabel); gravity = Gravity.CENTER; isClickable = true; setOnClickListener { onDismiss?.invoke(t) } }
            col.addView(close); col.addView(icon); col.addView(title)
            card.addView(col)
            card.isClickable = true
            card.setOnClickListener { onOpen?.invoke(t) }
            // Fixed iOS card width (~half the frame): weight would shrink
            // every card as the list grows.
            val d = context.resources.displayMetrics.density
            row.addView(card, LinearLayout.LayoutParams((200 * d).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = (12 * d).toInt()
            })
        }
    }
}
