package com.appleux.launcher.ui

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.appleux.design.tokens.SpacingTokens
import com.appleux.launcher.LauncherTokens
import com.appleux.launcher.R
import com.appleux.performance.quality.AppleuxQualityLevel

data class AppIconItem(
    val label: String,
    val icon: Drawable?,
    val intent: Intent,
    val packageName: String,
    val isSystemApp: Boolean = false
)

fun AppleuxQualityLevel.shouldSimplifyIcons(): Boolean = this.level <= 3

class AppLibraryView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : FrameLayout(context, attrs, defStyle) {

    private val recyclerView: RecyclerView
    private val adapter: AppLibraryAdapter
    private var cached: List<AppIconItem> = emptyList()

    init {
        LayoutInflater.from(context).inflate(R.layout.app_library_view, this, true)
        recyclerView = findViewById(R.id.recyclerView)
        // iOS App Library is a 3-column grid of icons, not a vertical list.
        recyclerView.layoutManager =
            GridLayoutManager(context, LauncherTokens.APP_LIBRARY_COLUMNS)
        adapter = AppLibraryAdapter()
        recyclerView.adapter = adapter
    }

    fun loadApps(): List<AppIconItem> {
        val pm = context.packageManager
        val apps = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }, 0).mapNotNull { info ->
            val ai = info.activityInfo ?: return@mapNotNull null
            val appInfo = pm.getApplicationInfo(ai.packageName, 0)
            AppIconItem(
                label = pm.getApplicationLabel(appInfo).toString(),
                icon = pm.getApplicationIcon(appInfo.packageName),
                intent = Intent(Intent.ACTION_MAIN).apply {
                    setClassName(ai.packageName, ai.name)
                    addCategory(Intent.CATEGORY_LAUNCHER)
                },
                packageName = ai.packageName,
                isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            )
        }.sortedBy { it.label.lowercase() }

        cached = apps
        adapter.updateApps(apps)
        return apps
    }

    /** Last result of [loadApps]; avoids re-querying PackageManager per tap. */
    fun cachedApps(): List<AppIconItem> = cached

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

class AppLibraryAdapter : RecyclerView.Adapter<AppLibraryViewHolder>() {
    private val apps = mutableListOf<AppIconItem>()
    var onItemClick: ((AppIconItem) -> Unit)? = null
    private var renderScale: Float = 1f
    private var qualityLevel: AppleuxQualityLevel = AppleuxQualityLevel.QUALITY_100

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppLibraryViewHolder {
        val view = AppIconView(parent.context)
        val density = parent.context.resources.displayMetrics.density
        // GridLayoutManager hands each item one column, so the cell must fill
        // it; a default wrap-content cell would shrink to the icon and pull the
        // whole column off-centre.
        val lp = RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        val verticalMargin = (SpacingTokens.INSETS_SM * density).toInt()
        lp.topMargin = verticalMargin
        lp.bottomMargin = verticalMargin
        view.layoutParams = lp
        return AppLibraryViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppLibraryViewHolder, position: Int) {
        val item = apps[position]
        holder.bind(item, renderScale, qualityLevel, onItemClick)
    }

    override fun getItemCount(): Int = apps.size

    fun updateApps(newApps: List<AppIconItem>) {
        apps.clear()
        apps.addAll(newApps)
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

class AppLibraryViewHolder(view: AppIconView) : RecyclerView.ViewHolder(view) {
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
