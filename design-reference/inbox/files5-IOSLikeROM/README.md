# IOSLike ROM layer (Android 16 / AOSP) - Control Center + Notification Center

**Status: written, NOT compiled and NOT run on a device.** It targets the Android 16 platform APIs from memory, so
expect to fix names, Compose module names and a few hidden-API signatures on first build. Treat it as a
well-structured starting point, not a verified build.

## What is in here
| Path | What it is |
|---|---|
| `packages/apps/IOSLikeUI/` | Privileged, platform-signed, persistent app (Kotlin + Compose) |
| `.../controlcenter/` | `ControlCenterController` (real system state + toggles) and `ControlCenterScreen` (glass UI) |
| `.../notifications/` | `IOSNotificationListener`, `NotificationCenterScreen`, left-sliding heads-up banner |
| `.../shell/ShellService.kt` | Windows + gestures: top-left = Notification Center, top-right = Control Center, top-middle = Search or Ask |
| `device/ioslike/` | `ioslike.mk`, privapp permission allowlist, RRO that grants listener/DND access |

Toggles implemented: Airplane, Mobile data, Wi-Fi, Bluetooth, Nearby Share (AirDrop-style, needs a backend),
Rotation lock, Focus/DND, Flashlight, Dark mode, Brightness, Volume.

## Wire it into the tree
```bash
cp -r IOSLikeROM/packages/apps/IOSLikeUI  <aosp>/packages/apps/
cp -r IOSLikeROM/device/ioslike           <aosp>/device/
# in your device .mk:
$(call inherit-product, device/ioslike/ioslike.mk)
# then:
source build/envsetup.sh && lunch <target>-userdebug
m IOSLikeUI IOSLikeOverlay          # fast module build while iterating
adb root && adb remount && adb sync  # or flash the full image
```

## Things you will have to check (known risks)
1. **Compose in Soong.** `Android.bp` lists best-guess module names. Copy the Compose block (static_libs, compiler plugin,
   kotlincflags) from `frameworks/base/packages/SystemUI/Android.bp` in your tree.
2. **Window type.** `TYPE_NOTIFICATION_SHADE` is hidden API. If a non-SystemUI process is rejected, move the three windows
   into SystemUI (hook where `NotificationShadeWindowController` is created) - the Compose screens work unchanged.
3. **Stock shade.** `StatusBarManager.disable(DISABLE_EXPAND)` stops the AOSP shade opening. Review side effects (lock screen, heads-up).
4. **Permissions.** A privileged permission missing from `privapp-permissions-*.xml` makes the device bootloop-check/log
   `Privileged permission ... not in allowlist`. Fix from logcat.
5. **Hidden calls.** `ConnectivityManager.setAirplaneMode`, `TelephonyManager.setDataEnabled`, `BluetoothAdapter.enable` may
   have moved to different classes in Android 16; compile errors will tell you.
6. **Config names** in `overlay/res/values/config.xml` must match `frameworks/base/core/res/res/values/config.xml`.
7. **Glass blur** uses window-level blur (`FLAG_BLUR_BEHIND`); it needs a GPU/device that supports cross-window blur.
   Tiles are translucent fills, not per-tile backdrop blur.
8. **Icons** in `res/drawable/ic_*.xml` are placeholder rings. Replace with Material Symbols (Apache-2.0) or your own.
   Do not use Apple's SF Symbols or icons.
9. **AirDrop** is Apple's protocol and cannot exist on Android. `NearbyShareBackend` is the hook for whatever you ship.

## Quick device checks once it boots
```bash
adb logcat | grep -i ioslike
adb shell dumpsys window windows | grep -i IOSLike      # three windows should be listed
adb shell cmd notification allow_listener org.ioslike.ui/org.ioslike.ui.notifications.IOSNotificationListener
```

## Lock screen (added)
Files: `lockscreen/LockScreen.kt` (`LockScreen`, `PasscodeScreen`), plus `ic_lock` / `ic_camera` placeholder icons.

A normal app cannot replace the real keyguard, so this is a UI layer you host inside SystemUI:
1. In your tree, find the keyguard root view (`KeyguardRootView` / `KeyguardViewConfigurator` area under
   `frameworks/base/packages/SystemUI/src/com/android/systemui/keyguard/`) and hide the stock clock/notification UI.
2. Add a `ComposeView` there that calls `LockScreen(notifs = IOSNotificationListener.items..., torchOn, LockCallbacks(...))`.
   (Move the notification listener into SystemUI, or keep the app and bind to it.)
