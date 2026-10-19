# AppleUXOS — PREVIOUS WORK DONE (full history, migrated 2026-10-19)

**This is the complete record of all work done before this documentation set
existed.** It supersedes and preserves the knowledge from the deleted
`deliverables/m1…m31` files. Working folder is and remains
`/home/elfakiris/OsProject`. This doc set (`docs/appleux/`) is the single
documentation authority going forward.

---

## 1. What exists and WORKS today

### Built and verified (Soong, real artifacts)

| Artifact | State |
|---|---|
| **`m SystemUI` — BUILT** | 32 min, 0 errors, after 12 root-caused attempts. `android-16/out/target/product/rock/system_ext/priv-app/SystemUI/SystemUI.apk` (179 MB, 11 dex). **First time any AppleUX chrome was ever compiled by Soong.** |
| Chrome classes verified inside the APK | `AppleuxShadeHost` (CC+NC host), `AppleuxShadeStatusBar`, `AppleuxNotchHost`, `AppleuxPowerControl` (real shutdown/reboot/factory-reset via `IPowerManager` binding), `AppleuxHomeIndicatorView`, `AppleuxChromeTokens`, `AppleuxNotificationListener`+`AppleuxNotificationBus`, plus design-system, performance-lib, rock device tree, 366 IPSW sounds — **all linked**. |
| **14 SDK-built APKs** | `scripts/build-emulator-apks.sh` → `out/emulator/apks/`: design-system, performance, launcher, settings + **10 real system apps** (phone, messages, contacts, photos, files, camera, clock, calendar, browser, video). |
| Working build invocations | `bash ~/OsProject/build-systemui-direct.sh` (direct shell, `GOMEMLIMIT=8GiB` written INSIDE the wrapper), `scripts/soong-capped.sh` (cgroup-capped variant), `scripts/build-emulator-apks.sh` (SDK route, no Gradle). |

### Source state (all committed; see §5 for repo layout)

- **10 system apps are real APKs** — own packages (`com.appleux.phone`…),
  `CATEGORY_LAUNCHER`, role intent-filters (`ACTION_DIAL`/`SENDTO`/`VIEW`),
  iOS 27 adaptive icons. Shared code in `appleux-apps-shared`
  (package `com.appleux.apps`). Previously they were launcher-embedded
  activities — invisible; that architectural error is fixed.
- **SystemUI takeover at source level** (delete-the-chrome, keep-the-behaviour):
  `ShadeTouchableRegionManager.setup()` attaches `AppleuxShadeHost` +
  `AppleuxShadeStatusBar` (passing the resolved panel, try/catch-protected);
  `StatusBarWindowView` attaches `AppleuxNotchHost`; volume panel restyled to
  iOS capsule with all six view ids preserved (`VolumeDialogControllerImpl`
  contract); `TextAppearance` typography restyles; SHUTDOWN permission +
  notification listener declared.
- **Launcher** (iOS 27): 4×6 grid derived from the MEASURED panel; dock glass
  (superellipse n≈4.4, 34 dp radius, persisted); app library (3-col, category
  folders, search); Today View (glass scrim + blur); jiggle edit mode;
  **icon badges** (red `#FF3B30` count bubble, dispatchDraw overlay,
  `AppleuxBadgeStore` cross-process seam); **gesture zones fixed**
  (CC/NC only from top-edge strip `downYFrac<0.06`; below = Spotlight);
  NC double-open removed; Spotlight with apps+settings index + web row;
  grid reserves the 146 dp dock clearance band; single-line labels.
- **Settings**: iOS 27 root row order + separate **Customization page**
  (`CustomizationActivity`) for AppleUX toggles; iOS 27 settings icon; detail
  pages (Wi-Fi, Battery, Display, Security, Performance).
- **Performance Controller**: `AppleuxPerformanceController` Q100→Q70 quality
  tiers, `FrameTimeMonitor` (30-frame window, jank at frame budget),
  `PressureMonitor` (CPU/GPU/thermal/mem/PSI), recovery + oscillation guard.
  Canonical 60 Hz budgets: good ≤14 ms, warn ≤16.67, jank >16.67, severe >20;
  scaled by `FrameBudget.forRefreshRate()` for 30/50/60/90 Hz.
- **Design system**: tokens (Color, Typography, Sizing, Spacing, Motion,
  LiquidGlass, Launcher), 52 components, squircle via Apple's own contour,
  366 IPSW sounds, `AppleuxToast`/`AppleuxDialog`/`AppleuxSlider`/`AppleuxToggle`
  replace Material everywhere (HIG charter, `m29`).

