# Phase 2: Keep/Remove/Disable Lists

## KEEP (Essential — must not be removed)

### Core Infrastructure
| Component | Path | Rationale |
|-----------|------|-----------|
| ART | `art` | Android Runtime — required for all apps |
| Bionic | `bionic` | C library — required for all native code |
| libcore | `libcore` | Java core libraries — required |
| libnativehelper | `libnativehelper` | JNI helpers — required |
| Build system | `build/make`, `build/soong`, `build/blueprint` | Required to build |
| SELinux | `system/sepolicy`, `external/selinux` | Security — required |
| Trustonic/TEE | `trusty/`, `system/secure_element` | Security — required |
| KeyMint/KeyStore | `system/keymint`, `system/keymaster`, `system/security` | Security — required |
| VNDK | `prebuilts/vndk/*`, `frameworks/native` | Compatibility — required |
| HAL interfaces | `hardware/interfaces`, `system/hardware/interfaces` | Treble — required |
| init | `system/core` | Boot — required |

### Core Framework
| Component | Path | Rationale |
|-----------|------|-----------|
| frameworks/base | `frameworks/base` | Core Android API |
| frameworks/av | `frameworks/av` | Audio/Video |
| frameworks/native | `frameworks/native` | Native framework |
| frameworks/minikin | `frameworks/minikin` | Text layout |
| frameworks/layoutlib | `frameworks/layoutlib` | Layout rendering (build-time) |
| SystemUI shared lib | `frameworks/libs/systemui` | SystemUI components |

### Essential System Services (system_server)
| Service | Path | Rationale |
|---------|------|----------|
| ActivityManager | `services/core/java/com/android/server/am/` | App lifecycle |
| PackageManager | `services/core/java/com/android/server/pm/` | App management |
| WindowManager | `services/core/java/com/android/server/wm/` | UI display |
| PowerManager | `services/core/java/com/android/server/power/` | Power control |
| BatteryService | `services/core/java/com/android/server/BatteryService.java` | Battery state |
| NotificationManager | `services/core/java/com/android/server/notification/` | Notifications |
| AudioService | `services/core/java/com/android/server/audio/` | Audio control |
| LocationManager | `services/core/java/com/android/server/location/` | Location API |
| ConnectivityManager | `services/core/java/com/android/server/connectivity/` | Network |
| WifiManager | `services/core/java/com/android/server/wifi/` | Wi-Fi |
| BluetoothManager | services/bluetooth | Bluetooth |
| AccountManager | `services/core/java/com/android/server/accounts/` | Auth |
| Phone/Telephony | services/telephony | Telephony API |
| SensorManager | `services/core/java/com/android/server/sensors/` | Sensors |
| VibratorService | `services/core/java/com/android/server/vibrator/` | Haptics |
| DisplayManager | `services/core/java/com/android/server/display/` | Display |
| UserManager | `services/core/java/com/android/server/pm/` | User profiles |
| PermissionManager | `services/core/java/com/android/server/permission/` | Permissions |

### Essential System Apps
| App | Path | Rationale |
|-----|------|----------|
| SystemUI | `frameworks/base/packages/SystemUI` | System UI (to be replaced by AppleUX) |
| Launcher3 | `packages/apps/Launcher3` | Home screen (to be replaced by AppleUX Launcher) |
| Settings | `packages/apps/Settings` | System settings (to be replaced by AppleUX Settings) |
| KeyChain | `packages/apps/KeyChain` | Security |
| Provision | `packages/apps/Provision` | First boot setup |
| ManagedProvisioning | `packages/apps/ManagedProvisioning` | Device provisioning |

### Essential Providers
| Provider | Path | Rationale |
|----------|------|----------|
| DownloadProvider | `packages/providers/DownloadProvider` | App downloads |
| MediaProvider | `packages/providers/MediaProvider` | Media storage |
| ContactsProvider | `packages/providers/ContactsProvider` | Contacts |
| SettingsProvider | `packages/providers/SettingsProvider` | Settings sync |

