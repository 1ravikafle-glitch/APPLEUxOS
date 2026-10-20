# C-Lane Verification Checklist

**Owner:** C (design authority) · **For:** B (visual QA) · **When:** after the
next build + install lands on the device.

**THE PANEL (2026-10-20, 16:05 — the device came back on a NEW AVD).** The
screen is now **1080×2408 @ 440 dpi** = **392.7 × 875.6 dp** (it was
1080×2400 on the previous AVD, which ran at **420 dpi = 2.625 px/dp** —
`NotchTokens.kt:91-92` says so, and it matters).

> ### THE CONVERSION — READ THIS ONCE, USE IT FOR EVERY ROW
> ```
> this AVD:  440 dpi -> 1 dp = 2.75 px     panel = 392.7 x 875.6 dp
> old AVD:   420 dpi -> 1 dp = 2.625 px    panel = 411.4 x 914.3 dp
> GT frame:  iPhone 11 at 2x -> 1 pt = 2 px, 414 x 896 pt
> width scale (GT -> this panel): 392.7 / 414 = 0.9486
> ```
> **Any number measured on the OLD panel must be divided by 2.625, and any
> measured on THIS one by 2.75.** Dividing by the wrong divisor is a 4.8 %
> error on every width, and the GT comparison is width-based.
>
> The status-band inset is **76 px = 27.6 dp** on this panel (72 px on the
> old one).

**Every measurement is re-taken from scratch** — nothing is diffed against
an earlier panel's numbers. The ground truth is the iPhone 11 at **414×896
pt**, so the width scale is **0.9486** and every GT-derived expected value
below is already scaled to our panel.

