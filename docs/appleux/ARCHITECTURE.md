# AppleUXOS — ARCHITECTURE

> **CONFIDENTIAL — INTERNAL PROJECT DOCUMENT. Do not publish, share as a link, or commit to a public repository.**
> This file contains no licensing or agreement details. Asset provenance lives only in `ASSET_MANIFEST.md` (references only, never agreement text).

**Status: DRAFT v1 — external research complete, repository NOT yet inspected.**
Evidence tags used below:
- **[R]** confirmed from a public source listed in section 16 (as of 19 Oct 2026).
- **[U]** reported by the project owner about their own device (not independently checked here).
- **[V]** must be verified in the real source tree or on the real device before anyone relies on it.
- **[D]** a design decision proposed here, open to change.

---

## 1. Purpose
AppleUXOS is an Android-based operating system with its own iOS 27 identity. AOSP supplies the kernel interface, HALs, framework services and app compatibility. AppleUXOS supplies the design system, SystemUI, launcher, Settings, system apps and the framework-level changes that deliver iOS 27 layout, motion and behavior on the POCO M5 (and down to 2 GB devices).

## 2. Key findings from research (read these first)

1. **Android 17 is already out; the AOSP "latest release" is no longer Android 16.** [R] Google released Android 17 in June 2026, and the `android-latest-release` manifest now points at `android17-release`. Starting 2026, AOSP source is published in Q2 and Q4. Android 16 branches (for example `android16-release`, `android16-qpr2-release`) still exist. **[D] Pin the Android 16 branch explicitly in the manifest; never use `android-latest-release`.** [V] Confirm the exact newest Android 16 branch/tag available.
2. **The build host is far below Google's stated minimum.** [R] AOSP documentation lists at least 400 GB free disk (about 250 GB checkout + 150 GB build) and a minimum of 64 GB RAM; Google quotes about 40 minutes for a full build on a 72-core/64 GB machine and about 6 hours on a 6-core/64 GB machine. [U] The Dell Latitude 5420 has 16 GB RAM, 1 TB NVMe, i5-1145G7 (4 cores/8 threads). Full builds are therefore possible only with large swap and low parallelism, and will be slow. See section 12.
3. **The phone's vendor layer is old, which is workable but sets the ceiling.** [U] The POCO M5 runs stock Android 14 with `ro.vndk.version=31` and `ro.treble.enabled=true`. [R] Android 16's framework supports vendor implementations from FCM level 6 (Android 12) through level 202504 (Android 16). So an Android 12-level vendor is within the supported window in principle. [V] Read the actual vendor manifest target FCM level on the device. The vendor image (HALs, blobs) and kernel will stay at their stock generation; only the system side moves to Android 16.
4. **Community device work for `rock` already exists, but it is unofficial.** [R] A public request lists device, kernel and vendor trees for POCO M5 (`rock`) built for an Android 16 ROM, plus a separate MiCode-derived kernel source repository; XDA reports a user building a custom kernel for this phone. The same source says Android 16 builds exist but that the maintainer sees little chance of newer versions on a 4 GB device. [V] Quality, licensing and provenance of those trees are unknown; audit before use, and treat as reference rather than trusted base.
5. **iOS-style translucency is the single biggest GPU risk.** [R] Android window blur (Android 12+) is documented as GPU-intensive; devices with weaker GPUs may leave it off via `ro.surface_flinger.supports_background_blur`, and docs advise keeping blur radius at or below 150 px. The Helio G99's Mali-G57 MC2 is a modest GPU. Blur must be budgeted and measured, not assumed (section 9).
6. **The emulator cannot prove phone smoothness.** [R] Cuttlefish renders with SwiftShader (software) unless the host supports accelerated graphics (GfxStream needs OpenGL ES/Vulkan/EGL drivers on the host), and the docs say software rendering is not as performant as a real device. Frame-pacing and memory results must come from the POCO M5 itself.
7. **Google services are not part of AOSP.** [R] Android 16 GSI/GMS images are licensed by Google for compatibility testing only. [D] Treat Google Play services, Google account setup, and anything that depends on them (including Quick Share, which is a Google service [V]) as absent unless separately and legitimately licensed. Plan replacements (section 11).

