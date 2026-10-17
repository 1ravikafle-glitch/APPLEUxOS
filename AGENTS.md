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
| Density | **440, no override — OUR ROM sets physical** (HyperOS reported an override of 430; that override dies at unlock and never applies here) |
| Logical frame | **392.73 × 875.64 dp** |
| Top inset | **76 px = 27.6 dp** — status bar and waterdrop cutout are the same 76 px |
| Bottom inset | **127 px = 46.2 dp** |
| Cutout | **100 × 76 px, top-centre**, centre x = 540 = exactly 1080/2 |
| Corners | **106 px radius, all four** (38.5 dp — reserve in fullscreen surfaces) |
| Refresh | **30 / 50 / 60 / 90 Hz** (`dumpsys display` supportedModes, 90 active at capture) |

Full capture and the corrections it forced:
`deliverables/m20/rock-display-measurement.md`.

Consequence: the `device-support/rock` fallbacks are measured at OUR ROM's
frame, and the home-grid derivation is done at 392.73 x 875.64 dp
(LauncherTokens: pitch 70.55, row 100, clearance 146, widget 141).

## Ground-truth screenshots — read first for Settings

`design-reference/iphone11-ios27/` holds the operator's REAL iPhone 11 iOS 27
photos (Settings root + General, 2026-10-16). They outrank every other design
source for Settings chrome: account card, value text, bottom search pill,
sub-screen hero cards, Shut Down row. See its README for the file manifest
and the extracted facts (47-file Drive set in `drive/` is binding). If the folder is empty of PNGs, ask the operator to
drop the originals in — do not substitute guide PNGs silently.

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

1. **Disk**: ~296 GB free on the project drive (`/dev/nvme0n1p4`, 738 GB total).
   A full AOSP sync + build fits. Disk stopped being the constraint on
   2026-10-17 — **RAM is the constraint**, see item 2.
2. **RAM**: 15 GB total. Whole-tree Soong analysis needs ~11 GB available —
   check `free -h` and stop the emulator before building, or the build gets
   OOM-killed and looks like a Soong bug. Use `scripts/soong-capped.sh`, which
   sets `GOMEMLIMIT=8GiB`. soong_build is Go 1.24, so the soft heap limit is
   honoured and it trades CPU for memory instead of dying. `-j1` alone is NOT
   enough: the 9.8 GB peak is live heap, not parallelism. Swap is 49 GB.
3. **No sudo**: Packages must be self-contained; JDK downloaded locally
4. **No Docker**: Cloud builds must be external
5. **No physical device yet**: UI verification goes through the Android SDK
   emulator first; real 4 GB POCO M5 hardware testing comes later

## `repo sync` is fragile here — read before running it

Four faults were fixed on 2026-10-17. All of them made `repo sync` abort
before syncing anything, so re-introducing any of them breaks the sync
silently.

1. `.repo/local_manifests/rock.xml` was not well-formed XML — a `<-` sequence
   and a `--flag` inside a comment. Neither is legal inside an XML comment.
2. `.repo/local_manifests/appleux.xml` declared five `<project>` entries against
   a `github.com/appleux` remote that does not exist, for directories that were
   never git repos. One unreachable project fails the ENTIRE sync.
3. `.repo/manifests` had `remote.origin.url` pointing at its own path and was
   pinned to `refs/heads/android-16.0.0_r4`, which did not exist. Now created
   locally at the same commit.
4. `vendor/xiaomi/rock` was still listed in `.repo/project.list` while
   `rock.xml` deliberately excludes it, so repo tried to delete it and refused
   because of uncommitted bring-up work. Removed from `project.list` —
   bookkeeping only, working tree untouched.

The AppleUX sources and the rock/hardware trees are **manually placed, not
repo-managed**. Never add `<project>` entries for them, and never run
`repo sync --force-sync` or `--prune` here.

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

`kotlinc` needs `-J-Xmx6g` for the launcher module (the script sets it).
Without it the build reports `Backend Internal error: Exception during IR
lowering`, which is an **OutOfMemoryError while rendering the diagnostic** —
the heap runs out building the error message, so the real error is never
printed. Do not read that line as a code bug.

`scripts/fix-context-receivers.py` repairs the dominant class of shell
breakage: `SomeView(this).apply { … }` makes `this` the View, so a nested
`SomeView(this)` or `LayoutUtils.dpToPx(t, this)` binds to the wrong receiver
(49 sites). It drives off kotlinc's own `file:line:col` and only rewrites bare
`this`, so it cannot clobber a correct one.

**Design system is consolidated — one palette, one theme hierarchy.**
`design-system/src/main/res/values{,-night}/colors.xml` is the single source
for every colour the XML layer can reach; `ColorTokens.kt` is the authority for
Kotlin drawing code. `tools/check-token-drift.py` compares them and fails on
drift — run it after any token change. All five per-app `colors.xml` files are
deleted. `Theme.Appleux.Base` in design-system is the only parent; launcher /
settings / systemui themes are three lines each. `Theme.Appleux.SystemUI` is
**forced dark** — an iOS 27 Control Center reads as dark glass over a light
wallpaper and does not invert with the system theme.

