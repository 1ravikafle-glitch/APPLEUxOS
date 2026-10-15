# Figma Drafts copy — local extraction for future use

Full 47 MB REST dump lives on SSD (not in git):
`/media/elfakiris/ElfakIris1/AppleUX-figma/drafts-copy-ios27-2026-10-15.json`

This directory holds the structured, repo-sized extraction:

| file | contents |
|---|---|
| `meta.json` | kit name, version `2410487113917420675`, modified 2026-10-15, source |
| `pages.json` | 46 pages (July kit had 45; **added: Safe Areas and Margins**) |
| `components.json` | 1506 components + 223 sets, id → name (the Assets panel shows a curated 322-subset; REST sees all) |
| `styles.json` | 86 shared styles |
| `materials.json` | full fills/effects/radii for Regular Large/Medium/Small + Clear |

## Verified against our tree (2026-10-15)

- October glass values are **identical** to the July dump: Regular fill
  #FFFFFF@0.70 LIGHTEN + #BFBFBF@0.10 DARKEN, shadow 48/8/0.25, cr 34, GLASS
  effect present-but-unexposed. `LiquidGlassTokens.kt` stands unchanged.
- Safe Areas page is new since July: layout-margin / safe-area-inset /
  reserved-region overlays plus per-device inset components (iPhone Duo
  portrait/landscape, sheets inner/outer). Inset numbers for our frame still
  come from the device (`dumpsys window`), not from iPhone figures — the page
  is a structural reference, not a value source.

## Rules carried forward

- Numbers transcribed here may enter tokens with a citation. Apple artwork,
  vectors, fonts, and images from the dump never enter the repo or any APK.
- The `figd_*` tokens that fetched this are REST credentials: they stay out
  of git, out of docs, out of shell history files committed anywhere.
