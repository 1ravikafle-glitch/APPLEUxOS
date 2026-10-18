package com.appleux.launcher

import android.content.Context
import android.os.Build
import android.util.Log
import java.lang.reflect.Method

/**
 * Opens the system panels from inside the launcher.
 *
 * ============================ WHY THIS EXISTS ============================
 * These call sites used to `startForegroundService` an explicit
 * `com.appleux.systemui.AppleuxSystemUIService`. That app was deleted on
 * 2026-10-17 (deliverables/m23): it floated two full-screen
 * TYPE_APPLICATION_OVERLAY windows on top of SystemUI, so the iOS chrome cost
 * an extra resident process and two composited full-screen layers per frame.
 * The chrome now compiles into com.android.systemui itself.
 *
 * An explicit `ComponentName` to a service that no longer exists does NOT throw
 * at compile time and does NOT reliably throw at runtime — it resolves to a
 * dead component and the caller sees nothing happen. Leaving those call sites
 * pointing at it would have been a silent functional regression.
 *
 * ============================ HOW IT WORKS NOW ============================
 * `StatusBarManager.expandNotificationsPanel()` / `expandSettingsPanel()` /
 * `collapsePanels()` are @hide. They are NOT in the SDK android.jar, so they
 * cannot be called directly from launcher code, and they cannot be added to
 * symbols.xml either — that is platform-build-only and this is an app.
 *
 * The honest position: this is reflection, and reflection on hidden APIs is
 * exactly what Android's hidden-API enforcement exists to discourage. It is
 * used here because the alternative is a dead service, and because the launcher
 * is the system home app on a platform-signed ROM where the launcher is a
 * first-class system component rather than a third-party app.
 *
 * If reflection is ever blocked, every method here degrades to a logged no-op
 * rather than a crash. That is deliberate: a launcher that throws because it
 * could not open a panel is strictly worse than one that silently does not
 * open it.
 *
 * ## The better long-term answer
 *
 * On iOS these panels open by a swipe from the top edge, and the surface under
 * the finger owns the gesture. The right implementation is for the launcher's
 * top inset to report the touch and let SystemUI's own shade handle it, not for
 * an app to poke the shade from outside. This helper is the stopgap for the
 * button-driven paths (the Clock widget's timer, the widget tap handlers) while
 * the top-edge gesture is built.
 */
object AppleuxSystemUi {

    private const val TAG = "AppleuxSystemUi"

    /** iOS Notification Center — pull down from the top-LEFT. */
    fun toggleNotificationCenter(context: Context) {
        requestPanel(context, "com.appleux.systemui.chrome.OPEN_NOTIFICATION_CENTER")
        invoke(context, "expandNotificationsPanel")
    }

    /**
     * Asks the SystemUI host to open one of the iOS panels.
     *
     * Best-effort by design: if SystemUI has not attached its host yet, or is not
     * running, the broadcast goes nowhere and the caller's existing gesture
     * handling still opens the stock panel. Degrading to the AOSP shade is
     * strictly better than dropping the gesture on the floor.
     */
    private fun requestPanel(context: Context, action: String) {
        try {
            context.sendBroadcast(
                android.content.Intent(action).setPackage("com.android.systemui")
            )
        } catch (t: Throwable) {
            Log.w(TAG, "panel request $action failed: ${t.javaClass.simpleName}")
        }
    }

    /**
     * iOS Control Center — pull down from the top-RIGHT.
     *
     * Was `invoke(context, "expandSettingsPanel")`, which opens AOSP's Material
     * quick settings — the single loudest remaining non-iOS surface. It now asks
     * AppleuxShadeHost, inside SystemUI, to show the iOS Control Center.
     *
     * A broadcast, because the launcher and SystemUI are different processes and
     * the host is the thing that owns the views. RECEIVER_NOT_EXPORTED on the
     * host side means no other app can raise our panels.
     *
     * expandSettingsPanel is still called afterwards, deliberately: the gesture
     * that reaches here is a pull-down that SystemUI has already begun to
     * expand, and collapsing it again would fight the finger. The host's panel is
     * drawn in the shade window above it.
     */
    fun toggleControlCenter(context: Context) {
        // First try the iOS host directly — when AppleuxShadeHost is attached in
        // SystemUI, this opens the real iOS Control Center instead of the stock
        // Material quick settings shade.
        try {
            val manager = context.getSystemService(Context.STATUS_BAR_SERVICE)
            if (manager != null) {
                val m = manager.javaClass.getMethod("toggleControlCenter")
                m.invoke(manager)
                return
            }
        } catch (t: Throwable) {
            // Reflective toggle through StatusBarManager (or a future bridge).
            // Falls through to the broadcast if StatusBarManager has no such
            // method yet.
        }

        // Broadcast to AppleuxShadeHost, which now lives in SystemUI.
        requestPanel(context, "com.appleux.systemui.chrome.OPEN_CONTROL_CENTER")
        // Do NOT call expandSettingsPanel — that opens the stock Material QS
        // shade on top of our iOS Control Center, which is the loudest remaining
        // non-iOS surface on the device.
    }

    fun collapse(context: Context) {
        invoke(context, "collapsePanels")
    }

    /**
     * Calls the named zero-argument StatusBarManager method if it exists.
     *
     * The Method handle is resolved once per call rather than cached in a
     * static: caching a reflective handle across a dark-mode or locale change
     * is fine, but caching it statically would keep a reference to a
     * classloader-owned object for the life of the launcher process, and the
     * win is a few microseconds on a gesture that is already throttled to one
     * call per user interaction.
     */
    private fun invoke(context: Context, method: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return
        runCatching {
            // Context.STATUS_BAR_SERVICE ("statusbar") is a public constant and
            // the String overload is the only one that yields the platform
            // StatusBarManager; the typed Context.getSystemService(Class)
            // overload is absent below API 23.
            @Suppress("DEPRECATION")
            val manager = context.getSystemService(Context.STATUS_BAR_SERVICE)
            if (manager == null) {
                Log.w(TAG, "StatusBarManager unavailable; $method not called")
                return
            }
            manager.javaClass.getMethod(method).invoke(manager)
        }.onFailure {
            // Logged, not thrown. See the class header.
            Log.w(TAG, "StatusBarManager.$method unavailable: ${it.javaClass.simpleName}")
        }
    }
}