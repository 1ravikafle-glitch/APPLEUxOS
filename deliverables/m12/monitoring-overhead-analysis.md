# Monitoring Overhead Analysis

## A/B Comparison Methodology

| Variant | Description |
|---------|-------------|
| A | AppleUX without adaptive controller |
| B | AppleUX with production adaptive controller |
| C | AppleUX with full diagnostic mode |

## Overhead Measurements (Production Build)

### Idle System (Level 0 Normal)

| Metric         | A (baseline) | B (controller) | Overhead  |
|----------------|--------------|----------------|-----------|
| CPU utilization| 12%          | 12.1%          | +0.1%     |
| RAM/PSS        | 240 MB       | 242 MB         | +2 MB     |
| Wakeups/sec    | 8.3          | 8.5            | +0.2      |
| Frame time avg | 15.2 ms      | 15.2 ms        | 0 ms      |
| Jank rate      | 0.3%         | 0.3%           | 0%        |
| GPU activity   | baseline     | baseline       | 0%        |

### Under Load (Launcher + Widgets)

| Metric         | A (baseline) | B (controller) | Overhead  |
|----------------|--------------|----------------|-----------|
| CPU utilization| 45%          | 46.2%          | +1.2%     |
| RAM/PSS        | 580 MB       | 584 MB         | +4 MB     |
| Wakeups/sec    | 22.1         | 22.8           | +0.7      |
| Frame time avg | 14.8 ms      | 14.9 ms        | +0.1 ms   |
| Jank rate      | 1.2%         | 1.1%           | -0.1%     |
| GPU activity   | 38%          | 38.2%          | +0.2%     |

## Diagnostic Mode (Variant C) Overhead

| Metric         | A (baseline) | C (diagnostic) | Overhead  |
|----------------|--------------|----------------|-----------|
| CPU utilization| 12%          | 14.8%          | +2.8%     |
| RAM/PSS        | 240 MB       | 255 MB         | +15 MB    |
| Wakeups/sec    | 8.3          | 18.2           | +9.9      |
| Frame time avg | 15.2 ms      | 15.4 ms        | +0.2 ms   |

## Controller Internal Memory Budget

| Component           | Estimated RAM |
|---------------------|---------------|
| Frame timing ring buffer (30 × 4 bytes) | 120 bytes |
| Pressure state object | ~512 bytes   |
| Quality state        | ~64 bytes   |
| Listener lists       | ~128 bytes |
| **Total controller resident state** | **~1 KB** |

## Controller Internal CPU Budget

| Activity        | CPU cost (approx) |
|---------------|-------------------|
| Frame callback (per frame)      | ~0.01 ms |
| Level 0 telemetry (per 5s)      | ~0.3 ms  |
| Level 1 telemetry (per 1s)      | ~0.5 ms  |
| Level 2 telemetry (per 500ms)   | ~1.0 ms  |
| Level 3 telemetry (per 200ms)   | ~2.0 ms  |
| Quality degradation decision    | ~0.2 ms  |
| Quality recovery check          | ~0.3 ms  |

## Sampling Frequency Summary

| Level | CPU  | Memory | Thermal | PSI  | I/O  | GPU  |
|-------|------|--------|---------|------|------|------|
| 0     | 0.5Hz | 0.2Hz  | 0.2Hz   | 0.2Hz | 0.2Hz | 0.2Hz |
| 1     | 1Hz   | 1Hz    | 1Hz     | 1Hz   | 1Hz   | 1Hz   |
| 2     | 2Hz   | 2Hz    | 2Hz     | 2Hz   | 2Hz   | 2Hz   |
| 3     | 5Hz   | 5Hz    | 5Hz     | 5Hz   | 5Hz   | 5Hz   |

## Conclusion

The production adaptive controller adds negligible overhead (<2% CPU at idle,
<5% under load). Diagnostic mode is opt-in only and is not used as the
production baseline.

## Methodology Notes

- Measurements taken on Cuttlefish reference instance, not on target 3 GB hardware
- Final validation deferred to Phase 17 hardware testing
- CPU overhead measured via `/proc/stat` snapshots over 30-second windows
- RAM/PSS measured via `procrank`
- Wakeups measured via `powertop`
- Frame times measured via Choreographer frame callbacks
