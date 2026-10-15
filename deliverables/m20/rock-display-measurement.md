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
| refresh rates | **60 / 90 Hz switchable** | operator-confirmed on the physical unit, 2026-10-15 (spec sheets list a 30 Hz mode that was not observed on this unit) |
| rounded corners | **106 px radius, all four** | `dumpsys display` RoundedCorners (39.4 dp — reserve in fullscreen surfaces) |
| RAM | **3758996 kB (4 GB variant)** | `/proc/meminfo` — the 3 GB budget stands |
| storage | **47 GB userdata, 6.1 GB free (87% used)** | `df` — flag to operator: little room for OTAs/backups |
| battery | **35%, charging on AC, health good** | `dumpsys battery` (level 35, status 2, health 2) |
| stock OS | **Android 14, HyperOS 1.0.8.0** | fingerprint `POCO/rock_p_in/rock:14/.../V816.0.8.0.ULUINXM`, SPL 2024-10-01 — vendor is **A14, not A12** |
| SoC platform | **mt6789** | `ro.hardware` + `ro.board.platform` — Helio G99 IS MT6789; the mt6765 "correction" applied earlier was wrong and is reverted |
| biometrics | **fingerprint only** | `pm list features` (95 entries): `android.hardware.fingerprint` present, **no face/iris** |
| NFC | **absent** | no `android.hardware.nfc` in `pm list features` — NFC settings row removed from catalog |
| measured dpi | **400.88 x 400.88** | `dumpsys display` xDpi/yDpi (vs 401 spec-sheet ppi — agreement, not news) |
| model | **22071219CI (rock_p_in, India)** | `ro.product.model/device/name` |

## What this corrected

Three of our numbers were wrong, and two of them had already been written into
code and into a correction script.

| | was | now | how wrong |
|---|---|---|---|
| panel height | 2400 px | **2408 px** | from a remembered spec sheet |
| density | 420 dpi | **430 dpi effective** (440 physical) | from a remembered spec sheet |
| top inset | 190 px | **76 px** | spec-sheet estimate, **2.5× too large** |
| logical frame | 411.43 × 914.29 dp | **401.9 × 896.0 dp** | inherited from the two errors above |
| refresh rates | 60 / 90 (unconfirmed pair) | **60 / 90 switchable, operator-confirmed** | the pair was right; the confirmation is new |

### The 190 px top inset was the expensive one

`RockDisplayProfile.NOMINAL_TOP_INSET_PX` was 190 px = 72.4 dp. Any layout that
reserved it — and the launcher grid, App Library and Control Center all do —
was reserving **44 dp more than the device needs**. On an 896 dp tall frame
that is 5% of the screen spent on a notch that occupies 28 dp.

### A 30 Hz detour, corrected twice

Spec sheets list a 30 Hz mode for this panel, and it was briefly written into
`FrameBudget`, then removed again the same day as "never observed". The live
`dumpsys display` settled it: 30.000002 Hz is a real advertised mode (along
with a 50 Hz mode nobody had mentioned). Lesson recorded: spec sheets were
right, the removal was hasty, and only the device ends the argument.

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

## Live sweep 2026-10-15 — device connected over adb

The operator connected the unit. Everything above was re-confirmed live, and
the sweep added the table in the previous section plus two corrections to
earlier claims in this project:

1. **Refresh modes are 30 / 50 / 60 / 90 Hz** (`dumpsys display`
   supportedModes, all at 1080×2408; alternativeRefreshRates cross-list all
   four). **90 Hz was active at capture** (`mActiveModeId=1`,
   `renderFrameRate 90.0`), default peak 60. The 30 Hz mode some spec sheets
   list was briefly removed from our docs as "unobserved" — wrongly: it is a
   real advertised mode. The 50 Hz mode nobody mentioned is real too.
   `FrameBudget.forRefreshRate()` already derives for any rate; the tables now
   carry all four columns (50 Hz: 20.00 / 16.80 / 24.00 ms).
2. **mt6789 was right all along.** The intake review (§8) and correction C01
   called it the wrong chipset (Dimensity 1080) and "fixed" it to mt6765.
   Both were wrong: Helio G99 is MT6789, and the unit reports mt6789 on
   `ro.hardware` and `ro.board.platform`. C01 is reverted; the review stands
   with this correction appended, not rewritten.

Raw outputs saved at `device-support/rock/measured-2026-10-15/`
(identity.txt, display.txt, hardware.txt, features.txt, cameras-haptics.txt,
flash-props.txt). Read-only sweep — nothing installed, nothing changed on the
device.

## Live sweep, part 2 — hardware, cameras, haptics, flash props

Same session, same read-only rule:

| fact | value |
|---|---|
| face unlock | **PRESENT** — `com.miui.face` installed, `miui.face.FaceService` running, `face_unlock_has_feature=1`, `face_unlock_valid_feature=1`, `lockscreen.options=enable_facelock`, and a face **enrolled** (`settings_face_id_prefix_1="Face 1"`). Software 2D via camera — which is why `pm list features` shows no `android.hardware.face`. Fingerprint HW also present. The "fingerprint-only" rename applied earlier today was wrong and is reverted. |
| cameras | **5 logical** (1 front, 4 back entries), **FULL** hardware level |
| haptics | single motor, effects CLICK/DOUBLE_CLICK/TICK/HEAVY_CLICK/TEXTURE_TICK, **no primitives, no PWLE** — lightImpact-style taps only, no rich waveforms |
| IR blaster | **present** (`android.hardware.consumerir`) |
| partitions | **A/B** (`ab_update=true`), current slot **_b**, **dynamic partitions** |
| bootloader | **LOCKED** (`flash.locked=1`, vbmeta locked, verifiedbootstate green) — unlocking wipes the phone and needs a Mi account wait; no action taken |
| storage | 47 GB userdata, **6.1 GB free (87% used)** — back up before any flash work |
| locale/tz | en-US, **Asia/Kathmandu** |
| vendor quirk | vendor fingerprint says Android 13/TP1A while system is 14 — recorded as-is, HyperOS versioning is inconsistent |

## Not measured — still open

- **Blur-behind cost on Mali-G57.** A GPU question; nothing here answers it.
- **Kernel patches (6) have never booted.** Still hypotheses.
- **Wallpaper source** (iOS 18 harvest vs. originals) — operator's call.
- **Bootloader unlock.** Still locked; unlocking wipes the phone and userdata
  is 87% full, so back up first. No action taken without the word.
- **A/B + dynamic partitions confirmed** (`ab_update=true`, slot `_b`). The
  eventual flash plan must be A/B-safe (fastbootd / OTA-style), not the old
  `fastboot flash super` sketch.
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