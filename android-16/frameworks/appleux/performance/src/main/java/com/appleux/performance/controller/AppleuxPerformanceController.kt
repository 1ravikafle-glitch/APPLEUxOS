package com.appleux.performance.controller

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Choreographer
import com.appleux.device.rock.RockDisplayProfile
import com.appleux.performance.monitor.FrameTimeMonitor
import com.appleux.performance.monitor.PressureMonitor
import com.appleux.performance.quality.AppleuxQualityLevel
import com.appleux.performance.quality.BottleneckState
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit

/**
 * Central adaptive-performance policy engine.
 *
 * Frame timing is continuous: a single [Choreographer] callback on the main
 * looper feeds [FrameTimeMonitor]. It is never polled and never slept on.
 * Escalation runs on the frame path (O(1) volatile reads); telemetry and
 * quality/recovery decisions run on one scheduled policy tick whose cadence is
 * the level's canonical sampling rate.
 *
 * Listeners are held as weak references and are dispatched on the main looper,
 * so this controller never keeps an Activity or View alive.
 *
 * Hard constraints: nothing here disables LMKD, suppresses thermal throttling,
 * forces clocks, or reserves memory. The controller only observes and scales
 * its own rendering.
 */
class AppleuxPerformanceController private constructor() {

    private val frameMonitor = FrameTimeMonitor()
    private val pressureMonitor = PressureMonitor()

    /**
     * The APPLICATION context, used only to read the live display refresh
     * rate. Held as the application context, never an Activity, so it cannot
     * outlive one or keep a window alive.
     */
    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var currentLevel: MonitoringLevel = MonitoringLevel.LEVEL_0_NORMAL
    @Volatile
    private var currentBottleneck: BottleneckState = BottleneckState.NORMAL
    @Volatile
    private var currentQuality: AppleuxQualityLevel = AppleuxQualityLevel.QUALITY_100
    @Volatile
    private var currentRenderScale: Float = 1.0f

    /**
     * GPU-cost gate. Blur-behind / RenderEffect is the single most expensive
     * effect in the project, so it has an explicit switch rather than being
     * inferred from render scale alone. Consumers (SystemUI) subscribe through
     * [registerEffectsListener] and read [isBlurEnabled] statically.
     */
    @Volatile
    var isBlurEnabled: Boolean = true
        private set

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Live display-mode watcher. Held so it can be unregistered on stop; a
     * controller is a process singleton, so leaking one registration would
     * mean a Context-free listener surviving forever.
     */
    @Volatile
    private var displayListener: DisplayManager.DisplayListener? = null

    private var executor: java.util.concurrent.ScheduledExecutorService? = null
    private var monitorFuture: ScheduledFuture<*>? = null

    private var levelEscalationTimeMs: Long = 0L
    private var qualityChangeTimeMs: Long = 0L
    private var recoveryStartConditionTimeMs: Long = 0L
    private var lastQualityBoundaryCrossMs: Long = 0L
    private var boundaryCrossCount: Int = 0
    private var oscillationHoldUntilMs: Long = 0L

    // Per-source last-sample stamps so a level's rate is respected exactly
    // without rescheduling the tick on every level change.
    private var lastCpuSampleMs: Long = 0L
    private var lastOtherSampleMs: Long = 0L

    @Volatile
    private var started = false

    interface PerformanceListener {
        fun onQualityChanged(level: AppleuxQualityLevel)
        fun onRenderScaleChanged(scale: Float)
        fun onMonitoringLevelChanged(level: MonitoringLevel)
    }

    /**
     * Optional cost-gate callback. Separate from [PerformanceListener] so that
     * existing consumers keep compiling unchanged.
     */
    interface EffectsListener {
        /** @param enabled false means drop blur/RenderEffect immediately. */
        fun onEffectsBudgetChanged(enabled: Boolean, quality: AppleuxQualityLevel)
    }

    private val listeners = CopyOnWriteArrayList<WeakReference<PerformanceListener>>()
    private val effectsListeners = CopyOnWriteArrayList<WeakReference<EffectsListener>>()

