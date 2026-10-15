# AGENTS.md — AppleUX Project

## Project Overview

AppleUX is a new OS built on Android 16, heavily inspired by iOS for design,
smooth UI, and performance — engineered for the Xiaomi Rock (POCO M5 /
Redmi 11 Prime 4G): 4 GB RAM, 64 GB internal storage, MediaTek Helio G99
(Cortex-A76 + A55, arm64).

Engineering rule: budget for **3 GB RAM** (low-end floor) so the OS runs
smoother and faster on the 4 GB device. All memory/perf targets in this file
are 3 GB numbers unless stated otherwise.

Design directive (locked): every UI decision follows iOS — Liquid-Glass-like
translucent materials, squircle iconography, SF-Symbol-weight iconography,
grouped Settings cards, spring-physics motion (see `MotionTokens.kt`), and
60fps smoothness via the Adaptive Performance Controller. Responsiveness and
consistency outrank visual excess; effects must degrade gracefully under
pressure (Q100 → Q70).

Replace-from-source strategy (locked): change the original Android 16 source
to the iOS 27 design instead of adding parallel duplicates. Verify ours works,
THEN delete the stock version — one surface at a time until no stock remains:
launcher→Launcher3, Settings→Settings, framework-res→RRO values, sounds,
boot animation. Never delete before the replacement is verified; never break
functionality for looks (charter priority order stands). Recents stays with
QuickStep until our task-switcher exists; stock Settings stays as intent
fallback until ACTION_SETTINGS coverage is audited.

## Project Root — Authoritative

**All work happens under `/home/elfakiris/OsProject`.** Never
operate from `/tmp` or any other location. This is the default and
only project root.

Important: `/home` and `/media/elfakiris/ElfakIris1` are **separate filesystems**.

| Path | Device | Total | Free (2026-10-14) | Use |
|---|---|---|---|---|
| `/` + `/home` (project) | `/dev/nvme0n1p4` | 738 GB | ~408 GB | 43% |
| `/media/elfakiris/ElfakIris1` (external) | `/dev/nvme0n1p3` | 137 GB | ~24 GB | 83% |

Check space on the project drive:

```bash
df -h /home/elfakiris/OsProject
```

## Project Structure

```
/media/elfakiris/ElfakIris1/OsProject/
├── android-16/           AOSP source (selective sync)
├── android-sdk/          Android SDK command-line tools
├── appleux/              AppleUX source tree
│   ├── design-system/    Shared design tokens and components
│   ├── performance/      Adaptive Performance Controller
│   ├── systemui/         SystemUI replacement
│   ├── launcher/         Launcher replacement
│   ├── settings/         Settings app replacement
│   └── motion/           Animation engine
├── cuttlefish/           Cuttlefish emulator source
├── tools/                Build tools, JDK, repo
├── backups/
├── builds/
├── device-support/       Device-specific adapters
├── downloads/
├── logs/
└── deliverables/         Phase deliverables (m1, m2, ... m18)
```

## Environment Setup

Before working:
```bash
source /media/elfakiris/ElfakIris1/OsProject/tools/envsetup.sh
```

Environment variables:
- `JAVA_HOME` → local JDK 21
- `ANDROID_HOME` → local SDK
- `AOSP_ROOT` → android-16/
- `CUTTLEFISH_ROOT` → cuttlefish/source/
- `REPO_TOOL` → tools/repo

## Device facts — MEASURED, authoritative

Captured 2026-10-15 by the operator on the **physical POCO M5 running stock
HyperOS**. These supersede every earlier figure in this project, including ones
that had already been written into code.

| | value |
|---|---|
| Panel | **1080 × 2408 px** (not 2400) |
| Density | 440 physical, **HyperOS override 430 — 430 is the effective value** |
| Logical frame | **401.9 × 896.0 dp** (at 440 with no override: 392.7 × 875.6) |
| Top inset | **76 px = 28.3 dp** — status bar and waterdrop cutout are the same 76 px |
| Bottom inset | **127 px = 47.3 dp** |
| Cutout | **100 × 76 px, top-centre**, centre x = 540 = exactly 1080/2 |
| Refresh | **30 / 60 / 90 Hz**, switches at runtime |

