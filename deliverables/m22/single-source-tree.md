# Single source tree — removing the duplicated AppleUX copies

Date: 2026-10-17. Supersedes the previous "two AppleUX trees" situation
described in `deliverables/m22/deployability-audit-2026-10-17.md`.

## What the bug was

There were two complete copies of the AppleUX sources:

| Location | design-system | launcher | settings | systemui | performance |
|---|---|---|---|---|---|
| `OsProject/appleux/` | 9217 lines, 50 components | 5445 | 1785 (39 rows) | 1359 | 1955 |
| `android-16/frameworks/appleux` + `android-16/packages/apps/AppleUX*` | 3967 lines, 23 components | 721 | 615 (11 rows) | **164** | 1005 |

They had diverged badly. The SystemUI copy in the AOSP tree was a 123-line
stub with six empty `onClick = { /* ... */ }` handlers, against a real
1050-line Control Center host in `appleux/`.

The damaging part was not that they differed — it was **which one each build
system consumed**:

- `scripts/build-emulator-apks.sh` read `appleux/` — the real sources.
- Soong read `android-16/…` — the stubs.

So all the system-core integration work was pointed at code that would have
shipped an empty Control Center and a third of the Settings catalog. It also
meant `appleux/settings` and `appleux/systemui` looked "deleted" from one
vantage point while still being the only real implementation.

Neither copy was recoverable with git: `appleux/` was a repo, the tree copies
were not, and they were absent from `.repo/project.list`.

## The fix

1. Snapshotted the five tree directories to
   `backups/stale-appleux-tree-2026-10-17/android16-appleux-copies.tar.gz`
   before deleting, so the deletion is reviewable.
2. Deleted them:
   `android-16/frameworks/appleux`,
   `android-16/packages/apps/AppleUX{,Launcher,Settings,SystemUI,Performance}`.
3. Replaced each with a **relative symlink** to the canonical source:

       frameworks/appleux            -> ../../appleux/design-system
       packages/apps/AppleUXLauncher -> ../../../appleux/launcher
       packages/apps/AppleUXSettings -> ../../../appleux/settings
       packages/apps/AppleUXSystemUI -> ../../../appleux/systemui
       packages/apps/AppleUXPerformance -> ../../../appleux/performance
       hardware/xiaomi-appleux/rock  -> ../../../device-support/rock

   `device-support/rock` is linked too because `appleux-design-system`
   depends on the module it defines, `appleux-device-support-rock`, and Soong
   can only resolve modules that live inside the tree.

4. Fixed the `Android.bp` files **in `appleux/`**, which is now the only place
   they exist. Each had never been validated against this tree:
   - referenced five `androidx.*` module names that do not exist in this
     manifest → replaced with the `androidx_runtime` aggregate
   - used `default_applicable_licenses: ["Android-src-setup"]`, which is not
     defined here → `Android-Apache-2.0`
   - `systemui` had `required: ["AndroidSystemHandler"]`, a module that exists
     nowhere in the tree, so it could never have resolved
   - `systemui` was **not** `privileged: true`, so STATUS_BAR and
     WRITE_SECURE_SETTINGS would have been denied at runtime regardless of the
     allowlist
   - module names were `AppleuxLauncher` / `AppleuxSettings` /
     `AppleuxSystemUI` while `appleux.mk` referenced `AppleUX*`. Normalised to
     the `AppleUX*` spelling. The APK **package** names are unrelated and stay
     `com.appleux.*`, because those are what the priv-app allowlist matches on.
   - added `product_specific: true` to all five

## Prevention

`scripts/check-single-source.sh` fails if anyone re-creates a physical copy of
an AppleUX module inside the AOSP tree, or breaks a symlink, or points the
product at a module that does not exist. Run it in CI or before any build:

    bash scripts/check-single-source.sh

Rules it enforces, so nobody has to rediscover these:

- **Never copy AppleUX sources into `android-16/`.** Link them.
- **`appleux-performance-lib` and `appleux-device-support-rock` are
  `android_library` modules.** They must never appear in `PRODUCT_PACKAGES` —
  there is no such APK to install. They reach the image through `static_libs`.
- **The allowlist grant and the manifest `<uses-permission>` must agree.** A
  grant for an undeclared permission is dead; a declared signature|privileged
  permission with no grant is denied at runtime.
- **`privileged: true` is mandatory** for any module holding a
  signature|privileged permission.

## Icon asset work (same session)

The iOS 27 HTML mockup at `/home/elfakiris/Videos/iosssssss/ios27` shipped its
28 Settings row icons as **64x64 JPEG crops lifted from an iPhone 11
screenshot**. `index.html` renders them at 30x30 CSS px and 50x50 for the hero,
so a 3x display needed 90-150 physical pixels from a 64px source — blurry at
every density above 1x, with JPEG ringing on small white-on-colour glyphs and
visibly desaturated tiles.

Replaced with 28 generated SVGs in `assets/settings-svg/`, built by
`scripts/gen-settings-svg.py` from the project's own vectors in
`appleux/design-system/src/main/res/drawable/`. `index.html:321` now resolves
icons from there instead of `assets/settings/*.jpg`.

The generator converts Android VectorDrawable to SVG, including nested
`<group>` transforms. Two ordering/format details that are easy to get wrong
and were wrong on the first attempt:

- Android group transforms compose as
  `translate * rotate * scale * translate(-pivot)` and points pass through
  scale first. An SVG transform list is a left-to-right matrix product, so the
  same matrix must be written **translate, rotate, scale**. The `sf_*` vectors
  depend on this — they carry `scaleY=-1` with `translateY=2048` to flip SF
  Symbols' Y-up geometry into Android's Y-down space.
- Android colours are `#AARRGGBB`, alpha **first**. `#FF000000` is opaque black.
  Converting by dropping the wrong end turns every white glyph black.

### Design-system glyph bugs this exposed

Rendering the generated set and looking at it (rather than trusting that valid
XML implies correct output) showed three vectors in the project's own design
system were simply wrong. Fixed at source, so both the mockup and the app
benefit:

| Vector | Was | Now |
|---|---|---|
| `il_ic_bluetooth` | a right-pointing triangle — read as a media play button | the real Bluetooth rune |
| `il_ic_controlcenter` | a 4x4 square with four dots — a grid icon | two toggle sliders, knobs on opposite sides |
| `il_ic_sos` | two concentric circles — read as a record button | the letters S-O-S, authored as stroke paths so they do not depend on an installed font |

Also fixed a real manifest typo: `android.permission.EXPEND_WINDOWS` in
`appleux/systemui/src/main/AndroidManifest.xml` was missing the `A`, so the app
declared a permission that does not exist and was silently missing
`EXPAND_WINDOWS`.

## Verification actually performed

- 28/28 SVGs generated, all pass `xmllint --noout`.
- All 28 label slugs produced by `slug()` in `index.html` resolve to a real
  file — checked by simulating the slugger in Python against the real
  `SETTINGS_ICONS` set.
- Rendered the set with ImageMagick and **looked at it**: confirmed the
  bluetooth / control-center / sos fixes visually.
- Drove the real page in headless Chrome via `openSet(); show('set')` and
  screenshotted at 828x1792 — Settings renders with the new icons, all rows
  correct. (Note `show('set')` alone is not enough; `show()` only toggles the
  `.on` class and the DOM is built by `openSet()`.)
- Side-by-side at 90px (3x the real 30px render size) to show the improvement.

Not verified: the Control Center glyphs in `assets/cc/glyphs/*.png` are still
low-resolution PNGs ranging from 32x32 to 104x104, and `assets/apps/*.jpg` are
still 96x96 JPEG third-party brand logos. Those were not touched yet. The app
logos are real brand marks and cannot simply be re-derived as vectors; the CC
glyphs are the same kind of fix as the Settings icons and should be done next.