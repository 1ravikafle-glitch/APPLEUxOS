# AppleUX Adaptive Performance Controller — Architecture

## Overview

The AppleUX Adaptive Performance Controller is a low-overhead, centralized system for
protecting UI responsiveness and frame pacing under hardware pressure.

## Architecture

```
                APPLEUX
                   │
         ┌───────┴────────┐
         │                │
    Rendering         Interaction
         │                │
         └───────┬────────┘
                 │
   AppleUX Performance Controller
                 │
   ┌─────────────┼─────────────┐
   │             │             │
Frame Timing  System Signals  Events
   │             │             │
   └─────────────┼─────────────┘
                 ↓
        Bottleneck State
                 ↓
        Rendering Policy
                 ↓
    ┌────────────┴────────────┐
    │                         │
Effect Quality     Render Resolution
    │                         │
    └────────────┬────────────┘
                 ↓
          Android Graphics
                 ↓
              Hardware
```

## Components

### Core Module (`appleux/performance/`)

Java/Kotlin sources under `com.appleux.performance`:

- `controller/AppleuxPerformanceController.kt` — singleton controller
- `controller/AdaptiveRenderPolicy.kt` — quality state bridge
- `controller/MonitoringLevel.kt` — 4-level monitoring taxonomy
- `monitor/FrameTimeMonitor.kt` — 30-frame rolling window
- `monitor/PressureMonitor.kt` — CPU/GPU/thermal/memory/IO/PSI telemetry
- `quality/AppleuxQualityLevel.kt` — Q100→Q70 enum
- `quality/BottleneckState.kt` — NORMAL..SEVERE_PRESSURE enum
- `integration/AppleuxPerformanceService.kt` — component wiring

### Monitoring Levels

| Level | Name          | CPU   | Memory | Thermal | PSI   | I/O   | GPU   |
|-------|---------------|-------|--------|---------|-------|-------|-------|
| 0     | Normal        | 0.5Hz | 0.2Hz  | 0.2Hz   | 0.2Hz | 0.2Hz | 0.2Hz |
| 1     | Warning       | 1Hz   | 1Hz    | 1Hz     | 1Hz   | 1Hz   | 1Hz   |
| 2     | Diagnostic    | 2Hz   | 2Hz    | 2Hz     | 2Hz   | 2Hz   | 2Hz   |
| 3     | Emergency     | 5Hz   | 5Hz    | 5Hz     | 5Hz   | 5Hz   | 5Hz   |

These are **maximum** sampling rates. Event-driven signals are preferred.

## Frame Timing Pipeline

Frame timing is sourced from the Android rendering pipeline:

1. SurfaceFlinger display-present timing (preferred)
2. Android FrameMetrics / FrameTimeline
3. RenderThread/HWUI timing

The `FrameTimeMonitor` maintains a 30-frame ring buffer. Each frame is classified:

- GOOD: ≤14.00 ms
- WARNING: >14.00 ms and ≤16.67 ms
- MISSED DEADLINE (jank): >16.67 ms
- SEVERE: >20.00 ms

## Jank Definition

> A janked frame is a frame that misses its 60 Hz presentation deadline (frame time >16.67ms),
> excluding frames explicitly skipped by the platform when no new content is required.

## Canonical Escalation Policy

| State       | Trigger (in 30-frame window)              |
|-------------|-------------------------------------------|
| Normal      | <2 janked frames                           |
| Warning     | ≥2 janked frames                           |
| Diagnostic  | ≥5 janked frames OR 3 consecutive severe   |
| Emergency   | ≥10 janked frames OR 5 consecutive severe |

## Quality-Down Policy

Quality reduction is only applied when **rendering/GPU pressure** is contributing, not for CPU-only pressure.

| Current → Target | Condition                                  |
|------------------|--------------------------------------------|
| Q100 → Q95       | Diagnostic + ≥5 janked/30 + GPU pressure   |
| Q95 → Q90        | Q95 active ≥1s + ≥5 janked/30 + GPU pressure |
| Q90 → Q85        | Q90 active ≥1s + ≥5 janked/30 + GPU pressure |
| Q85 → Q80        | Q85 active ≥1s + ≥5 janked/30 + GPU pressure |
| Q80 → Q75        | Q80 active ≥1s + ≥10 janked/30 + GPU/thermal |
| Q75 → Q70        | Q75 active ≥1s + ≥10 janked/30 + GPU/thermal |

Q70 is the hard floor — no automatic reduction below this level.

## Recovery Policy

All recovery conditions must be met simultaneously:

1. Frame time ≤14 ms
2. Jank rate <1%
3. Severe-jank rate <0.5%
4. No active severe CPU/GPU/thermal/memory/IO pressure

**Recovery steps:**

- Start recovery: 5 seconds of stable conditions (10s after thermal pressure clears)
- Each quality level increase: 2-second minimum dwell between steps
- One level at a time: Q70→Q75→Q80→...→Q100

## Monitoring Decay

Independent from quality recovery:

- Level 3 → Level 2: after 2 seconds without severe pressure
- Level 2 → Level 1: after 3 additional seconds without light pressure
- Level 1 → Level 0: after 5 additional seconds without light pressure

## Oscillation Protection

If a quality boundary is crossed twice within 10 seconds, hold the lower quality for an
additional 5 seconds before permitting re-recovery.

## Fail-Safe Design

- Controller crash does not crash SystemUI, Launcher, or foreground apps
- Telemetry failure degrades to normal Android rendering behavior
- All critical operations are guarded with try/catch
- No background service created solely for monitoring — integrates into existing rendering pipeline

## Implementation Files

| File | Purpose |
|------|---------|
| `FrameTimeMonitor.kt` | 30-frame ring buffer, jank detection, variance |
| `PressureMonitor.kt` | CPU/GPU/thermal/memory/PSI/IO telemetry |
| `AppleuxPerformanceController.kt` | Central policy engine, levels, degradation/recovery |
| `AdaptiveRenderPolicy.kt` | Quality state bridge, observer pattern |
| `AppleuxPerformanceService.kt` | Component integration, state propagation |
| `MonitoringLevel.kt` | Sampling rate table per monitoring level |
| `AppleuxQualityLevel.kt` | Q100–Q70 enum with scales |
| `BottleneckState.kt` | NORMAL/CPU/GPU/THERMAL/MEMORY/IO/MIXED/SEVERE |
