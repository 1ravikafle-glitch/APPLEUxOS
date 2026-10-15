# Phase 2: Android 16 Component Inventory (Complete)

## Project Groups Classification

### Core System (MUST KEEP)

| Group | Projects | Count |
|-------|----------|-------|
| Build system | build/* | 5 |
| Core runtime | art, bionic, libcore, libnativehelper, dalvik | 5 |
| System core | system/core, system/vold, system/netd, system/zygote, system/security, system/sepolicy, system/installd, system/lmkd, system/hwservicemanager, system/libhidl, system/libhwbinder, system/libvintf, system/init, system/logging, system/timezone, etc. | ~50 |
| Framework | frameworks/base, frameworks/av, frameworks/native, frameworks/minikin, frameworks/wilhelm | 5 |
| HAL interfaces | hardware/interfaces, hardware/libhardware | 2 |

### System Services (in system_server)

103 service directories identified under `frameworks/base/services/core/java/com/android/server/`

| Service Category | Count | Action |
|-----------------|-------|--------|
| Core lifecycle (am, pm, wm, power) | 4 | KEEP |
| Connectivity (wifi, bluetooth, net, connectivity) | 4 | KEEP |
| Media (audio, media) | 2 | KEEP |
| Security (biometrics, gatekeeper, keymint, security) | 5 | KEEP (some stub) |
| Location & Sensors (location, sensors, gps) | 3 | KEEP (stub if no HW) |
| Input (input, pm) | 2 | KEEP |
| Accounts & Users (accounts, user) | 2 | KEEP |
| Notifications & UI (notification, display, statusbar) | 3 | KEEP (AppleUX replace some) |
| Storage (storage, drm) | 2 | KEEP |
| Other services | ~70 | Audit individually |

### System Apps

| App | Path | Size Est. | Classification |
|-----|------|-----------|----------------|
| SystemUI | frameworks/base/packages/SystemUI | ~80 MB | REPLACE (AppleUX) |
| SystemUIGo | packages/apps/SystemUIGo | 70 KB | INVESTIGATE (lightweight base) |
| Launcher3 | packages/apps/Launcher3 | ~36 MB | REPLACE (AppleUX) |
| Settings | packages/apps/Settings | ~50 MB | REPLACE (AppleUX) |
| QuickSearchBox | packages/apps/QuickSearchBox | ~5 MB | REPLACE (AppleUX Search) |
| ThemePicker | packages/apps/ThemePicker | ~5 MB | REMOVE |
| Traceur | packages/apps/Traceur | ~1 MB | REMOVE |
| Provision | packages/apps/Provision | ~2 MB | KEEP |
| KeyChain | packages/apps/KeyChain | ~3 MB | KEEP (security) |
| DocumentsUI | packages/apps/DocumentsUI | ~8 MB | INVESTIGATE |
| EmergencyInfo | packages/apps/EmergencyInfo | ~2 MB | INVESTIGATE |
| ManagedProvisioning | packages/apps/ManagedProvisioning | ~5 MB | KEEP (provisioning) |
| CarrierConfig | packages/apps/CarrierConfig | ~1 MB | REMOVE (or minimal) |
| Music | packages/apps/Music | ~3 MB | REMOVE |
| MusicFX | packages/apps/MusicFX | ~1 MB | INVESTIGATE |
| Calendar | packages/apps/Calendar | ~5 MB | REMOVE |
| Camera2 | packages/apps/Camera2 | ~5 MB | REMOVE (demo) |
| Dialer | packages/apps/Dialer | ~8 MB | REMOVE |
| Messaging | packages/apps/Messaging | ~10 MB | INVESTIGATE |
| Contacts | packages/apps/Contacts | ~10 MB | INVESTIGATE |
| Gallery2 | packages/apps/Gallery2 | ~10 MB | REMOVE |
| Browser2 | packages/apps/Browser2 | ~3 MB | REMOVE |
| BasicSmsReceiver | packages/apps/BasicSmsReceiver | ~1 MB | REMOVE |
| AvatarPicker | packages/apps/AvatarPicker | ~1 MB | REMOVE |
| CellBroadcastReceiver | packages/apps/CellBroadcastReceiver | ~2 MB | INVESTIGATE |
| CertInstaller | packages/apps/CertInstaller | ~1 MB | INVESTIGATE |
| DeskClock | packages/apps/DeskClock | ~5 MB | REMOVE |
| EyeDropper | packages/apps/EyeDropper | ~1 MB | REMOVE |
| HTMLViewer | packages/apps/HTMLViewer | ~1 MB | INVESTIGATE |
| PrivateSpace | packages/apps/PrivateSpace | ~2 MB | INVESTIGATE |
| QuickAccessWallet | packages/apps/QuickAccessWallet | ~3 MB | REMOVE |
| SafetyRegulatoryInfo | packages/apps/SafetyRegulatoryInfo | ~1 MB | REMOVE |
| SpareParts | packages/apps/SpareParts | ~1 MB | REMOVE |
| Stk | packages/apps/Stk | ~3 MB | INVESTIGATE |
| StorageManager | packages/apps/StorageManager | ~2 MB | INVESTIGATE |
| Tag | packages/apps/Tag | ~1 MB | REMOVE (no NFC) |
| SettingsIntelligence | packages/apps/SettingsIntelligence | ~3 MB | REMOVE (AppleUX replaces) |
| DeviceDiagnostics | packages/apps/DeviceDiagnostics | ~2 MB | REMOVE |

### System Services (packages/services/)

| Service | Size Est. | Classification |
|---------|-----------|----------------|
| packages/services/Car | ~50 MB | REMOVE (automotive) |
| packages/services/Mms | ~5 MB | INVESTIGATE |
| packages/services/Mtp | ~3 MB | INVESTIGATE |
| packages/services/QualifiedNetworksService | ~1 MB | REMOVE |
| packages/services/Telecomm | ~5 MB | KEEP (telecom API) |
| packages/services/Telephony | ~10 MB | KEEP (telephony) |
| packages/services/BuiltInPrintService | ~2 MB | REMOVE |
| packages/services/AlternativeNetworkAccess | ~1 MB | INVESTIGATE |
| packages/services/CrossDeviceSync | ~1 MB | INVESTIGATE |
| packages/services/DeviceAsWebcam | ~1 MB | INVESTIGATE |
| packages/services/Iwlan | ~1 MB | INVESTIGATE |

### Content Providers

| Provider | Size Est. | Classification |
|----------|-----------|----------------|
| BlockedNumberProvider | ~1 MB | KEEP (telephony) |
| BookmarkProvider | ~1 MB | REMOVE |
| CalendarProvider | ~5 MB | INVESTIGATE |
| CallLogProvider | ~1 MB | KEEP (telephony) |
| ContactsKeysProvider | ~5 MB | KEEP (contacts) |
| ContactsProvider | ~10 MB | INVESTIGATE |
| DownloadProvider | ~5 MB | KEEP (GMS) |
| MediaProvider | ~15 MB | KEEP |
| PartnerBookmarksProvider | ~1 MB | REMOVE |
| TelephonyProvider | ~3 MB | KEEP (telephony) |
| TvProvider | ~5 MB | REMOVE |
| UserDictionaryProvider | ~1 MB | INVESTIGATE |

### Input Methods

| IME | Size Est. | Classification |
|-----|-----------|----------------|
| LatinIME | ~15 MB | KEEP (default keyboard) |
| LeanbackIME | ~5 MB | REMOVE (TV only) |

### Modules (packages/modules/)

| Module | Size Est. | Classification |
|--------|-----------|----------------|
| AdServices | ~3 MB | INVESTIGATE |
| AppSearch | ~3 MB | INVESTIGATE |
| ArtPrebuilt | ~20 MB | KEEP (ART) |
| Bluetooth | ~67 MB | KEEP (tuned) |
| CaptivePortalLogin | ~1 MB | KEEP (network) |
| CellBroadcastService | ~2 MB | INVESTIGATE |
| common | ~1 MB | KEEP |
| ConfigInfrastructure | ~2 MB | INVESTIGATE |
| Connectivity | ~43 MB | KEEP |
| CrashRecovery | ~1 MB | INVESTIGATE |
| DeviceLock | ~2 MB | INVESTIGATE |
| DnsResolver | ~1 MB | KEEP (network) |
| ExtServices | ~5 MB | INVESTIGATE |
| GeoTZ | ~2 MB | KEEP (timezone) |
| Gki | ~5 MB | INVESTIGATE |
| HealthFitness | ~3 MB | REMOVE |
| ImsMedia | ~1 MB | INVESTIGATE |
| IntentResolver | ~1 MB | KEEP (compat) |
| IPsec | ~1 MB | KEEP (network) |
| Media | ~30 MB | KEEP |
| ModuleMetadata | ~1 MB | KEEP (module) |
| NetworkStack | ~20 MB | KEEP (network) |
| NeuralNetworks | ~5 MB | INVESTIGATE |
| Nfc | ~8 MB | REMOVE (no HW) |
| OnDevicePersonalization | ~2 MB | REMOVE (privacy) |
| Permission | ~2 MB | KEEP |
| Profiling | ~1 MB | REMOVE (debug) |
| RemoteKeyProvisioning | ~2 MB | KEEP (security) |
| RuntimeI18n | ~1 MB | KEEP |
| Scheduling | ~3 MB | KEEP |
| SdkExtensions | ~2 MB | KEEP |
| StatsD | ~5 MB | INVESTIGATE |
| Telecom | ~10 MB | KEEP |
| Telephony | ~20 MB | KEEP |
| ThreadNetwork | ~1 MB | REMOVE |
| UprobeStats | ~1 MB | REMOVE |
| Uwb | ~2 MB | REMOVE (no HW) |
| Virtualization | ~2 MB | INVESTIGATE |
| vndk | ~500 MB | KEEP (VNDK) |
| Wifi | ~38 MB | KEEP |

### Wallpapers & Screensavers

| Component | Size Est. | Classification |
|-----------|-----------|----------------|
| LivePicker | ~1 MB | REMOVE |
| Basic (screensaver) | ~1 MB | REMOVE |
| PhotoTable | ~1 MB | REMOVE |

### External Libraries

| Library | Size Est. | Classification |
|---------|-----------|----------------|
| BoringSSL | ~50 MB | KEEP (crypto) |
| ICU | ~50 MB | KEEP (i18n) |
| SQLite | ~5 MB | KEEP (storage) |
| libxml2 | ~10 MB | INVESTIGATE |
| libyuv | ~5 MB | INVESTIGATE |
| Skia | ~50 MB | KEEP (graphics) |
| Vulkan | ~20 MB | INVESTIGATE |
| OpenGL | ~10 MB | INVESTIGATE |
| Wayland | ~5 MB | INVESTIGATE |
| TensorFlow Lite | ~200 MB | REMOVE |
| PyTorch | ~100 MB | REMOVE |
| Chromium | ~300 MB | REMOVE |
| WebRTC | ~50 MB | REMOVE |
| TensorFlow | ~200 MB | REMOVE |

### Prebuilts

| Prebuilt | Size Est. | Classification |
|----------|-----------|----------------|
| clang | ~500 MB | KEEP (build only) |
| ndk | ~500 MB | INVESTIGATE (dev only) |
| sdk | ~1 GB | INVESTIGATE (build only) |
| build-tools | ~200 MB | KEEP (build only) |
| jdk | ~500 MB | KEEP (build only) |
| vndk | ~500 MB | KEEP |
| go | ~100 MB | INVESTIGATE (build only) |
| rust | ~300 MB | INVESTIGATE |

Note: Prebuilts are build-time tools, not included in device storage.