### Essential Modules
| Module | Path | Rationale |
|--------|------|----------|
| NetworkStack | `packages/modules/NetworkStack` | Network stack |
| VNDK | `packages/modules/vndk` | Vendor NDK |
| SdkExtensions | `packages/modules/SdkExtensions` | API extensions |
| Statsd | `packages/modules/StatsD` | Metrics (light) |
| Bluetooth | `packages/modules/Bluetooth` | Bluetooth stack |
| Connectivity | `packages/modules/Connectivity` | Network |
| Telephony | `packages/modules/Telephony` | Phone |
| Wifi | `packages/modules/Wifi` | Wi-Fi |

### Essential Daemons
| Daemon | Path | Rationale |
|--------|------|----------|
| lmkd | `system/memory/lmkd` | Low memory killer — TUNE |
| netd | `system/netd` | Network daemon |
| vold | `system/vold` | Volume daemon |
| zygote | `system/zygote` | App process launcher |
| health | `system/health` | Battery/health monitoring |
| installer | `system/installd` | Package installation |
| keystore | `system/security/keystore` | Key storage |
| gatekeeper | `system/gatekeeper` | Authentication |
| keymint | `system/keymint` | Hardware keys |

---

## REMOVE (Unnecessary — can be deleted)

### Development/Test Components
| Component | Path | Size Estimate | Reason |
|-----------|------|---------------|--------|
| CTS | `cts` | ~2 GB | Compatibility Test Suite — not needed on device |
| Development | `development` | ~1 GB | Developer tools — build-only |
| platform_testing | `platform_testing` | ~500 MB | Test harness — not needed on device |
| Test apps | `packages/apps/Test/*` | ~200 MB | Internal test apps |
| Traceur | `packages/apps/Traceur` | ~50 MB | Debugging overlay |
| SpareParts | `packages/apps/SpareParts` | ~20 MB | Legacy dev tool |
| DeviceDiagnostics | `packages/apps/DeviceDiagnostics` | ~30 MB | Diagnostics not for prod |
| pdk | `pdk` | ~100 MB | Public SDK docs — not on device |

### Demo/Test System Apps
| Component | Path | Reason |
|-----------|------|--------|
| AvatarPicker | `packages/apps/AvatarPicker` | Demo |
| BasicSmsReceiver | `packages/apps/BasicSmsReceiver` | Demo |
| CellBroadcastReceiver | `packages/apps/CellBroadcastReceiver` | **KEPT — correction.** Earlier marked "Optional"; it carries emergency alerts. See "Deliberately EXCLUDED" |
| CertInstaller | `packages/apps/CertInstaller` | **KEPT — correction.** Earlier marked "Can be stubbed"; it is the credential-install path. See "Deliberately EXCLUDED" |
| Demo | `packages/apps/DevCamera` | Demo camera |
| Protips | `packages/apps/Protips` | Tips app — remove |
| SampleLocationAttribution | `packages/apps/SampleLocationAttribution` | Demo |

### Deprecated/Replaced Frameworks
| Component | Path | Reason |
|-----------|------|--------|
| Renderscript | `frameworks/rs` | Deprecated, replaced by Vulkan/OpenGL |
| Slang compiler | `frameworks/compile/slang` | Renderscript compiler — deprecated |
| libbcc | `frameworks/compile/libbcc` | Renderscript support — deprecated |

### Unused External Libraries
| Component | Path | Reason |
|-----------|------|--------|
| TensorFlow Lite | `external/tensorflow` | ML not needed for base OS |
| Flutter embedder | `external/angle` | Not used natively |
| Vulkan CTS | `external/vulkan-validation-layers` | Conformance tests — not needed |
| OpenCL | `external/OpenCL-*` | Not on target GPU |
| Various fonts | `external/google-fonts/*` | Use system font only |
| Chromium | `external/chromium*` | Not needed — use system WebView if needed |
| WebRTC | `external/webrtc` | Not needed on base |
| Pytorch | `external/pytorch` | ML framework — not needed |

---

## DISABLE (Retain but disable on first boot)

These components are kept in the build for compatibility but disabled:

