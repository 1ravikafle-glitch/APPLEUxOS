# iOS-Design Coverage Audit — Where iOS Is Present vs Where Stock Android Still Shows

> **SUPERSEDED (15:05, 2026-10-17)** by `ios-design-coverage-audit-r2-2026-10-17.md`.
> The theme layer changed materially after this file was written (a unified
> `Theme.Appleux` landed in design-system, which resolves the B2 finding and
> mitigates B1/B3). Read the r2 file, not this one, for current state.

**Date:** 2026-10-17
**Scope:** The whole `/home/elfakiris/OsProject` tree — AppleUX modules, the AOSP tree, the device overlays, and the shipping image path.
**Method:** Read-only static inspection (source, manifests, layouts, values, build/injection scripts). **No files were changed. No emulator was run. Nothing was committed.**
**Parallel work:** `deliverables/m23/ipsw-surface-comparison-2026-10-17.md` (same day, 11:03) is a *fidelity* audit of 10 surfaces against IPSW plist data, and it **applied fixes**. That work is authoritative and is **not** duplicated here. This document is a *coverage* audit: it maps which surfaces are iOS-designed, which are still stock Android, and which are missing on both sides. Where the two overlap, defer to the IPSW file.

> **The tree was being edited while this was written** (Contacts 696 → 2674 lines, Photos 905 → 2389, Files 737 → 2260 during the pass). Counts are a moving-target snapshot; the *structure* and the *stock-widget sites* below are what matters and are stable.

---

## 0. The one-sentence answer

The **home screen, the app shells, and the overlay chrome (shade/Control Center/lock elements) are iOS-designed**, but **the real system chrome is not ours** — the status bar, quick settings, navigation bar, recents, volume/power HUD, keyguard and screenshot UI are still the stock SystemUI process (or, on the image route, deleted without a verified replacement); and **inside our own screens** the stock Android widget set is still doing the work — `AlertDialog` ×23, `Toast` ×36, `SeekBar` ×38, `EditText` ×22, `Switch` ×2, `TextClock` ×5 — even though iOS-styled equivalents (`AppleuxDialog`, `AppleuxSheet`, `AppleuxToggle`, `AppleuxPowerSlider`) exist in the design system.

---

## 1. Three-layer model

| Layer | Meaning | Count |
|---|---|---|
| **A — iOS-designed** | AppleUX source draws it in iOS idiom | launcher, 13 app-shell activities, 51 design-system components, 7 Settings screens, 3 widget providers, 117 vendored icons |
| **B — Stock Android showing** | Still the platform's design (process, widget, theme, or asset) | system chrome process, dialogs/toasts/sliders/inputs, themes, 38 stock icons, boot animation, sounds, vendor overlays |
| **C — Missing on both** | iOS surface with no AppleUX implementation and, on the image route, no stock one either | Siri, share sheet, iOS keyboard, wired HUDs/recents/passcode/screenshot toolbar |

---

## 2. Layer A — what *is* iOS-designed

**Launcher** — `HomescreenGrid`, `DockView`, `AppLibraryView`, `AppleuxFolderView`, `AppIconView`, `AppleIconSquircle`, `JiggleAnimation`, `SquircleImageView`, `DockPreferences`, `AppleuxLibraryFolder`, `AppleuxAppsSheet`, `AppleuxIconPack`. Metrics derived at the measured POCO M5 frame (392.73 × 875.64 dp), per `LauncherTokens`.

**Home widgets** — `AppleuxWidgets` ships 3 `AppWidgetProvider`s (clock / battery / calendar) via `RemoteViews` + `widget_*.xml`, registered in `LauncherActivity` L66-68.

**Gestures** — `GestureHandler` + `GestureHostLayout` (observe-without-consume host, per AGENTS.md).

**App shells** (launcher-owned, `apps/`): Contacts, Phone, Messages, Photos, Camera, Calendar, Files, Browser, Clock, CallScreen, InCallService, CallRecorder, VideoPlayer.

