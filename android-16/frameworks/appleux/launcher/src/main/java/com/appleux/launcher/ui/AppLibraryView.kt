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
import com.appleux.design.components.AppleuxDialogs
import com.appleux.design.components.AppleuxDisplayInsets
import com.appleux.design.components.AppleuxText
import com.appleux.design.tokens.ColorTokens
import com.appleux.launcher.LauncherTokens
import com.appleux.launcher.R
import com.appleux.performance.quality.AppleuxQualityLevel

/**
 * One launchable app.
 *
 * ## Why `icon` is lazy, and why that matters on this device
 *
 * `icon` used to be a constructor parameter eagerly filled with
 * `pm.getApplicationIcon(appInfo.packageName)`. That call is a binder
 * round-trip into the other package's resource loader AND it materialises a
 * full-resolution Drawable — commonly a 192x192 (or larger) adaptive-icon
 * bitmap.
 *
 * It was paid for EVERY installed launcher activity at cold start, on the main
 * thread, inside `onCreate`, for the ~50 apps that are never on screen. On a
 * Helio G99 with a 3 GB budget that is the single largest avoidable cost in the
 * launcher, and it holds N decoded icon bitmaps alive for the process lifetime.
 *
 * It is now resolved on demand through [LauncherIconCache], keyed by package,
 * with a small LRU bound. Only what is actually drawn is ever loaded, and the
 * working set is capped.
 *
 * Note it is NOT in equals/hashCode/copy, which is correct: two AppIconItems for
 * the same package are the same app regardless of which icon has been resolved.
 */
data class AppIconItem(
    val label: String,
    val intent: Intent,
    val packageName: String,
    val isSystemApp: Boolean = false
) {
    /**
     * The app's own launcher icon, resolved on first use.
     *
     * Nullable because the vector in [AppleuxIconPack] or the squircle is used
     * whenever it exists; this is the fallback path for apps with neither.
     */
    val icon: Drawable? by lazy { LauncherIconCache.load(packageName) }
}

/**
 * Bounded, lazily-populated launcher-icon cache.
 *
 * ## Sizing
 *
 * 48 entries at 3 GB. The home grid shows at most
 * `LauncherTokens.GRID_COLUMNS * GRID_ROWS` icons plus 4 dock slots, and the
 * App Library shows a screen at a time, so anything past a couple of screens
 * worth is speculative. LruCache evicts least-recently-used on overflow, which
 * is the right policy here because paging is a sweep: the icons being dropped
 * are the ones furthest from where the user just was.
 *
 * The cache is static because it must outlive any single view, and because a
 * cold start should not have to rebuild it.
 */
private object LauncherIconCache {
    private const val MAX_ENTRIES = 48

    private val cache = object : android.util.LruCache<String, Drawable>(MAX_ENTRIES) {
        override fun entryRemoved(
            evicted: Boolean,
            key: String,
            old: Drawable?,
            new: Drawable?,
        ) {
            // Drawables can hold a Bitmap. Dropping the reference is enough for
            // GC; calling recycle() would be wrong here because a Drawable that
            // is still bound to a live ImageView shares the same Bitmap.
            super.entryRemoved(evicted, key, old, new)
        }
    }

    /** Not cached: a missing icon stays missing until the package changes. */
    private val misses = mutableSetOf<String>()

    @Synchronized
    fun load(packageName: String): Drawable? {
        cache.get(packageName)?.let { return it }
        if (packageName in misses) return null
        val d = runCatching {
            val ctx = appContext ?: return null
            ctx.packageManager.getApplicationIcon(packageName)
        }.getOrNull()
        if (d == null) misses += packageName else cache.put(packageName, d)
        return d
    }

    /** Set once by the launcher so a data-class lazy can reach a Context. */
    var appContext: Context? = null

    /** Called when the package set changes (install/uninstall). */
    fun invalidate() {
        cache.evictAll()
        misses.clear()
    }
}

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
        // Populated once so the per-app lazy in AppIconItem.icon has a Context.
        LauncherIconCache.appContext = context.applicationContext

        val apps = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }, 0).mapNotNull { info ->
            val ai = info.activityInfo ?: return@mapNotNull null
            // NOT pm.getApplicationInfo(ai.packageName, 0): ActivityInfo already
            // carries its own ApplicationInfo, fully populated, because
            // queryIntentActivities filled it. Calling getApplicationInfo per
            // app was one extra binder round-trip per installed app on the main
            // thread at cold start, to obtain a value already in hand.
            val appInfo = ai.applicationInfo
            AppIconItem(
                // Still a binder call per app (the label lives in the other
                // package's resources), but it is a small String rather than a
                // decoded icon, and sorting by label needs it eagerly.
                label = pm.getApplicationLabel(appInfo).toString(),
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
        AppleuxDialogs.custom(
            context,
            content = wrapFolderGrid(folder),
            title = folder.name,
        )
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
