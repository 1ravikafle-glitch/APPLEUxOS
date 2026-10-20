# m21 — Design-system consolidation and RRO audit, 2026-10-17

Everything below is verified by a build or a grep, not asserted. Where a claim
could not be verified it says so.

---

## 1. The build is green again

```
==> design-system
==> performance
==> launcher
==> settings
==> systemui
BUILD: clean
```

All five APKs regenerated in the same run (16:08–16:11). Before this the
launcher module had 88 kotlinc errors.

### What the errors actually were

They were not all the "concurrent-edit fallout" they looked like. Four were
real defects that would have shipped broken:

| File | Error | Cause | Fix |
|---|---|---|---|
| `PhoneActivity.kt:149`, `MessagesActivity.kt:136` | `unresolved reference 'dbm'` | `SignalStrength.getDbm()` is a **hidden platform API** — verified absent from `android.jar` with `javap`. It can never resolve in any normal build. | Rewritten to public `getLevel()` (0–4). The `SimInfo` field became `signalBars` and both label mappings became bars, which is also what iOS shows. |
| `CameraActivity.kt:733` | `COLOR_CORRECTION_GAINS` type mismatch | The code passed a `ColorSpace` to a key that wants gains. On API 33+ the key is `Key<RggbChannelVector>`, not `Rational[]`. Verified with `javap` on android-36. | `RggbChannelVector(red, green, blue, evenGreen)` |
| `CameraActivity.kt:710,878` | `unresolved reference 'previewRequestBuilder'` | The whole Pro-mode path read and wrote a field that was **never declared**. | Declared `private var previewRequestBuilder: CaptureRequest.Builder?` |
| `PhotosActivity.kt:2742` | `accidental override isSelected()/setSelected()` | A `var isSelected: Boolean` on a `FrameLayout` collides with `android.view.View`'s identical JVM signatures. | Renamed `photoSelected`. Deliberately **not** delegated to the platform flag: that flag tracks focus traversal, so letting the checkmark depend on focus order would be a bug. |
| `AppleuxSystemUIService.kt:401` | `Settings.Global.LOW_POWER_MODE` | That key does not exist (verified with `javap`). | `PowerManager.isPowerSaveMode()` |
| `AppleuxSystemUIService.kt:291` | `AppleuxTheme.applySystemBars(this)` | Takes an `Activity`; this is a `Service`. | `toggleDarkMode()` now genuinely rebuilds and reattaches both overlay windows. Previously it would not have compiled, and the "obvious" one-line fix — flipping the flag and doing nothing — would have left the Control Center in the old palette until it was torn down. |

Also fixed: missing `import android.util.Log` / `android.media.ImageReader` /
`android.hardware.camera2.CameraCharacteristics` / `android.view.Gravity` /
`android.util.TypedValue`; a 3-argument `coerceIn` (Kotlin has no such
overload); `Paint.strokeWidth = dpToPx(...)` Int→Float; `applyTypography()`
called on a bare `EditText` (it is an `AppleuxText` method); and the Calendar's
two segment labels, which each captured the other from a click listener — as
locals that is a forward reference, so they became fields.

### `scripts/fix-context-receivers.py`

49 `this` receivers resolved to a `View` instead of the Activity, because the
shells are programmatic views shaped as `SomeView(this).apply { … }`. The tool
drives off kotlinc's own `file:line:col` rather than a regex over the file, so
it can only rewrite the exact token the compiler named, and it only touches
**bare** `this` (not `this.foo`, `this@Foo`, `this$foo`). It rebuilds between
passes, so it converges. Kept in `scripts/` because the same shape will recur.

### `kotlinc` heap

The build was reporting only `Backend Internal error: Exception during IR
lowering`. The real cause was an `OutOfMemoryError` **while rendering the
diagnostic** — `RenderIrElementKt.renderTypeInner` recursing through a
self-referential generic — so the heap ran out building the error message and
the error itself was never printed. Fixed with `-J-Xmx6g -J-XX:MaxMetaspaceSize=1g`
in `scripts/build-emulator-apks.sh`. Anyone who reads that line as a code bug is
reading a compiler symptom.

---

## 2. P1 — themes are now one shared hierarchy

### Before

Three apps, three stock parents, no shared layer:

| App | Parent | Extra |
|---|---|---|
| launcher | `Theme.AppCompat.Light.NoActionBar` | 1 item |
| settings | `Theme.AppCompat.Light.NoActionBar` | 1 item; **night theme was a byte-for-byte copy of the light one** |
| systemui | `android:Theme.Material` | 5 items + its own colour file |

Changing the accent meant editing three files. Changing corner radius meant
editing none, because nothing read it. And every framework view that consults
the theme still received Material defaults.

### After

`design-system/src/main/res/values/themes.xml`:

```
Theme.Appleux.Base          AppCompat.Light.NoActionBar  + every override
  Theme.Appleux                                            (bare, night overrides this)
    Theme.Appleux.Launcher      windowBackground = system_background, showWallpaper
    Theme.Appleux.Settings      windowBackground = system_grouped_background
Theme.Appleux.Dialog.Base -> Theme.Appleux.Dialog
Theme.Appleux.SystemUI    android:Theme.Material.NoActionBar, FORCED DARK
```