## 3. Platform baseline

### 3.1 Target device — POCO M5 (`rock`)
| Item | Value | Tag |
|---|---|---|
| SoC | MediaTek Helio G99, TSMC 6 nm; some sources label the part MT8781, other community sources use MT6789 | [R] / [V] check `ro.board.platform` |
| CPU | 2x Cortex-A76 up to 2.2 GHz + 6x Cortex-A55 up to 2.0 GHz | [R] |
| GPU | Arm Mali-G57 MC2 | [R] |
| RAM / storage | LPDDR4X, UFS 2.2; sold with 4 GB RAM and 64/128 GB | [R] |
| Display | 6.58" IPS, 2408x1080, 30/60/90 Hz DynamicSwitch, 240 Hz touch sampling | [R] |
| Launch software | MIUI 13 on Android 12 (so launched with API 31, well above the GSI minimum of API 28) | [R] |
| Phone's current software | Android 14, build V816.0.8.0.ULUINXM, active slot _b | [U] |
| Treble / VNDK | treble enabled, VNDK 31 | [U] |
| Bootloader | Must be unlocked to flash; Xiaomi's unlock procedure and waiting period [V] | [V] |

### 3.2 Source baseline
- AOSP **Android 16**, branch pinned manually [D]. Build requirements per Google: Ubuntu 18.04+ or any 64-bit Linux with recent glibc, bundled prebuilt OpenJDK/Make/Python 3 in the Android 16 branch. [R]
- GSI route available as a fallback for early testing: needs an unlocked, Treble-compliant device that launched with Android 9 or later; GSIs are not CTS-approved. [R]

## 4. Layered architecture and ownership
| Layer | Contents | Owner | Candidate path [V] |
|---|---|---|---|
| Kernel / drivers | Linux kernel, MediaTek drivers | Vendor + community | device kernel tree (TBD) |
| HAL / vendor | Camera, audio, GPU, sensors, radio, power HAL | Vendor blobs | `vendor/xiaomi/rock` (TBD) |
| MediaTek support | Sepolicy and hardware glue commonly shared by MediaTek ROMs | Community | `device/mediatek/sepolicy_vndr`, `hardware/mediatek` (TBD) |
| Framework | system_server, WindowManager, shell transitions, resources | AOSP + AppleUXOS patches | `frameworks/base` |
| WM Shell | Shell-side transitions and windowing | AOSP + patches | `frameworks/base/libs/WindowManager/Shell` |
| SystemUI | Lock screen, status bar, shade, Control Center, volume | AppleUXOS | `frameworks/base/packages/SystemUI` |
| Launcher | Home, dock, app library, widgets, app switcher | AppleUXOS | `packages/apps/Launcher3` with Quickstep |
| Settings | Settings app | AppleUXOS | `packages/apps/Settings` |
| System apps | Dialer, contacts, messages, files, clock, etc. | AppleUXOS | `packages/apps/*` (audit) |
| Design system | Tokens, shared components | AppleUXOS | `vendor/appleux/design` (proposed) |
| Licensed assets | Fonts, icons, sounds, wallpapers | Project owner | `vendor/appleux/assets` (proposed, private) |

Ownership rule: the vendor half (kernel, HALs) stays as stock/community-provided; AppleUXOS owns everything from the framework upward.

## 5. Design-system architecture [D]
One token set (color, type, spacing, radii, motion, haptics) consumed by SystemUI, Launcher, Settings, framework overlays and system apps; one implementation per component. Delivered as a shared Android library plus Runtime Resource Overlays. Details in `DESIGN_SYSTEM.md`.

## 6. Framework customization strategy [D]
Order of preference: (1) resource overlays and themes, (2) SystemUI and app-level changes, (3) targeted framework patches. Every framework patch is logged in `PATCH_LEDGER.md` with file, reason and rebase risk, because Android 16 branch updates (QPR drops, security patches) must be merged over time. Keep patches small and isolated.