3. `onUnlockGesture` should show `PasscodeScreen` (or the stock bouncer if you prefer). In `PasscodeScreen.onSubmit`, verify with
   the platform's credential check (`LockPatternUtils.checkCredential(...)`) and then dismiss the keyguard
   through `KeyguardViewController`. These class names change between releases: grep your tree.
4. `onCamera` -> start the secure camera intent (`MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE`);
   `onFlashlight` -> `ControlCenterController.toggleFlashlight()`.
Never store or log the entered passcode; `PasscodeScreen` only passes it to `onSubmit`.

## Real glass (v2) - what changed and what it can and cannot do
Apple's Liquid Glass code is proprietary, so a pixel-exact copy is not possible. The closest open implementation is
**Kyant0/AndroidLiquidGlass** (`com.kyant.backdrop` + `com.kyant.capsule`): backdrop blur, vibrancy, lens refraction (AGSL,
**Android 13+**), highlight rim and continuous "squircle" corners. This project now uses it.

How it is wired:
- `theme/LiquidGlass.kt`: `GlassScene` draws the backdrop (wallpaper, optionally blurred/dimmed) and provides it;
  `Modifier.glass(...)` automatically becomes real glass inside a scene (falls back to translucent fill outside one);
  `Modifier.pressable` gives springy press-scale + haptics (put it BEFORE `glass`).
- Lock screen, passcode keys, Notification Center cards and the whole Control Center now sit on a `GlassScene`.
- Control Center v2: Connectivity (Airplane, Mobile data, Wi-Fi, Bluetooth), Now Playing, Rotation lock, Screen mirroring,
  Focus, Brightness and Volume sliders, **Quick Share** (replaces AirDrop), Hotspot, Torch, Timer, Calculator, Camera.

### Adding the library to the ROM build
1. Get the library's current Maven coordinates/version and LICENSE from its README (I could not verify them here), download
   the `backdrop` and `capsule` AARs, and put them in `prebuilts/kyant/`.
2. Add to `packages/apps/IOSLikeUI/Android.bp`:
```
android_library_import { name: "kyant-backdrop", aars: ["prebuilts/kyant/backdrop.aar"], sdk_version: "current" }
android_library_import { name: "kyant-capsule",  aars: ["prebuilts/kyant/capsule.aar"],  sdk_version: "current" }
```
3. Its Compose version must match the Compose in your AOSP tree. If they clash, vendor the library's source instead.

### Honest limits
- **Backdrop = wallpaper, not live apps.** Compose can only refract what is inside its own tree, so the panels refract a
  wallpaper snapshot. To show the real screen behind Control Center, pass a screenshot bitmap as `wallpaper`
  (platform screen-capture API, e.g. `ScreenCapture`/`SurfaceControl` captureDisplay: names vary by release; not included).
- **"Same smoothness as iOS" is not guaranteed.** Lens + blur are GPU-heavy: keep lens on few elements, test on the target
  phone, lower `blur`/`refraction*` in `liquidGlass()` if frames drop. Animate with `graphicsLayer {}` lambdas, not state in composition.
- **Quick Share needs Google Play Services.** Without GMS the tile shows "Needs Google services"; supply your own `NearbyShareBackend`.
- Media card needs notification-listener access (granted by the overlay) and reads active media sessions.
- Wallpaper loading uses `READ_WALLPAPER_INTERNAL` (added to manifest + allowlist).
- Do not copy Apple's assets (SF Pro, icons, wallpapers). Matching a look is fine; shipping Apple's assets is not.

### Fast way to tune the glass without rebuilding the ROM
Make a normal Android Studio app on your phone (API 33+), copy `theme/`, `notifications/`, `lockscreen/` and the drawables,
and call `LockScreen(fakeNotifs, false, wallpaperBitmap, LockCallbacks({}, {}, {}))` from an Activity.
Tune the numbers in `liquidGlass()` there, then copy the files back.

## Design <-> code parity (v3)
| Design board | Code | File |
|---|---|---|
| Home & Dock (widget, grid, dock, Search or Ask, dots) | yes (NEW) | `launcher/HomeScreen.kt`, `AppRepo.kt`, `LauncherActivity.kt` |
| App Library (search + glass folders) | yes (NEW) | `launcher/AppLibraryScreen.kt` |
| Notification Center (Now / Earlier today, stacks, swipe to dismiss) | yes (sections added) | `notifications/NotificationCenterScreen.kt` |
| Control Center | yes | `controlcenter/*` |
| Lock Screen | yes | `lockscreen/LockScreen.kt` |
| Passcode keypad (with letters) | yes (design added) | `PasscodeScreen` in `LockScreen.kt` |
| Heads-up banner from the left | code only | `HeadsUpHost` |
Icons: `glyphs.py` is the single source for the system glyphs; the same paths are in the design and the `res/drawable/ic_*.xml` vectors.

