package com.appleux.device.rock

/**
 * The per-frame budget, derived from the panel's ACTUAL refresh rate instead of
 * from a hardcoded 60 Hz.
 *
 * WHY — the rock panel (POCO M5, Helio G99) drives **30, 50, 60 or 90 Hz**
 * (MEASURED: `dumpsys display` supportedModes on the physical unit,
 * 2026-10-15; 90 Hz active at capture). Every threshold in the adaptive
 * performance controller was expressed in absolute milliseconds derived from
 * 60 Hz, which means that on a 90 Hz panel the controller was calling a frame
 * that took 13 ms "good" when it had already blown through the 11.11 ms
 * deadline — a quarter of a second of jank per panel-second shipped as
 * "smooth". The error is largest at the low modes, where the real deadline is
 * up to 33.3 ms.
 *
 * DERIVATION — the ratios below are the ones already in use at 60 Hz, so
 * nothing about the 60 Hz behaviour changes:
 *
 * | threshold    | ratio to budget | 30 Hz  | 50 Hz  | 60 Hz  | 90 Hz  |
 * |--------------|-----------------|--------|--------|--------|--------|
 * | good frame   | 0.84            | 28.00  | 16.80  | 14.00  | 9.33   |
 * | jank         | 1.00            | 33.33  | 20.00  | 16.67  | 11.11  |
 * | severe       | 1.20            | 40.00  | 24.00  | 20.00  | 13.33  |
 *
 * 14 / 16.67 = 0.840, 20 / 16.67 = 1.1998 — so 0.84 and 1.20 reproduce the
 * documented canonical values exactly at 60 Hz and scale them at anything
 * else. `AGENTS.md` §Key Thresholds remains literally true for a 60 Hz panel,
 * which is the only case it can state a number for.
 *
 * PROVENANCE — **AppleUX-chosen.** These ratios are ours. Apple publishes a
 * 120 Hz ProMotion cadence but no jank taxonomy, and the threshold set is a
 * measurement policy, not a design token.
 */
data class FrameBudget(
    val refreshRateHz: Float,
    val budgetMs: Float,
    val goodFrameMs: Float,
    val jankMs: Float,
    val severeMs: Float
) {
    companion object {

        /** Ratio of the good-frame threshold to the frame budget. 14/16.67. */
        const val GOOD_FRAME_RATIO = 0.84f

        /** Ratio of the severe threshold to the frame budget. 20/16.67. */
        const val SEVERE_FRAME_RATIO = 1.20f

        /**
         * Frames shorter than this are treated as unmeasurable rather than as
         * impossibly fast frames — Choreographer can hand back two callbacks
         * for one vsync on some panels.
         */
        private const val MIN_VALID_HZ = 1f

        /** Clamp: no panel this ROM will ever see refreshes above 240 Hz. */
        private const val MAX_PLAUSIBLE_HZ = 240f

        @JvmStatic
        fun forRefreshRate(hz: Float): FrameBudget {
            val rate = when {
                hz.isNaN() || hz < MIN_VALID_HZ -> RockDisplayProfile.NOMINAL_REFRESH_HZ
                hz > MAX_PLAUSIBLE_HZ -> MAX_PLAUSIBLE_HZ
                else -> hz
            }
            val budget = 1000f / rate
            return FrameBudget(
                refreshRateHz = rate,
                budgetMs = budget,
                goodFrameMs = budget * GOOD_FRAME_RATIO,
                jankMs = budget,
                severeMs = budget * SEVERE_FRAME_RATIO
            )
        }
    }
}