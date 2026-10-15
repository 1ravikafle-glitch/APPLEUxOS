package com.appleux.performance.monitor

import com.appleux.device.rock.FrameBudget

/**
 * 30-frame rolling frame-time window, fed continuously by
 * `Choreographer.FrameCallback` from [com.appleux.performance.controller.AppleuxPerformanceController].
 * It never polls and never sleeps: frame timing is push-only.
 *
 * Cost: every aggregate the policy engine reads is maintained incrementally on
 * push, so a decision costs O(1) volatile reads instead of re-scanning 30 slots
 * behind a lock 60 times a second.
 *
 * THRESHOLDS ARE DERIVED, NOT ASSUMED. The ratios below (good = 0.84 x the
 * frame budget, severe = 1.20 x it) reproduce the previously-canonical
 * absolute values *exactly* at 60 Hz — 14.00 / 16.67 / 20.00 ms — so nothing
 * about 60 Hz behaviour changed. On the POCO M5's panel, which drives 60 Hz
 * OR 90 Hz, [setRefreshRate] moves them to 9.33 / 11.11 / 13.33 ms at 90 Hz.
 * Before this, a 13 ms frame on a 90 Hz panel was classified "good" while
 * having already blown through an 11.11 ms deadline.
 *
 * [GOOD_FRAME_MS] and friends remain as the 60 Hz constants, because that is
 * the only refresh rate a constant can honestly state.
 */
class FrameTimeMonitor {

    interface FrameCallback {
        fun doFrame(frameTimeNanos: Long)
    }

    @Volatile
    private var frameCallback: FrameCallback? = null

    /**
     * Active threshold set. Volatile because the controller sets it from
     * whichever thread starts the monitor while frames are pushed on the main
     * thread. [FrameBudget] is immutable, so a reader sees either the old set
     * or the new one, never a half-updated mixture.
     */
    @Volatile
    private var budget: FrameBudget = FrameBudget.forRefreshRate(FrameBudget60_HZ)

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

        // One volatile read per frame: all four thresholds come from the same
        // immutable object, so they can never disagree with each other.
        val b = budget
        val goodMs = b.goodFrameMs
        val jankMs = b.jankMs
        val severeMs = b.severeMs

        val deltaTimeMs = if (previous > 0L) {
            (frameTimeNanos - previous) / 1_000_000f
        } else {
            b.budgetMs
        }

        val slot = frameIndex
        val overwritten = windowFilled

        // subtract the frame leaving the window before adding the new one.
        // Severe frames are also jank frames (severe > jank by construction),
        // so these are independent tests, not a chain.
        var outgoingWasMax = false
        if (overwritten) {
            val old = frameTimesMs[slot]
            winSumMs -= old
            outgoingWasMax = old >= winMaxMs
            if (old > severeMs) winSevere--
            if (old > jankMs) winJank--
            if (old > goodMs) winWarning--
        }

        frameTimesMs[slot] = deltaTimeMs
        winSumMs += deltaTimeMs
        when {
            deltaTimeMs > severeMs -> {
                winSevere++
                winJank++
                winWarning++
            }
            deltaTimeMs > jankMs -> {
                winJank++
                winWarning++
            }
            deltaTimeMs > goodMs -> winWarning++
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

        // strict >, matching the derived thresholds
        if (deltaTimeMs > jankMs) {
            totalJankedFrames++
            consecutiveJankCount++
        } else {
            consecutiveJankCount = 0
        }
        if (deltaTimeMs > severeMs) {
            totalSevereJankedFrames++
            consecutiveSevereJankCount++
        } else {
            consecutiveSevereJankCount = 0
        }

        frameCallback?.doFrame(frameTimeNanos)
    }

    /**
     * Re-derives every threshold from the panel's actual refresh rate.
     *
     * Called by the controller whenever the monitor starts and whenever the
     * display reports a mode change, because the POCO M5 panel switches between
     * 60 Hz and 90 Hz at runtime and a monitor left at 60 Hz thresholds would
     * accept every dropped frame on a 90 Hz surface.
     */
    fun setRefreshRate(hz: Float) {
        val next = FrameBudget.forRefreshRate(hz)
        if (next.refreshRateHz == budget.refreshRateHz) return
        budget = next
        // Totals accumulated under the old thresholds are not comparable with
        // the new ones, so the window is dropped rather than silently mixing
        // two different jank definitions.
        reset()
    }

    /** The active threshold set, for readouts and for the controller policy. */
    fun frameBudget(): FrameBudget = budget

    val goodFrameMs: Float get() = budget.goodFrameMs
    val frameDeadlineMs: Float get() = budget.jankMs
    val severeFrameMs: Float get() = budget.severeMs
    val nominalFrameMs: Float get() = budget.budgetMs

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
            return if (count > 0) winSumMs / count else budget.budgetMs
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
        val good = budget.goodFrameMs
        // every slot currently in the window must be a good frame
        for (i in 0 until count) {
            if (frameTimesMs[i] > good) return false
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

        /**
         * The refresh rate every constant below is quoted at. It is the
         * DEFAULT, not an assumption: [setRefreshRate] re-derives the live
         * thresholds from the panel.
         */
        const val FrameBudget60_HZ = 60f

        /**
         * 60 Hz instance of the derived thresholds: 0.84 / 1.00 / 1.20 x the
         * 16.67 ms budget. These reproduce the numbers AGENTS.md §Key
         * Thresholds documents, and only those numbers — a constant can only
         * state one refresh rate. The LIVE values are [goodFrameMs],
         * [frameDeadlineMs] and [severeFrameMs].
         */
        const val GOOD_FRAME_MS = 14f
        const val WARNING_FRAME_TIME_MS = 16.67f
        const val FRAME_DEADLINE_MS = 16.67f
        const val SEVERE_FRAME_MS = 20f

        const val NOMINAL_FRAME_MS = 16.67f
    }
}