### Physical device (connected 2026-10-19)

- **POCO M5, ADB serial `nb79nvba65tse6ug`**, codename rock, MT6789,
  currently stock MIUI Android 13 (`V816.0.8.0.ULUINXM`, **release-keys —
  locked bootloader**). It is the **flash target**, not an overlay-test
  target. Full audit in §7.
- Measured panel (operator, physical unit): **1080×2408 px @ 440 dpi**
  (logical 392.73×875.64 dp), top inset 76 px (= cutout; status bar shares
  it), bottom 127 px, corners 106 px, **30/50/60/90 Hz** (90 active).

---

## 2. Runtime verification state — THE CRITICAL FACT

**Our Android 16-built SystemUI CANNOT run on the stock Android 17
Cuttlefish guest.** Proven with a named method (crash-loop at app
construction, before any AppleUX code):

```
FATAL: Unable to instantiate application SystemUIApplicationImpl
Caused by: NoSuchMethodError: No static method
  sysuiLargePerfettoShmemBuffer()Z in class
  com.android.internal.hidden_from_bootclasspath.android.tracing.Flags
  (framework.jar!classes5.dex)
  at SystemUIApplicationImpl.<init>(SystemUIApplicationImpl.java:91)
```

Framework drift 16→17 in a hidden aconfig flag. SystemUI is the most
framework-coupled app in the system; patching individual calls is
whack-a-mole, not a path.

**Path forward (chosen):** build **`aosp_cf_x86_64_only_phone`** from our own
tree — the product EXISTS in `device/google/cuttlefish` (same product as the
stock prebuilt guest). Framework-matched by construction, our SystemUI and
design mods native. The API 36 SDK emulator also stays valid for the
launcher/settings/apps quick-checks (they are regular APKs).

