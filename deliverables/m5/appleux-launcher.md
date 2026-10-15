# Phase 5 — AppleUX Launcher

**Status: source complete, NOT built, NOT run.** No frame-timing or memory
number in this document is measured. See `deliverables/m7/` for why.

## Scope

A home-screen launcher built on the AppleUX design system and wired to the
Adaptive Performance Controller, so icon rendering degrades with device load
instead of dropping frames.

## Files

| File | Role |
|---|---|
| `LauncherActivity.kt` | entry point; owns view tree, wires controller listeners |
| `ui/HomescreenGrid.kt` | 4-column `RecyclerView` grid + adapter + view holder |
| `ui/DockView.kt` | horizontal dock + adapter + view holder |
| `ui/AppLibraryView.kt` | all-apps sheet; `AppIconItem` data class; app enumeration |
| `ui/AppIconView.kt` | single icon cell; scales to render quality |
| `gestures/GestureHandler.kt` | swipe-up gesture detection with haptic feedback |

## Design decisions

**Icon rendering follows quality level.** `AppIconView.bind()` multiplies
`IconTokens.SYSTEM_ICON_SIZE_HOME_SCREEN` by the controller's current render
scale, so under load icons shrink rather than the grid dropping frames. At
quality ≤ 3 it forces a hardware layer to reduce raster cost.

**Rebinds are guarded.** Both `HomescreenAdapter.setRenderScale` and
`setQualityLevel` early-return when the value is unchanged. The controller can
fire at 5 Hz; without this guard a whole-grid `notifyDataSetChanged()` would
run on every tick. This is the single most important detail for keeping the
launcher from becoming the performance problem it exists to avoid.

**Dock holds four slots.** A fixed small set keeps dock layout stable and
avoids relayout churn. `DOCK_SLOTS = 4` in `LauncherActivity`.

**Item view cache is pre-sized.** `setItemViewCacheSize(16)` on the grid plus
`setHasFixedSize(true)`, because the cell size is fixed at bind time.

**App enumeration is not filtered by system flag.** `AppLibraryView.loadApps()`
returns all launcher activities, sorted case-insensitively. An earlier draft
dropped `FLAG_SYSTEM` apps, which would have hidden Settings and the AOSP
launcher itself.

## Performance wiring

`LauncherActivity` registers as a `PerformanceListener` and also observes
`AdaptiveRenderPolicy`. On quality or scale change it updates grid and dock.
On `onDestroy` it unregisters both — a leaked listener keeps the activity
alive and keeps rebinding a detached view tree.

## Known gaps

- `GestureHandler` detects the swipe-up but `openLibrary()` does nothing; the
  library is never opened by gesture. Wiring it needs the scrim/library pair
  from `LauncherActivity`.
- No widget host. The `widgets` directory was created and removed; empty.
- No icon paging. All apps live on the grid with no folder support.
- No persistence for dock contents; dock is the first four apps alphabetically.
- `QUERY_ALL_PACKAGES` is declared with a `tools:ignore`. That is acceptable
  for a launcher but would fail Play review — irrelevant for AOSP, noted for
  honesty.
- Long-press, drag-to-reorder, and uninstall-from-grid are absent.