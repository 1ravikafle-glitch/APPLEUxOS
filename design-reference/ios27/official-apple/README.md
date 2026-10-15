# Official Apple design sources for iOS 27

Everything here was fetched from Apple's own servers
(`devimages-cdn.apple.com`) or extracted from Apple-authored files.
Nothing was eyeballed, reconstructed, or invented.

## The Figma UI Kit — NOT obtained

The iOS 27 UI Kit that `developer.apple.com/design/resources/` points at:

| Platform | Figma | Sketch |
|---|---|---|
| iOS/iPadOS 27 | `figma.com/community/file/1651309003795292092/ios-and-ipados-27` | `sketch.com/s/04c24d8b-38fb-4afb-8836-36617e022f02` |

**Retrieval failed, and the reason is not a workaround problem:**

- `developer.apple.com` and the Sketch page resolve fine (HTTP 200).
- Figma's CDN returns **HTTP 403 "Request blocked"** to every non-browser
  client, including the oEmbed API (`/api/oembed` rejects the URL outright).
- The harness desktop browser is not connected in this session
  (`browser.disconnected`), so the kit could not be opened either.

**What unblocks it:** open the session in the desktop app so the browser
tool attaches, then sign in to Figma and accept the kit's licence on
Apple's behalf. The file is a community file: the geometry inside it is
what we want, and the licence governs redistribution, not reading it for
reference while building an Android OS.

Until then the squircle and icon work below uses Apple's *other* official
artefacts, which are served without authentication.

## What IS retrievable (all from Apple's CDN)

| Artefact | URL | Status |
|---|---|---|
| iOS 27 Icon Templates (PSD/AI) | `.../iOS-27-Icon-Templates-Photoshop-Illustrator.dmg` | **fetched** |
| SF Symbols 27 | `.../SF-Symbols-27.dmg` | already harvested, see `../` |
| SF Pro / New York | `.../SF-Pro.dmg`, `.../NY.dmg` | reachable (large, not yet fetched) |

## Key measured result: the icon squircle

`App Icon Template.ai` is a PDF. It contains 27 paths: one icon outline and
26 guide circles. The outline is **path #3, 40 cubic Bézier segments, on a
1024 x 1024 grid**.

Measured against candidate models (`scripts/validate-squircle.py`):

| Model | mean abs pixel diff vs Apple's outline |
|---|---|
| rounded rect, r = 0.2836 | 0.0088 |
| **superellipse, n = 4.4** | **0.0055** |

So the iOS 27 icon shape is a **continuous-curvature superellipse with
n ≈ 4.4** — not a rounded rectangle. The corner is visibly fuller than a
circular arc (see `squircle-validation.png`, zoomed corners).

This corrected a shipped assumption. `AppIconView.kt` previously clipped
with a hand-picked rounded rect at 28% radius and a comment claiming
"Apple publishes a template, not a number". Apple does publish the outline,
and a rounded rect is the wrong shape.

## Files

| File | What it is |
|---|---|
| `iOS-27-Icon-Templates.dmg` | Apple's download, unmodified |
| `iOS-27-Icon-Templates-Photoshop-Illustrator/` | extracted contents |
| `squircle-path-1024.json` | path #3, Apple's coordinates |
| `squircle-mask-1024.png` | flattened outline render |
| `icon-template-paths.png` | contact sheet of all 27 paths (identifies #3) |
| `squircle-validation.png` | Apple vs rounded-rect vs superellipse |
| `apple-squircle-mask.xml` | VectorDrawable, 24x24 viewport |
| `scripts/extract-apple-squircle.py` | .ai → JSON |
| `scripts/render-apple-squircle.py` | JSON → PNG + VectorDrawable |
| `scripts/measure-squircle-corner.py` | radius + exponent fit |
| `scripts/validate-squircle.py` | model comparison + score |
| `scripts/generate-apple-squircle-kt.py` | JSON → Kotlin for the launcher |

## Still unresolved

The **control spacing, type scale, component metrics and colour values**
that only exist inside the Figma UI Kit have NOT been sourced. Current
values in `design-system/tokens/` remain AppleUX-authored. That is the next
blocker to clear, and it needs the Figma file.
---

## Third-party sources evaluated (2026-10-15)

| Source | License | Verdict |
|---|---|---|
| `zetadev0/Figma-ui-ios-27` | none stated | **Rejected.** Its own README says "I completely *recreated* the iOS 27 interface". It is not Apple's kit, and it ships no assets — only a link to the author's Figma file. Lower authority than the Apple files already banked here. |
| `seunghan91/ios27-design-system` | MIT | **Used.** Transcribes Apple's official June 2026 kits into tokens. Banked at `../ios27-ds/`. Source of `LiquidGlassTokens.kt`. |
| `Lakr233/vphone-cli` | — | **Unusable.** Runs iOS VMs on a physical Apple Silicon Mac; this host is Linux x86_64. |
| `blacktop/ipsw` | MIT | **Parked.** Good firmware/binary toolkit, better than the ad-hoc decrypt agent for future asset harvests. Contains no design specs, so it does not close the spacing/type-scale gap. |

### What the MIT transcription actually gave us

`packages/tokens/src/materials.json` marks values `_measured` where they come
from the kit's own layers, and states plainly that Apple publishes **no numeric
spec** for the Liquid Glass material and that the Figma kit only approximates
it. Taken at face value:

| Token | Value |
|---|---|
| Regular corner radius | **34pt** (Large and Medium identical in 27.0.2) |
| Regular fill, light | `#FFFFFF @ 0.70` |
| Regular fill, dark | `#1A1A1A @ 0.70` |
| Drop shadow | blur 48, offsetY 8, `#000000 @ 0.25` light / `0.45` dark |
| Rim | spread 0.5, sideOffset 1.25, sideSpread −0.75, `#DBDBDB` light |
| Inner shadow | blur 10, offsetY ±40, spread −40, `#282828` light |
| Clear variant | radius 23, base `#101010`, overlay `#FFFFFF @ 0.04` |
| Transparency slider | t∈[0,1], default 0.5; opacity 1.6−1.1t, blur 0.5+0.8t |

These replaced three invented constants: the CC card's 28f corner radius, the
`64 + level*96` scrim ramp, and `DEFAULT_LEVEL = 70` (now derived from the
kit's 0.5 default). `MAX_BLUR_RADIUS_PX` stays AppleUX-tuned — the kit has no
counterpart and the 3 GB budget needs it.

The slider curves are the transcription's own web model, not Apple values; it
validates itself against a native screenshot at t=0.5 (0.735 modelled vs
0.729 measured).

## Still blocked: control spacing, type scale, component metrics

These exist only inside the Figma file. Two independent blockers:

1. `figma.com` returns **403** to every non-browser client; the oEmbed API
   rejects the URL. Brave is installed here but **no browser is attached to
   the session**, so the browser tool cannot open it.
2. Even logged in, extracting precise geometry needs the **Figma REST API
   with a personal access token**, not a rendered page.

Needed to close it — either one:
- attach the desktop app so the browser tool connects, or
- supply a Figma personal access token for `api.figma.com`
