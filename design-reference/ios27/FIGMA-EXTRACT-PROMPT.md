# Prompt: extract full iOS 27 UI design specs for AppleUX

Paste the block below into a session that **has browser + Figma access**.
It is designed so the output drops straight into this repo and is
machine-readable — measurements, not descriptions.

Save output to: `/home/elfakiris/OsProject/design-reference/ios27/figma-extract/`

---

## THE PROMPT (paste from here)

You have browser access to Apple's official iOS 27 UI Kit and the iPhone User
Guide. Extract a complete, machine-readable design specification that a
developer can implement pixel-accurately in Android.

### SOURCES (in priority order)

1. Apple's official iOS 27 Figma UI Kit — from
   `developer.apple.com/design/resources/` → iOS 27 → UI Kit → Figma
2. Apple's iOS 27 iPhone User Guide at `support.apple.com/guide/iphone`
3. Apple's own screenshots from that guide (they have descriptive `alt` text;
   image URLs are on `ipcdn-web.apple.com`)

If the Figma kit is unreachable, use the User Guide screenshots and say so
explicitly in `provenance.json`. Do NOT substitute a third-party recreation —
authority matters, and a re-creation is less accurate than Apple's own images.

### OUTPUT — create these exact files

All paths relative to `design-reference/ios27/figma-extract/`.

**`design-tokens.json`**
```json
{
  "color": {
    "systemBackground":        {"light":"#FFFFFF","dark":"#000000","source":"hig|kit|screenshot","value":"#RRGGBB","alpha":1.0},
    "systemGroupedBackground": {"light":"#F2F2F7","dark":"#000000","value":"#RRGGBB","alpha":1.0}
  },
  "typography": {
    "largeTitle": {"size":34,"weight":"bold","lineHeight":41,"tracking":0.374,"family":"SF Pro"},
    "title1":     {"size":28,"weight":"bold","lineHeight":34,"tracking":0.364},
    "body":       {"size":17,"weight":"regular","lineHeight":22,"tracking":-0.408},
    "caption1":   {"size":12,"weight":"regular","lineHeight":16,"tracking":0}
  },
  "spacing":  {"inset":{"xxs":2,"xs":4,"sm":8,"md":12,"lg":16,"xl":20,"xxl":24,"xxxl":32}},
  "radius":   {"button":null,"card":null,"sheet":null,"control":null,"appIcon":null},
  "material": {
    "liquidGlassRegular": {"cornerRadius":null,"fillLight":null,"fillDark":null,"rimLight":null,"rimDark":null,"shadowBlur":null,"shadowOffsetY":null,"shadowAlphaLight":null},
    "liquidGlassClear":   {"cornerRadius":null,"base":null,"overlay":null,"overlayAlpha":null},
    "transparencySlider":{"min":0,"max":1,"default":0.5,"opacityScale":null,"blurScale":null}
  },
  "icon": {"appIconGrid":1024,"appIconSuperellipseN":null,"appIconCornerRadius":null,"controlDiameter":null}
}
```

**`screens/<screen>.json`** — for EACH of:
`home`, `lock-screen`, `control-center`, `notification-center`,
`app-library`, `settings-root`, `settings-detail`, `recents`,
`keyboard`, `dialog`, `sheet`, `volume-hud`, `battery-charging`

```json
{
  "screen": "control-center",
  "referenceScreenshot": "<URL>",
  "elements": [
    {"id":"cc-connectivity-cluster","type":"group",
     "frame":{"x":0.021,"y":0.075,"w":0.31,"h":0.20},   // fractions of screen
     "cornerRadiusPt":null,"children":[
        {"id":"cc-airplane","type":"control","shape":"circle",
         "centre":{"x":0.077,"y":0.118},"diameterPt":null,
         "label":null,"activeColour":null,"icon":"sf.airplane"}
     ]}
  ],
  "gestures": [{"from":"top-right","action":"swipe-down","opens":"control-center"}],
  "notes": "anything not expressible as numbers"
}
```

**`settings-menu.json`** — the COMPLETE Settings hierarchy:
every group, every row, title, subtitle, icon, value type, destination, and
the exact grouping/inset geometry.

**`components/<name>.svg`** — one file per component, real SVG paths
(extracted from Figma where possible). Must be usable as an Android
VectorDrawable; include the `android:pathData` equivalent in a sibling
`<name>.json`.

**`provenance.json`** — for EVERY value: which source it came from, the
file/URL, and whether it is Apple-published or inferred. Apple publishes NO
numeric spec for the Liquid Glass material — if a value is inferred from the
kit's visual construction, mark `"inferred": true`. Never present an inferred
value as an Apple-published number.

### RULES

1. **Measure, never estimate.** If you can read a value from the Figma file or
   measure it from Apple's pixels, do. If you cannot, set the field to `null`
   and record why in `provenance.json`. A `null` is useful; a guess is not.
2. **Never mark an inferred or eyeballed value as Apple-sourced.**
3. **Do not copy Apple's binaries or frameworks.** Extract design
   specifications and geometry only.
4. If the Figma kit blocks you, say so plainly and extract everything from the
   User Guide screenshots instead — that is still Apple's own material.

### VERIFY BEFORE REPORTING

- Confirm each JSON file parses
- Confirm at least 12 screens and the full Settings hierarchy are covered
- Report exactly which fields are `null` and why

---

## Why this is shaped this way

- **`null` beats a guess.** I already measured Apple's squircle as a
  superellipse with n≈4.4 by fitting Apple's pixels — a "close enough"
  rounded-rect answer would have been wrong by 38%.
- **Fractions, not pixels.** Apple screens are 390-447pt wide; the POCO M5 is
  ~411dp. Normalised coordinates port across both.
- **Provenance is per-field.** Apple publishes no Liquid Glass numbers. Anyone
  reading this later must be able to tell an Apple value from an inferred one.
- **`settings-menu.json` matters disproportionately** — Settings is the
  largest surface we have barely started, and it is pure structure, so it is
  cheap to port once extracted.

## What already exists here (don't re-derive)

| Already have | Source |
|---|---|
| App icon squircle: exact 40-segment vector, n≈4.4 | Apple's own icon template `.ai` |
| 53 official iOS 27 screenshots + behaviour text | Apple's iPhone User Guide |
| Liquid Glass values (34pt radius, fill, rim, shadow) | MIT transcription of the kit |
| 14 Apple HIG reference docs (2025-02, pre-Liquid-Glass) | hig-skills |

The gaps this prompt fills: **control spacing, type scale, component metrics,
the Settings hierarchy, and per-screen layout geometry** — which exist only
inside the Figma file.