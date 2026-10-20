# DESIGN TRUTH — how the design authority decides

> Owner: **C** (design authority, Master Prompt §12b).
> Established 2026-10-20. Supersedes the source-precedence table in
> `design/spec/SOURCES.md` where the two disagree; SOURCES.md remains the
> inventory of *what materials exist*, this file is *who wins a question*.

## 1. Why this file exists

Until 2026-10-20 the design authority validated surfaces against
**screenshot-driven truth only**: device captures and the operator's
ground-truth photos in `design-reference/iphone11-ios27/`.

Pixel evidence is the right instrument for **rendered appearance** — glass,
blur, shadow, proportion, spacing between two visible objects. It is the
wrong instrument for **published spec**. A screenshot cannot resolve:

- whether Title 2 tracking is −0.26 pt or +0.26 pt (a *sign* error),
- a 0.04 pt tracking difference,
- whether a translucent colour's alpha is 0.30 or 0.70,
- Apple's published touch-target minimum, Dynamic Type leading, or the exact
  tints of the iOS 27 palette,
- what Apple actually shipped, as opposed to what a screenshot of one device
  in one appearance shows.

So the truth base is now **four independent sources**, each with its own
authority scope, and every token in the ROM is checked against the sources
competent for that kind of fact. The harness is `scripts/design_truth.py`;
the machine-readable ledger is `design-truth/conflicts.json`; the readable
one is `design-truth/CONFLICTS.md`.

## 1b. The HIG also publishes RULES, not just values

Most of this document is about *values* — the kit and the HIG tables. But
the HIG publishes **hierarchy rules** that no value table can express, and
those rules are checkable against our token usage:

> iOS defines two sets of dynamic background colors — *system* and *grouped*
> — each of which contains primary, secondary, and tertiary variants… use the
> **grouped** background colors when you have a grouped table view; otherwise
> use the **system** set. Primary for the overall view, secondary for
> grouping content within the overall view, tertiary for grouping content
> within secondary elements.
> — HIG → Color, "Background colors"

