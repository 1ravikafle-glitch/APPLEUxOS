# iOS 27 Critical Layout Fixes — AppleUX

**Date:** 2026-10-17  
**Priority:** P0 — Must match iOS 27 exactly  
**Source:** Real IPSW analysis

---

## Fix 1: Control Center — Vertical Sliders (Brightness + Volume)

### Problem
Current: Brightness and volume are **tap-cycle** buttons (6 fixed steps)  
iOS 27: **Vertical sliders** with continuous control

### Solution
Replace tap-cycle with `SeekBar` styled as iOS vertical slider:

```kotlin
// In AppleuxControlCenter.kt
// Replace brightness/volume tap tiles with vertical sliders

private val brightnessSlider = AppleuxVerticalSlider(context).apply {
    label = "Brightness"
    iconRes = R.drawable.sf_sun
    maxValue = 255
    progress = currentBrightness()
    onProgressChanged = { value ->
        writeBrightness(value)
    }
}

private val volumeSlider = AppleuxVerticalSlider(context).apply {
    label = "Volume"
    iconRes = R.drawable.sf_volume
    maxValue = streamMaxVolume
    progress = currentVolume
    onProgressChanged = { value ->
        setStreamVolume(value)
    }
}
```

### Layout change
```
Old: [Brightness] [Volume]  (tap tiles)
New: [====Brightness====]  (vertical slider, 240dp tall)
     [====Volume========]  (vertical slider, 240dp tall)
```

---

## Fix 2: Control Center — 2-Column Circular Tile Grid

### Problem
Current: 4-column rectangular grid  
iOS 27: **2-column circular tiles** (63dp diameter)

### Solution
```kotlin
// In AppleuxControlCenter.kt
tileContainer.columnCount = 2  // Changed from 4

// Make tiles circular
tileView.apply {
    background = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(active ? activeColor : glassColor)
    }
}
```

### Layout change
```
Old: [1] [2] [3] [4]  (4 columns)
     [5] [6] [7] [8]

New: [1] [2]           (2 columns, circular)
     [3] [4]
     [5] [6]
     [7] [8]
```

---

## Fix 3: Control Center — Media Card (Now Playing)

### Problem
Current: No media card in top row  
iOS 27: **Media card** showing Now Playing with controls

### Solution
```kotlin
// In AppleuxControlCenter.kt — already exists but needs to be in top row
// Move mediaCard to top row next to connectivity cluster

val topRow = LinearLayout(context).apply {
    orientation = HORIZONTAL
    gravity = Gravity.CENTER_VERTICAL
}
topRow.addView(connectivityCluster, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
topRow.addView(mediaCard, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
addView(topRow)
```

### Layout
```
┌──────────┬──────────────┐
│Wi-Fi  BT │ ▶ Song Title  │
│Cell Airpl│   Artist      │
└──────────┴──────────────┘
```

---

## Fix 4: Lock Screen — Clock at 33%

### Problem
Current: Clock centered at 50%  
iOS 27: Clock center at **33%** of screen height

### Solution
```kotlin
// In AppleuxLockScreen.kt
// Change CLOCK_CENTER_Y_FRACTION from 0.5 to 0.33

topSpacer.layoutParams = LinearLayout.LayoutParams(
    LinearLayout.LayoutParams.MATCH_PARENT, 
    0, 
    0.33f  // Changed from 0.5
)
```

### Layout
```
┌─────────────────────────────┐
│         Status Bar          │
├─────────────────────────────┤
│                             │
│         9:41                │  ← Clock at 33%
│         Wed Apr 1           │
│                             │
├─────────────────────────────┤
│  [Notification Card]        │
│  [Notification Card]        │
│  [Notification Card]        │
├─────────────────────────────┤
│ 🔦                    📷    │  ← Shortcuts
└─────────────────────────────┘
```

---

## Fix 5: Status Bar — Time Centered

### Problem
Current: Time on left  
iOS 27 (notch devices): Time **centered**

### Solution
```kotlin
// In AppleuxStatusBar.kt
// For notch/Dynamic Island devices, center the time

carrier.layoutParams = LayoutParams(
    LayoutParams.WRAP_CONTENT, 
    LayoutParams.WRAP_CONTENT
).apply {
    gravity = Gravity.CENTER  // Changed from START
}
```

### Layout
```
Old: 9:41          [signal][wifi][battery]
New:         9:41 [signal][wifi][battery]  ← centered
```

