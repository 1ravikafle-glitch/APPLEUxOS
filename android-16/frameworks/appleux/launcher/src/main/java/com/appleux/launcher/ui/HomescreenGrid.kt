package com.appleux.launcher.ui

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.appleux.design.components.AppleuxDisplayInsets
import com.appleux.design.tokens.SpacingTokens
import com.appleux.launcher.LauncherTokens
import com.appleux.performance.quality.AppleuxQualityLevel

class HomescreenGrid @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : RecyclerView(context, attrs, defStyle) {

    private val homescreenAdapter = HomescreenAdapter()
    private val gridLayoutManager = GridLayoutManager(context, LauncherTokens.GRID_COLUMNS)

    init {
        gridLayoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int =
                homescreenAdapter.getSpanSize(position)
        }
        layoutManager = gridLayoutManager
        setHasFixedSize(true)
        setItemViewCacheSize(ITEM_CACHE_SIZE)
        setAdapter(homescreenAdapter)
        // Transparent: the iOS-style wallpaper behind the grid must show through.
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        val density = context.resources.displayMetrics.density
        setPadding(
            (LauncherTokens.GRID_SIDE_MARGIN_DP * density).toInt(),
            0,
            (LauncherTokens.GRID_SIDE_MARGIN_DP * density).toInt(),
            (LauncherTokens.HOMESCREEN_BOTTOM_RESERVE_DP * density).toInt()
        )
        // True, deliberately. The bottom padding IS the dock clearance band, and
        // clipToPadding = false let rows render straight through it, so on any
        // frame shorter than the rock panel the last row drew underneath the
        // translucent dock glass and read as a double row. Callers size a page
        // to whole rows with rowsThatFit(), so nothing belongs in that band.
        clipToPadding = true
        // iOS home screens never rubber-band or glow at the edge: with the
        // widget-aware page fit in rowsThatFit() a page always holds its whole
        // content, so there is nothing to overscroll — kill the affordance too.
        overScrollMode = android.view.View.OVER_SCROLL_NEVER
        isVerticalScrollBarEnabled = false
    }

    /**
     * How many whole app rows fit between the notch and the dock on THIS frame.
     *
     * The grid used to assume [LauncherTokens.GRID_ROWS] and let the overflow
     * render into the dock clearance. That only holds on the panel the tokens
     * were derived for (1080x2408 @ 440 dpi = 392.73 x 875.64 dp). Cuttlefish is
     * 720x1280 @ 320 dpi = 360 x 640 dp, where 6 rows of
     * [LauncherTokens.naturalRowPitchDp] need ~618 dp and only ~320 dp is left
     * after the notch and the dock band — so rows 4-6 landed under the dock.
     *
     * A page now holds only rows that genuinely fit and the remainder moves to
     * the next page. That is what iOS does on a short device: it picks a row
     * count for the screen rather than shrinking icons to fit a fixed count.
     *
     * Falls back to the designed row count before the first layout pass, when
     * [getHeight] is still 0.
     */
    fun rowsThatFit(reservedTopPx: Int = 0): Int {
        val pitchPx = LauncherTokens.naturalRowPitchDp * resources.displayMetrics.density
        if (pitchPx <= 0f) return LauncherTokens.GRID_ROWS
        val bandPx = when {
            height > 0 -> (height - paddingTop - paddingBottom).toFloat()
            // Pre-layout: rebuild the same band from the display, using the same
            // single bottom reserve the grid pads with.
            else -> (resources.displayMetrics.heightPixels - paddingTop - paddingBottom).toFloat()
        }
        if (bandPx <= 0f) return LauncherTokens.GRID_ROWS
        // iOS home screens NEVER scroll vertically — a page must hold
        // everything it is handed. Widgets live in this same grid above the
        // app rows, so their block is subtracted from the band BEFORE rows are
        // counted ([LauncherActivity.widgetBlockPx] measures it). Without this
        // the widget rows pushed the app rows past the band and the grid
        // scrolled — Android behaviour iOS never has.
        val usablePx = bandPx - reservedTopPx
        if (usablePx <= 0f) return MIN_ROWS_PER_PAGE
        return (usablePx / pitchPx).toInt()
            .coerceIn(MIN_ROWS_PER_PAGE, LauncherTokens.GRID_ROWS)
    }

    /**
     * Reserves the top-centre notch.
     *
     * The launcher root already pads by the window's system-bar insets, but
     * that inset is a *window* property and the grid is what actually holds
     * the first row of icons. Reading the cutout through the shared display
     * profile means the top row is clear of the waterdrop on the POCO M5 even
     * if the window ever stops laying out behind the cutout, and it makes the
     * gap an explicit design value instead of an accident of the window flags.
     */
    fun applyNotchInset() {
        val top = AppleuxDisplayInsets.topContentPaddingPx(context)
        if (paddingTop == top) return
        setPadding(paddingLeft, top, paddingRight, paddingBottom)
        requestLayout()
    }

    fun submitApps(apps: List<AppIconItem>) {
        homescreenAdapter.updateApps(apps)
    }

    fun submitWidgets(cells: List<WidgetCell>) {
        homescreenAdapter.updateWidgets(cells)
    }

    fun setOnWidgetLongClickListener(listener: ((WidgetCell) -> Unit)?) {
        homescreenAdapter.onWidgetLongClick = listener
    }

    fun setOnAppLongClickListener(listener: ((AppIconItem) -> Unit)?) {
        homescreenAdapter.onAppLongClick = listener
    }

    /**
     * Resolve a root-level long-press to the cell under it and dispatch it there.
     *
     * The launcher's root [com.appleux.launcher.gestures.GestureHandler] owns
     * long-press detection (RecyclerView long-click is unreliable across
     * devices) but it cannot tell an app icon from a widget, so it hands the
     * press back here — the grid hit-tests its own laid-out children and runs
     * the behaviour registered for whichever one was hit.
     *
     * @param x,y in this grid's coordinate space, not the root's.
     * @return false when the press landed on empty background, so the caller
     *   can decide its own background behaviour.
     */
    fun dispatchLongPress(x: Float, y: Float): Boolean {
        val child = findChildViewUnder(x, y)
        val holder = child?.let { getChildViewHolder(it) }
        return when (holder) {
            is WidgetViewHolder -> {
                val cell = holder.cell ?: return false
                homescreenAdapter.onWidgetLongClick?.invoke(cell)
                true
            }
            is HomescreenViewHolder -> {
                val item = (holder.itemView as? AppIconView)?.boundItem ?: return false
                homescreenAdapter.onAppLongClick?.invoke(item)
                true
            }
            else -> false
        }
    }

    fun setOnAppClickListener(listener: (AppIconItem) -> Unit) {
        homescreenAdapter.onItemClick = listener
    }

    fun setRenderScale(scale: Float) {
        homescreenAdapter.setRenderScale(scale)
    }

    fun setQualityLevel(level: AppleuxQualityLevel) {
        homescreenAdapter.setQualityLevel(level)
    }

    companion object {
        private const val ITEM_CACHE_SIZE = 16

        /** Never fit a page below two rows; below that the icons stop reading. */
        private const val MIN_ROWS_PER_PAGE = 2
    }
}