| Component | Path | Reason to Disable |
|-----------|------|-------------------|
| Backup transport | `packages/services/Backup` | Not needed on 3GB device |
| Text services | `services/textservices` | Use GMS alternatives |
| Text classification | `services/textclassifier` | Use GMS alternatives |
| Content capture | `services/contentcapture` | Privacy/UX concern |
| Content suggestions | `services/contentsuggestions` | Not in AppleUX design |
| App prediction | `services/appprediction` | Use custom implementation |
| Search UI | `services/searchui` | Use Spotlight replacement |
| Slice manager | `services/slice` | Deprecated feature |
| Autofill | `services/autofill` | Not in initial AppleUX |
| Credential manager | `services/credentials` | Investigate GMS replacement |
| Smartspace | `services/smartspace` | Replace with AppleUX implementation |
| Bluetooth mapping | `external/ics` | Legacy — if present |
| NFC | `packages/modules/Nfc` | No hardware on target |

### DISABLE Expansion — package level (v2)

On this device storage and RAM are **the same problem**: every package below is
both APK/odex bytes on a 64 GB `/data` **and** a process that can become resident
inside the 3 GB operating budget. Disabling is preferred over deleting because it
is reversible in one command, needs no ROM rebuild, and still stops the process.

Canonical list: `scripts/debloat-disable.sh` (tiers below map 1:1 to its
`--tier` argument). Measure before and after with `scripts/storage-report.sh`
(APK bytes, cache) and `scripts/ram-report.sh` (resident RSS).

**Nothing in this section has been measured on a POCO M5.** Sizes are not quoted
here on purpose — capture them with the report scripts rather than asserting them.

#### A. Core AOSP demo / dev / sample — tier `core` (safe default)

| Package | Justification | User-visible consequence |
|---------|---------------|--------------------------|
| `com.android.protips` | Tips-onboarding demo app | None |
| `com.android.traceur` | Developer GPU-tracing overlay | Debug tracing only |
| `com.android.spare_parts` | Legacy developer menu app | None |
| `com.android.basicnetworking` | `NetworkMonitor` demo activity | None |
| `com.android.demos.wallpaper` | Sample live wallpaper | One wallpaper removed |
| `com.android.bookmarkprovider` | Bookmarks provider feeding the stock browser — no browser ships | Bookmarks surface gone |
| `com.android.apps.tag` | NFC Tag app; `packages/modules/Nfc` is already listed as absent on target | NFC tag dialogs gone |
| `com.google.android.apps.tag` | Same, Play build | Same |
| `com.example.android.apis` | `ApiDemos` test harness | None |
| `com.android.wallpaper.livepicker` | Wallpaper picker helper; AppleUX owns its own surface | Wallpaper picker gone |

#### B. Google / Play stack — tier `google`

**Verification that gates this whole category:** `grep -rE 'com\.google\.' appleux/`
returns **zero hits** across every AppleUX module. The launcher, SystemUI,
Settings, performance controller and design-system reference only `android.*`
and `com.appleux.*`. No Google package is load-bearing for AppleUX.

| Package | Justification | User-visible consequence |
|---------|---------------|--------------------------|
| `com.google.android.googlequicksearchbox` | Search UI + on-device indexing; AppleUX owns search | No Google search bar / suggestions |
| `com.google.android.apps.nexuslauncher` | Pixel launcher, absent on POCO anyway | None |
| `com.google.android.apps.maps` | Maps; unusable as a navigation app without Play Services | No Maps |
| `com.google.android.apps.photos` | Google Photos; stock gallery is in tier `media` | No Google Photos |
| `com.google.android.apps.docs` | Drive client | No Drive |
| `com.google.android.youtube` | YouTube app | No YouTube |
| `com.android.chrome`, `com.google.android.apps.chrome`, `..._beta`, `..._dev` | All four Chrome variants | No Chrome (in-app browsers still use WebView) |
| `com.google.android.apps.podcasts` | Podcasts | No Podcasts |
| `com.google.android.apps.books` | Play Books | No Books |
| `com.google.android.apps.news` | Google News | No News |
| `com.google.android.apps.fitness` | Fit; duplicates the AppleUX activity/health surface | No Fit |
| `com.google.android.apps.finance` | Finance | No Finance |
| `com.google.android.familylink` | Parental controls | No Family Link |
| `com.google.android.apps.streetview` | Street View | No Street View |
| `com.google.android.apps.walletnfcrel` | Google Wallet NFC; target has no NFC | No Wallet |
| `com.google.android.apps.messaging` | **Google Messages is the SMS app** — disable only if the stock SMS app is retained | Losing this leaves no SMS UI; see excluded list for the safer route |
| `com.google.android.apps.wallpaper`, `com.google.android.wallpaper` | Pixel live wallpapers | Pixel wallpapers gone |
| `com.google.android.apps.vr`, `com.google.android.apps.daydream` | Daydream VR | No VR |
| `com.google.android.ar.core` | ARCore | No AR |
| `com.google.android.adservices` | Advertising ID / ad services | Privacy gain; apps relying on ad-ID lose it |
| `com.google.android.ext.services` | Play-services extension: background analytics upload | No Firebase/Play upload |
| `com.google.android.feedback` | Google feedback harvester | No "send feedback" |
| `com.google.android.partnersetup` | Preinstalled partner/carrier configuration installer | Partner/carrier presets not applied |