Each app's own `themes.xml` is now three lines. The `.Base` split exists so
`values-night/themes.xml` can override a bare subclass instead of having to
redeclare an entire style.

AppCompat stays the ancestor deliberately: all 16 activities extend
`AppCompatActivity`, which throws if its theme is not AppCompat-derived.
Dropping it is a component migration, not a theme edit. What matters is that
everything the theme supplies is overridden, so the Material defaults never
reach the screen.

### What the base theme now overrides

- **Tint** — `colorAccent` was Material blue 500 (`#2196F3`) on AppCompat and
  teal (`#009688`) on `Theme.Material`. Both were visible. Now `system_blue`.
- **Press feedback** — `selectableItemBackground`, `listSelector` and
  `selectableItemBackgroundBorderless` all pointed at a **ripple**. Now
  `@drawable/appleux_pressed`, an iOS highlight fill.
  *Also cheaper:* a `StateListDrawable` over two `ColorDrawable`s is two
  `fillRect`s on the GPU path; a `RippleDrawable` allocates a mask and runs a
  shader per frame. On a Helio G99 at 90 Hz that is a real saving.
- **Text field** — `editTextStyle` was `Widget.AppCompat.EditText` with the 2dp
  Material underline and the floating indigo hint. Now no underline, no
  floating label, tint on the selection handles.
- **Dialogs** — `alertDialogTheme`, `dialogTheme`, `datePickerDialogTheme` and
  `timePickerDialogTheme` all routed to the iOS card. That covers the 26
  `AlertDialog.Builder` call sites **without touching code**.
- **Type** — `android:textViewStyle` and `android:buttonStyle` now resolve to
  `AppleuxTextView` / `AppleuxButton`, so a bare `<TextView>` or `<Button>` in
  any XML layout gets HIG Body 17 with the right colour, and a bare `<Button>`
  is not all-caps, not Material-indigo, not elevated, and has a 44dp
  tappable-area floor.
- **System bars** — transparent with `windowDrawsSystemBarBackgrounds`, so
  every surface is edge-to-edge and owns its own inset.
- **Motion** — `windowAnimationStyle=@null` and activity transitions off.
  AppleUX draws its own sheet/spring motion in Kotlin; a platform window
  animation on top double-fades.

`Theme.Appleux.SystemUI` is forced dark, and that is not a shortcut: an iOS 27
Control Center reads as dark glass over a **light** wallpaper; it does not
invert with the system theme. It pins the dark tokens by hand rather than
trusting `values-night`.

### The five bugs this surfaced

1. `--` inside XML comments is illegal. Every `---` section divider in the
   headers I wrote broke `aapt2 compile`. They are `===` now.
2. `android:fontFamilySerif` and `android:fontFamilyMonospace` **do not exist**.
3. `android:shadowRadius`/`Dx`/`Dy` are **floats** — `0dp` is rejected.
4. `<scale>` uses `fromXScale`/`toXScale`, `<alpha>` uses `fromAlpha`/`toAlpha`.
   `startScale`/`endAlpha` are not attributes that exist.
5. `android:lineSpacingMultiplier` scales the font's **natural leading**, not
   the point size, so the HIG ratio (22/17) is not what it looks like. Only
   code can set an exact `lineHeight`, which is why `AppleuxText` does.

---

## 3. P1 — one palette, not four

### Before

The semantic colour layer lived in **four files under four naming schemes**, and
they disagreed:

- `launcher/values/colors.xml` → `appleux_surface`, `appleux_label`,
  `appleux_system_blue`
- `settings/values/colors.xml` → `appleux_settings_bg`, `_surface`, `_label`,
  `_summary`, `_accent`
- `systemui/values/colors.xml` → `system_background`, `label`, `secondary_label`,
  `separator`, `tertiary_label` — with `label` **#FFFFFF** and
  `system_background` **#000000** in the *default (light)* qualifier, i.e. a
  white-on-white status bar in light mode
- `design-system/values-night/colors.xml` → the only semantic set, and it only
  covered dark

`secondaryLabel` was an opaque `#A8A8A8` in XML against HIG's
`rgba(60,60,67,0.60)` in Kotlin — the same text, two colours, depending on
whether it was drawn in code or inflated.

### After

`design-system/values/colors.xml` + `values-night/colors.xml` are the single
source for the whole XML layer, mirroring `ColorTokens.kt` exactly. All five
per-app colour files are **deleted**, and the 14 `@color/appleux_settings_*` /
`@color/appleux_surface` references were rewritten to shared names.

Verified in the built APK:

```
resource 0x7f060054 color/secondary_label
  () #3c3c4399
  (night) #ebebf599
resource 0x7f0f000f style/AppleuxLauncherTheme
  colorAccent        = @color/system_blue
  colorControlHighlight = @color/fill_tertiary
```

### `tools/check-token-drift.py`

A colour changed in one representation and forgotten in the other previously
shipped silently. This compares them entry by entry across both appearances and
exits non-zero on drift.

