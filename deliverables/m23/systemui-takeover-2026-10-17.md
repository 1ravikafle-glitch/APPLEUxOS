# m23 — SystemUI takeover, first surface: navigation bar, 2026-10-17

Priority order applied throughout, per the charter restated today:
**performance > stability > iOS 27 design.**

---

## 1. The structural change: AppleUX code now compiles INTO `com.android.systemui`

This is the P0 work. Previously the iOS chrome shipped as a **separate app**
(`com.appleux.systemui`, `appleux/systemui/Android.bp`) whose foreground
service added two `TYPE_APPLICATION_OVERLAY` windows *on top of* SystemUI.

That arrangement, per the priority order:

| Cost | Detail |
|---|---|
| **performance** | one extra resident process on a 4 GB device |
| | a foreground service plus an ongoing notification |
| | **two `MATCH_PARENT` `PixelFormat.TRANSLUCENT` windows** — the compositor allocates and blends two extra full-screen layers every frame at 1080×2408 |
| | `attachOverlays()` is called unconditionally from `onStartCommand` (`AppleuxSystemUIService.kt:515`), so every tile press and every restart intent re-runs `wm.addView` on views that already have a parent. It fails silently inside `runCatching`. |
| | the foreground notification's small icon is `android.R.drawable.presence_online` — a stock Android drawable, in the app whose job is to remove stock |
| **stability** | it *floats above* SystemUI, so the stock status bar, quick settings, keyguard and volume HUD were still underneath, fighting it |
| **design** | the iOS chrome was never the real system chrome at all |

### What was done

New Soong module `appleux-systemui-chrome`:

```
appleux/systemui/chrome/Android.bp          appleux-systemui-chrome (android_library)
appleux/systemui/chrome/src/main/AndroidManifest.xml
appleux/systemui/chrome/src/main/java/com/appleux/systemui/chrome/AppleuxChromeTokens.kt
appleux/systemui/chrome/src/main/java/com/appleux/systemui/chrome/AppleuxHomeIndicatorView.kt
```

Linked into `frameworks/base/packages/SystemUI/Android.bp:638`, inside
`SystemUI-application`'s `static_libs`. The tree reaches the sources through a
symlink, matching the existing pattern for the design system:

```
android-16/frameworks/appleux               -> ../../appleux/design-system
android-16/frameworks/appleux-systemui-chrome -> ../../appleux/systemui/chrome
```

Two details in that module that are not incidental:

- **`platform_apis: true`, not `sdk_version`.** SystemUI is built with
  `platform_apis: true`. An SDK-versioned library linked into it is denied every
  `@hide` API AOSP's own controllers legitimately use.
- **`kotlincflags: ["-Xjvm-default=all"]`.** `SystemUI-srcs-defaults` sets it
  for the same reason. Without it, Kotlin interfaces with default methods
  compile differently and silently break against AOSP types.
- It is listed **after** `SystemUI-core`, so if a class ever collides the
  system one wins. A duplicate-class build error beats a silently shadowed
  AOSP controller.

The manifest is intentionally empty of components and permissions. Permission
declarations would be duplicates of SystemUI's own and the merger would reject
them.

---

## 2. Stock navigation-bar UI: DELETED

`frameworks/base/packages/SystemUI/res/layout/navigation_bar.xml` — replaced.

**Before**

```xml
<NavigationBarView android:background="@drawable/system_bar_background">  <!-- #ff000000 -->
    <NavigationBarInflaterView android:id="@+id/navigation_inflater"/>
</NavigationBarView>
```

**After**

```xml
<NavigationBarView android:background="@android:color/transparent">
    <NavigationBarInflaterView android:id="@+id/navigation_inflater"/>
    <AppleuxHomeIndicatorView
        android:layout_gravity="bottom|center_horizontal"
        android:clickable="false" android:focusable="false"/>
</NavigationBarView>
```

### Device config (`FrameworkOverlayRock/res/values/config.xml`)

```xml
<integer name="config_navBarInteractionMode">2</integer>  <!-- was 0: 3-button -->
<integer name="config_navBarOpacityMode">2</integer>      <!-- was 0: forced opaque -->
```

