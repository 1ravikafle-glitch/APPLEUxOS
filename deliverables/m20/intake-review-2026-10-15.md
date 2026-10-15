# m20 — Intake review: the iOS 27 × AOSP 16 surface specification

Date: 2026-10-15
Input: `design-reference/inbox/ios27-android16-spec-2026-10-15.txt`
      (externally generated, 220 KB, 20 batches, received 2026-10-15)
Verdict: **inventory — adopt after filtering. Token file — reject. Code — reject.
Device tree — reject. Build/flash instructions — reject as untested.**

Everything below was checked against this tree or against a measured
extraction. Where something could not be checked, it says so.

---

## 1. What it actually contains

Counted by `scripts/parse-ios27-spec.py`, not estimated:

| | |
|---|---|
| Batches | 20 |
| Batch headers declare | 664 surfaces |
| Summary-table rows | 640 |
| **Unique surface ids** | **630** |
| Screen JSONs extracted | 51 |
| Token file / provenance block | both extracted |

Three counting problems in its own self-description:

- The verification checklist claims "400+ surfaces". The spec's own batch
  headers total **664**. The headline undercounts its own inventory by ~40%.
- **10 ids appear in two batches**: `action-sheet`, `popover`,
  `dictation-overlay`, `text-selection-menu`, `text-selection-magnifier`,
  `settings-action-button`, `settings-action-button-function`,
  `settings-action-button-shortcut`, `settings-standby`,
  `settings-standby-night-mode`. Either the inventory or the batch boundaries
  are wrong; both cannot be right.

## 2. Token file — rejected

`scripts/compare-glass-tokens.py`. The spec says its values come from Apple's
Figma UI Kit. Apple's kit exposes fills, drop shadows, inner shadows, background
blurs and corner radii through the Figma REST API, so those numbers are
**checkable** rather than a matter of taste. Compared against
`ios27-ds/packages/tokens/src/materials.json`, measured from kit 27.0.2 via
REST on 2026-07-29:

| field | incoming | Apple kit (measured) | ours | verdict |
|---|---|---|---|---|
| corner radius | 34 | 34 | 34 | all agree |
| fill alpha (light) | **0.14** | 0.70 | 0.70 | incoming wrong |
| shadow blur | **20** | 48 | 48 | incoming wrong |
| shadow offsetY | **4** | 8 | 8 | incoming wrong |
| shadow alpha (light) | **0.35** | 0.25 | 0.25 | incoming wrong |
| shadow alpha (dark) | **0.35** | 0.45 | 0.45 | incoming wrong |
| dark glass fill | **#1C1C1E** | #1A1A1A | #1A1A1A | incoming wrong |

**5 of 6 comparable fields disagree with the measured kit**, and our shipped
`LiquidGlassTokens.kt` agrees with the kit on all six. The incoming file is not
an improvement over what we already have; on the one axis that can be verified
it is strictly worse.

Two further values have **no measured counterpart at all** and must never be
described as Apple-sourced:

- `blurRadius: 30`. Figma's `GLASS` effect does not expose its radius over
  REST. `ios27-ds` says so in its own `_figmaGlassEffect` note. This number is
  invented.
- `rimAlpha: 0.28`. The kit models the rim as spread-shadow rings, not an
  alpha. Our `LiquidGlassTokens.kt RIM_*` constants are the honest form.

The colour/typography/radius halves of the token file were **not** compared
field by field — there are ~60 of them and each needs its own check. They are
not adopted and not rejected yet. Nothing from this file has been written into
`appleux/design-system/`.

## 3. Provenance block — partly false

```json
"figmaKitReachable": true
```
Defensible, and narrower than it sounds: Apple's kit *is* reachable through the
Figma **REST API with a token** — `ios27-ds` did exactly that on 2026-07-29.
What is not reachable is an anonymous HTTP fetch of a kit URL, which returns
403. The spec's phrasing conflates the two.

The falsifiable part is elsewhere, and it is worse:

```
"referenceScreenshot": "https://ipcdn-web.apple.com/iphone-27-user-guide/lock-screen.png"
```
**HTTP 404.** Same for the `control-center.png` sibling. These URLs were
constructed, not observed. Real Apple asset URLs are
`ipcdn-web.apple.com/assets/v2/web/<uuid>`. We already recorded this failure
mode once; it has now arrived again inside a document that claims
"No guesses".

## 4. Geometry — one hard contradiction

Most repeated frames are legitimate (sibling cards carry frames relative to
their own parent). One is not. In `quick-settings.json`, two **top-level**
elements declare the identical absolute rect:

```
brightness   slider  {"x":0.021,"y":0.55,"w":0.31,"h":0.35}
toggles      grid    {"x":0.021,"y":0.55,"w":0.31,"h":0.35}
```

A brightness slider and the toggle grid cannot occupy the same rectangle. One
of them is misplaced. Anyone rendering this file gets one of them on top of the
other.

Separately, every coordinate is a **decimal fraction of the screen**, but the
spec also mixes absolute point values in the same objects (`diameterPt: 32`,
`size: 80`, `cornerRadiusPt: 13`). There is no stated rule for which frame a
value is relative to, and no unit for `Pt` versus `dp`. At 411.43 dp derived
width for rock, "32 pt" and "32 dp" are not the same number.