It immediately caught two mistakes of mine, which is the point of shipping it:

- `systemBackgroundTertiary` was mapped onto the wrong XML name — the checker
  was comparing *different* colours and calling it agreement.
- `ColorTokens` has **five** background levels, not three. All five are now
  declared in both appearances.

It also caught a bug in itself: the Kotlin regex captures bare hex and the XML
regex captures with `#`, so every row compared unequal and all 28 read as
mismatches. That is why the first two runs were noise; the third is real.

```
compared 28 tokens across light and dark
OK: XML palette matches ColorTokens.kt in both appearances.
```

### One doc bug fixed in passing

`TypographyTokens`' header claimed the tracking table was "already divided by
1000 so they can be handed straight to `letterSpacing`" and then that consumers
"divide by 1000 before applying it". Both cannot be true. Body is `0.40`, not
`0.0004`, so the table holds **raw HIG points** and consumers divide
(`AppleuxText:86`, `AppleuxButton:141`). Following the header would have
produced 1000×-too-tight tracking in every new call site.

---

## 4. P3 — RRO audit, and two measured-device corrections

### Dead keys

Every key in both surviving overlays is now checked against the target
package's own `res/values`, with the regex corrected to allow
`translatable="false"` between the tag and `name` (the first audit run
reported 7 false positives for exactly that reason).

| Overlay | Live keys | Dead keys |
|---|---|---|
| `FrameworkOverlayRock` | 55 | **0** (was 25) |
| `SystemUIOverlayRock` | 22 | **0** (was 6) |

Removed: the wifi/Lineage bools (`config_wifi_*`, `config_bluetooth_*`),
`config_dozeAfterScreenOff` (a **typo** — the real key is
`config_dozeAfterScreenOffByDefault`, so Lineage's doze intent never took
effect), `config_powerButtonFingerprint`, the four `config_smartCharging_*`,
`config_lineageHealthSupported`, `config_proximityCheck*`,
`config_screenRecorderMaxFramerate`, `config_fpsInfoSysNode`,
`config_showRefreshRateQsTile`, `config_keyguardQuickAffordanceRequiresLongPress`,
`config_aodMaxRefreshRate`, and both `apple_hig.xml` files (10 + 10 invented
names, zero effect).

`FrameworkOverlayRock/bools.xml` is now empty and was deleted.

### Measured-device corrections

These were live keys whose **values** were wrong, so the audit could not catch
them — they only show up against the physical panel.

**`config_mainBuiltInDisplayCutout`** was 168 px wide (`x = -84..84`). The
measured waterdrop is **100 × 76 px, centre x = 540 = 1080/2 exactly**
(`deliverables/m20/rock-display-measurement.md`). AOSP cutout paths are in
device-local coordinates centred on x=0 for a top-centre cutout and are
**not** auto-mirrored. The path was 68 px wider than the hardware, which
inflated the top safe-area inset and the waterfall corner radius.

```
before: M84,76 C30,76 54,20 0,20 C-54,20 -30,76 -84,76 L-84,0 L84,0 Z
after:  M50,76 C17.9,76 32.1,20 0,20 C-32.1,20 -17.9,76 -50,76 L-50,0 L50,0 Z
```

**`config_frontBuiltInDisplayCutoutProtection`** used r=36 → 72 px wide, i.e.
*narrower than the cutout it is supposed to protect*, so it was clipped by the
cutout and never drew around the waterdrop. Now r=60 → 120 px, 10 px of margin
each side.

### Vendor cruft removed

`CarrierConfigOverlayRock`, `SettingsOverlayRock` (ArrowOS copyright),
`SettingsProviderResOverlayRock` and `WifiResOverlayRock` are deleted, with
`PRODUCT_PACKAGES` in `device/xiaomi/rock/device.mk` and the duplicate entry in
`appleux.mk` updated. Surviving: `FrameworkOverlayRock`, `SystemUIOverlayRock`,
`SettingsOverlayStone`, `SettingsProviderResOverlayStone`.

All 11 remaining XML files parse.

---

## 5. Not done, in priority order

| # | Gap | Why it is not done |
|---|---|---|
| P0 | System chrome still stock | `AppleuxSystemUIService` draws `TYPE_APPLICATION_OVERLAY` windows; stock SystemUI still owns the status bar, QS, nav bar, keyguard and HUD. Making AppleUX *be* SystemUI is a Soong/`Android.bp` module in `frameworks/base/packages/SystemUI`, not an app change. Nothing below works until that decision is made. |
| P2 | 43 `android.R.drawable` refs in the shells | Needs an icon-by-icon mapping against the 92 vendored `sf_*`/`il_ic_*` drawables. |
| P2 | Sounds | 366 extracted sounds exist; no call site plays one. |
| P1 | 36 `Toast`, 5 `SeekBar`, 26 `AlertDialog.Builder` call sites | Dialogs are now **themed** via the base theme, so those 26 look iOS without a code change, but they are still `AlertDialog`. `Toast` is not an iOS surface at all. |

The SystemUI decision is the one that blocks the rest and it is a judgement
call, not a mechanical change, so it is left explicit rather than guessed.