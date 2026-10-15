# device-support/rock — runtime display profile

Device-specific adapters. One APK adapts at runtime instead of forking per
device.

## Why this exists

Every visual number shipped before the 2026-10-15 on-device measurement was
taken from Apple's screenshots (390 pt-class logical screen, 3x) or from the
emulator at an assumed 420 dpi. The measured target (401.9 × 896.0 dp at
effective 430 dpi — see ../README-needs.md) is different in ways that change
layout **and** that cannot be derived from a constant:

| Property | What we used to assume | POCO M5 (rock) |
|---|---|---|
| Panel | 1080×2400 @ 420 dpi | **1080×2408 px**, density 440 physical with a HyperOS override of **430** |
| Logical width | 375–447 pt | **401.9 dp** (1080 / 2.6875) |
| Top inset | 24 dp status bar | **76 px = 28.3 dp** — and on this panel the status-bar band and the waterdrop cutout are the same 76 px |
| Bottom inset | ~24 dp gesture bar | **127 px = 47.3 dp** |
| Cutout | assumed waterdrop | **100 × 76 px, top-centre, centre x = 540** (= exactly 1080/2) |
| Refresh | **60 Hz, hardcoded** | **60 / 90 Hz switchable**, operator-confirmed |
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

| threshold | ratio | 60 Hz | 90 Hz |
|---|---|---|---|
| good frame | 0.84 | 14.00 ms | 9.33 ms |
| jank (deadline) | 1.00 | 16.67 ms | 11.11 ms |
| severe | 1.20 | 20.00 ms | 13.33 ms |

The ratios are the ones already in use at 60 Hz, so **60 Hz behaviour is
bit-for-bit unchanged**. Before this, a 13 ms frame on a 90 Hz panel was
classified "good" while having already blown through an 11.11 ms deadline.

PROVENANCE: **AppleUX-chosen.** Apple publishes a 120 Hz ProMotion cadence but
no jank taxonomy; a threshold set is a measurement policy, not a design token.

## PROVENANCE of the fallback constants

**MEASURED**, 2026-10-15, on the physical POCO M5 running stock HyperOS:
1080×2408 px, density 440 physical with a HyperOS override of **430** (430 is
the effective value), 76 px top inset, 127 px bottom inset, 100×76 px top-centre
waterdrop cutout. Full capture:
`deliverables/m20/rock-display-measurement.md`.

The refresh-rate list is **operator-confirmed**: the panel switches between
**60 Hz and 90 Hz** on the physical unit (2026-10-15). Spec sheets also list a
30 Hz mode, but it was not observed on this unit and is not claimed. To see the
currently active mode: `adb shell dumpsys display | grep -iE 'fps|refresh|mode'`.

These constants are only reached when a platform read fails or returns a
degenerate value — the live values always come from the platform.

### Superseded values, kept here so nobody re-introduces them

| was | now | why it was wrong |
|---|---|---|
| 1080×2400 | 1080×**2408** | remembered spec sheet; `wm size` says 2408 |
| 420 dpi | **430** effective (440 physical) | HyperOS density override was unknown |
| 190 px top inset | **76 px** | spec-sheet estimate, 2.5× too large — it cost 44 dp of vertical space on every layout that reserved it |
| 411.43 × 914.29 dp | **401.9 × 896.0 dp** | inherited all three errors above |
| 60 / 90 Hz (assumed pair) | **60 / 90 Hz switchable (operator-confirmed)** | the pair was right; the confirmation is new. A 30 Hz mode some spec sheets list was briefly claimed here on 2026-10-15 and removed the same day — never observed on this unit |

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
2. **The active refresh mode is still worth confirming on demand.** The 60/90 Hz
   pair is operator-confirmed, but which mode is active by default and under
   load is not recorded. Check with
   `adb shell dumpsys display | grep -iE 'fps|refresh|mode'` when it matters.
3. **The launcher grid metrics were derived on the wrong frame.** The 5×7
   derivation (icon 56 dp, pitch 74.29 dp, row pitch 96 dp) used
   411.43 × 914.29 dp, which came from the wrong panel height and density. At
   the measured 401.9 × 896.0 dp those numbers need re-deriving. This is the
   highest-value open item in the module because it is visible on the first
   screen.
4. **The AVD's geometry is not the device's geometry.** Emulator screenshots
   validate layout logic only — not vertical space, notch clearance, or
   anything that depends on dp scale.