## 5. Backend mappings — what actually works

Checked against `frameworks/base` and `build/make` in this tree. 640 rows, of
which:

| verdict | rows | meaning |
|---|---|---|
| `no-equivalent` | 14 | Apple's service has no AOSP counterpart. Siri (3 surfaces), Apple Pay/Wallet, Matter/Thread as "Home", iCloud, Find My. |
| `blocked` | 2 | The API cannot be called by the mapped component. `wipeData` → both "Erase iPhone" and "Erase All Content". |
| `signature` | 3 | Works only because we replace the app in-tree. |
| `warn` | 51 | Mapped to something that does not exist in AOSP, or is not equivalent. |

The two that matter most:

**"Clear All" cannot live in an app.** `NotificationManagerService`
(line 4430) does `checkCallerIsSystemOrSameApp(pkg)`. A Notification Center
Clear All must be in SystemUI or another system-signed component. We already put
ours in SystemUI, so this is a constraint to keep, not a defect to fix — but it
means the spec's "replace Settings/SystemUI with `IOS27*` apps" framing is not
free.

**`getImei()` is fine, for exactly one reason.** It is
`@RequiresPermission(READ_PRIVILEGED_PHONE_STATE)`. I expected this to be a
blocked mapping and checked: AOSP Settings *does* declare it
(`packages/apps/Settings/AndroidManifest.xml:58`). So the About screen works —
**only because the replace-from-source strategy puts our code inside the
platform-signed Settings privapp**. Move it to any ordinary app and it breaks.
That is the strategy paying for itself, and it is worth writing down.

`wipeData` has no such escape: it requires a device owner / profile owner /
DO-NOT-KILL app. A Settings row cannot offer "Erase iPhone".

## 6. The new Figma link

`https://www.figma.com/design/WV8XFPk0oYZT1zMly5OoFu/Untitled` — what I could
and could not do with it:

| attempt | result |
|---|---|
| anonymous `GET` | 403 |
| `GET` with browser UA | **200**, 759 KB HTML |
| `oembed` | works — key `WV8XFPk0oYZT1zMly5OoFu`, title `Untitled` |
| `api.figma.com/v1/files/:key` | **403** — no token on this box |
| document geometry in the HTML | **none** — only `blank.png`; Figma streams the document over a multiplayer websocket after bootstrap |
| desktop browser (`browser.tabs.open`) | `browser.disconnected` — no desktop browser is attached to this session |

Two things follow. The file key is **not** Apple's community kit
(`1651309003795292092`), and it is titled `Untitled` — so I am not going to
assume what it contains. To extract from it, one of:

1. A Figma REST token, then `GET /v1/files/:key` + `/v1/files/:key/nodes`.
2. Attach a desktop browser to this session (`browser.disconnected` goes away).

Option 1 is cheaper and gives node-level numbers. Until then this file stays
unread, and nothing from it is in the tree.

## 7. Rejected — the code

Six defects that each break something independently:

1. **`LiquidGlass.kt` blurs itself.** `graphicsLayer { renderEffect = blur }`
   blurs a window's **own** content. It can never frost what is behind. Real
   frost needs `FLAG_BLUR_BEHIND` + `blurBehindRadius` (API 31+). We already
   learned this the expensive way; the spec reintroduces it as the flagship
   composable.
2. **`AppIcon.kt` reverts the icon.** `RoundedCornerShape(size * 0.2237f)` is a
   circular-corner rounded rect. `AppIconView.kt` explicitly replaced that with
   Apple's own 40-segment path scaled from the 1024 pt grid, and
   `AppleuxSuperellipse` documents that a circular corner at the same apparent
   radius "reads as noticeably tighter". Adopting this file walks back a
   measured fix.
3. **`codegen.py` emits invalid colours.** For any token at alpha 1.0 it
   produces an 8-digit `Color(0xFFFFFFFF)`. That exceeds `Int`, so Kotlin
   resolves it to the `Color(Long)` overload (packed colour space), not
   `Color(Int)` (ARGB). Wrong colour, no compile error. Our own tokens use
   `0xB2FFFFFF.toInt()` — that `.toInt()` is the correct precedent.
   The 8-digit branch also returns before applying the `alpha` argument and
   reads the digits as `#RRGGBBAA`, while the 6-digit branch reads them as
   `#RRGGBB` + alpha. Two different contracts in one function.
4. **The pasted "generated" output is not what the script generates.**
   `gen_color` emits `liquidGlassFillLight` (no underscore → no case
   conversion); the pasted `Color.kt` shows `LiquidGlassFillLight`. The
   shown output could not have come from the shown script.
5. **`FrameModifier` cannot work.** It mixes `fillMaxWidth(0.3)` (fraction of
   parent) with `offset(x = (x * 100).dp)` — a percentage promoted to dp, of
   nothing. `x` and `w` are then double-counted. Every element is misplaced.
   `ScreenRenderer` additionally stacks siblings in a plain `Box` with no
   ordering, and `ElementRenderer` wires **no gestures at all** despite the
   spec's `gestures` arrays being one of its main selling points.
