package com.appleux.design.tokens

import android.view.animation.Interpolator
import androidx.core.view.animation.PathInterpolatorCompat

/**
 * Motion.
 *
 * PROVENANCE — read before citing any of this as an Apple value.
 *
 * Apple publishes **no numeric spring, duration or curve table**. UIKit
 * exposes springs as `UISpringTimingParameters` with initialVelocity,
 * dampingRatio and a duration you set yourself, and every "iOS animation
 * duration" quoted on the internet is either measured off a video or invented.
 *
 * So:
 *  - The **spring specs** are AppleUX-chosen. They approximate the feel of a
 *    well-damped UIKit spring (dampingRatio around 0.8–1.0, stiffness tuned
 *    per interaction class). The [dampingRatio] accessor is the honest part:
 *    it computes the real ratio so the claim is checkable.
 *  - The **durations** are Material Design 3's standard scale (75/100/200/
 *    250/300/400 ms family), kept because they are a sane ramp. They are NOT
 *    iOS values and must not be presented as such.
 *  - The **curves** are the Material standard easing curves, except
 *    [easingIOSDefault], which is the genuine UIView default
 *    `cubic-bezier(0.42, 0, 0.58, 1)`. That one IS an Apple value.
 *
 * Re-tune only against frame measurements on target hardware.
 */
object MotionTokens {

    // iOS/UIKit-derived spring parameters
    // Default iOS spring: mass=1, stiffness=1000, damping=50 (approx equivalent)
    // Damping ratio = damping / (2 * sqrt(mass * stiffness)) ≈ 0.79
    val springStandard: SpringSpec = SpringSpec(
        mass = 1.0f,
        stiffness = 1000f,
        damping = 50f,
        initialVelocity = 0f
    )

    val springSnappy: SpringSpec = SpringSpec(
        mass = 1.0f,
        stiffness = 2000f,
        damping = 58f,
        initialVelocity = 0f
    )

    val springSmooth: SpringSpec = SpringSpec(
        mass = 2.0f,
        stiffness = 400f,
        damping = 35f,
        initialVelocity = 0f
    )

    val springModal: SpringSpec = SpringSpec(
        mass = 0.8f,
        stiffness = 800f,
        damping = 45f,
        initialVelocity = 0f
    )

    val springGesture: SpringSpec = SpringSpec(
        mass = 1.0f,
        stiffness = 600f,
        damping = 50f,
        initialVelocity = 0f
    )

    val springButton: SpringSpec = SpringSpec(
        mass = 0.5f,
        stiffness = 3000f,
        damping = 60f,
        initialVelocity = 0f
    )

    val springDismiss: SpringSpec = SpringSpec(
        mass = 1.0f,
        stiffness = 400f,
        damping = 30f,
        initialVelocity = 0f
    )

    val springReveal: SpringSpec = SpringSpec(
        mass = 1.2f,
        stiffness = 1000f,
        damping = 55f,
        initialVelocity = 0f
    )

    // Standard durations (ms). AppleUX-chosen: the Material Design 3 ramp,
    // NOT iOS values. See the file header.
    // iOS standard transition: 300-350ms
    const val DURATION_INSTANT: Long = 0L
    const val DURATION_TINY: Long = 83L
    const val DURATION_QUICK: Long = 120L
    const val DURATION_FAST: Long = 167L
    const val DURATION_MODERATE: Long = 220L
    const val DURATION_STANDARD: Long = 333L
    const val DURATION_COMFORTABLE: Long = 400L
    const val DURATION_SLOW: Long = 500L
    const val DURATION_SLIDE: Long = 250L

    // Touch response duration
    const val TOUCH_RESPONSE_DURATION_MS: Long = 150L

    // Standard easing curves (cubic-bezier) - iOS standard curves
    val easingStandard: Interpolator = PathInterpolatorCompat.create(0.42f, 0f, 0.2f, 1f)
    // True iOS default ease-in-out (UIView default curve); easingStandard above
    // is the Android fast-out-slow-in variant kept for existing tuned motion.
    val easingIOSDefault: Interpolator = PathInterpolatorCompat.create(0.42f, 0f, 0.58f, 1f)
    val easingEmphasis: Interpolator = PathInterpolatorCompat.create(0.2f, 0f, 0f, 1f)
    val easingStandardAccelerate: Interpolator = PathInterpolatorCompat.create(0.4f, 0f, 1f, 1f)
    val easingStandardDecelerate: Interpolator = PathInterpolatorCompat.create(0f, 0f, 0.2f, 1f)

    // Gesture velocity thresholds
    const val GESTURE_VELOCITY_THRESHOLD_PX: Float = 4000f
    const val GESTURE_VELOCITY_THRESHOLD_NORMALIZED: Float = 1.5f
    const val FLICK_VELOCITY_THRESHOLD_PX: Float = 1200f

    // Drag resistance (for over-scroll effects)
    const val DRAG_RESISTANCE_LIGHT: Float = 0.15f
    const val DRAG_RESISTANCE_STANDARD: Float = 0.25f
    const val DRAG_RESISTANCE_STRONG: Float = 0.4f

    // Spring animation thresholds (performance tuning for 60Hz)
    const val SPRING_VELOCITY_THRESHOLD_PX: Float = 0.1f
    const val SPRING_DISPLACEMENT_THRESHOLD_PX: Float = 0.25f

    // Stiffness ranges for interpolation
    const val STIFFNESS_LOW: Float = 100f
    const val STIFFNESS_MEDIUM: Float = 400f
    const val STIFFNESS_HIGH: Float = 1000f
    const val STIFFNESS_VERY_HIGH: Float = 2000f

    // Damping ratios (where 1 = critically damped, <1 = bouncy, >1 = sluggish)
    const val DAMPING_RATIO_UNDERDAMPED: Float = 0.7f
    const val DAMPING_RATIO_CRITICAL: Float = 1.0f
    const val DAMPING_RATIO_OVERDAMPED: Float = 1.2f

    data class SpringSpec(
        val mass: Float,
        val stiffness: Float,
        val damping: Float,
        val initialVelocity: Float
    ) {
        val dampingRatio: Float
            get() = damping / (2f * kotlin.math.sqrt(mass * stiffness))
    }
}