# iphone11-ios27 — REAL iPhone 11 iOS 27 screenshots (ground truth)

**Source:** operator's own iPhone 11 running iOS 27 (photos of the live
screen, 2026-10-16). These outrank Figma transcriptions, HIG snapshots,
and USSR-style "measured from guide PNGs" wherever they disagree.

**Rule for every session:** consult these images before implementing or
restyling ANY Settings surface (and home/CC/lock when extended here).
Do not guess iOS 27 chrome — look at the pixels.

## Files (operator drops originals here; names are fixed)

| File | Content (from conversation 2026-10-16) |
|---|---|
| `settings-root-top.png` | Root, top: account card (avatar+name+"Apple Account, iCloud, and more"), Airplane toggle row, Wi-Fi SSID value, Bluetooth "Not Connected", Cellular/Hotspot "Off", Battery |
| `settings-root-mid.png` | Root, scrolled: small centered "Settings" title, Appearance/Camera/Control Center/Display/Home/Search/Siri/StandBy/Wallpaper, Notifications/Sounds/Focus, floating Search pill |
| `settings-root-bottom.png` | Root, bottom: Focus/Screen Time, Face ID & Passcode, Emergency SOS, Privacy & Security, Game Center, iCloud, Wallet, Apps |
| `settings-general-top.png` | General: back-circle chevron, hero card (gear icon + "General" + description), About/Software Update/iPhone Storage, AppleCare, AirDrop/AirPlay/PiP/Screen Capture |
| `settings-general-mid.png` | General scrolled: AirPlay/PiP/Screen Capture/CarPlay, AutoFill/Background Refresh/Date & Time/Dictionary/Fonts/Keyboard/Language, TV Provider |
| `settings-general-bottom.png` | General bottom: Date & Time…Language, TV Provider, VPN & Device Management, Legal & Regulatory, Transfer or Reset, blue "Shut Down" row |

## Live captures (2026-10-16 23:3x, AirPlay mirror burst, filed as taken)
- `home-live.png`, `lock-screen-live.png` (Luffy wallpaper, huge clock,
  flashlight/camera buttons), `control-center-live.png` (real CC: sliders,
  toggles, Focus pill), `app-library-live.png` (real folders + badges).
  These are first-party pixels — prefer them over any transcription.

## Drive set (2026-10-17, `drive/`, 47 files, light + dark)
Full iOS 27 coverage from the operator's Drive: home/light+widgets,
lock light+dark, passcode x2, CC dark x2 + light + media/connectivity
expanded, App Library p1-p3, Settings root light+dark, General light+dark,
Wi-Fi/Bluetooth/Cellular/Battery/Camera/Appearance/Accessibility dark
details. Layout AND design match 100% — light/dark differ only in palette.

New binding rules from this set (beyond the 6 above):
7. CC toggles carry TINY labels under the discs (Rotation Lock, Timer…).
8. CC long-press opens dedicated expanded sheets (connectivity list,
   media player + volume + AirPlay row).
9. Settings root order is FIXED (General…Apps as photographed) — match it.
10. Sub-screen pattern: back-circle, hero card, groups, blue/destructive
    last rows (Shut Down). Detail pages mirror exact row inventories.
11. Lock: small date ABOVE the huge clock; flashlight/camera circles;
    passcode = dots + round lettered pad + Emergency/Cancel.
12. App Library: 2-col folders, 4 minis each, badges, search pill on top.

## Design facts extracted (2026-10-16, do not re-derive)

1. Root top: account card FIRST, then connectivity group (Airplane=toggle, rest chevrons with VALUE text right-aligned gray: SSID / Not Connected / Off).
2. Grouped cards: ~20dp radius, ~16dp side margins, generous inter-group gaps, `#F2F2F7`-like grouped bg.
3. Search = FLOATING BOTTOM pill (magnifier + "Search" + mic), not a top field.
4. Scrolled root: small centered title ("Settings"); top shows large title.
5. Sub-screen: circular back chevron top-left, centered small title, HERO card (big badge icon + bold title + gray description), then groups; danger/blue text rows (Shut Down) last.
6. Chevron `›` light gray on every nav row; toggles only where the setting is a switch (Airplane).

## Provenance note
Faces/SSIDs redacted where the operator blurred them. Account name in the
sample ("Ravi Kafle") is the operator's own data — NEVER ship it; the app
shows the system account page instead (see SettingsCatalog notes).
