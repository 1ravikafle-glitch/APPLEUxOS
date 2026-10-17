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

## Where the AppleUX sources actually live — READ THIS FIRST

**The real source directories are inside the AOSP tree:**

```
android-16/frameworks/appleux/design-system/     tokens + shared components
android-16/frameworks/appleux/performance/       Adaptive Performance Controller
android-16/frameworks/appleux/launcher/          Launcher
android-16/frameworks/appleux/settings/          Settings app
android-16/frameworks/appleux/sounds/            366 IPSW sounds + SoundRegistry
android-16/frameworks/appleux/systemui/chrome/   iOS 27 chrome INSIDE SystemUI
android-16/hardware/xiaomi-appleux/rock/         measured display profile
```

**`appleux/<module>` and `device-support/rock` are symlinks back into those
directories.** One copy of every byte, every pre-existing path still resolves,
`scripts/check-single-source.sh` asserts the invariant, and `.gitignore` has
negations so the bytes stay under version control.

### Why they moved — this bit every AppleUX module for months

They used to live in `appleux/` and be linked in as
`frameworks/appleux -> ../../appleux/design-system`. **That never worked.**
Soong's module finder reads each directory entry's `d_type` from `getdents`
and never descends into a symlinked directory:

```
build/soong/finder/fs/readdir.go:210   case DT_LNK: mode = os.ModeSymlink
build/soong/finder/fs/fs.go:89          func (d *dirEntryInfo) IsDir() bool
                                           { return d.mode.IsDir() }
```

So `appleux-design-system` — and every other AppleUX module linked the same way
— was absent from `out/.module_paths/Android.bp.list` and **unresolvable by
name**. Proof: that list has 14,149 module paths and **zero** appleux entries.

Any claim that `m -j1 AppleUXLauncher` ever built was false. `m nothing` only
proves configuration, which is why it kept passing.

**Do not move them back behind a symlink.** It fails silently, exactly as it
did. Soong analysis is the only thing that catches it, and it takes ~40 minutes
when it fails and hours when it succeeds.

**The cheap graph-only check is `m -j1 nothing`, not a ninja target.**
`out/soong/build.ninja` does not exist in this tree and asking for it fails with
`error: ninja: unknown target 'out/soong/build.ninja'`. This tree's files are
suffixed with the product name:

| file | size |
|---|---|
| `out/combined-aosp_rock.ninja` | entry point |
| `out/soong/build.aosp_rock.ninja` | 485 MB, the real graph |
| `out/soong/build.aosp_rock.0-9.ninja` | split subgraphs |
| `out/soong/build.aosp_rock.incremental.ninja` | 2.9 GB |

If you want to drive ninja directly, use `-f out/combined-aosp_rock.ninja`.

## Project Structure

