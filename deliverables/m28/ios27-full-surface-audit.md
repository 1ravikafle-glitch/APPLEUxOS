# iOS 27 Surface Parity Audit — AppleUX Custom ROM

**Sources (all read this session):**
- Real iOS 27 IPSW: `/home/elfakiris/ios-ui-analysis/`
- Ground-truth photos: `design-reference/iphone11-ios27/drive/` (47 files)
- Baseline spec: `design/spec/ios27-baseline.md` (630 surfaces, Batches 1–19)
- Our sources under `/home/elfakiris/OsProject/android-16/frameworks/appleux/`
- Dead-component check: grep over all .kt/.xml files

---

## Executive Summary

| Surface | iOS 27 Fidelity | Blocking Gaps |
|---|---|---|
| **Home grid/layout** | 95% | Icon badges missing |
| **Widgets (home)** | 40% | 3-widget closed set; no sizes/resize/3rd-party; flat dark cards wrong in light mode |
| **App Library** | 85% | Search pill wrong hint; 3-col vs iOS 2-col folders; no widget previews |
| **Today View** | 75% | Widget views are placeholder text; no real data backends |
| **Gestures** | 85% | NC double-opens stock; top-edge origin not checked; up-hold dead |
| **Control Center** | 80% static / 0% runtime | `m SystemUI` never built; tile grid empty (no tile registry) |
| **Notification Center** | 75% | Banner/expanded/footer dead; double-opens AOSP shade |
| **Spotlight** | 30% | Frontend only; no results backend |
| **Dock** | 95% | — |
| **Recents (with apps)** | 30% | Invisible switcher; `AppleuxRecentsView` dead; no empty state |
| **Recents (no apps)** | 0% | Nothing happens |
| **System chrome** | 60% | Lock screen absent; status bar not in 3rd-party apps |
| **Settings** | 21% root / 10% sub | 22/28 root rows missing; General branch 0/21; no search pill |

---

## Surface-by-Surface Detail

### 1. HOME — Grid, Icons, Folders, Edit Mode

| iOS 27 | AppleUX (file:line) | Verdict |
|---|---|---|
| 4×6 grid, 60dp icons | `LauncherTokens.kt:67-70` — 4×6, derived from measured 392.73×875.64dp | ✅ |
| No page wrap; dots hide on 1 page | `LauncherActivity.kt:719-727` | ✅ |
| Dock apps excluded from grid | `loadApps()` line 739 filters dock packages | ✅ |
| Folders: named, multi-page | `loadFolders`/`showFolderPopup` — **single hardcoded name "Folder"**; rename exists, `addToFolder` always writes "Folder" | 🟡 partial |
| Long-press → jiggle | `enterEditMode()` calls `AppleuxJiggle` (live now), haptic, wallpaper-tap exit | ✅ |
| Icon badges (red count) | **Zero badge code** in `AppIconView.kt` / `AppleuxIcon.kt` | ❌ |
| Swipe L/R pages | `nextPage()`/`prevPage()` + library past last page | ✅ |
| Swipe right from page 1 → Today | `openTodayView()` line 1243 | ✅ |
| Swipe up anywhere → home/close library | `onSwipeUp` — no-op if home, closes library | ✅ |
| Swipe up + hold 300ms → app switcher | `SHOW_RECENTS` → **log only** (recents = QuickStep) | ❌ |
| Swipe down zones (CC/NC/Spotlight) | `onSwipeDownAt(xFraction)` — **uses xFraction only, ignores downY** (any swipe-down anywhere triggers) | ⚠️ |

### 2. WIDGETS — Home Screen