Full capture and the corrections it forced:
`deliverables/m20/rock-display-measurement.md`. The refresh-rate list is from
the published panel spec, not from that capture — confirm with
`adb shell dumpsys display | grep -iE 'fps|refresh|mode'`.

Consequence: the `device-support/rock` fallbacks are now measured, and the
home-grid derivation (which assumed 411.43 x 914.29 dp) **needs re-deriving**
at 401.9 x 896.0 dp.

## Design sources — which one wins

`design/spec/SOURCES.md` is the authority. Short version:

1. **Measured Apple Figma kit** → material, colour, typography.
2. **files5 / IOSLikeROM** (`design-reference/inbox/files5-IOSLikeROM/`) →
   **layout**. `tools/layout_check.py` prints `OK`; `tools/make_profile.py`
   regenerates the whole spec from measured device values.
3. **`design/spec/ios27-baseline.md`** → **inventory** (630 surfaces), not
   geometry.

The ios27-android16 specification was adopted as the design baseline with 12
cited corrections; its token file disagreed with the measured Apple kit on 5 of
6 comparable Liquid Glass fields. Review: `deliverables/m20/intake-review-2026-10-15.md`.

## Key Constraints

1. **Disk**: 39 GB free on the project drive. Full AOSP sync + build (~200 GB)
   does not fit. Use selective sync (`repo sync -c` on specific projects), and
   build only what is needed. Never build on `/`.
2. **RAM**: 8 GB total, ~3.8 GB available. Use `-j2` or lower for builds.
3. **No sudo**: Packages must be self-contained; JDK downloaded locally
4. **No Docker**: Cloud builds must be external
5. **No physical device yet**: UI verification goes through the Android SDK
   emulator first; real 4 GB POCO M5 hardware testing comes later

## Development Workflow

1. Inspect existing code before changing
2. Make smallest coherent change
3. Build/test affected component
4. Measure performance where applicable
5. Document decisions in deliverables/

## Performance Philosophy

- Responsiveness > visual effects
- Consistency > complexity
- Measurement > intuition
- Minimalism > bloat

## Phase Status

Current phases:

- **Phase 2** — Android 16 Component Audit + Minimal Core (completed)
- **Phase 3** — AppleUX Design System (source complete: tokens, motion engine, components)
- **Phase 4** — AppleUX SystemUI Replacement (source complete: service, shade, control center)
- **Phase 5** — AppleUX Launcher (source complete: grid, dock, app library, gestures)
- **Phase 6** — AppleUX Settings (source complete: catalog, preferences, live performance readout)
- **Phase 7** — Build Verification (BLOCKED via Soong/Cuttlefish — see below)
- **Phase 12** — Adaptive Performance Controller (source complete: frame monitor, pressure detection, quality levels, recovery)

### Build status

**All five SDK-buildable modules compile (design-system, performance,
launcher, settings, systemui) — `scripts/build-emulator-apks.sh` → 
`out/emulator/apks/*.apk`, verified on the API 36 emulator.**

AOSP/Soong route (2026-10-14): unblocked through analysis.
`lunch aosp_rock-bp4a-eng && m -j2 nothing` now completes Soong analysis and
enters the ninja build phase. Fixes so far: `export_cflags`→`cflags`,
`generated_kernel_headers` dedup, bp4a release wiring, lineage-interfaces
namespace, kotlin-plugin/vibrator-service/IMS-blob disables, overlay-path
panic, AppleUX `libs` cleanup. Full trail in
`deliverables/m7/soong-unblock-log.md`. No device images built yet — no
Cuttlefish/physical-device verification. Cuttlefish prebuilt guest+host
(build 16373615) staged at `cuttlefish/dl/` as the interim CF path.
STANDING BLOCKER (2026-10-14, kernel-proof): whole-tree Soong analysis needs
~11-12GB anon RSS and is OOM-killed twice on this 15GB box (desktop+qemu+
opencode hold ~5GB). `-j1`/`GOGC=20` don't help — it is live state, not
parallelism. Full `m` needs a serialized run (qemu+browser stopped) or an
external builder; see `deliverables/m7/soong-unblock-log.md` §10.