```
/home/elfakiris/OsProject/    (canonical; the old /media path is stale)
├── android-16/           AOSP source + the AppleUX modules (see above)
├── android-sdk/          Android SDK command-line tools
├── appleux/              symlinks into android-16/frameworks/appleux
├── device-support/rock   symlink into android-16/hardware/xiaomi-appleux
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
2. **RAM**: 15 GB total, and this is the binding constraint. Whole-tree Soong
   analysis peaked at **13.2 GiB**. `GOMEMLIMIT` does NOT stop it — it is a soft
   hint to the Go GC and was measured exceeding a 6 GiB limit, so a build that
   used it got OOM-killed and took the desktop into swap with it. That is
   recorded in `deliverables/m26`.

   `scripts/soong-capped.sh` now enforces a **real** cap: a cgroup v2
   `MemoryMax` via `systemd-run --user`, verified enforced on this box (a 1.5 GB
   allocation under `MemoryMax=512M` is killed, exit 1). Defaults to 9 GiB RAM +
   6 GiB swap, and **refuses to run uncapped** if the cgroup is unavailable.
   `GOMEMLIMIT=7GiB`/`GOGC=40` remain as soft hints set below the hard cap so Go
   collects before the kernel has to kill anything.

   A silent death after "Globs changed, rerunning soong..." means the cap fired.
   Rerun with `RAM_CAP_MB=12800 SWAP_CAP_MB=8192` rather than removing the cap.
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

**`com.appleux.systemui` IS DELETED** (same session). Its 585-line overlay
service, 2 QS tiles, boot receiver, manifest, res and `Android.bp` are gone, and
so are its `PRODUCT_PACKAGES` entry, both priv-app allowlist blocks, its appops
exceptions, its `inject-rootfs.sh` injection, its link check and its
`ram-report.sh` identity. The image now has **4 AppleUX APKs, not 5**.

- Its two QS tiles were **deleted, not repointed** — they existed only because
  AppleUX had no SystemUI presence. In iOS both panels open by a top-edge swipe,
  and `ShadeController` (`instantExpandShade` / `animateExpandQs`) is the correct
  in-SystemUI accessor, but wiring Dagger blind would break SystemUI at boot.
  Until the shade is replaced they open via AOSP's own pull-down gesture.
- `AppleuxNotificationListener` **moved into SystemUI** and is declared in
  `frameworks/base/packages/SystemUI/AndroidManifest.xml`. That is a functional
  gain: `BIND_NOTIFICATION_LISTENER_SERVICE` is a signature permission SystemUI
  holds, so the platform connects the listener — the overlay app's only ran if
  the user approved it by hand. No `enabled_notification_listeners` seeding
  remains.
- Panel toggles from apps go through `appleux/launcher/.../AppleuxSystemUi.kt`,
  which reflects on `StatusBarManager`'s `@hide` methods (not in the SDK jar, not
  in `symbols.xml`) and degrades to a logged no-op, never a crash.
- **Watch for dead `ComponentName`s.** An explicit component pointing at a
  deleted service does not fail to compile and does not reliably fail at runtime;
  the caller just sees nothing happen. Four such sites were found and fixed.

**`m SystemUI` has NOT been run** — no warm Soong state, 48 min analysis alone.
The graph linkage, the manifest `<service>` entry under
`use_resource_processor`, the `config_navBar*` overrides on device, and the home
indicator rendering are all **unverified**.

Next surface is quick settings / shade. It cannot be swapped wholesale:
`status_bar_expanded.xml` carries hundreds of view ids that AOSP controllers
`requireViewById`, so the Material look has to come off style by style. Full
detail: `deliverables/m23/systemui-takeover-2026-10-17.md`.

## Stock Surfaces Still Present — measured, not estimated

Counted on 2026-10-17 with `grep -rE` over `frameworks/appleux`. **These are
real remaining stock surfaces and the replace-from-source rule says they go.**

| Stock surface | Sites | Status |
|---|---|---|
| `AlertDialog.Builder` | **15** (11 real) | **11 of 26 converted** to `AppleuxDialogs`. The remainder: 8 use `.create()` + `dialog.show()` as separate statements, 3 pass a non-`EditText` to `setView`. |
| `android.R.drawable` | **33** | 102 vendored `sf_*`/`il_ic_*` vectors exist. Photos 24, Messages 5, CallScreen 2, Banner 2. |
| `<SeekBar>` | **6** | No horizontal iOS slider exists. `settings_item.xml:85`, Photos 2557, Phone 635, Camera 584, DisplaySettings 97, plus a dead `AppleuxPowerSlider`. |
| `Toast.makeText` | **1** | 35 of 36 converted to `AppleuxToast`. **One remains — find it with `grep -rn "Toast.makeText" frameworks/appleux`.** |
| `android.R.id.` | **3** | |
| Stock `TextClock`/`ProgressBar` in widgets | 6 | Clock/calendar/battery widgets still use stock controls. |

### 16 declared components with zero references

Each is the *intended* implementation of a surface below, so these are to be
wired, not deleted. A component with no reference compiles fine and fails
silently, so treat the count as a live assertion:

`AppleuxCCTileOrder`, `AppleuxJiggle`, `AppleuxLockCustomizeSheet`,
`AppleuxNotificationBanner`, `AppleuxNotificationExpanded`,
`AppleuxNotificationFooter`, `AppleuxPanelDismissHandler`, `AppleuxPasscodeView`,
`AppleuxPowerSlider`, `AppleuxRecentsView`, `AppleuxScreenshotToolbar`,
`AppleuxSettingsDetailView`, `AppleuxSOSCountdown`, `AppleuxSystemHUD`,
`AppleuxTouchFeedbackOverlay`, `AppleuxUnavailableTimer`

Check with:

```bash
cd android-16/frameworks/appleux
for c in AppleuxCCTileOrder AppleuxJiggle ...; do
  n=$(grep -rl "\b$c\b" --include=*.kt --include=*.xml . | grep -v "/$c\." | wc -l)
  [ "$n" -eq 0 ] && echo "DEAD: $c"
