# TUNING_LOG — 2026-10-19 runtime verification session

Every entry is what was **observed on a running device**, not projected. Screenshots
are in `screenshots/a16-2026-10-19/` (Android 16, rock geometry) and
`screenshots/cf-2026-10-19/` (Cuttlefish, Android 17).

---

## Standing rule (operator, 2026-10-19)

> **Always run on Android 16.**

Enforced by `scripts/run-a16.sh`. See "Target" below for why the previously
documented Cuttlefish guest must not be used for A16 work.

---

## 1. Target: `appleux36d` — the Android 16 verification AVD

| | |
|---|---|
| AVD | `android-avd/appleux36d.avd` |
| Image | `system-images;android-36;default;x86_64` (**userdebug**) |
| Release / SDK | **16 / 36** |
| Geometry | **1080 x 2408 @ 440 dpi = 392.73 x 875.64 dp** |
| RAM | 3072 MB, `-gpu host`, `-writable-system` |
| Boot | **10.8 s** from the saved snapshot; ~32 s cold |
| Launch | `scripts/run-a16.sh` (`--cold` / `--snapshot`) |

The geometry is the **measured POCO M5 panel**, so every launcher metric in
`LauncherTokens.kt` — which is derived for 392.73 x 875.64 dp — is exercised as
designed. The old `appleux36.avd` was 1080x2400 @ 420, a frame nothing was
derived for.

### Why not the Cuttlefish guest

`cuttlefish/dl/` is build **16373615 = API 37**. Our SystemUI.apk is
`compileSdk 36 / targetSdk 36`. On the A17 guest `com.android.systemui` dies in
its Application constructor:

```
NoSuchMethodError: No static method sysuiLargePerfettoShmemBuffer()Z in class
  com.android.internal.hidden_from_bootclasspath.android.tracing.Flags
  (declaration appears in /system/framework/framework.jar!classes5.dex)
  at com.android.systemui.application.impl.SystemUIApplicationImpl.<init>(SystemUIApplicationImpl.java:91)
```

`sysui_large_perfetto_shmem_buffer` is declared in **our** tree at
`frameworks/base/core/java/android/tracing/flags.aconfig:29` and aconfig dropped
it after A16. 268 crash records; SystemUI restarts forever, so **no shade window
exists at all** and Control Center, Notification Center, shade bar and volume HUD
are all dead. That is a version mismatch, not an orphaned-host bug.

The APK *does* install and the guest *does* boot — the signature matches exactly
(`c8a2e9bc…` both sides, same AOSP test-keys). Only the framework class is
missing.

### Why `google_apis` cannot be used

It is a `user` build: `adb remount` answers *"Device must be bootloader
unlocked"*, `/system_ext` is ext4 + dm-verity, read-only. `default` is userdebug
and overlays cleanly.

---

## 2. Fixed this session

### 2.1 Launcher home layout — 3 defects (`LauncherTokens.kt`, `HomescreenGrid.kt`, `LauncherActivity.kt`)

| defect | root cause | fix |
|---|---|---|
| last icon row drew **under** the translucent dock | `GRID_ROWS = 6` assumed unconditionally; `clipToPadding = false` let overflow render into the dock band | `HomescreenFit.rowsThatFit()` derives rows from the measured frame; `clipToPadding = true` |
| search pill showed **through** the icons behind it | frozen `bottomMargin = 196 dp` | derived from `HOMESCREEN_DOCK_CLEARANCE_DP` |
| dead gap above the pill, only 2 rows fit | the dock band was reserved **twice** — LayoutParams `bottomMargin` 146 dp *and* grid padding 146 dp = 292 dp | one reserve, `HOMESCREEN_BOTTOM_RESERVE_DP` |

**`GRID_ROW_PITCH_DP = 117f` was dead** — declared, documented as the row pitch,
and read by nothing. Added `LauncherTokens.naturalRowPitchDp`
(icon 60 + gap 4 + label 15 + 2 x `INSETS_LG` 12 = 103 dp) for real fit
arithmetic. On rock: (875.64 - 27.6 - reserve) / 103 -> 6 rows, matching the
documented design. On Cuttlefish's 640 dp frame the same code gave 4 rows, which
is correct for that screen — iOS picks a row count per device rather than
shrinking icons to fit a fixed one.

