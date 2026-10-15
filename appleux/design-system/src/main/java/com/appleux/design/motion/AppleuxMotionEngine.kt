package com.appleux.design.motion

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.view.Choreographer
import android.view.View
import android.view.animation.Interpolator
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.appleux.design.tokens.MotionTokens
import com.appleux.design.tokens.MotionTokens.SpringSpec

class AppleuxMotionEngine(
    private val context: Context
) : Choreographer.FrameCallback {

    private val choreographer: Choreographer = Choreographer.getInstance()
    private val activeAnimations: MutableMap<Int, ActiveAnimator> = mutableMapOf()

    @Volatile
    private var framePending = false

    /**
     * Frame loop.
     *
     * The previous version posted a frame callback from its own constructor
     * and unconditionally re-posted in every `doFrame` — a wakeup at every
     * vsync for the whole life of the process, whether or not anything was
     * animating. On a device budgeted for 3 GB that is a permanent tax.
     *
     * The loop now runs only while [activeAnimations] is non-empty:
     * [ensureFrameCallback] starts it on the first animation, and `doFrame`
     * stops it again once the last one finishes. The cleanup pass was also
     * unreachable before, because `framePending` was only ever set by
     * [ensureFrameCallback], which nothing called — so the map grew by one
     * dead entry per animation, forever.
     */
    override fun doFrame(frameTimeNanos: Long) {
        framePending = false
        val iterator = activeAnimations.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value.isFinished) {
                entry.value.cleanup()
                iterator.remove()
            }
        }
        if (activeAnimations.isNotEmpty()) ensureFrameCallback()
    }

    /** Starts the frame loop if it is not already running. */
    private fun ensureFrameCallback() {
        if (framePending) return
        framePending = true
        choreographer.postFrameCallback(this)
    }

    fun createSpringAnimation(
        view: View,
        property: SpringAnimation,
        spec: SpringSpec,
        startValue: Float,
        endValue: Float,
        velocity: Float = 0f
    ): SpringAnimation {
        val spring = SpringForce(endValue)
        spring.setSpring(stiffness = spec.stiffness, damping = spec.damping, mass = spec.mass)

        property.setSpring(spring)
        property.setStartVelocity(velocity)
        property.start()

        val animator = ActiveAnimator(property) {
            activeAnimations.remove(view.id)
        }
        activeAnimations[view.id] = animator
        ensureFrameCallback()

        return property
    }

    fun createSpringAnimationFloat(
        target: Any,
        propertyName: String,
        spec: SpringSpec,
        startValue: Float,
        endValue: Float,
        velocity: Float = 0f
    ): ValueAnimator {
        val duration = ((endValue - startValue) / spec.stiffness * 1000).toLong().coerceIn(80, 500)

        val animator = ValueAnimator.ofFloat(startValue, endValue).apply {
            this.duration = duration
            interpolator = SpringInterpolator(spec)
            addUpdateListener { animation ->
                val value = animation.animatedValue as Float
                try {
                    val method = target.javaClass.getMethod("set$propertyName", Float::class.javaPrimitiveType)
                    method.invoke(target, value)
                } catch (e: Exception) {
                    // Silent fail - target doesn't support this property
                }
            }
        }

        val active = ActiveAnimator(null) {
            activeAnimations.remove(target.hashCode())
        }
        activeAnimations[target.hashCode()] = active
        ensureFrameCallback()

        return animator
    }

    fun createInterpolatedAnimation(
        target: Any,
        propertyName: String,
        duration: Long,
        interpolator: Interpolator,
        startValue: Float,
        endValue: Float
    ): ValueAnimator {
        val animator = ValueAnimator.ofFloat(startValue, endValue).apply {
            this.duration = duration
            this.interpolator = interpolator
            addUpdateListener { animation ->
                val value = animation.animatedValue as Float
                try {
                    val method = target.javaClass.getMethod("set$propertyName", Float::class.javaPrimitiveType)
                    method.invoke(target, value)
                } catch (e: Exception) {
                    // Silent fail
                }
            }
        }

        val active = ActiveAnimator(null) {
            activeAnimations.remove(target.hashCode())
        }
        activeAnimations[target.hashCode()] = active
        ensureFrameCallback()

        return animator
    }

    fun cancelAllFor(target: Any) {
        val id = target.hashCode()
        activeAnimations.remove(id)?.cleanup()
        if (target is View) {
            activeAnimations.remove(target.id)?.cleanup()
        }
    }

    fun animatePresence(
        target: Any,
        propertyName: String,
        duration: Long,
        interpolator: Interpolator = MotionTokens.easingStandard,
        startValue: Float,
        endValue: Float,
        delay: Long = 0,
        onEnd: (() -> Unit)? = null
    ) {
        val animator = createInterpolatedAnimation(
            target, propertyName, duration, interpolator, startValue, endValue
        )
        animator.startDelay = delay
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                onEnd?.invoke()
            }
        })
        animator.start()
    }

    fun animateSpring(
        target: Any,
        propertyName: String,
        spec: SpringSpec,
        startValue: Float,
        endValue: Float,
        velocity: Float = 0f,
        onEnd: (() -> Unit)? = null
    ) {
        val animator = createSpringAnimationFloat(
            target, propertyName, spec, startValue, endValue, velocity
        )
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                onEnd?.invoke()
            }
        })
        animator.start()
    }

    data class ActiveAnimator(
        val animation: SpringAnimation?,
        val cleanup: () -> Unit
    ) {
        /**
         * A null [animation] is a reflection-driven ValueAnimator, which this
         * engine does not hold a reference to; the frame loop owns its
         * lifetime and the record is done the moment it is registered.
         *
         * The previous predicate was
         * `animation?.isRunning != true && animation != null`, which is
         * **always false** for the null case — so every reflected animation
         * leaked a map entry for the life of the process.
         */
        val isFinished: Boolean
            get() = animation?.isRunning != true
    }
    companion object {
        @Volatile
        private var instance: AppleuxMotionEngine? = null

        @JvmStatic
        fun getInstance(context: Context): AppleuxMotionEngine {
            return instance ?: synchronized(this) {
                instance ?: AppleuxMotionEngine(context.applicationContext).also { instance = it }
            }
        }
    }
}

class SpringInterpolator(private val spec: MotionTokens.SpringSpec) : Interpolator {
    override fun getInterpolation(input: Float): Float {
        val t = input.coerceIn(0f, 1f)
        val x = 1f - t
        val result = 1f - (x * x * x * x * x * x)
        return if (t < 0.12f) {
            result * spec.damping / 30f
        } else {
            result
        }
    }
}

private fun SpringForce.setSpring(stiffness: Float, damping: Float, mass: Float) {
    this.stiffness = stiffness
    this.dampingRatio = damping / (2f * Math.sqrt(stiffness * mass.toDouble()).toFloat())
}
