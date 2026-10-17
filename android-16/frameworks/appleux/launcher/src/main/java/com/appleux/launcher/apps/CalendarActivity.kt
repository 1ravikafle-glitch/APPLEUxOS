package com.appleux.launcher.apps

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.CalendarContract
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.appleux.design.components.AppleuxText
import com.appleux.design.components.LayoutUtils
import com.appleux.design.tokens.ColorTokens
import com.appleux.design.tokens.SpacingTokens
import com.appleux.design.tokens.TypographyTokens
import java.util.Calendar

/**
 * iOS 27 Calendar UI for AppleUX.
 *
 * Large title navigation, day view with hour timeline, month view with
 * 7-column grid, and a floating add-event button. All spacing, typography,
 * and colour resolve from the shared design tokens.
 */
class CalendarActivity : AppCompatActivity() {

    private enum class ViewMode { DAY, MONTH }

    private var viewMode = ViewMode.DAY
    private lateinit var dayView: View
    private lateinit var monthView: View
    private lateinit var todayButton: AppleuxText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        setContentView(root)

        // Navigation bar with large title + Today button
        root.addView(buildNavBar())

        // View switcher (Day / Month)
        root.addView(buildViewSwitcher())

        // Day and Month views
        dayView = buildDayView()
        monthView = buildMonthView()
        root.addView(dayView)
        root.addView(monthView)

        // FAB for adding events
        root.addView(buildFab())

