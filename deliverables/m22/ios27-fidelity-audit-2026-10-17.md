# AppleUX parallel work — iOS 27 fidelity & ROM-viability audit

> **SUPERSEDED (2026-10-17 ~11:15).** Most of the defects below were fixed after this
> audit, and it contained two errors: the font blocker and the "accent regression"
> no longer apply as written. See
> **`deliverables/m22/ios27-fidelity-audit-r2-2026-10-17.md`** for the current state.

**Date:** 2026-10-17
**Auditor:** read-only review session
**Scope:** the parallel session's AppleUX work (design-system tokens, `design-preview/`
mockups, `deliverables/ios27-*.md`, commit `438c2ae`) compared against real iOS 27 —
Apple's published design (HIG) **and** the operator's own iPhone 11 iOS 27 photos in
`design-reference/iphone11-ios27/`.
**Mode:** read-only. **Nothing in the audited work was modified.** No fixes were applied.

---

## 0. Method, and one honest limitation

**This auditor cannot view images.** The image-read path rejects PNG input for this model,
so the real screenshots and the parallel work's renders were **not looked at visually**.
Instead they were **measured**:

- dominant-palette extraction and per-pixel sampling (PIL/numpy),
- edge detection to recover insets, card widths and radii,
- HTML/CSS source reading for the mockup spec,
- and direct comparison against Apple's published HIG values.

Where this report says "accurate", it means **the numbers match**, not "it looks right to
the eye". Subjective qualities — spacing rhythm, optical alignment, icon weight balance —
were not assessed and are marked as such.

---

## 1. What the parallel work actually consists of

| Artifact | Description | State |
|---|---|---|
| `design-preview/out/` | **51 surfaces**, HTML + PNG rendered at **1080×2408** | Device-exact **wireframes** |
| `appleux/design-system/` | 67 `.kt` — tokens + component library | Real code |
| `appleux/launcher/` | 31 `.kt` | Real code, wired |
| `appleux/settings/` | 12 `.kt` | Real code, wired |
| `appleux/systemui/` | 5 `.kt` | Real code, wired |
| `appleux/performance/` | 8 `.kt` | Real code |
| `deliverables/ios27-*.md` | 4 docs (integration, layout-mapping, gap-analysis, critical-fixes) | Claims — see §5.3 |
| commit `438c2ae` | glyph fixes + manifest + Android.bp | Committed |

**Critical reframing — the mockups are redlines, not visual renders.**
Reading the HTML source (`_settings-wifi.html`, `_settings-appearance.html`), every
`design-preview` file is `.stage { background:#1c1c1e }` plus absolutely-positioned
`.box { border:2px solid }` rectangles, colour-coded by component type. That is a
**Figma-style layout specification** ("redline"), and it is good at that job. It does
**not** demonstrate final appearance. The 85–90 % flat `#161618` measured at every centre
row is the box canvas, not iOS chrome.

> Consequence: the mockups answer *"is the geometry right?"* — they do **not** answer
> *"does it look like iOS?"*. Colour fidelity must be judged from the tokens, not the PNGs.

---

## 2. Fidelity vs. Apple's published design (the design tokens)

### 2.1 Colour — accurate, with one genuine bug 🔴

`ColorTokens.resolve(darkColor, lightColor)` — argument order is **(dark, light)** — and it
is used correctly throughout. Swatch-by-swatch against HIG:

| token | dark / light | vs HIG |
|---|---|---|
| `systemBlue` | `#0A84FF` / `#007AFF` | ✓ |
| `systemGreen` | `#30D158` / `#34C759` | ✓ |
| `systemRed` | `#FF453A` / `#FF3B30` | ✓ |
| `systemOrange` | `#FF9F0A` / `#FF9500` | ✓ |
| `systemYellow` | `#FFD60A` / `#FFCC00` | ✓ |
| `systemPurple` | `#BF5AF2` / `#AF52DE` | ✓ |
| `systemPink` | `#FF375F` / `#FF2D55` | ✓ |
| `systemTeal` | `#40C8E0` / `#30B0C7` | ✓ |
| `systemIndigo` | `#5E5CE6` / `#5856D6` | ✓ |
| `systemMint` | `#63E6E2` / `#00C7BE` | ✓ |
| `systemCyan` | `#64D2FF` / `#32ADE6` | ✓ |
| `systemBackground` | `#000000` / `#FFFFFF` | ✓ |
| `systemBackgroundSecondary` | `#1C1C1E` / `#F2F2F7` | ✓ |
| `systemBackgroundTertiary` | `#2C2C2E` / `#FFFFFF` | ✓ |
| `systemGroupedBackground` | `#000000` / `#F2F2F7` | ✓ |
| `secondarySystemGroupedBackground` | `#1C1C1E` / `#FFFFFF` | ✓ |