Screenshot evidence from the 17-guest attempt (pixel+MD5 analysed): home
renders (wallpaper #e1b26d 40.8%); CC/NC/shade/volume captures byte-identical
to home (SystemUI dead — nothing could open); Settings 100% black with
focus=null (degraded-system side effect, no exception of its own).

---

## 3. The 12-run build saga (each root-caused — process truth)

| Run | Died at | Root cause |
|---|---|---|
| 1–3 | 46 s bootstrap | `apps-shared/Android.bp` lost in a directory shuffle; `systemd-run` hid output in the journal so exit-1 looked like OOM |
| 4 | 14 min | systemd-oomd: 7 GiB GOMEMLIMIT GC-thrash filled the 8 G swap cap |
| 5–6 | 14–45 min | `android_library` requires a manifest; default path is module root, ours was `src/main/` |
| 7 | 51 min | oomd on PSI — my own `MemoryHigh=8192` split caused reclaim-thrash |
| 8 | instant | `ManagedOOM*=none` invalid on transient user units |
| 9 | — (never started) | **crosvm support gate**: `InSandbox()` = `access("/manager.sock")` OR `/usr/lib/cuttlefish-common` — neither existed (tarball host pkg never system-installed) |
| 10 | 24 min | turbine: orphaned `catch` from a try/catch flatten |
| 11 | 10 min | chrome module's first Soong compile: stray `}` in PowerControl; nonexistent APIs in ShadeStatusBar (`android.R.id.detail`, `PARENT_TOP`, `android.R.dimen.status_bar_height`, `android.R.id.notification_panel`), 3-arg LayoutParams |
| 12 | **SUCCESS** | 32 min, 0 errors |

**Non-negotiable lessons (all evidence-backed):**
1. `systemd-run` does NOT inherit the parent shell env — env vars must be
   written INSIDE the wrapper. Every GOMEMLIMIT "change" before that was a no-op.
2. Soong runs soong_build under `env -i` — assume no env vars reach it.
3. `.gitignore` line 2 ignores **all of `android-16/`** — `git add -A`
   silently skips every NEW file there (this lost `apps-shared/Android.bp`
   once). Always `git add -f` for new tree files.
4. `frameworks/base` is a **nested repo-managed git repo** — SystemUI-side
   edits must be committed THERE (`054b0280c`, `7c9a7d777`, `c61360925`,
   `755558cd5`).
5. Emulator needs `ANDROID_AVD_HOME=$OsProject/android-avd`.
6. Cuttlefish sandbox layout: `sudo touch /manager.sock` +
   `$HOME/instances/cvd-1/logs/launcher.log` must pre-exist (opened O_APPEND
   without O_CREAT).
7. Every capped build died to a *kill* (oomd/cgroup), never to exhaustion;
   direct shell + 50 G swap is the working configuration on this host.

---

## 4. Emulator performance data (recorded, honest)

SwiftShader software rendering — **NOT device-representative**, no GPU.
Percentiles move run to run; regenerate with `tools/capture-metrics.sh`.

| App | Frames | Janky | p50 | p90 | p95 |
|---|---|---|---|---|---|
| Launcher | 43 | 43 (100%) | 150 ms | 350 ms | 450 ms |
| Settings | 20 | 19 (95%) | 150 ms | 200 ms | 200 ms |

No measured device numbers exist. All `deliverables/m12/m17/m18` figures were
**projected**, not measurements — treat as hypotheses.

---

## 5. Repository layout (operative truth)

```
OsProject/                        ← working folder (unchanged)
├── android-16/                   ← AOSP tree (repo-managed; .gitignore'd — add -f new files)
│   ├── frameworks/appleux/       ← AppleUX sources (design-system, launcher, settings,
│   │                                performance, sounds, systemui/chrome, 10 app modules,
│   │                                apps-shared)
│   ├── frameworks/base/         ← nested git repo: SystemUI edits committed here
│   ├── device/xiaomi/rock/      ← device tree (LineageOS-derived, AVB test-keys)
│   ├── hardware/xiaomi-appleux/rock ← appleux-device-support-rock module
│   └── out/target/product/rock/  ← built SystemUI.apk
├── scripts/build-emulator-apks.sh, soong-capped.sh, capture-metrics.sh
├── build-systemui-direct.sh     ← THE working Soong invocation
├── out/emulator/apks/            ← 14 SDK APKs
├── docs/appleux/                ← THIS doc set (single documentation authority)
└── design-reference/iphone11-ios27/ ← operator's ground-truth photos (binding for Settings)
```

Design-source precedence (locked): measured Apple Figma kit → material/colour/
typography; files5/IOSLikeROM → layout; `ios27-baseline.md` → inventory;
ground-truth photos → Settings chrome.

---

## 6. Dead / pending components (honest ledger)

**Dead, need hosts (in priority order):**
1. `AppleuxLockScreen` + `AppleuxPasscodeView` — **no keyguard host**; iOS
   lock screen entirely absent at runtime (highest-value next surface)
2. `AppleuxRecentsView` — no caller; swipe-up-hold is a log line
3. `AppleuxNotificationBanner`/`Expanded`/`Footer` — no heads-up/expand/actions
4. `AppleuxSystemHUD` — volume HUD is launcher-key-driven only
5. `AppleuxSOSCountdown`, `AppleuxScreenshotToolbar`, `AppleuxUnavailableTimer`,
   `AppleuxLockCustomizeSheet`, `AppleuxSettingsDetailView`
6. `AppleuxTouchFeedbackOverlay` — must STAY dead (swallows taps)

**Known open gaps** (from the final audit): widgets are a 3-widget closed set
(iOS needs hybrid host: native canvas first-party + AppWidgetHost third-party,
analog clock, weather via Open-Meteo, 4 sizes); Settings root at ~6/28 rows
before the iOS-27 restructure landed (spec: `docs/appleux/` UI matrix);
typography tracking ~60× too tight (measure once, fix divisor); status bar is
ours only in home+shade, stock elsewhere until SystemUI ships on a matched
guest; Spotlight has app/settings/web index (backend exists, wired in
launcher); folders hardcode "Folder".

---

## 7. POCO M5 device tree audit (m31, physical + tree)

- **Physical**: serial `nb79nvba65tse6ug`, rock, MT6789, MIUI 13,
  release-keys (locked). Flash requires bootloader unlock (user decision,
  wipes data).
- **Tree**: `vendor/xiaomi/rock` 1.1 G blobs; `kernel/xiaomi/rock` 301 M
  (5.10 GKI, prebuilt Image.gz + dtb + DTBO + vndr/dlkm modules);
  VINTF complete (audio 7.0, radio 1.2/1.6 + mtkradioex 3.0, sensors 2.0,
  gatekeeper, composer 2.3, keymaster_attestation, Dolby, goodix+ fingerprints,
  NFC nxp, displayfeature, mtkpower 1.2); fstab EROFS/ext4 dual + F2FS
  userdata with metadata-encryption v2; AVB with **test-keys** and
  `--set_hashtree_disabled_flag`; dynamic partitions (super 9126805504);
  dalvik tuned for 16 G host builds; screen config matches measured panel.
- Upstream: `android-17.0.0_r1` tag exists (the drift source). Our tree pins
  Android 16 (BP4A). Disk: 236 G free.