### 2.2 All 10 app shells crashed on launch — theme

```
java.lang.IllegalStateException: You need to use a Theme.AppCompat theme
(or descendant) with this activity.
```

`AppleuxAppTheme` in **11** modules parented to
`android:Theme.Material.NoActionBar` — a *platform* theme — while every shell
inflates `AppleuxIcon`, an AppCompat widget. Reparented all 11 to
`Theme.Appleux.Base` (which is `Theme.AppCompat.Light.NoActionBar`, and what
launcher and settings already used). All 10 activities now reach
`topResumedActivity`.

This was also the cause of the benign-looking
`AppleuxIcon is an AppCompat widget that can only be used with a Theme.AppCompat
theme` `ThemeUtils` errors seen earlier — they were the same bug, one level down.

---

## 3. Open defects found, not fixed

| # | defect | evidence | severity |
|---|---|---|---|
| 1 | **10 app shells launch but are visually unstyled** — stock Android widgets, default blue `Start`, stock tab bar | `app-clock.png`, `app-phone.png` | high — the shells were never visually built |
| 2 | `SystemUI.apk` is **179 MB** (8 dex files, ~124 MB, unminified) vs 36.8 MB stock, and does **not fit** the 214 MB `adb remount` overlay (162 MB free). The m23 takeover therefore cannot be runtime-verified on any target yet. | `unzip -l`, `df -h /system` | high — needs R8 / dex-opt |
| 3 | `frameworks/base/packages/SystemUI/AndroidManifest.xml:50-53` claims SystemUI needs **no** privapp allowlist entry for `android.permission.SHUTDOWN`. False — SystemUI is in `/system_ext/priv-app`, so it is a privileged app. Boot-loops `system_server`: `IllegalStateException: Signature\|privileged permissions not in privileged permission allowlist … android.permission.SHUTDOWN`. **This will hit the rock device.** AOSP grants SHUTDOWN only to `com.android.managedprovisioning` and `com.android.phone`. | Cuttlefish, 6k+ crashes | **critical for the product** |
| 4 | `ro.control_privapp_permissions=enforce` on Cuttlefish could not be relaxed via setprop (ro.* immutable). Worked around by editing the guest's `/vendor/build.prop` — a **test-guest-only** hack, not a product fix. | `/vendor/build.prop` | low |
| 5 | Status bar / shade / CC / NC / volume HUD are **stock**, because of #2. | — | blocked by #2 |
| 6 | Lock screen absent — `AppleuxLockScreen` + `AppleuxPasscodeView` have no keyguard host. | — | medium |
| 7 | Immersive-mode education dialog ("Viewing full screen") fires on every launcher start. | screenshots | low |

### Note on #3 — the trap

`AppIdPermissionPolicy.kt:1552` and `SystemConfig.java:1599` bucket the privapp
allowlist by the **APK's partition**, so the grant must live in
`/system_ext/etc/permissions/`, not `/system/etc/permissions/`. Placing it in
`/system` is read, logged by `SystemConfig`, and then **ignored** — the lookup
targets the system_ext bucket. Verified by reading both files.

---

## 4. Verified working

- AppleUX launcher as HOME: squircle masks, 5 clean rows at rock geometry, page 2
  swipe, search pill, iOS dock glass bar, home indicator, notch pill.
- AppleUX Settings: grouped iOS cards, section headers, chevrons, secondary value
  text, scrolls.
- All 10 app shells launch and render (`am start` reaches `topResumedActivity`),
  subject to #1.
- Zero `com.appleux.*` crashes in logcat after the theme fix.

## 5. Deliberately not a bug

The "duplicate" Calendar / Camera / Clock / Contacts / Files icons on home are
**AppleUX apps alongside the stock AOSP ones** (`com.android.calendar`,
`camera2`, `deskclock`, `contacts`, `documentsui`, `dialer`, `gallery3d`).
`cmd package query-activities` returns **each package exactly once**. The
launcher is correct; the guest image simply ships the AOSP equivalents.