**Bug — the label ramp is one alpha step too opaque, and the broken column is the default.**

| token | code (dark / light) | HIG (dark / light) | verdict |
|---|---|---|---|
| `secondaryLabel` | `#EBEBF5FF` / `#3C3C43FF` | `#EBEBF599` / `#3C3C4399` | ✗ fully opaque; should be 60 % |
| `tertiaryLabel` | `#EBEBF599` / `#3C3C434D` | `#EBEBF54D` / `#3C3C434D` | ✗ dark is 60 %, should be 30 % (light ✓) |
| `quaternaryLabel` | `#EBEBF54D` / `#3C3C432E` | `#EBEBF52E` / `#3C3C432E` | ✗ dark is 30 %, should be 18 % (light ✓) |

`ColorTokens.isDarkModeEnabled` **defaults to `true`**, so the incorrect dark column is what
ships by default. Effect: secondary/tertiary text (Settings value text, subtitles, captions,
footers) renders visibly heavier than real iOS on every screen.

**Minor:** `separator` uses the *opaque* separator family (`#38383A` / `#C6C6C8`) rather than
HIG `separator` (light `rgba(60,60,67,.36)`, dark `rgba(84,84,88,.65)`). Hairline-only impact.

### 2.2 Typography — metrics correct; typeface is not 🟠

Every style matches Apple's Dynamic Type table exactly:

| style | size / line-height | vs HIG |
|---|---|---|
| largeTitle | 34 / 41 | ✓ |
| title1 | 28 / 34 | ✓ |
| title2 | 22 / 28 | ✓ |
| title3 | 20 / 25 | ✓ |
| headline | 17 / 22 semibold | ✓ |
| body | 17 / 22 | ✓ |
| callout | 16 / 21 | ✓ |
| subheadline | 15 / 20 | ✓ |
| footnote | 13 / 18 | ✓ |
| caption1 | 12 / 16 | ✓ |
| caption2 | 11 / 15 | ✓ |

Tracking values are plausibly sourced and self-consistently documented.

**But `FONT_FAMILY = "system"`** — it renders in the platform sans-serif (Roboto), **not
SF Pro**. The file states this honestly (SF Pro cannot be redistributed in an APK). This is
the single largest reason the ROM will not *look* pixel-identical to iOS: weight and width
match, **letterforms do not**, at every size.

### 2.3 Layout geometry — accurate and device-exact ✅

Measured against the operator's real photo (`settings-root-top.png`, 591×1280; iPhone 11 =
414×896 pt → **1.427 px/pt**):

| measurement | real iOS 27 | parallel mockups |
|---|---|---|
| list inset | 28 px = **19.6 pt** | 54 px = **19.6 dp** ✓ |
| card width / screen | 535 / 591 = **90.5 %** | 972 / 1080 = **90.0 %** ✓ |
| grouped background | `#F2F1F7` | token `#F2F2F7` ✓ |
| card fill | `#FFFFFF` | token `#FFFFFF` ✓ |

The mockups also hard-encode **our exact device** — the strongest ROM-fit signal in the work:

| mockup CSS | value | our device |
|---|---|---|
| `.inset-top` | `height:76px` | 76 px top inset ✓ |
| `.inset-bottom` | `height:127px` | 127 px bottom inset ✓ |
| `.cutout` | `left:490px; width:100px; height:76px` | 100×76 @ centre 540 ✓ |
| `.stage` | `border-radius:106px` | 106 px corners ✓ |
| canvas | `1080×2408` | 1080×2408 ✓ |

### 2.4 Iconography ✅

95 tracked `VectorDrawable`s named `il_ic_*` (SF-Symbol-weight line icons) — real files,
correct resource names. `R.drawable.*` references resolve: 98 refs / 123 files, only 7
unresolved and all are `android.R.drawable.*`. Real SF Symbols fonts are present
(`Symbol.ttf`, `AppleSymbols.ttf`) but **unused** (`R.font.` refs = 0).

### 2.5 Motion 🟠

`MotionTokens.kt` **self-disclaims**: its durations are "Material Design 3's standard scale…
NOT iOS values and must not be presented as such". Only
`easingIOSDefault = cubic-bezier(0.42,0,0.58,1)` is genuinely iOS. Any claim of
spring-physics parity is therefore over-claimed. (The compensating override is the
`apple_hig.xml` / `integers.xml` motion-duration work.)

