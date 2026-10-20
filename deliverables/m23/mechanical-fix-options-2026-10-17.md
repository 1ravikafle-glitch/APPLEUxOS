# Mechanical iOS-fix Options — Toast / Slider / Photos Icons

**Date:** 2026-10-17, 15:20
**Status:** PROPOSAL ONLY. Nothing changed. Read-only inspection produced this.
**Rule:** operator selects the option; no edit is made until then.

Read with `ios-design-coverage-audit-r2-2026-10-17.md` (§B2 toasts, §B3 sliders, §B4 icons).

---

## 0. Blockers to read before choosing

| # | Blocker | Consequence |
|---|---|---|
| K1 | **The launcher does not compile** — 92 errors, **64 in `PhotosActivity.kt`**, 21 in `CameraActivity.kt` | Do not touch those two files yet; they are mid-rewrite and cannot be compile-verified |
| K2 | **The parallel session owns these files** and is editing them live (shell line counts grew during the audit) | Any edit here races/conflicts. Two of the three work streams touch the same files. |
| K3 | **No iOS banner host helper exists** — `AppleuxNotificationBanner` is a raw `View` intended for the SystemUI overlay (grep found **0** call sites anywhere) | The toast fix is **not** a textual swap; it needs a new ~20-line host helper first |
| K4 | **No horizontal iOS slider component exists** — only `AppleuxVerticalSlider` (vertical CC capsule) and `AppleuxPowerSlider` (power-off/restart rows) | 4 of 5 `SeekBar` sites have **no matching component**; they need a new `AppleuxSlider` |
| K5 | `AppleuxNotificationBanner.bind()` falls back to `android.R.drawable.sym_def_app_icon` (L78) | Fixing this one line also removes 1 of the 43 stock-icon refs |

---

## 1. TOASTS — 36 call sites in 10 files

### Component reality
`AppleuxNotificationBanner` API: `bind(appLabel, body, iconDrawable)`, `show(animated)`, `hide(animated)`, `onOpen`, `onExpired`. It is a `FrameLayout`. To use it in an Activity you must add it to a host (activity root / content view). There is **no** static helper — that is the missing piece.

### Option T1 — Add a host helper, swap all 36 sites *(recommended)*
- **New code (design-system):** a small helper, e.g.
  `AppleuxBanner.show(activity, appLabel, body, icon = null)` that finds/attaches a
  container in the activity's root and calls `bind()` + `show()`. ~20-30 lines, one new file.
- **Then, per site:** `Toast.makeText(this, "Edits saved", Toast.LENGTH_SHORT).show()`
  → `AppleuxBanner.show(this, "Photos", "Edits saved")`.
- **Touch points:** 10 files, 36 sites.
- **Cost:** helper + 36 edits. **Risk:** low per-site; needs an activity-root lookup that works in all 10 activities.
- **Bonus:** fixes K5 as part of the helper (pass a vendored icon or the app icon).

### Option T2 — Façade named like Toast (textual rename only)
- **New code:** `AppleuxToast` object with `makeText(ctx, resId|CharSequence, dur): AppleuxToast` and `.show()`.
  Signature-compatible with `Toast`, so each site changes only the class name (`Toast` → `AppleuxToast`) and drops the unused `LENGTH_*` (or keeps it, ignored).
- **Cost:** façade + 36 near-mechanical renames. **Risk:** lowest (smallest diff per site).
- **Downside:** keeps the misleading "Toast" vocabulary and the `LENGTH_*` argument that does nothing.

### Option T3 — Fix only the high-visibility files
- Swap **Messages (12) + Photos (16) + Launcher (6)** = 34 of 36; leave the 2 Settings-file sites + CallScreen.
- **Cost:** helper + 34 edits, but Photos is blocked by K1 → effectively Messages + Launcher (18) now.
- **Downside:** leftover stock toasts remain; inconsistent.

### Option T0 — Defer
- Do nothing until the launcher tree compiles and the parallel session pauses.

**Per-file site counts:** Photos 16 · Messages 12 · Launcher 6 · SettingsList 4 · Security 2 · Display 2 · Phone 2 · Contacts 2 · Camera 2 · CallScreen 1.
*Files blocked by K1:* Photos, Camera → **18 sites** unreachable now.

---

## 2. SLIDERS — 5 real `SeekBar` sites

| # | File:line | What it is | iOS equivalent | Drop-in? |
|---|---|---|---|---|
| S1 | `settings/.../DisplaySettingsActivity.kt:96` | Brightness (horizontal, in Settings) | iOS Settings = **horizontal** slider | ❌ vertical only |
| S2 | `settings/.../SettingsListActivity.kt:613` | Generic row slider (`R.id.settingSeek`, layout-defined) | iOS Slider | ❌ |
| S3 | `launcher/.../PhotosActivity.kt:2529` | Editor value (−1…1: crop/rotate/straighten) | iOS edit slider | ❌ (and K1-blocked) |
| S4 | `launcher/.../PhoneActivity.kt:634` | Voicemail scrub seek | iOS playback scrubber | ❌ |
| S5 | `launcher/.../CameraActivity.kt:571` | Pro controls ISO/shutter/focus/exposure/temp (one bar reused ×5) | iOS camera tick sliders | ❌ (and K1-blocked) |

> `AppleuxPowerSlider.kt:24` also uses a `SeekBar` internally, but it *is* the component — not a call site to fix.

