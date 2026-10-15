# AppleUX — Phase 1 Audit: Architecture, Gaps, and Fidelity Failure Analysis

Date: 2026-10-15
Auditor: lead platform engineer pass
Device under test: Android 16 AOSP emulator, API 36, 1080×2400 @ 420 dpi
(POCO M5 geometry). Build: `scripts/build-emulator-apks.sh`.

References used (all legally obtained, banked in `design-reference/ios27/`):

| Source | What it gave us |
|---|---|
| Apple iOS 27 icon template `.ai` (devimages-cdn.apple.com) | Exact squircle path, 40 Bézier segments, 1024 grid |
| Apple iOS 27 **iPhone User Guide** (support.apple.com) | Official behaviour text + **53 official screenshots** |
| MIT transcription of Apple's Figma kit | Liquid Glass material values, labelled `_measured` |
| HIG snapshot (2025-02-02) | Pre-Liquid-Glass foundations only — **not** iOS 27 |

Baseline captures: `screenshots/baseline-20261015/`.

---

## 1. Current architecture and source paths

```
appleux/
├── design-system/   36 kt / 4753 loc — tokens + 24 components
├── performance/      8 kt / 1005 loc — frame/pressure controller
├── systemui/         5 kt /  760 loc — overlay service, CC, shade, QS tiles
├── launcher/        10 kt / 1433 loc — home, dock, grid, library, gestures
└── settings/         9 kt /  703 loc — catalog + screens
```

Runtime shape: `AppleuxSystemUIService` is a foreground service holding two
`TYPE_APPLICATION_OVERLAY` windows (Control Center, Notification Center),
driven by intents from the launcher and by two QS tiles. Notifications arrive
through a real `NotificationListenerService`. There is **no** replacement for
status bar, navigation bar, lock screen, or recents — those are stock.

Build is deliberately Gradle-free: `aapt2` + `kotlinc` + `d8` + `apksigner`
driven by `scripts/build-emulator-apks.sh`. This works and is fast; it is also
why no Jetpack Compose, no AndroidX UI beyond a pinned set, and no AppWidget
framework integration.

---

## 2. Existing versus missing

**Implemented and verified on device**
- Home grid, glass dock, wallpaper pair, widget host (clock/battery/calendar),
  app library, long-press gallery, icon persistence