Both are **live** framework-res keys (verified:
`frameworks/base/core/res/res/values/config.xml:4570` and `:4580`), so unlike
the 31 dead keys removed in m21 these actually take effect.

**performance** — mode 0 inflated three button views, three key-icon
drawables and three button dispatchers, and put three extra touch targets in
front of the gesture recogniser on every frame. Mode 2 removes all of it.
Opacity mode 0 drew an opaque `#ff000000` strip across the bottom of the screen
every frame; mode 2 ("nav bar is never forced opaque") plus the transparent
background removes that full-width opaque draw entirely and lets the wallpaper
through, which is also the iOS behaviour.

**stability** — gestural navigation is a first-class AOSP path, not new
behaviour: `NavigationBarView` already imports `NAV_BAR_MODE_GESTURAL` and
branches on `isGesturalMode()`.

`NavigationBarInflaterView` is **kept**, and deliberately: `onFinishInflate()`
(`NavigationBarView.java:855`) does
`findViewById(R.id.navigation_inflater).setButtonDispatchers(...)` and would NPE
without it. It inflates nothing in gesture mode.

### `AppleuxHomeIndicatorView`

iOS has no navigation buttons — only a capsule at the gesture origin.

Geometry is in `AppleuxChromeTokens`: 139 × 5 dp (HIG lists the indicator at
~140 × 5 pt; the width is measured off the operator's iPhone 11 iOS 27
screenshots in `design-reference/iphone11-ios27/`), full corner radius so it is
a true capsule, 8 dp bottom margin so it clears the gesture deadzone, ~61%
white.

Chosen for cost, because performance is the priority:

- one reused `Paint`; no shader, no layer, no `setLayerType`
- **no blur.** The iOS indicator is a flat capsule — there is nothing to blur. A
  blurred nav bar would cost a full-width offscreen pass per frame.
- no ripple, no `StateListAnimator` — it is not interactive, so there is no
  pressed state to render and no animator to tick.
- no background drawable: `onDraw` paints the capsule directly.
- `onMeasure` overridden to the exact drawn size, so the parent does not run a
  second measure pass to discover it.
- the `RectF` is computed in `onSizeChanged`, not per frame.
- not clickable, not focusable, `IMPORTANT_FOR_ACCESSIBILITY_NO`: a clickable
  view here would sit between the user and the home swipe.

---

## 3. Stock status-bar type: DELETED

`TextAppearance.StatusBar.Default` resolved the family to
`config_headlineFontFamilyMedium`, or `variable-label-large-emphasized` under
the feature flag. Both are **Android system font names** — that is Roboto, and
it is the visible stock signal on this surface. Now `sans-serif-medium` with
HIG Caption1 tracking (0.15).

SF Pro is Apple-platform-licensed and cannot ship in an APK, so the platform
medium face stands in — the same substitution documented in
`TypographyTokens`.

