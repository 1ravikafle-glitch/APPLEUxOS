package com.appleux.design.components

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import com.appleux.design.tokens.ColorTokens
import com.appleux.design.tokens.SpacingTokens

/**
 * iOS 27 Today View — widget overlay shown when swiping right from the first home page.
 *
 * Widget list from IPSW DefaultIconState.plist (today section):
 * - Calendar UpNext (small)
 * - Weather (small)
 * - News (newsLargeTall)
 * - Maps (medium)
 * - Tips (small)
 * - Batteries (small)
 * - Stocks (medium)
 * - Screen Time (large)
 *
 * Layout: 4-column grid with variable widget sizes.
 */
class AppleuxTodayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val grid: GridLayout
    private val header: AppleuxText

    init {
        // Background: wallpaper + blur (same as home screen)
        //
        // This used to be `setBackgroundColor(ColorTokens.systemBackground)`,
        // which contradicted the comment directly above it and made the whole
        // Today page an opaque slab: iOS 27 shows translucent cards over the
        // live wallpaper, so an opaque fill here is visible as a hard rectangle
        // covering the wallpaper behind the page.
        //
        // The fix is a glass surface rather than a solid one: a translucent scrim
        // from the Liquid Glass kit plus the platform blur, which is what the
        // launcher's own cards use. Blur is applied to the scrim only, not to a
        // full-screen view, because the 2 GB floor says at most one blurred
        // surface at a time and the cards below are what must read as glass.
        // scrimAlphaForLevel returns the kit's fill alpha; compose it onto the
        // system background colour rather than inventing a new colour token, so
        // the Today page follows light/dark exactly as everything else does.
        val scrim = androidx.core.graphics.ColorUtils.setAlphaComponent(
            ColorTokens.systemBackground,
            AppleuxGlass.scrimAlphaForLevel(AppleuxGlass.MAX_LEVEL),
        )
        setBackgroundColor(scrim)
        AppleuxGlass.applyBlur(this, AppleuxGlass.MAX_LEVEL)

        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }

        // Header: "Today" title + date
        header = AppleuxText(context).apply {
            text = "Today"
            textSize = 34f
            setTextStyle(AppleuxText.TextStyle.BOLD)
            setTextColor(ColorTokens.label)
            gravity = Gravity.START
            setPadding(
                LayoutUtils.dpToPx(SpacingTokens.INSETS_LG, context),
                LayoutUtils.dpToPx(SpacingTokens.INSETS_XL + SpacingTokens.INSETS_MD, context),
                LayoutUtils.dpToPx(SpacingTokens.INSETS_LG, context),
                LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, context)
            )
        }
        column.addView(header)

        // Widget grid (4 columns)
        grid = GridLayout(context).apply {
            columnCount = 4
            setPadding(
                LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, context),
                0,
                LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, context),
                LayoutUtils.dpToPx(SpacingTokens.INSETS_MD, context)
            )
        }

        // Add widgets in iOS 27 order (from IPSW)
        addWidget("Calendar", CalendarWidgetView(context), 2, 2)  // small
        addWidget("Weather", WeatherWidgetView(context), 2, 2)      // small
        addWidget("News", NewsWidgetView(context), 2, 4)            // newsLargeTall
        addWidget("Maps", MapsWidgetView(context), 4, 2)            // medium
        addWidget("Tips", TipsWidgetView(context), 2, 2)            // small
        addWidget("Batteries", BatteriesWidgetView(context), 2, 2)  // small
        addWidget("Stocks", StocksWidgetView(context), 4, 2)        // medium
        addWidget("Screen Time", ScreenTimeWidgetView(context), 4, 4) // large

        val scroll = android.widget.ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            clipToPadding = false
        }
        scroll.addView(grid)
        column.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        addView(column)
        AppleuxDisplayInsets.applyTopInset(context, column)
    }

    private fun addWidget(name: String, view: android.view.View, spanW: Int, spanH: Int) {
        val params = GridLayout.LayoutParams().apply {
            rowSpec = GridLayout.spec(GridLayout.UNDEFINED, spanH)
            columnSpec = GridLayout.spec(GridLayout.UNDEFINED, spanW)
            width = 0
            height = 0
            setMargins(8, 8, 8, 8)
        }
        view.layoutParams = params
        grid.addView(view)
    }
}

