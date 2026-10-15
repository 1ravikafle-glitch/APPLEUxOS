# AppleUX — Progress and Gap Report (all sections)

Date: 2026-10-15 · Target: POCO M5, 4 GB / 3 GB budget · Verified on API 36 emulator, 1080×2400 @420dpi

---

## A. Reference material secured

| Source | Status | Authority |
|---|---|---|
| Apple iOS 27 **iPhone User Guide** | **53 official screenshots** + behaviour text, harvested | Apple, official |
| Apple iOS 27 **icon template** (`.ai`) | squircle extracted, **n≈4.4 superellipse** | Apple, official |
| **Figma UI Kit** | ❌ **BLOCKED** — 403 to all non-browser clients | Apple, gated |
| Liquid Glass values (MIT transcription) | ✅ 34pt radius, `#FFF@0.70`, rim, shadow | 3rd-party, labelled |
| HIG skillset (14 skills) | ⚠️ installed — but snapshot is **2025-02, pre-Liquid-Glass** | Apple, stale |

The Figma kit needs either the desktop app attached (no browser is attached to
this session) **or** a Figma REST API token. Neither is a technical problem I
can route around; the CDN blocks it by design.

---

## B. Section-by-section status

### 1. Home Screen / Launcher — ~45%
**Done:** icon grid, glass dock, wallpaper pair, widget host (clock/battery/calendar),
app library, long-press gallery, icon persistence, Apple's exact squircle mask.

**Gap:**
- **No pages, no page swiping, no page dots** — single flat grid
- **No app folders** at all (open/close model absent)
- **No icon drag/rearrange**, no grid-order persistence
- **Zero motion tokens used** — `MotionTokens` defines spring stiffness/damping; the launcher imports none of it. All iOS motion is absent
- **Long-press hijacked** — every long-press opens the widget gallery, including on icons. iOS shows a context menu. Widget long-press is unreachable
- App Library is a **list**, not a 3-column grid with categories + search
- Dock not persisted (first 4 alphabetically)
- Dock icon size conflict: 72dp cell vs 60dp rendered icon

### 2. Lock Screen — **0%**
`AppleuxLockScreen` is written but **never instantiated**. Apple's iOS 27 lock screen
(reference in hand, `8313a5fa`) = full-bleed wallpaper, very large lightweight
translucent clock spanning most of screen width, small date above, circular glass
flashlight/camera buttons in the bottom corners. Nothing competes with it.

### 3. Control Center — ~35%
**Done:** full-screen Liquid Glass sheet (v2), blur-behind, kit corner radius/fill,
header with connectivity + date/time + battery, real tile actions, swipe-up dismiss.

**Gap:**
- Controls were icon+**label** pairs; iOS uses **circles with no label**. Labels now hidden (v4)
- **Controls went invisible** — inactive fill was near-white on white glass. iOS uses *dark* translucent discs. Fix in build
- **No vertical brightness/volume sliders** (iOS first-class)
- **No media player card**, no Focus pill, no connectivity cluster, no `+`/power buttons
- **No right-edge page dots**, no edit mode, no long-press secondary panels
- Header type oversized, collides with status-bar clock
- ⚠️ **Behavioural bug:** tapping Wi-Fi/Bluetooth calls `setWifiEnabled(false)`. Per Apple's guide, tapping **disconnects** and leaves the radio on

### 4. Notification Center — ~60%
**Done:** real `NotificationListenerService` feed, app badges (tinted), action buttons,
swipe-dismiss, clear-all, header/list/footer layout fixed.

**Gap:** flat opaque white — **no glass material at all**; no grouping cards; row metrics not iOS.

### 5. Navigation / Multitasking — ~10%
Recents is stock QuickStep (documented boundary per replace-from-source charter).
No own task switcher, no gesture-nav parity. Every app switch drops into stock Android UI.
Status bar / navigation bar: **blocked** — needs platform signature, ROM-stage item.

