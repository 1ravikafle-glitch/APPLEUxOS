# iOS 27 online research notes — firmware decryptability + design spec

Date: 2026-10-14. Research-only; no device work, no key markets, no paywalled content.
Scope note: `/home/elfakiris/OsProject/design-reference/ios27/sf27-catalog.json` was NOT re-done per instructions.

Method limits (affect confidence flags below):
- `theiphonewiki.com` and `theapplewiki.com` live were both unreachable at research time
  (`theiphonewiki.com` → CloudFront HTTP 502 on every page; `theapplewiki.com` → JS security check).
  Firmware-key findings below are verified against Internet Archive snapshots fetched 2026-10-14.
- `developer.apple.com` HIG and API-doc pages are JavaScript-gated (only a "requires JavaScript"
  shell is fetchable), so Apple-doc values could not be re-scraped today. Values below that match
  long-stable HIG content are flagged HIGH confidence; anything reconstructed from memory or
  community sources is flagged UNVERIFIED with the canonical source URL where it should be checked.

---

## PART A — Verdict: iOS 18+ `.aea` root-FS / Cryptex decryptability on Linux

**Verdict: YES — decryptable with public info (root FS + Cryptex), NO for SEP firmware.**

Evidence (The Apple Wiki key pages, via Wayback snapshots of 2026-10-14):

1. iOS 18.0 (RC + release), build 22A3354, iPhone12,1
   (`iPhone12,1_18.0_22A3354_Restore.ipsw`), live URL
   `https://theapplewiki.com/wiki/Keys:Crystal_22A3354_(iPhone12,1)`:
   - Root Filesystem: `044-37703-002.dmg.aea`, Key published (`7iww8d1D4j56Mkxyt789tV/r6DtpE7z7NHA3QRpFJ20`)
   - Cryptex1,SystemOS: `044-37624-002.dmg.aea`, Key published (`I/4oSTCpKifOvhWzj7nfE3dimfh3oLMSsr9sClm+aOY`)
   - SEP-Firmware (`sep-firmware.n104.RELEASE.im4p`): IV Unknown, Key Unknown — only KBAG + DevKBAG published.
2. iOS 18.6.1, build 22G90, iPhone12,1, live URL
   `https://theapplewiki.com/wiki/Keys:Crystal_22G90_(iPhone12,1)`:
   - Root Filesystem: `043-18757-101.dmg.aea`, Key published (`sRe+AFhFpFG5xuVsPqv22wK3d5IL/eW9iG2KBFAu62A=`)
   - Cryptex1,SystemOS: `043-18359-101.dmg.aea`, Key published (`Y/vWkXFpSes2b8ErnUfY0ncquNEdx0g/VnyEnUZF/fU=`)
   - SEP-Firmware: IV/Key Unknown again (KBAGs only). Page edited 2025-08-14 by FirmwareKeysBot.

Why this settles it: the old "no rootfs keys" story does not hold for these 18.x builds — both the
root-FS and the SystemOS cryptex are Apple Encrypted Archive (`.aea`) files whose **symmetric file
keys are published**, so no GID-key/AES-engine exploit, no KBAG cracking, and no device is needed.
The `.aea` key decrypts directly with public Linux tools (list below). What stays closed is the
SEP firmware: a KBAG without the IV/key is useless without Apple's GID key (hardware AES engine,
only reachable via bootrom/iBoot exploit), so SEP stays NO. Same logic applies to any build,
including a future/hypothetical "iOS 27.2 beta": decryptable **iff** its per-build `.aea` keys get
published on that build's `Keys:` page (59 such pages already exist for iPhone12,1 across 18.x,
from `Crystal_22A3354` through `CrystalSecurityLuckC_22H217`). Note: no public "iOS 27.2 beta"
exists at research time, so no keys page for it can exist yet. Post-decrypt, the inner payload is
an APFS DMG — plan for APFS-capable extraction on the Linux side (tooling for that step flagged
UNVERIFIED, not researched here).

Legitimate public Linux tools (all URLs verified live 2026-10-14, no key markets):

- `blacktop/ipsw` (Go, cross-platform incl. Linux) — README states: "Parse IMG4, decrypt AEA
  archives, and inspect iBoot" — the primary end-to-end option.
  https://github.com/blacktop/ipsw
- `0xilis/libNeoAppleArchive` (C, MIT) — "Cross-compat library for parsing Apple Archive +
  Apple Encrypted Archive (.aar/.yaa/.aea)". https://github.com/0xilis/libNeoAppleArchive
