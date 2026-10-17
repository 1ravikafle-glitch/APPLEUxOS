package com.appleux.design.components

import android.view.View
import android.view.animation.CycleInterpolator
import android.view.animation.RotateAnimation
import com.appleux.design.runtime.AppleuxRuntimeConfig

/** home-edit-jiggle: iOS icon jiggle applied to any view in edit mode. */
object AppleuxJiggle {
    fun start(v: View, index: Int = 0) {
        val reduce = runCatching {
            AppleuxRuntimeConfig.isReduceMotionEnabled
        }.getOrDefault(false)
        if (reduce) return
        val rot = RotateAnimation(-1.2f, 1.2f).apply {
            duration = 260
            interpolator = CycleInterpolator(1f)
            repeatCount = android.view.animation.Animation.INFINITE
            repeatMode = android.view.animation.Animation.REVERSE
            startOffset = (index % 4) * 60L
            fillAfter = true
        }
        v.startAnimation(rot)
    }
    fun stop(v: View) { v.clearAnimation() }
}