**Design system — 51 components**, including the iOS-specific ones: `AppleuxDialog`, `AppleuxSheet`, `AppleuxToggle`, `AppleuxPowerSlider`, `AppleuxVerticalSlider`, `AppleuxButton`, `AppleuxCard`, `AppleuxListItem`, `AppleuxText`, `AppleuxGlass`, `AppleuxGlassForeground`, `AppleuxSurface`, `AppleuxSuperellipse`, `AppleuxStatusBar`, `AppleuxNavigationBar`, `AppleuxHomeIndicator`, `AppleuxPageDots`, `AppleuxDynamicIsland`, `AppleuxNotificationShade` (+ Header/Footer/Item/Banner/Expanded), `AppleuxControlCenter` (+ Header/CCTileOrder/ControlTile/ConnectivityCluster), `AppleuxLockScreen`, `AppleuxLockCustomizeSheet`, `AppleuxSpotlightView`, `AppleuxTodayView`, `AppleuxRecentsView`, `AppleuxPasscodeView`, `AppleuxSOSCountdown`, `AppleuxScreenshotToolbar`, `AppleuxSystemHUD`, `AppleuxMediaCard`, `AppleuxPanelDismissHandler`, `AppleuxTouchFeedbackOverlay`, `AppleuxUnavailableTimer`, `AppleuxIcon`/`AppleuxIconRegistry`, `AppleuxHaptics`, `AppleuxDisplayInsets`, `AppleuxSettingsDetailView`, `AppleuxComponentsWiring`.

**Settings** — 7 activities declared: `SettingsActivity`, `SettingsListActivity`, `DisplaySettingsActivity`, `SecuritySettingsActivity`, `PerformanceSettingsActivity`, `WifiDetailActivity`, `BatteryDetailActivity`. It answers `android.settings.SETTINGS`.

**Vendored iOS iconography** — 47 `il_ic_*.xml` + 45 `sf_*.xml` in `design-system/res/drawable` (95 files) and 25 `ios_*.png` app tiles in `launcher/res/drawable-nodpi`. Plus 6 lowercase SF Symbol fonts staged.

---

## 3. Layer B — where **stock Android design still shows**

### B0 — P0: the real system chrome is not ours

`AppleuxSystemUIService` is a **foreground service that adds `TYPE_APPLICATION_OVERLAY` windows** (`AppleuxSystemUIService.kt:494-509`) — i.e. it draws its own notification shade and Control Center *on top of* the platform, it does **not** replace the SystemUI process.

Evidence the platform still owns the bars:
- `android-16/frameworks/base/packages/SystemUI` — **9570 source files, present and untouched**.
- The AppleUX manifest declares **no** SystemUI role/component — only a service + receivers.
- `AppleuxStatusBar` is a view that AppleUX *draws inside its own windows* (`AppleuxNotificationShade.kt:41`, `LauncherActivity.kt:493`), not the platform status bar.

So the platform still draws: **status bar, quick-settings panel, navigation bar / gesture pill, volume HUD, power menu, keyguard, screenshot UI, and recents**.

Two caveats, stated honestly:
1. **Recents** is explicitly still QuickStep (AGENTS.md) and `AppleuxRecentsView` has **0 consumers**.
2. On the **image route** (see §5) `SystemUIGoogle` is *deleted*. Whether the system bars then appear (drawn by anything) is **unverified** — no emulator run was performed. This is a risk, not a confirmed bug: deleting the SystemUI process while only an overlay app remains could leave the bars absent, which is worse than stock.

### B1 — P1: stock Android widgets inside iOS surfaces

| Stock widget | Uses | Sites |
|---|---|---|
| **`AlertDialog`** | 23 | PhotosActivity 8, LauncherActivity 6, ContactsActivity 4, AppleuxAppsSheet 2, AppLibraryView 1, PhoneActivity 1, AppleuxComponentsWiring 1 — **while `AppleuxDialog` exists and is used in only 3 files** |
| **`Toast`** | 36 | 14 files (Photos 10, Launcher 6, Messages 4, SettingsList 4, + Phone/CallScreen/Contacts/Camera/SecuritySettings/DisplaySettings 2 each, …) — iOS has no toast |
| **`SeekBar`** | 38 | CameraActivity 11, AppleuxPowerSlider 6, PhotosActivity 6, SettingsListActivity 6, DisplaySettingsActivity 6, PhoneActivity 3 — stock track/thumb look |
| **`EditText`** | 22 | ContactsActivity 5, MessagesActivity 4, BrowserActivity 3, AppLibraryView 2, PhotosActivity 2, ClockActivity 2, AppleuxSpotlightView 2, LauncherActivity 1, AppleuxNotificationItem 1 — stock field |
| **`Switch`** | 2 | `AppleuxToggle` (wrapper) + SettingsListActivity — stock material toggle |
| **`TextClock`** | 5 | `AppleuxWidgets` widget XML — stock widget drives the iOS clock widget |
| **`ProgressBar`** | 1 | `AppleuxSystemHUD` |

`AppleuxPowerSlider`/`AppleuxToggle` are wrappers *around* the stock controls, so the iOS look is only as good as the wrapper's overrides.

### B2 — P1: themes are AppCompat / Material parents

```
launcher  AppleuxLauncherTheme  parent Theme.AppCompat.Light.NoActionBar
settings  AppleuxSettingsTheme  parent Theme.AppCompat.Light.NoActionBar
systemui  Theme.Appleux         parent android:Theme.Material
```

