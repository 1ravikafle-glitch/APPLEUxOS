# Phase 6 — AppleUX Settings

**Status: source complete, NOT built, NOT run.** No measurement in this
document is measured. See `deliverables/m7/` for why.

## Scope

An iOS-grouped Settings app whose performance screen surfaces live Adaptive
Performance Controller state.

## Files

| File | Role |
|---|---|
| `SettingsActivity.kt` | manifest entry; delegates to the list and finishes |
| `SettingsListActivity.kt` | settings catalog, adapter, view holder |
| `pref/AppleuxPreferences.kt` | typed `SharedPreferences` accessor |
| `performance/PerformanceSettingsActivity.kt` | live quality / scale / monitoring readout |
| `display/DisplaySettingsActivity.kt` | display placeholder |
| `security/SecuritySettingsActivity.kt` | security placeholder |

## Design decisions

**Static catalog instead of a preference framework.** `SettingsCatalog.build()`
returns a `List<SettingsItem>` of a sealed shape (`SECTION_HEADER`, `SWITCH`,
`SEEK_BAR`, `NAVIGATION`). A `RecyclerView` adapter keyed on
`type.ordinal` handles view types. `PreferenceFragmentCompat` would have pulled
in the whole androidx.preference dependency tree for what is nine static rows.
On a 3 GB target the dependency is the cost, not the abstraction.

**Preferences go through one typed accessor.** `AppleuxPreferences` exposes
`getBoolean` / `setBoolean` / `getInt` / `setInt` / `getString` / `setString`
over a single `SharedPreferences` file, double-checked singleton. No
`PreferenceManager` default screen, no per-key string constants scattered
across classes.

**Seek-bar writes are gated on `fromUser`.** `onProgressChanged` fires during
programmatic `progress` assignment too. Without the `fromUser` check, binding
a view writes the preference back — a feedback loop that dirties storage on
every rebind.

**The performance screen is a live observer, not a snapshot.**
`PerformanceSettingsActivity` registers as a `PerformanceListener` and updates
three text views on each callback, unregistering in `onDestroy`. This is the
verification surface for the controller: if escalation, decay, or recovery is
broken, it is visible without a debugger.

## Known gaps

- Navigation items are inert. `DisplaySettingsActivity` and
  `SecuritySettingsActivity` render a title and nothing else; tapping
  "Lock Screen" or "Brightness" goes nowhere.
- `Brightness` writes to `SharedPreferences` instead of
  `Settings.System.SCREEN_BRIGHTNESS`, so it has no effect on the actual
  screen despite holding `WRITE_SETTINGS`.
- The adaptive-performance switch stores a preference but does not gate the
  controller. `AppleuxPerformanceController.start()` is unconditional in
  `LauncherActivity`.
- `Reduce Motion` and `Low Power Mode` are stored and never read.
- No search, no groups beyond flat headers, no per-page navigation.
- `dark_mode` is stored but never propagated to `AppleuxTheme.isDarkMode`, so
  the dark-mode toggle does nothing.

The last four items are wiring gaps, not design gaps. They are listed because a
settings app whose switches do nothing is worse than no settings app.