---

## 3. Measured comparison vs. the operator's real screenshots

The reference set is **light-mode Settings** but **dark-mode** home / lock / Control Center:

| reference file | size | measured |
|---|---|---|
| `settings-root-top.png` | 591×1280 | light; `#FFFFFF` 49.2 %, `#F2F1F7` 25.6 % |
| `home-live.png` | 556×913 | dark; `#000000` 18 %, gold `#FFCC6A` (wallpaper) |
| `control-center-live.png` | 556×913 | dark; glass greys `#646464` / `#5F5F62` / `#949494` |
| `lock-screen-live.png` | 556×913 | dark; `#000000` + gold `#FFCE77` |

The parallel mockups are **dark-only**. Consequence: **there is no mockup that can be checked
against the operator's own Settings photos** — the one surface with ground truth is the one
surface with no matching render. This is a coverage gap, not an error.

---

## 4. Will it work in the custom ROM? — **not yet**

### 4.1 🔴 Hard compile break — six illegal font resource names
`appleux/design-system/src/main/res/font/`:
`AppleSymbols.ttf`, `SFCamera.ttf`, `SFUI.ttf`, `SFUIItalic.ttf`,
`SFUISymbols-Regular.otf`, `Symbol.ttf`.
`res/font/` requires `[a-z0-9_.]`; uppercase and `-` are rejected by `aapt2`. This fails the
`design-system` compile, which every other module depends on — **it blocks the entire ROM**.
All six are untracked and unreferenced.

### 4.2 🔴 Functional regression — RRO overrides deleted
Uncommitted, in `appleux/rro-frameworks/res/values/`:
- `colors.xml` — real `accent_device_default_light` (`#007AFF`) and `accent_device_default_dark`
  (`#0A84FF`) **deleted**, replaced by 17 inert `appleux_*` colours.
- `dimens.xml` — real `dialog_corner_radius` (`13dp`) **deleted**, replaced by inert tokens.
- `bools.xml` — 5 inert `appleux_*` bools.

An RRO can only override resource names that already exist in framework-res. `appleux_*`
does not, so it **overrides nothing** — the accent colour and dialog radius silently regress
to stock AOSP. The motion durations in `integers.xml` survived (untracked).

### 4.3 🔴 Asset integration is void — the report's claims are false
`deliverables/ios27-integration-report.md` states **"Status: COMPLETE"** with
460 icons / 4,884 SVG / 64 JPEG / 1,384 PDF *integrated*. Measured reality:

| claim | reality |
|---|---|
| 4,884 SVG integrated | `*.svg` under `appleux/` = **0** |
| 460 app icons | `ios-app-icons/` = **gone** |
| 54 CC tiles | `ios-cc-tiles/` = **gone** |
| 64 wallpapers | `raw/ios-wallpapers/` = **gone** |
| — | remaining: **95 drawables + 6 fonts** |

Every count is of the *raw catalog dump*, which was itself arbitrary (`Setup.app` PNGs,
framework-bundle SVGs, `ChatKit.framework_Assets_*.jpeg`) and has been removed. The document
should not be cited.

### 4.4 🟢 Genuinely ROM-ready
- The design system **is wired**, not merely mocked: `com.appleux.design` imported by
  **launcher (94 refs / 23 files)**, **settings (17 / 7)**, **systemui (12 / 4)**;
  `AppleuxTheme.applyToActivity` / `applySystemBars` called from real activities.
- Colours, typography metrics, spacing ramp and 95 vector icons are real, correctly named,
  and compile-clean.
- Mockup geometry is derived at our measured frame — it will sit correctly on the panel.

### 4.5 🟠 Implementation lags the spec
51 mockup surfaces exist; the implemented shells do not. `ContactsActivity.kt` is 283 lines
of raw stock `Button`/`EditText`/`TextView` with no `AppleuxAppChrome.apply`;
Calendar/Camera/Files/Browser/Photos are 37–56 lines each. There is **no in-tree Apple-HIG
screen template** and **no text-field / search-bar / segmented-control component**. The
mockups are substantially *ahead* of the code — they are a target, not a description.

### 4.6 Gaps closed by this work (crediting the parallel session)
- `AppleuxDisplayInsets.applyTopInset` **is** now applied (`AppleuxNotificationShade.kt:93`) —
  the AGENTS.md "Notification Center does not reserve the cutout" gap is closed.
