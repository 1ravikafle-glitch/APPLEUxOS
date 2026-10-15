# Agent handoff: iOS 27-style UI layer for Android 16

Goal: a system UI layer (launcher, lock screen, passcode, notification center, control center, settings front end) styled like iOS 27, as a
privileged Compose app (`packages/apps/IOSLikeUI`) plus a device overlay (`device/ioslike`). Target: **Android 16 (AOSP)**.
Match the LOOK; never copy Apple assets (SF Pro, icons, wallpapers). Tested nowhere yet: nothing here has been compiled or run on a device.

## Source of truth (read in this order)
1. `spec/tokens.json`: every color, radius, spacing, size, type style, glass/motion parameter. Colors are `#AARRGGBB`.
2. `spec/screens.json`: all 11 screens (390x844 frame) as layout trees. Node types: `box` (dir row|col, gap, pad [t,r,b,l], align, just
   start|center|end|between|around, w/h number|"fill", `abs`=[x,y] anchored, `fill` glass|token|{glass:c}|{grad:[a,b]}|{tint:c}, `r` token|number|"circle",
   `clip`, `scroll`, `divider` indent, `ml/mr`), `text` (type = key in tokens.type), `icon` (glyph name), `appicon`, `switch`, `slider`, `art`.
   `component` names the reusable piece; `tap`/`hit` mark touch targets; `code` lists the Kotlin file that implements the screen.
3. `tools/glyphs.py`: the icon set. Same paths feed the HTML design and `res/drawable/ic_*.xml`.
4. Kotlin in `packages/apps/IOSLikeUI/src/org/ioslike/ui/...` (see the map below).

## Rules
- Change a value ONLY in `spec/tokens.json`, then run `python3 tools/gen_tokens.py` (regenerates `theme/Tokens.kt` and `spec/tokens.css`). Never edit `Tokens.kt`.
- Layout changes go in `tools/dsl.py` (writes `spec/screens.json`); then run `python3 tools/layout_check.py`. It must print `OK`.
- `python3 tools/render_html.py <dir>` redraws every design board from the spec.
- New Kotlin must use `Tokens.*`. `python3 tools/find_hardcoded.py` lists remaining literals (currently ~229; drive it down).
- Glass: use `Modifier.glass(shape, tint)` inside a `GlassScene`; press feedback: `Modifier.pressable` BEFORE `glass`. Quality tiers in `GlassConfig`.

## Screen -> code map
| spec id | Kotlin |
|---|---|
| home, home_2 | `launcher/HomeScreen.kt` (`LauncherRoot`; dots, Search or Ask pill and dock are fixed, only pages scroll) |
| app_library | `launcher/AppLibraryScreen.kt` |
| notifications | `notifications/NotificationCenterScreen.kt`, `IOSNotificationListener.kt` |
| control_center | `controlcenter/ControlCenterScreen.kt`, `ControlCenterController.kt`, `MediaRepo.kt`, `QuickShareBackend.kt` |
| lock, passcode | `lockscreen/LockScreen.kt` (`LockScreen`, `PasscodeScreen`) |
| settings, settings_scrolled, settings_wifi, settings_display | `settings/SettingsKit.kt`, `SettingsActivity.kt`, `WifiPage.kt`, `DisplayPage.kt` |
| (windows, gestures) | `shell/ShellService.kt`: top-left = Notification Center, top-right = Control Center, top-middle = Search or Ask |

## Workflow for an agent
1. `python3 tools/gen_tokens.py && python3 tools/dsl.py && python3 tools/layout_check.py`
2. Pick a screen, read its tree in `spec/screens.json`, open its Kotlin file, and bring the Kotlin in line with the tree (sizes, gaps, order, radii).
3. Replace literals with `Tokens.*`; if a value is missing add it to `tokens.json` and regenerate.
4. Build per `README.md` (Soong). Fix Compose/hidden-API names against the Android 16 tree. Do not claim a screen is done until it runs on a device or emulator.

