package org.ioslike.ui.shell

import android.app.PendingIntent
import android.app.Service
import android.app.StatusBarManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.WindowManager.LayoutParams as LP
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import org.ioslike.ui.theme.LocalSafeInsets
import org.ioslike.ui.theme.readSafeInsets
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import org.ioslike.ui.controlcenter.MediaRepo
import org.ioslike.ui.controlcenter.QuickShareBackend
import org.ioslike.ui.theme.WallpaperSource
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.ioslike.ui.controlcenter.ControlCenterController
import org.ioslike.ui.controlcenter.ControlCenterScreen
import org.ioslike.ui.notifications.HeadsUpHost
import org.ioslike.ui.notifications.IOSNotificationListener
import org.ioslike.ui.notifications.NotifItem
import org.ioslike.ui.notifications.NotificationCenterScreen
import org.ioslike.ui.theme.iosSpring
import kotlin.math.abs

enum class Panel { None, Notifications, Control }

/**
 * Hosts three system windows:
 *  1. EdgeStrip   - thin invisible strip at the top that detects the iOS 27 style swipe zones
 *                   (left = Notification Center, right = Control Center, middle = "Search or Ask")
 *  2. Panel       - full-screen Compose window with window-level backdrop blur (the glass effect)
 *  3. Heads-up    - banner that slides in from the left
 *
 * Window TYPE_NOTIFICATION_SHADE is hidden API; this module is built with platform_apis + platform signature.
 * If your tree rejects it for a non-SystemUI process, move this logic into SystemUI instead (see README).
 */
