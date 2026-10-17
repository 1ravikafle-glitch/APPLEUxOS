# iOS-Design Coverage Audit — r2 (re-check)

**Date:** 2026-10-17, 15:05
**Supersedes:** `ios-design-coverage-audit-2026-10-17.md` (13:28 same day).
**Scope:** Whole `/home/elfakiris/OsProject` tree — AppleUX modules, AOSP tree, device overlays, shipping image path.
**Method:** Read-only static inspection. **No OS source was changed. No emulator was run. Nothing was committed.**
**Why r2 exists:** the tree was being actively edited by the parallel session when r1 was written. A re-check at 15:00 found the theme layer had been rewritten, a build had been run, and several r1 counts were stale or methodologically wrong.

**Parallel / authoritative work:** `deliverables/m23/ipsw-surface-comparison-2026-10-17.md` (11:03) is the parallel *fidelity* audit and it **applies fixes**. It stays authoritative; this file is a *coverage* re-check and defers to it where they overlap.

---

## 0. What actually changed since r1 (13:28 → 15:05)

| # | r1 finding | r2 status | Evidence |
|---|---|---|---|
| 1 | **B2** — every app had its own theme on a stock parent; launcher `values-night/themes.xml` **EMPTY**; settings `values-night` was a byte-for-byte **copy** of light | **FIXED** | New unified `Theme.Appleux` in `design-system/src/main/res/values/themes.xml`; new `design-system/src/main/res/values-night/themes.xml` (real dark overrides); per-app themes now inherit it (`Theme.Appleux.Launcher/Settings` → `Theme.Appleux`); per-app `values-night/themes.xml` files **removed** (`settings/src/main/res/values-night/` no longer exists) |
| 2 | **B1** — `AlertDialog` ×23, `AppleuxDialog` used 3× | **MITIGATED** (component still unused) | `Theme.Appleux.Base` now sets `android:alertDialogTheme` + `dialogTheme` + `android:datePickerDialogTheme` + `android:timePickerDialogTheme` → `Theme.Appleux.Dialog`, so the 26 platform `AlertDialog.Builder` sites render as iOS cards. But `AppleuxDialog` has **0** consumers outside design-system. |
| 3 | **B3** — stock `EditText` ×22 / `TextView` unthemed | **MITIGATED** | Theme now sets `android:textViewStyle`→`AppleuxTextView`, `android:buttonStyle`→`AppleuxButton`, `editTextStyle`/`android:editTextStyle`→`AppleuxEditText`, `colorAccent`/`colorControlActivated`→iOS blue, `selectableItemBackground`, `popupBackground`, window animation style, status/nav bar colour + light flags |
| 4 | **`Switch` ×2** | **RESOLVED** | `Switch(` call sites = **0**; `AppleuxToggle` (iOS switch) has **3** shell consumers |
| 5 | `CheckBox`/`RadioButton`/`Spinner` | **N/A** | call sites = **0** all three — nothing to fix |
| 6 | Build: "aborts at launcher with **40 errors**" | **STILL RED — now 92 errors** | `out/gen/launcher-kotlinc.log` (15:02, 92 `error:` lines) |
| 7 | counts quoted as word-occurrences (`Toast` 36, `SeekBar` 38, `AlertDialog` 23) | **CORRECTED** | these were `grep -rho` word counts, not call sites. Real sites below. |

### Corrected call-site counts (r1 numbers were inflated)

| Widget | r1 quoted | **r2 real call sites** | Method |
|---|---|---|---|
| `Toast.makeText` | 36 | **36** | `Toast.makeText` — r1 happened to be right |
| `AlertDialog.Builder` | 23 | **26** | `AlertDialog.Builder` |
| `SeekBar(` | 38 | **5** | `new SeekBar(` constructor sites |
| `ProgressBar(` | 1 | **2** | constructor sites |
| `android.R.drawable` | 38 | **43** | refs across 5 files |
| `EditText(` | 22 | *themed, not counted* | now styled by theme |

> r1's `SeekBar 38` / `EditText 22` were `grep -rho` **word** counts (field names, imports, comments included). The **constructor** counts are the ones that matter and are much smaller.

