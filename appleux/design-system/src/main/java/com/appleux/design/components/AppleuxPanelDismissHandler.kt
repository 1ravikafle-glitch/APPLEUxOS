package com.appleux.design.components

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration

/**
 * iOS panel dismiss gesture: swipe UP closes a fullscreen panel.
 *
 * Both AppleUX panels (Notification Center, Control Center) are fullscreen
 * overlay windows, so by Android's rules they own every touch inside their
 * bounds (see milestone log §27). A swipe that begins on an open panel
 * therefore never reaches the launcher underneath — the panel can only be
 * closed by handling the gesture itself, exactly as iOS does.
 *
 * Background taps are handled separately by the panel root's click listener;
 * this handler covers the swipe case, which never fires onClick.
 *
 * Threshold is touch-slop relative, not a fixed pixel count, so it behaves
 * the same at every density (device, emulator).
 */
class AppleuxPanelDismissHandler(
    private val view: View,
    private val onDismiss: () -> Unit
) : View.OnTouchListener {

    private val touchSlop = ViewConfiguration.get(view.context).scaledTouchSlop

    private var downY = 0f
    private var downX = 0f
    private var tracking = false

    companion object {
        /**
         * Panels are clickable (background tap dismisses) but must not consume
         * touches: children (tiles, notification rows) still need the stream.
         * The listener therefore only observes and always returns false.
         */
        fun attach(view: View, onDismiss: () -> Unit): AppleuxPanelDismissHandler {
            val handler = AppleuxPanelDismissHandler(view, onDismiss)
            view.setOnTouchListener(handler)
            return handler
        }
    }

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                tracking = true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!tracking) return false
                val dy = event.y - downY
                val dx = event.x - downX
                // Upward and mostly-vertical: the iOS close gesture.
                if (dy < 0 && -dy > touchSlop &&
                    kotlin.math.abs(dy) > kotlin.math.abs(dx)
                ) {
                    tracking = false
                    onDismiss()
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> tracking = false
        }
        // Never consume: children (tiles, notification rows) must still work.
        return false
    }
}