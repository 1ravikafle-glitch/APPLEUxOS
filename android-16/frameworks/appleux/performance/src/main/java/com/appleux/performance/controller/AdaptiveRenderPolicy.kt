package com.appleux.performance.controller

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.appleux.performance.quality.AppleuxQualityLevel
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Quality state bridge for component observers.
 *
 * Observers are held weakly and capped, so an Activity or View that forgets to
 * unregister cannot be kept alive by the process-wide policy singleton. Every
 * callback is dispatched on the main looper.
 */
class AdaptiveRenderPolicy private constructor() : AppleuxPerformanceController.PerformanceListener {

    @Volatile
    private var currentQuality: AppleuxQualityLevel = AppleuxQualityLevel.QUALITY_100
    @Volatile
    private var currentScale: Float = 1.0f
    @Volatile
    private var currentMonitoringLevel: MonitoringLevel = MonitoringLevel.LEVEL_0_NORMAL
    @Volatile
    private var blurEnabled: Boolean = true

    private val observers = CopyOnWriteArrayList<WeakReference<QualityObserver>>()

    private val mainHandler = Handler(Looper.getMainLooper())

    interface QualityObserver {
        fun onRenderScaleChanged(scale: Float)
        fun onQualityLevelChanged(level: AppleuxQualityLevel)
        fun onMonitoringLevelChanged(level: MonitoringLevel)
    }

    /**
     * Optional GPU-cost gate, separate from [QualityObserver] so existing
     * consumers keep compiling. SystemUI will consume this to drop
     * blur-behind / RenderEffect under pressure.
     */
    interface EffectsObserver {
        fun onBlurBudgetChanged(enabled: Boolean, quality: AppleuxQualityLevel)
    }

    private val effectsObservers = CopyOnWriteArrayList<WeakReference<EffectsObserver>>()

    /**
     * Strong reference on purpose: the controller holds listeners weakly, so
     * without this the bridge itself would be collected and the blur gate would
     * silently stop updating.
     */
    private val controllerEffectsBridge = object : AppleuxPerformanceController.EffectsListener {
        override fun onEffectsBudgetChanged(enabled: Boolean, quality: AppleuxQualityLevel) {
            setBlurEnabled(enabled, quality)
        }
    }

    init {
        // mirror the process-wide controller so a second process (SystemUI)
        // still sees the same gate without a hard dependency on start().
        // The policy is a process-lifetime singleton, so holding it weakly on
        // the controller side leaks nothing.
        val controller = AppleuxPerformanceController.getInstance(null)
        controller.registerListener(this)
        controller.registerEffectsListener(controllerEffectsBridge)
    }

    override fun onQualityChanged(level: AppleuxQualityLevel) {
        currentQuality = level
        onMain {
            val snapshot = observers.toList()
            for (ref in snapshot) {
                val o = ref.get() ?: continue
                try {
                    o.onQualityLevelChanged(level)
                } catch (e: Exception) {
                    Log.w(TAG, "Observer failed: $o", e)
                }
            }
        }
    }

    override fun onRenderScaleChanged(scale: Float) {
        currentScale = scale
        onMain {
            val snapshot = observers.toList()
            for (ref in snapshot) {
                val o = ref.get() ?: continue
                try {
                    o.onRenderScaleChanged(scale)
                } catch (e: Exception) {
                    Log.w(TAG, "Observer failed: $o", e)
                }
            }
        }
    }

    override fun onMonitoringLevelChanged(level: MonitoringLevel) {
        currentMonitoringLevel = level
        onMain {
            val snapshot = observers.toList()
            for (ref in snapshot) {
                val o = ref.get() ?: continue
                try {
                    o.onMonitoringLevelChanged(level)
                } catch (e: Exception) {
                    Log.w(TAG, "Observer failed: $o", e)
                }
            }
        }
    }

    private fun setBlurEnabled(enabled: Boolean, quality: AppleuxQualityLevel) {
        if (enabled == blurEnabled && quality == currentQuality) return
        blurEnabled = enabled
        currentQuality = quality
        onMain {
            val snapshot = effectsObservers.toList()
            for (ref in snapshot) {
                val o = ref.get() ?: continue
                try {
                    o.onBlurBudgetChanged(enabled, quality)
                } catch (e: Exception) {
                    Log.w(TAG, "Effects observer failed: $o", e)
                }
            }
        }
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
    }

    private fun pruneObservers() {
        val it = observers.iterator()
        while (it.hasNext()) {
            if (it.next().get() == null) it.remove()
        }
    }

    private fun pruneEffectsObservers() {
        val it = effectsObservers.iterator()
        while (it.hasNext()) {
            if (it.next().get() == null) it.remove()
        }
    }

    fun registerObserver(observer: QualityObserver) {
        pruneObservers()
        if (observers.size >= MAX_OBSERVERS) return
        observers.add(WeakReference(observer))
        observer.onRenderScaleChanged(currentScale)
        observer.onQualityLevelChanged(currentQuality)
        observer.onMonitoringLevelChanged(currentMonitoringLevel)
    }

    fun unregisterObserver(observer: QualityObserver) {
        val it = observers.iterator()
        while (it.hasNext()) {
            val ref = it.next()
            val live = ref.get()
            if (live == null || live === observer) it.remove()
        }
    }

    fun registerEffectsObserver(observer: EffectsObserver) {
        pruneEffectsObservers()
        if (effectsObservers.size >= MAX_OBSERVERS) return
        effectsObservers.add(WeakReference(observer))
        observer.onBlurBudgetChanged(blurEnabled, currentQuality)
    }

    fun unregisterEffectsObserver(observer: EffectsObserver) {
        val it = effectsObservers.iterator()
        while (it.hasNext()) {
            val ref = it.next()
            val live = ref.get()
            if (live == null || live === observer) it.remove()
        }
    }

    fun getRenderScale(): Float = currentScale
    fun getQualityLevel(): AppleuxQualityLevel = currentQuality
    fun getMonitoringLevel(): MonitoringLevel = currentMonitoringLevel

    /** True while blur-behind / RenderEffect may be used. */
    fun isBlurEnabled(): Boolean = blurEnabled

    companion object {
        private const val TAG = "AdaptiveRenderPolicy"
        private const val MAX_OBSERVERS = 16

        @Volatile
        private var instance: AdaptiveRenderPolicy? = null

        fun getInstance(): AdaptiveRenderPolicy {
            return instance ?: synchronized(this) {
                instance ?: AdaptiveRenderPolicy().also { instance = it }
            }
        }

        fun isBlurEnabledStatic(): Boolean = getInstance().isBlurEnabled()
    }
}