---

## 1. Layer map (unchanged in structure)

| Layer | Meaning | r2 count |
|---|---|---|
| **A — iOS-designed** | AppleUX source draws it in iOS idiom | launcher + 13 app shells + 51 design-system components + 7 Settings screens + 3 widget providers + 117 vendored icons |
| **B — Stock Android still showing** | platform design still doing the work | system chrome process; toasts; sliders; 5 framework icons; boot anim; sounds; vendor overlays |
| **C — Missing on both** | no AppleUX impl and, on image route, no stock either | Siri; iOS share sheet; iOS keyboard; 10 wired-but-unconsumed components |

---

## 2. Layer B — where stock Android still shows (current)

Ranked by user-visible impact.

### B0. The system chrome is still stock (highest impact)
`AppleuxSystemUIService.kt` is a **foreground service** drawing `TYPE_APPLICATION_OVERLAY` windows (L494-509). It is **not** the SystemUI process. Wherever stock `SystemUIGoogle` is present (the AOSP/Soong route removes nothing), these remain **stock Android 16**:
- status bar, navigation bar, quick-settings panel
- keyguard (lock screen security UI)
- recents / task switcher
- volume + power HUD
- screenshot UI

On the `osbuild/` image route, `SystemUIGoogle` is **deleted** (per `scripts/strip-system-ext.sh`) while AppleUX SystemUI is only an overlay app — so **who draws status bar / nav bar / keyguard on that route is unverified** (no emulator run).

### B1. Dialogs — themed but the iOS component is unused
- **26** real `AlertDialog.Builder` sites across 7 files.
- They are now **re-skinned** to `Theme.Appleux.Dialog` (iOS card: 20sp, no all-caps, iOS line-height, no elevation shadow) via the theme.
- **`AppleuxDialog` = 0 consumers** outside design-system. So the *look* is iOS but the *component* (iOS button ordering / metrics) is not used; the platform still lays out the button bar via AppCompat.Dialog.

### B2. Toasts — the clearest visible stock tell (36 sites)
- **36** `Toast.makeText` call sites across 10 files; top: Photos 16, Messages 12, Launcher 6, SettingsList 4.
- `AppleuxNotificationBanner` exists (iOS banner) but has **0** shell consumers — every toast is the stock system toast, which **cannot be themed**. This is the single most visible remaining stock surface inside our own screens.

### B3. Sliders — stock material (5 sites)
- **5** `new SeekBar(` sites: Photos, Phone, Camera, DisplaySettings, SettingsList.
- `AppleuxPowerSlider` (6 internal uses) and `AppleuxVerticalSlider` both have **0** shell consumers → sliders render as material.

### B4. `android.R.drawable` framework icons (43 refs)
Photos **34**, Messages **6**, SystemUI **1**, NotificationBanner **1**, CallScreen **1**. `PhotosActivity.kt` is the worst offender — the shell still pulls the platform icon set where vendored iOS icons should be (`FilesActivity.kt` is the only shell already using vendored icons).

### B5. Widgets with no iOS theme item (residual)
Not in `Theme.Appleux`: `switchStyle`, `seekBarStyle`, `progressBarStyle`, `checkboxStyle`, `radioButtonStyle`, `spinnerStyle`, `popupMenuStyle` (only `popupBackground` is set), `tabWidgetStyle`, `listViewStyle`.
Practical exposure: SeekBar (B3), ProgressBar (2 sites). CheckBox/RadioButton/Spinner are moot (0 call sites).

### B6. Sound + boot animation
On the **Soong route**, `appleux.mk` wires **neither** boot animation **nor** the 4 sounds — stock Android 16 boot anim + stock sounds ship. Only the `osbuild/` image route wires them (`inject-rootfs.sh` L140-158). The sound assets themselves are dead code: tokens name `appleux_*.wav`, but no code plays them (only `PhoneActivity.kt:584` has a `MediaPlayer`, for the ringtone).

### B7. Vendor overlays are not iOS design
`device/xiaomi/rock/rro_overlay/SettingsOverlayRock/` is an ArrowOS-origin vendor overlay. `FrameworkOverlayRock` is different — it *does* hold real iOS values (accent, switch, radius, round icon, 5 motion durations) and is **untracked**.

