# iOS 27 Fidelity Audit — Re-run (r2)

**Date:** 2026-10-17 ~11:15
**Supersedes:** `deliverables/m22/ios27-fidelity-audit-2026-10-17.md` (r1)
**Method:** read-only + one emulator-APK compile (`scripts/build-emulator-apks.sh`, no emulator run).
**Scope:** `appleux/`, `design-preview/`, `deliverables/`, RRO overlays (emulator + device), outer/inner git state.

---

## 0a. RE-CHECK — 12:13, same day

Re-ran the compile after the P0 fix landed and after the session's shell rewrites.

**P0 is fixed, and verified good.** `AppleuxControlCenter.kt` now imports both
`LayoutParams.MATCH_PARENT` and `LayoutParams.WRAP_CONTENT` (lines 8–9), and
**`design-system` compiles clean** — fresh `appleux-design-system.apk` at 12:12,
`appleux-performance.apk` at 12:13. §1 below is historical; that blocker is gone.

**But the build still fails — the blocker moved to `launcher`.** 40 errors, all in
the app shells the session was rewriting between 11:23 and 12:04:

| File | errors |
|---|---|
| `PhotosActivity.kt` | 21 |
| `CameraActivity.kt` | 16 |
| `CalendarActivity.kt` | 2 |
| `MessagesActivity.kt` | 1 |

Distinct causes:

| count | message | root cause |
|---|---|---|
| 22 | `argument type mismatch: actual type is 'LinearLayout'/'AppleuxText'/'FrameLayout'/'GradientDrawable', but 'Context' was expected` | `LayoutUtils.dpToPx(dp, this)` called **inside an `apply { }` block**, where `this` is the View, not the Activity. Signature is `dpToPx(dp: Float, context: Context)`. |
| 11 | `unresolved reference 'Log'` | `CameraActivity.kt` has no `import android.util.Log`. |
| 3 | `unresolved reference 'previewRequestBuilder'` | genuinely unwritten member. |
| 1 each | `'ImageReader'`, `'close'`, `'monthLabel'`, `'buildHiddenEmptyView'` | missing import (`android.media.ImageReader`) / unwritten members / undefined local. |

The 22 Context errors are one mechanical bug: `ContactsActivity` does it right with a
member helper — `private fun dp(v: Float) = LayoutUtils.dpToPx(v, this)` (here `this`
*is* the Activity). `PhotosActivity`/`CalendarActivity`/`MessagesActivity` call
`LayoutUtils.dpToPx(x, this)` directly inside `apply {}`. Fix = add the same `dp()`
helper per shell and replace the call, or use `this@PhotosActivity`.

**`settings` and `systemui` were never reached** — the build aborts at `launcher`, so
their APKs are still 09:25 / 09:26. **They remain unverified.** So does whether the
P0 fix is clean in every module.

**Other r2 findings unchanged at 12:13:** `dialog_corner_radius` drift 14dp/13dp still
present; `FrameworkOverlayRock/bools.xml` and `SystemUIOverlayRock/apple_hig.xml` still
present (still all-dead); `android-avd` (6.5 GB), `Launcher-IOS-27`, `.claude`, `.kilo`
still not git-ignored; 74 untracked root entries; outer HEAD still `438c2ae` (nothing
committed); emulator overlay still lacks the accent/radius/round-icon keys.

**Caveat:** the tree was being edited during this check (`PhotosActivity.kt` at 12:04,
`Browser/Messages/Files/Camera/Calendar/Contacts/Phone` at 11:23–12:04, `SpacingTokens`
at 11:38). These numbers are a snapshot of a moving target, not a settled state.

---

## 0. Headline

Two things changed the verdict in opposite directions:

1. **Six of the r1 defects are fixed** — including all six illegal font names, which was r1's *only certain compile break*.
2. **A new compile break was introduced** in `AppleuxControlCenter.kt` — **8 unresolved `WRAP_CONTENT`**. The tree **does not compile right now.**

r1 said "fonts are the only certain compile break." That is now false in both halves: the fonts are fixed, and something else blocks the build.

