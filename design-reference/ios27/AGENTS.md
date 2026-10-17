# iOS 27-style Android 16 ROM: agent instructions (POCO M5, `rock`/`stone`)

Target: AOSP 16, POCO M5 (Helio G99 / Mali-G57, 4 GB RAM, 64 GB), ~411 dp wide.
Goal: iOS 27 look, layout and motion across lock screen, home, dock, notifications, control panel, settings, keyboard, dialogs, sheets, volume HUD.

## Read this first: trust levels
All numbers here are **unverified recall or estimates**, not measurements from Apple's kit (Figma was unreachable). Every value has `basis` / `inferred` / `verified` fields. Rules:
1. Never present a value as Apple-measured. If `verified` is false, treat it as a tuning knob.
2. When a screenshot or Figma export arrives, update `figma-extract/*.json`, set `verified: true`, then re-run the generator. No Kotlin edits should be needed.
3. `null` means unknown. Do not fill nulls by guessing in code; ask or measure.

## Source of truth (data, never hardcode in Kotlin/XML)
| File | Content |
|---|---|
| `../figma-extract/design-tokens.json` | colours (light/dark), type, spacing, radii |
| `../figma-extract/screens/*.json` | per-screen layout as fractions of screen |
| `../figma-extract/settings-menu.json` | Settings hierarchy |
| `../figma-extract/components/*.svg/.json` | shapes + Android `pathData` |
| `motion.json` | springs (with Android stiffness/damping), durations, scroll physics |
| `feature-map.json` | iOS feature -> Android alternative (AirDrop -> Quick Share etc.) |
| `aosp-module-map.json` | which AOSP module implements each surface |
| `generated/res/` | output of `tools/gen_android_res.py` (ship as an RRO) |

Regenerate: `python3 tools/build_spec.py && python3 tools/gen_android_res.py --screen-dp 411 --ref-pt 393`.

## Code rules
- Colours: `@color/ios_*` only. Dimens: `@dimen/ios_*`. Text: `@style/TextAppearance.iOS.*`.
- Motion: read `ios_spring_*_stiffness/damping` and `ios_dur_*_ms`; build `SpringForce` from them. No literal numbers in animation code.
- Screen geometry: use `screens/*.json` fractions through one helper (`IosLayout.frac(x,y,w,h)`), never pixel literals.
- Rounded shapes: capsule where the control is a pill; squircle (`app-icon-squircle`) for icons; do not use `RoundedCornerShape` approximations for icons.
- Every gesture must be interruptible and follow the finger 1:1, then pass release velocity to the spring.

## Do not delete; replace in place
Do not remove stock SystemUI, Settings or Launcher3 wholesale. They contain the framework hooks (keyguard auth, notification pipeline, tile services, preference backends, key handling) that the system needs. Fork each module, replace layouts, themes and view classes, and keep the backend logic. Delete stock UI only after the replacement passes the checks below.

## Working process (do in this order)
1. **Foundation**
   - Fonts: add Inter (or another open font) as the system font via `fonts.xml`. Do not ship SF Pro or SF Symbols (Apple-platform licence).
   - Icon shape: overlay `config_icon_mask` using `components/app-icon-squircle` path.
   - Theme RRO: ship `generated/res` as a vendor overlay (`vendor/ios27` or `device/xiaomi/rock`).
   - Exit check: boot, and every app shows squircle icons and the new font/colours in light and dark.
2. **Launcher** (`packages/apps/Launcher3`): 4-column grid (centres in `screens/home.json`), dock as a glass container, folders, wiggle edit mode, App Library as the page after the last home page, open/close zoom using `appLaunch`/`appClose` springs.
3. **Lock screen** (SystemUI keyguard): clock, date, widgets row, notification stack, flashlight/camera quick actions, home indicator, unlock swipe.
4. **Notifications**: restyle group/row views; keep the notification pipeline.
5. **Control panel**: new tile layout (connectivity cluster, media, focus, sliders, tiles); AirDrop tile launches Quick Share (see `feature-map.json`).
6. **Settings** (`packages/apps/Settings`): restyle to inset-grouped lists per `settings-menu.json`; keep preference backends and intents.
7. **Keyboard, dialogs, sheets, volume HUD, status bar, share sheet** per `aosp-module-map.json`.
8. **Performance pass on Helio G99 / 4 GB**: see budget below.

## Performance budget (G99, Mali-G57, 4 GB)
- No full-screen real-time blur. Use pre-blurred wallpaper layers, small low-radius `RenderEffect` blurs on at most one surface at a time, or a translucent solid fallback.
- Target 60 fps in the launcher and shade; fall back to 90 only where it is free. Profile with `perfetto` / GPU rendering profile.
- Keep a single shared `SpringAnimation` pool; avoid per-frame allocations in touch paths.
- Do not ship an on-device AI model.

## Definition of done per surface
- Matches its `screens/*.json` within an agreed tolerance on a 411 dp screenshot overlay.
- Light and dark both checked.
- Gesture is interruptible and keeps the finger attached.
- No literal colours, dimens or durations in code (grep check in CI).
- Backend behaviour unchanged (keyguard unlock, notification actions, tile toggles, preferences).

## Licensing and naming
Replicating layout and motion is a design choice; copying Apple's assets is not. Do not ship Apple logos, wallpapers, sounds, SF fonts/symbols, or the Apple trademarks (AirDrop, FaceTime, iMessage, Siri, etc.). Use the neutral names in `feature-map.json`.
