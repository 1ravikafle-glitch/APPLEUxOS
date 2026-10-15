package com.appleux.launcher.ui

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.appleux.design.tokens.SizingTokens
import com.appleux.design.tokens.SpacingTokens
import com.appleux.launcher.R
import com.appleux.performance.quality.AppleuxQualityLevel

class DockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : FrameLayout(context, attrs, defStyle) {

    private val recyclerView: RecyclerView
    private val adapter: DockAdapter

    init {
        LayoutInflater.from(context).inflate(R.layout.dock_view, this, true)
        recyclerView = findViewById(R.id.dockRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
        // No setHasFixedSize: the bar is WRAP_CONTENT and grows with the item
        // count, so claiming a fixed size would contradict syncGlassSize()
        // re-measuring the glass background after every layout pass.
        adapter = DockAdapter(context)
        recyclerView.adapter = adapter
        // The glass bar has no intrinsic size: mirror the icon strip after
        // layout (and on every content change) so it hugs the icons instead
        // of inflating the whole dock to fullscreen.
        recyclerView.post { syncGlassSize() }
    }

    /** Background-only glass bar; icons in the RecyclerView stay sharp. */
    val glassBackground: View = findViewById(R.id.dockGlassBg)

    fun syncGlassSize() {
        val w = recyclerView.measuredWidth
        val h = recyclerView.measuredHeight
        if (w <= 0 || h <= 0) return
        val lp = glassBackground.layoutParams as? FrameLayout.LayoutParams
            ?: FrameLayout.LayoutParams(w, h)
        if (lp.width != w || lp.height != h) {
            lp.width = w
            lp.height = h
            lp.gravity = Gravity.CENTER
            glassBackground.layoutParams = lp
        }
    }

    fun submitApps(apps: List<AppIconItem>) {
        adapter.updateApps(apps)
        recyclerView.post { syncGlassSize() }
    }

    fun setOnAppClickListener(listener: (AppIconItem) -> Unit) {
        adapter.onItemClick = listener
    }

    fun setRenderScale(scale: Float) {
        adapter.setRenderScale(scale)
    }

    fun setQualityLevel(level: AppleuxQualityLevel) {
        adapter.setQualityLevel(level)
    }
}

class DockAdapter(private val context: Context) : RecyclerView.Adapter<DockViewHolder>() {
    private val dockApps = mutableListOf<AppIconItem>()
    var onItemClick: ((AppIconItem) -> Unit)? = null
    private var renderScale: Float = 1f
    private var qualityLevel: AppleuxQualityLevel = AppleuxQualityLevel.QUALITY_100

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DockViewHolder {
        val view = AppIconView(context)
        val density = context.resources.displayMetrics.density
        // Cell edge and icon edge are the same token, so every icon is centred
        // in its cell and the whole inter-icon gap comes from marginEnd. They
        // used to disagree (72dp cell, 60dp icon), which silently produced
        // asymmetric 6dp padding on the cell edges instead of an even rhythm.
        val lp = RecyclerView.LayoutParams(
            (SizingTokens.DOCK_ICON_SIZE * density).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        lp.marginEnd = (SpacingTokens.INSETS_LG * density).toInt()
        view.layoutParams = lp
        return DockViewHolder(view)
    }

    override fun onBindViewHolder(holder: DockViewHolder, position: Int) {
        val item = dockApps[position]
        holder.bind(item, renderScale, qualityLevel, onItemClick)
    }

    override fun getItemCount(): Int = dockApps.size

    fun updateApps(apps: List<AppIconItem>) {
        dockApps.clear()
        dockApps.addAll(apps)
        notifyDataSetChanged()
    }

    fun setRenderScale(scale: Float) {
        renderScale = scale
        notifyDataSetChanged()
    }

    fun setQualityLevel(level: AppleuxQualityLevel) {
        qualityLevel = level
        notifyDataSetChanged()
    }
}

class DockViewHolder(view: AppIconView) : RecyclerView.ViewHolder(view) {
    fun bind(
        item: AppIconItem,
        scale: Float,
        quality: AppleuxQualityLevel,
        onClick: ((AppIconItem) -> Unit)?
    ) {
        val v = itemView as AppIconView
        v.onItemClick = onClick
        v.bind(item, scale, quality, SizingTokens.DOCK_ICON_SIZE)
    }
}