done
```

`AppleuxNotificationBus` is in the same state: the listener publishes, nothing
subscribes, so notifications are collected and dropped.

`AppleuxDialogs` is **no longer** in this list — it was dead until 2026-10-17 and
is now the host for every converted alert.

### Device tiering — the 2 GB floor

`performance/.../device/DeviceProfile.kt`. Tiers read **physical RAM** as the
primary axis, because that is how the product is specified:

| physical RAM | tier | icons | bitmap pool | heap | baseline | blur |
|---|---|---|---|---|---|---|
| **< 2.5 GB** | `TIER_LOW` | 10 | 1 MB | 55% | Q75 | **off** |
| 2.5–4 GB | `TIER_STANDARD` | 20 | 4 MB | 70% | Q90 | on |
| ≥ 4 GB | `TIER_ROOMY` | 40 | 10 MB | 80% | Q100 | on |

`memoryClass` is a **floor guard only** — it can push a device down to
`TIER_LOW`, never down to `TIER_STANDARD` and never up. An earlier version
combined the two as `minOf(ram, heap)`, which demoted a 4 GB phone with a 384 MB
heap *below* a 3 GB phone. A guard must guard; it must not also throttle.

`AppleuxPerformanceController` clamps quality to this ceiling at `start()`, so a
low-memory phone never pays for full effects on its first frames. On `TIER_LOW`
the ceiling is Q75 and the blur gate needs Q90, so the most expensive effect in
the system is off with no separate switch to get wrong.

### Sounds — now live (was: 733 resources shipping silent in two APKs)

The 733 `res/raw` entries shipped in both launcher and settings, with zero
playback call sites. Two independent defects were behind that:

1. **They did not link.** Every one of the 366 sounds had a `.caf` and an `.ogg`
   twin, and both compile to `R.raw.<stem>` — a duplicate-resource error. This is
   why `AppleUXSounds` could never be built by `build-emulator-apks.sh` at all,
   and why it stayed silent. `.caf` is uncompressed PCM at **82.2 MB**; `.ogg` is
   **6.9 MB**. The `.caf` set now lives in `sounds/extracted-reference/` (see its
   README): **91 MB → 7.7 MB**, no coverage lost.
2. **Nothing played them.** `AppleuxSoundPlayer` (lazy `SoundPool`, bounded to 24
   resident samples, never throws) is wired to the camera shutter and a toggle
   confirm. `AppleUXSounds` was **dropped from settings**, which has no playback
   call site and was paying 7.7 MB for it.

Remaining wiring: lock/unlock (needs the keyguard surface), notification, charge.

### `AppleuxDialogs` — the piece that made dialogs possible

`AppleuxDialog` extended `FrameLayout` and its `show()` only toggled visibility.
With no WindowManager path **and no host to attach it to**, it had zero
consumers while 26 stock dialogs ran. `AppleuxDialogs` now attaches it to the
Activity's content view — the same in-view approach as `AppleuxToast`, so an
alert costs no extra window and no extra composited layer — and exposes
`alert` / `prompt` / `choose` / `dismiss`, named for the iOS
`UIAlertController` they map to. That naming is what keeps `setView` out of the
codebase for good.

### Other open items

- **Control Center is orphaned.** Every `AppleuxControlCenter` reference is
  internal or a comment. `toggleControlCenter()` still calls AOSP
  `expandSettingsPanel()`, so the user gets the Material QS shade.
- **`m SystemUI` has still never run.** No SystemUI intermediates have been
  built, so the m23 takeover is statically verified and zero verified at runtime.
- Dock contents **are** persisted (`DockPreferences`, delimiter-encoded).
- The Notification Center still does not reserve the cutout;
  `AppleuxDisplayInsets.applyTopInset(context, column)` in
  `AppleuxNotificationShade.init` is a one-liner that has not been applied.
- `reduce_motion` is stored and propagated, but no component consumes it.
- Brightness writes need the user-granted `WRITE_SETTINGS` appop; without it the
  slider silently does nothing.
- **Typography tracking is probably ~60x too tight.** `spec.tracking / 1000f`
  yields 0.0001–0.0004 em while the tracking table holds 0.10–0.40, and
  `navigation_bar.xml`/`styles.xml` hardcode `letterSpacing 0.00015`. Both are
  visually zero. If Apple's published figures are points-at-size the correct
  divisor is the point size, which is ~60x larger. **Unresolved** — needs one
  measurement of real iPhone 11 text width vs the emulator's before any text
  style is called a match.

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