The emulator route works. `scripts/build-emulator-apks.sh` drives `aapt2`,
`kotlinc`, `d8` and `apksigner` directly against `android-sdk/` — no Gradle —
and produces `out/emulator/apks/*.apk`. `scripts/fetch-androidx-deps.py`
resolves the AndroidX closure from Maven POMs into `thirdparty/`.

These are **emulator** measurements. SwiftShader software rendering has no GPU,
so the frame numbers below say nothing about the 4 GB target device:

| App | Frames | Janky | p50 | p90 | p95 |
|---|---|---|---|---|---|
| Launcher | 43 | 43 (100%) | 150 ms | 350 ms | 450 ms |
| Settings | 20 | 19 (95%) | 150 ms | 200 ms | 200 ms |

Percentiles move run to run under SwiftShader; re-run the capture scripts
rather than quoting these as stable values.

Do not claim a measured performance number for the device. `deliverables/m12/`,
`m17`, and `m18` contain **projected** numbers from design-time analysis, not
measurements. Treat them as hypotheses.

`deliverables/m7/` holds the AOSP/Cuttlefish blocker and the sync/build steps
that would unblock it. `tools/capture-metrics.sh` and
`tools/capture-evidence.sh` regenerate the emulator numbers and screenshots.

## Performance Controller Architecture

The AppleUX Adaptive Performance Controller (`appleux/performance/`) uses:

- `FrameTimeMonitor.kt` — 30-frame rolling window, jank detection via Choreographer callbacks
- `PressureMonitor.kt` — CPU/GPU/thermal/memory/PSI/I/O telemetry from /proc and /sys
- `AppleuxPerformanceController.kt` — central policy engine with 4 monitoring levels
- `AdaptiveRenderPolicy.kt` — quality state bridge for component observers
- `MonitoringLevel.kt` — sampling rate table (0.2Hz–5Hz)
- `quality/AppleuxQualityLevel.kt` — Q100 → Q70 quality tiers
- `integration/AppleuxPerformanceService.kt` — per-component state mapping

### Key Thresholds

These are canonical **as the 60 Hz readout**. Any document or comment that
disagrees is wrong. The rock panel also runs at **30 Hz and 90 Hz**, so these
numbers only describe one of three modes: the values below are scaled from the
live refresh rate by `FrameBudget.forRefreshRate()` —
28.00/33.33/40.00 ms at 30 Hz, 14.00/16.67/20.00 ms at 60 Hz,
9.33/11.11/13.33 ms at 90 Hz. Nothing in the policy engine reads the
constants; they exist as the documented 60 Hz case.

- **Good frame**: ≤14 ms
- **Warning**: >14, ≤16.67 ms
- **Jank (missed deadline)**: >16.67 ms
- **Severe**: >20 ms
- **Rolling window**: 30 eligible frames
- **WARNING**: ≥2 janked/30
- **DIAGNOSTIC**: ≥5 janked/30 OR 3 consecutive severe
- **EMERGENCY**: ≥10 janked/30 OR 5 consecutive severe
- **Quality range**: Q100 → Q95 → Q90 → Q85 → Q80 → Q75 → Q70 (max 30% reduction)
- **Quality-down dwell**: ≥1 s per level
- **Recovery**: 5s stable for normal, 10s for thermal, 2s dwell between steps
- **Monitoring decay**: 2s → L2, +3s → L1, +5s → L0
- **Oscillation guard**: boundary crossed twice in 10s → hold lower quality 5s
- **Sampling**: L0 CPU 0.5Hz / others 0.2Hz; L1 1Hz; L2 2Hz; L3 5Hz cap.
  Frame timing is continuous via Choreographer, never polled.