| iOS 27 | AppleUX | Gap |
|---|---|---|
| 4 sizes: small/medium/large/extra-large | `WIDGET_SPAN_SMALL=2`, `WIDGET_SPAN_MEDIUM=4`, height fixed `WIDGET_CELL_HEIGHT_DP=150` | 2 sizes only |
| Widget stacks (swipeable) | Not supported | Missing |
| Smart rotate | Not supported | Missing |
| Widget gallery | `openWidgetGallery()` → `AppleuxDialogs.choose()` — **title list, max 3 widgets**, "Widget limit reached" toast | Wrong pattern, arbitrary limit |
| 3rd-party widgets | `requestBindWidget()` only accepts our 3 `widgetDefs` (clock, battery, calendar) | Closed set |
| Placement | Widgets **pinned above apps** in adapter (`getSpanSize` puts widgets first) | Pinned; no interleaving |
| Clock widget | `widget_clock.xml`: **TextClock 44sp bold + date** — digital | iOS = analog dial with hands |
| Weather widget | **None** (exists in IPSW, not in our 3) | Missing — key photo shows it |
| Card background | `widget_card_dark.xml`: **always-dark** `#CC1C1C1E` flat fill + 1dp rim | Wrong in light mode; no adaptive gradient art |
| Lock screen widgets | Circular/rect/inline — **no keyguard host** (`AppleuxLockScreen` dead) | Blocked on lock screen |
| Stock controls in widgets | `TextClock`/`ProgressBar` in 6 widget layouts (AGENTS stock-surfaces table) | Replace-from-source violation |

**IPSW finding:** 72 widget `.car` files in IPSW — **zero rendered images**. All widget art is SwiftUI-rendered at runtime. Translation strategy = hybrid host: first-party widgets as native in-process views (Canvas), third-party via `AppWidgetHostView` with squircle clip.

### 3. APP LIBRARY

| iOS 27 | AppleUX (`AppLibraryView.kt`) | Gap |
|---|---|---|
| Search pill on top | Present — but **hint = "Q  App Library"** (iOS = "Search Apps") | Wrong hint |
| 2-col category folders (1 large + 3 minis) | **3 columns** (`APP_LIBRARY_COLUMNS=3`), category folders use 4-col grid inside dialog | Column count wrong |
| Categories (fixed order) | `ios27Categories = ["Suggestions","Recently Added","Social","Utilities","Creativity","Productivity"]` | Matches |
| `categoryOf()` heuristic | Package-name prefix matching (camera→Creativity, message→Social, etc.) | Heuristic; not Apple's curated |
| Tap folder → full category grid | `openFolder()` → `AppleuxDialogs.custom` with 4-col `GridLayout` in `ScrollView` | Works; but dialog not page push |
| Search filters live | `filter(q)` on query text change | ✅ |
| Badges on folder icons | **No badge rendering** in `AppleuxLibraryFolder.bind()` | Missing |

### 4. TODAY VIEW (`AppleuxTodayView.kt`)

| iOS 27 | AppleUX | Gap |
|---|---|---|
| Swipe right from page 1 | `openTodayView()` in `LauncherActivity.kt:1243` — slide-in from right | ✅ |
| Glass background + blur | Fixed: `scrimAlphaForLevel` + `AppleuxGlass.applyBlur` | ✅ (was GAP-7) |
| 4-col widget grid | `GridLayout` columnCount=4 | ✅ |
| Widget sizes from IPSW DefaultIconState.plist | 8 widgets added: Calendar(2×2), Weather(2×2), News(2×4), Maps(4×2), Tips(2×2), Batteries(2×2), Stocks(4×2), Screen Time(4×4) | Sizes match IPSW |
| Widget content | **All placeholder text** — `CalendarWidgetView` = "Calendar"/"No Events", `WeatherWidgetView` = "Weather"/"Sunny 72°", etc. | No real data backends |
| Live data | None — `BatteriesWidgetView` has no `BatteryManager` binding, `CalendarWidgetView` no `CalendarProvider` | Missing |

### 5. GESTURES (`GestureHandler.kt`, `LauncherActivity.kt:677-696`)

| Gesture | iOS 27 | AppleUX | Gap |
|---|---|---|---|
| Swipe L/R pages | Page turn, no wrap | `nextPage()`/`prevPage()` + library past last | ✅ |
| Swipe right from page 1 | Today View | `openTodayView()` | ✅ |
| Swipe L/R at bottom edge (yFrac>0.85) | Quick app switch | `cycleRecentApp()` — works from launch history, **needs ≥2 entries** | 🟡 |
| Swipe down top-right (>0.6) | Control Center | `toggleControlCenter()` → reflective call (no-op) → broadcast `OPEN_CONTROL_CENTER` → `AppleuxShadeHost` | ✅ path exists |
| Swipe down top-left (<0.4) | Notification Center | Broadcast `OPEN_NOTIFICATION_CENTER` → **also calls `expandNotificationsPanel`** (stock AOSP opens too) | ⚠️ double-open |
| Swipe down center (0.4–0.6) | Spotlight | `openSpotlight()` — `AppleuxSpotlightView` live | ✅ |
| Swipe up | Home | No-op if home; closes library | ✅ |
| Swipe up + hold | App switcher | Log only — "recents is stock QuickStep" | ❌ dead |
| Long-press | Jiggle/edit | Root-owned, hit-tests grid → edit mode | ✅ |

