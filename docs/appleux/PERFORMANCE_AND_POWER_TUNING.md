# AppleUXOS — PERFORMANCE AND POWER TUNING
### Hardware/software co-optimization for POCO M5 (`rock`): RAM, CPU, GPU, frequency, voltage, battery, thermal

> **CONFIDENTIAL — INTERNAL PROJECT DOCUMENT.** Do not publish or commit to a public repository.
> **Status: DRAFT v1 — researched, not yet measured.** Nothing here has been run on a phone. All numbers marked TBD must come from your own measurements.

Evidence tags: **[R]** public source (section 17) · **[U]** reported by the project owner · **[V]** verify on the device or in the source tree · **[D]** design decision proposed here · **[G]** general engineering knowledge, not looked up for this project.

---

## 1. What "iOS-level" can and cannot mean here

Apple's smoothness comes mostly from co-design: its own silicon, one scheduler tuned for one UI stack, fixed display modes, aggressive memory compression, and apps written to a small set of system priority classes. [G]

AppleUXOS cannot change the silicon. The Helio G99 is a 6 nm mid-range SoC with two Cortex-A76 and six Cortex-A55 cores and a two-core Mali-G57 [R]. What we can copy is the **discipline**:

| Apple-side practice [G] | AppleUXOS equivalent [D] |
|---|---|
| Work classified by priority class (user-interactive, utility, background) | Thread roles mapped to cpusets/uclamp and Android Performance Hint sessions |
| UI render path kept deterministic and short | RenderThread/UI thread pinned to A76 cores, no sync Binder or I/O in transitions, allocation-free animation loops |
| Memory compression plus a predictable low-memory policy | zRAM + lmkd tuned from measurement, cached-process limits, no swap-file "RAM expansion" by default |
| Display and touch tuned together | 90 Hz frame budget, per-surface refresh policy, touch boost via Power HAL |
| Thermal state drives quality, not surprises | Thermal API/headroom drives automatic effect downgrade before the kernel throttles |
| One tuned build for one device | Device-specific profile for `rock`, plus a separate low-RAM tier |

**Success criterion:** recorded numbers (janky frames, p99 frame time, launch times, memory, battery draw) compared to stock Android on the same phone, with the remaining gap to iOS-class smoothness stated honestly. No claim without a measurement.

## 2. Hardware facts for tuning

| Item | Value | Tag |
|---|---|---|
| SoC | Helio G99 (phone model number MT6789; MT8781 is the tablet-variant name), TSMC 6 nm | [R] |
| CPU | 2x Cortex-A76 up to 2.2 GHz + 6x Cortex-A55 up to 2.0 GHz | [R] |
| GPU | Arm Mali-G57 MC2 (Valhall, 2 cores); stock top clock reported ~1.0 GHz (community kernel notes say 1003 MHz locked on most G99 phones) | [R] |
| GPU frequency table | Community tools describe a 45-entry GPU OPP table handled by MediaTek `gpufreq`/GED drivers, with debug nodes under `/proc/gpufreqv2` and `/sys/kernel/ged` | [R][V] |
| Memory | LPDDR4X at 2133 MHz, 2x16-bit; sold with 4 GB RAM; UFS 2.2 storage | [R] |
| Display | 6.58", 2408x1080, 30/60/90 Hz DynamicSwitch, 240 Hz touch sampling | [R] |
| Sustained power | Third-party spec sites quote roughly 4-5 W TDP; no official figure found | [R][V] |
| Kernel version / GKI status | Unknown. Community work references both a GKI-style custom kernel for this phone and Android 16-era GKI 6.12 builds on MT6789 devices | [V] |
| Battery | 5000 mAh, 18 W fast charging (22.5 W adapter in box in some regions) | [R] |

First task on the real phone (read-only): record `uname -a`, `/proc/cpuinfo`, available cpufreq frequencies per policy, `/sys/class/devfreq` and `/proc/gpufreqv2` contents if present, `cat /proc/meminfo`, `zramctl`, thermal zones, `dumpsys battery`, and the active scheduler/governor. Save to `docs/appleux/logs/device_baseline_*.txt`.

## 3. Measurement discipline (applies to every section below)