Two concrete defects:
- `appleux/launcher/src/main/res/values-night/themes.xml` is **empty** — the launcher has no night theme at all.
- `appleux/settings/src/main/res/values-night/themes.xml` is a **copy of the light theme** (`Theme.AppCompat.Light.NoActionBar`), so the "night" resource changes nothing at the theme level.

### B3 — P2: stock icons *inside* the iOS shells

`android.R.drawable.*` — **38 refs in 5 files**:

| File | Refs | Examples |
|---|---|---|
| `PhotosActivity.kt` | 30 | `ic_menu_*` throughout |
| `MessagesActivity.kt` | 5 | `ic_menu_search`/`_edit`/`_camera`/`_send` |
| `AppleuxSystemUIService.kt` | 1 | `presence_online` |
| `AppleuxNotificationBanner.kt` | 1 | `sym_def_app_icon` |
| `AppleuxCallScreenActivity.kt` | 1 | — |

Meanwhile the **117 vendored iOS icons are almost entirely unused inside app shells**: across all 13 shell activities, the only non-stock icon references are in `FilesActivity` (`il_ic_` ×1, `sf_` ×3). The `ios_*.png` tiles are used on the *home surface* via `AppleuxIconPack` but not inside the apps.

### B4 — P2: boot animation and system sounds

- **Boot animation** is wired **only on the image route** (`osbuild/work/bootanimation.zip` → `/system/media/bootanimation.zip`, embedded boot chime). The AOSP route leaves the stock animation.
- **Sounds** (`appleux_boot/lock/unlock/charge.wav`) are likewise installed **only on the image route**. **No AppleUX code plays them** — the only `MediaPlayer` in the tree is `PhoneActivity.kt:584`. So the audible UI is stock.

### B5 — P3: non-design vendor overlays

Not everything in `rro_overlay/` is iOS design. These are **ArrowOS / LineageOS device leftovers**, unrelated to iOS:

- `SettingsOverlayRock` — **ArrowOS** (Copyright 2021 ArrowOS; ships `maintainer_img.jpg`, `regulatory_info.png`, refresh-rate switches, color modes).
- `CarrierConfigOverlayRock`, `WifiResOverlayRock`, `SettingsProviderResOverlayRock`, `SettingsOverlayStone`, `SettingsProviderResOverlayStone`.

The design-relevant overlays are `FrameworkOverlayRock` (target `android`) and `SystemUIOverlayRock` (target `com.android.systemui`).

### B6 — P3: dead overlay resources (compile, ship, do nothing)

RROs bind only to names already present in the target package; invented names are inert.

| Overlay | Keys | Inert |
|---|---|---|
| `FrameworkOverlayRock` (target `android`) | 100 | 26 — **`bools.xml` 11/11 dead** |
| `SystemUIOverlayRock` (target `com.android.systemui`) | 38 | 16 — **`apple_hig.xml` 10/10 dead** (its `dimens.xml` 11/11 and `config.xml` 11/17 are real) |
| emulator `appleux/rro-frameworks` (target `android`) | 49 | 39 |

Also a drift: `dialog_corner_radius` is **14dp** in the emulator overlay vs **13dp** in the device overlay.

The **ROM's accent is correct**, though: `FrameworkOverlayRock/apple_hig.xml` carries 15/15 real keys (`accent_device_default_light/dark`, `switch_thumb_normal_material_light/dark`, 4 corner-radius keys, `config_useRoundIcon`, 5 motion durations).

---

## 4. Layer C — iOS surfaces missing on both sides

| iOS surface | AppleUX status | Result |
|---|---|---|
| **Siri** | no component | absent entirely |
| **Share sheet / action sheet** | `AppleuxSheet` exists (1 consumer); no iOS share sheet / activity view | stock Android chooser |
| **iOS keyboard** | none | stock AOSP/Google keyboard |
| **Recents / app switcher** | `AppleuxRecentsView` — **0 consumers** | QuickStep (or nothing on the image route) |
| **Volume / brightness HUD** | `AppleuxSystemHUD` — **0 consumers** | stock SystemUI HUD |
| **Screenshot toolbar** | `AppleuxScreenshotToolbar` — **0 consumers** | stock SystemUI screenshot UI |
| **Passcode / keyguard** | `AppleuxPasscodeView` — **0 consumers**; `AppleuxLockScreen` 1 consumer | stock keyguard |
| **Notification Center cutout** | known gap (AGENTS.md) — `AppleuxDisplayInsets.applyTopInset` not applied | shade collides with the waterdrop |
| **Reduce Motion** | stored + propagated, **not consumed** | no effect |
| **Wallpaper picker** | none (and `LiveWallpapersPicker` is deleted by the image route) | no iOS wallpaper UI |

