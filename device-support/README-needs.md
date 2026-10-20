# Device facts: received, closed, and still open

## Received 2026-10-15 — operator's own POCO M5 (panel geometry, HyperOS session)

`wm size` / `wm density` / `dumpsys window` / `dumpsys display` from the
physical unit, plus a full read-only sweep (`pm list features`, meminfo,
battery, cameras, haptics, flash props — raw outputs in
`device-support/rock/measured-2026-10-15/`). Panel geometry is treated as
absolute. Full capture and derivations:
`deliverables/m20/rock-display-measurement.md`.

**Density rule — read twice:** the 430 figure below is HyperOS's override. It
dies at bootloader unlock. This ROM installs as the main system, so HyperOS
has no role: OUR ROM sets **440 physical, no override**, frame **392.73 ×
875.64 dp**. The 401.9 × 896.0 dp frame belonged to the HyperOS session only.

| fact | value |
|---|---|
| panel | 1080 × 2408 px |
| density (OUR ROM) | **440, no override → 392.73 × 875.64 dp** (HyperOS session: 430 → 401.9 × 896.0) |
| top inset | **76 px** = 27.6 dp on our ROM (status bar and waterdrop are the same 76 px) |
| bottom inset | **127 px** = 46.2 dp on our ROM |
| cutout | **100 × 76 px**, top-centre, centre x = 540 = exactly half the width |
| corners | **106 px radius, all four** (38.5 dp) |
| refresh | **30 / 50 / 60 / 90 Hz** (`dumpsys display` supportedModes, 90 active) |

`device-support/rock/` exists and its fallbacks are these measured values.
`RockDisplayProfile` reads live values at runtime; the constants are fallback
only. Frame budget derives from the live rate (all four modes tabulated in
`device-support/rock/README.md`).

## Closed by that paste + the live sweep

- Panel height (2408, not 2400), effective density (430, not 420), top inset
  (76 px, not 190), logical frame (401.9 × 896.0), refresh modes (all four,
  not a 60/90 pair), corners (106 px), SoC platform (mt6789 — an earlier
  "correction" to mt6765 was wrong and is reverted).
- Biometrics: face unlock present (enrolled) + fingerprint + passcode.
- No NFC hardware → NFC settings row removed from the catalog.
- Dock persistence is implemented (`DockPreferences`); the "first four apps
  alphabetically" behavior is gone.
- Grid density decision (5×7) stands; metrics re-derived at the true frame
  (pitch 72.38, row 102, clearance 147, widget 144; icon kept at 56).

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