- `kinnay/AEA` (Python, MIT) — "Apple Encrypted Archive". https://github.com/kinnay/AEA
- `GavBog/aea-tools` (Rust, Apache-2.0) — "Utility for Apple Encrypted Archives".
  https://github.com/GavBog/aea-tools
- `m1stadev/PyIMG4` (Python, MIT, `pip install pyimg4`) — parses Apple's Image4 (IMG4/IM4P/IM4M)
  envelope around firmware files; works on Linux (uses `lzfse` lib off Apple platforms).
  https://github.com/m1stadev/PyIMG4
- `tihmstar/img4tool` + `Siguza/img4lib` — IMG4/IM4P manipulation (C, Linux-native).
  https://github.com/tihmstar/img4tool / https://github.com/Siguza/img4lib

---

## PART B — iOS design values → AppleUX tokens

Legend: HIGH = long-stable Apple-published value, corroborated by the token file; MED = confident
but not re-fetched today (JS-gated docs); UNVERIFIED = from memory/community, must be checked
against the source URL before changing code. Token paths are under
`appleux/design-system/src/main/java/com/appleux/design/tokens/`.

| iOS value | source URL | AppleUX target token | current AppleUX value | action |
|---|---|---|---|---|
| Type scale pt sizes — Large Title 34/41, Title1 28/34, Title2 22/28, Title3 20/24, Headline 17/22, Body 17/22, Callout 16/21, Subhead 15/20, Footnote 13/18, Caption1 12/16, Caption2 11/15 (HIGH) | https://developer.apple.com/design/human-interface-guidelines/typography | TypographyTokens.kt (all FontSpecs) | sizes + line-heights match exactly | KEEP sizes/line-heights; weights need review (next rows) |
| Large Title / Title1 / Title2 default weight Regular (400), NOT 200 (MED) | https://developer.apple.com/design/human-interface-guidelines/typography | TypographyTokens.kt largeTitle/title1/title2 `fontWeight` | 200 / 200 / 300 | ADJUST to 400 (verify on HIG page first — page was JS-gated today) |
| Title3 Regular 400, Headline SemiBold 600, Body/Callout/Subhead/Footnote/Caption Regular 400, emphasized variants SemiBold 600 (HIGH) | https://developer.apple.com/design/human-interface-guidelines/typography | TypographyTokens.kt | title3=400 ✓, headline=600 ✓, body/callout/subhead/footnote/caption1/caption2=400 ✓, emphasized=600 ✓ (headlineEmphasized=700 vs Apple 600 — 1-off) | KEEP; change headlineEmphasized 700→600 |
| SF tracking is baked into the font; HIG publishes no tracking table (HIGH) | https://developer.apple.com/design/human-interface-guidelines/typography | TypographyTokens.kt `tracking` fields (0.10–0.40) | custom values | KEEP but mark source as AppleUX-chosen, NOT Apple values |
| Dynamic Type: 7 text sizes (XS–XXXL, default = Large) + 5 accessibility sizes (AX1–AX5); Body 17pt scales per-category (UNVERIFIED percentages — commonly cited ≈ 82/88/94/100/112/124/141/165/188/212/235/259%, AX5 disputed) | https://developer.apple.com/design/human-interface-guidelines/typography | TypographyTokens.kt (no scaler exists) | no Dynamic-Type scaling table at all | ADD scaler later; DO NOT hard-code % until HIG table is read in a browser |
| systemBackground #000000 dark / #FFFFFF light (HIGH) | https://developer.apple.com/design/human-interface-guidelines/color | ColorTokens.kt systemBackground | #000000 / #FFFFFF ✓ | KEEP |
| secondarySystemBackground #1C1C1E / #F2F2F7 (HIGH) | https://developer.apple.com/design/human-interface-guidelines/color | ColorTokens.kt systemBackgroundSecondary | #1C1C1E / #F2F2F2 (≈, 1-digit off) | ADJUST light #F2F2F2→#F2F2F7 |
| tertiarySystemBackground #2C2C2E / #FFFFFF (MED) | https://developer.apple.com/design/human-interface-guidelines/color | ColorTokens.kt systemBackgroundTertiary | #262628 / #E6E6E8 | ADJUST dark #262628→#2C2C2E; verify light (#FFFFFF per Apple, token uses #E6E6E8) |
| systemGroupedBackground #000000 / #F2F2F7; secondarySystemGroupedBackground #1C1C1E / #FFFFFF (MED) | https://developer.apple.com/design/human-interface-guidelines/color | ColorTokens.kt systemBackgroundGrouped* (3 levels) | grouped #000000/#FFFFFF, secondary #0E0E0E/#F2F2F2, tertiary #141414/#E6E6E8 | RECONCILE to 2 Apple levels (drop custom tertiary; fix secondary dark #0E0E0E→#1C1C1E) |
| separator light ≈ #C6C6C8, dark opaque equiv. ≈ #38383A (HIGH-ish; Apple defines rgba(60,60,67,0.29) / rgba(84,84,88,0.6)) | https://developer.apple.com/design/human-interface-guidelines/color | ColorTokens.kt separator* | base #38383A/#C6C6C8 ✓ | KEEP base; apply 60%/29% alpha at draw time instead of opaque |
| label #FFFFFF/#000000; secondaryLabel ≈ #EBEBF5@60% / #3C3C43@60%; tertiary ≈ @30%; quaternary ≈ @18% (MED) | https://developer.apple.com/design/human-interface-guidelines/color | ColorTokens.kt label/secondaryLabel/tertiaryLabel/quaternaryLabel | #A8A8A8 / #6E6E73 etc. (opaque approximations) | KEEP (close); note Apple defines them as alpha blends |
| systemBlue #007AFF / #0A84FF; systemGreen #34C759 / #30D158; systemRed #FF3B30 both (HIGH) | https://developer.apple.com/design/human-interface-guidelines/color | ColorTokens.kt systemBlue/Green/Red | match exactly ✓ | KEEP |
| systemOrange #FF9500; systemYellow #FFCC00; systemPurple #AF52DE / #BF5AF2 (HIGH) | https://developer.apple.com/design/human-interface-guidelines/color | ColorTokens.kt | match exactly ✓ | KEEP |
| systemPink #FF2D55 / #FF375F (MED) | https://developer.apple.com/design/human-interface-guidelines/color | ColorTokens.kt systemPink | #FF2E83 both | ADJUST after verifying on HIG page (UNVERIFIED until read) |
| systemTeal #5AC8FA / #64D2FF (MED) | https://developer.apple.com/design/human-interface-guidelines/color | ColorTokens.kt systemTeal | #00C4D4 both | ADJUST after verifying (UNVERIFIED until read) |
| systemIndigo #5856D6 / #5E5CE6 (MED) | https://developer.apple.com/design/human-interface-guidelines/color | ColorTokens.kt systemIndigo | #5E5CE6 both | ADD light variant #5856D6 (verify first) |
| systemCyan #32ADE6 / #64D2FF; there is no `systemMint` in UIKit (SwiftUI-only `.mint`) (MED) | https://developer.apple.com/documentation/uikit/uicolor | ColorTokens.kt systemCyan/systemMint | #00C7BE both / #33D686 both | ADJUST cyan after verifying; DROP or rename mint (UNVERIFIED until docs read) |
| Dark-mode base is pure #000000 (HIGH) | https://developer.apple.com/design/human-interface-guidelines/dark-mode | ColorTokens.kt | darkMode default true, base #000000 ✓ | KEEP |
| Liquid Glass: Apple publishes NO numeric blur radius / saturation / brightness values — only material tiers (Glass `.regular`, `.clear`) and behavior (refraction, specular edge light, content-aware tint). Any number is community reverse-engineering (UNVERIFIED by definition) | https://developer.apple.com/videos/play/wwdc2025/219/ and https://developer.apple.com/design/human-interface-guidelines/materials | ColorTokens.kt overlays + ElevationTokens.kt | custom overlayDark/overlayLight alphas | DO NOT invent blur/saturation constants; implement glass as runtime blur + tint, keep overlay alphas as AppleUX-chosen |
| UIVisualEffectView blur styles (extraLight/light/dark, regular/prominent) have no public radius either (HIGH — no numbers in docs) | https://developer.apple.com/documentation/uikit/uivisualeffectview | (same as above) | n/a | no token action; do not copy iOS-26 glass numbers from blogs without marking UNVERIFIED |
| Minimum tappable target 44×44pt (HIGH) | https://developer.apple.com/design/human-interface-guidelines/layout | SizingTokens.kt TOUCH_TARGET_MIN | 44 ✓ | KEEP |
| iPhone screen edge margins 16pt compact / 20pt regular (MED) | https://developer.apple.com/design/human-interface-guidelines/layout | SpacingTokens.kt screenPadding (16/16) | horizontal 16 ✓ | KEEP horizontal; consider 20pt vertical on tall screens (verify first) |
| 8pt spacing grid (MED — de-facto Apple practice) | https://developer.apple.com/design/human-interface-guidelines/layout | SpacingTokens.kt GRID=8 | 8 ✓ | KEEP |
| App icon 1024×1024 master, continuous-corner squircle ≈ 22.4% radius (UNVERIFIED — community measurement, Apple publishes template not number) | https://developer.apple.com/design/human-interface-guidelines/app-icons | SizingTokens.kt APP_ICON_* (58/68/86) + IconTokens.kt | arbitrary 58/68/86 | VERIFY icon sizes against launcher grid; do not encode 22.4% until measured |
| Tab-bar glyph ≈ 25×25pt; toolbar ≈ 22×22pt; status-bar glyph ≈ 12–14pt (MED) | https://developer.apple.com/design/human-interface-guidelines/icons-and-images (now "App icons / SF Symbols" sections) | IconTokens.kt SYSTEM_ICON_SIZE_* (25/22/12) | 25 / 22 / 12 ✓ | KEEP |
| SF Symbols: 9 weights (ultralight→black) + 3 scales, NOT stroke-widths (HIGH) | https://developer.apple.com/sf-symbols/ | IconTokens.kt STROKE_WEIGHT_* (1.5/2.0/2.5/3.0) | non-Apple model | RENAME to WEIGHT_* on the 100–900 SF scale; keep px mapping as AppleUX-chosen |
| Sheet corner radius ≈ 10–12pt; grouped-cell radius ≈ 10pt; no Apple radius table exists (UNVERIFIED) | https://developer.apple.com/design/human-interface-guidelines/sheets + /layout | SizingTokens.kt CORNER_RADIUS_SHEET=22, CARD=16, set 8/12/16/20/24 | custom | KEEP as AppleUX-chosen; verify sheet 22→10/12 against screenshots before changing |
| SwiftUI default spring ≈ response 0.55s, dampingFraction 0.825 (UNVERIFIED — widely cited, docs JS-gated today) | https://developer.apple.com/documentation/swiftui/spring | MotionTokens.kt (no equivalent; springStandard is stiffness 1000/damping 50 → ζ≈0.79 but ω≈31.6 rad/s, ~3× stiffer/faster than Apple's default) | springStandard ζ 0.79 @ ω 31.6 | ADD `springDefault` matching response 0.55 / ζ 0.825 AFTER verifying docs; do not retune existing springs until then |
| Damping-ratio formula ζ = c / (2√(mk)) (HIGH — in Apple docs and standard physics; token file already implements it) | https://developer.apple.com/documentation/uikit/uispringtimingparameters | MotionTokens.kt SpringSpec.dampingRatio | implemented correctly ✓ | KEEP |
| iOS default ease-in-out ≈ cubic-bezier(0.42, 0, 0.58, 1); token's (0.42,0,0.2,1) is Android fast-out-slow-in, not iOS (MED) | https://developer.apple.com/documentation/uikit/uiview (animateWithDuration curve docs) | MotionTokens.kt easingStandard | (0.42,0,0.2,1) | ADD `easingIOSDefault` (0.42,0,0.58,1); keep existing as Android-tuned variant |
| HIG motion guidance ≈ 200–500ms for transitions; standard push/modal ≈ 350ms (UNVERIFIED — docs JS-gated) | https://developer.apple.com/design/human-interface-guidelines/motion | MotionTokens.kt DURATION_STANDARD=333, MODERATE=220, SLOW=500 | in range ✓ | KEEP durations |
| Nav-bar title 17pt semibold; large title 34pt; status bar / Dynamic Island layout values (UNVERIFIED — not fetched) | https://developer.apple.com/design/human-interface-guidelines/navigation-bars + /status-bar | SizingTokens.kt NAVIGATION_BAR_HEIGHT=44, STATUS_BAR_HEIGHT=24 | plausible | KEEP; verify against device metrics later |

Notes:
- Highest-value discrepancies to fix first (all need a 1-minute HIG-page check in a real browser):
  1. tertiary grouped/background colors (#262628 vs #2C2C2E; grouped light #FFFFFF vs #F2F2F7),
  2. pink/teal/cyan/mint hexes, 3. large-title weights 200→400, 4. iOS ease-in-out curve.
- Do not encode any Liquid Glass blur/saturation/radius number: Apple never published them, and
  the "iOS 27" values in community posts are estimates. Source of truth remains WWDC25 session 219
  (https://developer.apple.com/videos/play/wwdc2025/219/) + the HIG Materials page.
