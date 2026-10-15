package com.appleux.launcher.gestures

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.FrameLayout

/**
 * Root container that reports every touch event to a [GestureHandler] without
 * consuming any of them.
 *
 * A transparent clickable overlay was the obvious way to catch launcher-level
 * gestures, but it sits above the grid and dock and therefore swallows every
 * tap, so app icons could never be launched. Overriding
 * [dispatchTouchEvent] observes the stream while leaving child dispatch
 * completely intact.
 */
class GestureHostLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private var gestureHandler: GestureHandler? = null

    fun installGestureHandler(handler: GestureHandler) {
        gestureHandler = handler
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        gestureHandler?.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }
}