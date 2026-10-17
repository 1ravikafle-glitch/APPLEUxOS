# iOS 27 Icon Assets

118 icons extracted from 50 iPhone 11 (iOS 27) screenshots, delivered as
reusable PNG + SVG assets for the AppleUX design system and any other project.

Source: Google Drive folder **"Ios 27 iphone 11"**
(`https://drive.google.com/drive/folders/1s0231xqhTp1XlPr6wvRf3OtacKBXbyEw`),
downloaded to `original/` (50 PNGs, 828x1792).

## Layout

```
icons/
├── assets/                  the deliverable
│   ├── apps/                58 app icons (squircle, full bleed)
│   ├── settings/            42 Settings tiles / rows
│   ├── control-center/       6 round control-center glyphs
│   ├── glyphs/              12 small symbols (logo, clock, lock, ...)
│   └── manifest.json        per-asset name, category, paths, sources
├── qa/                      contact sheets of every asset (qa_sheets.py)
├── crops/                   211 deduped raw crops + crops/manifest.json
├── original/                the 50 source screenshots
├── tools/                   self-contained pipeline (see below)
└── .venv/                   python env (opencv-python-headless, vtracer)
```

Every asset ships in three forms:

| file | what |
|---|---|
| `<name>.png` | native-resolution RGBA crop (26-270 px, trimmed + alpha-feathered) |
| `<name>.svg` | vector trace (vtracer, spline/stacked, color_precision 6) |
| `<name>@512.png` | 512 px raster rendered from the SVG by Inkscape |

Use the SVG / @512 forms for anything larger than an icon grid — the native
PNGs come from 828p screenshots and are deliberately kept at source fidelity.
Known limitation: hairline details (e.g. the 60 tick marks of `clock-analog`)
simplify in the vector trace, since the trace starts from a ~48 px source.

## Pipeline

```
tools/detect.py    detect icon blobs in every screenshot (Canny + contours,
                   geometry filters, hole-aware alpha mask, erosion gates)
tools/extract.py   crop each detection, cluster near-duplicates, write
                   crops/icon_XXXX.png + crops/manifest.json
tools/build.py     dedupe crops to unique symbols (perceptual + edge-signature
                   shape matching + curated merges), strip notification
                   badges, rebuild clean squircle masks, assign semantic
                   names, render png/svg/@512, write assets/manifest.json
tools/qa_sheets.py render qa/<category>.png contact sheets for review
```

Regenerate everything from `crops/` (or from scratch by running detect and
extract first):

```bash
cd icons
.venv/bin/python tools/build.py        # ~90 s: 118 vtracer + 118 inkscape runs
.venv/bin/python tools/qa_sheets.py
```

Requirements: `.venv` (opencv-python-headless, vtracer) and system Inkscape
(`/usr/bin/inkscape`) for the 512 px renders. The build is deterministic and
exits if the 118-entry name table in `build.py` disagrees with the dedupe
result.

## Curation notes

- 354 raw detections → 211 unique crops → 118 final symbols after perceptual
  dedupe, edge-signature shape dedupe (correlation ≥0.80 + mask IoU ≥0.85) and
  manual merges of duplicate sightings.
- Only `messenger` carried a real notification badge. Removal reconstructs the
  artwork the badge covered: the bubble's arc is restored from the original
  pixels and extended along a robustly fitted circle (rms <1.5 px), and the
  icon-body border erased across the corner is re-inpainted from its two
  surviving ends. No red pixels remain in the output.
- Names are semantic slugs (`settings/bluetooth`, `apps/play-orange`, ...);
  the full table lives in `tools/build.py` (`NAMES`).
- `assets/manifest.json` records, per asset: source crop ids, source
  screenshot files, how many screenshots it appeared on, and whether a badge
  was removed.
