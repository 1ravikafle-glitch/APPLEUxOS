package com.appleux.launcher.gestures

import android.content.Context
import android.view.MotionEvent
import androidx.core.view.GestureDetectorCompat
import com.appleux.design.components.AppleuxHaptics
import com.appleux.launcher.LauncherTokens

/**
 * Detects launcher-level swipe gestures.
 *
 * The gesture stream is measured directly from DOWN/UP rather than through
 * [GestureDetectorCompat.onFling]. GestureDetector only reports a fling once it
 * has decided the movement left its "tap region", and that decision drops
 * perfectly good launcher swipes depending on how few MOVE samples the input
 * device emits; on the emulator a full-height swipe never reached onFling at
 * all. Coordinate and duration arithmetic is predictable and testable.
 */
class GestureHandler(
    private val context: Context,
    private val onSwipeUp: () -> Unit,
    private val onSwipeDown: (xFraction: Float) -> Unit = {},
    private val onLongPress: (x: Float, y: Float) -> Unit = { _, _ -> },
    /**
     * Horizontal swipes carry the DOWN position as a height fraction so the
     * caller can split the bottom edge (iOS recent-app switching) from the
     * page area (home pages). 1.0 is the bottom edge.
     */
    private val onSwipeLeft: (downYFraction: Float) -> Unit = {},
    private val onSwipeRight: (downYFraction: Float) -> Unit = {},
    private val onSwipeUpHold: () -> Unit = {}
) {

    private val haptics: AppleuxHaptics = AppleuxHaptics.getInstance(context)

    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L

    /** Called from the launcher's root for every touch event. */
    fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                downTime = event.eventTime
            }

            // A scrolling child (the library grid) turns the rest of the gesture
            // into ACTION_CANCEL, so the swipe has to be evaluated on cancel too
            // or closeLibrary never runs while the library is open.
            MotionEvent.ACTION_UP ->
                evaluateSwipe(event.x, event.y, event.eventTime, isUp = true)
            MotionEvent.ACTION_CANCEL ->
                evaluateSwipe(event.x, event.y, event.eventTime, isUp = false)
        }
        return false
    }

    private fun evaluateSwipe(x: Float, y: Float, eventTime: Long, isUp: Boolean): Boolean {
        val dx = x - downX
        val dy = y - downY
        val duration = (eventTime - downTime).coerceAtLeast(1L)
        val adx = kotlin.math.abs(dx)
        val ady = kotlin.math.abs(dy)

        // Horizontal first: the old order checked |dy| <= slop first, which
        // caught every flat swipe and returned before reaching here — so no
        // horizontal swipe (pages, library, edge app-switch) ever fired.
        // Only a dominant horizontal displacement past slop counts.
        if (adx > ady && adx > LauncherTokens.SWIPE_DISTANCE_PX) {
            haptics.impact(AppleuxHaptics.ImpactStyle.LIGHT)
            val height = context.resources.displayMetrics.heightPixels.coerceAtLeast(1)
            val downYFraction = (downY / height).coerceIn(0f, 1f)
            if (dx < 0) onSwipeLeft(downYFraction) else onSwipeRight(downYFraction)
            return true
        }
        if (ady <= LauncherTokens.SWIPE_DISTANCE_PX) {
            // Not a swipe: a slow, nearly stationary hold is a long-press
            // (RecyclerView long-click listeners are unreliable across
            // devices, so the launcher root owns this decision instead).
            //
            // Only ACTION_UP can END a long-press. CANCEL means a child took the
            // gesture over (the library grid scrolling), in which case the
            // finger never came to rest on the cell the user was aiming at.
            //
            // The callback carries the DOWN coordinates because that is the
            // point the press is anchored to, and the only correct hit-test
            // origin: by ACTION_UP a long-press finger has drifted.
            val dist = kotlin.math.hypot(dx, dy)
            if (isUp &&
                duration >= LauncherTokens.LONG_PRESS_DURATION_MS &&
                dist <= LauncherTokens.LONG_PRESS_SLOP_PX
            ) {
                haptics.impact(AppleuxHaptics.ImpactStyle.LIGHT)
                onLongPress(downX, downY)
            }
            return false
        }
        if (duration > LauncherTokens.SWIPE_MAX_DURATION_MS) return false

        haptics.impact(AppleuxHaptics.ImpactStyle.MEDIUM)
        if (dy < 0) {
            // Hold with finger down ~300ms before lifting: recents. Plain fast
            // swipe: App Library. Thresholds are emulator-verified, not paper.
            if (duration >= SWIPE_HOLD_DURATION_MS &&
                kotlin.math.hypot(dx, dy) <= LauncherTokens.LONG_PRESS_SLOP_PX * 3
            ) {
                onSwipeUpHold()
            } else {
                onSwipeUp()
            }
        } else {
            val width = context.resources.displayMetrics.widthPixels.coerceAtLeast(1)
            onSwipeDown(x / width)
        }
        return true
    }

    companion object {
        /** Hold-vs-flick boundary for swipe-up-and-hold (recents). */
        const val SWIPE_HOLD_DURATION_MS = 300L
    }

    /** Installs this handler into the launcher root container. */
    fun attachToRoot(root: GestureHostLayout) {
        root.installGestureHandler(this)
    }
}