#### C. Stock AOSP media & utilities — tier `media`

| Package | Justification | User-visible consequence |
|---------|---------------|--------------------------|
| `com.android.gallery3d` | Stock gallery; AppleUX will own media | No stock gallery |
| `com.android.music` | Stock music player | No stock Music |
| `com.android.deskclock` | Stock clock — **AppleUX has a clock *widget*, not a clock app** | No alarm/timer app; widget remains |
| `com.android.calculator2` | Stock calculator | No stock calculator |
| `com.android.calendar` | Stock calendar; **AppleUX has a calendar *widget*, not a calendar app** | No calendar app; widget remains |

The last three are honest gaps in AppleUX's replace-then-delete ladder. They are
in a named tier rather than `core` precisely because a phone without a working
clock is a support call, not a debloat win.

#### D. Secondary input methods — tier `ime`

Only the optional latin-IME **language packs**. AppleUX declares no IME of its own
and the launcher must never leave the device keyboardless.

| Packages (20) | Justification | User-visible consequence |
|---------------|---------------|--------------------------|
| `com.google.android.inputmethod.latin.{arabic,bengali,devanagari,farsei,gujarati,hebrew,hangul,japanese,kannada,korean,malayalam,marathi,nepali,nordic,persian,ru,sinhala,slavo,tamil,telugu,thai}` | Language packs shipped by default; a POCO M5 in one region needs one or two | Non-installed languages vanish from the keyboard language list |

#### E. Telemetry, cloud sync & ads

Covered above by `ext.services` (analytics upload), `adservices` (ad ID), and
`feedback` (harvesting). Deliberately **not** covered, because they are load-bearing:

- **Account sync / backup transport** — `com.google.android.gsf` is the transport
  that moves Google account state. Disabling it breaks account restore and
  cloud backup. Charter: account/auth providers and backup transport are untouchable.
- **Cloud *content* sync that rides on GSF** (Drive, Photos) is already removed,
  because those are user-facing apps, not the sync substrate.

#### F. Live wallpapers, VR/AR, print spooler — tier `optical`

Real user-visible loss, so opt-in and never in `core`.

| Package | Justification | User-visible consequence |
|---------|---------------|--------------------------|
| `com.android.printspooler` | Print framework; m2 previously listed it INVESTIGATE / "Optional" | **No printing at all** until a print service is installed |
| `com.android.dreams.basic` | Basic screen-saver / dream service | No screen saver; some OEM always-on displays ride on this |
| `com.android.bluetoothmap` | Legacy Bluetooth MAP profile | No BT contacts transfer |
| `com.android.wallpaper.holospiral` | AOSP 3D/parallax live wallpaper | One wallpaper removed |
| `com.android.wallpaper.category.{cell,cloud,mountain,organic,sea}` | AOSP wallpaper category bundles | Those wallpaper categories gone |

#### G. Preinstalled carrier & partner content

Only `com.google.android.partnersetup` (tier B) is removable at package level.
Vendor carrier bloat lives in `vendor/` and `product/`, not in `pm list packages`,
so it is a **build-time** removal, not an adb disable — out of scope for this
script and tracked in the REMOVE section instead.

#### H. Secondary users / profiles

Multi-user is a `UserManager` feature, not a package. Tearing down a secondary
user is `pm remove-user` — that **destroys that user's data irreversibly**.
Deliberately not scripted. If secondary users are ever a target, it needs a
separate, explicitly-flagged destructive script.

