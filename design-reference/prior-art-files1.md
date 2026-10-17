# Prior art on this machine — `/home/elfakiris/Videos/files1`

Two independent assets the user pointed us at. **Both are the user's own work
/ prototypes, so there is no licensing question here** — unlike Apple's
binaries, which we do not touch.

## 1. `IOSLikeROM/` — a prior iOS-like Android ROM in Kotlin

```
IOSLikeROM/packages/apps/IOSLikeUI/src/org/ioslike/ui/
  launcher/      HomeScreen, AppLibraryScreen, LauncherActivity, AppRepo
  controlcenter/ ControlCenterScreen, ControlCenterController, MediaRepo, QuickShareBackend
  notifications/ NotificationCenterScreen, IOSNotificationListener
  lockscreen/    LockScreen
  settings/      SettingsActivity, SettingsKit, WifiPage, DisplayPage
  theme/         GlassTheme, LiquidGlass
  shell/         ShellService
IOSLikeROM/device/ioslike/   overlay, default-permissions, privapp-permissions
```

### CRITICAL: it is NOT build-compatible with us

That tree is **Soong-integrated** — `Android.bp`, `device/ioslike/*.mk`,
privapp-permissions XML. Our build is deliberately Gradle-free
(`aapt2` + `kotlinc` + `d8` + `apksigner`) and has no Soong path. Dropping
`IOSLikeROM` in wholesale will NOT build.

**So: mine it, do not vendor it.** Two parts are safely portable:

| Asset | Action taken |
|---|---|
| `res/drawable/ic_*.xml` — 36 valid 24dp VectorDrawables | **Imported** into `appleux/design-system/src/main/res/drawable/` prefixed `il_ic_*`. Zero-risk, direct value: CC needs brightness/flashlight/airplane/battery/rotation/calculator/cellular which our SF set lacked |
| Kotlin sources | **Mine for patterns only.** Plain Kotlin + Android APIs, but the structure (ShellService, Screen-per-surface, different package root) differs from ours. Port selectively, never paste wholesale |

Naming: `il_ic_*` = iOSLikeROM. `sf_*` = Apple's SF Symbols geometry, which
remains the more authoritative source where both exist (we skipped `ic_wifi`
and `ic_bluetooth` because we already have the real SF glyphs).

## 2. `iOS 27-style Android OS UI.html`

A single-file HTML/CSS mockup of the OS. Useful as a **layout and hierarchy
reference** (what goes where on each screen). It is *not* a token source — it
uses few CSS custom properties and almost no colour literals, so there is
little to mine mechanically. Treat it as a layout storyboard, not a spec.

## Rule for agents

Mine both for patterns and assets. Never paste a module wholesale, never
assume anything from this tree compiles in our build, and never treat its
values as Apple-sourced — when in doubt, `LiquidGlassTokens.kt` (which carries
explicit Apple provenance) wins.