**Critical deviation:** `GestureHandler` zones by **x-fraction of whole screen**, ignores `downY`. iOS only treats swipes *starting in top edge* as CC/NC/Spotlight. Our launcher opens CC on swipe-down *anywhere*.

### 6. CONTROL CENTER (`AppleuxControlCenter.kt`, `AppleuxShadeHost.kt`)

| iOS 27 | AppleUX | Gap |
|---|---|---|
| Layout: connectivity cluster + media card + focus pill + sliders + 4-col tile grid (54 modules) | Exact structure implemented: `connectivityCluster`, `mediaCard`, `focusPill`, `brightnessSlider`/`volumeSlider`, `tileContainer` (4-col) | ✅ static layout |
| Module sizes | Media = 2×2, others 1×1 (63dp) | Implemented |
| Page dots | Vertical right-edge dots (`setDots()`) | ✅ |
| Tile registry / customization | `tiles` list + `ControlTile` model; `editMode` alpha fade | Model exists; **no tile population / registration** |
| Open path | `AppleuxShadeHost.attach()` called from `ShadeTouchableRegionManager.setup():225` + receiver `OPEN_CONTROL_CENTER` | ✅ wired in SystemUI |
| Dismiss | `AppleuxPanelDismissHandler.attach()` on swipe/scrim | ✅ live now |
| Runtime | **`m SystemUI` never built** — zero verification | ❌ 0% runtime |
| `AppleuxCCTileOrder` | Live(1) but only in ShadeHost comment | Not used |

### 7. NOTIFICATION CENTER (`AppleuxNotificationShade.kt`, `AppleuxShadeHost.kt`)

| iOS 27 | AppleUX | Gap |
|---|---|---|
| Header + list + Clear All stacked | `AppleuxNotificationShade`: header, RecyclerView, Clear All | ✅ |
| Cutout reservation | `AppleuxDisplayInsets.applyTopInset(context, column)` line 93 | ✅ (was GAP) |
| Swipe dismiss | `ItemTouchHelper` with `getMovementFlags`/`onMove` | ✅ |
| Grouped/stacked notifications | `AppleuxNotificationBus` publish → `subscribe` in ShadeHost → `setNotifications` | ✅ chain complete |
| Media slot | `setMedia()` on shade | ✅ |
| Dynamic Island binding | `pushIsland()` + `bindIsland()` | ✅ |
| Heads-up banner | `AppleuxNotificationBanner` **DEAD** | Missing |
| Expanded view | `AppleuxNotificationExpanded` **DEAD** | Missing |
| Footer actions | `AppleuxNotificationFooter` **DEAD** | Missing |
| Open path double-expand | `AppleuxSystemUi.kt:57` calls `expandNotificationsPanel` + broadcasts | ⚠️ AOSP shade also opens |

### 8. SPOTLIGHT (`AppleuxSpotlightView.kt`)

| iOS 27 | AppleUX | Gap |
|---|---|---|
| Search field + live results | `EditText` + `LinearLayout` results list; `onQuery` callback | Frontend only |
| Results backend | **None** — host must supply `submitRows()` | No indexer |
| App shortcuts / Siri suggestions | Not implemented | Missing |

### 9. DOCK (`DockView.kt`, `DockPreferences.kt`)

| iOS 27 | AppleUX | Gap |
|---|---|---|
| 4 fixed slots, 60dp icons | `DockView` + `DockPreferences` delimiter-encoded persistence | ✅ |
| Glass bar w/ superellipse + 34dp radius | `AppleuxComponentsWiring.syncGlassForeground` uses Figma kit radius | ✅ |
| Dock apps excluded from grid | `loadApps()` filters dock packages | ✅ |
| Long-press → replace/remove/reset | `showDockContextMenu()` with remove/replace | ✅ |