## 7. SystemUI architecture
- [R] Android's SystemUI includes a **Scene Framework** built on Jetpack Compose with `SceneTransitionLayout`, enabled through aconfig flags (for example `scene_container`, `compose_lockscreen`, `media_in_scene_container`, `predictive_back_sysui`), with the shade, lock screen and quick settings being scenes and transitions declared in `SceneContainerTransitions`. Docs show flags toggled with `device_config` / `aflags` and applied by restarting SystemUI.
- [V] Confirm which of these flags and sources exist and which are default-on in the pinned Android 16 branch.
- [D] Decision to make in Milestone 3, with measurements: (a) extend the existing scene-based shade/lock screen to reach iOS 27 gestures and layout, or (b) keep the classic View-based path. The scene framework offers shared-element and interruptible transitions, which match the iOS-style motion goal, but Compose adds memory and startup cost. It must pass the Tier B (2 GB) memory budget before adoption.
- Components in scope: lock screen, status bar, notification shade and banners, Control Center, volume/brightness, media controls, power menu, privacy indicators, app-switcher integration.

## 8. System application strategy [D]
Audit which apps exist in the pinned tree; classify each as REDESIGN, REPLACE, CREATE or OMIT. Native Android (Kotlin/Java), shared design-system library, public APIs preserved. See `UI_COVERAGE_MATRIX.md`.

## 9. Memory, CPU and GPU architecture

> Full RAM, CPU, GPU, frequency/voltage, battery and thermal tuning policy lives in **`PERFORMANCE_AND_POWER_TUNING.md`**; experiment results go in **`TUNING_LOG.md`**. This section keeps only the architectural summary.

### 9.1 Memory tiers [D]
| Tier | RAM | Policy |
|---|---|---|
| A | 3-4 GB (design budget 3 GB) | Full effects with adaptive quality |
| B | 2 GB (minimum) | Android Go-style low-RAM product, reduced effects, smaller caches |

- [R] AOSP's own low-RAM/Go defaults show what a Tier B product starts from: `ro.config.low_ram=true`, tuned lmkd pressure properties (for example `ro.lmk.upgrade_pressure`, `ro.lmk.downgrade_pressure`, `ro.lmk.kill_heaviest_task=false`), `ro.statsd.enable=false`, `speed-profile` compilation for system server, a profile-based boot image, an in-process network stack (`com.android.tethering.inprocess`), and Scudo disabled outside eng builds to save RAM. [R] lmkd on low-RAM devices is designed to tolerate higher memory pressure as normal, and low-RAM builds need memory cgroups. These are the starting values; each change needs measurement on the device. [V]
- Two product configs are created in Milestone 1: `appleux_rock_tierA` and `appleux_tierB_lowram` [D]. Note the phone itself has 4 GB, so Tier B is exercised by (a) limiting RAM in the emulator and (b) memory-capping tests on the phone; true 2 GB hardware is not available to this project yet [V].
- Per-process budgets (system_server, SystemUI, launcher, Settings, IME, system apps): **TBD pending measurement**.

### 9.2 CPU [D]
The Helio G99's big.LITTLE layout (2x A76, 6x A55) means UI thread, RenderThread and input must land on the A76 cores while background work stays on A55. Tuning is done through the supported paths (cpuset/uclamp configuration in init, the Power HAL boosts for touch and launch) rather than ad hoc kernel edits. [V] Which Power HAL and scheduler hooks the existing `rock` vendor tree exposes.

### 9.3 GPU and rendering
- [R] Window blur is GPU-heavy, is opt-in per device via `ro.surface_flinger.supports_background_blur`, can be toggled for testing with `wm disable-blur`, and apps must handle blur disabled states with higher-alpha fallbacks.
- [D] Blur policy: Tier B ships with blur disabled and iOS-style translucency approximated by opaque or high-alpha tinted surfaces. Tier A enables blur only for specific surfaces (for example shade, Control Center) after Perfetto shows frame times inside budget, with radius kept well under 150 px and automatic fallback if frames are missed.
- [D] Other rendering rules: flatten view hierarchies, minimize offscreen layers, cache continuous-corner paths, align animation with the 90 Hz display (11.1 ms frame budget), and measure Vulkan vs GLES renderer behavior on this Mali GPU rather than assuming.

