package com.appleux.performance.integration

import android.content.Context
import com.appleux.performance.controller.AppleuxPerformanceController
import com.appleux.performance.controller.AdaptiveRenderPolicy
import com.appleux.performance.controller.MonitoringLevel
import com.appleux.performance.quality.AppleuxQualityLevel
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Per-component state mapping.
 *
 * Holds no Context and no views. The component map is bounded: a component
 * registering repeatedly can never grow it without limit.
 */
class AppleuxPerformanceService private constructor() : AppleuxPerformanceController.PerformanceListener {

    private val controller: AppleuxPerformanceController =
        AppleuxPerformanceController.getInstance(null)
    private val policy: AdaptiveRenderPolicy = AdaptiveRenderPolicy.getInstance()

    private val componentStates = ConcurrentHashMap<String, ComponentState>()
    private val componentCount = AtomicInteger(0)

    data class ComponentState(
        val name: String,
        var reducedEffects: Boolean = false,
        var reducedResolution: Boolean = false,
        var simplifiedAnimations: Boolean = false,
        var deferredBackground: Boolean = false,
        var blurEnabled: Boolean = true
    )

    private val policyObserver = object : AdaptiveRenderPolicy.QualityObserver {
        override fun onRenderScaleChanged(scale: Float) = onRenderScale(scale)
        override fun onQualityLevelChanged(level: AppleuxQualityLevel) = applyQualityToComponents(level)
        override fun onMonitoringLevelChanged(level: MonitoringLevel) = Unit
    }

    private val effectsObserver = object : AdaptiveRenderPolicy.EffectsObserver {
        override fun onBlurBudgetChanged(enabled: Boolean, quality: AppleuxQualityLevel) {
            applyBlurToComponents(enabled)
        }
    }

    fun initialize() {
        policy.registerObserver(policyObserver)
        policy.registerEffectsObserver(effectsObserver)
        controller.registerListener(this)
        controller.start()
    }

    fun shutdown() {
        controller.unregisterListener(this)
        policy.unregisterObserver(policyObserver)
        policy.unregisterEffectsObserver(effectsObserver)
        controller.stop()
    }

    /** Ignored past [MAX_COMPONENTS] so the map stays bounded. */
    fun registerComponent(name: String) {
        if (componentStates.containsKey(name)) return
        if (componentCount.get() >= MAX_COMPONENTS) return
        if (componentStates.putIfAbsent(name, ComponentState(name)) == null) {
            componentCount.incrementAndGet()
        }
    }

    fun unregisterComponent(name: String) {
        if (componentStates.remove(name) != null) {
            componentCount.decrementAndGet()
        }
    }

    fun getComponentState(name: String): ComponentState? = componentStates[name]

    fun isBlurEnabled(): Boolean = policy.isBlurEnabled()

    private fun applyQualityToComponents(quality: AppleuxQualityLevel) {
        for (entry in componentStates.entries) {
            val original = entry.value
            when (quality) {
                AppleuxQualityLevel.QUALITY_100 -> {
                    original.reducedEffects = false
                    original.reducedResolution = false
                    original.simplifiedAnimations = false
                    original.deferredBackground = false
                }
                AppleuxQualityLevel.QUALITY_95 -> {
                    original.reducedEffects = true
                    original.reducedResolution = false
                    original.simplifiedAnimations = false
                    original.deferredBackground = false
                }
                AppleuxQualityLevel.QUALITY_90 -> {
                    original.reducedEffects = true
                    original.reducedResolution = true
                    original.simplifiedAnimations = true
                    original.deferredBackground = false
                }
                AppleuxQualityLevel.QUALITY_85 -> {
                    original.reducedEffects = true
                    original.reducedResolution = true
                    original.simplifiedAnimations = true
                    original.deferredBackground = true
                }
                else -> {
                    original.reducedEffects = true
                    original.reducedResolution = true
                    original.simplifiedAnimations = true
                    original.deferredBackground = true
                }
            }
        }
    }

    private fun applyBlurToComponents(enabled: Boolean) {
        for (entry in componentStates.entries) {
            entry.value.blurEnabled = enabled
        }
    }

    fun onRenderScale(scale: Float) {
        currentScale = scale
    }

    override fun onQualityChanged(level: AppleuxQualityLevel) = applyQualityToComponents(level)

    override fun onRenderScaleChanged(scale: Float) = onRenderScale(scale)

    override fun onMonitoringLevelChanged(level: MonitoringLevel) = Unit

    companion object {
        private const val MAX_COMPONENTS = 64

        @Volatile
        private var currentScale = 1.0f

        @Volatile
        private var instance: AppleuxPerformanceService? = null

        fun getInstance(context: Context?): AppleuxPerformanceService {
            return instance ?: synchronized(this) {
                instance ?: AppleuxPerformanceService().also { instance = it }
            }
        }
    }
}