# device-support/rock — runtime display profile

Device-specific adapters. One APK adapts at runtime instead of forking per
device.

## Why this exists

Every visual number shipped before the 2026-10-15 on-device measurement was
taken from Apple's screenshots (390 pt-class logical screen, 3x) or from the
emulator at an assumed 420 dpi. The measured target (392.73 × 875.64 dp at
OUR ROM's 440 dpi, no override — see ../README-needs.md) is different in ways
that change layout **and** that cannot be derived from a constant:

| Property | What we used to assume | POCO M5 (rock), OUR ROM |
|---|---|---|
| Panel | 1080×2400 @ 420 dpi | **1080×2408 px @ 440 dpi, no override** (HyperOS's 430 dies at unlock) |
| Logical width | 375–447 pt | **392.73 dp** (1080 / 2.75) |
| Top inset | 24 dp status bar | **76 px = 27.6 dp** — and on this panel the status-bar band and the waterdrop cutout are the same 76 px |
| Bottom inset | ~24 dp gesture bar | **127 px = 46.2 dp** |
| Cutout | assumed waterdrop | **100 × 76 px, top-centre, centre x = 540** |
| Corners | square (assumed) | **106 px radius, all four** (38.5 dp) |
| Refresh | **60 Hz, hardcoded** | **30 / 50 / 60 / 90 Hz**, 90 active at capture |
| RAM | 15 GB host | 4 GB device, 3 GB budget |
| GPU | SwiftShader (emulator) | Mali-G57 MC2 |

A 60 Hz assumption is wrong half the time on the very device we are building
for, and a jank threshold derived from it is wrong the other half. The 30 Hz
mode is worse still: the real deadline there is 33.3 ms, not 16.67 ms.

PROVENANCE: the panel, density, inset and cutout figures above are **MEASURED**
— pasted by the operator on 2026-10-15 from the physical unit running stock
HyperOS. Full capture and the corrections it forced:
`deliverables/m20/rock-display-measurement.md`. The refresh-rate list is from
the published panel specification, not from that capture.

## What is here

| File | What it does |
|---|---|
| `RockDisplayProfile.kt` | Reads cutout safe insets, the **live** refresh rate, density, panel size and RAM at runtime. Falls back to nominal rock constants only when a platform read fails. |
| `FrameBudget.kt` | Derives good / jank / severe frame thresholds from the refresh rate: `1000/Hz`, good `0.84×`, severe `1.20×`. |
| `Android.bp` | `android_library` → `appleux-device-support-rock`. |

Sources are symlinked into
`appleux/design-system/src/main/java/com/appleux/device/` so BOTH build paths
compile the same code: the Gradle-free `scripts/build-emulator-apks.sh` path
(which walks the design-system source tree) and the Soong path (`static_libs`
in `appleux/design-system/Android.bp`).

## Consumers

- **`appleux/design-system` → `AppleuxDisplayInsets`** applies the cutout inset.
  Used by the home-screen grid, the App Library and the Control Center.
  **The Notification Center still needs it** — see below.
- **`appleux/performance` → `AppleuxPerformanceController`** reads the live
  refresh rate at `start()` and re-derives the thresholds; a
  `DisplayManager.DisplayListener` re-reads it whenever the panel changes mode.

## Derivation (frame budget)

| threshold | ratio | 30 Hz | 50 Hz | 60 Hz | 90 Hz |
|---|---|---|---|---|---|
| good frame | 0.84 | 28.00 ms | 16.80 ms | 14.00 ms | 9.33 ms |
| jank (deadline) | 1.00 | 33.33 ms | 20.00 ms | 16.67 ms | 11.11 ms |
| severe | 1.20 | 40.00 ms | 24.00 ms | 20.00 ms | 13.33 ms |

The ratios are the ones already in use at 60 Hz, so **60 Hz behaviour is
bit-for-bit unchanged**. Before this, a 13 ms frame on a 90 Hz panel was
classified "good" while having already blown through an 11.11 ms deadline.

PROVENANCE: **AppleUX-chosen.** Apple publishes a 120 Hz ProMotion cadence but
no jank taxonomy; a threshold set is a measurement policy, not a design token.

## PROVENANCE of the fallback constants

**MEASURED**, 2026-10-15, on the physical POCO M5 (panel geometry; the 430 dpi
figure in that capture is HyperOS's override, which dies at bootloader unlock
and never applies to this ROM). OUR ROM: 1080×2408 px @ 440 dpi, 76 px top
inset, 127 px bottom inset, 100×76 px top-centre waterdrop cutout, 106 px
corners. Full capture: `deliverables/m20/rock-display-measurement.md`.

The refresh-rate list is **measured**: `dumpsys display` supportedModes lists
30.000002, 50.0, 60.0 and 90.0 Hz, with 90 active at capture (default peak 60).
To see the currently active mode: `adb shell dumpsys display | grep -iE 'fps|refresh|mode'`.

These constants are only reached when a platform read fails or returns a
degenerate value — the live values always come from the platform.

### Superseded values, kept here so nobody re-introduces them

| was | now | why it was wrong |
|---|---|---|
| 1080×2400 | 1080×**2408** | remembered spec sheet; `wm size` says 2408 |
| 420 dpi, then HyperOS 430 | **440, no override (OUR ROM)** | 420 was a spec sheet; 430 was HyperOS's override, which dies at unlock |
| 190 px top inset | **76 px** | spec-sheet estimate, 2.5× too large — it cost 44 dp of vertical space on every layout that reserved it |
| 411.43 × 914.29 dp, then 401.9 × 896.0 | **392.73 × 875.64 dp** | both earlier frames inherited wrong density assumptions |
| 60 / 90 Hz (assumed pair) | **30 / 50 / 60 / 90 Hz measured** | live `dumpsys display` lists all four; 90 active at capture |

## Known gaps

1. **Notification Center does not reserve the cutout yet.**
   `AppleuxNotificationShade` sets its own padding in `init` and no parent
   applies an inset to it. Applying one requires a one-line edit to
   `AppleuxNotificationShade.kt`, which was outside this change's ownership.
   The one-liner, for whoever owns that file:

   ```kotlin
   // in AppleuxNotificationShade.init, after setContentView-equivalent setup
   AppleuxDisplayInsets.applyTopInset(context, column)
   ```

   `AppleuxDisplayInsets` is already public in the design system and already
   applied to the Control Center, so nothing else is needed.
2. **The active refresh mode is worth re-checking on demand.** All four modes
   are confirmed advertised, 90 was active at capture, default peak 60. Check
   with `adb shell dumpsys display | grep -iE 'fps|refresh|mode'` when it matters.
3. **The launcher grid metrics are done at OUR ROM's frame.** The 5×7
   derivation (icon 56 dp kept, pitch 70.55 dp, row pitch 100 dp, clearance
   146 dp, widget 141 dp) is in LauncherTokens at 392.73 × 875.64 dp. Watch
   two thin slacks on-device: label slack 4.5 dp ("Calculator" in a 70.55 dp
   cell) and row slack 2.0 dp.
4. **The AVD's geometry is not the device's geometry.** Emulator screenshots
   validate layout logic only — not vertical space, notch clearance, or
   anything that depends on dp scale.