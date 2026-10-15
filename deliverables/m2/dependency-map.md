# Phase 2: Dependency Map

## System Architecture Dependency Graph

```
                    ┌──────────────────┐
                    │   Hardware HAL   │
                    └────────┬─────────┘
                             │
                             ▼
                    ┌──────────────────┐
                    │   Linux Kernel   │
                    └────────┬─────────┘
                             │
                             ▼
                    ┌──────────────────┐
                    │       init       │
                    │ (system/core)    │
                    └────────┬─────────┘
                             │
              ┌──────────────┼──────────────┐
              │              │              │
              ▼              ▼              ▼
        ┌─────────┐  ┌────────────┐  ┌──────────┐
        │ zygote  │  │   system_  │  │ vold/netd│
        │ (app    │  │  server    │  │ (system/ │
        │ loader) │  │ (Java svc) │  │  core)   │
        └─────────┘  └─────┬──────┘  └──────────┘
                           │
          ┌────────────────┼────────────────┐
          │                │                │
          ▼                ▼                ▼
   ┌────────────┐ ┌────────────┐ ┌─────────────┐
   │frameworks/ │ │System apps │ │  Providers  │
   │   base      │ │(SystemUI,  │ │(Media,     │
   │    (APIs,   │ │ Launcher3, │ │ Download,  │
   │ services)   │ │ Settings)  │ │ Contact,   │
   │             │ │            │ │ Storage)   │
   └─────┬──────┘ └─────┬──────┘ └─────┬───────┘
         │              │              │
         │              │              │
         └──────────────┴──────────────┘
                         │
                         ▼
                ┌─────────────────┐
                │ Android Apps    │
                │ (3rd party +    │
                │  GMS)           │
                └─────────────────┘
```

## Component Dependency Chains

### SystemUI Dependency Chain

```
SystemUI (packages/SystemUI)
    ↓ frameworks/base/packages/SystemUI
        ↓ frameworks/base (framework APIs)
            ↓ frameworks/base/services (system services)
                ↓ Binder IPC
                    ↓ system_server
                        ↓ native daemons
    ↓ frameworks/libs/systemui (shared library)
    ↓ frameworks/opt/systemui
    ↓ com.android.internal.R (resources)
    ↓ com.android.SystemUI (resources)
    ↘ packages/modules/WiFi
    ↘ packages/modules/Bluetooth
    ↘ packages/modules/Connectivity
```

### Launcher Dependency Chain

```
Launcher3 (packages/apps/Launcher3)
    ↓ frameworks/base (framework APIs)
        ↓ frameworks/base/services
            ↓ system_server
    ↓ frameworks/opt (widgets, etc.)
    ↓ com.android.launcher3 (resources)
    ↘ packages/services/AppPrediction (INVESTIGATE)
    ↘ packages/services/Smartspace (INVESTIGATE)
```

### Settings Dependency Chain

```
Settings (packages/apps/Settings)
    ↓ frameworks/base (framework APIs)
        ↓ frameworks/base/services
            ↓ system_server
    ↓ packages/apps/SettingsIntelligence
    ↓ com.android.settings (resources)
    ↓ androidx.* (Jetpack libraries)
    ↘ packages/modules/Wifi
    ↘ packages/modules/Bluetooth
    ↘ packages/modules/Telephony
    ↘ packages/modules/DeviceLock
```

### GMS Dependency Chain

```
Google Play Services (com.google.android.gms)
    ↓ AccountManager (system_server)
    ↓ PackageManager (system_server)
    ↓ NotificationManager (system_server)
    ↓ LocationManager (system_server)
    ↓ WifiManager (system_server)
    ↓ BluetoothManager (system_server)
    ↓ ConnectivityManager (system_server)
    ↓ ActivityManager (system_server)
    ↓ DownloadManager (system_server)
    ↓ AccountManager (system_server)
    ↓ UserManagerService (system_server)
    ↓ PermissionManagerService (system_server)
```

## Binder Service Dependencies

These system services communicate via Binder IPC and must remain:

```
system_server (main Java process)
    ├── ActivityManager
    ├── PackageManager
    ├── WindowManager
    ├── PowerManager
    ├── NotificationManager
    ├── AudioService
    ├── LocationManager
    ├── ConnectivityManager
    ├── WifiManager
    ├── BluetoothManager
    ├── AccountManager
    ├── TelephonyRegistry
    ├── SensorManager
    ├── VibratorService
    ├── DisplayManager
    ├── UserManager
    ├── PermissionManager
    ├── DevicePolicyManager
    └── (others)
```

## Shared Library Dependencies

```
frameworks/base (all apps depend on this)
    ├── com.android.internal.R
    ├── android.* (framework APIs)
    └── com.android.* (internal APIs)

frameworks/av
    ├── android.media.*
    ├── android.media.audiofx.*
    └── android.drm.*

libsqlite
    ├── SQLiteDatabase
    └── all apps using SQLite

libandroid_runtime
    ├── JNI glue
    └── all native apps
```

## Module (Mainline) Dependencies

Android 16 uses Mainline modules that can be updated via Play Store:

```
packages/modules/Wifi → Connectivity, NetworkStack
packages/modules/Bluetooth → Connectivity
packages/modules/Telephony → Telephony framework
packages/modules/Connectivity → NetworkStack
packages/modules/Nfc → Nfc framework
packages/modules/DeviceLock → DevicePolicy
packages/modules/AppSearch → Search
packages/modules/Contentcapture → ContentCapture
packages/modules/CaptivePortalLogin → Connectivity
packages/modules/CrashRecovery → System health
packages/modules/DnsResolver → Connectivity
packages/modules/ExtServices → Extensions
packages/modules/GeoTZ → System time
packages/modules/HealthFitness → Health
packages/modules/ImsMedia → Telephony
packages/modules/IntentResolver → App compatibility
packages/modules/IPsec → Connectivity
packages/modules/Media → Media framework
packages/modules/ModuleMetadata → Package info
packages/modules/OnDevicePersonalization → Personalization
packages/modules/Permission → App permissions
packages/modules/Profiling → Development
packages/modules/RemoteKeyProvisioning → Security
packages/modules/Scheduling → JobScheduler
packages/modules/SdkExtensions → API updates
packages/modules/StatsD → Metrics
packages/modules/SystemUI → SystemUI (shared lib)
packages/modules/Uwb → Ultra-wideband
packages/modules/UprobeStats → Debug
packages/modules/Virtualization → Virtualization
```

## AppleUX Replacement Points

Components that AppleUX will **replace**:

```
SystemUI → frameworks/base/packages/SystemUI → REPLACE with frameworks/base/packages/SystemUI (modified)
Launcher3 → packages/apps/Launcher3 → REPLACE with packages/apps/AppleUXLauncher
Settings → packages/apps/Settings → REPLACE with packages/apps/AppleUXSettings
QuickSearchBox → packages/apps/QuickSearchBox → REPLACE with packages/apps/AppleUXSearch
VolumeDialog → frameworks/base/packages/SystemUI → REPLACE with AppleUX implementation
Navigation bar → frameworks/base/packages/SystemUI → REPLACE with AppleUX implementation
Notifications → frameworks/base/packages/SystemUI → REPLACE with AppleUX implementation
Lock screen → frameworks/base/packages/SystemUI → REPLACE with AppleUX implementation
Control Center → frameworks/base/packages/SystemUI → REPLACE with AppleUX implementation
```
