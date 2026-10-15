# Phase 3 — AppleUX Design System

## Status

**Source complete. Compiled and exercised on the API 36 emulator as part of the
launcher and settings apps.**

Built as `out/emulator/apks/appleux-design-system.apk`. That artifact is a
library: it has no `<application>` element, so `adb install` rejects it with
`INSTALL_PARSE_FAILED_MANIFEST_MALFORMED`. That is expected and is not a build
failure — the classes are packaged into the launcher and settings APKs and run
there.

## Verified on the emulator

| Component | Evidence |
|---|---|
| `AppleuxTheme` light/dark tokens | launcher bg sampled `(245,245,247)` light, `(0,0,0)` dark |
| `AppleuxTheme.applySystemBars` | status/nav bar colours repaint with the theme |
| `AppleuxRuntimeConfig` | four flags cross-process via the config provider; see `AGENTS.md` |
| `AppleuxHaptics` | best-effort; a missing `VIBRATE` grant no longer crashes the launcher |

## Not verified

- No rendered-component screenshot review was performed. Icon sizing and the
  homescreen grid were corrected from measured view bounds (60 px cells rendered
  instead of ~157 px icons; first row under the status bar), not from a visual
  sign-off.
- No accessibility audit.
- Motion/easing is untested; `reduce_motion` is stored and propagated but no
  component consumes it yet.