---

## 1. Build status — FAILS (historical, 11:15 — see §0a)

> **Superseded by §0a (12:13).** The `WRAP_CONTENT` break below is **fixed and
> design-system compiles clean**. The build now fails one module later, in
> `launcher`. Kept for the record.

`bash scripts/build-emulator-apks.sh` → fails at the first module:

```
==> design-system
    kt: design-system/.../components/AppleuxControlCenter.kt:154:61: error: unresolved reference 'WRAP_CONTENT'.
    ... 8 errors total
```

**Cause.** `WRAP_CONTENT` is `ViewGroup.LayoutParams.WRAP_CONTENT`. Kotlin resolves an *inherited Java static* only **qualified** (`LayoutParams.MATCH_PARENT` at the same sites works), never by bare name. The author mixed the two styles.

**Exact failing sites** (all in `appleux/design-system/src/main/java/com/appleux/design/components/AppleuxControlCenter.kt`):

| Line | Code |
|---|---|
| 154 | `sysRow.addView(addBtn, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))` |
| 155 | `sysRow.addView(powerBtn, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply {` |
| 170 | `row3.addView(connectivityCluster, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))` |
| 171 | `row3.addView(mediaCard, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))` |
| 180 | `row4.addView(focusPill, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))` |
| 189 | `        LinearLayout.LayoutParams.MATCH_PARENT, WRAP_CONTENT` |
| 201 | `addView(midWrap, LayoutParams(LayoutParams.MATCH_PARENT, WRAP_CONTENT))` |
| 204 | `    LayoutParams.MATCH_PARENT, WRAP_CONTENT))` |

**Minimal fix — one line, no other edits.** Add to the imports:

```kotlin
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
```

(Kotlin allows importing a Java static field by name; all 8 bare `WRAP_CONTENT` then resolve. Importing the *field* does not shadow the class name `LayoutParams` already in use.)

Alternative: qualify each site as `LayoutParams.WRAP_CONTENT`.

**Blast radius.** `design-system` is a dependency of launcher/settings/systemui, so the build stops there and **launcher, settings, systemui and performance were never reached.** Additional errors may be hiding behind this one. A green design-system is necessary but not sufficient — re-run the full script after the fix.

**Stale artifacts.** Every APK in `out/emulator/` is 09:21–09:36; `out/emulator/rro/com.appleux.overlay.frameworks.apk` is 09:36. Source edits ran until 11:10. **Nothing on disk reflects the current source**, and the current source does not compile. Do not quote the 09:xx APKs as evidence of the 11:xx tree.

---

## 2. Delta vs r1

| # | r1 finding | r2 status |
|---|---|---|
| 1 | 6 illegal font names block `design-system` | **FIXED** — all lowercase (`applesymbols.ttf`, `sfcamera.ttf`, `sfui.ttf`, `sfuiitalic.ttf`, `sfuisymbols_regular.otf`, `symbol.ttf`), staged in git. Compile now reaches kotlinc. |
| 2 | 5 empty `mipmap-*/ios-launcher/` dirs | **FIXED** — 0 remain. |
| 3 | `bools.xml` (5 inert keys) | **FIXED** — file deleted from `appleux/rro-frameworks/res/values/`. |
| 4 | `ColorTokens` label ramp too opaque | **FIXED exactly as specified** — `secondaryLabel #EBEBF599`/`#3C3C4399`, `tertiaryLabel #EBEBF54D`/`#3C3C434D`, `quaternaryLabel #EBEBF52E`/`#3C3C432E`. |
| 5 | Phantom `EXPAND_WINDOWS` in systemui manifest | **FIXED** — removed; replaced by real `STATUS_BAR`, `EXPAND_STATUS_BAR`, `WRITE_SECURE_SETTINGS`, `PACKAGE_USAGE_STATS`, `CHANGE_WIFI_STATE`, … |
| 6 | `ios27-integration-report.md` claims COMPLETE while void | **FIXED** — VOID header now at top of file. |
| 7 | "RRO accent regression" | **CORRECTED** — the **shipping device overlay is right**; the emulator overlay is the weak one. See §3. |
| — | (not in r1) | **NEW BLOCKER**: 8× `WRAP_CONTENT`. §1. |
| — | (not in r1) | **NEW**: whole overlays now measurable; 81 inert resource keys across three overlays. §3. |

