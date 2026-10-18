package com.appleux.launcher

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.appleux.design.components.AppleuxDialogs
import com.appleux.design.components.AppleuxHaptics
import com.appleux.design.components.AppleuxSuperellipse
import com.appleux.design.components.AppleuxComponentsWiring
import com.appleux.design.runtime.AppleuxRuntimeConfig
import com.appleux.design.theme.AppleuxTheme
import com.appleux.launcher.gestures.GestureHandler
import com.appleux.launcher.gestures.GestureHostLayout
import com.appleux.launcher.ui.AppIconItem
import com.appleux.launcher.ui.AppLibraryView
import com.appleux.launcher.ui.DockPreferences
import com.appleux.launcher.ui.DockView
import com.appleux.launcher.ui.HomescreenGrid
import com.appleux.performance.controller.AdaptiveRenderPolicy
import com.appleux.performance.controller.AppleuxPerformanceController
import com.appleux.performance.controller.MonitoringLevel
import com.appleux.performance.quality.AppleuxQualityLevel
import com.appleux.design.components.AppleuxToast
import com.appleux.launcher.R

class LauncherActivity : AppCompatActivity(), AppleuxPerformanceController.PerformanceListener {

    private lateinit var homescreenGrid: HomescreenGrid
    private lateinit var dockView: DockView
    private lateinit var appLibraryView: AppLibraryView
    private lateinit var libraryScrim: View
    private lateinit var root: GestureHostLayout
    private lateinit var wallpaperView: ImageView

    /**
     * Dim layer between the wallpaper and the grid.
     *
     * PROVENANCE: prototype-derived — IOSLikeROM `launcher/HomeScreen.kt`
     * calls `GlassScene(wallpaper, dim = 0.12f)` on a home page and 0.30f on
     * the App Library page. iOS relies on this to keep white 12sp labels and
     * the translucent dock legible over an arbitrary wallpaper; we shipped
     * with no dim at all, which is why labels vanish on a light image.
     */
    private lateinit var wallpaperDim: View

    private lateinit var performanceController: AppleuxPerformanceController
    private lateinit var renderPolicy: AdaptiveRenderPolicy

    /** True while the adaptive performance controller is actually running. */
    private var performanceRunning = false

    // ---- Widget host (iOS-style widget pack, launcher-owned) ----
    private lateinit var appWidgetManager: android.appwidget.AppWidgetManager
    private lateinit var appWidgetHost: android.appwidget.AppWidgetHost
    private val hostViews = mutableMapOf<Int, android.view.View>()
    private var pendingBindWidgetId: Int = -1
    private var pendingBindProvider: android.content.ComponentName? = null
    private var batteryReceiver: android.content.BroadcastReceiver? = null

    private data class WidgetDef(
        val titleRes: Int,
        val providerClass: String,
        val span: Int
    )

    private val widgetDefs = listOf(
        WidgetDef(R.string.widget_clock, ".widgets.AppleuxClockWidgetProvider", LauncherTokens.WIDGET_SPAN_MEDIUM),
        WidgetDef(R.string.widget_battery, ".widgets.AppleuxBatteryWidgetProvider", LauncherTokens.WIDGET_SPAN_SMALL),
        WidgetDef(R.string.widget_calendar, ".widgets.AppleuxCalendarWidgetProvider", LauncherTokens.WIDGET_SPAN_MEDIUM)
    )

    private fun widgetPrefs() =
        getSharedPreferences("appleux_launcher_widgets", android.content.Context.MODE_PRIVATE)

    private fun loadWidgetSpecs(): MutableList<Pair<String, Int>> {
        val out = mutableListOf<Pair<String, Int>>()
        for (raw in widgetPrefs().getStringSet("widgets", emptySet()) ?: emptySet()) {
            val parts = raw.split("|")
            if (parts.size == 2) {
                val id = parts[1].toIntOrNull() ?: continue
                out.add(parts[0] to id)
            }
        }
        return out
    }

    private fun saveWidgetSpecs(specs: List<Pair<String, Int>>) {
        widgetPrefs().edit()
            .putStringSet("widgets", specs.map { "${it.first}|${it.second}" }.toSet())
            .apply()
    }

    private fun rebuildWidgetCells(): List<com.appleux.launcher.ui.WidgetCell> {
        val cells = mutableListOf<com.appleux.launcher.ui.WidgetCell>()
        val kept = mutableListOf<Pair<String, Int>>()
        for ((className, id) in loadWidgetSpecs()) {
            try {
                val info = appWidgetManager.getAppWidgetInfo(id) ?: continue
                // Reuse the live host view for an already-bound id: creating a
                // new view per rebuild leaks windows and duplicates content.
                val view = hostViews[id] ?: appWidgetHost.createView(this, id, info)
                    .also { hostViews[id] = it }
                val span = widgetDefs.firstOrNull { packageName + it.providerClass == className }
                    ?.span ?: LauncherTokens.WIDGET_SPAN_MEDIUM
                kept.add(className to id)
                cells.add(com.appleux.launcher.ui.WidgetCell(id, className, span, view))
            } catch (e: Exception) {
                hostViews.remove(id)
                try { appWidgetHost.deleteAppWidgetId(id) } catch (_: Exception) {}
            }
        }
        if (kept.size != loadWidgetSpecs().size) saveWidgetSpecs(kept)
        // Drop cached views for ids no longer bound.
        val keptIds = kept.map { it.second }.toSet()
        hostViews.keys.filterNot { it in keptIds }.forEach { stale ->
            hostViews.remove(stale)
        }
        // Providers only push RemoteViews on bind (updatePeriod=0 by design, no
        // polling). After a process restart the host views are fresh but empty,
        // so explicitly ask each bound provider to redraw.
        for ((className, id) in kept) {
            try {
                val intent = android.content.Intent(
                    android.appwidget.AppWidgetManager.ACTION_APPWIDGET_UPDATE
                ).apply {
                    component = android.content.ComponentName(packageName, className)
                    putExtra(
                        android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_IDS,
                        intArrayOf(id)
                    )
                }
                sendBroadcast(intent)
            } catch (e: Exception) {}
        }
        pushBatteryToWidgets()
        return cells
    }

    /**
     * Widget long-press target, and the app-icon context menu.
     *
     * Removing a widget used to hang off the widget's own long-click, which the
     * root long-press hijacked — so a long-press both deleted the widget
     * outright and opened the gallery. Removal now lives inside the gallery as
     * a listed action, which keeps a destructive tap one confirmation away.
     */
    private fun openWidgetGallery() {
        val placed = loadWidgetSpecs()
        if (placed.isEmpty()) {
            showWidgetPicker()
            return
        }
        AppleuxDialogs.choose(
            this,
            items = placed.map { widgetTitleFor(it.first) }.toTypedArray(),
            title = getString(R.string.widget_gallery_title),
            cancelLabel = getString(android.R.string.cancel),
        ) { index -> removeWidget(placed[index].second)
}
    }

    private fun showWidgetPicker() {
        val titles = widgetDefs.map { getString(it.titleRes) }.toTypedArray()
        AppleuxDialogs.choose(
            this,
            items = titles,
            title = getString(R.string.widget_gallery_title),
            cancelLabel = getString(android.R.string.cancel),
        ) { index -> requestBindWidget(widgetDefs[index]) }
    }

