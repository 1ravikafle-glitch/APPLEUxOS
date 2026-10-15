# Phase 7 — Build Verification and Cuttlefish Integration

**Status: BLOCKED for AOSP/Cuttlefish. The SDK emulator route does build and
run — see `runtime-verification-emulator.md` in this directory.**

Nothing in this document was verified on Cuttlefish, and nothing here is a
measurement. It records the AOSP/Soong blocker only.

This document records what was checked statically, what could not be checked,
and exactly what is required to unblock. Read the "Not verified" section before
citing any AppleUX number.

## Blocker

Cuttlefish testing requires a complete AOSP sync plus a Soong build. Neither
prerequisite is present.

| Requirement | Actual state |
|---|---|
| `android-16/build/` (Soong + Blueprint) | absent |
| `android-16/prebuilts/` (build-time toolchain, SDK) | absent |
| `android-16/out/` | absent |
| Full AOSP sync + build disk | ~200 GB required |
| Project drive free space | 31 GB of 137 GB (`/dev/nvme0n1p3`) |
| `packages/apps/Settings` source | not synced |
| Cuttlefish `cutl` binary | not built |

Only a partial selective sync exists (3.1 GB): `frameworks/base` plus four
`packages/apps` projects (`Launcher3`, `SystemUIGo`, `CarrierConfig`,
`EmergencyInfo`) and six `packages/modules` projects. That is enough to audit
some AOSP components; it is nowhere near enough to build.

The disk limit is structural, not temporary. The project drive holds 137 GB
total with 31 GB free. A full AOSP sync plus build needs roughly 200 GB, so it
cannot be made to fit by cleaning up files. Doing a full build here is not a
matter of finding more space.

Because no build ran, the following are **unverified**:

- Whether any AppleUX module compiles
- Whether the `Android.bp` files are accepted by Soong
- Whether resource references resolve at build time
- Any frame-timing, jank, memory, or thermal number
- Whether the launcher, settings, or SystemUI run at all

## What was verified statically

These checks are real but weak. They catch reference errors, not semantic or
runtime errors.

| Check | Result |
|---|---|
| All XML under `appleux/` parses | pass (5 files were broken and were fixed) |
| Manifest packages match Java package paths | pass |
| All `R.layout` / `R.id` refs resolve to declared resources | pass |
| All `com.appleux.*` imports resolve to a definition | pass |
| Brace balance across all 55 Kotlin files | pass |
| `Android.bp` present for all 5 modules | pass |
| Design-system module type supports resource export | fixed (`java_library` → `android_library`) |

Static checks do not catch type errors, missing overrides, wrong method
signatures, Android API misuse, or resource-name collisions across modules.

## XML defects found and fixed

All five were real and would have failed `aapt2` at build time.

| File | Defect |
|---|---|
| `design-system/res/values/colors.xml` | truncated mid-attribute, no closing tag |
| `design-system/res/values-night/colors.xml` | same truncation |
| `design-system/res/values/dimens.xml` | no closing `</resources>` |
| `systemui/res/values/themes.xml` | missing `<resources>` wrapper |
| `launcher/res/values/colors.xml` | stray `</item>` closing tag |

## Source defects found and fixed

| Defect | Location | Why it mattered |
|---|---|---|
| Duplicate companion methods | `AppleuxPerformanceController.kt` | `getCurrentQuality`, `getCurrentRenderScale`, `getCurrentMonitoringLevel` declared twice — redeclaration error |
| Wrong package root | `LauncherActivity.kt`, `SettingsActivity.kt` | imported `com.appleux.design.system.AppleuxTheme`; real path is `com.appleux.design.theme` |
| Non-existent API | `HomescreenGrid.kt` | called `ColorTokens.resolve(ColorTokens.Surface, isDarkMode=…)`; the real signature is `resolve(darkColor, lightColor)` and there is no `Surface` constant |
| Non-existent property | `HomescreenGrid.kt` | `isItemViewCacheSizeSet = true` is not a RecyclerView property |
| Duplicate class | `launcher/ui/` | `AppIconView` was declared in two files in the same package |
| Wrong haptic API | `GestureHandler.kt` | called `haptics.performImpact(...)` and `AppleuxHaptics(context)`; the real API is `impact(style)` and `AppleuxHaptics.getInstance(context)` |
| Non-existent interface method | `GestureHandler.kt` | declared `onSingleTapConfirmedForDoubleTap`, which is not on `OnDoubleTapListener` |
| Missing icon token | `AppIconView.kt` | referenced `IconTokens.iconSizeApp`; the real constant is `SYSTEM_ICON_SIZE_HOME_SCREEN` |
| Invalid manifest action | `settings/AndroidManifest.xml` | `<action android:name="android.settings.MAIN" />` is not a real action |
| Fake R class | `launcher/`, `settings/` | hand-written `R.kt` shims would collide with AAPT2-generated `R` |

