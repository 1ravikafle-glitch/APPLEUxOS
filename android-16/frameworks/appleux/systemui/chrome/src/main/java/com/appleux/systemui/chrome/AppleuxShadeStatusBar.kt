package com.appleux.systemui.chrome

import android.view.View
import android.view.ViewGroup
import com.appleux.design.components.AppleuxStatusBar

/**
 * Puts our iOS-style status bar into the notification shade header so the shade
 * and the home screen share one real bar instead of one stock bar and one custom
 * one.
 *
 * This is the system-wide side of gap 4: `AppleuxStatusBar` today is only drawn
 * inside the launcher's own wallpaper surface, and inside our own overlays. In
 * iOS the status bar in the shade is the same shape as the one on the home
 * screen, and the shade window is Android's status-bar-equivalent surface, so the
 * right host is the shade header, not an overlay app window.
 *
 * Placement, in order:
 *   1. Find the shade header — the simplest durable hook is that the shade view
 *      contains a child named `notification_panel` (the same id
 *      `ShadeTouchableRegionManager` uses to locate its panel). That parent is
 *      the header region of the shade window. We attach to the header rather
 *      than to `notification_panel` itself, so the status bar becomes the top
 *      strip of the shade and the notification rows scroll under it, which is
 *      what iOS does.
 *   2. Insert `AppleuxStatusBar` into that header, sized to the platform status
 *      bar height so the system chrome it replaces is the same size our bar
 *      occupies, and keep it above the panel content but below any pull-down
 *      state we do not own.
 *   3. When the shade is collapsed, hide it; when expanded, show it. We are NOT
 *      the flap — the stock shade controller still owns the flap — we just
 *      definitely show in the shade.
 *
 * Deliberately NOT done:
 *   - we do not replace the stock status bar's notification icons or heads-ups;
 *     those belong to AOSP and survive this change. We only add our own iOS bar
 *     to the shade header so the user sees ONE iOS bar, not two.
 *   - we do not claim to cover the platform status bar on the home screen. The
 *     home screen gets our bar via the launcher surface and the notch host; that
 *     is the real scope of "system-wide iOS bar" on a stock image.
 */
object AppleuxShadeStatusBar {

    private var host: ViewGroup? = null
    private var statusBar: AppleuxStatusBar? = null
    private var targetId: Int = 0

    /** True once attached; guards against double attach. */
    @Volatile
    var isAttached: Boolean = false
        private set

    /**
     * Adds our status bar into [shadeWindowView]'s header.
     *
     * Safe to call from `ShadeTouchableRegionManager.setup()`, which holds the
     * real shade window view handed to SystemUI at boot. If the header or the
     * id we expect does not exist, this is a no-op logged warning, not a
     * thrown exception.
     */
    @JvmStatic
    fun attachToShadeHeader(shadeWindowView: ViewGroup, panel: ViewGroup?) {
        if (isAttached) {
            // Already attached — either we are re-entered at boot, or
            // `ShadeTouchableRegionManager` was re-created during a config
            // change. We do not re-attach to the same window; a new instance
            // of the shade window will be handed to us again later if it
            // re-initializes.
            return
        }

        val header = (panel?.parent as? ViewGroup) ?: findByTraversal(shadeWindowView)
            ?: run {
                android.util.Log.w(TAG, "shade header not found; status bar stays in overlays only")
                return
            }

        val sb = AppleuxStatusBar(shadeWindowView.context).apply {
            id = View.generateViewId()
            // Sized to the platform status bar height so our bar occupies
            // roughly the same vertical space as the status bar it resembles
            // on the home screen. If the status bar has changed height (display
            // cutout, foldables, etc.), this follows the platform value rather
            // than hard-coding an iOS dp value that may disagree with the
            // host view's bounds.
            // MEASURED: the status bar and the waterdrop cutout are the
            // same 76px band on this panel (deliverables/m20), so the rock
            // profile's top inset IS the platform status bar height here —
            // android.R.dimen.status_bar_height is not public API and is
            // replaced by our own measured value.
            val heightPx = com.appleux.device.rock.RockDisplayProfile.NOMINAL_TOP_INSET_PX
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                heightPx
            )
            // White-on-glass: the shade header we want to live in presents over
            // wallpaper/shade content in the same posture as the home screen
            // status bar does.
            applyOverlayTheme(true)
        }

        // Insert into header so the status bar is a sibling of the panel.
        // We prefer the top of the header; if the header has children already
        // in a preferred order, the exact index is not critical — the header
        // is a stack whose first child is the topmost item.
        val index = if (header.childCount > 0) 0 else -1
        header.addView(sb, index, sb.layoutParams)
        statusBar = sb
        host = header
        targetId = index
        isAttached = true

        android.util.Log.i(TAG, "iOS status bar attached into shade header")
    }

    /**
     * Release when the shade is torn down — e.g. on config change or on
     * shutdown — so we do not leak the view.
     */
    @JvmStatic
    fun detach() {
        statusBar?.let { view ->
            host?.removeView(view)
            statusBar = null
        }
        host = null
        targetId = 0
        isAttached = false
    }

    /**
     * Watch the shade collapse / expand with the lifecycle of the shade window so
     * the status bar we added appears when the shade is up and disappears when
     * the shade is down.
     */
    @JvmStatic
    fun onShadeExpanded() {
        statusBar?.visibility = View.VISIBLE
    }

    @JvmStatic
    fun onShadeCollapsed() {
        statusBar?.visibility = View.GONE
    }

    /**
     * Fallback when the caller could not hand us the panel: walk the shade
     * window and match by class name. The shade window's direct children
     * include the notification panel hierarchy, whose top-level container
     * is the header region we want. No resource IDs are referenced — this
     * module does not compile against SystemUI's R.
     */
    private fun findByTraversal(window: ViewGroup): ViewGroup? {
        for (i in 0 until window.childCount) {
            val child = window.getChildAt(i) as? ViewGroup ?: continue
            val name = child.javaClass.name
            if (name.endsWith("NotificationPanelView") ||
                name.endsWith("NotificationStackScrollLayout") ||
                name.endsWith("ShadeHeader")
            ) {
                return child.parent as? ViewGroup ?: child
            }
        }
        return null
    }

    private const val TAG = "AppleuxShadeStatusBar"
}