Our Settings root satisfies it and the harness can see it: the **view** uses
the grouped set (`systemGroupedBackground`, #F2F2F7 light) and the **cards**
use the system set (`systemBackground`, #FFFFFF light). A surface that got
this wrong would put the grouped colour on the card and the system colour on
the view, and the two would be a step apart in the wrong direction.

This is the pattern for the rest of the model: **the kit supplies the values,
the HIG supplies the rules, the photos supply the rendered result, and the
shipped artefacts supply what is actually in the build.**

## 2. The four sources

| # | Source | Where it comes from | Authority | Decides |
|---|---|---|---|---|
| S1 | **web-hig** | Apple's HIG, harvested as DocC markdown (165/165 articles) into `design-reference/ios27/web/hig/` with a sha256 + HTTP-status manifest | published spec | typography metrics, tracking tables, layout/spacing tables, component anatomy, behaviour rules, published tint RGBs |
| S2 | **figma-kit** | Apple's iOS 27 Figma UI Kit 27.0.2, extracted via the Figma REST API into `design-reference/ios27/ios27-ds/packages/tokens/src/` | design kit | materials, glass, colour, radii, token values |
| S3 | **ipsw-artwork** | The iOS 27 IPSW asset harvest in `extracted/` (591 asset bundles, 4 290 readable SVG viewBoxes, 492 raster assets) | shipped artefact | what Apple actually shipped: artwork geometry, asset sizes, module inventory |
| S4 | **screenshot** | Ground-truth photos + device captures | rendered appearance | glass, blur, shadow, proportion, light/dark *rendering* |
| S5 | **kit-recipe** | `design-reference/ios27-design-system/` — an **MIT-licensed** community clone (© 2026 Dcode Labs / Seunghan Kim) of Apple's iOS 27 UI Kit, measured from the Figma REST API (kit 27.0.2, file `1he05dQDLN4F7TH3bAB3uk`). Its `packages/tokens/src/` is a **second copy of S2** (the same kit values, same authority). Its `packages/metadata/**` (36 component specs, 25 iPhone page recipes) is measured from a **DIFFERENT Figma file** (`pDmGXdYu2k8xlf1SQoU9PW`) and is therefore **examples of iOS-style screens, not Apple's published values** | design recipe (examples only) | screen **structure** — what a surface is made of; never the *values* of a surface S4 can measure |

**S5's rule (C, 2026-10-21, after the clone landed).** A recipe can SHOW a
structure; it cannot SET a value where S4 can measure one. So:
- where the recipe and a GT photo describe the same shipped screen and
  disagree, **the photo wins** (the rendered-appearance scope) and the
  recipe is recorded as a **divergence with its device class named** — not
  silently ignored, and not silently adopted;
- the recipe's TOKEN files are S2 (they already are, and our values agree);
- the recipe's page recipes are the best available statement of **structure**
  for screens we have no photo of (e.g. a tab bar's anatomy), and C may use
  them that way;
- the recipe is the **cleanest source we have** on licence grounds (MIT), so
  unlike `apple-design-resources/ios27/` (restricted) and `Solid-Glass/` (no
  licence) it may be used directly.

Two further things are **subjects under test**, never sources:

- **rom** — the AppleUXOS implementation (`ColorTokens.kt`, `colors.xml`,
  `TypographyTokens.kt`, …).
- **repo-spec** — the design spec this repo carries
  (`design/spec/ios27-baseline.md` → `design/spec/extracted/`).

The rule is absolute: *the implementation never justifies itself.* A value
that only the ROM has is **unverified**, not correct.

## 3. Precedence is scoped, not global

| Scope | Order | Why |
|---|---|---|
| typography | web-hig → figma-kit | Apple publishes the type ramp and the tracking table; the kit agrees |
| spacing | web-hig → figma-kit | same |
| accessibility | web-hig | only Apple states touch targets and Dynamic Type behaviour |
| material | figma-kit | Apple publishes **no numeric Liquid Glass spec**; the kit is the only source |
| color | figma-kit → web-hig | the kit carries alpha; the HIG's tint table carries exact RGB |
| radius | figma-kit → web-hig | |
| shipped-artwork | ipsw-artwork | only the shipped OS knows what it shipped |
| shipped-inventory | ipsw-artwork | same |
| rendered-appearance | screenshot | a spec table cannot tell you what a material looks like |

Where two sources describe *different tokens*, neither wins and the question
goes to the device screenshot. Example: HIG's `separator` semantic
(rgba(60,60,67,0.36)) and the kit's `separators.nonOpaque` swatch
(#000000 @ 0.12) are not the same colour. The ROM uses the HIG semantic;
the kit's Liquid-Glass-era value is a candidate. **Open question, not a
defect** — see §6.

## 4. What the harness does

```bash
python3 scripts/design_truth.py web     # harvest Apple web truth (network)
python3 scripts/design_truth.py cross   # merge all sources, adjudicate, write the ledger
python3 scripts/design_truth.py report  # re-render design-truth/CONFLICTS.md
```

`cross` is offline and deterministic. It merges every source into a single
fact store (`design-truth/facts.json`), compares each fact against the
sources competent for its scope, and emits:

- **conflicts** — subject disagrees with the authority;
- **agreements** — subject matches the authority;
- **unverified** — subject has a value but no competent source.

A colour fact is decoded **twice**: once as Android actually renders it
(`#AARRGGBB`, per `Color.parseColor` and the resource loader) and once as
the author wrote it (CSS `#RRGGBBAA`). If the rendered value disagrees with
the authority but rotating the alpha byte back into place reproduces it
exactly, the ledger classifies it as an **encoding** defect — the colour is
right, the bytes are in the wrong order for the consumer. If the author
intent itself disagrees, it is a **value** defect. Those need different fixes.

## 5. What it found on 2026-10-20 (first full pass)

Baseline: 296 facts, **66 conflicts**. After correction: 248 facts,
**0 conflicts**, 158 agreements, 61 unverified.

### 5.1 Encoding defect — every translucent colour was mis-decoded

**26 literals across 5 files.** Apple publishes translucent colours as
`#RRGGBBAA`; Android reads `#AARRGGBB` with no rearrangement
(`frameworks/base/graphics/java/android/graphics/Color.java`, verified in
this tree). Copying a HIG value across unchanged puts alpha in the red
channel and the alpha byte in blue.

| Token | Written | Android rendered | Should be |
|---|---|---|---|
| `secondaryLabel` light | `#3C3C4399` | A=0x3C R=0x3C G=0x43 B=0x99 | A=0x99 RGB(60,60,67) |
| `tertiaryLabel` light | `#3C3C434D` | A=0x3C RGB(60,67,77) | A=0x4D RGB(60,60,67) |
| `quaternaryLabel` light | `#3C3C432E` | A=0x3C RGB(60,67,46) | A=0x2E RGB(60,60,67) |
| `separator` light | `#545458A6` | A=0x54 RGB(84,88,166) | A=0x5C RGB(60,60,67) |
| `systemFillTertiary` light | `#7676801F` | A=0x76 RGB(118,128,31) | A=0x1F RGB(118,118,128) |
| `secondaryLabel` dark | `#EBEBF599` | A=0xEB RGB(235,245,153) | A=0x99 RGB(235,235,245) |

The dark label ramp is the worst case: **all three steps rendered at 92 %
alpha with a yellow cast** instead of 70 / 30 / 16 % in near-white. The fill
ramp rendered at 47 % alpha in olive instead of 8–20 % in neutral grey.

Why no tool caught it: `tools/check-token-drift.py` compares the Kotlin
palette against the XML palette **as strings**, and both sides carried the
same transposition, so they agreed. A screenshot would show "the secondary
labels look slightly off" and nobody could say why. The fix is a byte
rotation, plus a documented rule that every 8-digit literal in this project
is `#AARRGGBB`.

### 5.2 Value defect — the separator ramp was inverted

`values/colors.xml` (light) held `#545458A6` and `values-night/colors.xml`
(dark) held `#3C3C435C` — the two modes' separators were swapped. Each
file's own comment states the opposite of what the file contains
("HIG light separator is rgba(60,60,67,0.36) = #3C3C435C" above the dark
value). The Kotlin token had the same swap, so the drift tool saw agreement.
Corrected to light = rgba(60,60,67,0.36), dark = rgba(84,84,88,0.65).

### 5.3 Value defect — the palette was two major versions stale

The ROM carried the pre-iOS-26 UIKit tints (`#007AFF` blue, `#FF3B30` red,
`#30B0C7` teal, `#5856D6` indigo, `#AF52DE` purple, `#32ADE6` cyan,
`#64D2FF`/`#5AC8FA` teal) while **the live HIG and the iOS 27 Figma kit
both publish the iOS 26/27 unified palette** (`#0088FF`, `#FF383C`,
`#00C3D0`, `#6155F5`, `#CB30E0`, `#00C0E8`, `#00D2E0`). The HIG's own
changelog records "June 9, 2025 — Updated system color values"; the kit's
own note says the values are unchanged from the iOS 26 kit.

The ROM's `ColorTokens.kt` claimed the values were "transcribed from Apple
HIG → Color". That claim was false: the live page publishes different
values. All tints, the fill ramp, the label-ramp alphas and the dark
tertiary grouped background were corrected to the kit/HIG values, and the
provenance headers now name both sources and the cross-check that guards them.

### 5.4 Value defect — six tracking values were wrong

`TypographyTokens.kt` described its table as the HIG Dynamic Type table
"transcribed verbatim". It was not. Cross-checked against the live HIG
SF Pro tracking table and the kit (which agree):

| Style | Was | Is | Note |
|---|---|---|---|
| Title 1 | +0.34 | **+0.38** | |
| Title 2 | +0.26 | **−0.26** | sign inverted |
| Title 3 | +0.24 | **−0.45** | |
| Callout | −0.32 | **−0.31** | |
| Footnote | −0.09 | **−0.08** | |
| Caption 2 | +0.07 | **+0.06** | |
| Large Title, Headline, Body, Subhead, Caption 1 | — | — | already correct |

The emphasised variants carried the same errors and were corrected
identically. C1 (the 2026-10-19 body-tracking fix, −0.0253 em) is unaffected
and remains correct: 17 pt → −0.43 pt → −0.0253 em.

### 5.5 Value defect — the repo's own design spec was stale

`design/spec/ios27-baseline.md` claims HIG provenance
(`"typography.largeTitle": {"source":"hig","inferred":false}`) but carried
tracking values from an older Apple table (0.374 / 0.364 / 0.352 / 0.38 /
−0.408 …) and the stale pre-iOS-26 palette. It is now corrected and
regenerated, and the harness checks it as a **subject** so it cannot silently
drift again.

## 5c. C8 — closing the unverified list (2026-10-20)

Working the unverified list found three things a per-value comparison
would not:

1. **The ROM was missing the whole UIKit grey ramp.** Apple publishes
   `systemGray` … `systemGray6`; HIG's colour article gives each one's exact
   RGB in the swatch alt-text and the kit's `grays` group carries the same
   six pairs. We had `systemGray` and a `systemGray7` that is not an Apple
   name and held **systemGray3's** colours. `systemGray2..systemGray6` are
   now real tokens, `systemGray7` is a documented legacy alias of
   `systemGray3` (so its one consumer keeps working unchanged), and
   `systemBrown` — declared by the spec, never implemented — now exists.
2. **The spec was stale where the ROM was right.** The baseline carried the
   Liquid Glass fill at alpha 0.14, the rim at 0.28 and the shadow at 0.35;
   the kit measures **0.70 / 0.10 / 0.25 light / 0.45 dark**. `LiquidGlassTokens`
   has always had the measured values — the *spec* was the wrong side.
3. **A new invariant, `invariant.specColoursImplemented`,** compares every
   colour the design spec declares against `ColorTokens`. It reported **20
   missing on its first run** and is 0 now. It is the check that would have
   caught the stale spec.

The 43 values that remain unverified each carry their **reason** in the
ledger rather than being a bare list: Apple publishes no numeric values for
the Liquid Glass material (the kit's own words); Apple publishes only
`separator` and `opaqueSeparator`; the kit publishes no quaternary
background level; no pressed tint is published anywhere; and the 27
emphasised type metrics are **inherited, not measured** — HIG publishes one
SF Pro tracking table and the kit one letter-spacing per style, so both
sources use the regular table for the emphasised weight.

## 6. Open questions

| # | Question | Status | Owner |
|---|---|---|---|
| Q1 | Which separator does iOS 27 ship — HIG's `separator` semantic (rgba(60,60,67,0.36)) or the kit's `separators.nonOpaque` (#000000 @ 0.12)? | **RESOLVED 2026-10-20.** B measured the iOS 27 row hairline at **#E7E7E8** over a white card from `settings-root-light-top.png` (2px, x=400, y=666/776/886/996) = black at ~9.4% alpha. HIG's semantic composites to #B9B9BD — 47 levels too dark. The kit's value composites to #E0E0E0, 7 levels off a downscaled capture. **The kit value is now the token; the HIG semantic alias is retired.** | closed (B measured → C applied) |
| Q2 | Are the emphasised tracking values right to inherit the regular SF Pro table? | **OPEN.** HIG publishes one SF Pro tracking table; the kit exposes one letter-spacing per style with separate weight names. Both sources use the regular table, so the ROM does too — an inherited assumption, not a measurement. | Apple, next HIG revision |
| Q3 | Is the iOS 26/27 unified palette also the shipped UIKit `system*` set? | **RESOLVED 2026-10-20** (see Q5): The *published* tint table and the kit agree with each other and the ROM now follows them. What B measured is the *rendered* accent: #3F8FF7 on the Wi-Fi and Cellular icon squares, which is not any published token. See Q5. | closed for the token, open for the material |
| Q4 | 43 ROM values have no competent source (16 colour, 27 typography) | **OPEN, each with a stated reason.** 59 → 43 on 2026-10-20: the kit's grey ramp, brown and the opaque separator closed 14; the spec's stale glass alphas were corrected. What remains is genuinely unpublished or inherited. | `separatorSecondary`/`Tertiary`, `systemBackgroundQuaternary`/`Quinary`, `systemGray7`, `systemBluePressed`, the liquid-glass ramp, and the emphasised type metrics are AppleUX-chosen or unconfirmed. Ledgered in `DESIGN_SYSTEM.md` §14. | C, next pass |
| Q5 | iOS 26/27 render tints as **materials**: the Settings icon squares read #3F8FF7 while the published token is #0088FF. | **CLOSED 2026-10-20.** B ran a better test than the one I proposed: one iOS 27 frame carries a flat blue *text link* (#0091FF) AND a blue *material* tile (#0061F0 dark / #3F8FF7 light). The flat link samples #0091FF = our `systemBlue` dark, exactly. So the flat token family is right; #3F8FF7 is the **material**. B retracted the earlier claim. The ledger now carries the material rows separately (no ROM counterpart) and the flat link as the token comparison. The remaining difference is a rendering gap, not a palette gap. | closed (B measured → C applied) |
| Q6 | **4** colour literals remain in component/app Kotlin outside the token layer (34 → 6 → 4). | B's `AppleuxLibraryFolder.kt` (`0x59 shl 24` = black @ 35 %) is now tokenised as `ColorTokens.black35` (#59000000) and waits on B's one-line swap; the two `systemui` rows (the notification accent `0xFF0A84FF`, the home-indicator ink `0xFFFFFFFF`) are **A's**; the design-system `AppleuxNotificationBanner.kt` `0xE6 shl 24` is **C's** and is deliberately mode-independent (a banner scrim is white-on-dark whatever the appearance). |
| Q8 | Is the dock's **visible** band narrower than the ROM's own layout bar because the scrim fades before the bar's edges, or because the Oct-19 baseline build differs from the current tree? | **OPEN — one screenshot.** **GT (authority):** the glass is a rounded rectangle whose corner arcs pin the straight sides: left x=32 px = **16.0 pt**, right x=794 px = 397 pt → **381 pt wide, 16 pt inset per side** (92 % of the panel), 104 pt tall. The wallpaper differs out to 11.5 pt per side — the **soft falloff beyond the glass**, which is what B's read (391 pt / 11 pt) measured. **ROM:** the **layout** bar is 316 dp (80 %, a 38.4 dp inset — the width was an accident of the icon metrics, so nothing stated it); the **visible** band B measured on the Oct-19 *baseline* is 244 dp (56 %), i.e. 56 dp narrower than the bar it belongs to. Either the scrim fades ~28 dp before each edge, or the baseline's metrics differ from the tree's (the dock icon moved 56 → 60 dp in `e4bcdc44`). **@B:** a current-build screenshot of the dock settles it; C states the target either way (`LauncherTokens.DOCK_BAND_INSET_H_DP = 16f`) | B (one screenshot) → A (implements) |
| Q8 | Is the dock's **visible** band narrower than the ROM's own layout bar because the scrim fades before the bar's edges, or because the Oct-19 baseline build differs from the current tree? | **OPEN — one screenshot.** The GT **glass** is 381 pt wide with a 16 pt inset (92 % of the panel; the rounded rectangle's symmetric corner arcs pin the straight sides at x=32px and x=794px, identical in both GT frames). The ROM's **layout** bar is 316 dp (80 %); the **visible** band B measured on the baseline is 244 dp (56 %). The visible band is therefore 56 dp narrower than the bar it belongs to. Either the glass scrim fades ~28 dp before each edge, or the baseline build's metrics differ from the tree's (the dock icon moved 56 → 60 dp in `e4bcdc44`). **@B:** a current-build screenshot of the dock settles it; C states the target either way (`LauncherTokens.DOCK_BAND_INSET_H_DP = 16f`) | B (one screenshot) → A (implements) |
| Q7 | Does iOS 27 render the Settings section header in CAPS or sentence case? | **CLOSED 2026-10-20** — B's visual read: sentence case, and the Settings ROOT has no group headers at all (groups are separated by 35 pt of page background). The caps are removed from `settings_section_header.xml`. | No HIG article and no User Guide page specifies a section-header style, and the programmatic glyph-top measurement of the GT photo was inconclusive. The **tracking** is corrected to the published Footnote value (-0.0062em). The **case** needs a visual read. | B (one look) → C | **OPEN.** The design-system layer is clean (the invariant guard reports it). The rest live in component/app files that other lanes own; C supplies the correct token values, the file owner applies them. | A/B (file owners) |

## 6a. What B's device measurement proved about the encoding defect

B measured our rebuild-5 Settings label as **#D1D3E7** and called it "blue-tinted".
That number is not a judgement — it is the **exact composite of the
mis-decoded token**, and it confirms the static diagnosis to the byte:

```
mis-decoded secondaryLabel  #3C3C4399  ->  A=0x3C, RGB(60, 67, 153)
composited over #FFFFFF at a=60/255=0.235
  R = 255 - (255-60)  * 0.235 = 209 = 0xD1
  G = 255 - (255-67)  * 0.235 = 211 = 0xD3
  B = 255 - (255-153) * 0.235 = 231 = 0xE7
  => #D1D3E7          <- exactly what B measured on the device
```

And the corrected token reproduces the ground truth:

```
corrected secondaryLabel    #993C3C43  ->  A=0x99, RGB(60, 60, 67)
composited over #FFFFFF at a=153/255=0.60
  R = 255 - (255-60) * 0.60 = 138 = 0x8A
  G = 255 - (255-60) * 0.60 = 138 = 0x8A
  B = 255 - (255-67) * 0.60 = 142 = 0x8E
  => #8A8A8E   vs   GT #8A8A8D   (1 level of photo-sampling noise)
```

Two independent methods — static byte-order analysis and a device pixel
sample — agree. The label ramp is now in the ledger as a *rendered*
comparison (`design-truth/gt-measurements.json`), not just a token
comparison, so this class of check is repeatable.

## 6b. A second defect the token layer could not reach

`AppleuxTheme.kt` held **18 frozen hex literals** — a second copy of the
palette, in the pre-iOS-26 values (#007AFF blue, #32D74B green, #D1D1D6
separator, #F5F5F7 page background). It is the object the Settings page and
the launcher read (`SettingsListActivity.kt:45` → `AppleuxTheme.background`),
so **no token correction could ever reach those surfaces** — B's measurements
were of the literals, not of the tokens. Master Prompt §12b.2 requires every
measurable value to stay tokenized and §7 forbids a second implementation of
the same visual component, so the object now resolves from `ColorTokens` and
the 18 literals are gone. The harness reports the invariant
`invariant.noColorLiteralsOutsideTokens` so a literal cannot quietly return.



## 7. Rules that follow from this

1. **Never let a screenshot be the only evidence for a published number.**
   If a value is a spec, cite the spec; the screenshot confirms rendering.
2. **A value with no source is unverified.** Record it in
   `DESIGN_SYSTEM.md` §14 (deviation log) or find a source for it.
3. **Every 8-digit colour literal in this project is `#AARRGGBB`.**
4. **Do not trust a transcription.** The first thing to check on a
   hand-transcribed Apple value is whether it still matches Apple's live
   publication — and whether a second source agrees.
5. **Re-run the harness after any token edit.** It is offline and takes
   seconds; a silent drift in the palette is exactly the failure mode it
   was built for.

## 7a. The chrome tokens are verified too (2026-10-20)

B's samples of the detail-page chrome are first-class ledger facts, not prose
in a spec: the disc fill and rim match **exactly** (#F3F3F7 / #1F1F1F and
#F7F7FC / #202020), and the chevron matches within an **ink** tolerance —
our token is pure #000000 while the darkest core pixel of a rendered glyph
in a downscaled photo reads #0C0C0D. That 13-level gap is a rendering
artefact, not a colour difference, so the ledger carries a per-measurement
*ink* tolerance for the chevron and an exact match for the disc. A single
global tolerance would either have produced a false failure or hidden real
defects: the tolerance belongs to the measurement.

**Ledger: 269 facts · 200 agree · 0 conflicts · 43 unverified.**

## 8. Files

| Path | What |
|---|---|
| `scripts/design_truth.py` | the harness (`web`, `facts`, `cross`, `report`) |
| `design-reference/ios27/web/hig/` | 165 harvested Apple HIG articles (DocC markdown) |
| `design-reference/ios27/web/provenance.json` | URL, HTTP status, sha256, bytes for every fetch |
| `design-reference/ios27/web/guide/` | harvested iPhone User Guide pages |
| `design-truth/facts.json` | the merged fact store (every source, every value) |
| `design-truth/conflicts.json` | machine-readable ledger |
| `design-truth/CONFLICTS.md` | readable ledger, regenerated from the JSON |
| `design/spec/SOURCES.md` | inventory of materials (unchanged in authority, extended in coverage) |

## 9. Cross-session coordination

The three sessions (A platform, B implementation/visual QA, C design
authority) coordinate through `docs/appleux/SESSION_CHAT.md`. The master
prompt §12b describes it as "append-only, ≤1s watchers". Until 2026-10-20
that watcher did not exist: **no session was woken when another appended to
the file.** C found A's 09:11 post only because it read the file on purpose.

A file write cannot inject a turn into another process, so the mechanism is
two halves:

| Half | What it is | Where |
|---|---|---|
| **Watcher** | a persistent background process polling the file every 10 s, keeping a byte cursor and delivering each complete post exactly once to an inbox | `scripts/session_chat_watch.py` → `design-truth/session-chat/inbox.log` |
| **Check** | a scheduled turn every 5 minutes that resumes C, reads only what it has not consumed, acts on C-lane items, and routes the rest | `cron` task, `--unseen` mode of the watcher |

The harness's `board_post` is the third channel, but it is **per session
group**: the `kilo_board` table was empty and the three `kilo` processes run
independently, so a board post does not cross sessions either. The file plus
the watcher is what actually works, and the board remains a useful extra
record of the same summary.

**Standing rule for all three sessions:** append to `SESSION_CHAT.md` at the
end of a turn, and start a turn by reading the unseen posts. The watcher's
`--unseen` mode is the cheap way to do the latter.

