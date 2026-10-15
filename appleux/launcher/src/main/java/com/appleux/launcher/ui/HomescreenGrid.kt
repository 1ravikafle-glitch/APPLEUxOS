package com.appleux.launcher.ui

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.appleux.design.tokens.SpacingTokens
import com.appleux.launcher.LauncherTokens
import com.appleux.performance.quality.AppleuxQualityLevel

class HomescreenGrid @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : RecyclerView(context, attrs, defStyle) {

    private val homescreenAdapter = HomescreenAdapter()

    init {
        val glm = GridLayoutManager(context, COLUMN_COUNT)
        glm.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int =
                homescreenAdapter.getSpanSize(position)
        }
        layoutManager = glm
        setHasFixedSize(true)
        setItemViewCacheSize(ITEM_CACHE_SIZE)
        setAdapter(homescreenAdapter)
        // Transparent: the iOS-style wallpaper behind the grid must show through.
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        // The last row must stop above the floating dock instead of sliding
        // underneath its glass bar (iOS keeps clear space above the dock).
        val density = context.resources.displayMetrics.density
        setPadding(0, 0, 0, (LauncherTokens.HOMESCREEN_DOCK_CLEARANCE_DP * density).toInt())
        clipToPadding = false
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
        private const val COLUMN_COUNT = 4
        private const val ITEM_CACHE_SIZE = 16
    }
}

/**
 * A pinned widget cell: the host view plus its grid span (S = 2 columns,
 * M = 4 columns, matching iOS small/medium proportions on a 4-column grid).
 */
data class WidgetCell(
    val appWidgetId: Int,
    val providerClassName: String,
    val span: Int,
    val hostView: android.view.View
)

class HomescreenAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private var apps: List<AppIconItem> = emptyList()
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
        apps = newApps
        notifyDataSetChanged()
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