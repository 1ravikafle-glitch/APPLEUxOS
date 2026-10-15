# `design-reference/inbox/` — unvetted third-party intake

**Nothing in this directory is adopted, and nothing here is Apple-sourced.**
It is material received from outside the project, kept verbatim so it can be
argued with, edited, and re-checked later instead of being paraphrased into a
summary that loses its disagreements.

## Contents

| Path | What it is | Status |
|---|---|---|
| `ios27-android16-spec-2026-10-15.txt` | Verbatim copy of an externally generated "iOS 27 × Android 16 — Complete Surface Specification" (20 batches, 4651 lines, 220 KB). Received 2026-10-15. | **unvetted** |
| `extracted/screens/*.json` | One file per screen JSON block found in the spec. 51 files. | **unvetted** |
| `extracted/design-tokens.incoming.json` | The spec's token file, extracted for diffing. | **contradicted** — see below |
| `extracted/provenance.incoming.json` | The spec's own provenance block. | **partly false** — see below |
| `extracted/surfaces.incoming.json` | The spec's 640 summary-table rows, as data. | **useful**, after filtering |
| `extracted/backend-flags.json` | Rows whose declared AOSP backend is blocked / signature-gated / no-equivalent. | **actionable** |

## Re-derive after editing the spec

```bash
python3 scripts/parse-ios27-spec.py        # spec text  -> extracted/
python3 scripts/compare-glass-tokens.py    # token delta vs measured Apple kit
```

The parser is the only thing that reads the spec. Edit the prose, re-run, and
the data follows. Do not hand-edit anything under `extracted/`.

## The two things to know before reading anything here

1. **The spec's Liquid Glass values are wrong**, and checkably so. Apple's Figma
   UI kit exposes fills, drop shadows and corner radii through the REST API, so
   those numbers can be compared against a measured extraction instead of
   argued about. Five of six comparable fields disagree with
   `ios27-ds/packages/tokens/src/materials.json`, which was measured from kit
   27.0.2. `scripts/compare-glass-tokens.py` prints the comparison. Our shipped
   `LiquidGlassTokens.kt` matches the measured kit; the incoming file does not.

2. **`referenceScreenshot` URLs in the spec are fabricated.**
   `https://ipcdn-web.apple.com/iphone-27-user-guide/lock-screen.png` → HTTP 404,
   as does the `control-center.png` sibling. Real Apple asset URLs are of the
   form `ipcdn-web.apple.com/assets/v2/web/<uuid>`. Use
   `../ios27/official-guide/images/` instead.

## Naming

The spec is written against its own project names (`ios27_rock`, `IOS27Launcher`,
`IOS27SystemUI`, `com.ios27.*`). Ours are `appleux_rock`, `appleux-launcher`,
`appleux-systemui`, `com.appleux.*`. The verbatim copy keeps the original names
on purpose — renaming a third-party artefact in place destroys the ability to
diff it against the source. The mapping lives in
`deliverables/m20/intake-review-2026-10-15.md` § Naming.

## What survived the intake

The surface **inventory** (which surfaces exist, grouped by area) is worth
keeping, after the blocked and no-equivalent backends are removed. It is a
useful checklist against the replace-from-source sequence. The **code** in the
spec did not survive review — see the review's § Rejected.
## Figma data, local for future use (2026-10-15)

- SSD archive `/media/elfakiris/ElfakIris1/AppleUX-figma/`: July Apple kit dump
  (38 MB), October Drafts-copy dump (47 MB), `cc-modules.json`.
- Repo-sized extraction: `design-reference/ios27/figma-drafts-copy/`
  (meta/pages/components/styles/materials + README). Tokens re-verified
  identical October-vs-July; nothing changed in our code.