1. **Baseline first.** Measure stock Android on the same phone before changing anything. [D]
2. **One change at a time**, then re-measure; keep or revert; log in `TUNING_LOG.md`. [D]
3. **Controlled conditions:** same ambient temperature, same battery level band, same brightness, airplane mode or fixed network, same app set, device cooled to the same starting temperature before each run. [D]
4. **Use Fixed Performance Mode for benchmarks** so dynamic clocking does not skew comparisons [R]; use normal dynamic mode for real-world results.
5. **Tools:** Perfetto, `dumpsys gfxinfo/meminfo/batterystats/SurfaceFlinger`, `am start -W`, simpleperf, thermal and power_supply sysfs. [G]
6. Report p50/p90/p99 and jank percentage, not averages alone.

## 4. RAM

### 4.1 Targets [D]
Design budget 3 GB (Tier A), supported floor 2 GB (Tier B); the phone's 4 GB is headroom. Per-process budgets are **TBD after baseline**.

### 4.2 Levers (all measured, none assumed)
- **zRAM:** size, compression algorithm (fast lz4-class vs denser zstd-class trades CPU for capacity) and swappiness tuned by measurement; verify page-fault and kswapd CPU cost on the A55 cores. [G][V]
- **lmkd:** on low-RAM builds it intentionally tolerates higher memory pressure; AOSP's Go defaults set `ro.config.low_ram=true` and tuned `ro.lmk.*` pressure properties, and memory cgroups are required. Start Tier B from these, Tier A from stock. [R]
- **Reclaim behavior:** check whether the kernel has multi-gen LRU available and enabled; it often improves behavior under pressure on newer kernels. [G][V]
- **Cached-app policy:** raise or lower cached-process limits against measured kill rates and re-launch times. The aim is iOS-like "apps resume without reload" without causing pressure stalls. [D]
- **ART:** `speed-profile` compilation for system server and system apps, profile-based boot image, baseline profiles for launcher/SystemUI/Settings (AOSP Go products use these). [R] Avoid dex2oat during interactive use; run it in idle maintenance. [G]
- **Allocator:** AOSP Go products disable Scudo outside eng builds to save RAM [R]; weigh against the hardening it provides before copying (security tradeoff, decide in Milestone 9). [D]
- **Footprint:** remove or disable unused preinstalled apps, avoid live wallpapers on low-RAM tiers, run the network stack in-process as Go builds do. [R]
- **Do not ship swap-file "RAM expansion" on UFS by default:** it adds flash wear and latency; offer only as a clearly labeled option after measurement. [D]
- **DRAM frequency:** handled by the SoC's own DVFS; do not hand-tune. [D]

### 4.3 Acceptance (TBD numbers)
Idle free memory, cached-app count after the standard app-cycle test, LMKD kills per hour, app relaunch time after eviction, and memory growth over a 4-hour soak, each compared to stock on the same phone.

## 5. CPU

### 5.1 Scheduler and governor
- Android kernels use Energy Aware Scheduling with the schedutil governor and utilization clamping (uclamp) to place tasks on the right core and influence frequency [R]. EAS depends on schedutil and an energy model; uclamp lets the system express performance floors/caps per task or group. [R]
- [V] Confirm which scheduler/governor/energy model the `rock` kernel actually uses (MediaTek kernels often add vendor scheduler extensions).

### 5.2 Core placement [D]
- A76 (big): UI thread, RenderThread, input dispatch, SurfaceFlinger, launcher/SystemUI foreground threads.
- A55 (little): background services, sync, indexing, dexopt, logging.
- Mechanism order: cpusets and uclamp via init/cgroup configuration first, Power HAL second, kernel changes last.

### 5.3 Performance hints and boosts
- **Performance Hint API:** system components (SystemUI, launcher) should create hint sessions with target frame durations so the platform can raise frequency or pick cores just in time, working with DVFS for better performance per watt. [R]
- **Power Efficiency Mode** (Android 15+) marks sessions that can prefer efficiency. [R]
- **Thermal API:** SystemUI reads thermal headroom/status and degrades effects before the kernel throttles. [R]
- **Power HAL boosts** for touch and app launch through the `rock` Power HAL; verify which hint types the vendor HAL implements. [V]
- Rule: boosts are short and event-driven; no permanent "performance mode" as the default.

