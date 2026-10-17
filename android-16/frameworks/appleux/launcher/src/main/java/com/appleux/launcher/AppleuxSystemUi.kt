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
        invoke(context, "expandNotificationsPanel")
    }

    /** iOS Control Center — pull down from the top-RIGHT. */
    fun toggleControlCenter(context: Context) {
        invoke(context, "expandSettingsPanel")
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