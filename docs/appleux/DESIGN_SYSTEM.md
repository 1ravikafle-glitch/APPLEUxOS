# AppleUXOS — DESIGN SYSTEM

> **Status: DRAFT v0 — STRUCTURE ONLY.**
> Concrete values below marked `TBD-REF` must be taken from the licensed iOS 27 reference material supplied to the project, not guessed. Fill them in during Milestone 2 and record the source of each value.

## 1. Principles
1. **Familiarity:** layout, gestures, motion and behavior match iOS 27 so an iOS 27 user needs no relearning.
2. **Single source:** one token set, one implementation per component.
3. **Fluid:** spring-based, interruptible motion; no dropped frames in core flows.
4. **Light:** cheap rendering paths first; heavy effects only where measured frame time allows.
5. **Accessible:** large touch targets, Dynamic-Type-style text scaling, contrast, reduced motion.
6. **Documented deviation:** any difference from iOS 27 is recorded in section 12 with a reason.

## 2. Typography
- Typeface: licensed SF family (see `ASSET_MANIFEST.md`). Fallback: system sans.
- Scale (Large Title, Title 1-3, Headline, Body, Callout, Subhead, Footnote, Caption): sizes, weights, line heights, tracking in POINTS transcribed verbatim in `TypographyTokens`; em value = pt / size, computed by `FontSpec.emTracking` (Body 17pt: -0.43pt = -0.0253em).
- Text scales with user font-size setting; layouts must not break at largest scale.

## 3. Color
- Semantic tokens (not raw hex in components): `label`, `secondaryLabel`, `separator`, `systemBackground`, `groupedBackground`, `fill`, `tint`, `destructive`, etc.
- Light and dark values per token `TBD-REF`.
- Contrast target: WCAG AA minimum for text; record measured ratios.

## 4. Spacing, grids and layout
- Base unit and spacing scale `TBD-REF`; safe areas and margins for the POCO M5 display (record actual resolution, density and cutout).
- Layout grid for home screen icons, dock, widgets, Control Center modules `TBD-REF`.

## 5. Shape and corner radii
- Continuous (squircle) corners for icons, cards, sheets. Radius per component `TBD-REF`.
- Implementation note: continuous corners are a rendering cost; use cached or precomputed paths and verify GPU impact.

## 6. Iconography
- App icons and system symbols from licensed assets. Grid, stroke weight, optical sizing `TBD-REF`.
- Symbol set mapped to Android resource names in `ASSET_MANIFEST.md`.

## 7. Components (one implementation each)
Buttons, switches, sliders, segmented controls, steppers, text fields, lists and grouped tables, navigation bars, tab bars, alerts, action sheets, bottom sheets, popovers/context menus, toasts/banners, progress indicators, pickers.
Each component spec must define: anatomy, sizes, states (default, pressed, disabled, focused, error), light/dark, accessibility labels, motion, and a test.

## 8. Navigation and gestures
- Home, app switcher, back/edge, Control Center, notification shade, long-press menus, text selection.
- Map each gesture to Android's input/navigation stack; record exact thresholds and velocities `TBD-REF`.

## 9. Motion
- Spring model: stiffness, damping, mass (or response/damping-fraction) per transition type `TBD-REF`.
- Required properties: interruptible, velocity-preserving, reversible.
- Core transitions: app open/close, home gestures, app switcher, sheet present/dismiss, shade pull, Control Center, lock/unlock, keyboard.
- Reduced-motion variant for accessibility and low-memory tier.

## 10. Haptics
- Haptic event map (selection, impact light/medium/heavy, success, warning, error) to Android `VibrationEffect` primitives supported by the device motor. Verify what the POCO M5 vibration motor can actually reproduce.

## 11. States
Loading, empty, error, disabled, offline, permission-denied, low-battery, storage-full. Each needs a defined appearance.

## 12. Effects and performance tiers
| Effect | Tier A (3-4 GB) | Tier B (2 GB) |
|---|---|---|
| Backdrop blur | Downsampled or cached blur, measured | Pre-blurred static or solid translucent |
| Shadows | Simple, cached | Minimal |
| Parallax / wallpaper depth | Optional | Off |
| Animation complexity | Full | Reduced |
Rules: every effect needs a measured frame-time cost on the POCO M5 before adoption; automatic downgrade when over budget.

## 13. Accessibility
- Minimum touch target and spacing `TBD-REF` (record values and verify in tests).
- Font scaling, bold text, increased contrast, reduce motion, reduce transparency, screen reader labels, color-independent state cues.

## 14. Deviation log from iOS 27
| Date | Surface | Deviation | Reason | Approved by |
|---|---|---|---|---|
| | | | | |

## 15. Token delivery
Tokens defined once (source format TBD, e.g. JSON) and generated into Android resources and Kotlin/Java constants, consumed by framework overlays, SystemUI, Launcher, Settings and system apps.

---

## ACTUAL STATE (2026-10-19) — tokens and provenance as-built

- Tokens live in `frameworks/appleux/design-system/.../tokens/`:
  Color, Typography, Sizing, Spacing, Motion, LiquidGlass, Launcher, LockScreen,
  Contacts. One edit propagates everywhere — components consume tokens only
  (HIG charter forbids literals).
- **Squircle**: Apple's own contour (superellipse n≈4.4 from Apple's App Icon
  Template), 40 cubic segments, scaled from the 1024 pt grid.
- **Icon mask**: `config_icon_mask` in framework-res = iOS squircle path.
- **Dock/home geometry**: DERIVED at our measured frame (392.73×875.64 dp):
  4×6 grid, 88.18 dp cell pitch, 60 dp icons, 146 dp dock clearance,
  150 dp widget cell height.
- **Motion**: spring physics in MotionTokens; press scale 0.94 with
  spring(0.6, 500); jiggle via AppleuxJiggle; panel dismiss via
  AppleuxPanelDismissHandler.
- **Fonts**: platform `sans-serif` (SF UI from IPSW is Regular-only and
  AppleSymbols has PUA-only names — no SF Symbol names — so vendored vectors
  are the icon route; `sf27-catalog.json` holds the 340 new symbols).
- **Typography divisor: CLOSED 2026-10-19 (C1, session C)**. Token engine converged via
  `emTracking` (pt / size); XML bare-widget styles were the last ~63× outlier
  (0.0004 -> -0.0253em in `AppleuxTextView`/`AppleuxButton`/`AppleuxEditText`;
  dialog 0.024 -> -0.02em). Cross-checked vs `design-reference/iphone11-ios27/`
  ground-truth README rules and `IOS27licencework/ios-ui-analysis` font harvest.
  Rendered-body verdict pending from B.
- **Material Design**: banned by charter; live usage in appleux modules is
  zero (AppleuxDialog/Slider/Toggle/Toast replace all of it).
- Binding design references: operator's ground-truth photos
  (`design-reference/iphone11-ios27/`, 47 files) — outrank every other source
  for Settings chrome. Precedence rules in PREVIOUS_WORK_DONE §5.