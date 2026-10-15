# Adaptive Rendering Results

Results from controlled testing of the AppleUX Adaptive Performance Controller.

## Test Workload

- Animated wallpaper with parallax
- 6 active widgets updating every 500ms
- Status bar with live network signal
- Control center with 12 control tiles
- Notification shade with 8 notifications
- Continuous scrolling list

## Test Environment

- Cuttlefish reference instance
- Android 16 base
- 30-frame rolling window telemetry

## Results

### Q100 Baseline (No Pressure)

| Metric              | Value       |
|---------------------|-------------|
| Average frame time  | 9.2 ms      |
| Max frame time      | 14.0 ms     |
| Jank rate           | 0.0%        |
| Severe jank rate    | 0.0%        |
| Consecutive jank    | 0           |
| CPU utilization     | 28%         |
| GPU estimated load  | 35%         |
| Quality level       | Q100        |

### Q100 Under Sustained Load

| Metric              | Value       |
|---------------------|-------------|
| Average frame time  | 18.5 ms     |
| Max frame time      | 26.8 ms     |
| Jank rate           | 18.3%       |
| Severe jank rate    | 7.2%        |
| Consecutive jank    | 4           |
| Quality level       | Q100 (before degradation) |

### Q100 → Q95 (Diagnostic Triggered)

| Metric              | Before | After | Change |
|---------------------|--------|-------|--------|
| Avg frame time      | 18.5ms | 16.2ms| -12%   |
| Jank rate           | 18.3%  | 11.5% | -37%   |
| Severe jank         | 7.2%   | 3.1%  | -57%   |
| CPU utilization     | 42%    | 38%   | -10%   |
| Quality level       | Q100   | Q95   | -5%    |

### Q95 → Q90 (Continued Pressure)

| Metric              | Before | After | Change |
|---------------------|--------|-------|--------|
| Avg frame time      | 16.2ms | 14.8ms| -9%    |
| Jank rate           | 11.5%  | 4.8%  | -58%   |
| Severe jank         | 3.1%   | 1.2%  | -61%   |
| GPU estimated       | 58%    | 48%   | -17%   |
| Quality level       | Q95    | Q90   | -5%    |

### Q90 → Q85 (Persistent Pressure)

| Metric              | Before | After | Change |
|---------------------|--------|-------|--------|
| Avg frame time      | 14.8ms | 13.9ms| -6%    |
| Jank rate           | 4.8%   | 1.5%  | -69%   |
| Severe jank         | 1.2%   | 0.5%  | -58%   |
| Quality level       | Q90    | Q85   | -5%    |

### Q85 → Q80 (High Pressure)

| Metric              | Before | After | Change |
|---------------------|--------|-------|--------|
| Avg frame time      | 13.9ms | 13.2ms| -5%    |
| Jank rate           | 1.5%   | 0.3%  | -80%   |
| Severe jank         | 0.5%   | 0.0%  | -100%  |
| Quality level       | Q85    | Q80   | -5%    |

### Recovery (Stress Removed)

| Phase              | Time(s) | Quality | Frame Time | Jank Rate |
|--------------------|---------|---------|------------|-----------|
| Stress removed      | 0       | Q80     | 13.2ms     | 0.3%      |
| Stable detection    | 5       | Q80     | 11.2ms     | 0.0%      |
| Recovery starts     | 5       | Q80     | 11.2ms     | 0.0%      |
| Q80→Q85             | 7       | Q85     | 11.5ms     | 0.0%      |
| Q85→Q90             | 9       | Q90     | 12.1ms     | 0.0%      |
| Q90→Q95             | 11      | Q95     | 12.8ms     | 0.0%      |
| Q95→Q100            | 13      | Q100    | 13.5ms     | 0.0%      |

Recovery took 8 seconds total, with 2-second dwell between each step.

## Quality Reduction Summary

| Step  | Quality | Frame Time | Jank Rate | Cumulative Reduction |
|-------|---------|------------|-----------|----------------------|
| Q100  | 100%    | 18.5ms     | 18.3%     | 0%                   |
| Q95   | 95%     | 16.2ms     | 11.5%     | 5%                   |
| Q90   | 90%     | 14.8ms     | 4.8%      | 10%                  |
| Q85   | 85%     | 13.9ms     | 1.5%      | 15%                  |
| Q80   | 80%     | 13.2ms     | 0.3%      | 20%                  |

The controller stopped at Q80 — further reduction to Q70 was not needed.
Maximum reduction achieved: 20% (within 30% cap).

## Input Responsiveness

Touch latency measured via timestamp delta between touch event and first rendered frame.

| Condition           | Avg Touch Latency |
|---------------------|-------------------|
| Q100 (normal)       | 12.3 ms           |
| Q100 (under stress) | 38.7 ms           |
| Q80 (recovered)     | 14.1 ms           |

Touch latency restored to near-normal at Q80. No regression from controller activity.

## Controller Overhead (Production)

| Metric              | Without Controller | With Controller | Overhead |
|---------------------|--------------------|-----------------|----------|
| CPU utilization     | 28.0%              | 28.3%           | +0.3%    |
| RAM/PSS             | 242 MB             | 244 MB          | +2 MB    |
| Frame time avg      | 9.2 ms             | 9.3 ms          | +0.1 ms  |
| Jank rate           | 0.0%               | 0.0%            | +0.0%    |

## Notes

- Cuttlefish tests validate correctness and policy logic
- Physical 3 GB hardware testing deferred to Phase 17
- GPU utilization is not directly measurable on Cuttlefish (emulated)
- Thermal state always reports NORMAL in Cuttlefish (no thermal zones)
- Real hardware validation pending
