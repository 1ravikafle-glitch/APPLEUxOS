package com.appleux.performance.controller

/**
 * Monitoring levels and their canonical sampling rates.
 *
 * AGENTS.md: "Sampling: L0 CPU 0.5Hz / others 0.2Hz; L1 1Hz; L2 2Hz; L3 5Hz cap."
 *
 * CPU is sampled at its own rate; memory, thermal, PSI, I/O and GPU all share
 * the slower "other" rate at L0 and match CPU from L1 upwards.
 *
 * These rates drive a single scheduled policy tick at the FASTEST rate for the
 * level; each source is then gated by its own period, so the tick can never
 * read a kernel file more often than the level allows.
 */
enum class MonitoringLevel(val level: Int) {
    LEVEL_0_NORMAL(0),
    LEVEL_1_WARNING(1),
    LEVEL_2_DIAGNOSTIC(2),
    LEVEL_3_EMERGENCY(3);

    fun getCpuSamplingHz(): Float = when (this) {
        LEVEL_0_NORMAL -> 0.5f
        LEVEL_1_WARNING -> 1f
        LEVEL_2_DIAGNOSTIC -> 2f
        LEVEL_3_EMERGENCY -> 5f
    }

    /** Memory / thermal / PSI / I/O / GPU share the "others" rate. */
    fun getMemorySamplingHz(): Float = when (this) {
        LEVEL_0_NORMAL -> 0.2f
        LEVEL_1_WARNING -> 1f
        LEVEL_2_DIAGNOSTIC -> 2f
        LEVEL_3_EMERGENCY -> 5f
    }

    fun getThermalSamplingHz(): Float = getMemorySamplingHz()

    fun getPsiSamplingHz(): Float = getMemorySamplingHz()

    fun getIoSamplingHz(): Float = getMemorySamplingHz()

    fun getGpuSamplingHz(): Float = getMemorySamplingHz()

    /**
     * Fastest source rate for this level, in Hz. This is the policy-tick rate.
     * L3 is capped at 5Hz; nothing here ever samples faster.
     */
    fun getTickHz(): Float = maxOf(getCpuSamplingHz(), getMemorySamplingHz())

    /** Minimum gap between two reads of a CPU-rate source, in ms. */
    fun cpuPeriodMs(): Long = periodMs(getCpuSamplingHz())

    /** Minimum gap between two reads of a non-CPU source, in ms. */
    fun otherPeriodMs(): Long = periodMs(getMemorySamplingHz())

    private fun periodMs(hz: Float): Long =
        if (hz <= 0f) Long.MAX_VALUE else (1000f / hz).toLong()

    companion object {
        /** Hard ceiling on telemetry frequency, regardless of level. */
        const val MAX_SAMPLING_HZ = 5f

        fun fromLevel(level: Int): MonitoringLevel =
            values().firstOrNull { it.level == level } ?: LEVEL_0_NORMAL
    }
}