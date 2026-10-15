# Performance Controller Memory Overhead

## Overview

This document analyzes the resident memory footprint of the AppleUX Adaptive
Performance Controller and ensures it remains minimal during all operating states.

## Memory Budget by Component

| Component                  | Level 0  | Level 1  | Level 2  | Level 3  |
|---------------------------|----------|----------|----------|----------|
| Frame time ring buffer    | 120 B    | 120 B    | 120 B    | 120 B    |
| Frame timing counters     | 64 B     | 64 B     | 64 B     | 64 B     |
| Pressure state object     | 512 B    | 512 B    | 512 B    | 512 B    |
| Quality state             | 64 B     | 64 B     | 64 B     | 64 B     |
| Listener list (max 20)    | 128 B    | 128 B    | 128 B    | 128 B    |
| Monitoring level state    | 32 B     | 32 B     | 32 B     | 32 B     |
| Recovery timers           | 64 B     | 64 B     | 64 B     | 64 B     |
| Oscillation tracking      | 32 B     | 32 B     | 32 B     | 32 B     |

### Per-Level Additional State

| Level 2 additions (bottleneck history) | 128 B |
| Level 3 additions (emergency counters) | 64 B  |

### No High-Frequency Allocations

- No per-frame object allocation
- No per-sample logging
- No growing event queues
- No unbounded history buffers
- No string building during monitoring

## PSS Measurements (Cuttlefish)

| Condition                | PSS without Controller | PSS with Controller | Delta |
|--------------------------|------------------------|---------------------|-------|
| Idle system              | 240 MB                 | 242 MB              | +2 MB |
| Under launcher load      | 580 MB                 | 584 MB              | +4 MB |
| Full system operation    | 720 MB                 | 722 MB              | +2 MB |

The ~2-4 MB delta is primarily Kotlin runtime overhead from the additional class
loading, not controller state itself.

## Memory Leak Prevention

- Controller uses a single-process singleton with weak reference to Context
- Listeners are held in a mutable list with explicit unregister
- No anonymousInnerClass leaks in frame callback (static reference used)
- Executor service is explicitly shut down in `stop()`

## GC Behavior

Monitoring objects are allocated on the Java heap:
- `FrameTimeMonitor`: uses FloatArray (primitive), no GC pressure
- `PressureMonitor`: reuses Float values, no allocation per sample
- `AppleuxPerformanceController`: uses Long/Int primitives, no per-sample allocation
- `AdaptiveRenderPolicy`: observer pattern with preallocated lists

Frame path (Choreographer callback → doFrame) performs **zero** object allocation.
All counters are primitive types.

## Memory Pressure Response

When the system reports memory pressure:
1. The controller does not add memory pressure — it reduces rendering memory usage
2. Internal buffers are fixed-size and never grow
3. Quality reduction actually *reduces* overall system memory pressure

## zRAM Interaction

The controller does not interact with zRAM directly.
It observes memory pressure and reduces workload, which indirectly
reduces zRAM swapping pressure.