- `reduce_motion` **is** now consumed (`AppleuxSheet`, `AppleuxNotificationBanner`,
  `LauncherActivity`) — the AGENTS.md "no component consumes it" gap is closed.

---

## 5. Fix list — what, why, how

> Severity: **P0** = blocks or silently regresses the ROM · **P1** = visual fidelity ·
> **P2** = correctness/documentation · **P3** = hygiene · **P4** = the real project gap.

### P0 — Blocker or silent regression

**1 · Six illegal font resource names**
- **Why:** `aapt2` rejects uppercase and `-` in resource file names → `design-system` cannot
  compile → whole ROM blocked.
- **How:** rename in place to `applesymbols.ttf`, `sfcamera.ttf`, `sfui.ttf`, `sfuiitalic.ttf`,
  `sfuisymbols_regular.otf`, `symbol.ttf`. Nothing else changes (`R.font.` refs = 0). To keep
  original names, move to `assets/fonts/` and load via `Typeface.createFromAsset`.
  **Do not delete** `symbol.ttf` / `applesymbols.ttf` (genuine SF Symbols).

**2 · RRO accent colours deleted**
- **Why:** RRO can only override existing names; `appleux_*` overrides nothing → ROM loses its
  iOS accent and reverts to stock AOSP on all toggles/links/selection.
- **How:** additive — keep the `appleux_*` block **and** re-add:
  ```xml
  <color name="accent_device_default_light">#FF007AFF</color>
  <color name="accent_device_default_dark">#FF0A84FF</color>
  ```
  Confirm the exact expected name/format first:
  `grep -n 'accent_device_default' android-16/frameworks/base/core/res/res/values/colors.xml`.

**3 · RRO dialog corner radius deleted**
- **Why:** same RRO rule → every dialog falls back to the stock AOSP radius.
- **How:** re-add `<dimen name="dialog_corner_radius">13dp</dimen>` alongside the tokens.

**4 · Inert `bools.xml` + untracked overlay values files**
- **Why:** (a) the 5 `appleux_*` bools override nothing — a false dependency; (b) untracked
  files are one `git checkout`/`reset` from silent loss (a prior `apple_hig.xml` tail was
  already lost this way).
- **How:** delete `bools.xml` or rename its keys to real framework-res bool names; then
  `git add` `bools.xml`, `integers.xml`, and both device `rro_overlay/*/res/values/apple_hig.xml`.

### P1 — Visual fidelity

**5 · Label ramp one alpha step too opaque**
- **Why:** `isDarkModeEnabled` defaults `true`, so the broken dark column ships. Secondary/
  tertiary text renders heavier than iOS on every screen. Most pervasive visual deviation
  measured.
- **How:** exact replacement —
  - `secondaryLabel` → `#EBEBF599` / `#3C3C4399`
  - `tertiaryLabel` → `#EBEBF54D` / `#3C3C434D`
  - `quaternaryLabel` → `#EBEBF52E` / `#3C3C432E`
  Spot-check any call site using a label colour as an opaque fill (alpha behaves differently
  as a fill than as text colour).

**6 · `separator` uses the opaque family**
- **Why:** row hairlines read heavier than iOS and don't let the background through.
- **How:** `separator` → `#545458A6` (dark, 0.65) / `#3C3C435C` (light, 0.36). Leave
  `separatorPrimary` as the opaque variant (already correct).

**7 · Typeface is Roboto, not SF Pro**
- **Why:** largest single visual difference from real iOS — letterforms, not metrics.
- **How:** bundle an open-licensed SF-class face (Inter OFL, or Roboto Flex) in
  `design-system/src/main/res/font/` with lowercase names, and point `FONT_FAMILY` at it,
  keeping `"system"` as fallback. **Do not bundle SF Pro** (Apple licence). Trade-off:
  +1–2 MB APK.

### P2 — Correctness / documentation

**8 · `deliverables/ios27-integration-report.md` is false**
- **Why:** a false "COMPLETE" suppresses the work still needed and is currently cited.
- **How:** prepend `**Status: VOID — superseded 2026-10-17. The "Integrated" column no longer
  exists in the tree (SVG=0; app-icons/cc-tiles/wallpapers removed). Retained for extraction
  inventory only.**` Do not delete — the raw extraction counts remain useful history.

**9 · Motion durations are Material, not iOS**
- **Why:** presenting them as Apple motion degrades trust in the spec.
- **How:** keep the in-file disclaimer; correct any doc claiming spring-physics parity.