        // Apply chrome and show initial view
        AppleuxAppChrome.apply(this, root)
        updateViewVisibility()
    }

    // ---- Navigation bar ----------------------------------------------------

    private fun buildNavBar(): View {
        val density = resources.displayMetrics.density
        val bar = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // Large title "Calendar" — 34sp per HIG Large Title
        val title = AppleuxText(this).apply {
            text = "Calendar"
            applyTypography(TypographyTokens.largeTitle)
            setTextColor(ColorTokens.label)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.START or Gravity.BOTTOM
                val padH = (SpacingTokens.INSETS_XL * density).toInt()
                val padV = (SpacingTokens.INSETS_LG * density).toInt()
                setMargins(padH, padV, padH, padV)
            }
        }

        // Today button — top-right, systemBlue
        todayButton = AppleuxText(this).apply {
            text = "Today"
            applyTypography(TypographyTokens.bodyEmphasized)
            setTextColor(ColorTokens.systemBlue)
            isClickable = true
            isFocusable = true
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.END or Gravity.BOTTOM
                val padH = (SpacingTokens.INSETS_XL * density).toInt()
                val padV = (SpacingTokens.INSETS_LG * density).toInt()
                setMargins(padH, padV, padH, padV)
            }
            setOnClickListener { goToToday() }
        }

        bar.addView(title)
        bar.addView(todayButton)
        return bar
    }

    // ---- View switcher -----------------------------------------------------

    private fun buildViewSwitcher(): View {
        val density = resources.displayMetrics.density
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                val padH = (SpacingTokens.INSETS_XL * density).toInt()
                setMargins(padH, 0, padH, 0)
            }
        }

        dayLabelBtn = AppleuxText(this).apply {
            text = "Day"
            applyTypography(TypographyTokens.subheadlineEmphasized)
            setTextColor(if (viewMode == ViewMode.DAY) ColorTokens.systemBlue else ColorTokens.secondaryLabel)
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                gravity = Gravity.CENTER
                val padV = (SpacingTokens.INSETS_MD * density).toInt()
                setPadding(0, padV, 0, padV)
            }
            setOnClickListener {
                viewMode = ViewMode.DAY
                updateViewVisibility()
                refreshSwitcherColors(this@CalendarActivity.dayLabelBtn, monthLabelBtn)
            }
        }

        monthLabelBtn = AppleuxText(this).apply {
            text = "Month"
            applyTypography(TypographyTokens.subheadlineEmphasized)
            setTextColor(if (viewMode == ViewMode.MONTH) ColorTokens.systemBlue else ColorTokens.secondaryLabel)
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                gravity = Gravity.CENTER
                val padV = (SpacingTokens.INSETS_MD * density).toInt()
                setPadding(0, padV, 0, padV)
            }
            setOnClickListener {
                viewMode = ViewMode.MONTH
                updateViewVisibility()
                refreshSwitcherColors(dayLabelBtn, this@CalendarActivity.monthLabelBtn)
            }
        }

        container.addView(dayLabelBtn)
        container.addView(monthLabelBtn)
        return container
    }

    /**
     * Day/Month segment labels. These are FIELDS, not locals: each label's
     * click listener calls refreshSwitcherColors with BOTH labels, so the two
     * lambdas reference each other. As locals that is a forward reference and
     * Kotlin reports `unresolved reference 'monthLabel'`.
     */
    private var dayLabelBtn: AppleuxText? = null
    private var monthLabelBtn: AppleuxText? = null

    private fun refreshSwitcherColors(dayLabel: AppleuxText?, monthLabel: AppleuxText?) {
        dayLabel?.setTextColor(if (viewMode == ViewMode.DAY) ColorTokens.systemBlue else ColorTokens.secondaryLabel)
        monthLabel?.setTextColor(if (viewMode == ViewMode.MONTH) ColorTokens.systemBlue else ColorTokens.secondaryLabel)
    }

    // ---- Day view ----------------------------------------------------------

    private fun buildDayView(): View {
        val density = resources.displayMetrics.density
        val hourHeightPx = (SpacingTokens.INSETS_XXXL6 * density).toInt() // 44dp per hour

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0, 1f
            )
            isFillViewport = true
        }

        val timeline = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val cal = Calendar.getInstance()
        val hourLabelWidth = (56 * density).toInt()

        for (hour in 0..23) {
            val hourRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    hourHeightPx
                )
            }

            // Hour label
            val hourLabel = AppleuxText(this).apply {
                text = formatHour(hour)
                applyTypography(TypographyTokens.footnote)
                setTextColor(ColorTokens.secondaryLabel)
                gravity = Gravity.END
                layoutParams = LinearLayout.LayoutParams(hourLabelWidth, ViewGroup.LayoutParams.MATCH_PARENT).apply {
                    val padR = (SpacingTokens.INSETS_MD * density).toInt()
                    setPadding(0, 0, padR, 0)
                }
            }

            // Hour cell (events go here)
            val hourCell = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(ColorTokens.systemBackground)
                }
            }

            // Separator line
            val separator = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    (1 * density).toInt()
                ).apply { topMargin = 0 }
                setBackgroundColor(ColorTokens.separator)
            }

            hourRow.addView(hourLabel)
            hourRow.addView(hourCell)
            timeline.addView(hourRow)
            timeline.addView(separator)
        }

        scroll.addView(timeline)
        return scroll
    }

    private fun formatHour(hour: Int): String {
        if (hour == 0) return "12 AM"
        if (hour < 12) return "$hour AM"
        if (hour == 12) return "12 PM"
        return "${hour - 12} PM"
    }

    // ---- Month view --------------------------------------------------------

    private fun buildMonthView(): View {
        val density = resources.displayMetrics.density
        val dayCellHeight = (SpacingTokens.INSETS_XXXL5 * density).toInt() // 40dp per day cell

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0, 1f
            )
            isFillViewport = true
        }

        val grid = GridLayout(this).apply {
            columnCount = 7
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // Day-of-week headers
        for (dow in listOf("S", "M", "T", "W", "T", "F", "S")) {
            grid.addView(AppleuxText(this).apply {
                text = dow
                applyTypography(TypographyTokens.footnoteEmphasized)
                setTextColor(ColorTokens.secondaryLabel)
                gravity = Gravity.CENTER
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0
                    height = ViewGroup.LayoutParams.WRAP_CONTENT
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    val padV = (SpacingTokens.INSETS_SM * density).toInt()
                    setPadding(0, padV, 0, padV)
                }
            })
        }

        // Day cells
        val cal = Calendar.getInstance()
        val today = cal.get(Calendar.DAY_OF_MONTH)
        val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val firstDayOfWeek = (cal.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
        val leadingBlanks = (firstDayOfWeek.get(Calendar.DAY_OF_WEEK) - 1) % 7

        // Leading blanks
        repeat(leadingBlanks) {
            grid.addView(View(this).apply {
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0
                    height = dayCellHeight
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                }
            })
        }

        // Day numbers
        for (day in 1..maxDay) {
            val isToday = day == today
            val cell = AppleuxText(this).apply {
                text = "$day"
                applyTypography(TypographyTokens.body)
                gravity = Gravity.CENTER
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0
                    height = dayCellHeight
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                }
                if (isToday) {
                    setTextColor(ColorTokens.white)
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(ColorTokens.systemBlue)
                    }
                } else {
                    setTextColor(ColorTokens.label)
                }
                isClickable = true
                isFocusable = true
                setOnClickListener { onDayTapped(day) }
            }
            grid.addView(cell)
        }

        scroll.addView(grid)
        return scroll
    }

    // ---- FAB ---------------------------------------------------------------

    private fun buildFab(): View {
        val density = resources.displayMetrics.density
        val fabSize = (SpacingTokens.INSETS_XXXL9 * density).toInt() // 56dp circle

        val container = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val fab = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(fabSize, fabSize).apply {
                gravity = Gravity.END or Gravity.BOTTOM
                val margin = (SpacingTokens.INSETS_XL * density).toInt()
                setMargins(margin, margin, margin, margin)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(ColorTokens.systemBlue)
            }
            isClickable = true
            isFocusable = true
            contentDescription = "Add Event"
            setOnClickListener { addEvent() }

            // Plus icon
            addView(AppleuxText(this@CalendarActivity).apply {
                text = "+"
                applyTypography(TypographyTokens.title2)
                setTextColor(ColorTokens.white)
                gravity = Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            })
        }

        container.addView(fab)
        return container
    }

    // ---- Actions -----------------------------------------------------------

    private fun updateViewVisibility() {
        dayView.visibility = if (viewMode == ViewMode.DAY) View.VISIBLE else View.GONE
        monthView.visibility = if (viewMode == ViewMode.MONTH) View.VISIBLE else View.GONE
    }

    private fun goToToday() {
        val cal = Calendar.getInstance()
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH)
        val day = cal.get(Calendar.DAY_OF_MONTH)
        // Scroll to current hour in day view, or highlight today in month view
        if (viewMode == ViewMode.DAY) {
            dayView.post {
                val density = resources.displayMetrics.density
                val hourHeightPx = (SpacingTokens.INSETS_XXXL6 * density).toInt()
                val scrollView = dayView as ScrollView
                val targetHour = cal.get(Calendar.HOUR_OF_DAY)
                scrollView.smoothScrollTo(0, targetHour * hourHeightPx)
            }
        }
    }

    private fun onDayTapped(day: Int) {
        viewMode = ViewMode.DAY
        updateViewVisibility()
        goToToday()
    }

    private fun addEvent() {
        val cal = Calendar.getInstance()
        startActivity(Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, cal.timeInMillis)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, cal.timeInMillis + 3600000)
        })
    }
}
