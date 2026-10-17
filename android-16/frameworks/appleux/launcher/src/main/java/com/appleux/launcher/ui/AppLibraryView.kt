package com.appleux.launcher.ui

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.appleux.design.components.AppleuxDisplayInsets
import com.appleux.design.components.AppleuxText
import com.appleux.design.tokens.ColorTokens
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

/**
 * iOS App Library: search pill on top, 2-column category folders
 * (1 large + 3 small minis + label), tap opens the full category grid.
 */
class AppLibraryView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : FrameLayout(context, attrs, defStyle) {

    private val recyclerView: RecyclerView
    private val adapter = FolderAdapter()
    private var cached: List<AppIconItem> = emptyList()
    private var onAppClick: ((AppIconItem) -> Unit)? = null
    private var query = ""

    init {
        val col = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }
        addView(col)
        AppleuxDisplayInsets.applyTopInset(context, col)
        // iOS 27: same wallpaper background as home, with blur
        val wallpaperView = android.widget.ImageView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
            setImageResource(
                if (com.appleux.design.theme.AppleuxTheme.isDarkMode) com.appleux.launcher.R.drawable.appleux_wallpaper_dark
                else com.appleux.launcher.R.drawable.appleux_wallpaper_light
            )
        }
        addView(wallpaperView, 0)
        // Blur overlay for the wallpaper
        val blurOverlay = View(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            setBackgroundColor(0x40000000)
        }
        addView(blurOverlay, 1)
        val search = EditText(context).apply {
            hint = "Q  App Library"
            isSingleLine = true
            gravity = Gravity.CENTER
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = 48f * resources.displayMetrics.density
                setColor((0x59 shl 24) or 0x000000)
            }
            val d = resources.displayMetrics.density
            setPadding((24 * d).toInt(), (10 * d).toInt(), (24 * d).toInt(), (10 * d).toInt())
            setTextColor(ColorTokens.label)
            setHintTextColor(ColorTokens.secondaryLabel)
        }
        val d = resources.displayMetrics.density
        col.addView(search, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins((20 * d).toInt(), (8 * d).toInt(), (20 * d).toInt(), (8 * d).toInt())
        })
        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(s: android.text.Editable?) {
                query = s?.toString().orEmpty()
                refresh()
            }
        })
        recyclerView = RecyclerView(context).apply {
            layoutManager = GridLayoutManager(context, LauncherTokens.APP_LIBRARY_COLUMNS)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        recyclerView.adapter = adapter
        col.addView(recyclerView)
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
                // Raw artwork here; AppIconView.bind resolves the iOS pack
                // (AppleuxIconPack) per package and rasterizes everything to
                // the squircle, so there is exactly one icon pipeline.
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
        refresh()
        return apps
    }

    fun cachedApps(): List<AppIconItem> = cached

    fun setOnAppClickListener(listener: (AppIconItem) -> Unit) {
        onAppClick = listener
    }

    fun setRenderScale(scale: Float) = Unit
    fun setQualityLevel(level: AppleuxQualityLevel) = Unit

    private fun categoryOf(pkg: String): String = when {
        pkg.contains("camera") || pkg.contains("gallery") || pkg.contains("photo") -> "Creativity"
        pkg.contains("message") || pkg.contains("sms") || pkg.contains("mail") ||
            pkg.contains("dialer") || pkg.contains("phone") -> "Social"
        pkg.contains("clock") || pkg.contains("calendar") || pkg.contains("calc") ||
            pkg.contains("note") -> "Productivity"
        pkg.contains("music") || pkg.contains("video") || pkg.contains("game") -> "Entertainment"
        else -> "Utilities"
    }

    /** iOS 27 App Library categories in order. */
    private val ios27Categories = listOf(
        "Suggestions", "Recently Added", "Social", "Utilities", "Creativity", "Productivity"
    )

    fun categories(): Map<String, List<AppIconItem>> = cached.groupBy { categoryOf(it.packageName) }

    fun filter(q: String) {
        query = q
        refresh()
    }

    private fun refresh() {
        val q = query.trim().lowercase()
        val groups = categories()
        val folders = if (q.isEmpty()) {
            // iOS 27: Suggestions (first 4 apps), Recently Added (last 4),
            // then category folders in fixed order
            val suggestions = cached.take(4)
            val recentlyAdded = cached.takeLast(4)
            val categoryFolders = ios27Categories.mapNotNull { cat ->
                groups[cat]?.let { Folder(cat, it) }
            }
            buildList {
                if (suggestions.isNotEmpty()) add(Folder("Suggestions", suggestions))
                if (recentlyAdded.isNotEmpty()) add(Folder("Recently Added", recentlyAdded))
                addAll(categoryFolders)
            }
        } else {
            val hits = cached.filter { it.label.lowercase().contains(q) }
            if (hits.isEmpty()) emptyList()
            else listOf(Folder("Results", hits))
        }
        adapter.submit(folders,
            { folder -> openFolder(folder) },
            { app -> onAppClick?.invoke(app) })
    }

    private fun openFolder(folder: Folder) {
        val dlg = android.app.AlertDialog.Builder(context)
            .setTitle(folder.name)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        dlg.setView(wrapFolderGrid(folder))
        dlg.show()
    }

    private fun wrapFolderGrid(folder: Folder): View {
        val gl = GridLayout(context).apply { columnCount = 4 }
        val d = resources.displayMetrics.density
        for (app in folder.apps) {
            val v = AppIconView(context)
            v.bind(app, 1f, AppleuxQualityLevel.QUALITY_100,
                com.appleux.launcher.LauncherTokens.HOME_ICON_SIZE_DP, true)
            v.onItemClick = onAppClick
            gl.addView(v, ViewGroup.LayoutParams((80 * d).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        val scroll = android.widget.ScrollView(context)
        scroll.addView(gl)
        return scroll
    }

    data class Folder(val name: String, val apps: List<AppIconItem>)

    private inner class FolderAdapter : RecyclerView.Adapter<FolderHolder>() {
        private var folders: List<Folder> = emptyList()
        private var onFolder: ((Folder) -> Unit)? = null
        private var onApp: ((AppIconItem) -> Unit)? = null
        fun submit(f: List<Folder>, of: (Folder) -> Unit, oa: (AppIconItem) -> Unit) {
            folders = f; onFolder = of; onApp = oa
            notifyDataSetChanged()
        }
        override fun onCreateViewHolder(p: ViewGroup, t: Int): FolderHolder {
            val v = AppleuxLibraryFolder(p.context)
            v.layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            return FolderHolder(v)
        }
        override fun onBindViewHolder(h: FolderHolder, pos: Int) {
            val f = folders[pos]
            (h.itemView as AppleuxLibraryFolder).bind(f.name, f.apps) { app -> onApp?.invoke(app) }
            (h.itemView as AppleuxLibraryFolder).onOpen = { onFolder?.invoke(f) }
        }
        override fun getItemCount(): Int = folders.size
    }

    private class FolderHolder(v: View) : RecyclerView.ViewHolder(v)
}