6. **`Android.bp` / `build.gradle.kts`.** `optimize: { enabled: false }` is
   not a valid `android_app` field. `androidx.compose.material3_material3` is
   asserted as a prebuilt that I have not verified exists in AOSP 16. And
   introducing Gradle contradicts the Gradle-free `kotlinc` build
   (`scripts/build-emulator-apks.sh`) that currently produces all five APKs.

Plus: the launcher manifest uses
`@android:style/Theme.DeviceDefault.NoActionBar`, discarding whatever
Launcher3 theming and configuration-change handling we inherit today.

**And the architecture underneath it is wrong for us.** The spec's headline
argument is that a runtime JSON renderer beats 400 generated Composables. But
this project's charter is *"every tap calls an AOSP API"*, and a JSON element
has no binding to one. `RenderKeypad` is a comment reading
`/* Full implementation in production */`. Rendered this way we get the exact
failure we already diagnosed — surfaces that look like something and do
nothing — except now the styling is also not shared, because there are no
shared components. Our design-system has 30 components and the problem is
already that they are under-used; a JSON renderer makes that strictly worse.

## 8. Rejected — the device tree

| incoming | reality |
|---|---|
| `TARGET_BOARD_PLATFORM := mt6789` | ~~Helio G99 is **mt6765**. mt6789 is Dimensity 1080.~~ **CORRECTED 2026-10-15: wrong.** Helio G99 IS MT6789 — the physical unit reports mt6789 on `ro.hardware` and `ro.board.platform`. The incoming value was right; the reviewer's part table was wrong. Correction C01 reverted with device evidence. |
| `TARGET_SCREEN_HEIGHT := 2408` | ~~Documented panel is **2400**.~~ **CORRECTED 2026-10-15: wrong.** `wm size` reports 1080x2408. Correction C02 reverted. |
| `BOARD_KERNEL_BASE := 0x40078000` | ~~the mt6789 value; not transferable~~ **re-examine**: it is the mt6789 value, which is now the RIGHT platform — but a kernel base still must come from the real rock boot.img header, not from the incoming sketch. Unverified either way. |
| `PRODUCT_OVERRIDE_PACKAGES += ...` | **not a real variable** — no definition anywhere in `build/make/` |
| `PRODUCT_ENFORCE_RRO_TARGETS += ...` | real but deprecated |
| `fastboot flash super` on POCO M5 | plausible, **untested** — no device attached |
| `fastboot -w` | destructive; on this AVD `-wipe-data` already destroys `userdata-qemu.img` unrecoverably |

Also absent: vendor blobs, kernel, and any `lunch` that has ever been run.
This is not a device tree, it is a sketch of one, for the wrong chipset.

## 9. Naming

The spec is written against its own project. Ours:

| incoming | ours |
|---|---|
| `ios27_rock-userdebug` | `appleux_rock-userdebug` |
| `IOS27Launcher` | `appleux-launcher` (`appleux/launcher`) |
| `IOS27SystemUI` | `appleux-systemui` (`appleux/systemui`) |
| `IOS27Settings` | `appleux-settings` (`appleux/settings`) |
| `com.ios27.launcher` | `com.appleux.launcher` |
| `com.ios27.theme.*` | `com.appleux.design.tokens.*` |
| `theme/Color.kt` `Type.kt` `Shape.kt` | `tokens/ColorTokens.kt` `TypographyTokens.kt` … |
| `/screenSize {"unit":"dp"}` | dp **and** px, see `device-support/rock/RockDisplayProfile.kt` |
| `/screenSize 411 × 916` | **411.43 × 914.29 dp**, derived at runtime |

## 10. Adopted

1. `design-reference/inbox/` — the verbatim spec plus the parser, so it stays
   editable and re-derivable.
2. `scripts/parse-ios27-spec.py` — 51 screen JSONs, 640 inventory rows,
   token + provenance files, and the backend rules checked against this tree.
3. `scripts/compare-glass-tokens.py` — the three-way token check. Run it
   before anyone proposes importing a token file again.
4. The **inventory as a checklist**, filtered: 630 ids minus 14 no-equivalent,
   2 blocked, and the Siri/Wallet/Home rows that name a product we will never
   ship. It is a usable map of "surfaces that exist" for the
   replace-from-source sequence. It is **not** a spec: no coordinate, radius or
   duration in it has been verified, and most have not been measured.

## 11. Open

- **Figma token or a desktop browser** — unblocks precise extraction from
  `WV8XFPk0oYZT1zMly5OoFu`. Nothing from that file is in the tree.
- The token file's colour/typography/radius halves remain uncompared.
- `device-support/README-needs.md` still governs what hardware facts are
  missing. The incoming device tree adds no facts.

## 12. Correction to an earlier assumption

While reviewing, I expected the "About → IMEI" mapping to be broken and checked
it. It is not — AOSP Settings holds `READ_PRIVILEGED_PHONE_STATE`. Recorded in
§5 because it is the clearest example we have of the replace-from-source
strategy being load-bearing rather than cosmetic.