### 6. System-wide components — ~15%
`AppleuxStatusBar`, `AppleuxNavigationBar`, `AppleuxSheet`, `AppleuxDialog`,
`AppleuxCard`, `AppleuxToggle`, `AppleuxListItem` — **all defined, all unused**.

### 7. Built-in apps — ~25%
Settings exists (green switches, blue sliders, glass slider, real platform writes).
Phone, Contacts, Messages, Photos, Camera, Clock, Calendar, Files, Browser: **not started**.

---

## C. The root cause

**The design system is bypassed.** Measured, not estimated:

| | |
|---|---|
| Components never instantiated | **8 of 12** (LockScreen, StatusBar, NavigationBar, Sheet, Dialog, Card, Toggle, ListItem) |
| Design-system imports in the launcher | **1** (`AppleuxHaptics`) |

Every surface hand-rolls text, cards, spacing and motion. Fidelity never compounds —
fixing one screen never shows up in another. This is Phase A and blocks everything else.

---

## D. Bugs found and fixed this session

| Bug | Impact |
|---|---|
| `SYSTEM_ALERT_WINDOW` appop rejected → `wm.addView` throws | **Every panel gesture was a silent no-op for a full session.** Was logged at WARN, cached as failure, never surfaced |
| Stale `/data/app` install shadowing `/system/priv-app` | Device ran a 09:56 build while the image on disk was new |
| Fullscreen overlays swallow all in-bounds touches | Swipe never fires `onClick` → panels were an inescapable dead end |
| `weight=1` inside a `FrameLayout` (inert) | Header rendered straight through the first notification |
| Overlay inset by display cutout (1080×2209 not ×2400) | Launcher leaked through the top band |
| `RenderEffect` used as "glass" | Blurs only a window's own content — never what is behind it |
| Rounded-rect squircle instead of Apple's | 38% further from Apple's outline than a superellipse |

---

## E. Unverified / risk

- **Blur-behind frame cost is unmeasured.** Fullscreen `FLAG_BLUR_BEHIND` is the most expensive path in the build. Must be gated on measurement (JankStats + `gfxinfo`) before shipping.
- No claim in this report is a device measurement. Emulator figures are SwiftShader software rendering and say nothing about the Helio G99.

---

## F. Next, in order

1. **A** — make components actually used (unblocks all)
2. **B** — Control Center to reference (sliders, media, clusters, dots)
3. **C** — Lock Screen (highest visibility, currently zero)
4. **D** — glass material on Notification Center
5. **E** — own task switcher
6. **G** — measurement, then real-hardware profiling
---

## G. Correctness bugs found by the parallel audit (2026-10-15)

Found by subagent audit, verified by hand in source. **These are functional
defects, not cosmetic ones.**

1. **Every Control Center control displayed the inverse of its true state.**
   `AppleuxControlCenter.kt` click path ran `tile.onClick()` → service →
   `refreshTileStates()` → `setActive(realState)`, then called
   `tileView.animateToggle()` which flipped `isActive` *again* locally.
   Two writers, opposite results. Fixed: `animateToggle()` is now press
   feedback only; `setActive()` is the single writer of appearance.

2. **Wi-Fi and Bluetooth tiles are not toggles.** `setWifi(on: Boolean)`
   ignores its parameter entirely and always launches
   `Settings.Panel.ACTION_WIFI`; `openBluetoothSettings()` likewise always
   opens the Bluetooth settings screen. Tapping either opens a settings page
   rather than changing anything. Apple documents the iOS behaviour as
   *disconnect, radio stays on* — which Android cannot do from a normal app
   since API 29 (`setWifiEnabled` is privileged). Needs a decision: either
   relabel honestly as a shortcut, or obtain the platform signature on the
   ROM to make it a real toggle. **Not silently left as-is.**

3. **Brightness slider scale mismatch.** `readBrightness()` returns 0–255 and
   is assigned into a `max = 100` SeekBar. Initial 128 pins the slider at
   maximum; dragging writes back 0–100 as the platform value, capping
   brightness at ~39%.

