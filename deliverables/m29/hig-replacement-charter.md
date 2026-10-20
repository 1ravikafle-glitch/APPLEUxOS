# Apple HIG Replacement Charter (m29) — BINDING

**Directive (user, 2026-10-18):** Replace EVERY Material Design / Android
platform design surface in the system core with Apple Human Interface
Guidelines (HIG) and Apple design. Nothing Material Design remains. No slop.

## Scope — "every whole from very core"

| Layer | Rule |
|---|---|
| Framework / backend | No Material Components, no `Theme.Material*`, no `com.google.android.material`. |
| SystemUI | No Material QS tiles, no Material dialogs, no Material switches. |
| Every app module | iOS typography (SF-style weights/sizes), iOS spacing, iOS corner radii, iOS materials (Liquid Glass). |
| Settings / Launcher / Phone / Messages / Contacts / Photos / Files / Camera / Clock / Calendar / Browser / Videos | All HIG. |
| Dialogs, sliders, toggles, toasts, HUDs, banners | Appleux* components only (AppleuxDialog, AppleuxSlider, AppleuxToggle, AppleuxToast…). |

## Specific replacements (Material → HIG)

| Android Material | Apple replacement |
|---|---|
| `Theme.Material3.DayNight.NoActionBar` | `AppleuxAppTheme` (translucent glass, iOS corners) |
| `AlertDialog.Builder` | `AppleuxDialog` (+ `AppleuxDialogs`) |
| `android.widget.Switch` / `SwitchMaterial` | `AppleuxToggle` |
| `android.widget.SeekBar` | `AppleuxSlider` |
| `Toast.makeText` | `AppleuxToast` |
| `android.R.drawable.*` | vendored iOS vectors (`AppleuxIconRegistry`, `R.drawable.sf_*`, `il_ic_*`) |
| `TextClock` / `ProgressBar` | iOS-style custom views |
| Material QS tiles | `AppleuxControlCenter` |
| Material volume panel | `AppleuxVolumeHud` |

## Anti-slop rules

1. **No credit for components that exist but never render.** Every AppleUX
   component must have a live call site or it is documented as blocked with a
   specific reason (see `deliverables/m27/dead-components-*.md`).
2. **No comments describing intent as a substitute for behaviour.**
3. **No hardcoded values** — everything goes through shared design tokens
   (`ColorTokens`, `TypographyTokens`, `SizingTokens`, `MotionTokens`,
   `LiquidGlassTokens`, `LauncherTokens`, `SpacingTokens`).
4. **One edit propagates everywhere** — components consume tokens, never
   literals.
5. **Measure, don't assert** — no performance claim without
   `tools/capture-metrics.sh` / `tools/capture-evidence.sh` output.

## Verification gate

Before any claim of "iOS 27 parity":
- `bash /tmp/opencode/verify.sh` (recreate if absent) — gap table must show
  zero Material Design references.
- `grep -rn "Theme.Material\|com.google.android.material\|AlertDialog.Builder\|android.widget.SeekBar\|Toast.makeText\|android.R.drawable" --include=*.kt --include=*.java --include=*.xml` must return **zero live hits** in `frameworks/appleux` and `frameworks/base/packages/SystemUI`.

## Standing rule

This charter is the default for every future change in this project. Any new
code that introduces a Material Design or Android-platform UI surface is a
bug to be fixed, not a feature to be reviewed.