---

## 3. Layer C — wired but with zero consumers (dead components)

Components that exist and compile but nothing in launcher/settings/systemui instantiates:

| Component | shell consumers |
|---|---|
| `AppleuxDialog` | 0 |
| `AppleuxPowerSlider` | 0 |
| `AppleuxVerticalSlider` | 0 |
| `AppleuxNotificationBanner` | 0 |
| `AppleuxSystemHUD` | 0 |
| `AppleuxScreenshotToolbar` | 0 |
| `AppleuxPasscodeView` | 0 |
| `AppleuxRecentsView` | 0 |
| `AppleuxLockScreen` | 0 |
| `AppleuxSheet` | 0 |
| `AppleuxSpotlightView` | 1 |
| `AppleuxTodayView` | 1 |
| `AppleuxToggle` | 3 ✅ |

Still missing entirely (no AppleUX impl on either route): **Siri**, **iOS share sheet**, **iOS keyboard**.

---

## 4. Build status — RED

`scripts/build-emulator-apks.sh` was run at ~15:00 and **failed at the launcher**:

- **design-system** — OK (APK 15:00)
- **performance** — OK (APK 15:01)
- **launcher** — **FAIL, 92 errors**; APK still 09:24
- **settings / systemui** — never reached; APKs still 09:25 / 09:26 (**never compiled against current source**)

Error distribution (`out/gen/launcher-kotlinc.log`, 15:02):

| File | errors |
|---|---|
| `apps/PhotosActivity.kt` | 64 |
| `apps/CameraActivity.kt` | 21 |
| `apps/PhoneActivity.kt` | 3 |
| `apps/MessagesActivity.kt` | 2 |
| `apps/CalendarActivity.kt` | 2 |

Error kinds: 51 `argument type mismatch`, 33 `unresolved reference`, 3 `no candidates`, 2 `overrides nothing`, +3 misc. All in **WIP shell apps** the parallel session is mid-edit on (Photos/Camera shells grew past 2 900 / 1 000 lines during the pass).

> Because the build aborts at launcher, **settings and systemui have never been compiled against the current source**. Their green status in `AGENTS.md` refers to the 09:25/09:26 APKs.

---

## 5. Prioritised gap list (r2)

**P0 — visible on the device, in our own screens**
1. Toasts (36) → iOS banner. `AppleuxNotificationBanner` already exists; wire it. **(B2)**
2. Sliders (5 `SeekBar`) → `AppleuxPowerSlider`/`AppleuxVerticalSlider`. **(B3)**
3. `android.R.drawable` in `PhotosActivity` (34) → vendored iOS icons. **(B4)**

**P1 — system chrome (structural)**
4. Status bar / nav bar / QS / keyguard / recents / HUD / screenshot UI still stock, or on the image route unverified. **(B0)**

**P2 — theme residuals**
5. Add `seekBarStyle` / `progressBarStyle` theme items. **(B5)**
6. Wire boot animation + sounds on the **Soong** route. **(B6)**

**P3 — dead components**
7. 10 components with 0 consumers — wire or delete. **(§3)**

**Blocker**
8. Launcher does not compile (92 errors) → settings/systemui unverified. **(§4)**

---

## 6. Honesty bounds

- **Static inspection only.** No emulator run; no runtime verification on any route.
- **Counts are a snapshot** of an actively-edited tree (15:00-15:05).
- **r1 was wrong on two method points** — `SeekBar 38` and `EditText 22` were word-occurrences, not call sites; real `SeekBar` constructor sites are 5. r1's theme findings (B2) are now obsolete.
- **Nothing was committed.** Outer HEAD unchanged; inner `device/xiaomi/rock` tree has uncommitted modifications.
- `--auto-add-overlay` can let a build pass while an overlay key silently misses — verify keys by grep against the target package, never by the build alone.
- **Fidelity** (does it match real iOS 27?) is owned by `ipsw-surface-comparison-2026-10-17.md`; this file measures **coverage**, not pixel fidelity.
