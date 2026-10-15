# Bottleneck Detection

## Classification States

```text
NORMAL
CPU_BOUND
GPU_BOUND
THERMAL_BOUND
MEMORY_BOUND
IO_BOUND
MIXED_PRESSURE
SEVERE_PRESSURE
```

## Detection Logic

### Step 1 — Check Thermal (Highest Priority)

| Thermal State | Classification    |
|---------------|-------------------|
| SEVERE        | THERMAL_BOUND     |
| HOT           | THERMAL_BOUND     |
| WARM          | (continue to CPU/GPU) |
| NORMAL        | (continue to CPU/GPU) |

If thermal severe → always classify as THERMAL_BOUND regardless of other signals.

### Step 2 — Evaluate System Signals

| Signal           | CPU Pressure | GPU Pressure | Memory Pressure | I/O Pressure |
|------------------|--------------|--------------|-----------------|--------------|
| CPU utilization  | >85%         | —            | —               | —            |
| PSI CPU          | >50%         | —            | —               | —            |
| GPU utilization  | —            | >90%         | —               | —            |
| Memory pressure  | —            | —            | >85%            | —            |
| PSI Memory       | —            | —            | >50%            | —            |
| I/O wait         | —            | —            | —               | >30%         |
| PSI I/O          | —            | —            | —               | >50%         |

### Step 3 — Classify

```text
if thermal == SEVERE:
    THERMAL_BOUND
elif cpu_pressure and gpu_pressure:
    MIXED_PRESSURE
elif cpu_pressure:
    CPU_BOUND
elif gpu_pressure:
    GPU_BOUND
elif memory_pressure and frame_time > deadline:
    MEMORY_BOUND
elif io_pressure and frame_time > deadline:
    IO_BOUND
elif thermal == HOT:
    THERMAL_BOUND
elif any pressure:
    MIXED_PRESSURE
else:
    NORMAL
```

## Quality-Down Policy by Bottleneck

| Bottleneck    | Reduce Quality? | Action                          |
|---------------|-----------------|---------------------------------|
| CPU_BOUND     | No              | Defer background work           |
| GPU_BOUND     | Yes             | Reduce rendering quality        |
| THERMAL_BOUND | Yes (if GPU contributes) | Reduce all workload |
| MEMORY_BOUND  | Yes (if rendering contributes) | Reduce buffers |
| IO_BOUND      | No initially    | Defer background I/O            |
| MIXED_PRESSURE| Yes (if GPU contributes) | Reduce rendering     |

## Telemetry Sources

### CPU Utilization
- Source: `/proc/stat`
- Method: Sample total vs idle jiffies over interval
- Fallback: `/proc/loadavg` if `/proc/stat` unavailable

### CPU Frequency
- Source: `/sys/devices/system/cpu/cpufreq/policy*/scaling_cur_freq`
- Method: Read all policy frequencies, average them
- Fallback: Use `/proc/cpuinfo` (less precise)

### GPU Utilization
- Source: `/sys/class/kgsl/` (Qualcomm Adreno)
- Method: `kgsl-busmon` GPU util if available
- Fallback: GPU not directly monitored; rely on frame timing + CPU correlation
- Note: No universal GPU monitoring interface on Android; gracefully degrade

### Thermal State
- Source: `/sys/class/thermal/thermal_zone*/temp`
- Method: Read all thermal zones, take maximum temperature
- Conversion: Celsius = raw_value / 1000
- Thresholds: WARM ≥65°C, HOT ≥75°C, SEVERE ≥85°C

### Memory Pressure
- Source: `/proc/meminfo`
- Method: (MemTotal - MemAvailable) / MemTotal × 100
- Fallback: `/proc/zoneinfo` for page reclaim stats

### PSI (Pressure Stall Information)
- Source: `/proc/pressure/{cpu,memory,io}`
- Fields read: `avg10` (10-second average)
- Available on Linux kernel ≥4.20 / Android 10+

### I/O Wait
- Source: `/proc/stat`
- Field: `iowait` column in line starting with `cpu`
- Method: (iowait / (idle + iowait)) × 100

## Fail-Safe Fallback Behavior

If any telemetry source fails:
1. Skip that metric
2. Rely on remaining available metrics
3. If too many metrics unavailable, fall back to frame-timing-only decisions
4. If frame timing unavailable, degrade to Normal/NORMAL state (no quality reduction)

## Known Limitations

1. GPU utilization is not universally available across all SoCs
2. Some thermal zones report in millidegree, others in decidegree
3. PSI may not be mounted on all kernel configurations
4. Some `/sys` paths differ between kernel versions and SoC vendors
5. These are handled with try/catch and graceful fallbacks