/**
 * A pinned widget cell: the host view plus its grid span (S = 2 columns,
 * M = full width on the 4-column home grid). A small widget is 2 cells wide
 * and a medium one spans the full row; both share WIDGET_CELL_HEIGHT_DP.
 */
data class WidgetCell(
    val appWidgetId: Int,
    val providerClassName: String,
    val span: Int,
    val hostView: android.view.View
)

class HomescreenAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private var apps: MutableList<AppIconItem> = mutableListOf()
    private var widgets: List<WidgetCell> = emptyList()
    private var renderScale: Float = 1f
    private var qualityLevel: AppleuxQualityLevel = AppleuxQualityLevel.QUALITY_100
    var onItemClick: ((AppIconItem) -> Unit)? = null
    var onWidgetLongClick: ((WidgetCell) -> Unit)? = null
    var onAppLongClick: ((AppIconItem) -> Unit)? = null

    companion object {
        private const val TYPE_WIDGET = 0
        private const val TYPE_APP = 1
    }

    fun getSpanSize(position: Int): Int =
        if (position < widgets.size) widgets[position].span else 1

    override fun getItemViewType(position: Int): Int =
        if (position < widgets.size) TYPE_WIDGET else TYPE_APP

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        if (viewType == TYPE_WIDGET) {
            val frame = android.widget.FrameLayout(parent.context)
            val density = parent.context.resources.displayMetrics.density
            val cellMargin = (SpacingTokens.INSETS_MD * density).toInt()
            frame.layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (LauncherTokens.WIDGET_CELL_HEIGHT_DP * density).toInt()
            ).apply {
                topMargin = cellMargin
                bottomMargin = cellMargin
            }
            return WidgetViewHolder(frame)
        }
        val view = AppIconView(parent.context)
        val density = parent.context.resources.displayMetrics.density
        // GridLayoutManager assigns each item a span; MATCH_PARENT width makes the
        // cell fill that span, WRAP_CONTENT would shrink it to the icon.
        val lp = RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        val iconMargin = (SpacingTokens.INSETS_LG * density).toInt()
        lp.topMargin = iconMargin
        lp.bottomMargin = iconMargin
        view.layoutParams = lp
        return HomescreenViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is WidgetViewHolder) {
            holder.bind(widgets[position])
        } else {
            (holder as HomescreenViewHolder).bind(
                apps[position - widgets.size], renderScale, qualityLevel, onItemClick
            )
        }
    }

    override fun getItemCount(): Int = widgets.size + apps.size

    fun updateApps(newApps: List<AppIconItem>) {
        apps = newApps.toMutableList()
        notifyDataSetChanged()
    }

    fun moveApp(from: Int, to: Int) {
        val data = apps.toMutableList()
        val flatFrom = from - widgets.size; val flatTo = to - widgets.size
        if (flatFrom < 0 || flatTo < 0 || flatFrom >= data.size || flatTo >= data.size) return
        val item = data.removeAt(flatFrom)
        data.add(flatTo, item)
        apps = data
        notifyItemMoved(from, to)
    }

    fun updateWidgets(cells: List<WidgetCell>) {
        // Detach recycled host views before swapping lists so AppWidgetHost
        // never loses track of a live view.
        widgets = cells
        notifyDataSetChanged()
    }

    fun setRenderScale(scale: Float) {
        if (renderScale == scale) return
        renderScale = scale
        notifyDataSetChanged()
    }

    fun setQualityLevel(level: AppleuxQualityLevel) {
        if (qualityLevel == level) return
        qualityLevel = level
        notifyDataSetChanged()
    }
}

