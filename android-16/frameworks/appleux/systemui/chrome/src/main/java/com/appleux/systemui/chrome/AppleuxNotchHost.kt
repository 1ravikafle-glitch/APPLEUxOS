package com.appleux.systemui.chrome

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.appleux.design.components.AppleuxDynamicIsland
import com.appleux.design.tokens.ColorTokens

/**
 * Draws the iPhone 11 notch **system-wide**, over the POCO M5's physical
 * waterdrop.
 *
 * ======================================================================
 * THE PROBLEM THIS SOLVES
 * ======================================================================
 * The panel has a 100 x 76 px waterdrop centred at x = 540, and no software
 * can remove it. What software CAN do is cover it: a black shape drawn above
 * it, matching the iPhone 11 notch, makes the device read as a notched iPhone
 * instead of a waterdrop Android phone. That is the single largest hardware
 * tell on this device.
 *
 * Until now the mask was drawn only by [AppleuxDynamicIsland] instances the
 * launcher and the shade own, which means the waterdrop came back the moment
 * any other app was in front. This host puts the mask in the **status bar
 * window**, which is above every application window and above the keyguard,
 * so the notch is present everywhere.
 *
 * ======================================================================
 * WHY THE STATUS BAR WINDOW AND NOT AN OVERLAY APP
 * ======================================================================
 * An overlay app would need `SYSTEM_ALERT_WINDOW`, a second resident process
 * and a foreground service. The status bar window already exists, is already
 * above apps, and is already owned by SystemUI. Adding one child view costs
 * one measure/layout pass at boot and nothing per frame.
 *
 * ======================================================================
 * GEOMETRY, AND WHY THE STATUS BAR HAD TO GROW
 * ======================================================================
 * [com.appleux.design.tokens.NotchTokens] sizes the notch at 50.2% of frame
 * width and 29 dp tall — the iPhone 11 proportions (208 x 30 pt on a 414 pt
 * frame) mapped onto our 392.73 dp frame. On this panel 29 dp is 79.75 px,
 * which is just over the 76 px the waterdrop occupies, so the mask covers it.
 *
 * The status band itself was 73 px, which is SHORTER than the notch. A notch
 * taller than its window gets its rounded bottom corners clipped by the
 * window bounds, and a notch with square-cut corners is the one detail that
 * makes a fake notch look fake. `status_bar_height_portrait` in
 * FrameworkOverlayRock is therefore 44 dp (121 px): iPhone 11's status bar is
 * 44 pt on an 896 pt frame = 4.9%, and 4.9% of our 875.64 dp frame is 43 dp,
 * so 44 dp is the proportion-correct value, not an arbitrary enlargement.
 * With 121 px of window there is 41 px of clearance under the notch, which is
 * the same relationship iOS has (14 pt of bar below a 30 pt notch).
 *
 * ======================================================================
 * WHAT IT DELIBERATELY DOES NOT DO
 * ======================================================================
 * The island does **not** expand here. Its expanded state is 150 dp tall and
 * this window is 121 px, so an expansion in this host would be clipped
 * mid-animation and look broken. Expansion stays with the launcher (where it
 * already works, unclipped, on a full-screen surface). This host draws the
 * resting notch, the charging dot and the battery state — the parts that are
 * the same size as the notch.
 *
 * Tap is disabled for the same reason: a click target that cannot show its
 * result is worse than no target.
 */
object AppleuxNotchHost {

    private var host: ViewGroup? = null
    private var island: AppleuxDynamicIsland? = null
    private var receiver: BroadcastReceiver? = null

    /** True once [attach] has run, so boot code can skip re-attaching. */
    @Volatile
    var isAttached: Boolean = false
        private set

    /**
     * Adds the notch to [parent], which must be the status bar window root.
     *
     * Idempotent: a second call on the same parent is ignored, and a call on a
     * different parent detaches the old one first. `StatusBarWindowView` can be
     * re-created when the display configuration changes, and two notches over
     * one status bar is not a state any iPhone has.
     */
    @JvmStatic
    fun attach(parent: ViewGroup) {
        if (host === parent && isAttached) return
        detach()

        val notch = try {
            AppleuxDynamicIsland(parent.context)
        } catch (t: Throwable) {
            // A failure here must not take SystemUI's status bar with it. The
            // caller's try/catch is the outer guard; this is the inner one.
            android.util.Log.w(TAG, "notch view could not be created", t)
            return
        }

        notch.apply {
            // Resting notch only — see the class comment for why expansion is
            // not enabled in this window.
            isClickable = false
            isFocusable = false
            setOnClickListener(null)
            contentDescription = null
            // Above the status bar's own icons: the notch must never be
            // drawn under a clock glyph.
            elevation = 1000f
        }

        val lp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.CENTER_HORIZONTAL,
        )
        // StatusBarWindowView.applyMargins() rewrites topMargin from the
        // display cutout's waterfall inset on every insets pass. Waterfall is
        // always 0 on this device, so 0 is the value that survives; setting it
        // explicitly documents the dependency rather than relying on a default.
        lp.topMargin = 0

        parent.addView(notch, lp)

        host = parent
        island = notch
        registerBattery(parent.context)
        isAttached = true
    }

    /** Removes the notch and stops the battery subscription. */
    @JvmStatic
    fun detach() {
        receiver?.let { r -> runCatching { host?.context?.unregisterReceiver(r) } }
        receiver = null
        island?.let { v -> (v.parent as? ViewGroup)?.removeView(v) }
        island = null
        host = null
        isAttached = false
    }

    /**
     * Feeds real battery state into the notch, so the charging dot reflects
     * the device rather than a placeholder.
     *
     * `ACTION_BATTERY_CHANGED` is a sticky broadcast, so registering here also
     * delivers the current value immediately — no separate initial read, and
     * no window in which the notch shows a wrong state.
     */
    private fun registerBattery(context: Context) {
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                val i = intent ?: return
                val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level < 0 || scale <= 0) return
                val pct = (level * 100) / scale
                val charging = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
                val view = island ?: return
                // Charge time estimate is only meaningful while charging and
                // is nullable by design; null hides the line rather than
                // inventing one.
                val chargeText = if (charging) {
                    AppleuxDynamicIsland.chargeTimeText(view.context)
                } else {
                    null
                }
                view.bindBattery(charging, pct, chargeText)

                // The notch's own black area is the island's display surface:
                // while charging, the right shoulder carries the battery glyph
                // and the percentage, so the content lives INSIDE the iPhone 11
                // notch rather than beside it. Cleared the moment the cable
                // comes out, so the notch returns to plain black.
                //
                // Charging is the one live state SystemUI can source without
                // reaching into another subsystem. Media and timers would need
                // a MediaSessionManager subscription or a timer registry, and a
                // live region that silently never updates is worse than one
                // that admits it has nothing to show.
                view.bindNotchContent(
                    text = if (charging) "$pct%" else null,
                    iconName = if (charging) "system_battery" else null,
                    tint = if (charging) ColorTokens.systemGreen else null,
                )
            }
        }
        runCatching {
            context.registerReceiver(r, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            receiver = r
        }.onFailure { t ->
            // Losing the live dot degrades to a static notch, which is still
            // correct; never let this abort status bar setup.
            android.util.Log.w(TAG, "battery feed unavailable", t)
        }
    }

    private const val TAG = "AppleuxNotch"
}
