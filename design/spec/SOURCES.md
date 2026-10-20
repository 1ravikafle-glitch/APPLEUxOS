# Design source precedence — which source wins for what

Established 2026-10-15. Three sources now describe iOS 27 surfaces and they
disagree. This file says which one is authoritative for which question, so a
later pass is a diff instead of an argument.

| # | Source | Authoritative for | NOT authoritative for |
|---|---|---|---|
| 1 | **Measured Apple kit** — `design-reference/ios27/ios27-ds/packages/tokens/src/materials.json` | Liquid Glass fills, drop shadows, inner shadows, background blurs, corner radii, colours, typography. Measured from Apple's Figma UI Kit 27.0.2 via REST API, 2026-07-29. | Anything the Figma REST API does not expose — notably the GLASS effect's blur radius. |
| 2 | **files5 / IOSLikeROM** — `design-reference/inbox/files5-IOSLikeROM/` | **Layout.** Real geometry for 11 screens as a tree DSL, verified by `tools/layout_check.py` (prints `OK`). Also the icon glyph set and press/spring motion values. `spec/devices/rock.json` is regenerated from measured device values by `tools/make_profile.py`. | Absolute dp values as-is — they were re-derived for this panel, but a `wm density reset` changes the frame again (see below). Icon corner shape (it uses a rounded rect). Glass material numbers (its `blur: 8` is a CSS backdrop value, not Apple's 34pt radius). |
| 3 | **ios27-android16 baseline** — `design/spec/ios27-baseline.md` | **Inventory and coverage.** Which 630 surfaces exist, grouped into 20 batches, with the AOSP component each maps to. | Every coordinate, radius and duration in it. These are unverified decimal fractions; use files5's layout or measure instead. |

files4 is superseded by files5. files5 additionally ships `check_kotlin.py`, a
`make_profile.py` that regenerates the whole spec from a measured device, and
insets that are wired through rather than assumed.

## Why this order

Source 1 beats 2 and 3 on material because it is the only one whose values can
be checked against Apple rather than reasoned about. Source 2 beats 3 on layout
because 3 has no layout solver and contains at least one hard contradiction
(`brightness` and `toggles` declared on the same rect). Source 3 beats nothing,
but it is the only one that tells us what we have *not* built yet, which is why
it is kept rather than discarded.

## The frame problem — read before using any dp number

**MEASURED 2026-10-15 on the physical POCO M5 — panel geometry only. Density
is OUR ROM's choice: 440 physical, no override.** The 430 figure in that
capture is HyperOS's override, which dies at bootloader unlock and never
applies to this ROM. So a dp value from *any* source is only correct for the
frame it was derived on, and OUR frame is 392.73 × 875.64 dp:

| | px | dpi | dp |
|---|---|---|---|
| panel | 1080 × **2408** | 440 physical (OUR ROM, no override) | **392.73 × 875.64** |
| HyperOS session (dead) | — | 430 override | 401.9 × 896.0 — HyperOS-only, never ours |

Insets, measured in px (density-independent): top **76 px** (27.6 dp on our
ROM), bottom **127 px** (46.2 dp), cutout **100 × 76 px** top-centre, centre
x = 540 = exactly half the width, corners **106 px radius** all four
(38.5 dp). Refresh **30 / 50 / 60 / 90 Hz** (`dumpsys display`
supportedModes, 90 active at capture).

The home-grid numbers previously in this file were derived on the HyperOS
frame and have been superseded; the live derivation is:

| | our 5×7 grid at 392.73 dp wide | how it was derived |
|---|---|---|
| usable width | 392.73 − 2×20 = 352.73 | screen margin |
| cell pitch | **70.55** (352.73 / 5) | binding axis |
| app icon | **56 kept** (56/70.55 = 0.794, under the 0.81 merge limit) | LauncherTokens |
| row pitch | **100** (7 rows = 700, 2.0 dp slack — thin, noted in LauncherTokens) | 875.64 − 27.6 − 146 = 702.0 band |
| widget height | **141** (3 × 70.55 / 1.5) | same formula, true pitch |
| dock radius | **34** | source 1 wins (`REGULAR_CORNER_RADIUS_DP`), not files5's 38 |
| refresh rate | **30 / 50 / 60 / 90**, live read | `Display.getMode()` |

The pattern: take the layout *tree* from files5, re-derive the *numbers* for
the measured frame. Never copy a dp literal across frames.

## Where our implementation already outranks all three

Kept deliberately; do not "fix" these back toward the sources:

- **Icon shape.** files5 and the ios27-android16 spec both use
  `RoundedCornerShape(0.2237 × size)`. `AppIconView.kt` uses Apple's own
  40-segment path scaled from the 1024 pt grid, with n = 4.4 **measured** from
  `App Icon Template.ai` (mean abs pixel error 0.0055 vs 0.0088 for a rounded
  rect). Measured beats inferred.
- **Glass surfaces.** files5 uses CSS corners; the ios27 spec's `LiquidGlass.kt`
  blurs its own window instead of what is behind it. Ours uses the kit's values
  and `FLAG_BLUR_BEHIND`.
- **Frame budget.** files5 assumes a fixed rate. Ours derives good/jank/severe
  thresholds from the live rate — 14.00/16.67/20.00 ms at 60 Hz,
  9.33/11.11/13.33 ms at 90 Hz, 16.80/20.00/24.00 ms at 50 Hz,
  28.00/33.33/40.00 ms at 30 Hz — because the panel advertises all four.
- **Insets.** files5 reads them live in Kotlin and keeps dp values in the
  profile as a fallback. Ours does the same, but the fallback is now measured
  rather than assumed (28.3 / 47.3 dp, previously 72.4 dp).

## Re-derive after editing anything

```bash
# layout (files5) — must print OK
cd design-reference/inbox/files5-IOSLikeROM
python3 tools/make_profile.py --size 1080x2408 --density 430 \
    --top 28.3 --bottom 47.3 --measured
python3 tools/gen_tokens.py && python3 tools/dsl.py && python3 tools/layout_check.py

# inventory + token delta (ios27-android16 baseline)
python3 scripts/parse-ios27-spec.py --spec design/spec/ios27-baseline.md \
                                    --out design/spec/extracted
python3 scripts/compare-glass-tokens.py --in design/spec/extracted/design-tokens.incoming.json
```

`layout_check.py` printing `OK` is a claim about **geometry only**. It says
nothing about colour, provenance, or whether anything binds to an AOSP API.

## Settings has its own layout reference

`design/spec/settings-layout-reference.md` + the board it describes,
`design-reference/inbox/settings-aosp16-ios27-tree.html` (136 screens,
machine-checked: 186 nav refs all resolve, 38/38 icons defined, one
self-parent defect fixed in our copy). Row anatomy, metrics and a 59-entry
backend map, five of which are flagged against `frameworks/base`. Use it for
every settings row built from here on.

## Unresolved

- `design-reference/inbox/files5-IOSLikeROM/AGENT_HANDOFF.md` § Known gaps is
  accurate and still applies: the prototype was **never compiled or run** in
  that sandbox, and several Compose dependency names remain unverified.
- files5 covers **11 screens**. The inventory's 630 surfaces have no layout for
  619 of them. That gap is the work.
- Apple publishes no numeric spec for Liquid Glass. Where source 1 has a number
  it is the *designer's* value from the kit, not a runtime parameter.
- Fullscreen `FLAG_BLUR_BEHIND` cost on this panel's GPU is still unmeasured.