### 9.4 Honest ceiling
iOS's smoothness comes from hardware and software co-design. On a 6 nm mid-range SoC with a Mali-G57 MC2 and an Android vendor stack frozen at an older generation, parity is a target to measure toward, not a result to promise. The documented success criterion is recorded numbers against a stock-Android baseline on the same phone.

## 10. Security boundaries (must not weaken)
SELinux enforcing; Verified Boot (note: with an unlocked bootloader the device boots in an unlocked/orange state, so hardware attestation and Play-Integrity-style checks will not pass, and some banking or DRM apps may refuse to run; document this for users) [D]; keystore and biometric protections; permission enforcement; Binder boundaries; TLS and certificate validation; WebView updates. No broad permission grants as UI workarounds. Because the vendor image is frozen at an older generation, vendor security patches will not advance with the AppleUXOS system image; document this limitation. [D]

## 11. Accounts, setup and Google boundary [D]
- Setup flow redesigned in iOS 27 style on the AOSP setup components present in the tree [V].
- No Google account or Play services by default. Document which flows are Google-owned and unavailable; define a fallback (local accounts, open-source sync such as CalDAV/CardDAV, an open app store such as F-Droid-style distribution) as options, not commitments.
- File and device sharing: an AirDrop-style feature cannot rely on Quick Share if it is Google-provided; options are a self-built Wi-Fi Direct/BLE share service or an open-source implementation, evaluated in Milestone 6. [V]

## 12. Build and host constraints (Dell Latitude 5420)
- Requirement vs reality: Google recommends 64 GB RAM and 400 GB disk [R]; the host has 16 GB RAM [U]. Plan for: large swap on NVMe (size chosen from measurement, with disk wear in mind), low `-j`, ccache, incremental module builds, and a clean full build only occasionally.
- Realistic fallback options if builds are impractical: rent a cloud build machine for full builds, develop apps/SystemUI as module builds on top of a prebuilt base, or use GSI for early UI testing on the phone. [D]
- A first full-build time estimate is deliberately not given; it will be measured in Milestone 1.
- Cuttlefish: needs KVM-capable Linux host; with the Iris Xe iGPU, GfxStream acceleration may work if the host has working GLES/Vulkan drivers; otherwise SwiftShader. [R][V] Emulator results prove behavior, not G99 performance.

## 13. Update and release strategy [D]
OTA mechanism decided after the device audit (A/B with slot _b currently active per owner [U]). Until then, install by fastboot/recovery with a verified stock-firmware restore path. Android 16 branch updates are merged on a fixed cadence with the patch ledger as the guide.

## 14. Risks
> The full, scored risk register (POCO M5 India 4 GB/64 GB, plus other devices) is **`RISK_REGISTER.md`**. The table below is a short summary only.

| Risk | Impact | Mitigation |
|---|---|---|
| Host RAM far under 64 GB | Very slow or failing builds | Swap, low -j, cloud build option |
| Frozen vendor/kernel | Limits HAL features and security patches | Document; verify FCM level early |
| Community trees of unknown quality | Boot or hardware regressions | Audit; use as reference |
| Blur and translucency cost on Mali-G57 | Janky UI | Tiering, measurement, fallbacks |
| Compose SystemUI memory on 2 GB | OOM, restarts | Flag-gated trial, memory budget |
| No Google services | Missing sync, Quick Share, Play | Defined replacements |
| Android 17 now the default branch | Branch confusion | Pin Android 16 explicitly |
| Unlocked bootloader | Integrity-check failures | Document limitation |

## 15. Open questions (for Milestone 0)
1. Is the checkout a full AOSP tree on a pinned Android 16 branch? Which exact branch/tag?
2. Which `rock` device, vendor and kernel trees are used, and what are their licenses and quality?
3. What is the vendor's target FCM level and the stock kernel version?
4. Is KVM/Cuttlefish usable on the Zorin host, and does GfxStream work?
5. What swap/disk plan is workable on the host?
6. Which scene-framework flags exist and are enabled in the pinned branch?
7. Which Power HAL and scheduler hooks are present for `rock`?

