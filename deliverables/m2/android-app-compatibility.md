# Phase 2: Android App Compatibility

## Compatibility Goals

AppleUX must remain a real Android operating system that can:
1. Install apps from Google Play Store
2. Launch and run ordinary Android applications
3. Support permissions model
4. Handle notifications
5. Support background/foreground lifecycle
6. Handle networking
7. Handle storage access
8. Handle media playback
9. Handle location
10. Support app switching
11. Support updates
12. Support process recreation

## Android API Compatibility

### Required APIs

| API | Path | Must Keep | Notes |
|-----|------|-----------|-------|
| ActivityManager | frameworks/base/core/java/android/app/ | KEEP | Core lifecycle |
| PackageManager | frameworks/base/core/java/android/content/pm/ | KEEP | App installation |
| Intent | frameworks/base/core/java/android/content/ | KEEP | IPC mechanism |
| Context | frameworks/base/core/java/android/content/Context.java | KEEP | Base API |
| View | frameworks/base/core/java/android/view/ | KEEP | UI framework |
| WindowManager | frameworks/base/core/java/android/view/window/ | KEEP | Windowing |
| NotificationManager | frameworks/base/core/java/android/app/NotificationManager.java | KEEP | Alerts |
| LocationManager | frameworks/base/core/java/android/location/ | KEEP (stub) | Location API |
| SensorManager | frameworks/base/core/java/android/hardware/ | KEEP (stub) | Sensor API |
| Vibrator | frameworks/base/core/java/android/os/ | KEEP (stub) | Haptics |
| AudioManager | frameworks/base/core/java/android/media/ | KEEP | Audio control |
| ConnectivityManager | frameworks/base/core/java/android/net/ | KEEP | Network state |
| CameraManager | frameworks/base/core/java/android/hardware/camera2/ | KEEP | Camera API |
| WifiManager | frameworks/base/core/java/android/net/wifi/ | KEEP | Wi-Fi API |
| BluetoothManager | frameworks/base/core/java/android/bluetooth/ | KEEP | Bluetooth API |
| UsbManager | frameworks/base/core/java/android/usb/ | KEEP (stub) | USB API |
| AccountManager | frameworks/base/core/java/android/accounts/ | KEEP | Auth |

### Optional / Removable APIs

| API | Path | Decision | Reason |
|-----|------|----------|--------|
| TvManager | frameworks/base/core/java/android/tv/ | REMOVE | No TV support |
| CarManager | frameworks/base/core/java/android/car/ | INVESTIGATE | Automotive only |
| BluetoothLeScanner | frameworks/base/core/java/android/bluetooth/le/ | KEEP | BLE needed |
| NfcManager | frameworks/base/core/java/android/nfc/ | REMOVE for 3GB | No NFC on target |
| FingerprintManager | frameworks/base/core/java/android/hardware/fingerprint/ | KEEP (stub) | Apps expect API |
| HealthManager | packages/modules/HealthFitness | INVESTIGATE | Fitness, optional |
| AppPredictor | frameworks/base/core/java/android/app/prediction/ | KEEP | General purpose |

## Application Compatibility Testing Plan

Applications to test on AppleUX:

### Tier 1 (Must Pass)

| App | Category | Dependencies |
|-----|----------|--------------|
| Google Play Store | Store | Account, PackageManager, DownloadManager |
| Gmail | Email | Account, Notification, Download |
| YouTube | Media | MediaSession, Audio, Connectivity |
| Google Maps | Navigation | Location, GL, Sensors |
| Google Drive | Cloud | Account, Storage, Download |
| Google Photos | Media | MediaProvider, Download, Storage |
| Chrome | Browser | WebView, Network |
| WhatsApp | Messaging | Camera, Audio, Notification |
| Instagram | Social | Camera, Media, Network |
| TikTok | Social | Camera, Media, Network |

### Tier 2 (Should Pass)

| App | Category | Dependencies |
|-----|----------|--------------|
| Spotify | Music | MediaSession, Audio |
| Telegram | Messaging | Camera, Notification |
| Discord | Communication | MediaSession, Notification |
| Microsoft Teams | Work | Account, Notification, Camera |

### Tier 3 (Nice to Have)

| App | Category | Dependencies |
|-----|----------|--------------|
| Banking apps | Finance | Camera, Biometric, Notification |
| Ride-sharing | Transport | Location, Maps |
| Games (Unity/Unreal) | Gaming | OpenGL, Audio, Input |
| Netflix | Streaming | Media, DRM |
| Kindle | Reading | Storage, Network |

## API Surface Reduction Analysis

### Safe to Stub/Stub-only

These APIs can be stubbed (return empty/default) without breaking app compatibility:

- NfcManager (returns no NFC hardware)
- BluetoothLeScanner (returns empty scan results)
- FingerprintManager (returns not enrolled)
- BiometricPrompt (returns error)
- TvManager (returns unavailable)
- CarManager (returns unavailable)
- UsbManager (supports basic file transfer)
- PrinterManager (stub)

### Cannot be Stubbed

These must have full implementations:

- ActivityManager
- PackageManager
- Context (all content)
- View/WindowManager
- NotificationManager
- AudioManager
- ConnectivityManager
- LocationManager (stub is acceptable if apps handle gracefully)
- SensorManager (stub may cause app crashes)
- CameraManager (stub acceptable for apps that check availability)
- AccountManager

## Risk Assessment

| Risk | Impact | Mitigation |
|------|--------|------------|
| App crashes due to missing APIs | High | Keep API surface intact, stub implementations acceptable |
| GMS core failure | Critical | Maintain all documented GMS dependencies |
| Performance degradation from bloat | High | Remove unused services/daemons |
| Security regression | Critical | Preserve SELinux, verification, keymint |
| App data loss | Critical | Preserve all storage/media providers |