### 10. RECENTS — With Apps / Without Apps

| Scenario | AppleUX Behavior |
|---|---|
| **With apps** — bottom-edge L/R swipe | `cycleRecentApp()` walks persisted launch history (12 entries, `rememberLaunch`), starts activity directly — *launcher-side switcher, no cards* |
| **With ≥2 history** | Works silently |
| **With <2 history** | Silent no-op (`if (h.size < 2) return`) — **no empty state, no visual feedback** |
| **Swipe-up-hold** | `SHOW_RECENTS` → log only — recents remains stock QuickStep (locked decision) |
| **`AppleuxRecentsView`** | **DEAD** — iOS card UI exists, zero references |

iOS shows an empty app-switcher card view either way; we show nothing.

### 11. SYSTEM CHROME

| Surface | Status |
|---|---|
| Status bar (in shade) | ✅ `AppleuxShadeStatusBar.attachToShadeHeader` called at `ShadeTouchableRegionManager.java:227` |
| Status bar (home) | ✅ `AppleuxStatusBar` at `LauncherActivity.kt:491` |
| Status bar (3rd-party apps) | ❌ Still stock AOSP — no host in status-bar window |
| Notch | ✅ `AppleuxNotchHost.attach(this)` in `StatusBarWindowView.java:89` |
| Home indicator | ✅ `navigation_bar.xml` + `AppleuxHomeIndicatorView` |
| Lock screen | ❌ **`AppleuxLockScreen` never instantiated**; `AppleuxPasscodeView` DEAD; no keyguard host in SystemUI |
| Volume HUD | 🟡 `AppleuxVolumeKeys` live from launcher only; `AppleuxSystemHUD` DEAD |
| Power | 🟡 `AppleuxPowerControl` in SystemUI; `AppleuxPowerSlider` referenced only in comments/manifest |

### 12. SETTINGS (catalog from `SettingsListActivity.kt`)

| Metric | iOS 27 | AppleUX |
|---|---|---|
| Root rows | 28 | 6 (21%) |
| Root groups | 5 | 3 |
| Sub-branches | 50+ | 5 detail pages (10%) |
| Search | Floating bottom pill | **Missing** |
| Account card | Avatar + name + subtitle | **Missing** |
| Value text on nav rows | Right-aligned gray | **Missing** |

**Root screen — exact iOS 27 order (from ground truth photos):**

```
[Account Card]
[Group: Connectivity] Airplane(toggle), Wi-Fi(value), Bluetooth(value), Cellular(value), Hotspot(value), Battery(value)
[Group: Apps] Appearance, Camera, Control Center, Display, Home, Search, Siri, StandBy, Wallpaper
[Group: Notifications] Notifications, Sounds, Focus, Screen Time
[Group: Security] Face ID & Passcode, Emergency SOS, Privacy & Security
[Group: Apple Services] Game Center, iCloud, Wallet, Apps
[Search Pill — floating bottom]
```

**Implemented (6):** Adaptive Performance, Low Power, Reduce Motion, Dark Mode, Brightness, Focus (stock intent), Screen Time (stock intent), Lock Screen (→ SecuritySettingsActivity)

**Missing root rows (22):** Account card, Airplane, Wi-Fi, Bluetooth, Cellular, Hotspot, Battery, Appearance, Camera, Control Center, Display(nav), Home, Search, Siri, StandBy, Wallpaper, Notifications, Sounds, Face ID, Emergency SOS, Privacy, Game Center, iCloud, Wallet, Apps

**Missing General sub-branch (21 rows from photos):** About, Software Update, iPhone Storage, AppleCare, AirDrop, AirPlay, Picture in Picture, Screen Capture, CarPlay, AutoFill, Background App Refresh, Date & Time, Dictionary, Fonts, Keyboard, Language, TV Provider, VPN, Legal, Reset, Shut Down(blue)

---

## Dead Components (zero references outside own file)