**10 · `EXPAND_WINDOWS` is not a real permission**
- **Why:** phantom no-op that falsely implies status-bar expansion is granted through it.
  (`EXPAND_STATUS_BAR`, also declared, **is** real.)
- **How:** delete the `android.permission.EXPAND_WINDOWS` line in
  `appleux/systemui/src/main/AndroidManifest.xml:6`; keep `EXPAND_STATUS_BAR`.

### P3 — Hygiene

**11 · Five empty `res/mipmap-*/ios-launcher/` directories**
- **Why:** subdirectories inside `res/<type>/` are invalid structure; empty ones are likely
  ignored by `aapt2`, but the instant a file lands there the build breaks.
- **How:** `rmdir` all five (empty; safe) —
  `mipmap-mdpi|hdpi|xhdpi|xxhdpi|xxxhdpi/ios-launcher`.

**12 · Untracked overlay/config files (fragility)**
- **Why:** one reset away from silent loss.
- **How:** `git add` device `rro_overlay/*/res/values/apple_hig.xml` (×2), outer
  `appleux/rro-frameworks/res/values/{bools,integers}.xml`; inner repo also has modified
  `device.mk`, `permissions/privapp-permissions-appleux.xml` and untracked `appleux.mk`.

**13 · Six font files unreferenced (~9 MB dead)**
- **Why:** either they earn their keep or they are dead weight on a 3 GB-RAM / 64 GB budget.
- **How:** after lowercasing (#1), reference `R.font.symbol` from the icon component; move
  unused ones to `assets/` or drop them.

### P4 — The real gap

**14 · 51 mockup surfaces, almost none implemented**
- **Why:** tokens and layout are accurate, but the device will not look like iOS until the
  screens are built. This is the difference between "the spec is right" and "the ROM looks
  right".
- **How, in order:** (1) add the three missing primitives (text field, search pill, segmented
  control) to `design-system`; (2) build one reusable Apple-HIG screen template; (3) implement
  **Contacts → Phone → Messages**, one at a time, compile-verifying each with
  `scripts/build-emulator-apks.sh` (no emulator) before starting the next.

---

## 6. Summary

| # | Defect | Severity | Effort |
|---|---|---|---|
| 1 | 6 illegal font names | **Blocks build** | minutes |
| 2 | RRO accent deleted | **Silent regression** | minutes |
| 3 | RRO dialog radius deleted | **Silent regression** | minutes |
| 4 | Inert bools + untracked overlays | Fragility | minutes |
| 5 | Label ramp alpha | **Default-theme fidelity** | minutes |
| 6 | `separator` wrong family | Minor fidelity | minutes |
| 7 | Roboto instead of SF Pro | **Largest visual gap** | hours |
| 8 | False "COMPLETE" report | Trust | minutes |
| 9 | Motion mislabelled | Trust | minutes |
| 10 | Phantom permission | Correctness | minutes |
| 11 | 5 empty res dirs | Trap | seconds |
| 12 | Untracked overlay files | Data loss | minutes |
| 13 | 6 unused fonts | Bloat | minutes |
| 14 | Spec ≫ implementation | **The actual project** | days |

Items **1–6, 8, 10, 11** are mechanical and would take well under an hour together; they
unlock both the build and the default-theme fidelity.

---

## 7. Verdict

| Question | Answer |
|---|---|
| Follows **Apple's official design**? | **Yes, at token level.** Colours and type metrics are faithful HIG transcriptions with honest provenance notes. |
| Matches the **real iOS 27 screenshots**? | **At geometry and colour, measured — yes** (inset 19.6, card 90 %, `#F2F2F7`/`#FFFFFF`). **Not visually confirmable** — mockups are dark-only wireframes; the Settings ground truth is light-mode. |
| Will it **look** like iOS on the device? | **Not fully** — Roboto ≠ SF Pro, and the dark-mode label ramp is too heavy by default. |
| Will it **work** in the custom ROM? | **Not as-is.** One compile blocker (fonts), one silent functional regression (RRO accent/radius), one voided asset integration. Wiring is real, but the shipped UI is far behind the mockups. |

---

## 8. Caveats on this report

- Images were **measured, not viewed** (§0). Composition, rhythm and icon-weight balance were
  not assessed.
- Verification is **static**. No emulator or device run was performed (forbidden), so every
  finding is source/asset-level, not runtime-verified.
- The reference photo set is **light-mode Settings + dark-mode home/lock/CC**; dark-mode
  Settings could not be checked against ground truth.
- No file in the audited work was modified by this audit.
