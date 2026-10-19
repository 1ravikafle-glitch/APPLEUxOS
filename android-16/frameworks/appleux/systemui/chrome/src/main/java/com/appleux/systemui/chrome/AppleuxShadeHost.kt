package com.appleux.systemui.chrome

import android.view.View
import android.view.ViewGroup
import com.appleux.design.components.AppleuxControlCenter
import com.appleux.design.components.AppleuxNotificationShade
import com.appleux.design.components.AppleuxPanelDismissHandler
import com.appleux.systemui.AppleuxNotificationBus

/**
 * Hosts the iOS 27 Control Center and Notification Center **inside**
 * `com.android.systemui`.
 *
 * ======================================================================
 * WHAT THIS CLASS IS FOR
 * ======================================================================
 * Three separate things were broken in the same way, and one host fixes all of
 * them because they need the same thing: a place to live, and a subscription.
 *
 *  * **Control Center (UI-1).** `toggleControlCenter()` called AOSP
 *    `expandSettingsPanel()`, so the user got the Material QS shade. That is the
 *    single most visible iOS tell in the product. `AppleuxControlCenter` was
 *    written and never shown.
 *  * **Notification bus (UI-1N).** `AppleuxNotificationListener` attaches and
 *    publishes on every notification change, and `subscribe()` had zero callers.
 *    Notifications were being collected and dropped, because a publisher with no
 *    subscriber is a data source with no sink.
 *  * **Orphaned helpers.** `AppleuxPanelDismissHandler` and `AppleuxCCTileOrder`
 *    existed to sequence exactly this and had zero references.
 *
 * ======================================================================
 * WHY IT IS A HOST AND NOT A SERVICE
 * ======================================================================
 * The previous arrangement was a separate APK running a foreground service that
 * added two full-screen `TYPE_APPLICATION_OVERLAY` windows above SystemUI. That
 * cost an extra resident process, a foreground service and its notification, and
 * two MATCH_PARENT TRANSLUCENT layers the compositor blended every frame at
 * 1080x2408 — while the stock chrome sat underneath, unchanged.
 *
 * This attaches to a container SystemUI already owns, so there is no extra
 * window, no extra process and no extra layer. That is the performance half of
 * the charter's priority order, and it is why this lives in `chrome` rather than
 * in an app.
 *
 * ======================================================================
 * HOW SYSTEMUI SHOULD CREATE IT — read before wiring
 * ======================================================================
 * Attach it to the container SystemUI already inflates for the expanded status
 * bar, from a class that is guaranteed to run at boot:
 *
 * ```kotlin
 * // in SystemUI's central controller, after the shade views are inflated
 * AppleuxShadeHost.maybeAttach(shadeContainer)
 * ```
 *
 * Deliberately NOT done here: this class does not reach for `ShadeController`,
 * does not touch Dagger, and does not call `instantExpandShade`. A wrong Dagger
 * binding in SystemUI fails at boot and there is no device in this environment
 * to catch it on, so the accessor is left to the caller that already holds a
 * valid reference. That decision is the reason this file is a host and not a
 * controller.
 */
object AppleuxShadeHost {

    /**
     * Already-hosted container, so a second call replaces rather than stacks.
     *
     * Two Control Centers over one status bar is not a state any version of iOS
     * has, and AlertDialog-style stacking would fight the stock shade for the
     * same pixels.
     */
    private var container: ViewGroup? = null

    private var controlCenter: AppleuxControlCenter? = null
    private var notificationShade: AppleuxNotificationShade? = null

    /** True once [attach] has run, so the caller can skip re-attaching. */
    @Volatile
    var isAttached: Boolean = false
        private set

