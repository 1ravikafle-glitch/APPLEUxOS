package com.appleux.performance.quality

enum class BottleneckState {
    NORMAL,
    CPU_BOUND,
    GPU_BOUND,
    THERMAL_BOUND,
    MEMORY_BOUND,
    IO_BOUND,
    MIXED_PRESSURE,
    SEVERE_PRESSURE
}
