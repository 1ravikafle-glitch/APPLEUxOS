# Phase 2: GMS Dependencies

## Target GMS Apps

```
Google Play Store
Google Play Services
Gmail
YouTube
Google Maps
Google Drive
Google Photos
```

## GMS Component → Android Framework Dependency Map

### Google Play Store (com.android.vending)

Dependencies:
- `PackageManagerService` (packages/apps/PackageManager)
- `DownloadManager` (packages/providers/DownloadProvider)
- `AccountManager` (services/accounts)
- `UserManager` (services/user)
- `PermissionService` (services/permission)
- `IntentResolver` — framework
- `VibratorService` (services/vibrator)
- `NotificationManager` (services/notification)

### Google Play Services (com.google.android.gms)

Dependencies:
- `AccountManager` — REQUIRED
- `PackageManager` — REQUIRED
- `ActivityManager` — REQUIRED
- `NotificationManager` — REQUIRED
- `LocationManager` (services/location) — REQUIRED
- `WifiManager` (services/wifi) — REQUIRED
- `BluetoothManager` (services/bluetooth) — REQUIRED
- `TelephonyManager` (services/telephony) — INVESTIGATE
- `SensorManager` (services/sensors) — INVESTIGATE
- `BiometricManager` (services/biometrics) — INVESTIGATE
- `DevicePolicyManager` (services/devicepolicy) — INVESTIGATE
- `ConnectivityManager` (services/connectivity) — REQUIRED
- `NfcManager` (packages/modules/Nfc) — INVESTIGATE
- `GpuService` (services/gpu) — INVESTIGATE
- `FingerprintManager` (services/biometrics) — INVESTIGATE

### Gmail (com.google.android.gm)

Dependencies:
- `AccountManager` — REQUIRED
- `NotificationManager` — REQUIRED
- `DownloadManager` — REQUIRED
- `ConnectivityManager` — REQUIRED
- `ActivityManager` — REQUIRED

### YouTube (com.google.android.youtube)

Dependencies:
- `NotificationManager` — REQUIRED
- `MediaSession` (frameworks/av) — REQUIRED
- `ConnectivityManager` — REQUIRED
- `DownloadManager` — REQUIRED
- `AudioService` (services/audio) — REQUIRED
- `AccountManager` — INVESTIGATE

### Google Maps (com.google.android.apps.maps)

Dependencies:
- `LocationManager` — REQUIRED
- `ConnectivityManager` — REQUIRED
- `NotificationManager` — REQUIRED
- `SensorManager` — INVESTIGATE
- `GpuService` — INVESTIGATE
- `NfcManager` — INVESTIGATE

### Google Drive (com.google.android.apps.docs)

Dependencies:
- `AccountManager` — REQUIRED
- `StorageManager` — REQUIRED
- `DownloadManager` — REQUIRED
- `ConnectivityManager` — REQUIRED

### Google Photos (com.google.android.apps.photos)

Dependencies:
- `MediaProvider` (providers/MediaProvider) — REQUIRED
- `DownloadManager` — REQUIRED
- `ConnectivityManager` — REQUIRED
- `AccountManager` — INVESTIGATE

## GMS-Compatible Core Services Required

Based on the dependency analysis, these services **must** be retained:

```
ActivityManager
PackageManager
WindowManager
PowerManager
AccountManager
NotificationManager
LocationManager
WifiManager
BluetoothManager
ConnectivityManager
DownloadManager
UserManager
PermissionManager
AudioManager
VibratorManager
```

These services provide the **minimum** required for GMS core functionality.

## GMS-Incompatible Services

| Service | Path | Status | Notes |
|---------|------|--------|-------|
| TextServicesManager | services/textservices | REMOVE | GMS uses its own |
| TextClassification | services/textclassifier | REMOVE | GMS uses its own |
| FingerprintService | services/fingerprint | INVESTIGATE | Only for physical devices |
| TvService | services/tv | REMOVE | TV only |
| VrService | services/vr | REMOVE | VR not in scope |
| HdmiService | services/hdmi | INVESTIGATE | TV only |
| UsbService | services/usb | REMOVE | Minimal for non-dev |
| SerialService | services/serial | REMOVE | Legacy serial |

## Prebuilts Required for GMS

| Component | Path | Must Keep |
|-----------|------|-----------|
| com.google.android.gms | external/GMS | REQUIRED (proprietary) |
| com.google.android.gms.policy_sidecar | external/GMS | REQUIRED |
| com.google.android.play | external/GMS | REQUIRED |

GMS packages themselves are **not** part of AOSP and must be integrated separately via the vendor partition.