---

## 5. The two build routes disagree about stock — this matters

| | **AOSP / Soong** (`device/xiaomi/rock`) | **Image injection** (`osbuild/` + `scripts/strip-system-ext.sh`) |
|---|---|---|
| Stock SystemUI / Launcher3 / Settings | **present** (fallback, by design) | **deleted** — `SystemUIGoogle`, `NexusLauncherRelease`(+QuickStep), `SettingsGoogle` removed from `system_ext` |
| AppleUX apps | `PRODUCT_PACKAGES` (not built into an image yet) | injected into `/system/priv-app` |
| RROs | `FrameworkOverlayRock`, `SystemUIOverlayRock`, `SettingsOverlayRock` | static APKs into `/system/overlay` |
| Boot animation / sounds | **not wired** | **wired** (bootanimation + 4 wavs) |
| Target | arm64 POCO M5 | **x86_64 emulator** image (`/oat/x86_64` in the strip list) |
| Doctrine | "stock stays until ours is verified" | "NAKED-BASE — no stock UI surface survives" |

The image route is the one that actually removes stock, so its §3-B0 caveat is the live one: with `SystemUIGoogle` deleted and AppleUX SystemUI only an overlay app, **who draws the status bar / nav bar / keyguard needs a runtime check that was not performed.**

`osbuild/out/appleux-system.img` (08:50 today) and `…img.pre-stockdelete` exist — the naked-base image has been produced.

---

## 6. Prioritised gap list

| # | Gap | Layer | Evidence |
|---|---|---|---|
| 1 | Real system chrome (status/nav/QS/recents/HUD/keyguard/screenshot) is stock SystemUI, not ours | B0 | `SystemUI` 9570 files; service uses `TYPE_APPLICATION_OVERLAY` |
| 2 | Recents still QuickStep | B0 | `AppleuxRecentsView` 0 consumers |
| 3 | Stock Settings still the real Settings for every non-`SETTINGS` intent | B0 | 3828 source files; AppleUX covers 7 screens |
| 4 | `AlertDialog` ×23 while `AppleuxDialog` used 3× | B1 | per-file counts above |
| 5 | `Toast` ×36 (no iOS equivalent) | B1 | per-file counts above |
| 6 | `SeekBar` ×38 / `EditText` ×22 / `Switch` ×2 / `TextClock` ×5 | B1 | per-file counts above |
| 7 | Themes parent AppCompat/Material; launcher night theme empty; settings night = light | B2 | themes.xml files |
| 8 | 38 stock `android.R.drawable` refs inside shells; 117 iOS icons ~unused in-app | B3 | refs above |
| 9 | Sounds never played; boot animation AOSP-route unwired | B4 | no playback call sites |
| 10 | UIKit-less surfaces: Siri, share sheet, keyboard | C | absent |
| 11 | Unwired components: HUD, screenshot toolbar, passcode, recents (0 consumers each) | C | wiring counts |
| 12 | Dead overlay keys (11+10+39) + ArrowOS/Lineage vendor overlays | B5/B6 | RRO scans |
| 13 | Notification shade doesn't reserve the cutout; Reduce Motion unconsumed | C | AGENTS.md known gaps |
| 14 | `SystemUIGoogle` deleted on the image route with no verified replacement | B0 | `strip-system-ext.sh` |

---

## 7. What I did not verify (honesty bounds)

- **No emulator run, no device run.** Every statement about what is *on screen* is inferred from source and build scripts. The §3-B0 / §5 question ("do the bars exist on the naked-base image?") is **open**.
- **No frame measurements.** Emulator figures under SwiftShader say nothing about the 4 GB POCO M5.
- **Counts are a snapshot** of a tree that was being actively edited during the pass.
- Stock-app presence was read from `device/*.mk` and the AOSP tree; I did not enumerate the final image's `/system/app` to confirm each stock app ships.

---

## 8. Pointers

- Fidelity (not coverage) audit + applied fixes: `deliverables/m23/ipsw-surface-comparison-2026-10-17.md`
- Prior AppleUX quality audits: `deliverables/m22/ios27-fidelity-audit-r2-2026-10-17.md` (canonical, incl. re-check) and `ios27-fidelity-audit-2026-10-17.md` (superseded)
- Deployability: `deliverables/m22/deployability-audit-2026-10-17.md`
- Image route: `osbuild/inject-rootfs.sh`, `scripts/strip-system-ext.sh`
- Known gaps the project already tracks: `AGENTS.md` → "Other Known Gaps"
