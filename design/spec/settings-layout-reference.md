# Settings layout reference — adopted

Source: `design-reference/inbox/settings-aosp16-ios27-tree.html` (saved to disk
2026-10-15, 81 KB, single self-contained file: CSS + icon library + settings
tree + backend map + renderer).

This is the layout reference for the Settings app. It is a **design board**,
not a port task: it renders 136 screens in a browser so the tree, the row
anatomy and the metrics can be read at a glance. Our `appleux-settings` stays
native; rows get built against this file's numbers one section at a time.

## Validation (machine-checked, not eyeballed)

| check | result |
|---|---|
| screens in `TREE` | **136** |
| `nav:` references | **186** across 135 unique targets — **all resolve**, none dangling |
| icons referenced by rows | **47 refs, 14 unique — all defined** in the 38-glyph library |
| backend map rows | **59** |
| defects found | **1** — fixed in our copy, marked inline |

The defect: the `wallpaper` screen declared `parent: 'wallpaper'` — a
self-parent, so its back button looped onto itself. Fixed to `parent: 'root'`
with an HTML comment marking the correction.

## Frame

`--w: 402px; --h: 896px`. Our measured frame is 401.9 × 896.0 dp, so the
0.1 dp width difference is rounding, not a disagreement.

## Row anatomy (the contract for every settings row we build)

| element | metric |
|---|---|
| large title | 34 sp / 700 / 41 lh, `0.011em` tracking, padding `8px 16px 4px` |
| search | 36 h, r10, card bg, 17 sp secondary label |
| section margin | `0 16px 28px`; header 13 sp uppercase secondary, `0 16px 6px` |
| card | bg `#1C1C1E` (dark), **r10**, overflow hidden |
| row | **min-height 44**, padding `11px 16px`; two-line rows min-height 52, `8px 16px` |
| separator | 0.5 px, inset **left 57 px** (16 px for icon-less rows) |
| icon square | **29 × 29, r6.5**, glyph 18 × 18 white |
| title | 17 sp / 400 / 22 lh, `-0.024em`; sub 13 sp secondary |
| value | 17 sp secondary-label; chevron 15 sp tertiary |
| toggle | **51 × 31, r15.5**, knob 27, translateX 20 when on, green `#30D158` |
| radio | blue check `✓` when on, hidden (layout-preserving) when off |
| slider | 4 h track, **24 px thumb** |
| status bar | 54 h, 16 sp semibold, 17/22 side padding |
| nav bar | 44 h, back chevron `‹` 24 px, centred 17 sp semibold title |
| home indicator | 134 × 5, r3, white 0.9, bottom 10 |
| destructive | system-red title (`#FF453A` dark) |

Dark values match our shipped tokens (`#1C1C1E` surface, `#30D158` green,
`#0A84FF` blue). Light values are the standard HIG set, not new information.

## Backend map — checked against this tree

59 entries. Most name real AOSP classes (`WifiP2pManager.discoverPeers()`,
`NightDisplayManager`, `UiModeManager.setNightMode()`, `UpdateEngine`,
`PrintManager`, `FaceManager`, `android.debug.AdbManager` — all present in
`frameworks/base`). Five entries do not survive contact with the tree:

| entry | verdict |
|---|---|
| `SmartLock` | **no AOSP class** — no `*SmartLock*` under `frameworks/base`. Smart Lock is a GMS feature. |
| `GoogleServices`, `AdvertisingIdClient` | **GMS, not AOSP**. Absent from an unbundled build. |
| `NearbyConnections.startDiscovery()` | **GMS** (Play Services Nearby). Not AOSP. |
| `DevicePolicyManager.wipeData() · resetPassword()` | **blocked for a settings row** — needs device/profile owner. Same ruling as the intake review §5. |
| `TelephonyManager.setDataEnabled()` | **deprecated** — use `setDataEnabledForReason(REASON_USER)`. files5 caught this independently. |

Everything else in the map is a usable starting pointer, not a verified call —
each still needs its permission and call-site checked when the row is built.

## How to use it

1. Open the file in a browser, tap through the tree, read the metric off the
   row being built.
2. Build the row in `appleux/settings` against the anatomy table above.
3. Check the backend entry against `frameworks/base` before wiring it; if it
   is one of the five flagged entries, the row is display-only until a real
   backend is found.

## Open

- Our `SettingsListActivity` currently ships 3 sections (AppleUX, display,
  security). This tree has 136 screens. The gap between them is the work, and
  it is tracked per-row, not per-screen.
- The reference's demo values (contact names, percentages, MAC addresses) are
  placeholders. Nothing in it reads a real device.