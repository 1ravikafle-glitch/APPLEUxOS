# System-wide iPhone 11 notch + status-band geometry (2026-10-18, m28)

Goal: stop the POCO M5 reading as a waterdrop Android phone. The physical
cutout cannot be removed, so the only lever is what we draw over it, on every
screen, from SystemUI rather than from our own apps.

## What was changed — 3 files

### 1. `appleux/systemui/chrome/.../AppleuxNotchHost.kt` (NEW)

Hosts a `AppleuxDynamicIsland` inside the **status bar window**, which is above
every application window and above the keyguard. Before this, the mask was drawn
only by island instances the launcher and the shade own, so the waterdrop
reappeared the moment any other app was in front.

- Attached as the **last** child, so it draws above the status bar's own icons.
- Fed by a sticky `ACTION_BATTERY_CHANGED` receiver, so the charging dot
  reflects the device and the first callback arrives pre-populated (no window in
  which the notch shows a wrong state).
- Idempotent (`attach` twice on the same parent is a no-op) because
  `StatusBarWindowView` is re-created on display configuration changes.
- **No expansion, and no click target.** The expanded island is 150 dp tall and
  this window is 121 px, so an expansion here would be clipped mid-animation.
  Expansion stays with the launcher, which has a full-screen surface. A tap
  target that cannot show its result is worse than no tap target.

### 2. `frameworks/base/packages/SystemUI/src/.../StatusBarWindowView.java`

`onAttachedToWindow()` now calls `AppleuxNotchHost.attach(this)` in a
`try/catch (Throwable)`. This is the first moment there is a real parent with
measured bounds, and this view *is* the status bar window root
(`super_status_bar.xml`).

The `try/catch` is deliberate: this runs during SystemUI boot, and a cosmetic
mask must never be able to leave the device with no status bar, no keyguard and
no quick settings. The failure mode is the stock waterdrop.

### 3. `device/xiaomi/rock/rro_overlay/FrameworkOverlayRock/res/values/dimens.xml`

`status_bar_height_default` / `_portrait`: **73.0px → 44dp (121 px)**.

This is not cosmetic preference, it is a hard requirement of the notch:

| | value | consequence |
|---|---|---|
| waterdrop cutout | 100 × 76 px, centre x = 540 | must be covered |
| notch drawn | 0.502 × frame width × **29 dp (79.75 px)** | covers 76 px ✓ |
| old status band | 73 px | **notch taller than its window → rounded bottom corners clipped by the window bounds** |
| new status band | 121 px | 41 px of clearance below the notch |

The value is proportion-derived, not invented: iPhone 11's status bar is 44 pt on
an 896 pt frame = 4.91 %, and 4.91 % of our 875.64 dp frame is 43 dp. 44 dp also
reproduces iOS's gap relationship (14 pt of bar under a 30 pt notch).

### 4. `AppleuxDynamicIsland.kt` — the notch's own black area is now live

The brief was explicit: keep the iPhone 11 notch outline, and use that black
space as the Dynamic Island. One physical fact decides the whole design:

> **The waterdrop is a hole in the panel. The centre 100 px have no display
> pixels at all.**

That produced a real, previously invisible bug. `chargeDot` was added at
`Gravity.CENTER` with a 24 dp margin — i.e. right on the waterdrop — so on this
device it could never be seen, no matter what it was bound to. It is now inside
a shoulder row anchored to the notch's right end, in pixels that exist.

New API, additive and off by default:

```kotlin
fun bindNotchContent(text: String?, iconName: String? = null, tint: Int? = null)
```

- `null` text hides the row, so the launcher and shade keep drawing a plain
  notch with byte-for-byte unchanged behaviour.
- `refreshShoulder()` is the single owner of the show/hide decision, because the
  dot lives inside the shoulder and two call sites setting visibility
  independently would disagree.
- An expanded pill covers the same pixels, so the shoulder hides while expanded.
- Content must stay under ~6 characters: the shoulder is ~221 px, and it must
  never reach the hole.

Wired in `AppleuxNotchHost` to the one live source SystemUI can read without a
second subsystem: while charging, the notch shows a green battery glyph and the
percentage (`system_battery` from `AppleuxIconRegistry`, `ColorTokens.systemGreen`
— the existing token, not a second palette). It clears when the cable comes out.

Deliberately **not** wired: media and timers. They need a `MediaSessionManager`
subscription or a timer registry, and a live region that silently never updates
is worse than one that shows nothing.

## Verified