class HomescreenViewHolder(view: AppIconView) : RecyclerView.ViewHolder(view) {
    fun bind(
        item: AppIconItem,
        scale: Float,
        quality: AppleuxQualityLevel,
        onClick: ((AppIconItem) -> Unit)?
    ) {
        val v = itemView as AppIconView
        v.onItemClick = onClick
        v.bind(item, scale, quality)
        // Push the unread count so the iOS badge renders when non-zero.
        // Zero renders nothing — the honest state until SystemUI publishes.
        v.setBadgeCount(AppleuxBadgeStore.countFor(v.context, item.packageName))
    }
}

class WidgetViewHolder(frame: android.widget.FrameLayout) : RecyclerView.ViewHolder(frame) {

    /**
     * The widget this cell currently hosts, published so [HomescreenGrid]'s
     * hit-test can report WHICH widget a long-press landed on.
     */
    var cell: WidgetCell? = null
        private set

    fun bind(cell: WidgetCell) {
        this.cell = cell
        val container = itemView as android.widget.FrameLayout
        // Re-parent the live host view into this recycled cell.
        (cell.hostView.parent as? android.view.ViewGroup)?.removeView(cell.hostView)
        if (cell.hostView.parent == null) {
            container.addView(
                cell.hostView,
                android.widget.FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }
        // No per-view long-click listener: the launcher root already detects the
        // long-press and routes it back through dispatchLongPress(). A second
        // listener here would fire the action twice for the same press.
    }
}