# IPSW harvest — findings adopted into the project

Source: `/home/elfakiris/ios-ui-analysis/` (513 MB, outside this repo).
iOS 27 + iOS 18 IPSW content: SpringBoard plists, 57 Control Center module
Info.plists (per-version), 413 app + 1258 framework Info.plist entries,
color asset plists, kernelcache, Assets.car files, font trees, wallpaper
manifest. Parsed with Python `plistlib` (binary + XML) — no Apple tooling,
no device.

## What was adopted (facts only — no Apple binaries, fonts, or artwork copied)

### 1. Dock order + slot 4 — Apple-measured (DockPreferences)

`layouts/SB_DefaultIconState~iphone.plist` → `buttonBar`:
`com.apple.mobilephone`, `com.apple.mobilesafari`, `com.apple.MobileSMS`,
`com.apple.Music`. Two corrections to our defaults: slot order is now
dial/browser/message/music (was dial/message/browser), and slot 4 is MUSIC
(was camera — iOS carries no camera in the dock; camera is a lock-screen
shortcut, which we also have). Resolution stays by explicit identity
(allowlist → label → role intent); only the slots changed. User-replaceable
as before.

### 2. Control Center module inventory — 57 real bundle IDs

Parsed every `control-center/*_Info.plist`. New in 27 (MinOS 27.2):
`com.apple.accessibility.controlcenter.noiselevels` (Noise Levels),
`com.apple.devicemanagementclient.DMCRRTSControlCenterModule` (Reset Device),
`com.apple.replaykit.controlcenter.RPControlCenterModuleHQLR`. Gone from 27:
Home Compact, Print Center. None adopted as tiles: Reset Device is
destructive, Noise Levels needs a hearing-accessibility backend we have not
built, and our tile set (wifi/bluetooth/airplane/flashlight/dnd/brightness)
all correspond to modules present in BOTH versions. Full map saved at
`/tmp/opencode/cc-modules.json` (regenerable — rerun the parse, don't hand-copy).

### 3. Default page layout (reference, not implemented)

Page 0 order: FaceTime, Calendar, Photos, Camera, Mail, Notes, Clock, Maps,
TV, News, App Store, Games, Health, Wallet, Siri, Settings (+3 onboarding
widgets). Not implemented: our launcher lists installed Android apps, so an
Apple bundle order has nothing to attach to. Kept as reference for future
default-placement work.

## What was deliberately NOT taken

- **Binaries**: kernelcache, executables, Assets.car — encrypted or
  uninterpretable without Apple's keys; also unredistributable. Untouched.
- **Fonts**: full font trees present, but Apple fonts ship under an
  Apple-platforms-only license. Our type stays system sans + HIG metrics.
  Font NAMES may inform future work; font FILES never enter the tree.
- **Wallpapers**: only `DefaultWallpapers~iphone.plist` (device-key manifest),
  no image files. The wallpaper gap stands.
- **Color asset plists**: CoreImage filter chains (blur 3px + line overlays),
  not color values — no token extracted.
- **App/framework inventory** (413 + 1258 Info.plists): counted, not imported.
  Useful later for coverage mapping against our 630-surface inventory.

## Provenance rule for this source

IPSW plists that parse as data (bundle IDs, default orders, display names)
are facts and may be transcribed with a citation. Anything requiring Apple
keys, Apple binaries, Apple fonts, or Apple artwork stays outside the repo.

## Icon reference set (macOS 27, user-supplied Drive folder)

37 valid 1024x1024 PNGs in `/tmp/opencode/icons-ref/` (reference only — Apple
artwork, never enters the repo or any APK; `app store.png` failed to download,
HTTP 500). These are macOS-style icons (e.g. Safari WITH tick marks — iOS
Safari has none), so they inform glyph SHAPE language, not iOS-exact art.

Notable for our redraws: Messages bubble (tail bottom-left, ~barely tapered),
Safari compass (needle red/white, 60 tick ring on macOS only), Photos pinwheel
(8 petals, specific hue order). Our launcher shows real Android app icons, so
these feed the settings-badge / CC-tile glyph set (`sf_*`/`il_ic_*`), where
18-24dp white-on-color rendering means shape transfers, detail does not.

## SF Symbols 27 DMG — verified against the harvest (2026-10-15)

Second drive: `/media/elfakiris/323EA7F52BCB4EB5/SF-Symbols-27.dmg` (445,052,983
bytes). Extracted on Linux with 7z (DMG) → cpio (`Payload~`) — no macOS needed
— to `/tmp/opencode/sfdmg/payload` (631 MB, reference only, NOT in repo).

- App version: SF Symbols 27, full app + `SFSymbolsFallback.otf` (41.7 MB).
- `Metadata/name_availability.plist`: 9524 symbols; **2026 set is 340/340
  identical** to our `sf27-catalog.json` — the harvest is complete and
  accurate, nothing new to transcribe.
- Geometry pipeline proven but parked: fallback font holds **41,986 glyphs**
  as CFF2 outlines, parseable with fontTools (tables CFF2/GDEF/GPOS/GSUB/HVAR
  present). Usable in future ONLY as redraw reference (stroke weights,
  proportions) — Apple vectors never enter an APK.
- Rule restated: names/versions are facts and bankable; font files, glyph
  outlines, and artwork stay outside the tree.