### Launcher notes (honest)
- `LauncherActivity` is declared HOME. It is a standalone Compose launcher: no drag-to-rearrange / edit mode / folders yet,
  and home pages are just your apps in alphabetical order.
- Gesture navigation (swipe-up-home, recents) is implemented by Launcher3 **Quickstep**. A non-Quickstep home will not get
  those animations unless you fork Quickstep or keep it as the recents provider. Decide this before shipping.
- Suggestions use usage stats (`PACKAGE_USAGE_STATS`, allowlisted); Recently Added uses install time.
- Dock = your default phone, messages, browser and camera apps.

## Target phone: Poco M5 (Helio G99, Mali-G57 MC2, 4 GB RAM, 64 GB)
- Codename `rock`; your fingerprint `POCO/rock_p_in/rock` is the India build. (I earlier tied model numbers 22071219CI/CG to codenames; that mapping is NOT verified.)
  The Poco M5**s** is a different phone with official LineageOS; the M5 has none.
- No official AOSP/Lineage support and no Android 16 vendor/kernel for it, so building a full ROM with your own device tree is a
  big project. **Practical path: build an AOSP GSI (generic system image) with these UI changes and flash it over the stock vendor.**
  A community guide for this phone exists on the phh-treble wiki ("Xiaomi POCO M5"): unlock bootloader (Mi Unlock), check
  `fastboot getvar current-slot`, vbmeta without verification, system resizer, flash GSI, format data.
- Unknown until tested: whether an Android 16 GSI boots on this phone's older vendor image. Validate the flash pipeline with an
  older GSI first; if Android 16 will not boot, build the same code on the newest branch that does (Compose lens needs Android 13+).
- Glass quality: `GlassConfig.autoDetect()` picks Medium on a 4 GB phone (blur + vibrancy, no lens). Try High on the device and
  fall back if frames drop. Low = translucent only. The wallpaper blur is a cheap downscale at Medium/Low.
- Risks: unlocking wipes the phone, Xiaomi may impose a waiting period, banking/Play Integrity apps may stop working,
  a bad flash can brick. Keep the stock fastboot ROM and a backup.

## Verified device facts (from your adb output)
| Property | Value | Meaning |
|---|---|---|
| ro.product.device | rock | Poco M5 (India model 22071219CI) |
| ro.build.version.release | 14 | stock system is Android 14 (HyperOS, build V816.0.8.0.ULUINXM, India) |
| ro.treble.enabled | true | GSI is possible |
| ro.vndk.version | 31 | the vendor image is Android 12 level |
| ro.boot.slot_suffix | _b | active slot is B |

Consequences:
- **Target: Android 16** (your decision). Hardware note, stated once: this phone's vendor image is Android 12 level, so whether an Android 16
  system image boots on it is unverified; Android 14 is only a fallback if it will not boot. Expect to fix hidden-API names on the Android 16 branch.
- **Active slot B**: the community Poco M5 guide says to flash the stock fastboot ROM first in this case. Use an India ROM at the
  same or NEWER version than V816.0.8.0.ULUINXM (Xiaomi anti-rollback can brick on older builds), and do not use a script that re-locks the bootloader.
- Plain AOSP GSIs often miss MediaTek fixes (camera, sensors, calls). phh's `treble_experimentations` build scripts apply such patches; plan to
  add `IOSLikeUI` + `IOSLikeOverlay` as packages in that build instead of a vanilla AOSP GSI.

## Settings (iOS-style front end)
Files: `settings/SettingsKit.kt` (large collapsing title, glass nav bar + back button, inset grouped lists, icon tiles, iOS switch),
`SettingsActivity.kt` (home list), `WifiPage.kt` (real toggle + scan), `DisplayPage.kt` (real appearance, brightness, auto-lock).
Honest scope: this is a new front end, not a rewrite of AOSP Settings. Rows without their own page open the matching stock Settings screen
(`setPackage("com.android.settings")`), so stock Settings must stay in the build. The home grid hides the stock Settings icon and shows ours.
Wi-Fi scan needs location permission (default-permissions XML grants it) and joining a network opens the stock Wi-Fi screen.
Settings uses opaque grouped lists like Apple's; glass is only on the nav bar, back button and search.
