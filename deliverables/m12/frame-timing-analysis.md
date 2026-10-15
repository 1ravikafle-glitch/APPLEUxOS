# Frame Timing Analysis

## Frame Classification Thresholds

| Category   | Frame Time |
|------------|------------|
| Good       | ≤14.00 ms  |
| Warning    | >14.00, ≤16.67 ms |
| Missed (jank)| >16.67 ms |
| Severe     | >20.00 ms |

## Jank Definition

A janked frame is a frame that misses its 60 Hz presentation deadline
(frame time >16.67 ms), excluding frames explicitly skipped by the platform
when no new content is required.

`Jank % = (janked frames / eligible frames) × 100`

`Severe Jank % = (frames >20 ms / eligible frames) × 100`

## Rolling Window

30 consecutive eligible frames (~0.5 seconds at 60 Hz).

Ring buffer of 30 float entries, updated per-frame with O(1) amortized cost.

## Metrics Maintained

| Metric                       | Description |
|------------------------------|-------------|
| `windowJankCount`            | janked frames in 30-frame window |
| `windowSevereJankCount`      | severe frames in window |
| `jankPercentage`             | lifetime average jank rate |
| `severeJankPercentage`       | lifetime severe-jank rate |
| `averageFrameTimeMs`         | rolling 30-frame average |
| `maxFrameTimeMs`             | max frame time in window |
| `consecutiveJank`            | current streak of jank |
| `consecutiveSevereJank`      | current streak of severe |

## Escalation Thresholds

| Level      | Trigger (30-frame window)                          |
|------------|----------------------------------------------------|
| Normal     | <2 janked frames                                   |
| Warning    | ≥2 janked frames                                    |
| Diagnostic | ≥5 janked frames OR 3 consecutive severe           |
| Emergency  | ≥10 janked frames OR 5 consecutive severe          |

## Quality-Down Thresholds

| Step | Condition |
|------|-----------|
| Q100→Q95 | Diagnostic + ≥5 janked/30 + GPU/rendering pressure |
| Q95→Q90  | Q95 active ≥1s + ≥5 janked/30 + GPU pressure |
| Q90→Q85  | Q90 active ≥1s + ≥5 janked/30 + GPU pressure |
| Q85→Q80  | Q85 active ≥1s + ≥5 janked/30 + GPU pressure |
| Q80→Q75  | Q80 active ≥1s + ≥10 janked/30 + GPU/thermal pressure |
| Q75→Q70  | Q75 active ≥1s + ≥10 janked/30 + GPU/thermal pressure |

## Recovery Conditions

ALL must be true:
1. Frame time ≤14 ms
2. Jank rate <1%
3. Severe-jank rate <0.5%
4. No active severe CPU/GPU/thermal/memory/IO pressure

Start recovery: 5 seconds stable (10s after thermal clears)
Dwell between steps: 2 seconds

## Oscillation Protection

If same quality boundary crossed twice within 10s: hold lower quality for 5s.

## Implementation: FrameTimeMonitor.kt

```kotlin
private val WINDOW_SIZE = 30
private val frameTimesMs: FloatArray = FloatArray(WINDOW_SIZE)

fun doFrame(frameTimeNanos: Long) {
    val deltaTimeMs = (frameTimeNanos - lastFrameTimeNanos) / 1_000_000f
    frameTimesMs[frameIndex] = deltaTimeMs
    frameIndex = (frameIndex + 1) % WINDOW_SIZE
    
    val isJank = deltaTimeMs > FRAME_DEADLINE_MS
    val isSevere = deltaTimeMs > SEVERE_FRAME_MS
    
    if (isJank) consecutiveJankCount++ else consecutiveJankCount = 0
    if (isSevere) consecutiveSevereJankCount++ else consecutiveSevereJankCount = 0
}
```

## Frame Timing Sources (Priority)

1. SurfaceFlinger display-present timing
2. Android FrameMetrics/FrameTimeline
3. RenderThread/HWUI timing
4. Choreographer FrameCallback (fallback)

No FPS calculation from coarse periodic timer — only actual frame timestamps.