    private fun widgetTitleFor(providerClassName: String): String =
        widgetDefs.firstOrNull { packageName + it.providerClass == providerClassName }
            ?.let { getString(it.titleRes) }
            ?: providerClassName.substringAfterLast('.')

    private fun requestBindWidget(def: WidgetDef) {
        if (loadWidgetSpecs().size >= 3) {
            AppleuxToast.show(this, "Widget limit reached")
            return
        }
        val provider = android.content.ComponentName(packageName, packageName + def.providerClass)
        val id = appWidgetHost.allocateAppWidgetId()
        val allowed = try {
            appWidgetManager.bindAppWidgetIdIfAllowed(id, provider)
        } catch (e: Exception) {
            false
        }
        if (allowed) {
            onWidgetBound(def, id)
        } else {
            pendingBindWidgetId = id
            pendingBindProvider = provider
            val intent = android.content.Intent(
                android.appwidget.AppWidgetManager.ACTION_APPWIDGET_BIND
            ).apply {
                putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider)
            }
            try {
                @Suppress("DEPRECATION")
                startActivityForResult(intent, REQ_BIND_WIDGET)
            } catch (e: Exception) {
                appWidgetHost.deleteAppWidgetId(id)
            }
        }
    }

    private fun onWidgetBound(def: WidgetDef, id: Int) {
        val specs = loadWidgetSpecs()
        specs.add(packageName + def.providerClass to id)
        saveWidgetSpecs(specs)
        homescreenGrid.submitWidgets(rebuildWidgetCells())
        pushBatteryToWidgets()
    }

    private fun removeWidget(appWidgetId: Int) {
        val specs = loadWidgetSpecs().filterNot { it.second == appWidgetId }
        saveWidgetSpecs(specs)
        try { appWidgetHost.deleteAppWidgetId(appWidgetId) } catch (e: Exception) {}
        homescreenGrid.submitWidgets(rebuildWidgetCells())
    }

    private fun pushBatteryToWidgets() {
        for ((className, id) in loadWidgetSpecs()) {
            if (!className.endsWith("AppleuxBatteryWidgetProvider")) continue
            val pct = com.appleux.launcher.widgets.AppleuxBatteryWidgetProvider.readStickyLevel(this)
            if (pct >= 0) {
                try {
                    com.appleux.launcher.widgets.AppleuxBatteryWidgetProvider
                        .pushBatteryLevel(this, appWidgetManager, id, pct)
                } catch (e: Exception) {}
            }
        }
        pushIslandBattery()
    }

    private var homeIsland: com.appleux.design.components.AppleuxDynamicIsland? = null

    /** Island battery slot from the sticky broadcast (no permission, no polling). */
    private fun pushIslandBattery() {
        val island = homeIsland ?: return
        runCatching {
            val b = registerReceiver(null, android.content.IntentFilter(
                android.content.Intent.ACTION_BATTERY_CHANGED
            )) ?: return
            val lvl = b.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
            val scl = b.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1)
            val plugged = b.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, 0) != 0
            if (lvl < 0 || scl <= 0) return
            island.bindBattery(
                plugged, (lvl * 100 / scl),
                com.appleux.design.components.AppleuxDynamicIsland.chargeTimeText(this)
            )
        }
    }

    @Deprecated("Bind flow uses the system consent dialog")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_BIND_WIDGET) return
        val id = pendingBindWidgetId
        val provider = pendingBindProvider
        pendingBindWidgetId = -1
        pendingBindProvider = null
        if (resultCode == android.app.Activity.RESULT_OK && provider != null) {
            val def = widgetDefs.firstOrNull { packageName + it.providerClass == provider.className }
            if (def != null) onWidgetBound(def, id)
        } else if (id >= 0) {
            try { appWidgetHost.deleteAppWidgetId(id) } catch (e: Exception) {}
        }
    }

    companion object {
        private const val REQ_BIND_WIDGET = 0xAC1
        private const val APPWIDGET_HOST_ID = 0xAC13
    }

    private val libraryObserver = object : AdaptiveRenderPolicy.QualityObserver {
        override fun onRenderScaleChanged(scale: Float) = Unit
        override fun onQualityLevelChanged(level: AppleuxQualityLevel) = Unit
        override fun onMonitoringLevelChanged(level: MonitoringLevel) = Unit
    }

    private val onAppSelected = { item: AppIconItem -> launchApp(item) }

    private lateinit var dockPreferences: DockPreferences
    private var spotlight: com.appleux.design.components.AppleuxSpotlightView? = null

    /** Static settings index for Spotlight (contacts join once READ_CONTACTS works). */
    private fun settingsIndex(): List<Pair<String, () -> Unit>> = listOf(
        "Wi-Fi" to { openStock("android.settings.WIFI_SETTINGS") },
        "Bluetooth" to { openStock("android.settings.BLUETOOTH_SETTINGS") },
        "Battery" to { openStock("android.settings.BATTERY_SAVER_SETTINGS") },
        "Display" to { openStock("android.settings.DISPLAY_SETTINGS") },
        "Sound" to { openStock("android.settings.SOUND_SETTINGS") },
        "Privacy" to { openStock("android.settings.PRIVACY_SETTINGS") },
        "Accessibility" to { openStock("android.settings.ACCESSIBILITY_SETTINGS") },
        "Apps" to { openStock("android.settings.APPLICATION_SETTINGS") }
    )
    private fun openStock(action: String) {
        runCatching { startActivity(android.content.Intent(action)) }
    }
    private fun openSpotlight() {
        var sp = spotlight
        if (sp == null) {
            sp = com.appleux.design.components.AppleuxSpotlightView(this).apply {
                visibility = android.view.View.GONE
                onQuery = { query -> renderSpotlight(this, query) }
            }
            spotlight = sp
            root.addView(sp, android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT))
        }
        sp.visibility = android.view.View.VISIBLE
        renderSpotlight(sp, "")
    }
    private fun renderSpotlight(
        sp: com.appleux.design.components.AppleuxSpotlightView, query: String
    ) {
        val q = query.trim().lowercase()
        val rows = mutableListOf<android.view.View>()
        for (app in appLibraryView.cachedApps().filter {
            q.isEmpty() || it.label.lowercase().contains(q)
        }.take(6)) {
            rows.add(com.appleux.design.components.AppleuxListItem(this).apply {
                setTitle(app.label); setDividerVisible(false)
                setOnClickListener {
                    sp.visibility = android.view.View.GONE; launchApp(app)
                }
            })
        }
        for ((name, fn) in settingsIndex().filter {
            q.isEmpty() || it.first.lowercase().contains(q)
        }.take(4)) {
            rows.add(com.appleux.design.components.AppleuxListItem(this).apply {
                setTitle(name); setSubtitle("Settings"); setDividerVisible(false)
                setOnClickListener { sp.visibility = android.view.View.GONE; fn() }
            })
        }
        // iOS Spotlight always ends with a web-search row for the query.
        if (q.isNotEmpty()) {
            rows.add(com.appleux.design.components.AppleuxListItem(this).apply {
                setTitle("Search Web for \"$query\"")
                setSubtitle("Safari"); setDividerVisible(false)
                setOnClickListener {
                    sp.visibility = android.view.View.GONE
                    runCatching {
                        startActivity(
                            android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse("https://duckduckgo.com/?q=" +
                                    java.net.URLEncoder.encode(query, "UTF-8"))
                            )
                        )
                    }
                }
            })
        }
        // HIG search pattern: an empty result is a helpful state, not blank.
        if (rows.isEmpty()) {
            rows.add(com.appleux.design.components.AppleuxListItem(this).apply {
                setTitle("No Results"); setSubtitle("Try a different search"); setDividerVisible(false)
                isClickable = false; isFocusable = false
            })
        }
        sp.submitRows(rows)
    }
    private fun closeSpotlight(): Boolean {
        val sp = spotlight
        if (sp != null && sp.visibility == android.view.View.VISIBLE) {
            sp.visibility = android.view.View.GONE
            return true
        }
        return false
    }
    private var homePage = 0
    private var homeSearchPill: View? = null
    private var pageDots: com.appleux.design.components.AppleuxPageDots? = null
    private fun folderPrefs() = getSharedPreferences("appleux_launcher_folders", MODE_PRIVATE)
    private fun loadFolders(): MutableMap<String, String> {
        val m = mutableMapOf<String, String>()
        for (raw in folderPrefs().getStringSet("folders", emptySet()) ?: emptySet()) {
            val k = raw.substringBefore("|"); val v = raw.substringAfter("|", "")
            if (k.isNotEmpty()) m[k] = v
        }
        return m
    }
    private fun hiddenPkgs(): Set<String> =
        getSharedPreferences("appleux_launcher_hidden", MODE_PRIVATE).getStringSet("hidden", emptySet()) ?: emptySet()
    private fun setHidden(pkg: String, hidden: Boolean) {
        val cur = hiddenPkgs().toMutableSet()
        if (hidden) cur.add(pkg) else cur.remove(pkg)
        getSharedPreferences("appleux_launcher_hidden", MODE_PRIVATE).edit().putStringSet("hidden", cur).apply()
    }
    private fun gridOrder(): List<String> = prefs_order().getStringSet("order", emptySet())?.toList() ?: emptyList()
    private fun prefs_order() = getSharedPreferences("appleux_launcher_order", MODE_PRIVATE)
    private fun saveGridOrder(pkgs: List<String>) { prefs_order().edit().putStringSet("order", pkgs.toSet()).apply() }

    /**
     * Which persisted slot [item] currently sits in, matched by package name
     * rather than by adapter position: an empty slot is not rendered, so the
     * dock's rendered index and its stored index drift apart as soon as the
     * user removes something. -1 when the app is not in the dock at all.
     */
    private fun dockSlotOf(item: AppIconItem): Int =
        dockPreferences.resolve(appLibraryView.cachedApps())
            .indexOfFirst { it?.packageName == item.packageName }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        performanceController = AppleuxPerformanceController.getInstance(this)
        renderPolicy = AdaptiveRenderPolicy.getInstance()
        appWidgetManager = android.appwidget.AppWidgetManager.getInstance(this)
        appWidgetHost = android.appwidget.AppWidgetHost(this, APPWIDGET_HOST_ID)
        dockPreferences = DockPreferences(this)

        buildUi()
        loadApps()
        // iOS home opens with widgets: first run places our Clock widget.
        // Host + provider share this UID, so bindAppWidgetIdIfAllowed
        // succeeds silently; otherwise the system consent dialog appears once.
        if (loadWidgetSpecs().isEmpty()) {
            widgetDefs.firstOrNull()?.let { requestBindWidget(it) }
        }

        renderPolicy.registerObserver(libraryObserver)
        performanceController.registerListener(this)
        syncPerformanceWithConfig()

        // com.appleux.systemui was DELETED 2026-10-17 (deliverables/m23): the
        // iOS chrome now compiles into com.android.systemui, so there is no
        // overlay process to cold-start. The status bar, shade and Control
        // Center belong to SystemUI and come up with it - starting anything
        // here would have added a process back that we just removed.
        // See AppleuxSystemUi for how panel toggles work now.
    }

    override fun onStart() {
        super.onStart()
        try { appWidgetHost.startListening() } catch (e: Exception) {}
        homescreenGrid.submitWidgets(rebuildWidgetCells())
        val filter = android.content.IntentFilter(
            android.content.Intent.ACTION_BATTERY_CHANGED
        )
        batteryReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
                pushBatteryToWidgets()
            }
        }
        try {
            if (android.os.Build.VERSION.SDK_INT >= 34) {
                registerReceiver(
                    batteryReceiver, filter,
                    android.content.Context.RECEIVER_NOT_EXPORTED
                )
            } else {
                @Suppress("DEPRECATION")
                registerReceiver(batteryReceiver, filter)
            }
        } catch (e: Exception) {
            batteryReceiver = null
        }
    }

    override fun onStop() {
        try { batteryReceiver?.let { unregisterReceiver(it) } } catch (e: Exception) {}
        batteryReceiver = null
        try { appWidgetHost.stopListening() } catch (e: Exception) {}
        super.onStop()
    }

    private fun buildUi() {
        // platform night mode first, then the user's stored AppleUX choice
        AppleuxTheme.applyToActivity(this)
        applyDarkMode()
        // iOS chrome: the stock status + navigation bars are hidden inside
        // OUR window (no permission needed for our own window) and replaced
        // by our status bar + home indicator below. Transient swipe reveals.
        hideStockChrome()

        root = GestureHostLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(AppleuxTheme.background.resolved)
        }

        homescreenGrid = HomescreenGrid(this)
        wallpaperView = ImageView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
            // Set immediately (not only via applyDarkMode): a missed theme
            // pass used to leave the stock Pixel wallpaper bleeding through.
            setImageResource(
                if (AppleuxTheme.isDarkMode) R.drawable.appleux_wallpaper_dark
                else R.drawable.appleux_wallpaper_light
            )
        }
        // iOS status row drawn by us (white clock + icons over wallpaper).
        val iosStatus = com.appleux.design.components.AppleuxStatusBar(this).apply {
            // iPhone layout: live clock left (ticking by itself), cellular /
            // wifi / battery glyphs right. Without setIcons the row is
            // time-only, which is what shipped before.
            setIcons(
                listOf(
                    com.appleux.design.components.AppleuxStatusBar.StatusBarIcon(
                        com.appleux.design.R.drawable.sf_cellular, "cellular"
                    ),
                    com.appleux.design.components.AppleuxStatusBar.StatusBarIcon(
                        com.appleux.design.R.drawable.sf_wifi, "wifi"
                    ),
                    com.appleux.design.components.AppleuxStatusBar.StatusBarIcon(
                        com.appleux.design.R.drawable.il_ic_battery, "battery"
                    )
                )
            )
            applyOverlayTheme(true)
        }
        val homeIndicator = com.appleux.design.components.AppleuxHomeIndicator(this)
        wallpaperDim = View(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(android.graphics.Color.BLACK)
            alpha = LauncherTokens.HOMESCREEN_WALLPAPER_DIM
        }
        libraryScrim = View(this).apply {
            setBackgroundColor(AppleuxTheme.overlayPrimary)
            visibility = View.GONE
            isClickable = true
            setOnClickListener { closeLibrary() }
        }
        appLibraryView = AppLibraryView(this).apply { visibility = View.GONE }
        dockView = DockView(this)

        homescreenGrid.setOnAppClickListener(onAppSelected)
        dockView.setOnAppClickListener(onAppSelected)
        dockView.setOnAppLongClickListener { item ->
            showDockContextMenu(dockSlotOf(item), item)
        }
        appLibraryView.setOnAppClickListener(onAppSelected)

        root.addView(wallpaperView)
        root.addView(wallpaperDim)
        root.addView(
            iosStatus,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL
            )
        )
        root.addView(
            homeIndicator,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
            )
        )
        // Dynamic Island over the emulated notch (home surface). Battery-only
        // live state, pushed from the same sticky read as the widgets — the
        // view registers no receivers. Tap expands to the live pill.
        homeIsland = com.appleux.design.components.AppleuxDynamicIsland(this)
        root.addView(
            homeIsland,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL
            )
        )
        pushIslandBattery()
        // The grid must reserve the dock's clearance band, otherwise the last
        // icon row slides under the translucent dock glass and reads as a
        // double row. DOCK_CLEARANCE_DP is derived in LauncherTokens from the
        // measured panel (gesture bar 46.2 + gap 16 + dock bar 84 = 146 dp).
        root.addView(
            homescreenGrid,
            android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            ).apply {
                bottomMargin = (com.appleux.launcher.LauncherTokens
                    .HOMESCREEN_DOCK_CLEARANCE_DP * resources.displayMetrics.density).toInt()
            }
        )
        root.addView(libraryScrim)
        root.addView(appLibraryView)
        // The iOS 27 search pill: a translucent capsule carrying a magnifier and a
        // "Search" placeholder. This used to be a bare AppleuxText label, which
        // read as a caption rather than an affordance — the giveaway being that a
        // search field shows a magnifier and this did not.
        //
        // The fill now comes from the Liquid Glass kit rather than a hardcoded
        // 0x59000000, so the pill follows light/dark and the transparency slider
        // like every other glass surface instead of being a fixed grey.
        val d = resources.displayMetrics.density
        val searchPill = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            contentDescription = "Search"
            setOnClickListener { openSpotlight() }
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = 48f * d
                setColor(androidx.core.graphics.ColorUtils.setAlphaComponent(
                    com.appleux.design.tokens.ColorTokens.systemBackground,
                    com.appleux.design.components.AppleuxGlass
                        .scrimAlphaForLevel(
                            com.appleux.design.components.AppleuxGlass.MAX_LEVEL
                        ),
                ))
            }
            setPadding(
                (14 * d).toInt(), (13 * d).toInt(), (22 * d).toInt(), (13 * d).toInt()
            )
            addView(
                android.widget.ImageView(this@LauncherActivity).apply {
                    setImageResource(com.appleux.design.R.drawable.il_ic_search)
                    // Tint rather than recolour: the vendored vectors are black
                    // and the pill sits on a wallpaper of unknown lightness.
                    setColorFilter(
                        com.appleux.design.tokens.ColorTokens.secondaryLabel,
                        android.graphics.PorterDuff.Mode.SRC_IN,
                    )
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        (15 * d).toInt(), (15 * d).toInt()
                    )
                }
            )
            addView(
                com.appleux.design.components.AppleuxText(this@LauncherActivity).apply {
                    text = "Search"
                    textSize = 13f
                    setTextColor(com.appleux.design.tokens.ColorTokens.secondaryLabel)
                    // 8dp between glyph and label, per the pill's own metrics.
                    setPadding((8 * d).toInt(), 0, 0, 0)
                },
                android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                )
            )
        }
        root.addView(
            searchPill,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
            ).apply { bottomMargin = (196 * resources.displayMetrics.density).toInt() }
        )
        homeSearchPill = searchPill
        pageDots = com.appleux.design.components.AppleuxPageDots(this).apply { setPages(2, 0) }
        root.addView(
            pageDots,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL)
                .apply { bottomMargin = (100 * resources.displayMetrics.density).toInt() }
        )
        // Dock pins bottom-center. The LayoutParams inflated without a parent
        // are base ViewGroup.LayoutParams (no gravity field), so they must be
        // replaced here — never pass through dockView.layoutParams.
        root.addView(
            dockView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
            )
        )

        // Target SDK 35+ is edge-to-edge by default, so without this the first
        // icon row renders underneath the status bar.
        //
        // Top is deliberately NOT taken from the window insets here: the grid
        // and the App Library each reserve the cutout themselves through
        // AppleuxDisplayInsets (the real display-cutout inset, not a window
        // approximation), while the wallpaper stays full-bleed under the
        // notch the way iOS does it.
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(
                androidx.core.view.WindowInsetsCompat.Type.systemBars()
            )
            view.setPadding(bars.left, 0, bars.right, bars.bottom)
            homescreenGrid.applyNotchInset()
            insets
        }

        // swipe up anywhere opens the library; swipe down closes it.
        // Long-press detection is owned by the root (RecyclerView long-click is
        // unreliable across devices), but it does NOT own the decision: the
        // press is resolved against the homescreen grid, which routes it to an
        // app icon's context menu or to the widget gallery. Only empty
        // background falls through to the gallery here.
        GestureHandler(
            context = this,
            // iOS: swipe up means home. On home it is a no-op (already
            // there); with the library open it comes back home. The library
            // opens by swiping left past the last page, exactly like iOS.
            onSwipeUp = { if (isLibraryOpen()) closeLibrary() },
            onSwipeDown = { xFraction, downYFraction ->
                onSwipeDownAt(xFraction, downYFraction) },
            onLongPress = { x, y -> onLongPressAt(x, y) },
            onSwipeLeft = { yFrac ->
                // Bottom edge: iOS recent-app switching. Anywhere else:
                // next page (or the App Library past the last page).
                if (yFrac > 0.85f) cycleRecentApp(forward = true) else nextPage()
            },
            onSwipeRight = { yFrac ->
                if (yFrac > 0.85f) cycleRecentApp(forward = false)
                else if (homePage > 0) prevPage()
                else openTodayView()  // iOS 27: swipe right from first page = Today View
            },
            onSwipeUpHold = { sendSystemUi("com.appleux.systemui.SHOW_RECENTS") }
        ).attachToRoot(root)

        setContentView(root)
        // Apply once up front as well as from the insets listener: the cutout
        // is a property of the display, not of the window, so the grid must be
        // correct even if no window inset is ever dispatched.
        homescreenGrid.applyNotchInset()
    }

    /**
     * Reads every launchable app, resolves the persisted dock, and splits the
     * rest across the grid.
     *
     * Dock apps are EXCLUDED from the home grid. That is prototype-derived
     * (IOSLikeROM `launcher/HomeScreen.kt` builds each page from
     * `apps.filter { it.pkg !in dockPkgs }`) and matches iOS, where an app in
     * the dock does not also occupy a grid cell.
     */
    private var pageCount = 1
    private fun setHomePage(page: Int) {
        homePage = page.coerceIn(0, maxOf(0, pageCount - 1))
        pageDots?.setPages(maxOf(1, pageCount), homePage)
        // iOS shows no dots on a single page.
        pageDots?.visibility = if (pageCount > 1) View.VISIBLE else View.GONE
        loadApps(animate = true)
    }
    private fun nextPage() {
        // iOS: pages do not wrap. Swiping left past the last page opens the
        // App Library — that IS the iOS entry point (no tab bar on home).
        if (homePage < pageCount - 1) setHomePage(homePage + 1)
        else if (!isLibraryOpen()) openLibrary()
    }
    private fun prevPage() {
        // iOS: first page is the end of the line, no wrap-around.
        if (homePage > 0) setHomePage(homePage - 1)
    }

    private fun loadApps(animate: Boolean = false) {
        val all = appLibraryView.loadApps()
        val dock = dockPreferences.resolve(all)
        val dockPkgs = dock.filterNotNull().map { it.packageName }.toSet()
        val folders = loadFolders()
        val hidden = hiddenPkgs()
        var grid = all.filter { it.packageName !in dockPkgs && !folders.containsKey(it.packageName) && it.packageName !in hidden }
        val order = gridOrder()
        if (order.isNotEmpty()) {
            grid = grid.sortedBy { order.indexOf(it.packageName).let { i -> if (i < 0) Int.MAX_VALUE else i } }
        }
        val perPage = com.appleux.launcher.LauncherTokens.GRID_COLUMNS *
            com.appleux.launcher.LauncherTokens.GRID_ROWS
        pageCount = maxOf(1, (grid.size + perPage - 1) / perPage)
        homePage = homePage.coerceIn(0, pageCount - 1)
        val page = grid.drop(homePage * perPage).take(perPage)
        homescreenGrid.submitApps(page)
        pageDots?.setPages(maxOf(1, pageCount), homePage)
        pageDots?.visibility = if (pageCount > 1) View.VISIBLE else View.GONE
        enableGridDrag(grid)
        if (animate && !AppleuxRuntimeConfig.isReduceMotionEnabled) {
            homescreenGrid.animate().translationX(0f).setDuration(com.appleux.design.tokens.MotionTokens.DURATION_SLIDE)
                .setInterpolator(com.appleux.design.tokens.MotionTokens.easingIOSDefault).start()
        }
        homescreenGrid.submitWidgets(rebuildWidgetCells())
        homescreenGrid.setOnWidgetLongClickListener { openWidgetGallery() }
        // iOS: long-press enters EDIT MODE — the icons jiggle and can be
        // rearranged or removed. It does not open a context menu; that is
        // Android behaviour and it is what this replaced. The per-app context
        // menu is still reachable, from inside edit mode.
        homescreenGrid.setOnAppLongClickListener { enterEditMode() }
        dockView.submitApps(dock.filterNotNull())
    }

    /**
     * Long-press a dock icon to change what is in that slot.
     *
     * The dock is user-owned state from here on: it is written to
     * SharedPreferences by [DockPreferences] and is never recomputed.
     */
    private fun showDockContextMenu(slot: Int, current: AppIconItem?) {
        // A long-press can only originate from a rendered dock icon, so slot
        // is normally >= 0; guard anyway, because a negative index would throw
        // on assignment rather than degrade.
        if (slot < 0) return
        val all = appLibraryView.cachedApps()
        val options = mutableListOf<String>()
        val actions = mutableListOf<() -> Unit>()

        options.add(getString(R.string.dock_menu_replace))
        actions.add { showDockAppPicker(slot, all) }
        if (current != null) {
            options.add(getString(R.string.dock_menu_remove))
            actions.add { setDockSlot(slot, null) }
        }
        options.add(getString(R.string.dock_menu_reset))
        actions.add {
            dockPreferences.reset()
            loadApps()
        }

        AppleuxDialogs.choose(
            this,
            items = options.toTypedArray(),
            title = current?.label ?: getString(R.string.dock_menu_title),
            cancelLabel = getString(android.R.string.cancel),
        ) { index -> actions[index]() }
    }

    private fun showDockAppPicker(slot: Int, all: List<AppIconItem>) {
        val labels = all.map { it.label }.toTypedArray()
        AppleuxDialogs.choose(
            this,
            items = labels,
            title = getString(R.string.dock_picker_title),
            cancelLabel = getString(android.R.string.cancel),
        ) { index -> setDockSlot(slot, all[index]) }
    }

    private fun setDockSlot(slot: Int, app: AppIconItem?) {
        val dock = dockPreferences.resolve(appLibraryView.cachedApps()).toMutableList()
        while (dock.size <= slot) dock.add(null)
        dock[slot] = app
        dockPreferences.save(dock)
        loadApps()
    }

    /**
     * iOS "drag an app onto the dock". Uses the first EMPTY slot so a gap the
     * user just made by removing an app can be refilled with the app they were
     * thinking of, rather than silently overwriting slot 0.
     */
    private fun addToDock(item: AppIconItem) {
        val dock = dockPreferences.resolve(appLibraryView.cachedApps()).toMutableList()
        val existing = dock.indexOfFirst { it?.packageName == item.packageName }
        if (existing >= 0) {
            setDockSlot(existing, item)
            return
        }
        val empty = dock.indexOfFirst { it == null }
        setDockSlot(if (empty >= 0) empty else 0, item)
    }

    /**
     * Long-press dispatch. Coordinates arrive in root space; the grid hit-tests
     * in its own space, and the two differ because window insets pad the root
     * and therefore offset the grid inside it.
     *
     * Nothing is resolved against the grid while the library is open — it is
     * stacked above the grid and the icons underneath it are not what the user
     * pressed.
     */
    private fun onLongPressAt(x: Float, y: Float) {
        if (!isLibraryOpen()) {
            val handled = homescreenGrid.dispatchLongPress(
                x - homescreenGrid.left,
                y - homescreenGrid.top
            )
            if (handled) return
        }
        openWidgetGallery()
    }

    /** Two-choice remove badge: hide from Home (stays in Library) or delete. */
    private fun showRemoveDialog(item: AppIconItem) {
        val root = window.decorView as? android.view.ViewGroup ?: return
        val d = com.appleux.design.components.AppleuxComponentsWiring.confirmDialog(
            this, item.label, "Remove \"${item.label}\" from Home, or delete the app?",
            "Delete App", destructive = true
        ) { tryPrivAppUninstall(item) }
        d.addButton("Remove from Home",
            com.appleux.design.tokens.ColorTokens.systemBlue) {
            setHidden(item.packageName, true); loadApps()
        }
        root.addView(d, android.view.ViewGroup.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT))
        d.show()
    }

    private fun tryPrivAppUninstall(item: AppIconItem) {
        // Priv-app route first (DELETE_PACKAGES / REQUEST_DELETE_PACKAGES);
        // without the grant it throws and we fall back to ACTION_DELETE.
        val ok = runCatching {
            val sender = android.app.PendingIntent.getBroadcast(
                this, item.packageName.hashCode(),
                android.content.Intent("$packageName.UNINSTALL_STATUS"),
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_MUTABLE
            ).intentSender
            packageManager.packageInstaller.uninstall(item.packageName, sender)
            true
        }.getOrDefault(false)
        if (!ok) removeApp(item)
    }

    /** iOS app-icon context menu. */
    private fun showAppContextMenu(item: AppIconItem) {
        val labels = arrayOf(
            getString(R.string.app_menu_add_to_dock),
            "Add to Folder",
            getString(R.string.app_menu_info),
            getString(R.string.app_menu_remove),
            getString(R.string.app_menu_widgets)
        )
        AppleuxDialogs.choose(
            this,
            items = labels,
            title = item.label,
            cancelLabel = getString(android.R.string.cancel),
        ) { index -> when (index) {
                    0 -> addToDock(item)
                    1 -> addToFolder(item)
                    2 -> openAppInfo(item)
                    3 -> showRemoveDialog(item)
                    else -> openWidgetGallery()
                } }
    }

    private fun openAppInfo(item: AppIconItem) {
        runCatching {
            startActivity(
                android.content.Intent(
                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                ).apply {
                    data = android.net.Uri.fromParts("package", item.packageName, null)
                }
            )
        }.onFailure {
            AppleuxToast.show(this, getString(R.string.app_info_unavailable, item.label))
        }
    }

    /**
     * Removal goes through the system's uninstall confirmation; there is no
     * silent path and no launcher-side permission for it. System apps have no
     * uninstall activity, which surfaces as the same toast.
     */
    private fun removeApp(item: AppIconItem) {
        val packageUri = android.net.Uri.fromParts("package", item.packageName, null)
        runCatching {
            startActivity(
                android.content.Intent(android.content.Intent.ACTION_DELETE).apply {
                    data = packageUri
                }
            )
        }.recoverCatching {
            startActivity(
                android.content.Intent(
                    android.content.Intent.ACTION_UNINSTALL_PACKAGE,
                    packageUri
                )
            )
        }.onFailure {
            AppleuxToast.show(this, getString(R.string.app_remove_unavailable, item.label))
        }
    }

    private fun enableGridDrag(current: List<AppIconItem>) {
        runCatching {
            val cb = object : androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback(
                androidx.recyclerview.widget.ItemTouchHelper.UP or androidx.recyclerview.widget.ItemTouchHelper.DOWN or
                    androidx.recyclerview.widget.ItemTouchHelper.LEFT or androidx.recyclerview.widget.ItemTouchHelper.RIGHT, 0
            ) {
                override fun onMove(rv: androidx.recyclerview.widget.RecyclerView, vh: androidx.recyclerview.widget.RecyclerView.ViewHolder, t: androidx.recyclerview.widget.RecyclerView.ViewHolder): Boolean {
                    val from = vh.bindingAdapterPosition; val to = t.bindingAdapterPosition
                    if (from < 0 || to < 0) return false
                    (homescreenGrid.adapter as? com.appleux.launcher.ui.HomescreenAdapter)?.moveApp(from, to)
                    return true
                }
                override fun clearView(rv: androidx.recyclerview.widget.RecyclerView, vh: androidx.recyclerview.widget.RecyclerView.ViewHolder) {
                    super.clearView(rv, vh)
                    val ordered = appLibraryView.cachedApps().map { it.packageName }
                    saveGridOrder(ordered)
                }
                override fun onSwiped(vh: androidx.recyclerview.widget.RecyclerView.ViewHolder, dir: Int) = Unit
            }
            androidx.recyclerview.widget.ItemTouchHelper(cb).attachToRecyclerView(homescreenGrid)
        }
    }

    private fun addToFolder(item: AppIconItem) {
        val folders = loadFolders()
        folders[item.packageName] = "Folder"
        folderPrefs().edit().putStringSet("folders", folders.map { "${it.key}|${it.value}" }.toSet()).apply()
        loadApps()
        showFolderPopup("Folder")
    }

    private fun showFolderPopup(name: String) {
        val folders = loadFolders()
        val members = appLibraryView.cachedApps().filter { folders[it.packageName] == name }.map { it.label }
        if (members.isEmpty()) return
        val view = com.appleux.design.components.AppleuxFolderView(this)
        view.bind(name, members) { label ->
            appLibraryView.cachedApps().firstOrNull { it.label == label }?.let { launchApp(it) }
        }
        view.setOnTitleLongClickListener {
            AppleuxDialogs.prompt(
                this,
                title = "Rename Folder",
                initialText = name,
                confirmLabel = getString(android.R.string.ok),
                cancelLabel = getString(android.R.string.cancel),
            ) { typed ->
                val renamed = typed.ifEmpty { name }
                val updated = folders.mapValues { (_, v) -> if (v == name) renamed else v }
                folderPrefs().edit().putStringSet("folders",
                    updated.map { "${it.key}|${it.value}" }.toSet()).apply()
                loadApps()
            }
        }
        com.appleux.design.components.AppleuxComponentsWiring.detailSheet(this, view).present()
    }

    /**
     * iOS 27 home-screen edit mode.
     *
     * Long-pressing any icon enters this: every icon and dock item starts a
     * small spring rotation, and a tap outside leaves. This is the behaviour
     * `AppleuxJiggle` was written for and never received — it had zero
     * references, alongside a duplicate in the launcher claiming provenance
     * that could not exist.
     *
     * Only VISIBLE children are animated. Both the grid and the dock are
     * RecyclerViews, so iterating all children would animate rows the user
     * cannot see, which is wasted work per frame on a 2 GB device and is exactly
     * the mistake the launcher icon cache fix already made once.
     */
    private var inEditMode = false

    private fun enterEditMode() {
        if (inEditMode) return
        inEditMode = true
        AppleuxHaptics.getInstance(this).mediumImpact()
        jiggleVisibleChildren(homescreenGrid)
        jiggleVisibleChildren(dockView)
        // Tapping the wallpaper leaves edit mode, as in iOS.
        wallpaperView.setOnClickListener { exitEditMode() }
    }

    private fun exitEditMode() {
        if (!inEditMode) return
        inEditMode = false
        stopJiggling(homescreenGrid)
        stopJiggling(dockView)
        wallpaperView.setOnClickListener(null)
    }

    private fun jiggleVisibleChildren(group: android.view.ViewGroup) {
        val container = group as? androidx.recyclerview.widget.RecyclerView
            ?: group.getChildAt(0) as? androidx.recyclerview.widget.RecyclerView
            ?: return
        for (i in 0 until container.childCount) {
            com.appleux.design.components.AppleuxJiggle.start(container.getChildAt(i), i)
        }
    }

    private fun stopJiggling(group: android.view.ViewGroup) {
        val container = group as? androidx.recyclerview.widget.RecyclerView
            ?: group.getChildAt(0) as? androidx.recyclerview.widget.RecyclerView
            ?: return
        for (i in 0 until container.childCount) {
            com.appleux.design.components.AppleuxJiggle.stop(container.getChildAt(i))
        }
    }

    /**
     * Hardware volume keys -> the iOS HUD.
     *
     * Returning true consumes the key so the activity does not also run its
     * default handling. The stream change and the HUD both happen in the
     * dispatcher; see it for why the AOSP panel cannot be suppressed from here.
     */
    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent): Boolean {
        if (com.appleux.design.components.AppleuxVolumeKeys.handle(this, keyCode)) {
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onPause() {
        com.appleux.design.components.AppleuxVolumeKeys.dismiss(this)
        super.onPause()
    }

    private fun launchApp(item: AppIconItem) {
        try {
            rememberLaunch(item.packageName)
            startActivity(item.intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: android.content.ActivityNotFoundException) {
            AppleuxToast.show(this, "Cannot open ${item.label}")
        }
    }

    /**
     * iOS bottom-edge app switching, without any special permission.
     *
     * Switching tasks via ActivityManager needs a signature permission we do
     * not have, so the launcher keeps its own launch history (prefs, capped)
     * and re-fires the target's launch intent: with FLAG_ACTIVITY_NEW_TASK
     * that resumes the existing task instead of restarting the app. The
     * cursor walks the list across consecutive swipes and resets on launch.
     */
    private var recentCursor = 0
    private fun recentPrefs() = getSharedPreferences("appleux_launcher_recent", MODE_PRIVATE)
    private fun recentHistory(): MutableList<String> =
        (recentPrefs().getStringSet("history", emptySet()) ?: emptySet()).toMutableList()
    private fun rememberLaunch(pkg: String) {
        val h = recentHistory()
        h.remove(pkg)
        h.add(0, pkg)
        recentPrefs().edit().putStringSet("history", h.take(12).toSet()).apply()
        recentCursor = 0
    }
    private fun cycleRecentApp(forward: Boolean) {
        val h = recentHistory()
        if (h.size < 2) return
        recentCursor = if (forward) (recentCursor + 1) % h.size
        else (recentCursor - 1 + h.size) % h.size
        val pkg = h[recentCursor]
        val item = appLibraryView.cachedApps().firstOrNull { it.packageName == pkg } ?: return
        try {
            startActivity(item.intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) { recentCursor = 0 }
    }

    private fun openAppleuxApps() {
        com.appleux.launcher.ui.AppleuxAppsSheet.show(this)
    }

    private fun openLibrary() {
        // Reduce Motion consumes the flag here too: cancel any in-flight
        // animation and snap the library open with no transition.
        val reduceMotion = AppleuxRuntimeConfig.isReduceMotionEnabled
        if (reduceMotion) {
            appLibraryView.animate().cancel()
            libraryScrim.animate().cancel()
        }
        // iOS shows the library as its own page, not as a sheet over home:
        // the home content hides underneath instead of bleeding through.
        homescreenGrid.visibility = View.GONE
        dockView.visibility = View.GONE
        homeSearchPill?.visibility = View.GONE
        pageDots?.visibility = View.GONE
        libraryScrim.visibility = View.VISIBLE
        appLibraryView.visibility = View.VISIBLE
        // iOS page transition: quick fade, skipped under Reduce Motion.
        if (!reduceMotion) {
            appLibraryView.alpha = 0f
            appLibraryView.animate().alpha(1f).setDuration(160L).start()
        } else {
            appLibraryView.alpha = 1f
        }
        // Prototype-derived: the prototype deepens the backdrop dim from 0.12 to
        // 0.30 when the App Library page is on top (HomeScreen.kt GlassScene).
        wallpaperDim.alpha = LauncherTokens.APP_LIBRARY_WALLPAPER_DIM
    }

    /**
     * iOS zones: swipe down from the top-right opens Control Center,
     * top-LEFT opens Notification Center (iOS 27 change: the corner replaces
     * the old top-of-screen pull-down), top-center opens Spotlight (our
     * stand-in for the iOS 27 Search-or-Ask panel, which needs Apple
     * Intelligence hardware we do not have).
     */
    /**
     * The top-edge strip: swipes that start here are CC/NC, exactly like iOS.
     * Below it, a swipe-down is Spotlight. 0.06 is the status-bar band on our
     * measured frame (76px cutout on 2408px panel).
     */
    private fun onSwipeDownAt(xFraction: Float, downYFraction: Float) {
        val fromTopEdge = downYFraction < 0.06f
        if (fromTopEdge && xFraction > 0.6f) {
            // Toggle, not mirrored SHOW/HIDE: the service owns panel state and
            // launcher-side flags desync across process restarts.
            sendSystemUi("com.appleux.systemui.TOGGLE_CONTROL_CENTER")
        } else if (isLibraryOpen()) {
            closeLibrary()
        } else if (closeSpotlight()) {
            // A visible Spotlight consumes the swipe-down first.
        } else if (xFraction >= 0.4f) {
            // Center-home swipe-down: Spotlight (apps + settings index).
            openSpotlight()
        } else {
            sendSystemUi("com.appleux.systemui.TOGGLE_NOTIFICATIONS")
        }
    }

    private fun isLibraryOpen(): Boolean =
        this::appLibraryView.isInitialized &&
            appLibraryView.visibility == View.VISIBLE

    private fun sendSystemUi(action: String) {
        // The com.appleux.systemui overlay service was deleted on 2026-10-17
        // (deliverables/m23): the iOS chrome now compiles into
        // com.android.systemui, so there is no second process to address.
        //
        // These are the launcher's system-chrome entry points, mapped onto the
        // platform panels they stood for:
        //   SHOW/TOGGLE_CONTROL_CENTER -> iOS Control Center
        //   SHOW/TOGGLE_NOTIFICATIONS  -> iOS Notification Center
        //   HIDE_ALL                   -> close whichever is open
        //   SHOW_RECENTS               -> not reachable from an app, see below
        //
        // Recents is NOT AppleuxSystemUi: it is still QuickStep, owned by
        // SystemUI, and the launcher reaches it through the public recents
        // intent rather than through StatusBarManager (which has no public or
        // reliable hidden accessor for it).
        runCatching {
            when (action) {
                "com.appleux.systemui.SHOW_CONTROL_CENTER",
                "com.appleux.systemui.TOGGLE_CONTROL_CENTER" ->
                    AppleuxSystemUi.toggleControlCenter(this)

                "com.appleux.systemui.SHOW_NOTIFICATIONS",
                "com.appleux.systemui.TOGGLE_NOTIFICATIONS" ->
                    AppleuxSystemUi.toggleNotificationCenter(this)

                "com.appleux.systemui.HIDE_ALL" ->
                    AppleuxSystemUi.collapse(this)

                // Recents is still stock QuickStep inside SystemUI and there is
                // no verifiable app-side accessor for it: RecentsManager is not
                // in the SDK android.jar (javap: class not found) and AOSP
                // declares no symbols.xml entry for showRecentTasks, so an
                // app cannot reach it without reflection on a hidden API. The
                // system recents gesture still works. Adding a fabricated
                // reflective call here would be worse than saying so.
                "com.appleux.systemui.SHOW_RECENTS" -> android.util.Log.i(
                    "AppleuxLauncher",
                    "SHOW_RECENTS: recents is stock QuickStep; use the system gesture"
                )

                else -> android.util.Log.w(
                    "AppleuxLauncher",
                    "sendSystemUi: unhandled action $action"
                )
            }
        }.onFailure {
            android.util.Log.w("AppleuxLauncher", "sendSystemUi $action failed: ${it.javaClass.simpleName}: ${it.message}")
        }
    }

    private fun openTodayView() {
        val todayView = com.appleux.design.components.AppleuxTodayView(this)
        todayView.visibility = View.GONE
        root.addView(todayView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        todayView.visibility = View.VISIBLE
        // iOS page transition: slide from right
        todayView.translationX = root.width.toFloat()
        todayView.animate().translationX(0f).setDuration(300).start()
    }

    private fun closeLibrary() {
        if (AppleuxRuntimeConfig.isReduceMotionEnabled) {
            appLibraryView.animate().cancel()
            libraryScrim.animate().cancel()
        }
        libraryScrim.visibility = View.GONE
        appLibraryView.visibility = View.GONE
        homescreenGrid.visibility = View.VISIBLE
        dockView.visibility = View.VISIBLE
        homeSearchPill?.visibility = View.VISIBLE
        pageDots?.visibility = if (pageCount > 1) View.VISIBLE else View.GONE
        wallpaperDim.alpha = LauncherTokens.HOMESCREEN_WALLPAPER_DIM
        // Absolute HIDE_ALL is state-safe (no mirrored flags).
        sendSystemUi("com.appleux.systemui.HIDE_ALL")
    }

    /**
     * Dark mode is a user choice stored in Settings.System, so it wins over the
     * platform night mode. Must run AFTER [AppleuxTheme.applyToActivity], which
     * otherwise resets isDarkMode from the system uiMode.
     */
    /** Hides stock status/nav bars in our own window (iOS chrome instead). */
    private fun hideStockChrome() {
        runCatching {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                // The insets controller only exists once the window is
                // attached: calling this from onCreate alone silently no-ops
                // (null controller) and the stock bars stay up — which is why
                // a first swipe only revealed battery/time instead of reaching
                // our panels. Re-applied on focus and resume below.
                val c = window.insetsController ?: return
                c.hide(
                    android.view.WindowInsets.Type.statusBars() or
                        android.view.WindowInsets.Type.navigationBars()
                )
                c.systemBarsBehavior =
                    android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                @Suppress("DEPRECATION")
                window.setFlags(
                    android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN
                )
            }
        }
    }

    private fun applyDarkMode() {
        AppleuxTheme.isDarkMode = AppleuxRuntimeConfig.isDarkModeEnabled
        AppleuxTheme.applySystemBars(this)
        val background = AppleuxTheme.background.resolved
        if (this::root.isInitialized) root.setBackgroundColor(background)
        if (this::wallpaperView.isInitialized) {
            // iOS-style wallpaper: light/dark pair swaps automatically with the
            // theme, exactly like iOS appearance-coupled wallpapers.
            wallpaperView.setImageResource(
                if (AppleuxTheme.isDarkMode) R.drawable.appleux_wallpaper_dark
                else R.drawable.appleux_wallpaper_light
            )
        }
        if (this::homescreenGrid.isInitialized) homescreenGrid.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        applyGlass()
    }

    /**
     * Liquid-Glass surfaces: the dock gets a rounded translucent bar plus
     * live blur, and the library scrim gets blur plus dim — both scaled by
     * the user-adjustable glass intensity (0 = Reduce-Transparency equivalent).
     */
    private fun applyGlass() {
        if (!this::root.isInitialized) return
        // #6: blur budget — Q70/low-power disables costly RenderEffect blur.
        val budgetOk = runCatching {
            com.appleux.performance.controller.AdaptiveRenderPolicy.getInstance().isBlurEnabled()
        }.getOrDefault(true)
        val level = if (budgetOk) AppleuxRuntimeConfig.glassIntensity else 0
        if (this::dockView.isInitialized) {
            val density = resources.displayMetrics.density
            val radius = LauncherTokens.DOCK_CORNER_RADIUS_DP * density
            val base = if (AppleuxTheme.isDarkMode) android.graphics.Color.BLACK
                else android.graphics.Color.WHITE
            val alpha = (
                LauncherTokens.DOCK_GLASS_ALPHA_BASE +
                    com.appleux.design.components.AppleuxGlass.level01(level) *
                    LauncherTokens.DOCK_GLASS_ALPHA_RANGE
                ).toInt().coerceIn(0, 255)
            // Tint + blur the background bar ONLY; the icons above it stay sharp.
            // The GradientDrawable carries the fill and a 1dp rim — a rim is
            // what actually reads as glass at this size — while the CORNER
            // SHAPE comes from the outline below, because a GradientDrawable
            // can only do circular corners and iOS's are superelliptical.
            dockView.glassBackground.background =
                android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                    setColor((alpha shl 24) or (base and 0x00FFFFFF))
                    setStroke(
                        (density).toInt().coerceAtLeast(1),
                        if (AppleuxTheme.isDarkMode) 0x33FFFFFF else 0x59FFFFFF
                    )
                }
            dockView.glassBackground.clipToOutline = true
            dockView.glassBackground.outlineProvider =
                object : android.view.ViewOutlineProvider() {
                    override fun getOutline(
                        view: View,
                        outline: android.graphics.Outline
                    ) {
                        AppleuxSuperellipse.outline(
                            view.width, view.height, radius, outline
                        )
                    }
                }
            dockView.background = null
            com.appleux.design.components.AppleuxGlass.applyBlur(dockView.glassBackground, level)
        }
        if (this::libraryScrim.isInitialized) {
            val dim = com.appleux.design.components.AppleuxGlass.scrimAlphaForLevel(level)
            val base = if (AppleuxTheme.isDarkMode) android.graphics.Color.BLACK
                else android.graphics.Color.WHITE
            libraryScrim.setBackgroundColor((dim shl 24) or (base and 0x00FFFFFF))
            com.appleux.design.components.AppleuxGlass.applyBlur(libraryScrim, level)
        }
    }

    /**
     * The adaptive controller must only run when the user has left it enabled.
     * Reading the flag here (and again on resume) is what makes the Settings
     * toggle actually take effect.
     */
    private fun syncPerformanceWithConfig() {
        val shouldRun = AppleuxRuntimeConfig.isAdaptivePerformanceEnabled &&
            !AppleuxRuntimeConfig.isLowPowerModeEnabled
        if (shouldRun == performanceRunning) return
        if (shouldRun) {
            performanceController.start()
        } else {
            performanceController.stop()
        }
        performanceRunning = shouldRun
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideStockChrome()
    }

    override fun onResume() {
        super.onResume()
        hideStockChrome()
        AppleuxRuntimeConfig.observe(this)
        AppleuxRuntimeConfig.reload(this)
        applyDarkMode()
        syncPerformanceWithConfig()
        loadApps()
    }

    override fun onDestroy() {
        performanceController.unregisterListener(this)
        renderPolicy.unregisterObserver(libraryObserver)
        super.onDestroy()
    }

    override fun onQualityChanged(level: AppleuxQualityLevel) {
        homescreenGrid.setQualityLevel(level)
        dockView.setQualityLevel(level)
    }

    override fun onRenderScaleChanged(scale: Float) {
        homescreenGrid.setRenderScale(scale)
        dockView.setRenderScale(scale)
    }

    override fun onMonitoringLevelChanged(level: MonitoringLevel) = Unit
}