AOSP/Soong route: **whole-tree analysis passes.** The 2026-10-14 "~11-12GB anon
RSS" OOM blocker was a loaded host, not Soong — `qemu-system` was holding
3.6GB. Kill the emulator first; `m -j1 nothing` then completed in 48:21.
Do not cite the OOM as a standing blocker without checking `free -h` first.

`m nothing` proves config only. It never compiles the AppleUX modules, so it
passes while `appleux.mk` can still be wrong. The AppleUX modules are built
through `m -j1 AppleUXLauncher AppleUXSettings AppleUXSystemUI`.

AppleUX system-core packaging lives in `device/xiaomi/rock/appleux.mk`
(inherited from `device.mk`), with priv-app allowlists in
`device/xiaomi/rock/permissions/privapp-permissions-appleux.xml`, copied to
`/system/etc/permissions` by `PRODUCT_COPY_FILES` — without that copy every
signature|privileged grant is denied at runtime. Trail:
`deliverables/m7/soong-appleux-core-build-2026-10-17.md`.

No device images built yet — no Cuttlefish/physical-device verification.
Cuttlefish prebuilt guest+host (build 16373615) staged at `cuttlefish/dl/`
as the interim CF path.

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
disagrees is wrong. The rock panel advertises **30 / 50 / 60 / 90 Hz**
(`dumpsys display` supportedModes, 90 active at capture), so these numbers
describe one of four modes: the values below are scaled from the live refresh
rate by `FrameBudget.forRefreshRate()` — 28.00/33.33/40.00 ms at 30 Hz,
16.80/20.00/24.00 ms at 50 Hz, 14.00/16.67/20.00 ms at 60 Hz,
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

## SystemUI Takeover — in progress (2026-10-17, m23)

The iOS chrome is now compiled **into `com.android.systemui`** rather than
floated over it in a separate app.

- `appleux/systemui/chrome/Android.bp` → module `appleux-systemui-chrome`
  (`android_library`, `platform_apis: true`, `-Xjvm-default=all`), linked into
  `SystemUI-application`'s `static_libs` at
  `frameworks/base/packages/SystemUI/Android.bp:638`.
- Tree symlinks: `frameworks/appleux` → design-system,
  `frameworks/appleux-systemui-chrome` → `appleux/systemui/chrome`.
- **Stock nav-bar UI is deleted.** `res/layout/navigation_bar.xml` now has a
  transparent background and an `AppleuxHomeIndicatorView` instead of three
  Material buttons over an opaque `#ff000000` strip.
  `config_navBarInteractionMode = 2` and `config_navBarOpacityMode = 2` in
  `FrameworkOverlayRock` remove the buttons and the forced-opaque draw.
- **Stock status-bar type is deleted.** `TextAppearance.StatusBar.Default` was
  `config_headlineFontFamilyMedium` / `variable-label-large-emphasized` (both
  Android system font names = Roboto); now `sans-serif-medium` + HIG tracking.
  **Size is still AOSP's 14sp and is NOT iOS-measured** — do not call it a match
  until it is measured off `design-reference/iphone11-ios27/`.

**Rule being followed: delete the chrome, keep the behaviour.** `SystemUIService`
binds to `SystemUIApplication`, `Dependency` and a Dagger graph over ~4,900
files; deleting the controllers leaves the device with no status bar, no
keyguard and no QS. Every replacement keeps the AOSP root class so gestures,
IME, rotation, recents and keyguard mediation keep working.

**`m SystemUI` has NOT been run** — no warm Soong state, 48 min analysis alone.
The graph edit and runtime result are unverified.

`com.appleux.systemui` is **not** deleted yet: its 3 tiles/receiver launch
`AppleuxSystemUIService` by name to toggle the panels, so removing the service
first would strand the Control Center and Notification Center. Repoint them at
SystemUI's `CommandQueue` first, then delete. Full detail and the remaining
surface order: `deliverables/m23/systemui-takeover-2026-10-17.md`.

## Other Known Gaps

- **P0, unstarted: the real system chrome is still stock.** `AppleuxSystemUIService`
  draws `TYPE_APPLICATION_OVERLAY` windows and floats above SystemUI; it does
  not replace it. Stock `frameworks/base/packages/SystemUI` still owns the
  status bar, quick settings, nav bar, volume/power HUD and keyguard, and
  Recents is still QuickStep. Making AppleUX *be* SystemUI is a Soong
  `Android.bp` module inside `frameworks/base/packages/SystemUI`, not an app
  change, and it is the prerequisite for the items below. Not started.
- `android.R.drawable` is still referenced in ~43 places across the shells
  (Photos 34, Messages 6) while the 92 vendored `sf_*` / `il_ic_*` drawables
  are nearly unused in-app. Needs an icon-by-icon mapping.
- The 366 extracted sounds exist and nothing plays one.
- 26 `AlertDialog.Builder` call sites are now **themed** to the iOS card by
  `Theme.Appleux.Base` (no code change needed), but they are still
  `AlertDialog`. 36 `Toast` call sites are stock — iOS has no toast surface,
  so the answer is a replacement, not a restyle.

- Dock contents **are** now persisted (`DockPreferences`, shared prefs
  `appleux_launcher_dock`, delimiter-encoded so empty slots are representable).
  Resolved once by explicit package allowlist → exact label → platform role
  intent, never positional. Done: the home-grid metrics are derived at our
  ROM's frame (392.73 x 875.64 dp).
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