- Control Center overlay: tiles, real tile actions, frosted card
- Notification shade: real feed, app badges, actions, dismiss, clear-all
- iOS squircle icon mask (now Apple's exact path)
- Settings app with real platform writes

**Missing entirely**
- Lock Screen experience (`AppleuxLockScreen` exists but is **orphaned**)
- App switcher / recents (stock QuickStep)
- Status bar and navigation bar (platform signature required — documented)
- Edit mode for Control Center, media player, focus picker, page groups
- Vertical brightness/volume sliders
- Dialogs, sheets, toggles, list items as *used* components

---

## 3. The five biggest reasons AppleUX does not look or feel like iOS

### 3.1 The design system is bypassed — this is the root cause

Measured, not estimated:

| Component | Files that instantiate it |
|---|---|
| `AppleuxLockScreen` | **0** |
| `AppleuxStatusBar` | **0** |
| `AppleuxNavigationBar` | **0** |
| `AppleuxSheet` | **0** |
| `AppleuxDialog` | **0** |
| `AppleuxCard` | **0** |
| `AppleuxToggle` | **0** |
| `AppleuxListItem` | **0** |
| `AppleuxHaptics` | 1 |
| `AppleuxButton` | 2 |
| `AppleuxIcon` | 1 |

The launcher imports exactly **one** component from the design system:
`AppleuxHaptics`. Every surface hand-rolls its own text, cards, spacing and
motion. This is why the OS has no consistent material, type scale, or motion
language, and why adding fidelity to one screen never shows up in another.
Fixing screens without fixing this cannot converge.

### 3.2 Control Center is structurally wrong, not cosmetically off

Apple's own screenshot (`official-guide/images/7511c37e….png`) shows the real
iOS 27 Control Center: a **full-screen Liquid Glass sheet**, containing

- a `+` edit button (top-left) and a power button (top-right)
- a **connectivity cluster** — one grouped card, Airplane / AirDrop / Wi-Fi / cellular / Bluetooth, each independently coloured
- a **media player card** — artwork, "Not Playing", transport
- a **Focus pill** with a chevron
- **full-height vertical brightness and volume sliders**
- circular glass buttons (rotation, mirror, calculator, camera, QR)
- **page dots on the right edge** to jump between control groups

Ours is a single small floating card with a rigid 2-column grid of six
identical circular tiles. No media, no sliders, no groups, no page dots, no
edit mode. The resemblance is superficial, and it is the most-seen surface
after the home screen.

### 3.3 The glass material was never actually implemented

`AppleuxGlass` applied a `RenderEffect`. A `RenderEffect` blurs a window's
**own** drawables — it cannot touch the layer behind the window. Over a
translucent fullscreen overlay this produced the launcher showing straight
through the Control Center card, with launcher labels colliding with tile
labels (captured, `03-controlcenter.png`).

The platform primitive for the iOS material is `FLAG_BLUR_BEHIND` +
`blurBehindRadius` (API 31+). Added today and not yet frame-measured — see
Phase 5 risk. The Notification Center is still flat opaque white with no
material at all.

### 3.4 There is no Lock Screen

`AppleuxLockScreen` is defined and never used. Apple's iOS 27 lock screen is
specific and recognisable: full-bleed wallpaper, a **very large, lightweight,
semi-transparent clock spanning most of the screen width**, a small date above
it, and circular glass buttons for flashlight and camera in the bottom
corners. Nothing in AppleUX competes with this, and it is the first screen
seen on every boot.

### 3.5 Recents and system bars break character the moment the user leaves Home

Recents is stock QuickStep. Per the replace-from-source charter this is a
deliberate, documented boundary — stock recents stays until our task-switcher
exists. But it means any app switch drops the user into unmistakably Android
UI. Replacing the **status bar** is genuinely blocked (needs platform
signature) and is a ROM-stage item; the task switcher is not blocked, merely
unbuilt.

---

## 4. Phased plan, with build dependencies

| Phase | Work | Depends on | Gate |
|---|---|---|---|
| **A. Unblock fidelity** | Make components actually used: text, card, toggle, list item, sheet, dialog | — | each surface imports the shared component, not local styling |
| **B. Control Center** | Full-screen glass sheet; connectivity cluster; vertical sliders; media card; page dots; edit mode | A | screenshot-compared vs `7511c37e` |
| **C. Lock Screen** | Large translucent clock, date, wallpaper, flashlight/camera buttons, secure-keyguard respect | A | screenshot-compared vs `8313a5fa`; must not bypass Android security |
| **D. Notification Center** | Apply the glass material; group cards; iOS row metrics | B (shares material) | screenshot-compared |
| **E. Navigation** | Own task switcher; gesture-nav parity | A, C | replacing QuickStep only after ours is verified |
| **F. Settings shell** | Use shared components; iOS grouped list metrics | A | screenshot-compared |
| **G. Measurement** | JankStats; `gfxinfo`; memory on real hardware | — | no claim without evidence |

Build dependency is strictly **A → {B, C, D, F} → E → G**. Nothing in B–D
should be styled independently; if a screen needs a new look, the component
changes, not the screen.

### What is deliberately NOT planned
- Status bar / navigation bar replacement — requires platform signature
  (STATUS_BAR). ROM-stage item, boundary already documented.
- RRO reskin on Google-signed images — inert there; bacon-ROM item.

---

## 5. First concrete source changes made under this directive

| Change | File | Verified by |
|---|---|---|
| Apple's exact squircle replaces an eyeballed rounded rect | `launcher/ui/AppleIconSquircle.kt` (generated), `AppIconView.kt` | `squircle-validation.png`; superellipse fit n≈4.4 vs rounded rect, 38% lower error |
| Liquid Glass values replaced invented constants | `design-system/tokens/LiquidGlassTokens.kt` | build |
| CC corner radius 28f → 34pt from the kit; scrim ramp → slider-derived alpha | `AppleuxControlCenter.kt`, `AppleuxGlass.kt` | build; screenshot pending |
| Real frosted glass via blur-behind | `AppleuxSystemUIService.kt` | screenshot |
| Cutout/fullscreen overlay fix | same | window now fills 1080×2400 |
| Swipe-up panel dismiss (iOS behaviour) | `AppleuxPanelDismissHandler.kt` | screenshot: shade closes |
| Shade layout: header/list/footer stacking fix | `AppleuxNotificationShade.kt` | screenshot: no overlap |
| Notification app badges | `AppleuxNotificationListener.kt` | screenshot: icons render |
| Overlay-permission failure made loud | `AppleuxSystemUIService.kt` | this was the cause of a full day of "gesture does nothing" (§34) |

**Not yet verified:** blur-behind frame cost. It is the most likely
performance risk in the current build and must be measured before shipping
(Phase G).

---

## 6. Control Center iteration log (screenshot-driven)

Captured against `official-guide/images/7511c37e-…png`.

| Version | Change | Result |
|---|---|---|
| v1 (baseline) | small centered frosted card, 2-col grid of 6 tiles | launcher icons visible straight through the card; labels collided |
| v2 | **full-screen glass sheet**, top-rounded, blur-behind, kit fill + rim tokens | sheet now fills 1080×2400, wallpaper genuinely blurred through it — the material finally reads as glass |
| v3 | tile rim + round corners | rim at 0.5dp is not perceptible at this scale; **no visible change** |

### Remaining Control Center gaps, in priority order

1. **Controls are wrong-shaped.** iOS renders each control as a ~56pt circle
   with **no text label**. Ours are icon+label pairs in a fixed grid, so the
   grid carries text that iOS does not have, and the controls are not
   circular. This is the largest remaining delta.
2. **Header type is oversized** and collides with the status-bar clock
   ("Wi-Fi Bluetooth" overlaps the system clock). iOS keeps the header compact
   and left-aligned.
3. **No vertical sliders.** iOS has full-height brightness and volume sliders
   as first-class elements; ours has a "Brightness" tile that cycles a value.
4. **No media card**, no Focus pill, no connectivity cluster (one grouped
   card with independently-coloured sub-toggles), no `+`/power buttons, no
   right-edge page dots, no edit mode, no long-press secondary panels.
5. **Behavioural bug:** tapping Wi-Fi/Bluetooth calls `setWifiEnabled(false)`.
   Per Apple's iOS 27 guide, tapping them **disconnects** and leaves the radio
   on. Not a cosmetic difference — it changes what the user experiences.

### Note on the rim
The kit's rim is specified as a 0.5pt spread drop-shadow, which is below the
threshold of visibility once rasterised at 420 dpi. Keep the token (it is
correct) but do not expect it to carry the edge definition on its own; the
edge contrast has to come from the fill delta against the sheet.