## Device profile: POCO M5 (`rock`)
Source of truth: `spec/devices/rock.json` (written by `tools/make_profile.py`). From the spec sheets: 6.58" IPS LCD, **1080x2408 px**, ~401 ppi, 30/60/90 Hz,
waterdrop notch, Helio G99, Mali-G57 MC2, 4 GB RAM. **Assumed until measured** (flagged `measured:false`): density 440 dpi -> **392.7 x 875.6 dp**; status bar 32 dp; gesture bar 24 dp.
Measure on the phone, then regenerate everything in one go:
```
adb shell wm size ; adb shell wm density ; adb shell dumpsys window | grep -iE 'statusBars|navigationBars|displayCutout'
python3 tools/make_profile.py --size 1080x2408 --density <N> --top <dp> --bottom <dp> --measured
python3 tools/dsl.py && python3 tools/layout_check.py && python3 tools/render_html.py <dir>
```
Kotlin never hardcodes a phone size: every screen reads `LocalSafeInsets` (status bar and gesture bar, from window insets or the framework's own `status_bar_height`).

## Verification you can run here (no Android SDK needed)
`gen_tokens.py`, `dsl.py`, `layout_check.py` (geometry), `check_kotlin.py` (real Kotlin syntax parse via tree-sitter, Tokens references, drawables, imports), `find_hardcoded.py`.
Last run: layout OK for 11 screens at 392.7x875.6; 22 Kotlin files parse; 210 hardcoded literals left. The parser found and I fixed a real bug (a `*/` inside a comment).

## Facts checked against Android 16 source (tag android-16.0.0_r3)
Present: permissions INTERNAL_SYSTEM_WINDOW, STATUS_BAR, MODIFY_PHONE_STATE, WRITE_SECURE_SETTINGS, BLUETOOTH_PRIVILEGED, READ_WALLPAPER_INTERNAL, MODIFY_DAY_NIGHT_MODE,
PACKAGE_USAGE_STATS, NETWORK_AIRPLANE_MODE (signature only), ACCESS_NOTIFICATION_POLICY, QUERY_ALL_PACKAGES; `WindowManager.LayoutParams.TYPE_NOTIFICATION_SHADE`;
`StatusBarManager.DISABLE_EXPAND` and `disable(int)`; Settings actions used (cast, home, privacy, voice input, display, sound, operator, device info, accessibility, bluetooth, wifi);
Settings-app actions TETHER_SETTINGS, ALL_APPS_NOTIFICATION_SETTINGS, ZEN_MODE_SETTINGS; SystemUI Compose modules runtime_runtime, ui_ui, material3_material3, lifecycle-runtime-ktx, kotlinx_coroutines(+_android).
Corrected after checking: `config_defaultDndAccessPackages` is a colon-separated STRING (I had a string-array); `TelephonyManager.setDataEnabled` is deprecated (now `setDataEnabledForReason`).
Kyant backdrop: Maven `io.github.kyant0:backdrop`, Apache-2.0.

## Known gaps (status)
Fixed in this pass: Search pill touch target (48 dp hit area), Quick Share without Google services (falls back to Bluetooth), overlay config types, deprecated data API,
Compose module names (verified subset), hardcoded phone size (now insets + profile), Kotlin syntax errors.
Still open:
- Not type-checked or compiled (no Android SDK / Compose in the sandbox). Wrong Compose or Android API usage can still exist.
- `ConnectivityManager.setAirplaneMode` could not be verified (module source not reachable). Names `androidx.savedstate_savedstate`, `androidx.activity_activity-compose`, Capsule's Maven name also unverified.
- Density 440 and insets 32/24 are assumptions until you paste the adb output.
- The consumer of `config_defaultDndAccessPackages` was not located in the files checked; DND access may need another grant path.
- Design HTML is a browser approximation and was not screenshot-checked (no browser here); `layout_check.py` verifies geometry.
- Settings is a front end (other rows open stock Settings); launcher has no edit mode, folders or drag.
- Booting an Android 16 image on this phone is unverified: the phh guide for this phone tested an Android 14 (crDroid 10.1) GSI only, and that repository was archived in January 2025.

## Prompt to give another agent
"Read AGENT_HANDOFF.md, spec/tokens.json and spec/screens.json. Implement or fix <screen id> in Kotlin so it matches the spec tree exactly,
using only Tokens.* values. Run tools/layout_check.py and tools/find_hardcoded.py before and after, and report the numbers. Do not edit
Tokens.kt by hand. State plainly what you could not compile or run."
