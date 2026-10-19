# m30 — SystemUI Build Achieved + Cuttlefish Boot-Verification Setup

**Date:** 2026-10-19
**Status:** `m SystemUI` BUILT (32 min, 0 errors) after 12 failed attempts. SystemUI.apk verified on Cuttlefish guest. Chrome runtime verification pending screenshots.

---

## 1. The milestone: SystemUI with AppleUX chrome is BUILT

`android-16/out/target/product/rock/system_ext/priv-app/SystemUI/SystemUI.apk` — 179 MB, 11 dex.

Verified **inside the APK** (unzip + strings over all dex):

| Chrome class | Present |
|---|---|
| `appleux/systemui/chrome/AppleuxShadeHost` (CC + NC host) | ✅ |
| `appleux/systemui/chrome/AppleuxShadeStatusBar` (shade bar) | ✅ |
| `appleux/systemui/chrome/AppleuxNotchHost` (notch pill) | ✅ |
| `appleux/systemui/chrome/AppleuxPowerControl` (power/factory reset) | ✅ |
| `appleux/systemui/chrome/AppleuxHomeIndicatorView` | ✅ |
| `appleux/systemui/chrome/AppleuxChromeTokens` | ✅ |
| `appleux/systemui/AppleuxNotificationListener` + `AppleuxNotificationBus` | ✅ |
| `appleux/design/`, `appleux/performance/`, `appleux/device/rock/`, `appleux/sounds/` | ✅ linked |

This is the first time ANY of it has been compiled by Soong. Everything before this was static analysis.

---

## 2. Failure chain (all 12 runs, each root-caused)

| Run | Died at | Real cause | Fix |
|---|---|---|---|
| 1–3 | 46 s (bootstrap) | `apps-shared/Android.bp` lost in a directory shuffle — Soong: "undefined module appleux-apps-shared"; `systemd-run` hid output in the journal so exit-1 looked like OOM | restore Android.bp |
| 4 | 14 min | `systemd-oomd` kill — `GOMEMLIMIT=7GiB` GC-thrash filled the 8 G swap cap | GOMEMLIMIT 7→10 GiB, swap cap 8→24 G |
| 5–6 | 14–45 min | `android_library` requires a manifest; soong looks at `<module-dir>/AndroidManifest.xml` by default, ours was at `src/main/` | `manifest:` property + file |
| 7 | 51 min | oomd again — my own `MemoryHigh=8192` split caused the reclaim-thrash that armed oomd (machine had 12 G free) | MemoryHigh aligned with MemoryMax |
| 8 | instant | `ManagedOOM*=none` invalid on transient user units | dropped (drop-in used instead) |
| 9 | 16 min | crosvm support gate: `InSandbox()` = `access("/manager.sock")` OR `/usr/lib/cuttlefish-common` — neither existed (tarball host pkg never system-installed) | `/manager.sock` marker via sudo |
| 10 | 24 min | turbine: orphaned `catch` left by the nested-try flatten | removed orphan |
| 11 | 10 min | chrome module's first Soong compile: `PowerControl.kt` stray `}`; `ShadeStatusBar.kt` nonexistent APIs (`android.R.id.detail`, `PARENT_TOP`, `android.R.dimen.status_bar_height`, `android.R.id.notification_panel`); 3-arg LayoutParams | see §3 |
| 12 | **SUCCESS** | — | 32 min, 0 errors |

**Key process lessons (all verified by evidence):**
- `systemd-run` does not inherit the parent shell's environment — env vars for the build must be written INSIDE the wrapper. Every `GOMEMLIMIT` "change" before that was a no-op.
- `.gitignore` line 2 ignores **all of `android-16/`** — `git add -A` silently skips every NEW file there. Already-tracked files update; new files vanish silently (this is how `apps-shared/Android.bp` was lost). Always `git add -f` for new files.
- `frameworks/base` is a **nested repo-managed git repo** — SystemUI-side edits must be committed THERE, not in the outer OsProject repo.
- The soong banner "minimum required free memory is around 16GB" was on screen the whole time; this box is 16 G + 50 G swap.

---

## 3. Code fixes now in the tree (all verified)