class ShellService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private val owner = ComposeOwner()
    private val panel = MutableStateFlow(Panel.None)

    private lateinit var wm: WindowManager
    private lateinit var cc: ControlCenterController
    private lateinit var media: MediaRepo
    private var wallpaper by mutableStateOf<ImageBitmap?>(null)
    private lateinit var panelView: ComposeView
    private lateinit var panelLp: LP

    override fun onBind(i: Intent?): IBinder? = null
    override fun onStartCommand(i: Intent?, f: Int, id: Int) = START_STICKY

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WindowManager::class.java)
        cc = ControlCenterController(this, QuickShareBackend(this)).also { it.start() }
        media = MediaRepo(this).also { it.start() }

        // Stop the stock shade from also opening on the same gesture.
        getSystemService(StatusBarManager::class.java).disable(StatusBarManager.DISABLE_EXPAND)

        addPanelWindow()
        addEdgeStrip()
        addHeadsUpWindow()

        scope.launch {
            panel.collect { p ->
                handler.removeCallbacks(hideRunnable)
                if (p != Panel.None) {
                    if (wallpaper == null) wallpaper = WallpaperSource.load(this@ShellService)
                    setPanelInteractive(true)
                } else handler.postDelayed(hideRunnable, 320)
            }
        }
    }

    override fun onDestroy() {
        scope.cancel(); cc.stop(); media.stop(); super.onDestroy()
    }

    // ---------------------------------------------------------------- windows

    private fun baseLp(w: Int, h: Int, flags: Int) = LP(
        w, h, LP.TYPE_NOTIFICATION_SHADE, flags or LP.FLAG_LAYOUT_IN_SCREEN or LP.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    )

    private fun addPanelWindow() {
        panelLp = baseLp(LP.MATCH_PARENT, LP.MATCH_PARENT, LP.FLAG_NOT_FOCUSABLE or LP.FLAG_NOT_TOUCHABLE)
            .apply { title = "IOSLikePanel" }
        panelView = composeView {
            val p by panel.collectAsState()
            val notifs by IOSNotificationListener.items.collectAsState()
            Box(Modifier.fillMaxSize()) {
                AnimatedVisibility(
                    visible = p == Panel.Control,
                    enter = fadeIn() + scaleIn(iosSpring(), initialScale = 0.9f, transformOrigin = TransformOrigin(1f, 0f)),
                    exit = fadeOut(tween(160)) + scaleOut(tween(160), targetScale = 0.9f, transformOrigin = TransformOrigin(1f, 0f)),
                ) { ControlCenterScreen(cc, media, wallpaper) { panel.value = Panel.None } }

                AnimatedVisibility(
                    visible = p == Panel.Notifications,
                    enter = fadeIn() + slideInVertically(iosSpring()) { -it / 8 },
                    exit = fadeOut(tween(160)) + slideOutVertically(tween(160)) { -it / 8 },
                ) {
                    NotificationCenterScreen(
                        notifs = notifs,
                        onOpen = { openNotification(it) },
                        onDismiss = { IOSNotificationListener.dismiss(it.key) },
                        onClearAll = { IOSNotificationListener.dismissAll() },
                        onClose = { panel.value = Panel.None },
                        wallpaper = wallpaper,
                    )
                }
            }
        }
        wm.addView(panelView, panelLp)
    }

    private val hideRunnable = Runnable { if (panel.value == Panel.None) setPanelInteractive(false) }

    /** On: catch touches and blur what is behind. Off: fully click-through, no blur cost. */
    private fun setPanelInteractive(on: Boolean) {
        if (on) {
            panelLp.flags = (panelLp.flags and LP.FLAG_NOT_TOUCHABLE.inv()) or LP.FLAG_BLUR_BEHIND
            panelLp.blurBehindRadius = 70
        } else {
            panelLp.flags = (panelLp.flags or LP.FLAG_NOT_TOUCHABLE) and LP.FLAG_BLUR_BEHIND.inv()
            panelLp.blurBehindRadius = 0
        }
        wm.updateViewLayout(panelView, panelLp)
    }

    private fun addHeadsUpWindow() {
        val lp = baseLp(dp(346), LP.WRAP_CONTENT, LP.FLAG_NOT_FOCUSABLE or LP.FLAG_NOT_TOUCH_MODAL).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(22); y = dp(40); title = "IOSLikeHeadsUp"
        }
        wm.addView(
            composeView { HeadsUpHost(IOSNotificationListener.arrived) { openNotification(it) } },
            lp,
        )
    }

    private fun addEdgeStrip() {
        val lp = baseLp(LP.MATCH_PARENT, dp(18), LP.FLAG_NOT_FOCUSABLE or LP.FLAG_NOT_TOUCH_MODAL).apply {
            gravity = Gravity.TOP; title = "IOSLikeEdgeStrip"
        }
        wm.addView(EdgeStrip(this), lp)
    }

    private inner class EdgeStrip(c: Context) : View(c) {
        private var sx = 0f
        private var sy = 0f
        private var fired = false
        private val slop = dp(24)

        override fun onTouchEvent(e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { sx = e.rawX; sy = e.rawY; fired = false }
                MotionEvent.ACTION_MOVE -> {
                    val dy = e.rawY - sy
                    if (!fired && dy > slop && abs(e.rawX - sx) < dy) { fired = true; route(sx / width.coerceAtLeast(1)) }
                }
            }
            return true
        }
    }

    /** iOS 27 beta layout: left = Notification Center, right = Control Center, centre = Search or Ask. */
    private fun route(frac: Float) {
        when {
            frac < 0.38f -> panel.value = Panel.Notifications
            frac > 0.62f -> panel.value = Panel.Control
            else -> startActivity(Intent(Intent.ACTION_ASSIST).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private fun openNotification(n: NotifItem) {
        panel.value = Panel.None
        try { n.intent?.send() } catch (_: PendingIntent.CanceledException) {}
        if (n.autoCancel) IOSNotificationListener.dismiss(n.key)
    }

    // ---------------------------------------------------------------- helpers

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun composeView(content: @androidx.compose.runtime.Composable () -> Unit) = ComposeView(this).apply {
        setViewTreeLifecycleOwner(owner)
        setViewTreeSavedStateRegistryOwner(owner)
        setViewTreeViewModelStoreOwner(owner)
        setContent { CompositionLocalProvider(LocalSafeInsets provides context.readSafeInsets()) { content() } }
    }

    private class ComposeOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
        private val lc = LifecycleRegistry(this)
        private val ssrc = SavedStateRegistryController.create(this)
        override val lifecycle: Lifecycle get() = lc
        override val savedStateRegistry: SavedStateRegistry get() = ssrc.savedStateRegistry
        override val viewModelStore = ViewModelStore()
        init { ssrc.performAttach(); ssrc.performRestore(null); lc.currentState = Lifecycle.State.RESUMED }
    }
}
