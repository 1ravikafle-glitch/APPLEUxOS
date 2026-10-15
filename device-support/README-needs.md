# Device facts: received, closed, and still open

## Received 2026-10-15 — operator's own POCO M5, stock HyperOS

`wm size` / `wm density` / `dumpsys window` pasted from the physical unit.
This output is treated as absolute. Full capture and derivations:
`deliverables/m20/rock-display-measurement.md`.

| fact | value |
|---|---|
| panel | 1080 × 2408 px |
| density | 440 physical, **HyperOS override 430 (effective)** |
| frame | **401.9 × 896.0 dp** |
| top inset | **76 px** = 28.3 dp (status bar and waterdrop are the same 76 px) |
| bottom inset | **127 px** = 47.3 dp |
| cutout | **100 × 76 px**, top-centre, centre x = 540 = exactly half the width |
| refresh | **60 / 90 Hz switchable**, operator-confirmed |

`device-support/rock/` exists and its fallbacks are these measured values.
`RockDisplayProfile` reads live values at runtime; the constants are fallback
only. Frame budget derives from the live rate (14.00/16.67/20.00 ms at 60 Hz,
9.33/11.11/13.33 ms at 90 Hz).

## Closed by that paste

- Panel height (2408, not 2400), effective density (430, not 420), top inset
  (76 px, not 190), logical frame (401.9 × 896.0), refresh pair (60/90).
- Dock persistence is implemented (`DockPreferences`); the "first four apps
  alphabetically" behavior is gone.
- Grid density decision (5×7) stands; only the metrics need re-deriving at the
  true frame.

## Still genuinely open (this output cannot answer these)

1. **Active refresh mode.** `wm size` / `wm density` / `dumpsys window` do not
   report it. `adb shell dumpsys display | grep -iE 'fps|refresh|mode'`.
2. **Blur-behind cost on Mali-G57.** A GPU question; SwiftShader cannot answer
   it. Biggest perf unknown, unchanged.
3. **Kernel patches (6) have never booted.** Still hypotheses.
4. **Wallpaper source** (iOS 18 harvest vs. originals) — operator's call.
5. **Bootloader unlock + userdata backup.** Still locked, 87% full. No action
   without the word. Flash plan must be A/B-safe (slot `_b`, dynamic
   partitions) — fastbootd / OTA-style, not `fastboot flash super`.

## What is explicitly NOT needed

Apple assets, Figma files, or legal guidance. The 53 official Apple
screenshots and Apple's own icon vector are banked; the Figma kit would be
less accurate ground truth, not more.
