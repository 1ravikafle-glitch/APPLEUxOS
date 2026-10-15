# Hardware Adaptation

## Overview

The AppleUX Adaptive Performance Controller uses tiered thresholds optimized
for the reference 3 GB low-end hardware, with adaptive scaling for more
capable devices.

## Reference Hardware Profile

```text
RAM:       3 GB
Storage:   32 GB eMMC 5.1
Display:   1080p @ 60 Hz
CPU:       7-core (2×2.0 GHz + 5×1.6 GHz)
SoC:       Below Helio G99 class
```

## Hardware Detection

The controller identifies hardware capability at boot and selects an
appropriate threshold profile:

| Profile      | RAM     | SoC             | Threshold Multiplier |
|--------------|---------|-----------------|---------------------|
| LOWEND_3GB   | 3 GB    | Below G99       | 1.0x (baseline)    |
| LOWEND_4GB   | 4 GB    | G99 class       | 1.1x               |
| MIDRANGE_6GB | 6 GB    | SD 7-series     | 1.2x               |
| MIDRANGE_8GB | 8 GB    | SD 8+ series    | 1.3x               |
| FLAGSHIP     | 12+ GB  | SD 8 Gen 1+     | 1.5x               |

## Threshold Scaling

### Frame Time Thresholds

| Profile   | Healthy  | Warning  | Missed   | Severe   |
|-----------|----------|----------|----------|----------|
| LOWEND_3GB| ≤14.0 ms | >14, ≤16.67 ms | >16.67 ms | >20 ms |
| FLAGSHIP  | ≤12.0 ms | >11, ≤14 ms | >14 ms | >18 ms |

### Escalation Jank Counts (30-frame window)

| Profile   | Warning | Diagnostic | Emergency |
|-----------|---------|------------|-----------|
| LOWEND_3GB| ≥2      | ≥5         | ≥10       |
| FLAGSHIP  | ≥2      | ≥5         | ≥10       |

### CPU Utilization Thresholds

| Profile   | Light Pressure | Heavy Pressure |
|-----------|----------------|----------------|
| LOWEND_3GB| >85%           | >90%           |
| FLAGSHIP  | >85%           | >90%           |

### Memory Pressure Thresholds

| Profile   | Light | Heavy |
|-----------|-------|-------|
| LOWEND_3GB| >80%  | >90%  |
| FLAGSHIP  | >85%  | >95%  |

### Thermal Thresholds

| Profile   | Warm  | Hot   | Severe |
|-----------|-------|-------|--------|
| LOWEND_3GB| 65°C  | 75°C  | 85°C   |
| FLAGSHIP  | 70°C  | 85°C  | 95°C   |

### GPU Pressure Thresholds

| Profile   | Light | Heavy |
|-----------|-------|-------|
| LOWEND_3GB| >80%  | >92%  |
| FLAGSHIP  | >85%  | >95%  |

### Recovery Dwell Times

| Profile   | Recovery Stable Window | Dwell Between Steps |
|-----------|------------------------|---------------------|
| LOWEND_3GB| 5 seconds              | 2 seconds           |
| FLAGSHIP  | 5 seconds              | 1.5 seconds         |

## Render Resolution Scaling

Render resolution scaling is consistent across all hardware profiles:

| Quality | Scale  |
|---------|--------|
| Q100    | 1.00x  |
| Q95     | 0.95x  |
| Q90     | 0.90x  |
| Q85     | 0.90x  |
| Q80     | 0.85x  |
| Q75     | 0.80x  |
| Q70     | 0.75x  |

The 30% maximum quality reduction is a hardware-agnostic upper bound.

## Battery-Aware Adaptation

When the device is on battery (not charging):

| Modification                  | Effect |
|------------------------------|--------|
| Emergency sampling reduced   | 3 Hz instead of 5 Hz |
| Thermal thresholds lowered   | -2°C   |
| Quality recovery slower      | +30%   dwell time |

## Adaptive Tuning Process

Hardware-specific tuning requires:

1. Benchmark on the target device (Phase 17)
2. Measure baseline frame times for each workload
3. Identify the quality level where frame pacing stabilizes
4. Adjust thresholds to reduce unnecessary quality degradation
5. Verify no regression in touch latency or input responsiveness
6. Document all tuning changes with measured data

## Per-SoC Profiles

Specific SoC profiles are maintained for:

- MediaTek Helio G80/G85/G99
- Qualcomm Snapdragon 665/680/695/720G/732G/778G/8 Gen 1/8 Gen 2/8 Gen 3
- Samsung Exynos 9611/990/1080/2100/2200
- Unisoc T610/T618

Each profile includes:
- GPU utilization path (vendor-specific sysfs paths)
- Thermal zone layout
- CPU core configuration
- Default performance thresholds