---

## 3. Overlay audit — the deepest new finding

There are **three** RROs, not one. r1 only examined the emulator one. Validated every key against the target package's real resources.

### 3.1 Key counts

| Overlay | Manifest target | Files | Keys | **Inert** |
|---|---|---|---|---|
| `appleux/rro-frameworks` (emulator) | `android` (`com.appleux.overlay.frameworks`) | colors, dimens, integers | 49 | **39 (80 %)** |
| `device/xiaomi/rock/rro_overlay/FrameworkOverlayRock` | `android` (`com.android.overlay.rock`) | apple_hig, bools, config, dimens | 100 | **26** |
| `device/xiaomi/rock/rro_overlay/SystemUIOverlayRock` | `com.android.systemui` (`com.lineage.systemui.rock`) | apple_hig, config, dimens | 38 | **16** |
| | | **Total** | **187** | **81 dead** |

"Inert" = the name does not exist in the target package, so the override binds to nothing.

### 3.2 The shipping ROM is correct on accent — r1 was wrong about this

`FrameworkOverlayRock/res/values/apple_hig.xml` — **15/15 keys real** — carries the genuinely important overrides:

- `accent_device_default_light` → iOS blue, `accent_device_default_dark` → iOS dark blue
  (this is the pair every `DeviceDefault` theme reads for switch tracks, checkbox/radio marks, slider fills, cursor, focus underlines)
- `switch_thumb_normal_material_light/dark`, `config_useRoundIcon`
- `config_buttonCornerRadius`, `config_dialogCornerRadius`, `dialog_corner_radius`, `config_bottomDialogCornerRadius`, `chooser_corner_radius`
- the 5 motion durations

And it **ships**: listed in `android-16/device/xiaomi/rock/appleux.mk:11` and `device.mk:302`. All 15 keys verified present in `frameworks/base/core/res/res/values/`.

**So the r1 "accent regression" does not affect the ROM.** It is confined to the emulator overlay.

### 3.3 But the emulator overlay is now the weak one

`appleux/rro-frameworks` (targets `android`) — only **10 of 49 keys are real**:

- colors.xml — 18 keys, **17 inert**; the one real key is `system_notification_accent_color #FF007AFF` (a notification-accent colour, *not* the theme `colorAccent`).
- dimens.xml — 24 keys, **22 inert**; real: `dialog_corner_radius`, `config_icon_mask`.
- integers.xml — 7 keys, **0 inert**: `config_defaultRefreshRate`, `config_defaultPeakRefreshRate`, + the 5 motion durations.

Missing vs the device overlay: `accent_device_default_*`, `config_dialogCornerRadius`, `config_buttonCornerRadius`, `switch_thumb_*`, `config_useRoundIcon`, `config_bottomDialogCornerRadius`, `chooser_corner_radius`.

### 3.4 Feature drift between the two overlays that must agree

`FrameworkOverlayRock/apple_hig.xml` **contains its own requirement to stay in step** with the emulator overlay ("kept identical so the two cannot disagree"). That requirement is now **violated**:

| Key | Device | Emulator |
|---|---|---|
| `dialog_corner_radius` | **13dp** | **14dp** |

The device comment literally reads *"13dp is the value the emulator overlay already uses"* — no longer true. Same file also references *"the emulator overlay's `bools.xml`"*, which has been deleted. Two stale cross-references in a file whose whole point is synchronisation.

### 3.5 Files that are 100 % dead

| File | Keys | Reality |
|---|---|---|
| `SystemUIOverlayRock/.../apple_hig.xml` | 10 | **all 10 inert** — `apple_cc_tile`, `apple_glass_fill`, `apple_cc_radius`, … none exist in SystemUI. |
| `FrameworkOverlayRock/.../bools.xml` | 11 | **all 11 inert** — `config_wifi_*`, `config_bluetooth_*`, `config_dozeAfterScreenOff`. These live in WifiResources / Bluetooth / SettingsProvider, **not** framework-res, so a framework-res RRO can never bind them. |