| Component | Status | What it blocks |
|---|---|---|
| `AppleuxLockCustomizeSheet` | DEAD | Lock screen customize |
| `AppleuxNotificationBanner` | DEAD | Heads-up notifications |
| `AppleuxNotificationExpanded` | DEAD | Expanded notification view |
| `AppleuxNotificationFooter` | DEAD | Notification actions |
| `AppleuxPasscodeView` | DEAD | Passcode entry |
| `AppleuxRecentsView` | DEAD | App switcher cards |
| `AppleuxScreenshotToolbar` | DEAD | Screenshot UI |
| `AppleuxSettingsDetailView` | DEAD | Settings hero card |
| `AppleuxSOSCountdown` | DEAD | Emergency SOS timer |
| `AppleuxSystemHUD` | DEAD | Volume/brightness HUD |
| `AppleuxUnavailableTimer` | DEAD | Unavailable timer |
| `AppleuxTouchFeedbackOverlay` | DEAD | Must stay dead (swallows taps) |

**Now live (parallel work):** `AppleuxCCTileOrder`, `AppleuxJiggle`, `AppleuxPanelDismissHandler`, `AppleuxPowerSlider`

---

## Top 5 Fixes by Visual/Functional Weight

1. **Build + boot SystemUI** — makes CC/NC/Status Bar/Notch/Home Indicator real (currently 0% runtime)
2. **Lock screen host** for `AppleuxLockScreen` + `AppleuxPasscodeView` — only missing lock surface
3. **NC open path** — drop `expandNotificationsPanel` fallback; gate CC/NC/Spotlight zones to top-edge origin (check `downY`)
4. **Widget gallery + hybrid host** — open to system widgets, native canvas for first-party (analog clock, weather gradient), lift 3-widget cap
5. **Recents empty state + wire `AppleuxRecentsView`** — show card UI on swipe-up-hold; populate from launch history

---

## File Targets for Implementation

| Surface | Files to modify/create |
|---|---|
| Home badges | `launcher/.../ui/AppIconView.kt` (add badge drawable) |
| Widgets | `launcher/.../widgets/AppleuxWidgets.kt` (extend `widgetDefs`); `LauncherActivity.kt` (hybrid host); new widget views |
| App Library | `launcher/.../ui/AppLibraryView.kt` (2-col folders, search hint, badges) |
| Today View | `design-system/.../components/AppleuxTodayView.kt` (real data backends for 8 widgets) |
| Gestures | `launcher/.../gestures/GestureHandler.kt` (add `downY` gate); `LauncherActivity.kt` (zone logic) |
| Control Center | `design-system/.../AppleuxControlCenter.kt` (tile registry + population); `systemui/chrome/.../AppleuxShadeHost.kt` (bind tiles) |
| Notification Center | `design-system/.../AppleuxNotificationShade.kt` (wire dead components); `AppleuxSystemUi.kt` (drop AOSP fallback) |
| Spotlight | `design-system/.../AppleuxSpotlightView.kt` (results backend) |
| Recents | `launcher/.../LauncherActivity.kt` (wire `AppleuxRecentsView` on hold); `design-system/.../AppleuxRecentsView.kt` (implement) |
| Lock Screen | `systemui/.../keyguard/` (new host) + wire `AppleuxLockScreen` + `AppleuxPasscodeView` |
| Settings | `settings/.../ui/SettingsListActivity.kt` (all 28 root rows); `SettingsCatalog.kt` (new); detail activities for each branch |

---

## Verification Checklist (per-surface)

- [ ] Home: badges render on icon long-press/notification; grid 4×6 measured; folders multi-name
- [ ] Widgets: analog Clock, gradient Weather, 4 sizes, stacks, gallery with previews, 3rd-party browse
- [ ] App Library: 2-col folders, correct search hint, badges on folder icons
- [ ] Today View: 8 widgets show live data (Calendar, Weather, News, Maps, Tips, Batteries, Stocks, Screen Time)
- [ ] Gestures: CC/NC/Spotlight only from top edge; up-hold → recents cards; bottom-edge L/R → app switch
- [ ] CC: tiles populated, customizable, sliders live, media card shows now-playing
- [ ] NC: heads-up banner, expanded view, footer actions; no AOSP double-open
- [ ] Spotlight: live app/file/web results
- [ ] Dock: 4 slots, glass bar, persist, long-press menu
- [ ] Recents: card UI on hold; empty state when no history
- [ ] Lock: clock/date/notifications/passcode; flashlight/camera shortcuts
- [ ] Settings: all 28 root rows in exact order; General 21 rows; search pill; account card; value text on every nav row

No files changed — this is a read-only audit report.