**ROW 1.8 IS NOW SETTLED (2026-10-21, C).** The card corner is **34 dp with
a superellipse exponent of 3.2** - measured by tracing the card's actual
boundary in the GT frame and fitting a symmetric superellipse to the trace
(three cards, six corners, one answer: a = 69 px, b = 68.5 px, n = 3.2, RMS
about 1.3 px -> 32.7 dp -> the kit's `radius.glass` = 34). The old "two
candidates" were circular-arc fits anchored to an assumed card top. **Row
1.8 is now a normal PASS/FAIL row** - and the ROM still has to change (the
row groups draw 26 dp and the account card 12 dp).

Every row is a **rendered** value, which is the one thing the design-truth
ledger cannot settle by itself: the ledger compares tokens against published
sources, but what reaches the screen is the composite. One pass, these rows,
and C's lane is closed on evidence rather than on hope.

**How to read a row:** sample the pixel at the stated place, compare with the
expected value, and report PASS/FAIL per row with the measured hex. A row
that cannot be sampled (feature absent, screen unreachable) is reported as
NOT REACHED, not skipped silently.

**Tolerances** are in 0-255 levels per channel. The ground truth is a
downscaled photo capture, so ±2 levels is measurement noise, not a defect.

**Sample the COMPOSITE on our panel**, the same way the GT photo was
sampled — a published hex compared against a photographed composite is not a
comparison. Where the expected value below is a *composite* of a translucent
token over a backdrop (the label ramp, the hairline), the sample is the
composite too. The one exception is the chevron (row 3.3), where the
expected value is a *rendered ink* rather than a colour.

---

## 1. Settings root — `drive/settings-root-light-top.png`

The four reads C asked for, in one place:

| # | What | Where to sample | Expected | Tolerance |
|---|---|---|---|---|
| 1.1 | **primary label** ("Airplane Mode") | the glyph cores of a row title | **#000000** | ±2 |
| 1.2 | **secondary label** (a row's value/subtitle) | the glyph cores | **#8A8A8E** (neutral — *not* #D1D3E7) | ±2 |
| 1.3 | **row hairline** | between two rows in a card | the kit's **vibrant** separator swatch: **#E6E6E6** light (the GT reads **#E7E7E8** — 2 levels off the kit's value, and the kit's swatch is the authority) | +/-4 (C, 2026-10-21: the ROM's hairline is the **non-opaque composite** (#E0E0E0) instead — **7 levels too dark**, so this row was going to FAIL; the token that matches is `separatorTertiary` (#E4E4E8), which is **unused** today |
| 1.4 | **page background** | between two cards | **#F2F2F7** | ±2 (GT reads #F2F2F6) |

Two structural reads, from the same screenshot:

| # | What | Expected |
|---|---|---|
| 1.7 | **card horizontal margin** | the white card's left and right edges, measured from the screen edge | **20 dp** (GT: 20.0 pt — the nav card's white run is x 40..787 on four independent rows, card width 374.0 pt; scaled to our 393 dp panel that is 19.0 dp, so 20 dp is the nearest step) | ±2 dp |
| 1.8 | **card corner radius** | the top-left corner of a group card, AND of the account card | **34 dp** (the GT: 69 px = 34.5 pt = 32.7 dp -> the kit's `radius.glass`), with a **superellipse exponent of 3.2** - ONE radius for every Settings card (C18, settled 2026-10-21) | +/-1 dp on the radius; the exponent is visual - a 4.4 squircle reads visibly squarier than the GT's |
| 1.9 | **accent tint on a setting icon** | the dominant colour of a blue icon square | ours is the flat **#0088FF**; the GT paints the icon *material* at **#3F8FF7**. They are different surfaces, not a palette error — confirm the hue reads right, and do not expect an exact match | hue only |
| 1.5 | **uppercase text anywhere in the root** | **none** — every label is sentence case (C14/C16) |
| 1.6 | **group headers** | **none** — groups are separated by 35 pt of page background; "General" is a ROW in the same card as Appearance / Camera / Control Center |

## 2. Home screen — `drive/home-light*.png`

| # | What | Expected | Tolerance |
|---|---|---|---|
| 2.1 | **page dots** | visible, **between the last grid row and the search pill**, NOT inside the dock band (they used to land at 78 dp, behind the dock glass) | — |
| 2.2 | dot count / alignment | one dot per home page, centred | — |
| 2.3 | **clock widget** | centred (B13) | — |

## 3. Detail pages — `drive/general-light-top-hero.png`, `drive/bluetooth-dark.png`

| # | What | Expected | Tolerance |
|---|---|---|---|
| 3.1 | **back button** | a **44 dp CIRCLE** (a ring, not a bare chevron), left edge 19 dp from the screen edge, top 31.6 dp from the window top | ±2 dp |
| 3.2 | back-button **fill** | light **#F3F3F7** · dark **#1F1F1F**, edge/rim **#F7F7FC** / **#202020** | ±2 |
| 3.3 | **chevron** | 10 dp, painted in the **primary** label colour. Sample the DARKEST CORE pixel: ours should be ≈ **#000000** (light) / ≈ **#FFFFFF** (dark). The GT photo reads #0C0C0D / #F9F9F9 because that is the same token after antialiasing and gamma in a downscaled capture — so a core up to **14 levels** off our token is still PASS. **Report the measured core**, not just PASS/FAIL | **±14 (ink)** |
| 3.4 | **centred nav title** | **absent** on detail pages (General, Bluetooth). The Appearance page is the exception. | — |
| 3.5 | **page title** | starts **below** the status band, never at y=0 | — |

## 4. Appearance page — `drive/appearance-dark.png`

| # | What | Expected |
|---|---|---|
| 4.1 | **section header** | "Appearance", **sentence case** (not "APPEARANCE"), grey, ~13 pt |
| 4.2 | **back button** | as 3.1–3.3, plus the **centred inline title** (this page has one) |
| 4.3 | Light/Dark picker card | two phone previews, radio indicator under each, label below |
| 4.4 | rows | "Automatic" + switch, "Liquid Glass … Default >", "Text Size", "Bold Text", "Display Zoom … Default >" |
| 4.5 | **no "Glass Effect" SLIDER** | the row replaces it |

## 5. The one VISIBLE change C made

| # | What | Expected | Tolerance |
|---|---|---|---|
| 5.1 | **an alert/dialog panel in LIGHT mode** | the panel is **#FFFFFF**, clearly separated from the **#F2F2F7** page behind it | — |
| 5.2 | the same in DARK mode | the panel is **#1C1C1E** on the #000000 page (unchanged) | — |

**This is the only C edit that changes what is drawn.** Until 2026-10-20 the
alert's light fill was #F2F2F7 — *exactly* the page colour — so the panel
had no contrast and was visible only by its shadow. HIG → Color's background
hierarchy puts a floating element at the grouped SECONDARY level (#FFFFFF /
#1C1C1E), which is also what the component's own comment asked for. The
token change is `ColorTokens.systemSurface`; every other C edit in C12 was
value-preserving and is recorded as such in `design-truth/applies.json`.

## 6. Dark mode

Every read above has a dark twin. Sample the same surfaces with the system
in dark appearance and confirm the **same structure**, with the dark values
from the table (page #000000, card #1C1C1E, label #F9F9F9, disc #1F1F1F).

## 7. What C does NOT need re-read

Already settled by a source and unchanged by any build: the type scale and
tracking (HIG's SF Pro table), the tint palette (HIG tint table + the iOS 27
kit), the separator/label/fill ramps (the kit), the Liquid Glass material
(the kit), the grey ramp (HIG + kit). If any of these look wrong on screen,
that is a **rendering** defect, not a token defect — say so and C will chase
the composition, not the value.

## 9. This cycle's NEW rows (2026-10-21)

Everything C settled or delivered since the checklist was written, in one
place. Rows marked **NOT REACHED** are features that are not in the build
yet - report them as NOT REACHED, not as FAIL.

| # | Where | What | Expected | Tolerance |
|---|---|---|---|---|
| 9.1 | **home** | the **search pill** is a compact capsule, not a full-width band | **92 dp wide (a maximum - it hugs its content), 28 dp tall, its bottom at 132 dp from the screen's bottom** (C31) | +/-2 dp; the SHAPE is the row - a band instead of a capsule is a FAIL whatever the numbers |
| 9.2 | **home** | the dock's icons sit in the first three **cells** | the first icon's left edge **32 dp** from the screen's edge, the pitch **89.56 dp**, and the **4th cell empty** - the icons must NOT spread to fill the band (C23/C32) | +/-1 dp; the empty 4th cell is the row |
| 9.3 | **home** | the dock band's edges | the band **368.7 dp** wide, **20 dp** from each screen edge to the band's rim, and the icons **29.56 dp** apart | +/-1 dp |
| 9.4 | **Settings** | the card corner | as row **1.8** - **34 dp**, exponent **3.2**, on the row groups AND the account card | +/-1 dp |
| 9.5 | **Settings** | the App Library's folder title's shadow | a soft dark halo under the 26 sp white title: **blur 8 dp, offsetY 2 dp** (C19) - a 4 dp / 1 dp halo is the OLD wrong value | the halo's extent +/-1 dp |
| 9.6 | **Control Center** | the tile label's size | **11 sp** (the HIG's iOS minimum text size; C20) | +/-1 sp |
| 9.7 | **Control Center** | the tile grid's rhythm | **84 dp** pitch on both axes, a **69 dp** glyph, a **46 dp** side margin - **only if @A adopts the CC tokens** (C22) | +/-2 dp; **NOT REACHED** if the grid is unchanged |
| 9.8 | **Settings -> Appearance** | the Increase Contrast appearance | the third appearance - **NOT REACHED** until the resolution lands (C21's values are landed; the resolution is @A/@B's) | - |
| 9.9 | **any glass surface** | the transparency slider's three positions | the glass at the **default**, at **ultra-clear** and at **fully-tinted** - the ends are reachable only since C32 (the level now spans the full slider) | a visual signature, not a number: the surface must be visibly more see-through at 100 and more opaque at 0 |

## 8. Reporting

One post per build, this order: **PASS/FAIL/NOT REACHED per row**, then the
measured hex for any FAIL. A row is only PASS if the measurement is inside
the tolerance — a visual impression is not a measurement, and C will not
accept one for a number.
