# Apple Design Context

## Product
- **Name**: AppleUX OS (AppleUX ROM)
- **Description**: Android 16 AOSP-based ROM for POCO M5 that reproduces the iOS 27 look, layout, motion and gestures across launcher, SystemUI, Settings and lock screen.
- **Category**: Utility (system software)
- **Stage**: Development (emulator-verified; hardware port pending)

## Platforms
| Platform | Supported | Min OS | Notes |
|----------|-----------|--------|-------|
| iOS (design target only) | N/A | iOS 27 visual/API reference | iPhone 11 photos + ios27 spec JSONs are the visual truth |
| Android (runtime) | Yes | Android 16 (API 36) | AOSP 16 tree + emulator system-image route |

## Target device (measured, authoritative)
- POCO M5 (`rock`): 1080×2408 px @ 440 dpi, frame 392.73×875.64 dp
- Top inset 76 px (status bar + waterdrop same), bottom inset 127 px
- Cutout 100×76 px top-center; corner radius 106 px; 30/50/60/90 Hz
- Emulator appleux36 replicates the frame for verification

## Technology
- **UI Framework**: Native Android Views (Kotlin), XML layouts compiled by aapt2. No Compose, no web views for runtime UI.
- **Architecture**: Privileged system apps (launcher, settings, systemui) + RRO overlays + injected system media; replace-from-source vs stock.
- **Apple Technologies**: None at runtime (no Apple binaries/frameworks/artwork in repo by policy). SF Symbols approximated by vendored `sf_*` vectors + canvas paths; Inter-like system typography (no SF Pro shipped).

## Design System
- **Base**: Custom (Liquid Glass tokens measured from Apple Figma kit; layout from files5/IOSLikeROM prototype + ios27 spec fractions)
- **Brand Colors**: Black bg, silver logo gradient, accent green #3DDC84, iOS system palette (blue/orange/green/red)
- **Typography**: 12sp icon labels (2-line max), semibold brand, caption scale per surface
- **Dark Mode**: Supported (user choice overrides platform)
- **Dynamic Type**: Not yet (fixed sp sizes)

## Accessibility
- **Target Level**: Baseline (content descriptions on icons/tiles/island; a11y labels kept where visuals hide them)
- **Key Considerations**: Touch targets ≥44dp for tiles/dock; Reduce Motion honored on all custom animations

## Users
- **Primary Persona**: POCO M5 owner wanting an iPhone-grade feel on low-end hardware (3 GB RAM budget)
- **Key Use Cases**: Home/app launching, control center, notifications, settings, lock screen — one-handed, on-the-go
- **Known Challenges**: 3 GB RAM floor; no GPU blur budget (RenderEffect/FLAG_BLUR_BEHIND only); stock SystemUI deleted (keyguard/volume/power/nav need custom ownership); signature permissions ungrantable without platform cert

## Existing Design Assets
- `design/spec/SOURCES.md` (authority ranking), `design/spec/ios27-baseline.md` (630-surface inventory)
- `design/spec/extracted/screens/*.json` (per-screen fractions — layout source of truth)
- `design-reference/ios27/official-guide/images/` (53 reference renders) + user-supplied iPhone 11 photos
- `design-reference/boot/appleux-boot.html` (boot design source), `design-preview/` (HTML verification harness)