### 5.4 Frequency policy
See section 7. Default is stock A76 2.2 GHz / A55 2.0 GHz tables. [R]

### 5.5 Other
IRQ affinity for display/touch to the A76 cluster, avoid CPU wakeups from system services at idle, and batch background work with JobScheduler. [G]

## 6. GPU (Mali-G57 MC2)

- The GPU is the tightest resource for iOS-style visuals. Window blur is documented as GPU-heavy, is opt-in per device (`ro.surface_flinger.supports_background_blur`), should keep radii low (docs warn above 150 px), and can be disabled at runtime (`wm disable-blur`). [R]
- **Rendering rules [D]:** flatten hierarchies, minimize offscreen/saveLayer passes, cache continuous-corner paths and blurred backdrops, avoid per-frame bitmap allocation, keep texture memory within the tier budget, and compare Vulkan vs GLES renderers on this GPU by measurement.
- **Adaptive quality [D]:** a frame-time governor in SystemUI lowers blur radius or swaps to a cached backdrop when GPU frame time exceeds budget for N consecutive frames, and restores it when headroom returns.
- **Display budget:** at 90 Hz the budget is about 11.1 ms per frame; at 60 Hz about 16.7 ms. Static surfaces should drop to the lowest refresh the panel supports (30/60/90 Hz DynamicSwitch) to save power. [R][D]
- **GPU DVFS:** governed by the MediaTek gpufreq/GED stack (see section 7 for frequency/voltage policy). [R]

## 7. Frequency and voltage policy (underclock, undervolt, overclock)

### 7.1 Facts from public sources [R]
- Most G99 phones cap the GPU near 1.0 GHz. Community members have unlocked higher GPU clocks on the POCO M5 with custom kernels. XDA thread: unlock by editing the kernel's GPU frequency table under the MediaTek power driver directory.
- Open community projects exist that patch the GPU OPP (frequency/voltage) table of the MT6789, and in at least one case the CPU top frequency entry, at runtime through out-of-tree kernel modules. One tool describes patching the kernel module binary itself to change the OPP table and bypass runtime calibration overwrites, extrapolating voltage from the top two entries. They report working configurations on their own units, but these results are specific to those units and kernels.

### 7.2 What this means for AppleUXOS [D]
1. **Shipped default = stock frequencies and stock voltages.** No overclock and no undervolt in the default image.
2. **Silicon varies between units.** An undervolt that is stable on your phone can crash another phone; an overclock that survives a short test can fail when hot or at low battery. A system image meant for other people must not assume your unit's margins.
3. **Do not ship runtime kernel-table patching modules** (unsigned out-of-tree modules patching vendor driver tables). It conflicts with the project's security rules (kernel integrity, module signing, SELinux) and is fragile across kernel updates. If a frequency change is ever adopted, it is done properly in the kernel source's OPP tables, built into a signed kernel/module set.
4. **Three profiles, user-selectable, in this order of priority:**

| Profile | What it does | Default? |
|---|---|---|
| **Stock/Balanced** | Vendor OPP tables and governors, AppleUXOS scheduling and hints | Yes |
| **Efficiency** | Lower frequency caps (underclock) on A76 and GPU for battery and thermals; no voltage changes | Opt-in; battery-saver and low-battery auto-enable |
| **Experimental** (developer builds only) | Per-unit OC and/or undervolt experiments | Never default; hidden behind developer options; never in release images |

5. **Underclock/efficiency caps are the safe direction** and are the first thing to test: they reduce heat and battery draw, and cannot push the silicon past vendor-validated points. Undervolting is the second step (stability risk but not over-spec); overclocking is last (heat, current and lifetime risk).

### 7.3 Guardrails for any experiment (developer builds)
- Start from the highest validated stock OPP and move in small steps; record frequency, voltage, temperature, and result for every step in `TUNING_LOG.md`.
- Validate with long stress (CPU, GPU, memory, mixed), at low battery and at elevated temperature, plus the standard UI scenario, before calling a setting stable. Crashes, reboots, GPU hangs, artifacts or `dmesg` gpufreq errors fail the step.
- **Automatic revert:** a watchdog resets to stock if the system did not complete boot or a heartbeat within a set time, so a bad setting cannot soft-brick the phone.
- **Hard ceilings:** thermal limit stays in force; if skin or SoC temperature crosses the vendor thresholds, the experimental profile is dropped immediately.
- **Never raise limits** for charger or battery protections (section 8).
- Overclock gains must be reported as measured improvements in jank/frame time against the energy cost, not as raw clock numbers.