| Check | Result |
|---|---|
| `bash scripts/build-emulator-apks.sh` | **exit 0**, 4 APKs (`appleux-{design-system,performance,launcher,settings}.apk`) |
| `bash scripts/check-chrome-compiles.sh` (NEW) | **exit 0**, 81 Kotlin sources, 150 `Appleux*` classes, `AppleuxNotchHost.class` present |
| `javap` on the compiled output | `bindNotchContent(java.lang.String, java.lang.String, java.lang.Integer)` and `refreshShoulder()` present — the new API is real bytecode, not just source |
| `strings` check that the RRO value landed | `status_bar_height_portrait = 44dp` in the overlay source |

### New check script: `scripts/check-chrome-compiles.sh`

The chrome module is compiled into `com.android.systemui` by Soong, which needs a
warm tree (~48 min of analysis here), so until now there was no cheap way to know
whether a chrome edit even compiles. This script runs one `kotlinc` invocation
over design-system + rock + sounds + chrome, reusing the R classes
`build-emulator-apks.sh` already generated, and asserts per-directory source
counts rather than a guessed total.

`AppleuxPowerControl.kt` is **excluded, by name and with the reason printed on
every run**: it imports `android.os.IPowerManager` / `ServiceManager`, which only
exist under Soong's `platform_apis: true`. The script states this rather than
pretending full coverage.

## NOT verified — read before believing any of it works

1. **`m SystemUI` has still never been run.** The graph linkage of
   `AppleuxNotchHost` into `SystemUI-application` is by the same mechanism as
   `AppleuxShadeHost`, which was already linked — but linking is exactly the step
   that has never been executed. A `kotlinc` type-check is not a `static_libs`
   check.
2. **No device, no emulator boot.** The status bar window height is read from
   `SystemBarUtils.getStatusBarHeight` → `config_statusBarHeight`/`dimen
   status_bar_height`; the RRO override resolves at runtime. Whether 44 dp reads
   as premium or as a gap is an eyeball judgement nobody has made yet.
3. **`StatusBarWindowView.applyMargins()` overwrites every child's
   `topMargin`** from the waterfall inset on each insets pass. Waterfall is 0 on
   this device, so `topMargin = 0` is what survives — but this is a real coupling
   to AOSP behaviour that a future AOSP merge could change.
4. **Interaction between this notch and the launcher's own island is untested.**
   The launcher hides system bars and draws its own notch, so the two should
   never appear at once. That is reasoning, not observation.

## What this is NOT — the honest scope

The instruction was "complete all aspects and make it iOS 27-identical". Four
changes do not do that, and pretending otherwise would be the failure this
project's own charters warn about. Still outstanding, unchanged by m28:

- **Lock screen: absent.** All 12 real `lock-screen-*` surfaces have no
  implementation; `AppleuxLockScreen` is still never instantiated.
- **Settings: 21 rows still drop into Material Android Settings** (Bluetooth,
  Sound, Accessibility, Privacy, Notifications, Tether, Sync, Dreams, Voice
  input, App notifications, …). 5 owned sub-pages of 9 real iOS Settings
  surfaces.
- **Recents is stock QuickStep**; `AppleuxRecentsView` is dead.
- **Status bar glyphs are still Material** — see item 2 below.
- **Nothing here is visually confirmed.** No device, no successful emulator
  boot, and `m SystemUI` has never run.

## Next, in order of value

1. **`m SystemUI`** — until it runs, everything in m23 and m28 is static.
2. **iOS status-bar glyphs.** The clock is already iOS-styled
   (`TextAppearance.StatusBar.Default` = 15 sp / `sans-serif-medium`), but the
   signal, Wi-Fi and battery glyphs are still Material, so a launcher screen
   (which draws our `sf_wifi`/`sf_cellular`/`il_ic_battery` vectors) and an app
   screen disagree with each other. The override targets are the resources the
   `ModernStatusBarWifiView` / `ModernStatusBarMobileView` layouts inflate.
3. **Island expansion system-wide.** It needs the full-screen shade window, not
   the 121 px status bar window: a pill grown there and drawn *behind* the
   fixed notch would compose into a correct growing island, since the expanded
   pill (0.9 × frame) is wider than the notch (0.502 × frame) and its sides would
   show around it.
4. **Status-bar icon shapes are NOT to be changed from guesswork.** The three
   `*-live.png` iPhone captures in `design-reference/iphone11-ios27/` are AirPlay
   mirror bursts at 556 × 913 — not the device aspect ratio (2.164), so they are
   not valid for absolute measurement of insets or icon sizes. The status bar is
   measured from nothing until a true 828 × 1792 capture exists.