### Option S1 — Build one horizontal `AppleuxSlider` and use it for S1, S2 *(recommended)*
- **New code (design-system):** `AppleuxSlider` — a horizontal `View` in iOS idiom (thin 4dp track, white 28dp thumb, no Material tick), API `max/progress/onProgressChanged` compatible with `SeekBar` so S1/S2 swap is mechanical.
- **Then:** DisplaySettings brightness → iOS-like horizontal slider (correct idiom), SettingsList generic rows → same.
- **Cost:** 1 component + 2 sites. **Why first:** it is the only one of the five that matches real iOS Settings.

### Option S2 — Extend `AppleuxVerticalSlider` into a general slider *(not recommended)*
Reuse the existing capsule for brightness. **Wrong idiom for Settings** (iOS uses vertical only in Control Center; Settings is horizontal). Also would not serve S2-S5.

### Option S3 — All five via new components
Build horizontal `AppleuxSlider` **and** an iOS scrubber **and** camera tick-sliders. **Cost:** 3 components + 5 sites + 2 blocked files. Largest scope; defer the Camera/Photos parts until K1 clears.

### Option S0 — Defer all sliders
Leave Material sliders; revisit after the launcher compiles.

---

## 3. PHOTOS ICONS — 34 `android.R.drawable` refs in `PhotosActivity.kt`

**Blocked by K1** (the file does not compile). Mapping is prepared so it can be applied in one pass once it does.

**Available vendored icons:** 58 `sf_*` + 46 `il_ic_*`. Reusable here:
`sf_trash, sf_star, sf_heart, sf_clock, sf_calendar, sf_camera, sf_play, sf_add, sf_remove, sf_location, sf_maps, sf_lock, sf_key, sf_folder, sf_settings, sf_xmark, sf_checkmark` and `il_ic_search, il_ic_info, il_ic_share, il_ic_photos, il_ic_rotation, il_ic_settings`.

### Proposed mapping (23 distinct stock drawables → 18 mapped, 5 gaps)

| Stock drawable | Refs | Use |
|---|---|---|
| `ic_menu_search` | 436, 523 | `il_ic_search` |
| `ic_menu_gallery` | 508, 1497 | `il_ic_photos` |
| `ic_menu_myplaces` / `ic_menu_mylocation` | 513, 1490, 1491 | `sf_location` |
| `ic_menu_agenda` | 518, 1736 | `sf_clock` |
| `ic_lock_lock` | 528, 1426 | `sf_lock` |
| `ic_menu_delete` | 540, 927, 1325, 1429 | `sf_trash` |
| `ic_menu_share` | 902 | `il_ic_share` |
| `star_big_on` | 911, 1499 | `sf_star` |
| `ic_menu_info_details` | 923 | `il_ic_info` |
| `ic_menu_recent_history` | 1487, 1887 | `sf_clock` |
| `ic_menu_month` / `ic_menu_my_calendar` | 1488, 1489 | `sf_calendar` |
| `ic_menu_directions` | 1492 | `sf_maps` |
| `ic_menu_camera` | 1493 | `sf_camera` |
| `ic_media_play` | 1494 | `sf_play` |
| `ic_menu_rotate` | 1495 | `il_ic_rotation` |
| `ic_menu_add` | 1500 | `sf_add` |
| **`ic_menu_set_as`** | 907 | **gap** |
| **`ic_menu_edit`** | 915, 1736 | **gap** (no pencil) |
| **`ic_menu_crop`** | 919, 1498 | **gap** |
| **`ic_menu_revert`** | 1318 | **gap** (no undo) |
| **`ic_menu_compass`** | 1496 | **gap** |

### Option I1 — Map the 18, add 5 new vectors *(recommended)*
- Create 5 small vector drawables in iOS idiom: `sf_pencil`, `sf_crop`, `sf_arrow_uturn` (restore), `sf_photo_on_rectangle` (set-as/wallpaper), `sf_compass`.
- Then all 23 distinct refs → vendored icons, **34 sites** done; stock-icon count in Photos → 0.

### Option I2 — Map the 18, leave the 5 gaps on stock
- Partial; 5 refs (≈9 sites) remain stock. Fast, but not a clean sweep.

### Option I3 — Defer entirely until Photos compiles (K1)
- Recommended given K1, regardless of I1 vs I2.

---

## 4. Suggested order (least-risk first)

1. **T1 or T2 helper** — design-system only, no shell files → **not blocked by K1**, no conflict with the compiles.
2. **S1** — `AppleuxSlider` in design-system + DisplaySettings/SettingsList (settings compiles; only launcher is broken).
3. **T swap** in Settings files (6 sites) — settings compiles.
4. **After the launcher compiles (K1 clears):** T swap in Messages/Launcher/Photos/Camera/Phone/Contacts (28 sites), **S3/S4/S5** sliders, **I1** Photos icons.

> Steps 1-3 are in `design-system` and `settings`, both of which **compile today** (design-system APK 15:00 OK). They can proceed without waiting on the launcher. Steps 4 are all behind K1 and behind the parallel session's rewrite.

---

## 5. What I need from you

Pick one letter per line (or say "all recommended"):

- **Toasts:** T1 / T2 / T3 / T0
- **Sliders:** S1 / S2 / S3 / S0
- **Photos icons:** I1 / I2 / I3
- **Scope now:** only unblocked (steps 1-3) / include blocked files too (steps 1-4)

Nothing is edited until you answer.