Note on the R shims: an app must never declare its own `R`. AAPT2 generates it
at build time. The shims also used arbitrary hex IDs that would not match real
resource assignments, so every `R.id.*` lookup would silently resolve to the
wrong view. Removed in favour of standard `@+id` declarations.

## To unblock

Two separate paths. Path A does not need a full AOSP tree.

### Path A — Android SDK emulator (recommended, ~3 GB)

The Android SDK emulator runs the AppleUX apps unmodified and needs roughly
3 GB instead of 200 GB. This is the route to a visible UI.

1. Install SDK components under `android-sdk/` (project drive, 39 GB free):
   ```bash
   source tools/envsetup.sh
   sdkmanager "platform-tools" "platforms;android-36" \
              "build-tools;36.0.0" "system-images;android-36;google_apis;x86_64"
   sdkmanager "emulator"
   ```
2. The five modules currently use Soong `Android.bp` only. Add a Gradle build
   so they can be compiled without the AOSP tree. This is the real work in
   Path A — see the open question on build system.
3. Create an AVD and boot it, then install `AppleuxLauncher.apk` and
   `AppleuxSettings.apk`.

KVM is present (`/dev/kvm`, `vmx` flag exposed), so hardware acceleration is
available. RAM is the tighter constraint: 8 GB total with ~3.8 GB available.
Use `-no-snapshot -no-audio -gpu swiftshader_indirect` to stay inside it.

### Path B — full Soong build

Only viable with an external machine or cloud builder. Requires ~200 GB.

```bash
source tools/envsetup.sh
$REPO_TOOL sync -c -j2 \
    build/soong,build/blueprint,build/boards,prebuilts/build-tools,\
prebuilts/clang,prebuilts/jdk,prebuilts/sdk,frameworks/base,packages/apps/Settings
source build/envsetup.sh
m nothing
m appleux-design-system appleux-performance-lib
m AppleuxSystemUI AppleuxLauncher AppleuxSettings
```

Then build Cuttlefish and measure with `/measure-frames`.

## Canonical thresholds for whoever measures next

| Metric | Value |
|---|---|
| Frame budget | 16.67 ms |
| Good frame | ≤ 14 ms |
| Warning | > 14 ms and ≤ 16.67 ms |
| Jank | > 16.67 ms |
| Severe | > 20 ms |
| Rolling window | 30 eligible frames |
| WARNING | ≥ 2 janked / 30 |
| DIAGNOSTIC | ≥ 5 janked / 30 OR 3 consecutive severe |
| EMERGENCY | ≥ 10 janked / 30 OR 5 consecutive severe |
| Quality range | Q100 → Q95 → Q90 → Q85 → Q80 → Q75 → Q70 |
| Quality floor | Q70 (max 30% reduction) |
| Recovery stable | ≥ 5 s (10 s after thermal) |
| Recovery dwell | 2 s between steps |
| Monitoring decay | 2 s → L2, +3 s → L1, +5 s → L0 |
| Oscillation guard | boundary crossed twice in 10 s → hold 5 s |

Minimum valid frame-timing sample: 300 frames.

## Caveat that survives any amount of testing

Cuttlefish and the SDK emulator both run on the host GPU through a software or
ANGLE path. Either validates correctness — does the code compile, does the UI
respond, does the controller escalate and recover. Neither validates
physical-device GPU, thermal throttling, or eMMC I/O behaviour on the 3 GB
target class.

Any claim about real thermal behaviour or sustained eMMC throughput requires a
physical device. This project has none, so those numbers stay projected.