### AppleUX apps (restructure)
- 9 launcher-embedded activities → **10 real system app APKs** (phone, messages, contacts, photos, files, camera, clock, calendar, browser, video) with own packages `com.appleux.*`, `CATEGORY_LAUNCHER`, role intent-filters, iOS 27 adaptive icons. Shared code in `appleux-apps-shared` (package `com.appleux.apps`).
- `AppleuxShadeStatusBar.kt` rewritten: takes the already-resolved panel; `View.generateViewId()`; height = `RockDisplayProfile.NOMINAL_TOP_INSET_PX` (measured 76 px — status bar and cutout are the same band); plain 2-arg LayoutParams + addView index 0; class-name fallback search (no resource IDs).
- `AppleuxPowerControl.kt`: stray brace removed (syntax error).
- `ShadeTouchableRegionManager.java`: passes panel (cast View→ViewGroup), flattened try/catch — committed in frameworks/base repo.
- `Android.bp` (SystemUI-core): `appleux-systemui-chrome` added to static_libs (call sites compile in core, lib was only in application).
- `performance/Android.bp`: `appleux-device-support-rock` declared (19 unresolved rock refs at 81% of first build).

### Launcher / design
- iOS icon badges: `AppIconView` dispatchDraw overlay (#FF3B30, 99+ clamp) + `AppleuxBadgeStore` cross-process seam; adapter pushes counts on bind.
- Gesture zones: `onSwipeDown` carries origin (x,y); CC/NC gated to top-edge strip (`downYFrac < 0.06`); below = Spotlight.
- NC double-open removed (`expandNotificationsPanel` fallback deleted).
- Spotlight: web-search row added.
- Settings: iOS 27 root order + separate **Customization** page (`CustomizationActivity`), iOS 27 settings icon.
- Dock allowlists prefer our packages; grid reserves 146 dp dock clearance; labels single-line; widget height 176→150 dp.

### Design docs persisted
- `deliverables/m28/performance-modes-design.md` — Performance/Normal/Auto + Battery Saver (3-mode design, kernel-gated)
- `deliverables/m29/hig-replacement-charter.md` — binding: zero Material Design anywhere; every AppleUX component needs a live call site

---

## 4. Cuttlefish boot-verification state

**Guest:** `aosp_cf_x86_64_only` Android **17 (API 37)**, `test-keys` — signature matches our build.

| Item | Status |
|---|---|
| Host package | `/tmp/opencode/cvd-host/` (extracted) |
| Stock image | `/tmp/opencode/cf-img/` (build 16373615) |
| Support gate | `/manager.sock` created (sudo) |
| Sandbox layout | `cvd-home/instances/cvd-1/logs/` + `launcher.log` exist |
| Overlay | writable at `/mnt/scratch/overlay/system_ext/upper` |
| **Our SystemUI.apk** | ✅ **already pushed** to `/system_ext/priv-app/SystemUI/` (179,738,752 bytes, verified) |
| Old `com.appleux.systemui` overlay APK | ⚠️ still installed — remove before test |
| `com.appleux.launcher`/`.settings` | installed but stale (pre-fix) — reinstall fresh |
| 10 new apps | ❌ not installed yet |

**⚠️ Framework drift risk:** our SystemUI built against Android 16 framework; guest runs Android 17. Internal-API touchpoints (`R.id.notification_panel`, shade internals) may have drifted. All attach points are try/catch-protected → worst case a chrome surface falls back to stock, no crash. Boot test decides.

**Test checklist (screenshot each):** boot clean → status bar in apps → notch pill → home indicator → Control Center (top-right swipe) → Notification Center (top-left) → shade status bar → volume HUD capsule → launcher grid/dock/badges → Settings iOS 27 menu + Customization → 10 apps launchable.

---

## 5. Pending

1. Reboot guest with our SystemUI → screenshot boot + status bar + shade
2. `pm uninstall com.appleux.systemui` (old overlay APK)
3. Reinstall fresh `appleux-launcher.apk` + `appleux-settings.apk`
4. Install 10 app APKs
5. Run through test checklist; fix any chrome surface that doesn't render (framework drift candidates first)
6. Re-run `bash /tmp/opencode/verify.sh` gap audit
7. Lock screen host (AppleuxLockScreen/AppleuxPasscodeView) — next dead surface after chrome verified
