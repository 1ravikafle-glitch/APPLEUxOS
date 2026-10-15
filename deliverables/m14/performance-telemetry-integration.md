# Performance Telemetry Integration

## Android/Linux Telemetry Sources

This document describes what telemetry sources the AppleUX Adaptive Performance
Controller uses, how they are obtained, and the fallback chain when sources are unavailable.

## 1. Frame Timing

| Priority | Source                          | Method                          |
|----------|---------------------------------|---------------------------------|
| 1        | SurfaceFlinger present timing   | `Choreographer.FrameCallback`  |
| 2        | Android FrameMetrics            | `FrameMetrics` API              |
| 3        | RenderThread/HWUI               | (platform-internal)             |

**Implementation:** `FrameTimeMonitor.kt` uses `Choreographer.FrameCallback.doFrame(frameTimeNanos)`
on the main application thread. Frame timestamps are monotonic nanosecond values from the
system's vsync clock.

**No FPS timer polling.** The controller only processes data when actual frames arrive.

## 2. CPU Utilization

| Source           | Path              | Read Frequency  |
|------------------|-------------------|-----------------|
| /proc/stat       | `cpu` line, field  | per sample      |

Method: Read total and idle jiffies, compute busy ratio.
The first read is stored; the next sample computes delta over elapsed time.

Fallback: `/proc/loadavg` 1-minute average (less precise but widely available).

## 3. CPU Frequency

| Source              | Path                                           |
|---------------------|------------------------------------------------|
| cpufreq policies    | `/sys/devices/system/cpu/cpufreq/`              |

Method: Enumerate all `policy*` subdirectories, read `scaling_cur_freq`,
average across all online cores.

Availability: Linux kernel ≥3.14 with CPUfreq subsystem enabled.
Fallback: If no policy directories exist, CPU frequency telemetry is unavailable.
The controller falls back to CPU utilization + PSI only.

## 4. GPU Utilization

| Source       | Path                                            |
|--------------|-------------------------------------------------|
| Qualcomm KGSL| `/sys/class/kgsl/kgsl-busmon/kgsl_busmon_gpu_util` |

Availability: Qualcomm Adreno GPUs only.

Fallback:
- If unavailable, the controller infers GPU pressure from:
  1. Frame timing (missed deadlines)
  2. CPU utilization (high CPU → likely GPU bottleneck in UI thread)
  3. Total jank rate

The controller marks `gpuUtilization = 0f` when unavailable and
reliably detects GPU pressure through frame timing.

## 5. Thermal State

| Source          | Path                              |
|-----------------|-----------------------------------|
| thermal class   | `/sys/class/thermal/`             |

Method: Enumerate all `thermal_zone*` directories, read `temp` file
(raw value in millidegrees Celsius), take the maximum across all zones.

Thresholds:
- WARM: ≥65°C
- HOT: ≥75°C
- SEVERE: ≥85°C

Availability: All Android devices with Linux kernel ≥3.0 with `CONFIG_THERMAL`.
Fallback: If thermal zones are unavailable, thermal monitoring is disabled.
The controller degrades to frame-timing-based decisions.

## 6. Memory Pressure

| Source      | Path        |
|-------------|-------------|
| Meminfo     | `/proc/meminfo` |

Method: Read `MemTotal` and `MemAvailable`, compute
`(MemTotal - MemAvailable) / MemTotal × 100`.

Availability: Available on all Linux systems.
Fallback: None needed — `/proc/meminfo` is universally present on Android.

## 7. PSI (Pressure Stall Information)

| Source | Path                       |
|--------|----------------------------|
| PSI    | `/proc/pressure/{cpu,memory,io}` |

Method: Read `avg10` field from each pressure file.
The `avg10` value represents the 10-second running average of
pressure stall time as a fraction (0.0–1.0).

Scaling: Convert to percentage: `value * 100`.

Availability: Linux kernel ≥4.20, mounted at `/proc/pressure/`.
Present on Android 10+ devices.

Thresholds:
- CPU pressure: `avg10 > 50%` → pressure
- Memory pressure: `avg10 > 50%` → pressure
- I/O pressure: `avg10 > 50%` → pressure

Fallback: If `/proc/pressure/` is unavailable, PSI is not collected.
The controller relies on CPU%, memory%, and iowait% instead.

## 8. I/O Pressure

| Source   | Path       |
|----------|------------|
| /proc/stat | `cpu` line, `iowait` field |

Method: Read iowait jiffies, compute as percentage of total (idle + iowait).
Combined with PSI I/O if available.

Availability: Universal on Linux.
Fallback: If `/proc/stat` iowait is zero or unavailable, rely on PSI I/O only.

## 9. zRAM

| Source          | Path                              |
|-----------------|-----------------------------------|
| zram block devs | `/sys/block/zram0/`               |

The controller does NOT directly manage zRAM.
It observes memory pressure and reduces workload to indirectly
reduce zRAM swapping activity.

zRAM is managed by the Android memory management subsystem (LMKD).

## 10. LMKD (Low Memory Killer Daemon)

| Source        | Mechanism              |
|---------------|------------------------|
| LMKD          | Android memory pressure |

The controller does not interact with LMKD directly.
Android's `ComponentCallbacks2.onTrimMemory()` provides memory-pressure
notifications to applications. The controller uses `/proc/meminfo`
and PSI as proxy signals.

## Integration Architecture

```
Frame Timing → FrameTimeMonitor → Controller
                                    │
                                    ↓
PressureMonitor ← CPU, Thermal, Memory, I/O, PSI
                                    │
                                    ↓
                         Bottleneck Classifier
                                    │
                                    ↓
                          Quality Policy Engine
                                    │
                                    ↓
                ┌───────────────────┼───────────────────┐
                ↓                   ↓                   ↓
          SystemUI           Launcher            Other Components
                │                   │                   │
          AdaptiveRender    AdaptiveRender    AdaptiveRender
```

## Sampling Strategy

### Level 0 — Normal (Production Idle)
- Frame timing: continuous (via Choreographer callback)
- CPU: 0.5 Hz (2s interval)
- Memory: 0.2 Hz (5s interval)
- Thermal: 0.2 Hz (5s interval)
- PSI: 0.2 Hz (5s interval)
- I/O: 0.2 Hz (5s interval)
- GPU: 0.2 Hz (5s interval IF available)

### Level 1 — Warning (Diagnostic Aware)
All metrics: 1 Hz (1s interval)

### Level 2 — Diagnostic (Pressure Identified)
All metrics: 2 Hz (500ms interval)

### Level 3 — Emergency (Severe Pressure)
All metrics: 5 Hz max (200ms interval)

## Event-Driven vs Polling

Where available, event-driven signals are preferred:

| Event                        | Source                       | Used For              |
|------------------------------|------------------------------|-----------------------|
| Frame callback               | Choreographer                | Frame timing          |
| Thermal change               | Thermal HAL broadcast        | Thermal state         |
| Memory pressure              | ActivityManager              | Memory pressure       |
| Process lifecycle            | ActivityManager              | Background work       |

When events are available, the controller reduces polling for that metric.
For example, if a thermal change event fires, the controller reads thermal state
immediately rather than waiting for the next polling tick.
