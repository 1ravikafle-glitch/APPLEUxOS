# Phase 2: Storage + RAM Baseline Report

## Status: IN PROGRESS — estimates pending actual measurement

This report provides estimated baselines. Actual measurements require a bootable build.

---

## Storage Baseline (Estimated)

### Typical AOSP 16 System Partition Sizes (reference device)

| Partition | Typical Size | AppleUX Target | Reduction Strategy |
|-----------|-------------|----------------|--------------------|
| system/ | ~4-5 GB | ≤3 GB | Remove unused apps/services |
| system_ext/ | ~1-2 GB | ≤500 MB | Minimal system_ext apps |
| product/ | ~500 MB-1 GB | ≤300 MB | Minimal product partition |
| vendor/ | ~2-3 GB | Device-dependent | Not applicable for GSI |
| odm/ | ~500 MB-1 GB | ≤300 MB | Minimal ODM |
| boot/ | ~32 MB-64 MB | ~32 MB | Standard |
| **Total** | **~8-12 GB** | **≤4 GB** | |

### Estimated Savings from Removal

| Component | Size | Can Remove |
|-----------|------|------------|
| CTS | ~2 GB | Yes |
| Development samples | ~1 GB | Yes |
| Test apps | ~200 MB | Yes |
| Demo apps | ~50 MB | Yes |
| Traceur, SpareParts | ~100 MB | Yes |
| Full Settings (replaced) | ~200 MB | Partial (AppleUX Settings) |
| Full SystemUI (replaced) | ~300 MB | Partial (AppleUX SystemUI) |
| PDK docs | ~100 MB | Yes |
| Unused external libs | ~500 MB | Yes (keep only needed) |
| Renderscript | ~200 MB | Yes |
| Voice interaction | ~300 MB | Yes (investigate GMS) |
| Text classification | ~100 MB | Yes |
| **Total potential savings** | **~5.2 GB** | |

### Storage Target

```
Goal: Core AppleUX OS ≤ 4 GB
Estimate after removals: ~3.5-4.5 GB
```

This is achievable through:
- Removing CTS and development packages
- Removing unused system apps
- Replacing full Settings/SystemUI with lighter AppleUX versions
- Removing unused external libraries, fonts, and frameworks
- Minimal vendor/ODM partitions

### Measurement Method (for later)

```bash
# Storage measurement
adb shell "du -sh /system /system_ext /product /vendor /odm"
adb shell "ls -la /system/app/ /system/priv-app/"
```

---

## RAM Baseline (Estimated)

### Typical Android 16 Baseline RAM Usage (3 GB device, idle after boot)

| Component | Estimated PSS | AppleUX Target | Notes |
|-----------|---------------|----------------|-------|
| system_server | ~450-550 MB | ≤300 MB | Primary reduction target |
| SurfaceFlinger | ~100-150 MB | ≤100 MB | Optimize composition |
| SystemUI | ~250-350 MB | ≤150 MB | AppleUX SystemUI lighter |
| Launcher | ~200-300 MB | ≤150 MB | AppleUX Launcher lighter |
| InputMethodService | ~80-120 MB | ≤80 MB | Keyboard |
| zRAM compressed | ~200-300 MB | ~250-350 MB | Tune for 3 GB |
| kernel | ~100-150 MB | ~100-150 MB | Optimize via kernel tuning |
| native daemons | ~100-200 MB | ≤100 MB | Remove unused daemons |
| **Total baseline** | **~1.3-1.8 GB** | **1.5-2.0 GB target** | |

### RAM Budget for 3 GB Device

```
Total RAM: 3072 MB
- Core OS baseline: ~1500-2000 MB (target)
- Available for apps: ~1000-1500 MB
- zRAM compressed cache: ~250-350 MB
```

### RAM Reduction Strategies

1. **Service consolidation**: Combine or stub unnecessary services (text services, content capture, autofill, etc.)
2. **AppleUX SystemUI**: Lighter than stock SystemUI — fewer services, simpler UI
3. **AppleUX Launcher**: Optimized for memory — less caching, simpler data structures
4. **Service lazy initialization**: Defer non-critical services until needed
5. **zRAM tuning**: Configure for optimal compression/decompress ratio on this CPU
6. **LMKD tuning**: Adjust thresholds for 3 GB configuration
7. **Background limit**: Restrict background processes more aggressively
8. **Preloading reduction**: Eliminate unnecessary preloading

### Measurement Method (for later)

```bash
# RAM measurement (run after idle for 1 minute)
adb shell "ps -A -o name,pss,rss --sort=-pss | head -30"
adb shell dumpsys meminfo
adb shell dumpsys system_memory
adb shell "cat /proc/vmallocinfo"
adb shell "cat /proc/meminfo"
```

---

## Disk Usage Tracking

### Current AOSP Source Sync (for audit)

```
frameworks/base:         ~1.8 GB
packages/apps/Launcher3:  ~36 MB
packages/apps/SystemUIGo: ~70 KB
packages/modules/Wifi:    ~38 MB
packages/modules/Bluetooth: ~67 MB
packages/modules/Connectivity: ~43 MB
packages/modules/Telephony: ~649 KB
packages/apps/CarrierConfig: ~2 MB
packages/apps/EmergencyInfo: ~2 MB
packages/providers/DownloadProvider: ~5 MB
packages/services/Telephony: ~2 MB
packages/modules/Nfc: ~8 MB
packages/modules/DeviceLock: ~2 MB
build/make: ~500 MB
Total synced: ~2.3 GB
```

### Remaining Budget

```
Total project disk budget: 42 GB
Used: ~3 GB (AOSP + tools + SDK)
Remaining: ~39 GB
```

Available space is sufficient for additional selective syncs and build outputs.