### Deliberately EXCLUDED from every tier

| Package / Surface | Why excluded |
|-------------------|--------------|
| `com.android.cellbroadcastreceiver` | Emergency alerts. m2 listed it "Optional"; **that was wrong** — disabling it is a safety regression |
| `com.android.certinstaller` | Credential/certificate install. m2 said "can be stubbed"; **rejected**, it is the security path |
| `com.android.emergency` | Emergency info surface |
| `com.android.location.fused`, `com.android.location.provider` | Location providers — charter |
| `com.android.keychain` | Credential storage — charter |
| `com.google.android.gms` | Accounts, auth, FCM, backup transport, SafetyNet/Play Integrity. Removing it looks like the biggest single win and is the single biggest way to brick the device |
| `com.google.android.gsf` | Account sync + backup transport |
| `com.android.packageinstaller` | Package installer — charter |
| `com.android.permissioncontroller` | Permission controller — charter |
| `com.android.providers.media.module` | MediaProvider — charter |
| `com.android.providers.downloads` | DownloadProvider |
| `com.android.externalstorage` | Storage provider (SD-card / USB) — charter |
| `com.android.webview`, `com.google.android.webview` | WebView; breaking it breaks every app with a web view, and it is the one component that cannot be repaired in the field |
| `com.android.settings` | AppleUX Settings answers `android.settings.*` intents but **stock Settings stays as the documented intent fallback** until ACTION_SETTINGS coverage is audited (AGENTS.md). Disabling it removes that fallback |
| `com.android.systemui`, Launcher3 host | Replacement-not-yet-verified ladder (AGENTS.md: verify ours, *then* delete stock) |
| `com.android.dialer`, `com.android.contacts`, `com.android.messaging` | AppleUX replacements are **pending**. Excluded from tier `all`; disableable only by naming `--tier replaced` |
| `com.google.android.inputmethod.latin`, `.unlisted`, `com.android.inputmethod.latin.AOSP-latin` | These are the keyboard. Disabling leaves no IME |
| `com.android.shell` | adb itself |
| `com.android.carrierconfig` | Carrier configuration for telephony |
| `packages/modules/DeviceLock`, `Profiling`, `OnDevicePersonalization`, `RemoteKeyProvisioning`, `NFC` | Already tracked in m2's DISABLE/INVESTIGATE tables at **module** level; mixing build-time module removal into an adb package list would double-manage them |

### Scripts

| Script | Purpose | Mutates device? |
|--------|---------|-----------------|
| `scripts/debloat-disable.sh` | Applies this list. `disable` / `enable` / `status`, tiered, idempotent | Only on `disable`/`enable`; both fully reversible |
| `scripts/storage-report.sh` | `/data` usage, per-app data+cache, APK sizes, top consumers | No — read-only |
| `scripts/ram-report.sh` | `meminfo` total vs available, per-AppleUX-package PSS, process count, LMKD activity | No — read-only |

---

## INVESTIGATE (Requires analysis before decision)

| Component | Path | Questions |
|-----------|------|----------|
| Renderscript | `frameworks/rs` | Any apps still using it? |
| Car services | `packages/services/Car`, `frameworks/opt/car/*` | Automotive only — remove? |
| TV services | `packages/services/Car/TV/*`, `packages/apps/TV/*` | TV-only — remove? |
| Accessibility | `services/accessibility` | Required for accessibility, but can be minimized |
| Content capture | `services/contentcapture` | GMS dependency? App dependency? |
| Device policy | `services/devicepolicy` | Enterprise — needed for GMS? |
| Voice interaction | `services/voiceinteraction` | GMS/Google Assistant dependency? |
| Print service | `packages/services/BuiltInPrintService` | Optional |
| Cross-device | `packages/services/CrossDeviceSync` | New feature — investigate |
| Device lock | `packages/modules/DeviceLock` | Security — investigate |
| Profiling | `packages/modules/Profiling` | Debug — remove in prod? |
| Remote key provisioning | `packages/modules/RemoteKeyProvisioning` | Security — GMS dependency? |
| On-device personalization | `packages/modules/OnDevicePersonalization` | Privacy concern — investigate |