**Correction to an interim finding:** a partial scan initially suggested the whole `SystemUIOverlayRock` was a no-op. It is not — its `dimens.xml` (11/11) and `config.xml` (11/17) carry real overrides (status-bar padding, blur radii, doze configs, recents). Only its `apple_hig.xml` (10) and 6 `config.xml` keys are dead. `config.xml` misses: `config_aodMaxRefreshRate`, `config_fpsInfoSysNode`, `config_keyguardQuickAffordanceRequiresLongPress`, `config_screenRecorderMaxFramerate`, `config_showRefreshRateQsTile`, `doze_proximity_sensor_supported` — none exist in SystemUI *or* framework-res.

### 3.6 Inert keys are not free

Each dead key is a maintainer trap: it compiles, ships, and silently does nothing — exactly the failure mode r1 flagged with `--auto-add-overlay`. 81 of them now.

---

## 4. New work since r1

| Area | State |
|---|---|
| `AppleuxControlCenter.kt` | restructured (IPSW-plist layout) — **but the file that broke the build** |
| New design-system components | `AppleuxPasscodeView` (134), `AppleuxStatusBar` (182), `AppleuxTodayView` (250), `AppleuxLockScreen`, `AppleuxPanelDismissHandler` (73) |
| App shells | `ContactsActivity` 282, `MessagesActivity` 344, `PhoneActivity` 425, `AppleuxCallScreenActivity` 341, `AppleuxAppChrome` 76 — all 3 activities declared in the launcher manifest |
| `deliverables/m23/ipsw-surface-comparison-2026-10-17.md` | new; 10 surfaces from IPSW plist data only |

**Fidelity gap in the shells:** `grep` for `il_ic_` across all `apps/*.kt` → **0 hits**. The 95 iOS `il_ic_*` vector drawables are **not used by any app shell**. `PhoneActivity` instead uses **4** stock `android.R.drawable.ic_menu_*` icons and `AppleuxCallScreenActivity` **1**. So the phone tab bar / call UI still renders Android's own icon set.

**m23 report:** well-disciplined — explicitly "no screenshot knowledge, no assumptions", plist/plist only — which is the opposite of r1's over-claiming problem. But by construction it cannot validate visual fidelity, so its "✅ NO GAP" rows mean "no gap in the plist numbers", not "looks like iOS". Treat as geometry evidence, not a fidelity verdict. It also reads slightly stale vs the 11:02–11:05 shell edits.

---

## 5. Repo hygiene — new untracked mass

| Path | Size | Git | Note |
|---|---|---|---|
| `android-avd/` | **6.5 GB** | not ignored | AVD home at project root |
| `Launcher-IOS-27/` | clone | not ignored | third-party repo, **duplicate** of `design-reference/ios27/ref-Launcher-IOS-27/` |
| `.claude/`, `.freebuff/`, `.kilo/`, `.vscode/` | small | not ignored | agent/IDE config |

- **74 untracked entries** at project root; **none** of the five new dirs is in `.gitignore`. A `git add -A` would try to stage 6.5 GB of emulator data.
- **`Launcher-IOS-27/`**: `origin https://github.com/kirikkirikon220-svg/Launcher-IOS-27`, one 5052-line `MainActivity.kt`, package `com.ios27.launcher`, **no LICENSE file**. Same remote already cloned at `design-reference/ios27/ref-Launcher-IOS-27/` (only `.git` metadata differs). Not referenced by any build; the sole mention in `appleux/` is a comment at `LauncherTokens.kt:66`. Two copies at project root = licence and confusion risk.
- Disk: 410 G used / 298 G free on the project drive. Fine, but the 6.5 GB is pure recoverable waste.

**Critical device-side work is untracked in the inner repo** (one `git checkout`/reset from loss):

