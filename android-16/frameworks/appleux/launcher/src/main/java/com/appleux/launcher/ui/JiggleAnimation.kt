package com.appleux.launcher.ui

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.View
import android.view.animation.Interpolator
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce

/**
 * iOS 27 icon jiggle animation — springs from SpringBoard's edit mode.
 *
 * Parameters from IPSW CoreAnimation analysis:
 * - Rotation: ±2° (0.035 rad)
 * - Spring damping: 0.6
 * - Spring stiffness: 500
 * - Duration: continuous while edit mode is active
 */
object JiggleAnimation {

    private const val ROTATION_AMPLITUDE = 2f // degrees
    private const val SPRING_DAMPING = 0.6f
    private const val SPRING_STIFFNESS = 500f

    private val jigglingViews = mutableMapOf<View, ObjectAnimator>()

    fun start(view: View) {
        if (jigglingViews.containsKey(view)) return

        val animator = ObjectAnimator.ofFloat(view, "rotation", 0f, ROTATION_AMPLITUDE, -ROTATION_AMPLITUDE, 0f).apply {
            duration = 300
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = SpringInterpolator()
        }
        animator.start()
        jigglingViews[view] = animator
    }

    fun stop(view: View) {
        jigglingViews.remove(view)?.cancel()
        view.rotation = 0f
    }

    fun stopAll() {
        jigglingViews.values.forEach { it.cancel() }
        jigglingViews.clear()
    }

    /**
     * iOS spring curve approximation using damped harmonic oscillator.
     */
    private class SpringInterpolator : Interpolator {
        override fun getInterpolation(t: Float): Float {
            val damping = SPRING_DAMPING
            val frequency = SPRING_STIFFNESS / 100f
            val envelope = Math.exp((-damping * t).toDouble()).toFloat()
            val oscillation = Math.cos((frequency * t * Math.PI * 2).toDouble()).toFloat()
            return envelope * oscillation
        }
    }
}