## 8. Battery and voltage

### 8.1 Hard rules [D]
- **Do not raise battery charge voltage, charge current, or any charger/gauge protection threshold above the vendor's specification.** Lithium-ion cells can overheat, swell or ignite when overcharged; nothing in this project changes those limits upward.
- Charging control stays in the vendor charger driver/HAL. AppleUXOS only reads values and requests *lower* limits through supported interfaces. [V]

### 8.2 What AppleUXOS does instead [D]
- **Read-only monitoring:** battery voltage, current, temperature, state of charge and health via the standard `power_supply` interface and `dumpsys battery/batterystats`; sampled in test runs to produce power-per-scenario numbers. [G][V]
- **Charge-limit option (for example stop at 80%)** to slow battery aging, implemented only if the vendor charger exposes a supported limit; otherwise documented as unavailable. [V]
- **Thermal-aware charging:** reduce charging current while hot via the existing thermal framework, not by overriding it. [G][V]
- **Brownout protection:** peak current rises with frequency, and battery voltage sags under load, especially when cold or low. The system must keep any CPU/GPU boost conditional on battery state: below a low-charge band or under measured voltage sag, boosts and the Experimental profile are disabled and the Efficiency caps apply. MediaTek platforms commonly include their own low-battery and power-budget throttling in the kernel; keep it enabled and verify what `rock` has. [G][V]
- **Test:** load-step test at low state of charge and at low temperature to confirm no unexpected shutdown with the shipped profile. [D]

### 8.3 Battery acceptance (TBD numbers)
Idle drain per hour (screen off, airplane), screen-on scenario drain (scroll, video, mixed), standby drain overnight, and charging temperature; each against stock on the same phone.

## 9. Display and touch [D]
Per-surface refresh policy (use the lowest suitable rate for static content, 90 Hz during animation/scroll), touch boost on first touch-down through the Power HAL, and verification that the 240 Hz touch rate [R] actually reaches the input pipeline without added queueing.

## 10. Thermal [D]
- Map the phone's thermal zones and the vendor thermal HAL/config; record throttle thresholds. [V]
- Test sustained scenarios (10-30 minutes) and record clock vs time to find real sustained performance; sustained is what users feel in games and long scrolling, not the first minute.
- Thermal API headroom feeds the SystemUI quality governor (section 6) so the UI degrades gracefully before the kernel clamps clocks. [R][D]

## 11. Storage and I/O [D]
UFS 2.2 [R]. Keep dexopt, backups and indexing in idle maintenance windows; avoid large synchronous writes during interactions; verify the filesystem and I/O scheduler choices on the device by measurement.

## 12. Per-tier profile summary [D]

| Setting | Tier A (3-4 GB) | Tier B (2 GB class) |
|---|---|---|
| Product config | Stock-derived, AppleUXOS tuned | Android Go-style low-RAM defaults [R] |
| Blur/translucency | Selected surfaces, governed by frame time | Off; tinted opaque or cached backdrops |
| CPU/GPU profile | Balanced; Efficiency available | Efficiency by default |
| Cached apps / zRAM | Tuned from measurement | More aggressive, tuned from measurement |
| Animation complexity | Full | Reduced |

## 13. Test plan and acceptance
Standard scenarios (each repeated at least 5 times, same conditions): cold/warm app launch for 10 apps, home-screen scroll and page swipe, app open/close transitions, notification shade and Control Center pull, app switcher with 5/10 apps, lock/unlock, keyboard open, 30-minute mixed use, 4-hour soak, overnight standby. Metrics: janky-frame %, p50/p90/p99 frame time, launch time, memory (free/cached/kills), power per scenario, thermal curves. All targets are set after the stock baseline and recorded as numbers in `TUNING_LOG.md`; releases are blocked on regressions.

## 14. Risk register
> Scored, device-specific risks and gating rules: **`RISK_REGISTER.md`** (A9, A10, A20, A21 and B11-B13 cover tuning). The table below is a summary.

