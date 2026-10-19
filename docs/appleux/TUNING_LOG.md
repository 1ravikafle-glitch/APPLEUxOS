# AppleUXOS — TUNING LOG
> CONFIDENTIAL. One row per experiment. Never record a setting as "stable" without the stress result.
> Rules and guardrails: `PERFORMANCE_AND_POWER_TUNING.md` sections 3, 7, 8.

## Device baseline (fill once)
| Item | Value |
|---|---|
| Unit ID / build | |
| Kernel version | |
| Governor / scheduler | |
| CPU frequencies (A55 / A76) | |
| GPU frequencies (OPP table) | |
| zRAM size / algorithm | |
| Thermal zones | |

## Experiments
| Date | Build | Tier | Change (one variable) | Freq/voltage if any | Scenario | Jank % | p99 ms | Launch ms | Free mem | Power/Temp | Result (keep/revert) | Notes |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| | | | stock baseline | stock | | | | | | | baseline | |

## Failures and reverts
| Date | Setting | Symptom (crash, hang, artifact, dmesg error) | Action |
|---|---|---|---|
| | | | |

---

## Experiments (2026-10-18/19) — build-host tuning

| # | Change | Result | Verdict |
|---|---|---|---|
| 1 | cgroup MemoryMax/MemorySwapMax under systemd-run | every capped run died to oomd/cgroup kill; empty logs (output went to journal) | REVERTED — direct shell wins |
| 2 | MemoryHigh=8192 split (reclaim cache) | caused the reclaim-thrash that ARMED oomd (run 7, 51 min, machine had 12 G free) | REVERTED — align MemoryHigh with MemoryMax or drop |
| 3 | GOMEMLIMIT 7→10→8 GiB via env | NO-OP — systemd-run/`env -i` never delivered it to soong_build | Fixed: write INSIDE the wrapper |
| 4 | ManagedOOM*=none via `-p` | invalid on transient user units | drop-in file works (stable unit name) |
| 5 | Direct shell, GOMEMLIMIT=8GiB inside wrapper, emulator dead, nothing else running | **m SystemUI: 32 min, 0 errors** | **ADOPTED** |

Guest/runtime experiments: Android 17 CF guest + our 16 SystemUI → crash-loop
(`NoSuchMethodError sysuiLargePerfettoShmemBuffer`) → REVERTED; framework-matched
guest required. Emulator perf numbers (SwiftShader, not device-representative):
Launcher 100% janky p50 150 ms; Settings 95% janky p50 150 ms — baseline only.

## Device baseline
Not yet filled — first real POCO M5 measurements pending (device connected
2026-10-19, serial `nb79nvba65tse6ug`, still stock MIUI).