4. **Dismiss ignores `isClearable`.** Swipe-dismisses any row including
   `FLAG_ONGOING_EVENT` / `FLAG_NO_CLEAR` notifications.

5. **Settings section headers print their title twice.** `SECTION_HEADER`
   reuses the body row layout, and `titleRes == summaryRes` for all three
   headers.

6. **Settings Dark Mode switch is inert.** `SettingsActivity` applies the
   theme then `finish()`es; `SettingsListActivity` never applies it, and
   `applyToActivity` reads platform uiMode rather than `KEY_DARK_MODE`.

7. **`AndroidManifest-debug.xml` references `.AppleuxSystemUIActivity`**, which
   does not exist in the tree.

### Unverified claim
A subagent reported that `android:process="com.appleux.systemui"` puts the
notification listener in a different process from the service, which would
break the in-memory `AppleuxNotificationBus`. **This contradicts observation** —
the real feed renders correctly on device (screenshots
`02-shade.png`, `appleux-emu-shade-live2`). Not treated as a bug until the
manifest is read directly.

---

## Addendum — Debloat / storage / RAM tooling (m2 scope)

Three scripts under `scripts/`, all `bash -n` clean, all `set -euo pipefail`:

| Script | Does | Mutates device |
|--------|------|----------------|
| `debloat-disable.sh` | Tiered `disable`/`enable`/`status`. 76 package literals across 6 tiers; `all` = 73 (excludes `replaced`) | Only on disable/enable; fully reversible |
| `storage-report.sh` | `/data` df, per-app data+cache, per-app cache, APK sizes, partition totals | Read-only |
| `ram-report.sh` | `/proc/meminfo`, `dumpsys meminfo`, per-AppleUX-package PSS, process count, LMKD activity | Read-only |

### Reversibility and idempotency

Verified against a stateful fake `pm` stub, not just by inspection:

- `disable` twice in a row → second run reports `already-disabled`,
  `changed=0`. Idempotent.
- `enable` after that → `changed=3`, state fully restored.
- `enable` again → `already-enabled`, `changed=0`. Idempotent.
- `enable` iterates the *same* `packages_for_tier` source as `disable`, so a
  package that can be disabled can always be re-enabled. No one-way path.

### v1 defects fixed

1. `set -u` only — no `set -e`, so a failed `adb` call mid-loop continued.
2. Default mode was `disable`. A bare invocation mutated the device. Default is
   now `status` (read-only).
3. One `adb` round-trip **per package** for the install check. Now 2 total
   (`pm list packages`, `pm list packages -d --user N`).
4. `enable` ran `pm enable` without `--user`, but `disable` used
   `disable-user --user 0` — asymmetric, could leave a work profile untouched.
5. v1's list was mostly dead: `com.android.protips` has not shipped since
   Android 9, so most of the 15 entries were no-ops.
6. No tiering, so a genuinely risky package could not be separated from a
   genuinely free one.

### Gate that made the Google tier safe

`grep -rE 'com\.google\.' appleux/` → **zero hits**. Every AppleUX module
references only `android.*` and `com.appleux.*`. The launcher holds
`QUERY_ALL_PACKAGES` and enumerates whatever is installed, so removing the
Google stack changes the app grid but breaks no AppleUX code path.

### Two open gaps this tooling deliberately does not close

- **`com.android.settings` stays enabled.** AppleUX Settings answers
  `android.settings.*`, but AGENTS.md keeps stock Settings as the intent
  fallback until ACTION_SETTINGS coverage is audited. Disabling it would
  remove that fallback.
- **Clock and Calendar are removed as apps while only widgets exist.**
  `com.android.deskclock` and `com.android.calendar` are in tier `media` with
  that consequence stated. Tier exists so this is a deliberate act, not a
  side effect of typing `all`.

### Not yet measured

No device output exists. `storage-report.sh` and `ram-report.sh` are written to
produce exactly what `deliverables/m2/storage-ram-baseline.md` currently
*estimates*. Run both on the POCO M5 and replace the estimates before any
storage or RAM figure is quoted.