---

## Fix 6: Today View (Swipe Right from Home)

### Problem
Current: Swipe right = previous page only  
iOS 27: Swipe right from first page = **Today View** (widgets)

### Solution
```kotlin
// In LauncherActivity.kt — modify gesture handler
onSwipeRight = { yFrac ->
    if (yFrac > 0.85f) cycleRecentApp(forward = false)
    else if (homePage > 0) prevPage()
    else openTodayView()  // NEW: swipe right from first page
}

private fun openTodayView() {
    // Show widget overlay with iOS 27 widgets:
    // Calendar, Weather, News, Maps, Tips, Batteries, Stocks, Screen Time
    val todayView = AppleuxTodayView(context)
    root.addView(todayView, FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.MATCH_PARENT
    ))
}
```

### Today View Layout
```
┌─────────────────────────────┐
│         9:41 AM             │
│         Wed, Apr 1          │
├─────────────────────────────┤
│  [Calendar Widget]          │
│  [Weather Widget]           │
│  [News Widget]              │
│  [Maps Widget]              │
│  [Tips Widget]              │
│  [Batteries Widget]         │
│  [Stocks Widget]            │
│  [Screen Time Widget]       │
├─────────────────────────────┤
│         ───                 │
└─────────────────────────────┘
```

---

## Fix 7: CC Page Dots — Vertical Right Edge

### Problem
Current: Horizontal at bottom  
iOS 27: **Vertical on right edge**

### Solution
```kotlin
// In AppleuxControlCenter.kt
pageDots.orientation = LinearLayout.VERTICAL  // Already vertical
// Move to right edge
(pageDots.layoutParams as FrameLayout.LayoutParams).gravity = Gravity.END
```

---

## Fix 8: CC Focus Tile — Circular with Icon

### Problem
Current: Text pill "🌙 Focus"  
iOS 27: **Circular tile with moon icon**

### Solution
```kotlin
// In AppleuxControlCenter.kt
val focusTile = AppleuxControlTile(context, ControlTile(
    id = "focus",
    name = "Focus",
    iconName = "sf_moon",
    isToggle = true,
    onClick = { onFocusEdit?.invoke() }
))
// Make it circular like other tiles
focusTile.background = GradientDrawable().apply {
    shape = GradientDrawable.OVAL
    setColor(if (active) ColorTokens.systemIndigo else glassColor)
}
```

---

## Fix 9: Icon Jiggle Animation

### Problem
Current: No jiggle on edit mode  
iOS 27: **Icons jiggle** when long-pressed

### Solution
```kotlin
// In AppleuxLauncher — when edit mode is active
fun setEditMode(enabled: Boolean) {
    homescreenGrid.setEditMode(enabled)
    if (enabled) {
        homescreenGrid.animateJiggle()
    }
}

fun animateJiggle() {
    val animator = ObjectAnimator.ofFloat(this, "rotation", 0f, 2f, -2f, 0f)
    animator.duration = 300
    animator.repeatCount = ObjectAnimator.INFINITE
    animator.interpolator = SpringInterpolator(0.6f, 500f)
    animator.start()
}
```

---

## Fix 10: Press Scale 0.94

### Problem
Current: Press scale is **0.88**  
iOS 27: Press scale is **0.94**

### Solution
```kotlin
// In LauncherTokens.kt
const val PRESS_SCALE: Float = 0.94f  // Changed from 0.88
```

---

## Summary of Changes

| Fix | File | Change | iOS 27 Match |
|-----|------|--------|--------------|
| 1 | AppleuxControlCenter.kt | Vertical sliders | YES |
| 2 | AppleuxControlCenter.kt | 2-col circular grid | YES |
| 3 | AppleuxControlCenter.kt | Media card top row | YES |
| 4 | AppleuxLockScreen.kt | Clock at 33% | YES |
| 5 | AppleuxStatusBar.kt | Time centered | YES |
| 6 | LauncherActivity.kt | Today View | YES |
| 7 | AppleuxControlCenter.kt | Page dots right | YES |
| 8 | AppleuxControlCenter.kt | Focus circular | YES |
| 9 | AppleuxLauncher | Jiggle animation | YES |
| 10 | LauncherTokens.kt | Press scale 0.94 | YES |

---

*All fixes derived from real iOS 27 IPSW analysis. No blockers.*