- `device/xiaomi/rock/appleux.mk` (untracked)
- `rro_overlay/FrameworkOverlayRock/res/values/apple_hig.xml` (untracked) ← **carries the accent**
- `rro_overlay/SystemUIOverlayRock/res/values/apple_hig.xml` (untracked)
- modified: `device.mk`, `permissions/privapp-permissions-appleux.xml`

---

## 6. Prioritised fix list (r2)

### P0 — unblocks the build
1. ~~**`AppleuxControlCenter.kt`** — add `import android.view.ViewGroup.LayoutParams.WRAP_CONTENT`~~ — **DONE, verified 12:13.** design-system and performance compile clean.
1b. **`launcher` — 40 errors (§0a).** The mechanical 22 (Context-vs-View) are one fix per shell: add a member `private fun dp(v: Float) = LayoutUtils.dpToPx(v, this)` and call it inside `apply {}`, instead of `LayoutUtils.dpToPx(x, this)`. Then the missing imports: `android.util.Log` in `CameraActivity.kt` (11×) and `android.media.ImageReader` (1×). Then the genuinely unwritten members: `previewRequestBuilder` (3×), `close` (1×, Camera), `monthLabel` (1×, Calendar), `buildHiddenEmptyView` (1×, Photos).

### P1 — ship-blocking correctness
2. Reconcile `dialog_corner_radius` **13dp (device) vs 14dp (emulator)** — pick one; the device file claims they agree.
3. Delete or wire `SystemUIOverlayRock/.../apple_hig.xml` (10 dead keys) and `FrameworkOverlayRock/.../bools.xml` (11 dead keys). Dead keys are worse than absent keys.
4. Fix the 6 dead `SystemUIOverlayRock/config.xml` keys (move to the package that actually defines them, or drop).

### P2 — fidelity
5. Bring the emulator overlay up to the device overlay: `accent_device_default_*`, `config_dialogCornerRadius`, `config_buttonCornerRadius`, `switch_thumb_*`, `config_useRoundIcon`.
6. Strip the 39 inert `appleux_*` keys from `appleux/rro-frameworks` (harmless but 80 % noise).
7. Use the 95 `il_ic_*` icons in the app shells. Currently only 1 use total (`FilesActivity`). Stock `android.R.drawable` count now: `MessagesActivity` 5, `PhotosActivity` 6, `AppleuxCallScreenActivity` 1 — `PhoneActivity` was fixed (4 → 0), but the new shells re-introduced the pattern.
8. Correct the two stale comments in `FrameworkOverlayRock/apple_hig.xml` (bools.xml reference; the 13dp claim).

### P3 — hygiene
9. `.gitignore`: add `android-avd/`, `Launcher-IOS-27/`, `.claude/`, `.freebuff/`, `.kilo/`, `.vscode/`. 6.5 GB is at stake.
10. Remove the duplicate `Launcher-IOS-27/` at root (keep the `design-reference/` copy) or add the `LICENSE` and document the reference use.
11. Commit the untracked inner-repo files, `apple_hig.xml` first — it is the accent.

---

## 7. Verdict

- **Direction of travel: positive.** Every actionable r1 defect is fixed, and the fixes are correct — the label-ramp values match HIG exactly, and the font rename is right. The r2 P0 (`WRAP_CONTENT`) was fixed and **verified clean** at 12:13 (§0a).
- **Build: still red, one module later.** design-system ✅ and performance ✅ compile. `launcher` ❌ 40 errors on the half-rewritten app shells, and it aborts the build before `settings` and `systemui` — which have therefore **never been compiled against current source** and are unverified.
- **ROM fidelity: better than r1 implied.** The shipping `FrameworkOverlayRock` carries the real accent, switch, radius and motion overrides — r1's accent claim was based on the emulator overlay only and does not apply to the device build.
- **Real remaining drag:** 81 inert resource keys across three overlays (two files entirely dead), emulator/device value drift, iOS icons unused in the app shells, and 6.5 GB of untracked AVD data with no ignore rule.
- **Static only.** No emulator run. Nothing here says anything about the 4 GB POCO M5's performance.
