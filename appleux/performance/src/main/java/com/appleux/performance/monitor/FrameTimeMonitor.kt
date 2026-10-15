package com.appleux.performance.monitor

/**
 * 30-frame rolling frame-time window, fed continuously by
 * `Choreographer.FrameCallback` from [com.appleux.performance.controller.AppleuxPerformanceController].
 * It never polls and never sleeps: frame timing is push-only.
 *
 * Cost: every aggregate the policy engine reads is maintained incrementally on
 * push, so a decision costs O(1) volatile reads instead of re-scanning 30 slots
 * behind a lock 60 times a second.
 *
 * Thresholds are the canonical ones (>14 warning, >16.67 jank, >20 severe).
 */
class FrameTimeMonitor {

    interface FrameCallback {
        fun doFrame(frameTimeNanos: Long)
    }

    @Volatile
    private var frameCallback: FrameCallback? = null

    // ------------------------------------------------------ window storage --

    private val frameTimesMs = FloatArray(WINDOW_SIZE)
    private var frameIndex = 0
    private var windowFilled = false

    private var lastFrameTimeNanos = 0L

    // running aggregates over the current window (O(1) reads)
    @Volatile private var winJank = 0
    @Volatile private var winSevere = 0
    @Volatile private var winWarning = 0
    @Volatile private var winSumMs = 0f
    @Volatile private var winMaxMs = 0f
    @Volatile private var winCount = 0

    private var consecutiveJankCount = 0
    @Volatile private var consecutiveSevereJankCount = 0

    @Volatile private var totalEligibleFrames = 0L
    @Volatile private var totalJankedFrames = 0L
    @Volatile private var totalSevereJankedFrames = 0L

    /**
     * Feed one Choreographer frame time. Main thread only.
     */
    fun doFrame(frameTimeNanos: Long) {
        val previous = lastFrameTimeNanos
        lastFrameTimeNanos = frameTimeNanos

        val deltaTimeMs = if (previous > 0L) {
            (frameTimeNanos - previous) / 1_000_000f
        } else {
            NOMINAL_FRAME_MS
        }

        val slot = frameIndex
        val overwritten = windowFilled

        // subtract the frame leaving the window before adding the new one.
        // Severe frames are also jank frames (>20 implies >16.67), so these are
        // independent tests, not a chain.
        var outgoingWasMax = false
        if (overwritten) {
            val old = frameTimesMs[slot]
            winSumMs -= old
            outgoingWasMax = old >= winMaxMs
            if (old > SEVERE_FRAME_MS) winSevere--
            if (old > FRAME_DEADLINE_MS) winJank--
            if (old > GOOD_FRAME_MS) winWarning--
        }

        frameTimesMs[slot] = deltaTimeMs
        winSumMs += deltaTimeMs
        when {
            deltaTimeMs > SEVERE_FRAME_MS -> {
                winSevere++
                winJank++
                winWarning++
            }
            deltaTimeMs > FRAME_DEADLINE_MS -> {
                winJank++
                winWarning++
            }
            deltaTimeMs > GOOD_FRAME_MS -> winWarning++
        }

        if (outgoingWasMax) {
            recomputeMax()
        } else if (deltaTimeMs > winMaxMs) {
            winMaxMs = deltaTimeMs
        }

        frameIndex = if (frameIndex + 1 >= WINDOW_SIZE) 0 else frameIndex + 1
        if (!windowFilled && frameIndex == 0) windowFilled = true
        if (windowFilled) winCount = WINDOW_SIZE else winCount = frameIndex

        totalEligibleFrames++

        // strict >, matching the canonical thresholds
        if (deltaTimeMs > FRAME_DEADLINE_MS) {
            totalJankedFrames++
            consecutiveJankCount++
        } else {
            consecutiveJankCount = 0
        }
        if (deltaTimeMs > SEVERE_FRAME_MS) {
            totalSevereJankedFrames++
            consecutiveSevereJankCount++
        } else {
            consecutiveSevereJankCount = 0
        }

        frameCallback?.doFrame(frameTimeNanos)
    }

    fun setFrameCallback(callback: FrameCallback?) {
        frameCallback = callback
    }

    // ------------------------------------------------------------- queries --

    fun currentWindowCount(): Int = winCount

    val windowJankCount: Int get() = winJank
    val windowSevereJankCount: Int get() = winSevere

    /** Frames later than the 14 ms good-frame budget (superset of jank). */
    val windowWarningCount: Int get() = winWarning

    val jankPercentage: Float
        get() = if (totalEligibleFrames > 0L) {
            totalJankedFrames.toFloat() / totalEligibleFrames * 100f
        } else 0f

    val severeJankPercentage: Float
        get() = if (totalEligibleFrames > 0L) {
            totalSevereJankedFrames.toFloat() / totalEligibleFrames * 100f
        } else 0f

    val averageFrameTimeMs: Float
        get() {
            val count = winCount
            return if (count > 0) winSumMs / count else NOMINAL_FRAME_MS
        }

    val maxFrameTimeMs: Float get() = winMaxMs

    val consecutiveJank: Int get() = consecutiveJankCount
    val consecutiveSevereJank: Int get() = consecutiveSevereJankCount

    val totalFrames: Long get() = totalEligibleFrames

    /**
     * A reported average only becomes trustworthy after 300 eligible frames.
     * Recovery decisions refuse to act before that.
     */
    val hasMinimumSample: Boolean get() = totalEligibleFrames >= MIN_VALID_SAMPLE

    fun isWindowJankRateExceeded(thresholdPercent: Float): Boolean {
        val count = winCount
        if (count == 0) return false
        return (winJank.toFloat() / count * 100f) >= thresholdPercent
    }

    fun isWindowSevereJankCountExceeded(threshold: Int): Boolean = winSevere >= threshold

    fun isFrameTimeStableForRecovery(): Boolean {
        val count = winCount
        if (count == 0) return false
        // every slot currently in the window must be a good frame
        for (i in 0 until count) {
            if (frameTimesMs[i] > GOOD_FRAME_MS) return false
        }
        return true
    }

    @Synchronized
    fun reset() {
        frameIndex = 0
        windowFilled = false
        winJank = 0
        winSevere = 0
        winWarning = 0
        winSumMs = 0f
        winMaxMs = 0f
        winCount = 0
        consecutiveJankCount = 0
        consecutiveSevereJankCount = 0
        totalEligibleFrames = 0L
        totalJankedFrames = 0L
        totalSevereJankedFrames = 0L
        lastFrameTimeNanos = 0L
    }

    private fun recomputeMax() {
        val count = if (windowFilled) WINDOW_SIZE else frameIndex
        var max = 0f
        for (i in 0 until count) {
            val v = frameTimesMs[i]
            if (v > max) max = v
        }
        winMaxMs = max
    }

    companion object {
        /** 30 eligible frames, per AGENTS.md. */
        const val WINDOW_SIZE = 30
        const val MIN_VALID_SAMPLE = 300L

        const val GOOD_FRAME_MS = 14f
        const val WARNING_FRAME_TIME_MS = 16.67f
        const val FRAME_DEADLINE_MS = 16.67f
        const val SEVERE_FRAME_MS = 20f

        const val NOMINAL_FRAME_MS = 16.67f
    }
}