// Placeholder widget views — each renders the iOS 27 widget style
class CalendarWidgetView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : AppleuxCard(context, attrs) {
    init {
        addView(AppleuxText(context).apply {
            text = "Calendar"
            textSize = 17f
            setTextStyle(AppleuxText.TextStyle.SEMIBOLD)
            setTextColor(ColorTokens.label)
        })
        addView(AppleuxText(context).apply {
            text = "No Events"
            textSize = 15f
            setTextColor(ColorTokens.secondaryLabel)
        })
    }
}

class WeatherWidgetView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : AppleuxCard(context, attrs) {
    init {
        addView(AppleuxText(context).apply {
            text = "Weather"
            textSize = 17f
            setTextStyle(AppleuxText.TextStyle.SEMIBOLD)
            setTextColor(ColorTokens.label)
        })
        addView(AppleuxText(context).apply {
            text = "Sunny 72°"
            textSize = 15f
            setTextColor(ColorTokens.secondaryLabel)
        })
    }
}

class NewsWidgetView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : AppleuxCard(context, attrs) {
    init {
        addView(AppleuxText(context).apply {
            text = "News"
            textSize = 17f
            setTextStyle(AppleuxText.TextStyle.SEMIBOLD)
            setTextColor(ColorTokens.label)
        })
        addView(AppleuxText(context).apply {
            text = "Top Stories"
            textSize = 15f
            setTextColor(ColorTokens.secondaryLabel)
        })
    }
}

class MapsWidgetView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : AppleuxCard(context, attrs) {
    init {
        addView(AppleuxText(context).apply {
            text = "Maps"
            textSize = 17f
            setTextStyle(AppleuxText.TextStyle.SEMIBOLD)
            setTextColor(ColorTokens.label)
        })
        addView(AppleuxText(context).apply {
            text = "Nearby"
            textSize = 15f
            setTextColor(ColorTokens.secondaryLabel)
        })
    }
}

class TipsWidgetView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : AppleuxCard(context, attrs) {
    init {
        addView(AppleuxText(context).apply {
            text = "Tips"
            textSize = 17f
            setTextStyle(AppleuxText.TextStyle.SEMIBOLD)
            setTextColor(ColorTokens.label)
        })
        addView(AppleuxText(context).apply {
            text = "New Features"
            textSize = 15f
            setTextColor(ColorTokens.secondaryLabel)
        })
    }
}

class BatteriesWidgetView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : AppleuxCard(context, attrs) {
    init {
        addView(AppleuxText(context).apply {
            text = "Batteries"
            textSize = 17f
            setTextStyle(AppleuxText.TextStyle.SEMIBOLD)
            setTextColor(ColorTokens.label)
        })
        addView(AppleuxText(context).apply {
            text = "iPhone 85%"
            textSize = 15f
            setTextColor(ColorTokens.secondaryLabel)
        })
    }
}

class StocksWidgetView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : AppleuxCard(context, attrs) {
    init {
        addView(AppleuxText(context).apply {
            text = "Stocks"
            textSize = 17f
            setTextStyle(AppleuxText.TextStyle.SEMIBOLD)
            setTextColor(ColorTokens.label)
        })
        addView(AppleuxText(context).apply {
            text = "AAPL +1.2%"
            textSize = 15f
            setTextColor(ColorTokens.secondaryLabel)
        })
    }
}

class ScreenTimeWidgetView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : AppleuxCard(context, attrs) {
    init {
        addView(AppleuxText(context).apply {
            text = "Screen Time"
            textSize = 17f
            setTextStyle(AppleuxText.TextStyle.SEMIBOLD)
            setTextColor(ColorTokens.label)
        })
        addView(AppleuxText(context).apply {
            text = "3h 45m Today"
            textSize = 15f
            setTextColor(ColorTokens.secondaryLabel)
        })
    }
}