| Risk | Impact | Control |
|---|---|---|
| Overclock/undervolt instability across units | Crashes, data loss | Stock default, watchdog revert, developer-only |
| Heat and battery stress from higher clocks | Hardware wear, safety | Thermal ceilings, never exceed vendor charge limits |
| Runtime kernel patching | Security and fragility | Source-level OPP changes only if ever adopted |
| Vendor kernel/HAL limits on tunables | Plan cannot be applied | Audit early, adapt |
| Tuning on one unit | Does not generalize | Test on more than one phone if possible |
| Aggressive RAM tuning causing stalls | Jank | Measure stalls (PSI), revert on regression |
| Disabling hardening for memory/speed | Security regression | Security review before adopting |

## 15. Implementation order (maps to roadmap)
1. **M0/M8:** read-only device baseline (section 2) and kernel/HAL capability audit.
2. **M1:** Tier A and Tier B product configs, stock baseline measurements.
3. **M3-M4:** frame-time governor, hint sessions in SystemUI/launcher, cpuset/uclamp placement.
4. **M9:** zRAM/lmkd/ART tuning loop, Efficiency profile, battery and thermal tests, then (optionally) developer-only Experimental profile.

## 16. Open items [V]
Kernel version and scheduler type; whether the vendor Power HAL exposes touch/launch hints; GPU/CPU OPP tables on this exact unit; thermal zone map; charger limit interface; presence of MediaTek low-battery throttling; MGLRU availability; sustained power limits.

## 17. Sources (researched 19 Oct 2026)
- Android Dynamic Performance Framework (thermal API, hint sessions, fixed performance mode, power efficiency mode): https://developer.android.com/games/optimize/adpf
- Performance Hint API (AOSP): https://source.android.com/docs/core/perf/performance-hint-api
- lmkd and low-RAM configuration: https://source.android.com/docs/core/perf/lmkd
- Android Go defaults: https://android.googlesource.com/platform/build/+/3021218d2a/target/product/go_defaults_common.mk
- Energy Aware Scheduling, schedutil, uclamp background: https://developer.arm.com/community/arm-community-blogs/b/architectures-and-processors-blog/posts/energy-aware-scheduling-in-linux
- Window blurs: https://source.android.com/docs/core/display/window-blurs
- Helio G99 specs: https://nanoreview.net/en/soc/mediatek-helio-g99 and https://www.mi.com/uk/product/poco-m5/
- Community GPU/CPU frequency work for MT6789 (reference only, unaudited): https://xdaforums.com/t/overclocking-mediatek-helio-g99.4675986/ , https://github.com/Anomali1304/Overclock_MT6789 , https://github.com/af-t/mt6789-gpu-oc , https://pypi.org/project/mtk-gpu-oc/

---

## ACTUAL STATE (2026-10-19) — implemented levers + honest numbers

**Implemented (source, compiled where noted):**
- Adaptive Performance Controller: Q100→Q70 (max 30% reduction), 30-frame
  rolling window, jank thresholds at the FRAME BUDGET scaled by
  `FrameBudget.forRefreshRate()` (60 Hz canonical: 14/16.67/20 ms → 9.33/
  11.11/13.33 at 90 Hz), WARNING/DIAGNOSTIC/EMERGENCY levels, recovery
  (5 s stable, 2 s dwell), oscillation guard, monitoring decay 2s→L2→L1→L0.
- Blur discipline: RenderEffect on glass surfaces only, gated by quality
  level; at most one blurred surface at a time on the 2 GB floor.
- ART/dalvik tuned for the build host in device.mk; per-process budgets TBD.

**3-mode power design (documented, kernel-gated):** Performance / Normal /
Auto with Battery Saver as an automatic override (triggers: low battery
20% configurable, manual, hot, charging-recovery; idle-on-lock ≠ full saver —
calls/alarms/background must survive). Auto adjusts frequency CEILINGS and
boost behaviour, never pins clocks; kernel DVFS stays enabled. Implementation
waits on kernel/CPUFreq interface work.

**Measured numbers: NONE on device.** Emulator figures are SwiftShader
(Launcher p50 150 ms, 100% janky) — capture-baseline only. Every earlier
projected figure is a hypothesis. First real numbers come from the POCO M5
after M8 (device connected: serial `nb79nvba65tse6ug`).