package com.appleux.launcher

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.appleux.design.components.AppleuxSuperellipse
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
        android.app.AlertDialog.Builder(this)
            .setTitle(R.string.widget_gallery_title)
            .setItems(placed.map { widgetTitleFor(it.first) }.toTypedArray()) { dialog, which ->
                removeWidget(placed[which].second)
                dialog.dismiss()
            }
            .setNeutralButton(R.string.widget_add) { _, _ -> showWidgetPicker() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showWidgetPicker() {
        val titles = widgetDefs.map { getString(it.titleRes) }.toTypedArray()
        android.app.AlertDialog.Builder(this)
            .setTitle(R.string.widget_gallery_title)
            .setItems(titles) { _, which -> requestBindWidget(widgetDefs[which]) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun widgetTitleFor(providerClassName: String): String =
        widgetDefs.firstOrNull { packageName + it.providerClass == providerClassName }
            ?.let { getString(it.titleRes) }
            ?: providerClassName.substringAfterLast('.')

    private fun requestBindWidget(def: WidgetDef) {
        if (loadWidgetSpecs().size >= 3) {
            android.widget.Toast.makeText(this, "Widget limit reached", android.widget.Toast.LENGTH_SHORT).show()
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

        renderPolicy.registerObserver(libraryObserver)
        performanceController.registerListener(this)
        syncPerformanceWithConfig()

        // Cold-start the SystemUI overlays (also started on BOOT_COMPLETED).
        runCatching {
            val svc = android.content.Intent().apply {
                component = android.content.ComponentName(
                    "com.appleux.systemui",
                    "com.appleux.systemui.AppleuxSystemUIService"
                )
            }
            if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(svc)
            else @Suppress("DEPRECATION") startService(svc)
        }
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
        }
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
        root.addView(homescreenGrid)
        root.addView(libraryScrim)
        root.addView(appLibraryView)
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
            onSwipeUp = { openLibrary() },
            onSwipeDown = { xFraction -> onSwipeDownAt(xFraction) },
            onLongPress = { x, y -> onLongPressAt(x, y) }
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
    private fun loadApps() {
        val all = appLibraryView.loadApps()
        val dock = dockPreferences.resolve(all)
        val dockPkgs = dock.filterNotNull().map { it.packageName }.toSet()

        homescreenGrid.submitApps(all.filter { it.packageName !in dockPkgs })
        homescreenGrid.submitWidgets(rebuildWidgetCells())
        homescreenGrid.setOnWidgetLongClickListener { openWidgetGallery() }
        homescreenGrid.setOnAppLongClickListener { showAppContextMenu(it) }
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

        android.app.AlertDialog.Builder(this)
            .setTitle(current?.label ?: getString(R.string.dock_menu_title))
            .setItems(options.toTypedArray()) { _, which -> actions[which]() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showDockAppPicker(slot: Int, all: List<AppIconItem>) {
        val labels = all.map { it.label }.toTypedArray()
        android.app.AlertDialog.Builder(this)
            .setTitle(R.string.dock_picker_title)
            .setItems(labels) { _, which -> setDockSlot(slot, all[which]) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
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

    /** iOS app-icon context menu. "Add to Folder" is absent: this launcher has no folders. */
    private fun showAppContextMenu(item: AppIconItem) {
        val labels = arrayOf(
            getString(R.string.app_menu_add_to_dock),
            getString(R.string.app_menu_info),
            getString(R.string.app_menu_remove),
            getString(R.string.app_menu_widgets)
        )
        android.app.AlertDialog.Builder(this)
            .setTitle(item.label)
            .setItems(labels) { _, which ->
                when (which) {
                    0 -> addToDock(item)
                    1 -> openAppInfo(item)
                    2 -> removeApp(item)
                    else -> openWidgetGallery()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
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
            android.widget.Toast.makeText(
                this,
                getString(R.string.app_info_unavailable, item.label),
                android.widget.Toast.LENGTH_SHORT
            ).show()
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
            android.widget.Toast.makeText(
                this,
                getString(R.string.app_remove_unavailable, item.label),
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun launchApp(item: AppIconItem) {
        try {
            startActivity(item.intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: android.content.ActivityNotFoundException) {
            android.widget.Toast.makeText(this, "Cannot open ${item.label}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun openLibrary() {
        libraryScrim.visibility = View.VISIBLE
        appLibraryView.visibility = View.VISIBLE
        // Prototype-derived: the prototype deepens the backdrop dim from 0.12 to
        // 0.30 when the App Library page is on top (HomeScreen.kt GlassScene).
        wallpaperDim.alpha = LauncherTokens.APP_LIBRARY_WALLPAPER_DIM
    }

    /**
     * iOS zones: swipe down from the top-right opens Control Center,
     * anywhere else closes the library.
     */
    private fun onSwipeDownAt(xFraction: Float) {
        android.util.Log.d("AppleuxZone", "swipe-down frac=$xFraction lib=${isLibraryOpen()}")
        if (xFraction > 0.6f) {
            // Toggle, not mirrored SHOW/HIDE: the service owns panel state and
            // launcher-side flags desync across process restarts.
            sendSystemUi("com.appleux.systemui.TOGGLE_CONTROL_CENTER")
        } else if (isLibraryOpen()) {
            closeLibrary()
        } else {
            sendSystemUi("com.appleux.systemui.TOGGLE_NOTIFICATIONS")
        }
    }

    private fun isLibraryOpen(): Boolean =
        this::appLibraryView.isInitialized &&
            appLibraryView.visibility == View.VISIBLE

    private fun sendSystemUi(action: String) {
        runCatching {
            val intent = android.content.Intent(action).apply {
                component = android.content.ComponentName(
                    "com.appleux.systemui",
                    "com.appleux.systemui.AppleuxSystemUIService"
                )
            }
            if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(intent)
            else @Suppress("DEPRECATION") startService(intent)
        }.onFailure {
            android.util.Log.w("AppleuxLauncher", "sendSystemUi $action failed: ${it.javaClass.simpleName}: ${it.message}")
        }
    }

    private fun closeLibrary() {
        libraryScrim.visibility = View.GONE
        appLibraryView.visibility = View.GONE
        wallpaperDim.alpha = LauncherTokens.HOMESCREEN_WALLPAPER_DIM
        // Absolute HIDE_ALL is state-safe (no mirrored flags).
        sendSystemUi("com.appleux.systemui.HIDE_ALL")
    }

    /**
     * Dark mode is a user choice stored in Settings.System, so it wins over the
     * platform night mode. Must run AFTER [AppleuxTheme.applyToActivity], which
     * otherwise resets isDarkMode from the system uiMode.
     */
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
        val level = AppleuxRuntimeConfig.glassIntensity
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

    override fun onResume() {
        super.onResume()
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