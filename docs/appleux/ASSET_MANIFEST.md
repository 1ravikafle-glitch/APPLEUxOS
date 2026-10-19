# AppleUXOS — ASSET MANIFEST

> **Status: DRAFT v0 — TEMPLATE. NO ASSETS HAVE BEEN VERIFIED AS PRESENT.**
> Per the project owner, licensed iOS 27 design assets are authorized for this project under a confidential agreement with Apple. The agreement documents are held by the project owner outside the repository. This manifest records what exists and what is needed; it must not contain confidential agreement text.
> Fields marked `OWNER TO FILL` must be completed by the project owner. Nothing here should be invented.

## 1. Agreement references
| Item | Value |
|---|---|
| Agreement reference ID | OWNER TO FILL |
| Date / version | OWNER TO FILL |
| Covered asset categories | OWNER TO FILL |
| Redistribution allowed? (public build / repo) | OWNER TO FILL |
| Allowed deployment (devices, regions) | OWNER TO FILL |
| Custodian / contact | OWNER TO FILL |

## 2. Storage rules
- Licensed assets live in `vendor/appleux/assets/` only.
- Exclude that directory from any public repository or public release unless Section 1 permits redistribution (`.gitignore` or separate private repository).
- Do not copy agreement documents into the repo.

## 3. Assets present
Populate after M0 audit. One row per asset (or per asset package).
| Asset / package | Category | Path in repo | Delivered by / date | Agreement ref | Format | Used in | Status |
|---|---|---|---|---|---|---|---|
| (none verified yet) | | | | | | | |

## 4. Assets needed
Request list for the project owner. Check off when delivered.
| Category | Needed for | Priority | Delivered? |
|---|---|---|---|
| System font family (SF) with all weights and optical sizes | Typography, all UI | P0 | No |
| System symbol/icon set | All UI | P0 | No |
| App icons for system apps | Launcher, Settings, system apps | P0 | No |
| Wallpapers (light/dark, lock/home) | Home, lock screen, setup | P1 | No |
| System sounds (ringtones, alerts, keyboard clicks, lock/unlock) | Sound and haptics | P1 | No |
| Boot animation / logo assets | Boot | P2 | No |
| Setup flow artwork | First boot | P1 | No |
| Motion references (spring curves, timing specs) | Motion spec | P0 | No |
| Haptic pattern references | Haptics | P2 | No |
| UI measurement references (layout, spacing, radii, colors) | Design tokens (`TBD-REF`) | P0 | No |
| Widget and Control Center artwork | SystemUI, Launcher | P1 | No |
| Keyboard layouts and artwork | IME | P1 | No |
| Localization and emoji assets (if covered) | Languages | P2 | No |

## 5. Android resource mapping
Record how each asset is packaged (fonts in `res/font`, vector or raster icons, raw sounds, overlay resources) and the resource names used by the design-system tokens.

## 6. Change log
| Date | Change | By |
|---|---|---|
| | | |

---

## ACTUAL STATE (2026-10-19) — extracted assets ledger

- **1,042 iOS 27 .car files analysed** (0 empty, 695 >100 KB, 297 >1 MB).
- Extracted: 75+ app icon PNGs, 6 symbol fonts, 366 .ogg sounds
  (linked into every APK via AppleUXSounds static_lib), PDF/SVG vectors.
- **Widget truth from IPSW**: 72 widget .car files contain ZERO rendered
  widget images — iOS widgets are SwiftUI views rendered at runtime. Fidelity
  comes from REBUILDING layouts as native views (hybrid host), not extraction.
- Fonts: AppleSymbols glyphs are PUA-only (no SF names) → vendored vectors +
  `sf27-catalog.json` (340 symbols) is the icon route; platform sans-serif
  for text.
- Extraction tool: `tools/car_extract.py` (BOM parser: PNG/JPEG/PDF/SVG/fonts).
- Legal (binding): IPSW-derived assets are personal/educational use only;
  distribution requires open-licensed replacements.