**Size deliberately unchanged.** `@dimen/status_bar_clock_size` is still 14sp.
That is AOSP's value and it is *not* iOS-measured: the operator's screenshots do
not state a point size for the status bar clock, and the HIG Dynamic Type table
has no style for it (iOS's status bar time is not one of the 17 named styles).
Inventing a number here and calling it a match is exactly what this project has
been correcting for. It must be measured off
`design-reference/iphone11-ios27/` before it can be called iOS.

---

## 4. What was verified, and what was not

**Verified by running it:**

```
kotlinc -classpath android.jar ... AppleuxChromeTokens.kt AppleuxHomeIndicatorView.kt
  exit=0   -> AppleuxChromeTokens.class, AppleuxHomeIndicatorView.class

aapt2 compile res/layout/navigation_bar.xml   -> OK
XML well-formed: navigation_bar.xml, styles.xml, FrameworkOverlayRock config.xml,
              appleux-systemui-chrome AndroidManifest.xml
no stray '--' inside any XML comment
config_navBarInteractionMode / config_navBarOpacityMode exist in framework-res
```

**Not verified — stated plainly:**

`m SystemUI` was **not** run. There is no warm Soong state in `out/`, Soong
analysis alone took 48:21 on a clear machine, and SystemUI is 1,151 Java + 3,783
Kotlin files with Dagger, Compose and R8. That does not fit in one session
alongside making the changes. So the Soong graph edit, the module linkage and
the runtime result are **unverified**.

`aapt2 compile res/values/styles.xml` fails on lines 207+ with
`Resource flag value undefined: com.android.systemui.shade_header_font_update`.
That is **pre-existing and not from this change** — those are aconfig feature
flags that only exist inside Soong's pipeline
(`aconfig/systemui.aconfig`). My edit is at line 46, outside every reported
line. The file is well-formed (`ElementTree` parses it).

---

## 5. Why the rest of SystemUI was NOT deleted

The user asked for stock UI to go now. Deleting stock **controller classes**
blind was not done, for a reason that is about stability, not caution for its
own sake:

`SystemUIService` starts the `com.android.systemui` process and binds to
`SystemUIApplication`, `Dependency`, and a Dagger graph spanning ~4,900 source
files. Remove the controllers that draw and mediate it and the device boots
with **no status bar, no keyguard and no quick settings** — the OS is
unusable, and there is no build in this session that would tell me which files
were load-bearing.

The deletable-now boundary is therefore: **delete the chrome, keep the
behaviour.** Every replacement so far follows it — the root class is still
AOSP's `NavigationBarView`, so `EdgeBackGestureHandler`, `Recents`, `Pip`, the
IME switcher, rotation and screen pinning all still work; only the window's
contents are ours.

Rewriting that machinery would be the single largest stability risk available
and would buy nothing visually. That is the charter's own priority order
applied literally.

### `com.appleux.systemui` is also NOT deleted yet — concretely

Its QS tiles and `AppleuxBroadcastReceiver` launch
`AppleuxSystemUIService` by name to toggle the panels
(`AppleuxControlTileService.kt:38`, `AppleuxShadeTileService.kt:34`,
`AppleuxBroadcastReceiver.kt:14`). Delete the service now and the Control Center
and Notification Center toggles have **nothing to talk to** — a functional
regression, i.e. a stability loss, to buy one process of memory back.

Correct order: repoint the tiles at SystemUI's own `CommandQueue`
(`CentralSurfaces` / `handleStatusBarAction`) → then delete the overlay app.
That is the next concrete step and it is mechanical, not exploratory.

---

## 6. Remaining stock UI, in the order it should go

| # | Surface | Why it is next |
|---|---|---|
| 1 | `com.appleux.systemui` overlay app | One less process, two less composited layers. Blocked only on repointing 3 call sites at `CommandQueue`. |
| 2 | Quick settings / shade (`res/layout/status_bar_expanded.xml`, `super_status_bar.xml`, `ShadeViewController`) | Largest remaining stock surface. Hundreds of controller-required view ids, so the layout cannot be swapped wholesale — the Material look has to be removed style-by-style. |
| 3 | Keyguard / lock screen | `AppleuxLockScreen` and `AppleuxPasscodeView` already exist and have no consumer. |
| 4 | Volume / brightness HUD | `AppleuxSystemHUD` exists, no consumer. |
| 5 | Status bar arrangement (time centred, iOS left/right clusters) | See below. |
| 6 | Recents | QuickStep stays until an AppleUX task switcher exists, per the charter. |

### Status bar layout — deliberately not restructured

iOS 27 centres the clock. AOSP's `status_bar.xml` puts `Clock` inside
`status_bar_start_side_except_heads_up`. Moving it is blocked by three real
constraints found in the source, not by caution:

- `StatusBarRoot.kt:504-508` re-parents and re-orders `notification_icon_area`
  **within that exact parent**, with a comment stating it is required "to
  maintain the same ordering for ongoing activity".
- `PhoneStatusBarTransitions.java:47` fades that same view as the shade expands.
- `StatusBarRoot.kt:683` uses `R.id.clock` as the `startSideIconView` anchor for
  the new status bar pipeline.

Changing the tree without a build to check those three is how the status bar
breaks. It is worth doing and it needs a build — see §4.