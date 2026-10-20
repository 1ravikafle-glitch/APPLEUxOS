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
        // iOS App Library sits on a HEAVILY blurred wallpaper — this was a flat
        // dark scrim over a sharp image, which read as "dimmed", not "blurred".
        // RenderEffect is GPU/RenderThread and only costs anything while the
        // view is visible (the library is GONE when closed).
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            wallpaperView.setRenderEffect(
                android.graphics.RenderEffect.createBlurEffect(
                    26f, 26f, android.graphics.Shader.TileMode.CLAMP
                )
            )
        }
        // Lighter dim: the blur itself already softens; iOS keeps it bright.
        val blurOverlay = View(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            setBackgroundColor(0x2E000000)
        }
        addView(blurOverlay, 1)
        val d = resources.displayMetrics.density
        // iOS 27 App Library search (applibrary-light-p1): a LIGHT frosted
        // capsule carrying a magnifier + centered "App Library" placeholder.
        // The old fill was a near-black 0x59000000 with a literal "Q" hint —
        // an ASCII magnifier on a dark hole in the wallpaper.
        val searchPill = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = 44f * d
                // Same Liquid-Glass fill as the home search pill so the two
                // search surfaces track light/dark and the transparency slider.
                setColor(androidx.core.graphics.ColorUtils.setAlphaComponent(
                    ColorTokens.systemBackground,
                    com.appleux.design.components.AppleuxGlass.scrimAlphaForLevel(
                        com.appleux.design.components.AppleuxGlass.MAX_LEVEL
                    )
                ))
            }
            setPadding((18 * d).toInt(), (12 * d).toInt(), (18 * d).toInt(), (12 * d).toInt())
        }
        searchPill.addView(android.widget.ImageView(context).apply {
            setImageResource(com.appleux.design.R.drawable.il_ic_search)
            setColorFilter(ColorTokens.secondaryLabel, android.graphics.PorterDuff.Mode.SRC_IN)
            layoutParams = LinearLayout.LayoutParams((15 * d).toInt(), (15 * d).toInt())
        })
        val search = EditText(context).apply {
            hint = "App Library"
            isSingleLine = true
            background = null
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(ColorTokens.label)
            setHintTextColor(ColorTokens.secondaryLabel)
            textSize = 15f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                leftMargin = (8 * d).toInt()
                width = (140 * d).toInt()
            }
        }
        searchPill.addView(search)
        col.addView(searchPill, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins((16 * d).toInt(), (8 * d).toInt(), (16 * d).toInt(), (8 * d).toInt())
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
            // iOS: folders breathe — 16dp page gutters, 14dp between the two
            // columns, 22dp between rows (the old grid had zero spacing, which
            // is why tiles crashed into their labels and each other).
            val gut = (16 * d).toInt()
            setPadding(gut, (6 * d).toInt(), gut, (40 * d).toInt())
            clipToPadding = false
            addItemDecoration(object : RecyclerView.ItemDecoration() {
                override fun getItemOffsets(
                    outRect: android.graphics.Rect,
                    view: View,
                    parent: RecyclerView,
                    state: RecyclerView.State
                ) {
                    val hx = (7 * d).toInt()
                    val vy = (11 * d).toInt()
                    outRect.set(hx, vy, hx, vy)
                }
            })
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
        pkg.contains("camera") || pkg.contains("gallery") || pkg.contains("photo") ||
            pkg.contains("image") || pkg.contains("draw") -> "Creativity"
        pkg.contains("message") || pkg.contains("sms") || pkg.contains("mail") ||
            pkg.contains("dialer") || pkg.contains("phone") || pkg.contains("chat") -> "Social"
        pkg.contains("clock") || pkg.contains("calendar") || pkg.contains("calc") ||
            pkg.contains("note") || pkg.contains("wallet") || pkg.contains("bank") ||
            pkg.contains("doc") -> "Productivity & Finance"
        pkg.contains("music") || pkg.contains("video") || pkg.contains("player") ||
            pkg.contains("game") || pkg.contains("tv") -> "Entertainment"
        pkg.contains("health") || pkg.contains("fit") || pkg.contains("medit") ||
            pkg.contains("doctor") -> "Health & Fitness"
        pkg.contains("travel") || pkg.contains("flight") || pkg.contains("hotel") ||
            pkg.contains("taxi") || pkg.contains("maps") || pkg.contains("navi") -> "Travel"
        pkg.contains("news") || pkg.contains("reader") || pkg.contains("book") ||
            pkg.contains("browser") || pkg.contains("weather") || pkg.contains("wiki") ->
            "Information & Reading"
        else -> "Utilities"
    }

    /**
     * iOS 27 App Library categories, in the order the reference photo shows
     * them row by row: Suggestions | Recently Added, Social | Utilities,
     * Creativity | Productivity & Finance, Entertainment | Information &
     * Reading, Health & Fitness | Travel.
     */
    private val ios27Categories = listOf(
        "Suggestions", "Recently Added", "Social", "Utilities", "Creativity",
        "Productivity & Finance", "Entertainment", "Information & Reading",
        "Health & Fitness", "Travel"
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

    /**
     * iOS App Library folder open: a full-screen page — large white category
     * title over the blurred library, the category's whole grid with labels and
     * badges, tap-outside (or back) closes. The old path was
     * AppleuxDialogs.custom: a small-titled centered modal, i.e. an Android
     * alert wearing our colors, not the iOS folder zoom.
     */
    private fun openFolder(folder: Folder) {
        val d = resources.displayMetrics.density
        val dialog = android.app.Dialog(context, android.R.style.Theme_Translucent_NoTitleBar)
        dialog.window?.setBackgroundDrawable(
            android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
        )
        dialog.window?.setDimAmount(0f) // our own scrim, so the blur stays visible
        val root = FrameLayout(context).apply {
            background = android.graphics.drawable.ColorDrawable(0x59000000)
            isClickable = true
            setOnClickListener { dialog.dismiss() }
        }
        val col = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                (20 * d).toInt(),
                AppleuxDisplayInsets.topContentPaddingPx(context) + (20 * d).toInt(),
                (20 * d).toInt(),
                0
            )
        }
        col.addView(AppleuxText(context).apply {
            text = folder.name
            textSize = 26f
            setTextColor(android.graphics.Color.WHITE)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setShadowLayer(8f, 0f, 2f, 0x99000000.toInt())
            setPadding(0, 0, 0, (18 * d).toInt())
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        val gap = (16 * d).toInt()
        val cellW = (resources.displayMetrics.widthPixels - (40 * d).toInt() - gap * 3) / 4
        val grid = GridLayout(context).apply { columnCount = 4 }
        for (app in folder.apps) {
            val icon = AppIconView(context)
            icon.onItemClick = { launched ->
                dialog.dismiss()
                onAppClick?.invoke(launched)
            }
            icon.bind(app, 1f, AppleuxQualityLevel.QUALITY_100,
                com.appleux.launcher.LauncherTokens.HOME_ICON_SIZE_DP, true)
            icon.setBadgeCount(AppleuxBadgeStore.countFor(context, app.packageName))
            // GridLayout has NO (width, height) constructor — only specs and
            // the copy ctors — so size the default LayoutParams fields instead.
            grid.addView(icon, android.widget.GridLayout.LayoutParams().apply {
                width = cellW
                height = android.widget.GridLayout.LayoutParams.WRAP_CONTENT
                rightMargin = gap
                bottomMargin = gap
            })
        }
        val scroll = android.widget.ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            isFillViewport = true
            addView(grid)
        }
        col.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        root.addView(col, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        dialog.setContentView(root, android.view.ViewGroup.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT
        ))
        dialog.show()
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
