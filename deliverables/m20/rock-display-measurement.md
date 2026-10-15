# rock display geometry — MEASURED

Date measured: 2026-10-15
Device: physical POCO M5, stock **HyperOS** (the operator's own unit)
Captured by: operator, pasted from their own shell
Status: **authoritative**. Supersedes every earlier figure in this project.

```
adb shell wm size
adb shell wm density
adb shell dumpsys window | grep -iE 'statusBars|navigationBars|displayCutout'
```

## Measured values

| property | value | source line |
|---|---|---|
| panel | **1080 × 2408 px** | `Physical size: 1080x2408` |
| density, physical | **440 dpi** | `Physical density: 440` |
| density, override | **430 dpi** | `Override density: 430` |
| effective density | **430 dpi** → 2.6875 | the override is what `DisplayMetrics` reports |
| logical frame | **401.9 × 896.0 dp** | 1080/2.6875, 2408/2.6875 |
| status bar / top inset | **76 px** = 28.3 dp | `InsetsSource type=statusBars frame=[0,0][1080,76]` |
| navigation bar / bottom inset | **127 px** = 47.3 dp | `InsetsSource type=navigationBars frame=[0,2281][1080,2408]` |
| cutout | **100 × 76 px, top-centre waterdrop** | `boundingRect=… Rect(490,0 - 590,76)` |
| cutout path | `M 0,0 H -50 V 76 H 50 V 0 H 0 Z` | `cutoutPathParserInfo.cutoutSpec` |
| cutout centre x | **540** = exactly 1080/2 | confirms top-centre, not offset |
| mandatory gesture inset | 32 px = 11.9 dp | `mDisplayCutoutTouchableRegionSize=32` |
| refresh rates | **30 / 60 / 90 Hz** | po.co + GSMArena; not in this capture |

## What this corrected

Three of our numbers were wrong, and two of them had already been written into
code and into a correction script.

| | was | now | how wrong |
|---|---|---|---|
| panel height | 2400 px | **2408 px** | from a remembered spec sheet |
| density | 420 dpi | **430 dpi effective** (440 physical) | from a remembered spec sheet |
| top inset | 190 px | **76 px** | spec-sheet estimate, **2.5× too large** |
| logical frame | 411.43 × 914.29 dp | **401.9 × 896.0 dp** | inherited from the two errors above |
| refresh rates | 60 / 90 | **30 / 60 / 90** | 30 Hz was missing entirely |

### The 190 px top inset was the expensive one

`RockDisplayProfile.NOMINAL_TOP_INSET_PX` was 190 px = 72.4 dp. Any layout that
reserved it — and the launcher grid, App Library and Control Center all do —
was reserving **44 dp more than the device needs**. On an 896 dp tall frame
that is 5% of the screen spent on a notch that occupies 28 dp.

### The 30 Hz case was missing entirely

`FrameBudget` derives correctly from any rate, so nothing was broken, but the
documentation and every consumer's assumption said "60 or 90". On a panel
sitting at 30 Hz the real deadline is **33.3 ms**, not 16.67 ms, so a
consumer that assumes "at least 60" mis-classifies frames on the slowest mode.
`MIN_KNOWN_REFRESH_HZ = 30f` is now in the profile and the derivation table
carries the 30 Hz column.

## Density: which one to use, and how to flip it

HyperOS ships a density **override**. `DisplayMetrics` reports the override, so
every dp conversion in this codebase must use **430**.

| density | frame | when |
|---|---|---|
| **430** (effective, override) | 401.9 × 896.0 dp | what the app actually sees today |
| 440 (physical panel) | 392.7 × 875.6 dp | after `adb shell wm density reset` |

To model the no-override case:

```bash
cd design-reference/inbox/files5-IOSLikeROM
python3 tools/make_profile.py --size 1080x2408 --density 440 \
    --top 27.6 --bottom 46.2 --measured
python3 tools/dsl.py && python3 tools/layout_check.py
```

`layout_check.py` currently prints **OK: no layout problems in 11 screens** at
401.9 × 896.0.

## Not measured — still open

- The refresh rate was not in this capture. `wm density` and `dumpsys window`
  do not report it. To confirm 30/60/90 and see which mode is active:
  `adb shell dumpsys display | grep -iE 'fps|refresh|mode'`
- Whether the 430 override survives a reboot, or is applied per-boot by
  HyperOS. If it is transient, the effective density is 440 and the frame is
  392.7 × 875.6 dp.
- Behaviour under rotation; `ROTATION_90` reports the nav bar as 127 px on the
  right edge, so the bottom inset is not the only inset that matters.

## Consequence for the emulator

The AVD's geometry is **not** this device's geometry. Any emulator screenshot
is a valid check of layout logic and of nothing else — in particular it cannot
validate vertical space, notch clearance, or dp-scale-dependent sizing. See
`design/spec/SOURCES.md` § Frame problem.