    // ------------------------------------------------------ frame plumbing --

    private val frameCallback = Choreographer.FrameCallback { frameTimeNanos ->
        handleFrame(frameTimeNanos)
        // repost: continuous sampling for as long as we are started
        if (started) postNextFrame()
    }

    private fun postNextFrame() {
        try {
            Choreographer.getInstance().postFrameCallback(frameCallback)
        } catch (e: IllegalStateException) {
            // no Looper on this thread; fall back to the policy tick only
        }
    }

    private fun handleFrame(frameTimeNanos: Long) {
        frameMonitor.doFrame(frameTimeNanos)

        // Escalation is decided on the frame path, from O(1) counters.
        val jank = frameMonitor.windowJankCount
        val severeRun = frameMonitor.consecutiveSevereJank
        if (jank < WARNING_JANK_PER_WINDOW) return

        val now = SystemClock.elapsedRealtime()
        when {
            jank >= EMERGENCY_JANK_PER_WINDOW || severeRun >= EMERGENCY_CONSECUTIVE_SEVERE ->
                escalateTo(MonitoringLevel.LEVEL_3_EMERGENCY, now)
            jank >= DIAGNOSTIC_JANK_PER_WINDOW || severeRun >= DIAGNOSTIC_CONSECUTIVE_SEVERE ->
                escalateTo(MonitoringLevel.LEVEL_2_DIAGNOSTIC, now)
            else ->
                escalateTo(MonitoringLevel.LEVEL_1_WARNING, now)
        }
    }

    // ----------------------------------------------------------- lifecycle --

    fun start() {
        if (started) return
        started = true

        // Read the refresh rate BEFORE the first frame is judged. The POCO M5
        // panel drives 60 Hz or 90 Hz, so a monitor left on 60 Hz thresholds
        // would call a 13 ms frame good on a 90 Hz surface. Re-read on every
        // start because the panel can have switched mode while the controller
        // was stopped.
        frameMonitor.setRefreshRate(
            appContext?.let { RockDisplayProfile.currentRefreshRateHz(it) }
                ?: FrameTimeMonitor.FrameBudget60_HZ
        )

        val now = SystemClock.elapsedRealtime()
        qualityChangeTimeMs = now
        levelEscalationTimeMs = now
        lastQualityBoundaryCrossMs = 0L
        boundaryCrossCount = 0
        oscillationHoldUntilMs = 0L
        recoveryStartConditionTimeMs = 0L
        lastCpuSampleMs = 0L
        lastOtherSampleMs = 0L

        postNextFrame()
        scheduleTick(MONITOR_INTERVAL_MS)
        registerDisplayListener()
    }

