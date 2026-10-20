# Control Center Skeleton

**Source:** `design-reference/iphone11-ios27/drive/cc-light-full.png` (828×1792), `cc-dark-full.png`, `cc-connectivity-expanded.png`, `cc-light-media-expanded.png`  
**Implementation:** `AppleuxControlCenter.kt` + `AppleuxControlTile.kt`

## Layout Structure

iOS 27 Control Center: full-screen glass overlay with modular tile grid.

### Top Section — Connectivity Block (2×2)

| Tile | Position | Icon | States |
|---|---|---|---|
| Airplane | top-left | `sf_airplane` | On/Off |
| Cellular | top-right | `sf_cellularbars` | On/Off |
| Wi-Fi | bottom-left | `sf_wifi` | On/Off/SSID |
| Bluetooth | bottom-right | `sf_bluetooth` | On/Off |

- Expanded on long-press → detail sheet
- Active: filled circle with accent color
- Inactive: gray circle

### Media Card

- Album art + title + artist
- Play/pause, next/prev
- Expandable to full controls

### Brightness/Volume Sliders

- Vertical sliders, rounded caps
- Fill: white gradient
- Track: translucent dark

### Tile Grid — MEASURED (2026-10-20)

Measured programmatically from `drive/cc-dark-full.png` (828×1792, iPhone 11
at 2× = 414×896 pt): the saturated tile glyphs were located by saturation,
and the white ink under each row gave the label bands.

| quantity | measured | on our 393 dp panel |
|---|---|---|
| columns | **4** | 4 |
| **column pitch** (cell to cell) | 168 px = **84 pt** | 84 dp |
| **row pitch** | 168 px = **84 pt** | 84 dp |
| visible tile glyph | 138 px = **69 pt** | 69 dp (gap ≈ 15 dp) |
| tile label ink height | 10-11 px = **5.0-5.5 pt** (an x-height → a 9.6–11.0 pt face) | **11 dp** (the HIG's iOS minimum text size) |
| grid side margin | x 92..733 → ~46 pt | 46 dp |

**Our implementation does not have this rhythm.** `AppleuxControlCenter`
builds its 4-column grid with `GridLayout.LayoutParams.WRAP_CONTENT` cells
(`AppleuxControlCenter.kt:261-262`), so the pitch is whatever the default
GridLayout gap happens to be — the rhythm is **unspecified**, and the values
this spec used to carry ("~60dp square", "~120dp", `INSETS_SM`) were
**guesses** that do not match the frame.

**C recorded the measurement and named the tokens; C did NOT change the
layout**, because a tile-pitch change is visible across all 55 modules and
needs a device read. The tokens to use, when it is built:
`CC_TILE_PITCH_DP = 84f`, `CC_TILE_GLYPH_DP = 69f`, `CC_TILE_LABEL_DP = 11f`
— **now tokens** (`ControlCenterTokens`).

**The label size, settled (C, 2026-10-21).** The earlier note here leaned on
the HIG's **contrast** rule to justify a 10 pt label ✗ — that was the wrong
rule. HIG → Typography, "Ensuring legibility", publishes iOS's **minimum
text size as 11 pt** and says plainly to *follow the recommended default and
minimum text sizes for each platform* ✓ — that is a **size** rule, and it
binds. The GT's own tile label measures an **x-height of 5.0–5.5 pt**, which
is a **9.6–11.0 pt** face at San Francisco's 0.5 x-height ratio — so the photo
**straddles** the floor and cannot settle it ✓. The HIG settles it: **11 pt**,
which is also our ramp's smallest step (`caption2`) and exactly Apple's
minimum ✓. So the tile label is **11 sp**, not the 10 sp the ROM hard-codes.

**Corner radius:** the tiles are **circular** in iOS 27 (the glyph is a filled
circle), not rounded rectangles — so `LiquidGlassTokens.REGULAR_CORNER_RADIUS_DP`
does not apply to them.

## Glass Material

- Background: `LiquidGlassTokens.REGULAR` (frosted glass over wallpaper)
- Fill: `REGULAR_BG_LIGHT` / `REGULAR_BG_DARK` at `REGULAR_BG_LIGHT_ALPHA = 0.70`
- Rim: `RIM_LIGHT` / `RIM_DARK`
- Blur: `AppleuxGlass.applyBlur(root, MAX_LEVEL)`

## Expand Behavior

- Long-press tile → expands in-place with spring animation
- Background dims further
- Tap outside → collapses

## Dark Mode

- Same geometry
- Glass fill switches to `REGULAR_BG_DARK`
- Tile icons remain white
- Active tiles: accent colors unchanged

## Cross-State Notes

- **Q70/low-power:** blur disabled; fill becomes opaque; tiles still functional
- **2 GB tier:** reduced tile count; media card simplified
- **Reduce Motion:** expand/collapse uses fade instead of spring