    /**
     * Adds the iOS panels to [host] and starts the notification subscription.
     *
     * Idempotent and safe to call from more than one place: the first call wins
     * and later calls are ignored, because a second Control Center over the same
     * status bar is never the intent and cannot be undone without restarting.
     */
    @JvmStatic
    fun attach(host: ViewGroup) {
        if (container === host && isAttached) return

        // Detach any previous host first, or a configuration change leaves an
        // orphan view holding the bus subscription alive.
        detach()

        container = host
        val cc = AppleuxControlCenter(host.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            visibility = View.GONE
        }
        val ns = AppleuxNotificationShade(host.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            visibility = View.GONE
        }

        controlCenter = cc
        notificationShade = ns
        host.addView(cc)
        host.addView(ns)

        // AppleuxPanelDismissHandler is the swipe/scrim-to-dismiss gesture, and
        // this is its first caller — it had zero references. Note its listener
        // only OBSERVES the touch stream and always returns false, so tiles and
        // notification rows keep receiving it. An overlay that consumed touches
        // here would swallow every tap, which is how the launcher's old gesture
        // overlay broke icon taps.
        cc?.let { AppleuxPanelDismissHandler.attach(it) { it.visibility = View.GONE } }
        ns?.let { AppleuxPanelDismissHandler.attach(it) { it.visibility = View.GONE } }

        // The subscription is the whole point of this line. It is the first
        // caller of AppleuxNotificationBus.subscribe in the tree, which is why
        // notifications were being dropped.
        AppleuxNotificationBus.subscribe { notifications ->
            // Called on whatever thread the listener publishes from. The shade
            // must be touched on the main thread, and the post is unconditional
            // rather than checking Looper.myLooper() because a shade that
            // silently never updated is exactly the bug this fixes.
            controlCenter?.post { notificationShade?.setNotifications(notifications) }
        }

        registerReceiver(host)
        isAttached = true
    }

    // ==================================================================
    // CROSS-PROCESS OPEN REQUESTS
    //
    // The launcher is a different process, so it cannot call openControlCenter()
    // directly. It broadcasts instead, and the host — which lives inside
    // SystemUI and owns the views — does the work.
    //
    // This is deliberately a broadcast rather than a bound service: a service
    // would need a permission and a binder transaction per swipe, and the host
    // is already the thing that has to be told. The action is namespaced to this
    // package and the receiver is registered at runtime, so it is not exported
    // and cannot be triggered by another app.
    // ==================================================================
    private const val ACTION_OPEN_CONTROL_CENTER =
        "com.appleux.systemui.chrome.OPEN_CONTROL_CENTER"
    private const val ACTION_OPEN_NOTIFICATION_CENTER =
        "com.appleux.systemui.chrome.OPEN_NOTIFICATION_CENTER"

    private var receiver: android.content.BroadcastReceiver? = null

    private fun registerReceiver(host: ViewGroup) {
        if (receiver != null) return
        val r = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: android.content.Context?, i: android.content.Intent?) {
                when (i?.action) {
                    ACTION_OPEN_CONTROL_CENTER -> openControlCenter()
                    ACTION_OPEN_NOTIFICATION_CENTER -> openNotificationCenter()
                }
            }
        }
        // Android 13+ requires an explicit export flag on runtime receivers.
        val flags = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            android.content.Context.RECEIVER_NOT_EXPORTED
        } else {
            0
        }
        host.context.registerReceiver(r, android.content.IntentFilter().apply {
            addAction(ACTION_OPEN_CONTROL_CENTER)
            addAction(ACTION_OPEN_NOTIFICATION_CENTER)
        }, flags)
        receiver = r
    }

    private fun unregisterReceiver() {
        val r = receiver ?: return
        receiver = null
        container?.context?.let { runCatching { it.unregisterReceiver(r) } }
    }

    /** Removes the panels and releases the subscription. */
    @JvmStatic
    fun detach() {
        AppleuxNotificationBus.subscribe(null)
        unregisterReceiver()
        controlCenter?.let { (it.parent as? ViewGroup)?.removeView(it) }
        notificationShade?.let { (it.parent as? ViewGroup)?.removeView(it) }
        controlCenter = null
        notificationShade = null
        container = null
        isAttached = false
    }

    /** Opens the Control Center, closing the Notification Center if it is up. */
    @JvmStatic
    fun openControlCenter() {
        val cc = controlCenter ?: return
        val shade = notificationShade
        if (shade != null) shade.visibility = View.GONE
        cc.visibility = View.VISIBLE
    }

    /** Opens the Notification Center, closing the Control Center if it is up. */
    @JvmStatic
    fun openNotificationCenter() {
        val shade = notificationShade ?: return
        val cc = controlCenter
        if (cc != null && cc.visibility == View.VISIBLE) cc.visibility = View.GONE
        shade.visibility = View.VISIBLE
    }

    /** True when either iOS panel is on screen. */
    @JvmStatic
    fun isPanelShowing(): Boolean =
        controlCenter?.visibility == View.VISIBLE ||
            notificationShade?.visibility == View.VISIBLE

    /** Closes whichever panel is open. */
    @JvmStatic
    fun dismissPanels() {
        controlCenter?.visibility = View.GONE
        notificationShade?.visibility = View.GONE
    }
}