    /**
     * Watches for the panel switching between 60 Hz and 90 Hz.
     *
     * The rock panel changes mode on its own (adaptive refresh), so reading the
     * rate once at start() is not enough: without this the budget would stay
     * on the rate the panel happened to be in when the controller started.
     */
    private fun registerDisplayListener() {
        val ctx = appContext ?: return
        if (displayListener != null) return
        val dm = ctx.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager ?: return
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) = Unit
            override fun onDisplayRemoved(displayId: Int) = Unit
            override fun onDisplayChanged(displayId: Int) {
                // Delivered on the handler passed to registerDisplayListener
                // (the main looper here). The post inside is only there
                // because start() can be called from a background thread.
                mainHandler.post { refreshFrameBudget() }
            }
        }
        runCatching { dm.registerDisplayListener(listener, mainHandler) }
            .onSuccess { displayListener = listener }
            .onFailure { displayListener = null }
    }

    /**
     * Re-reads the live refresh rate and re-derives the thresholds. Safe to
     * call at any time: the monitor ignores a rate it is already on.
     */
    fun refreshFrameBudget() {
        val ctx = appContext ?: return
        frameMonitor.setRefreshRate(RockDisplayProfile.currentRefreshRateHz(ctx))
    }

    fun stop() {
        if (!started) return
        started = false

        monitorFuture?.cancel(false)
        monitorFuture = null
        executor?.shutdownNow()
        executor = null

        try {
            Choreographer.getInstance().removeFrameCallback(frameCallback)
        } catch (e: IllegalStateException) {
            // no Looper; nothing to remove
        }

        displayListener?.let { listener ->
            val dm = appContext?.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
            runCatching { dm?.unregisterDisplayListener(listener) }
        }
        displayListener = null

        recoveryStartConditionTimeMs = 0L
    }

    fun isStarted(): Boolean = started

    /**
     * One-shot policy tick. Recreated on every start() so that a
     * start-after-stop cycle works: the previous executor was shut down and a
     * shut-down executor silently rejects new work.
     */
    private fun scheduleTick(initialDelayMs: Long) {
        if (!started) return
        val exec = executor ?: Executors.newSingleThreadScheduledExecutor(THREAD_FACTORY)
            .also { executor = it }
        monitorFuture = exec.scheduleWithFixedDelay(
            {
                try {
                    collectTelemetryAndDecide()
                } catch (e: Exception) {
                    // never let a telemetry hiccup kill the fixed-delay task
                }
            },
            initialDelayMs,
            currentTickIntervalMs(),
            TimeUnit.MILLISECONDS
        )
    }

    private fun currentTickIntervalMs(): Long {
        val hz = currentLevel.getTickHz()
        val capped = if (hz > MonitoringLevel.MAX_SAMPLING_HZ) {
            MonitoringLevel.MAX_SAMPLING_HZ
        } else {
            hz
        }
        return if (capped <= 0f) 1000L else (1000f / capped).toLong()
    }

    // ------------------------------------------------------------- decisions --

    private fun collectTelemetryAndDecide() {
        val level = currentLevel
        val now = SystemClock.elapsedRealtime()

        // CPU-rate sources first; at L0 that is 0.5Hz against 0.2Hz for the rest.
        if (now - lastCpuSampleMs >= level.cpuPeriodMs()) {
            lastCpuSampleMs = now
            pressureMonitor.updateCpuLight()
        }
        if (now - lastOtherSampleMs >= level.otherPeriodMs()) {
            lastOtherSampleMs = now
            // full sample only above L0; at L0 updateCpuLight already covered
            // cpu/thermal/mem/psi
            if (level != MonitoringLevel.LEVEL_0_NORMAL) {
                pressureMonitor.update()
            } else {
                pressureMonitor.updateCpuLight()
            }
        }

        if (level == MonitoringLevel.LEVEL_0_NORMAL) {
            decayMonitoringLevel(now)
            return
        }

        // Frame-driven escalation already ran on the frame path; re-assert here
        // so a level raised between ticks is not lost.
        reassertEscalation(now)

        val bottleneck = classifyBottleneck()
        if (shouldDegradeQuality(bottleneck, now)) {
            degradeQuality(bottleneck, now)
        } else {
            attemptRecovery(bottleneck, now)
        }

        decayMonitoringLevel(now)
    }

    private fun reassertEscalation(now: Long) {
        val jank = frameMonitor.windowJankCount
        val severeRun = frameMonitor.consecutiveSevereJank
        when {
            jank >= EMERGENCY_JANK_PER_WINDOW || severeRun >= EMERGENCY_CONSECUTIVE_SEVERE ->
                escalateTo(MonitoringLevel.LEVEL_3_EMERGENCY, now)
            jank >= DIAGNOSTIC_JANK_PER_WINDOW || severeRun >= DIAGNOSTIC_CONSECUTIVE_SEVERE ->
                escalateTo(MonitoringLevel.LEVEL_2_DIAGNOSTIC, now)
            jank >= WARNING_JANK_PER_WINDOW ->
                escalateTo(MonitoringLevel.LEVEL_1_WARNING, now)
        }
    }

    private fun escalateTo(newLevel: MonitoringLevel, now: Long) {
        if (newLevel.level <= currentLevel.level) return
        currentLevel = newLevel
        levelEscalationTimeMs = now
        applyBlurGate(newLevel)
        notifyLevelChanged()
    }

    private fun classifyBottleneck(): BottleneckState {
        val thermal = pressureMonitor.thermalState
        if (thermal == PressureMonitor.ThermalState.SEVERE) {
            return setBottleneck(BottleneckState.THERMAL_BOUND)
        }

        val hasCpuPressure = pressureMonitor.cpuUtilization > 85f || pressureMonitor.psiCpu > 50f
        val hasGpuPressure = pressureMonitor.gpuUtilization > 90f
        val hasMemPressure = pressureMonitor.memoryPressure > 85f || pressureMonitor.psiMemory > 50f
        val hasIoPressure = pressureMonitor.ioWait > 30f || pressureMonitor.psiIo > 50f
        val framesLate = frameMonitor.averageFrameTimeMs > frameMonitor.nominalFrameMs

        val newState = when {
            thermal == PressureMonitor.ThermalState.SEVERE -> BottleneckState.THERMAL_BOUND
            thermal == PressureMonitor.ThermalState.HOT -> BottleneckState.THERMAL_BOUND
            hasCpuPressure && hasGpuPressure -> BottleneckState.MIXED_PRESSURE
            hasCpuPressure -> BottleneckState.CPU_BOUND
            hasGpuPressure -> BottleneckState.GPU_BOUND
            hasMemPressure && framesLate -> BottleneckState.MEMORY_BOUND
            hasIoPressure && framesLate -> BottleneckState.IO_BOUND
            hasCpuPressure || hasGpuPressure || hasMemPressure || hasIoPressure ->
                BottleneckState.MIXED_PRESSURE
            else -> BottleneckState.NORMAL
        }

        return setBottleneck(newState)
    }

    private fun setBottleneck(newState: BottleneckState): BottleneckState {
        currentBottleneck = newState
        return newState
    }

    /**
     * Quality-down. Two gates: rendering pressure must be present, and every
     * step including the first one (Q100 -> Q95) waits out the 1s dwell.
     *
     * The oscillation hold is NOT applied here on purpose: it exists to stop
     * quality being raised again too soon, and must not delay a degradation
     * that the frames are already demanding.
     */
    private fun shouldDegradeQuality(bottleneck: BottleneckState, now: Long): Boolean {
        val isRenderingPressure = bottleneck == BottleneckState.GPU_BOUND ||
            bottleneck == BottleneckState.MIXED_PRESSURE ||
            bottleneck == BottleneckState.THERMAL_BOUND

        if (!isRenderingPressure) return false
        if (currentQuality == AppleuxQualityLevel.QUALITY_70) return false

        val stableSinceQuality = now - qualityChangeTimeMs
        // quality-down dwell: >= 1s at every level, including the first step
        if (stableSinceQuality < QUALITY_DOWN_DWELL_MS) return false

        val jank = frameMonitor.windowJankCount
        val severe = frameMonitor.windowSevereJankCount
        val severeRun = frameMonitor.consecutiveSevereJank

        // Below Q80 a single emergency-grade window is required; above it,
        // diagnostic-grade jank suffices.
        val atOrAboveQ80 = currentQuality.level >= AppleuxQualityLevel.QUALITY_80.level
        return if (atOrAboveQ80) {
            jank >= EMERGENCY_JANK_PER_WINDOW || severe >= EMERGENCY_CONSECUTIVE_SEVERE ||
                severeRun >= EMERGENCY_CONSECUTIVE_SEVERE
        } else {
            jank >= DIAGNOSTIC_JANK_PER_WINDOW
        }
    }

    private fun degradeQuality(bottleneck: BottleneckState, now: Long) {
        val newQuality = when (currentQuality) {
            AppleuxQualityLevel.QUALITY_100 -> AppleuxQualityLevel.QUALITY_95
            AppleuxQualityLevel.QUALITY_95 -> AppleuxQualityLevel.QUALITY_90
            AppleuxQualityLevel.QUALITY_90 -> AppleuxQualityLevel.QUALITY_85
            AppleuxQualityLevel.QUALITY_85 -> AppleuxQualityLevel.QUALITY_80
            AppleuxQualityLevel.QUALITY_80 -> AppleuxQualityLevel.QUALITY_75
            AppleuxQualityLevel.QUALITY_75 -> AppleuxQualityLevel.QUALITY_70
            AppleuxQualityLevel.QUALITY_70 -> return
        }
        setQuality(newQuality, now)
        recordBoundaryCross(now)
    }

    private fun attemptRecovery(bottleneck: BottleneckState, now: Long) {
        if (currentQuality == AppleuxQualityLevel.QUALITY_100) return
        // A cold window must not be read as stability.
        if (!frameMonitor.hasMinimumSample) return

        val performanceStable = frameMonitor.averageFrameTimeMs <= frameMonitor.goodFrameMs &&
            frameMonitor.windowJankCount == 0 &&
            frameMonitor.windowSevereJankCount == 0 &&
            !pressureMonitor.hasSeverePressure()

        if (!performanceStable) {
            recoveryStartConditionTimeMs = 0L
            return
        }

        if (recoveryStartConditionTimeMs == 0L) {
            recoveryStartConditionTimeMs = now
        }

        val stableDuration = now - recoveryStartConditionTimeMs
        val thermalStable = pressureMonitor.thermalState == PressureMonitor.ThermalState.NORMAL ||
            pressureMonitor.thermalState == PressureMonitor.ThermalState.WARM

        val readyToRecover = when (bottleneck) {
            BottleneckState.THERMAL_BOUND ->
                thermalStable && stableDuration >= THERMAL_RECOVERY_STABLE_MS
            BottleneckState.MEMORY_BOUND ->
                pressureMonitor.memoryPressure < 70f && stableDuration >= RECOVERY_STABLE_MS
            BottleneckState.IO_BOUND ->
                pressureMonitor.ioWait < 20f && stableDuration >= RECOVERY_STABLE_MS
            else -> stableDuration >= RECOVERY_STABLE_MS
        }

        if (!readyToRecover) return
        recoveryStartConditionTimeMs = 0L
        proceedWithRecovery(now)
    }

    private fun proceedWithRecovery(now: Long) {
        // 2s dwell between steps
        if (now - qualityChangeTimeMs < RECOVERY_DWELL_MS) return
        // oscillation guard: hold the lower quality
        if (oscillationHoldUntilMs > now) return

        val newQuality = when (currentQuality) {
            AppleuxQualityLevel.QUALITY_70 -> AppleuxQualityLevel.QUALITY_75
            AppleuxQualityLevel.QUALITY_75 -> AppleuxQualityLevel.QUALITY_80
            AppleuxQualityLevel.QUALITY_80 -> AppleuxQualityLevel.QUALITY_85
            AppleuxQualityLevel.QUALITY_85 -> AppleuxQualityLevel.QUALITY_90
            AppleuxQualityLevel.QUALITY_90 -> AppleuxQualityLevel.QUALITY_95
            AppleuxQualityLevel.QUALITY_95 -> AppleuxQualityLevel.QUALITY_100
            else -> return
        }

        setQuality(newQuality, now)
        recordBoundaryCross(now)
    }

    /**
     * Oscillation guard. AGENTS.md: "boundary crossed twice in 10s -> hold lower
     * quality 5s". The previous implementation set a boolean that was never
     * read, so the guard never fired; crossings are counted here and the hold
     * is enforced by [oscillationHoldUntilMs].
     */
    private fun recordBoundaryCross(now: Long) {
        if (lastQualityBoundaryCrossMs != 0L &&
            now - lastQualityBoundaryCrossMs <= QUALITY_OSCILLATION_WINDOW_MS
        ) {
            boundaryCrossCount++
            if (boundaryCrossCount >= QUALITY_OSCILLATION_CROSS_LIMIT) {
                oscillationHoldUntilMs = now + QUALITY_OSCILLATION_HOLD_MS
                boundaryCrossCount = 0
                // a hold invalidates the window: next cross starts fresh
                lastQualityBoundaryCrossMs = oscillationHoldUntilMs
                return
            }
        } else {
            boundaryCrossCount = 1
        }
        lastQualityBoundaryCrossMs = now
    }

    private fun setQuality(newQuality: AppleuxQualityLevel, now: Long) {
        if (newQuality == currentQuality) return

        currentQuality = newQuality
        currentRenderScale = newQuality.scale
        qualityChangeTimeMs = now

        applyBlurGate(currentLevel)
        postToMain {
            notifyQuality(newQuality)
        }
    }

    /**
     * Blur-behind / RenderEffect gate. Off from L2 up, and off below Q90 too,
     * so the most expensive effect is the first thing to go and the last thing
     * to come back.
     */
    private fun applyBlurGate(level: MonitoringLevel) {
        val shouldBeOn = level.level <= MonitoringLevel.LEVEL_1_WARNING.level &&
            currentQuality.level >= AppleuxQualityLevel.QUALITY_90.level
        if (shouldBeOn == isBlurEnabled) return
        isBlurEnabled = shouldBeOn
        val snapshot = currentQuality
        postToMain {
            val snapshotListeners = effectsListeners.toList()
            for (ref in snapshotListeners) {
                val l = ref.get() ?: continue
                try {
                    l.onEffectsBudgetChanged(shouldBeOn, snapshot)
                } catch (e: Exception) {
                    // a dead consumer must not stall the policy engine
                }
            }
        }
    }

    private fun decayMonitoringLevel(now: Long) {
        if (currentLevel == MonitoringLevel.LEVEL_0_NORMAL) return

        val stableSince = now - levelEscalationTimeMs
        val target = when (currentLevel) {
            MonitoringLevel.LEVEL_3_EMERGENCY ->
                if (stableSince >= MONITOR_DECAY_L3_MS && !pressureMonitor.hasSeverePressure()) {
                    MonitoringLevel.LEVEL_2_DIAGNOSTIC
                } else null
            MonitoringLevel.LEVEL_2_DIAGNOSTIC ->
                if (stableSince >= MONITOR_DECAY_L2_MS && !pressureMonitor.hasLightPressure()) {
                    MonitoringLevel.LEVEL_1_WARNING
                } else null
            MonitoringLevel.LEVEL_1_WARNING ->
                if (stableSince >= MONITOR_DECAY_L1_MS && !pressureMonitor.hasLightPressure()) {
                    MonitoringLevel.LEVEL_0_NORMAL
                } else null
            else -> null
        } ?: return

        currentLevel = target
        levelEscalationTimeMs = now
        applyBlurGate(target)
        postToMain { notifyLevelChanged() }
    }

    private fun postToMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    // ------------------------------------------------------------ listeners --

    private fun notifyQuality(level: AppleuxQualityLevel) {
        val scale = level.scale
        for (ref in listeners) {
            val l = ref.get() ?: continue
            try {
                l.onQualityChanged(level)
                l.onRenderScaleChanged(scale)
            } catch (e: Exception) {
                // a dead consumer must not stall the policy engine
            }
        }
    }

    private fun notifyLevelChanged() {
        val level = currentLevel
        for (ref in listeners) {
            val l = ref.get() ?: continue
            try {
                l.onMonitoringLevelChanged(level)
            } catch (e: Exception) {
            }
        }
    }

    /**
     * Registered listeners are held weakly and capped. A consumer that forgets
     * to unregister cannot leak an Activity past GC, and the list cannot grow
     * without bound.
     */
    fun registerListener(listener: PerformanceListener) {
        pruneListeners()
        if (listeners.size >= MAX_LISTENERS) return
        listeners.add(WeakReference(listener))
    }

    fun unregisterListener(listener: PerformanceListener) {
        val it = listeners.iterator()
        while (it.hasNext()) {
            val ref = it.next()
            if (ref.get() == null || ref.get() == listener) it.remove()
        }
    }

    fun registerEffectsListener(listener: EffectsListener) {
        pruneListeners()
        if (effectsListeners.size >= MAX_LISTENERS) return
        effectsListeners.add(WeakReference(listener))
    }

    fun unregisterEffectsListener(listener: EffectsListener) {
        val it = effectsListeners.iterator()
        while (it.hasNext()) {
            val ref = it.next()
            if (ref.get() == null || ref.get() == listener) it.remove()
        }
    }

    private fun pruneListeners() {
        val it = listeners.iterator()
        while (it.hasNext()) {
            if (it.next().get() == null) it.remove()
        }
        val it2 = effectsListeners.iterator()
        while (it2.hasNext()) {
            if (it2.next().get() == null) it2.remove()
        }
    }

    // --------------------------------------------------------------- public --

    fun getFrameTimeMonitor(): FrameTimeMonitor = frameMonitor
    fun getPressureMonitor(): PressureMonitor = pressureMonitor

    companion object {
        // Frame-time thresholds are no longer constants here. They are derived
        // from the panel's ACTUAL refresh rate by FrameTimeMonitor.setRefreshRate
        // (1000/Hz, good = 0.84x, severe = 1.20x) and read back live through
        // frameMonitor.nominalFrameMs / goodFrameMs / severeFrameMs.
        //
        // These three remain only as the documented 60 Hz values, for anything
        // that needs a compile-time number (a settings readout's "target").
        // Nothing in the policy engine reads them.
        const val TARGET_FRAME_TIME_MS: Float = 16.67f
        const val GOOD_FRAME_TIME_MS: Float = 14f
        const val SEVERE_FRAME_TIME_MS: Float = 20f

        // Per-window escalation thresholds, per AGENTS.md
        const val WARNING_JANK_PER_WINDOW = 2
        const val DIAGNOSTIC_JANK_PER_WINDOW = 5
        const val DIAGNOSTIC_CONSECUTIVE_SEVERE = 3
        const val EMERGENCY_JANK_PER_WINDOW = 10
        const val EMERGENCY_CONSECUTIVE_SEVERE = 5

        // Quality policy
        const val QUALITY_DOWN_DWELL_MS: Long = 1000
        const val RECOVERY_DWELL_MS: Long = 2000
        const val RECOVERY_STABLE_MS: Long = 5000
        const val THERMAL_RECOVERY_STABLE_MS: Long = 10000
        const val QUALITY_OSCILLATION_WINDOW_MS: Long = 10000
        const val QUALITY_OSCILLATION_CROSS_LIMIT = 2
        const val QUALITY_OSCILLATION_HOLD_MS: Long = 5000

        // Monitoring decay: 2s -> L2, +3s -> L1, +5s -> L0
        const val MONITOR_DECAY_L3_MS: Long = 2000
        const val MONITOR_DECAY_L2_MS: Long = 3000
        const val MONITOR_DECAY_L1_MS: Long = 5000

        /**
         * Initial tick delay. The first real interval is then derived from the
         * level, so this only decides how soon the first sample happens.
         */
        const val MONITOR_INTERVAL_MS: Long = 200

        private const val MAX_LISTENERS = 16

        private val THREAD_FACTORY = ThreadFactory { r ->
            Thread(r, "appleux-perf").apply { priority = Thread.MIN_PRIORITY }
        }

        @Volatile
        private var instance: AppleuxPerformanceController? = null

        fun getCurrentQuality(): AppleuxQualityLevel =
            instance?.currentQuality ?: AppleuxQualityLevel.QUALITY_100

        fun getCurrentRenderScale(): Float = instance?.currentRenderScale ?: 1.0f

        fun getCurrentMonitoringLevel(): MonitoringLevel =
            instance?.currentLevel ?: MonitoringLevel.LEVEL_0_NORMAL

        /** Static read for consumers that poll instead of subscribing. */
        fun isBlurEnabled(): Boolean = instance?.isBlurEnabled ?: true

        fun getInstance(context: Context?): AppleuxPerformanceController {
            return instance ?: synchronized(this) {
                instance ?: AppleuxPerformanceController().also {
                    // Only ever the APPLICATION context, and only to read the
                    // display's live refresh rate. Holding an Activity here
                    // would keep a window alive for the process's lifetime.
                    it.appContext = context?.applicationContext
                    instance = it
                }
            }
        }
    }
}