# Home Screen Layout Skeleton

**Source:** `design-reference/iphone11-ios27/drive/home-light-alt.png` (828×1792), `home-light-widgets.png` (828×1792)  
**Panel:** POCO M5 1080×2408, density 2.75 (**392.7 dp** wide) — confirmed live on the device (`wm size` → 1080×2408, `wm density` → 440 dpi → 2.75 px/dp)  
**Mapping:** iPhone 11 (828px @ 2x = 414pt) → rock (1080px @ 2.75 = 392.7dp). Width scale **392.7/414 = 0.9486**; dp ≈ pt 1:1 by design, so a GT measurement in pt is read as dp to within ~5 % (NotchTokens provenance). **The previous AVD ran 1080×2400 at 420 dpi (2.625 px/dp = 411.4 dp wide) — a number taken on that panel must not be read on this one.**

## Vertical Structure (top → bottom)

| Region | iPhone 11 (828×1792) | rock dp | Token / Source |
|---|---|---|---|
| Status bar / notch band | y 0–88 (44pt) | 0–28.3dp | `RockDisplayInsets` + `NotchTokens.NOTCH_HEIGHT_DP=29` |
| **Dock band inset** | **11.5 pt** from the screen edge (the glass's 1 px rim) | 12 dp | `LauncherTokens.DOCK_BAND_INSET_H_DP` + `@dimen/appleux_space_dock_band_inset` — the band's WIDTH follows: 392.7 − 2×12 = **368.7 dp** (the GT's 391 pt scaled) |
| **Status clock** | **x 31–57 pt (LEFT)**, y 17.5–31 pt | x 29–54 dp | bold white, on the LEFT |
| Top margin (below notch) | ~88–140px | ~28–46dp | `SpacingTokens` |
| Widget block (page 0 only) | y 140–580px | ~46–190dp | `LauncherTokens` widget span |
| App grid (6 rows × 4 cols) | y 580–1480px | ~190–527dp | `LauncherTokens.GRID_COLUMNS=4`, 6 rows |
| **Search pill** (GT, measured 2026-10-20) | capsule y 1457–1513 px = **139.5–167.5 pt from the bottom**; x 300–493 px = 150–246.5 pt | **132 dp from the bottom**, 28 dp tall, 92 dp wide | `LauncherTokens.SEARCH_PILL_*` — ours is a **full-width band** (MATCH_PARENT), 44 dp tall from raw literals: **wrong shape, 16 dp too tall** (C31, @B) |
| Page dots | **no page dots in the GT home frame** | — | the white pixels the old spec called "dots" at y 1490–1510 are the **search pill's own label and magnifier** ✓; iOS 26/27's home is ONE page, so there is no page indicator (B's 20:35 retraction ✓) |
| Dock glass bar | y ~1562–1772px | 729.6–813.6 dp from the top | `LauncherTokens.DOCK_*` = 62…146 dp from the bottom |
| Home indicator | y ~1780–1792px | system | System |

> The last three rows are **re-derived from the tokens** (2026-10-20) and are
> exact. The **App grid** and **Widget block** rows still carry pre-rework
> numbers and are marked for re-derivation — the grid's own bottom is
> `HOMESCREEN_BOTTOM_RESERVE_DP` = **168 dp** from the window bottom
> (685.6 dp from the top) — `SEARCH_PILL_BOTTOM_DP` 132 + `SEARCH_PILL_HEIGHT_DP`
> 28 + 8 — not the value in the table. (It was 214 dp until the pill's shape
> was corrected on 2026-10-21, C31: the pill was a 44 dp full-width band.)

## Dock Band (measured from GT)

**GT measurement** (`home-light-alt.png`, x=100 column):
- Dock band top edge: y≈1562 (sharp transition from wallpaper)
- Dock band bottom edge: y≈1772 (back to wallpaper)
- Dock band height: ~210px @ 828 = ~105pt ≈ 105dp
- Dock band color: (222,179,106) — **darker** than wallpaper (240,194,117)
- Wallpaper left of dock (x=20): (240,194,117) uniform

**Our render** (from A's 20:43 probe):
- Dock band: #e1c19a (225,193,154) — **lighter** than wallpaper #e1b26d (225,178,109)
- ~~**BUG: direction inverted.**~~ **FIXED** — `LauncherActivity.applyGlass()` now
  uses a **black** base in light mode (`val base = android.graphics.Color.BLACK`),
  so the band darkens the wallpaper like the GT. Shipped in commit `e4bcdc44`
  ("C3 dock dark scrim (measured 7.8 %)").

**Dock geometry:**
- **4 icon SLOTS**, horizontally centered — a fixed grid, not a count of
  whatever apps happen to be installed (see "The dock is a four-slot grid"
  below)
- Icon size: `LauncherTokens.DOCK_ICON_SIZE_DP` = **60 dp** (it now mirrors
  `HOME_ICON_SIZE_DP = 60`; the 56 dp dock icon is gone — one icon size
  everywhere, as iOS does it)
- Vertical padding: `LauncherTokens.DOCK_BAR_PADDING_V_DP` = **20 dp**
  (was 14 dp; the GT's glass is 105 pt = 99.6 dp tall, ours was 88 dp)
- Corner radius: `LauncherTokens.DOCK_CORNER_RADIUS_DP` = 34 dp ✓ (the GT's
  fitted radius at the matching exponent is 33.7 dp)
- Corner **shape**: `LauncherTokens.DOCK_CORNER_EXPONENT` = **2.0**, not the
  icon's n=4.4 — the GT's dock corner is ROUND (fitted over the full corner
  arc: n=2.0 → RMS 0.96 px; n=4.4 → 2.62 px)
- Per-item offset: **none** — the gap is DERIVED (below); the old
  `SpacingTokens.INSETS_LG` left-offset on every cell is gone, because a
  left offset on every cell made the outer gaps differ from the inner ones
- Outer padding of the icon strip: `appleux_space_dock_bar_padding_h` =
  **20 dp** (the GT's 21.25 pt; it used to be a raw 14 dp literal)
- **Band inset from the screen edge: `LauncherTokens.DOCK_BAND_INSET_H_DP`
  = 12 dp** (the GT's 11.5 pt — see the section below)

### The dock is a four-slot grid, not a count of icons (C, 2026-10-20)

**The rule: the icon gap is derived from the SLOT count
(`LauncherTokens.DOCK_SLOTS` = 4), never from the number of icons in the
dock.** This is the answer to B's question of 23:10.

Why the slot count is the design fact:
- **Apple's own provenance** — `SB_DefaultIconState~iphone.plist`
  (`buttonBar`) carries exactly four dock apps: phone, safari, messages,
  music. The ROM mirrors that structure (`DockPreferences.DEFAULT_SLOTS`,
  four persisted slots).
- **The GT frame has four cells** — measured: the four icons' edges are
  x = 65, 254, 444, 633 (each 128 px wide), a uniform pitch of 189.33 px
  across the band's inner width. The GT's rhythm is a four-cell grid.
- **The icon count is USER DATA.** It drops to 3 the moment a slot is
  empty, and it changed when an app was uninstalled. A gap derived from it
  would move the band whenever the user edits the dock — which is exactly
  the class of defect the token layer exists to remove (the old dock "moved
  around", per `DockPreferences`).
- The derivation is therefore a pure function of the tokens:
  `gap = (band − 2 × inset − 2 × padding − slots × icon) / (slots − 1)`.

**An empty slot keeps its CELL.** The icons do not spread to fill the band:
the pitch is the slot pitch, and the empty cell is space. This is our
decision for the empty-dock case (we have no GT frame of an iOS dock with
an empty slot), and it is recorded in `DESIGN_SYSTEM.md` §14.

**Why the ROM renders three icons today — a data fact, not a layout bug.**
Slot 4 is **Music** (Apple's provenance). The appleux app set ships no music
app (`frameworks/appleux/` has phone, browser, messages, video, camera,
clock, calendar, contacts, photos, files — no music), and slot 4's
package allowlist has no AppleUX package, so slot 4 resolves to nothing and
`dock.filterNotNull()` renders three icons. The GT shows four. Closing that
gap is a build item, not a layout one: either the ROM ships a Music app, or
the fourth slot is filled by another media app (a recorded deviation — iOS
carries no video app in the dock).

### Dock band — horizontal extent (MEASURED 2026-10-20; RE-MEASURED the same day)

**This is the band the dock glass sits on, and it is the one piece of dock
geometry that had no value anywhere — not in the spec, not in a token, and
not in Apple's HIG (the HIG has no home-screen dock geometry section, and
the extracted artwork catalogue has no dock template).**

**GT — the glass's own edge is a 1 px dark rim stroke.** Both GT home frames
(`home-light-alt.png`, `home-light-widgets.png`) agree:
```
left rim  x = 23 px = 11.5 pt        right rim x = 804 px = 402.0 pt
-> glass 782 px = 391 pt wide, 11.5 pt inset per side (94.4 % of 414 pt)
top edge  y = 1556 px (a 1 px BRIGHT specular line)
bottom edge y = 1766 px (the band fades out) -> 105 pt tall
wallpaper (240,194,117) is uniform from x=16 to x=22, the rim is at x=23,
and the glass interior (223,178,106) runs x=24..803.
```
**CORRECTION (C, 2026-10-20): the earlier reading of this band was wrong.**
It took the corner arc's END (x=32 at y=1598) for the straight side and
called the difference between wallpaper and glass a "soft edge falloff, 5 pt
beyond the glass". Both are wrong: the arc runs on to **x=23 at y=1636**,
and there is no halo at all — the wallpaper is uniform right up to the rim.
The glass is **391 pt** wide with an **11.5 pt** inset, not 381 pt / 16 pt.
B's independent read of 391 pt was the glass after all.

The corner arc (both frames, x per row): 1556→81, 1560→72, 1564→62,
1568→55, 1572→50, 1576→45, 1580→42, 1584→38, 1588→36, 1592→33, 1596→31,
1600→29, 1604→28, 1608→27, 1612→26, 1616→25, 1620→24, 1624→24, 1628→24,
1632→23, and the straight side from 1636 to 1684 (the bottom arc mirrors
it). Fitting `|x−L|ⁿ + |y−(T+R)|ⁿ = Rⁿ` with L=23, T=1556 over the FULL
arc:

| n | fitted R | RMS edge error |
|---|---|---|
| **2.0** | **71.0 px = 35.5 pt = 33.7 dp** | **0.96 px** |
| 2.2 | 76.5 px = 36.3 dp | 0.86 px |
| 2.4 | 81.5 px = 38.7 dp | 0.98 px |
| 2.8 | 91.5 px | 1.28 px |
| 4.4 | 129.0 px | 2.62 px |

The exponent and the radius are **coupled**: the outline only fixes the
pair. `DOCK_CORNER_RADIUS_DP` is fixed at **34 dp** (the iOS 27 Figma kit's
Regular glass radius), and 34 dp pairs with **n = 2.0** — the GT's outline
reproduced to about a pixel. n=2.4 would need a 38.7 dp radius, so the old
pairing (2.4 with 34 dp) drew the corner **4.7 dp too tight**.

**Target on our panel** (×0.9486): 11.5 pt inset → 10.9 dp → the nearest
declared step is **12 dp** (`SpacingTokens.INSETS_LG`); the band is then
392.7 − 24 = **368.7 dp** wide (the GT's 391 pt scaled = 370.9 dp).

**The whole rhythm, re-measured and re-derived.** Every number below is
measured off the GT frame (2x) and converted to our panel with x 0.9486:

| element | GT | our dp | step | token |
|---|---|---|---|---|
| band inset | 11.5 pt | 10.9 dp | **12 dp** | `DOCK_BAND_INSET_H_DP` |
| outer padding (band edge → icon) | 21.25 pt | 20.2 dp | **20 dp** | `appleux_space_dock_bar_padding_h` |
| icon size | 64.0 pt | 60.7 dp | **60 dp** | `DOCK_ICON_SIZE_DP` (already correct) |
| inner gap (icon → icon) | 30.67 pt | 29.1 dp | **derived** | `DOCK_SLOTS` + the band |
| vertical padding | 21.0 pt | 19.9 dp | **20 dp** | `DOCK_BAR_PADDING_V_DP` |
| band | 391 × 105 pt | 368.7 × 99.6 dp | - | - |

**The check that ties it together:** the four GT icons (128 px each) plus
three inner gaps (61.33 px) plus the two outer gaps (42.5 px) =
512 + 184 + 85 = **781 px** against the glass's measured **782 px** — the
whole width closes to 1 px, and only with the 11.5 pt inset and the 21.25 pt
outer gap.

**How the rhythm is DERIVED (the contract).** The band is an **input** —
`panel − 2 × DOCK_BAND_INSET_H_DP` — and the strip fills it, carrying the
20 dp inner padding, so:
```
avail = 392.7 − 2×12 (inset) − 2×20 (padding) = 328.7 dp
gap   = (avail − DOCK_SLOTS × DOCK_ICON_SIZE_DP) / (DOCK_SLOTS − 1)
      = (328.7 − 4 × 60) / 3 = 29.56 dp
```
No gap value is declared, and none should be: the band is a function of the
panel, so its remainder is too. Note that `inset + padding` is unchanged at
32 dp, so the derived gap and the icon pitch are the same as before this
re-measurement — only the band's WIDTH (360.7 → 368.7 dp) and the outer gap
(16 → 20 dp) move.

**The one ordering rule:** the derivation reads the strip's *measured*
width, which is 0 on the first measure pass, so the offsets must be
re-derived once the width is known. That is safe here precisely because the
band is `match_parent` — the strip's size does not depend on its content,
so re-deriving the offsets cannot feed back into the size and loop.

**Read-back targets for the next launcher build** (panel 1080×2408 @
440 dpi = 2.75 px/dp):
- band: x **33..1047 px** (368.7 dp), the glass's rim 1 px at each edge;
- first icon at x **88 px**, icon width **165 px**, pitch **246.3 px**
  (89.56 dp), inner gap **81.3 px** (29.56 dp);
- outer gap **55 px** (20 dp) on both sides;
- band height **275 px** (100 dp); the bottom margin 12 dp from the
  screen's bottom edge.

**Status:** the *target* is stated and tokenised
(`DOCK_BAND_INSET_H_DP` = 12 dp, `DOCK_BAR_PADDING_H_DP` = 20 dp,
`DOCK_CORNER_EXPONENT` = 2.0) and `dock_view.xml` reads the dimens. The
**implementation** is A's: `syncGlassSize()` still sizes the glass from the
icon strip, so the band's width is still an OUTPUT and the strip and the
band measure each other (two consecutive dumps of the same build disagreed:
271 dp band / 29.5 dp gaps, then 212 dp / 0 dp gaps). **B** verifies on
device once the launcher APK is rebuilt and installed.

## Grid

- Columns: 4 (iOS standard on 414pt-class)
- Rows: 6 (24 icons/page, matches iOS)
- Icon pitch: measured from GT icon centers
- Label: below icon, `TypographyTokens.caption2` (11pt), white with shadow

## Widget Block

- Page 0 only (A11 rule: widgets on page 0, later pages get full icon band)
- Width: `WIDGET_SPAN_SMALL` = half-width (Clock|Battery pair per B5)
- **GT (measured 2026-10-20 from the home frame):** the widget's rounded card
  is **150 × 165 pt** — x 30..180 pt, y 70..235 pt — with the widget's **name
  label centred BELOW** the card, and the **left margin at 30 pt (28.4 dp)**.
  The GT holds **one** widget per card; ours holds a Clock|Battery **pair**
  (B5), so the *width* is not directly comparable — the **height** and the
  **margin** are.
- **Ours (B's read of the 11:58 shipping build):** the card is **176 × 150
  dp** at a **20 dp** left margin → 6.8 pt too short and the margin 8.4 dp
  too small.
- **And it sits too LOW.** The GT's card starts at **70 pt**, i.e. **26 pt
  below the 44 pt status band** (27.4 dp below ours) → its top is **55 dp**
  from the window top. B's read of the 12:06 build puts the card's content at
  y ≈ 95..103 iOS-pt (and it moved 3/6 iOS-pt between the 11:58 and 12:06
  builds), so ours is **~25–33 pt lower** than the GT's — a vertical
  placement defect, not only a margin one.

### The status clock belongs at the LEFT (2026-10-20, from the GT frame)

The GT's status bar carries the time at the **top left** — the "3:31" ink
measures **x 73..130 px = 36.5..65 pt**, y 18..31 pt, in **bold** white over
the wallpaper (B's earlier read of the same frame: 31..57 pt, left ✓).

Ours is **centred** (B measured x 501 px = 182 dp = 192 iOS-pt on the baseline
build) and on the shipping build B found **no clock ink at all** — not
centred, not drawn. The *position* is the design fact and it is now stated
here; the drawing is **A's**.

### The widget grid's geometry (measured 2026-10-20, was unspecified)

Off the GT frame (2x), the Clock widget card is a **rounded square**:

```
card        x  60..360 px =  30..180 pt   (150 pt wide)
            y 140..470 px =  70..235 pt   (165 pt tall)
next widget starts at x = 415 px = 207 pt  -> a 27 pt gap
clock face  a white analogue dial, ~122 pt across, with the label BELOW
```

Scaled to our panel: **142 x 157 dp**, a **28 dp** left margin, a **26 dp**
gap. Ours (B's read on the shipping build) is **176 x 150 dp** at a **20 dp**
left margin — so the card is **35 iOS-pt too wide** and starts **11 iOS-pt
too close to the edge**, and the card is **7 pt short** of the GT's height.

One deliberate difference to keep: the GT's top-left widget is a **single
Clock**, ours is the **Clock|Battery pair** (B5's rule), so the card's
*contents* are not comparable — only its **grid geometry** is, and that is
what is stated above.

## Status bar — the clock belongs on the LEFT

**GT (measured 2026-10-20):** the status clock reads **"3:31" in bold white
at the top left** — its ink spans **x 73..135 px of 828 (= 36.5..67.5 pt)**
and **y 35..62 px (= 17.5..31 pt)**. iOS puts the **time on the left** of
the status bar and the indicators on the right; B's GT reading agrees at
31..57 pt.

**Ours:** the status clock is **centred** (B: x=501 px = 182.2 dp = 192
iOS-pt, against the GT's 31..57 pt on the left), and on the 11:58 shipping
build **no clock ink is drawn at all** — the status bar's left is plain
wallpaper (225,178,109). So the deviation is two-staged: wrong side, then
missing. **Owner: A** (the status bar is launcher code; the position and
weight above are the design fact C states).

## Search Pill

- Bottom-centered capsule, inside dock clearance band
- Visible on page 0 only (B6 rule)
- Fill: frosted glass (Liquid Glass Regular scrim)
- **A's 00:27 flag:** `settings_search_pill.xml` hardcoded `#59FFFFFF` — A fixed with token + values-night

**The shape is the design fact (C, 2026-10-21).** Apple's home search is a
**COMPACT capsule that hugs its content** — a magnifier, the word "Search" and
a mic — floating above the dock. **Ours is a `MATCH_PARENT` band**: a
**full-width translucent bar** across the bottom of the home screen, 44 dp
tall (raw 13 dp paddings + the text) with 14/22 dp side paddings. That is not
a bigger pill, it is a different element — and it is why the launcher's own
comment says the capsule "showed through the icons behind it": a full-width
glass band IS a band.

**The target geometry (measured off the GT home frame):**
| | GT | our panel | token |
|---|---|---|---|
| width | 96.5 pt (x 300..493 px of 828) | 91.5 dp | `SEARCH_PILL_WIDTH_DP` = **92 dp** (a MAXIMUM — the capsule hugs its content inside it) |
| height | 28.0 pt (y 1457..1513 px of 1792) | 26.6 dp | `SEARCH_PILL_HEIGHT_DP` = **28 dp** (the nearest declared step) |
| bottom | 139.5..167.5 pt from the bottom | 132.4..159.1 dp | `SEARCH_PILL_BOTTOM_DP` = **132 dp** ✓ already right |

**The defect class:** `SEARCH_PILL_HEIGHT_DP` existed but **the pill did not
read it** — only the page dots' derivation did — so the pill's own height came
from raw literals. A token that only another derivation reads is not a
consumer. **@B: rebuild the pill from the two tokens (WORK_QUEUE C31)** —
a fixed/max width instead of `MATCH_PARENT`, and the height from
`SEARCH_PILL_HEIGHT_DP` instead of 13 dp paddings.

## Page Dots

- Between search pill and dock
- Active: filled white; inactive: translucent white
- Count = the number of **grid** pages (not pages in general)
- **Visibility: the indicator is shown only when the home has more than one
  grid page** — iOS hides it when there is one. Our home currently needs
  **one** grid page, so the indicator is *correctly* absent and the
  checklist row is **NOT REACHED, not FAIL** (B's 20:35 retraction). The
  168 dp position below applies the day a second page exists.

**Correction (2026-10-20, C):** there are **no page dots in the GT
home frame** — the white pixels at y 1490–1510 px that the old spec called the
dots are the **search pill's own label and magnifier glyph**, and the pill's
capsule is at **y 1457–1513 px = 139.5..167.5 pt from the bottom** ✓. iOS
26/27's home screen is a single page, so it carries no page indicator ✓
(B's 20:35 retraction ✓). Our ROM's home is two pages (A11), so whether the
dots appear at all is a *page-structure* question for @A ✓ — and if they do,
their position follows the **search pill**, whose own geometry C has now
measured ✓ (132 dp from the bottom, 28 dp tall, 92 dp wide, against our
162 dp / 44 dp ✗).
last grid row — the GT photo has the dots at y≈1490–1510 and the pill at
y≈1520–1580, i.e. the dots are the higher of the two.

**Token defect fixed 2026-10-20.** `LauncherTokens.PAGE_DOTS_BOTTOM_DP` was
`HOMESCREEN_DOCK_CLEARANCE_DP - PAGE_DOTS_GAP_DP - 60f` = **78 dp**, which
contradicted the ordering above: the dock bar occupies the top 84 dp of the
146 dp clearance band (62 dp..146 dp from the window bottom), so 78 dp put
the dots *inside* the dock band. B measured the consequence on device (the Oct-19
baseline, which had **two** grid pages) — the dots existed at 21×30 px,
y2166..2196, while the dock glass band is y2106..2337; the dock is drawn
after them and covered them, so on that build the home screen showed no
page indicator either.

**Correction (2026-10-20, C):** I had read the absent dots as a failure of
the *position* and said the home "has two pages". B read the code instead
of the symptom and was right: `pageCount` counts the **grid** pages, our
current app count needs **one**, and `visibility = if (pageCount > 1)
VISIBLE else GONE` is exactly what iOS does. The indicator is therefore
correctly hidden today, and the row is **NOT REACHED** — it becomes
readable only when a second grid page exists (A's Today View build item) or
when the app count grows past one page. The token defect fixed above was
real either way: with two pages, the old 78 dp put the dots *inside* the
dock band, which is why they were invisible on the baseline.

The value is now derived from the pill, so it cannot drift again:

```kotlin
const val PAGE_DOTS_BOTTOM_DP: Float =
    SEARCH_PILL_BOTTOM_DP + SEARCH_PILL_HEIGHT_DP + PAGE_DOTS_GAP_DP   // 132 + 28 + 8 = 168 dp
```

That equals `HOMESCREEN_BOTTOM_RESERVE_DP`, so the grid reserve already
accounts for the dots. **@B: the row is NOT REACHED until there is a second grid
page** (A's Today View build item, or an app count past one page) — then
read the dots' position off the device. If they collide with the last grid
row, raise `HOMESCREEN_BOTTOM_RESERVE_DP` to include the dots' height rather
than moving the dots back down.

## Cross-State Notes

- **Dark mode:** same geometry; dock base switches to `Color.BLACK`; wallpaper changes
- **Q70/low-power:** blur disabled (`AdaptiveRenderPolicy.isBlurEnabled()`), fill alpha unchanged
- **2 GB tier:** same layout; blur budget may reduce dock blur to 0
- **Font scale 1.3:** grid rows shrink to compensate (rowsThatFit); dock/pill unchanged
