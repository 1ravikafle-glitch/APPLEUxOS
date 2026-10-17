---
description: Measure AppleUX frame timing and jank on Cuttlefish
---

# Measure Frame Timing

Captures real frame-timing evidence for AppleUX components. Use this before
claiming any jank, frame-rate, or responsiveness number.

## Prerequisites

Cuttlefish must already be running. If it is not running, stop and tell the
user — booting takes several minutes and needs explicit go-ahead.

## Steps

1. Reset gfxinfo for the target package so the sample window is clean:

```bash
adb shell dumpsys gfxinfo com.appleux.launcher reset
```

2. Drive the UI for a fixed, repeatable interaction (10 seconds). Use
`adb shell input` for swipes and taps; keep the script identical every run so
results are comparable.

3. Capture frame stats:

```bash
adb shell dumpsys gfxinfo com.appleux.launcher > /tmp/appleux-launcher-gfx.txt
```

4. Also capture SurfaceFlinger layer timing, which reflects the real
compositor deadline rather than the app's own view of it:

```bash
adb shell dumpsys SurfaceFlinger --timestats -enable
```

Repeat the same interaction, then:

```bash
adb shell dumpsys SurfaceFlinger --timestats -dump > /tmp/appleux-sf.txt
adb shell dumpsys SurfaceFlinger --timestats -disable
```

5. Report the numbers against the canonical thresholds:

| Metric | Good | Warning | Jank | Severe |
|---|---|---|---|---|
| Frame time | <=14 ms | >14-16.67 ms | >16.67 ms | >20 ms |

Extract the percentile rows from the gfxinfo dump — `50th`, `90th`, `95th`,
`99th` — and the jank count. Report as a table.

## Reporting rules

- State the package, the interaction performed, and the sample size (frame
  count, not wall time).
- A run with fewer than 300 frames is not a valid sample; say so and repeat.
- **Never** substitute projected or simulated numbers for measured ones. If a
  number cannot be measured, write `not measured` and explain why.
- Cuttlefish results are not proof of physical-device GPU, thermal, or eMMC
  performance. Say this explicitly whenever reporting Cuttlefish data.

## Related deliverables

`deliverables/m12/` and `deliverables/m18/` contain projected numbers from
design-time analysis. Treat those as hypotheses to be replaced by this
measurement, not as results.