- **Minimum valid frame-timing sample**: 300 frames

## Settings Wiring — Verified

The Settings app and the Launcher are separate processes, so shared flags need
a store both can reach. **`Settings.System` cannot be used for this on Android
16**: the platform throws
`IllegalArgumentException: You cannot keep your settings in the secure settings`
on any app write to that table, which crashes the Settings UI. The shared store
is a signature-guarded ContentProvider instead:

- `appleux/settings/.../config/AppleuxConfigProvider.kt` — authority
  `com.appleux.settings.config`, backed by the Settings app's private prefs.
- Permission `com.appleux.settings.permission.READ_APPLEUX_CONFIG`,
  `protectionLevel="signature"`. Both APKs are signed with `tools/debug.keystore`,
  so the launcher is granted it and nothing else is.
- `AppleuxRuntimeConfig` reads each flag through its **per-key** URI
  (`content://authority/<key>`). Querying the authority root returns every key,
  and taking the first row silently aliases all four flags to whichever was
  written first.
- `Settings.System` remains a read-only fallback, so values seeded with
  `adb shell settings put` still work, and defaults apply when a key is absent.
- Brightness and auto-brightness are real platform settings and still go to
  `Settings.System`; they need the user-granted `WRITE_SETTINGS` appop, which is
  now requested on demand rather than on every `onResume`.

Verified on the API 36 emulator by toggling in the Settings UI and observing the
launcher process: `adaptive_performance` and `low_power_mode` both start and
stop `AppleuxPerformanceController` (16 vs 0 pressure polls over 8s), and
`dark_mode` repaints the launcher (sampled `(0,0,0)` vs `(245,245,247)`).

## Other Known Gaps

- Dock contents **are** now persisted (`DockPreferences`, shared prefs
  `appleux_launcher_dock`, delimiter-encoded so empty slots are representable).
  Resolved once by explicit package allowlist → exact label → platform role
  intent, never positional. Open: the home-grid metrics behind it were derived
  on a superseded frame (411.43 x 914.29 dp) and need re-deriving at
  401.9 x 896.0 dp.
- The Notification Center still does not reserve the cutout;
  `AppleuxDisplayInsets.applyTopInset(context, column)` in
  `AppleuxNotificationShade.init` is a one-liner that has not been applied.
- `reduce_motion` is stored and propagated, but no component consumes it yet.
- Brightness writes need the user-granted `WRITE_SETTINGS` appop; without it the
  slider silently does nothing.

## Touch Handling

`GestureHandler` observes the touch stream in
`GestureHostLayout.dispatchTouchEvent` instead of adding a transparent overlay.
An overlay must be clickable to receive events, so it sits above the grid and
dock and swallows every tap, which made app icons impossible to launch. The host
observes without consuming, so grid and dock taps keep working.

Swipes are measured directly from DOWN/UP rather than through
`GestureDetector.onFling`: the detector never reported a fling for a full-height
emulator swipe because it had not left its "tap region". The decision is made on
`ACTION_UP` **and** `ACTION_CANCEL` — a scrolling child converts the rest of the
gesture to `ACTION_CANCEL`, which is what happened while the library was open.

`AppleuxHaptics` must never throw. A missing `VIBRATE` grant previously killed
the launcher on every swipe with `SecurityException: vibrate`; all vibration
calls are now best-effort and the manifest declares `VIBRATE`.

## Kilo Config

- `.kilo/command/measure-frames.md` — captures real gfxinfo/SurfaceFlinger
  evidence. Use before quoting any frame-timing number.
- `.kilo/agent/perf-auditor.md` — audits performance claims against measured
  evidence; rejects projected numbers presented as results.

## Git

- AOSP repo at `android-16/` (managed by `repo`)
- AppleUX source in `appleux/` subdirectories
- Create local git repo for AppleUX source
