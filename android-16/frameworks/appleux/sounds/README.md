# appleux/sounds — iOS 27 Sound Collection

Complete sound extraction from the iOS 27 IPSW for the AppleUX custom ROM.
All sounds are for **personal and educational use only**.

## Overview

| Category | Count | Description |
|---|---|---|
| Ringtones | 44 | Incoming call ringtones, ringback tones, haptic ringtones |
| Notifications | 89 | Message, mail, calendar, and app notification sounds |
| System | 215 | Lock, unlock, charging, camera, navigation, and more |
| UI | 18 | Keyboard clicks, DTMF tones, key press feedback |
| **Total** | **366** | |

## Directory Structure

```
appleux/sounds/
├── ringtone/           # 44 ringtones (.caf + .ogg)
├── notification/       # 89 notification sounds (.caf + .ogg)
├── system/             # 215 system sounds (.caf + .ogg)
├── ui/                 # 18 UI sounds (.caf + .ogg)
├── src/main/
│   ├── res/
│   │   ├── raw/        # 366 .ogg + 366 .caf (Android raw resources)
│   │   └── values/
│   │       └── sounds.xml  # Default sound configuration
│   └── java/com/appleux/sounds/
│       └── SoundRegistry.kt  # Kotlin registry mapping
└── README.md
```

## Sound Sources

- `System/Library/Audio/UISounds/` — Core system and UI sounds
- `System/Library/Audio/UISounds/nano/` — Apple Watch / compact sounds
- `System/Library/Audio/UISounds/New/` — Classic ringtones
- `System/Library/Audio/UISounds/Modern/` — Modern UI sounds
- `System/Library/PrivateFrameworks/ToneLibrary.framework/AlertTones/` — Alert tones
- `System/Library/Carrier Bundles/` — Carrier emergency alerts
- `System/Library/UserNotifications/Bundles/` — App notification sounds

## Audio Format

- **CAF** (Core Audio Format) — Original iOS format, preserved for reference
- **OGG Vorbis** — Converted for Android raw resource playback

All files are 44.1 kHz, mono, extracted from the iOS 27 IPSW.

## Usage

### In Kotlin code:

```kotlin
import com.appleux.sounds.SoundRegistry

// Play a ringtone
val ringtoneUri = Uri.parse("android.resource://${packageName}/${SoundRegistry.RINGTONE_FANFARE}")

// Play a notification
val notifUri = Uri.parse("android.resource://${packageName}/${SoundRegistry.NOTIFICATION_NANO_ALERT_5}")

// Play a system sound
val lockUri = Uri.parse("android.resource://${packageName}/${SoundRegistry.SYSTEM_LOCK}")
```

### In XML:

```xml
<reference name="default_ringtone">@string/default_ringtone</reference>
```

## Default Assignments

| Setting | Default Sound |
|---|---|
| Ringtone | `ringtone_fanfare` |
| Notification | `notification_nano_alert_5` |
| System | `system_lock` |
| Lock | `system_lock` |
| Unlock | `system_auto_unlock` |
| Charging | `system_connected_to_power` |
| Camera Shutter | `system_camera_shutter` |
| Key Click | `ui_key_click` |

## Original AppleUX Chimes

The original synthesized AppleUX chimes remain at the root of this directory:
- `appleux_boot.wav` — Boot chime
- `appleux_lock.wav` — Lock sound
- `appleux_unlock.wav` — Unlock sound
- `appleux_charge.wav` — Charging sound

## Legal

These sounds are extracted from a real iOS 27 IPSW for **personal and educational use only**.
Apple's standard licenses restrict use to Apple platform development. For any distribution or
commercial use, replace all Apple-derived assets with open-licensed alternatives.