## 16. Sources (public, researched 19 Oct 2026)
- AOSP "Set up for AOSP development" (hardware requirements, android16 release branches): https://source.android.com/setup/download
- AOSP site updates (Android 17 release, `android-latest-release` now `android17-release`, source drops in Q2/Q4): https://source.android.com/docs/whatsnew/site-updates
- Android 16 GSI release notes (device requirements, not CTS-approved, GMS licensing for testing): https://developer.android.com/about/versions/16/gsi-release-notes
- Framework Compatibility Matrix (supported FCM levels 6 through 202504 for Android 16): https://source.android.com/docs/core/architecture/vintf/fcm
- SystemUI Scene Framework: https://android.googlesource.com/platform/frameworks/base/+/main/packages/SystemUI/docs/scene.md
- lmkd and low-RAM configuration: https://source.android.com/docs/core/perf/lmkd
- Android Go defaults (build repo): https://android.googlesource.com/platform/build/+/3021218d2a/target/product/go_defaults_common.mk
- Window blurs: https://source.android.com/docs/core/display/window-blurs
- Cuttlefish GPU modes: https://source.android.com/docs/devices/cuttlefish/gpu
- POCO M5 official specs: https://www.mi.com/uk/product/poco-m5/ and https://mi.com/my/product/poco-m5
- POCO M5 community ROM request (device/kernel/vendor trees): https://github.com/ProjectInfinity-X/official_devices/issues/1615
- XDA POCO M5 custom-kernel thread: https://xdaforums.com/t/overclocking-mediatek-helio-g99.4675986/

---

## ACTUAL STATE (2026-10-19) — as-built architecture

The shipped layering (all source-committed, Soong-verified for SystemUI):

```
AOSP android-16 (BP4A)
├── frameworks/base/packages/SystemUI  ← MODIFIED IN PLACE (nested git repo)
│     SystemUI-core ──static_libs──▶ appleux-systemui-chrome  ◀── also SystemUI-application
│     ShadeTouchableRegionManager.setup() → attaches AppleuxShadeHost + AppleuxShadeStatusBar
│     StatusBarWindowView               → attaches AppleuxNotchHost
│     volume_panel_dialog.xml           → iOS capsule (6 view ids preserved)
│     AndroidManifest.xml                → SHUTDOWN perm + notification listener
├── frameworks/appleux/                 ← OUR modules
│     design-system (tokens+components+sounds) ◀── everything depends on this
│     performance (Q100→Q70 controller, rock-measured frame budgets)
│     launcher (grid/dock/badges/gestures/spotlight/library)
│     settings (iOS 27 root + CustomizationActivity)
│     systemui/chrome (ShadeHost, ShadeStatusBar, NotchHost, PowerControl,
│                      HomeIndicatorView, NotificationListener+Bus)
│     apps-shared + 10 app APKs (phone/messages/contacts/photos/files/
│                      camera/clock/calendar/browser/video)
├── hardware/xiaomi-appleux/rock        ← appleux-device-support-rock
│     (MEASURED panel truth: 1080×2408@440, inset 76px, 30–90Hz, FrameBudget)
└── device/xiaomi/rock                  ← product tree (AVB test-keys)
```

Key decisions locked by evidence:
- **Delete the chrome, keep the behaviour**: AOSP controllers (Dagger, shade
  controller, keyguard mediation) stay; only drawing is replaced. Every attach
  point is try/catch-protected so chrome failure degrades to stock, never
  bricks boot.
- **No extra processes**: chrome compiles INTO SystemUI (static_lib); the old
  `com.appleux.systemui` overlay APK approach is deleted (extra process + 2
  full-screen layers for zero benefit on a 4 GB device).
- **Cross-process flags** go through a signature-guarded ContentProvider
  (`AppleuxConfigProvider`), NOT Settings.System (platform throws on app
  writes there on Android 16).
- **Power-off at system core**: `IPowerManager` binder via
  `platform_apis` (hidden-API, compile-verified in the real build);
  factory reset via public `RecoverySystem.rebootWipeUserData`.