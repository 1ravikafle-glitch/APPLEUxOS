# iOS 27 Layout Gap Analysis — AppleUX

**Date:** 2026-10-17  
**Source:** Real iOS 27 IPSW analysis + AppleUX source code review  
**Verdict:** Core structure solid, but several layout details don't match iOS 27

---

## 1. What's Correctly Implemented

| Component | Status | Notes |
|-----------|--------|-------|
| Home screen grid (4×6) | CORRECT | 4 columns, 6 rows, 60dp icons |
| Dock (4 slots, glass) | CORRECT | Liquid Glass bar, 60dp icons |
| App Library (swipe left) | CORRECT | 2-column folders, search pill |
| Spotlight search | CORRECT | Swipe down from center |
| Gesture system | CORRECT | Up=home, down=panels, left=library |
| Control Center (basic) | CORRECT | Glass sheet, tiles, sliders |
| Notification Shade | CORRECT | Glass, cards, clear all |
| Lock Screen | CORRECT | Date, clock, notifications, shortcuts |
| Status Bar | CORRECT | Time + icons over wallpaper |
| Squircle icons | CORRECT | 22.37% corner radius |
| Liquid Glass | CORRECT | 70% fill, rim, blur |
| Dynamic Island | CORRECT | Battery, call, timer, music slots |
| Performance controller | CORRECT | Q100→Q70 adaptive |
| Fonts (SF Symbols) | CORRECT | 6 fonts integrated |

---

## 2. What's Missing or Wrong (Needs Fix)

### Gap 1: Control Center Layout — Doesn't Match iOS 27

**iOS 27 layout (from IPSW):**
```
┌─────────────────────────────────┐
│  Status Bar / Island            │
├─────────────────────────────────┤
│  [+ Add]              [⏻ Power] │
├─────────────────────────────────┤
│  ┌──────────┐  ┌──────────────┐ │
│  │Wi-Fi  BT │  │  Media Card  │ │
│  │Cell Airpl│  │  (Now Play)  │ │
│  └──────────┘  └──────────────┘ │
├─────────────────────────────────┤
│  ┌────────┐  ┌────┐ ┌────┐     │
│  │ Focus  │  │ B  │ │ V  │     │
│  │  pill  │  │ r  │ │ o  │     │
│  └────────┘  │ i  │ │ l  │     │
│              │ g  │ │ u  │     │
│  ┌────────┐  │ h  │ │ m  │     │
│  │ Toggle │  │ t  │ │ e  │     │
│  │ Grid   │  │ n  │ │    │     │
│  │ (4col) │  │ e  │ │    │     │
│  │        │  │ s  │ │    │     │
│  │        │  │ s  │ │    │     │
│  └────────┘  └────┘ └────┘     │
├─────────────────────────────────┤
│  ● ● ○ ○  (page dots, right)    │
├─────────────────────────────────┤
│  ─── (home indicator)           │
└─────────────────────────────────┘
```

**Current AppleUX issues:**
- Brightness is a **tap-cycle** (6 steps) — iOS 27 has a **vertical slider**
- Volume is a **tap-cycle** — iOS 27 has a **vertical slider**
- Focus is a **text pill** — iOS 27 has a **circular tile with icon**
- Toggle grid is **4-column** — iOS 27 is **2-column with circular tiles**
- Page dots are on the **right edge** — should be **right edge vertical**
- Missing the **media card** in the top row

### Gap 2: CC Module Count — Only 8 tiles vs iOS 27's 40+

**Current:** 8 tiles (Wi-Fi, BT, Airplane, Flashlight, DND, Brightness, Rotation, Mute)  
**iOS 27:** 40+ modules (see cc_modules.xml)

**Missing tiles:** Screen Recording, Magnifier, QR Scanner, NFC Reader, Calculator, Timer, Alarm, Stopwatch, Voice Memos, Wallet, Notes, Home controls, Accessibility shortcuts, Text Size, etc.

### Gap 3: Lock Screen Layout — Clock Position

**iOS 27 (from IPSW):**
- Clock center at **33%** of screen height
- Clock width: **83%** of screen width
- Clock weight: **Light** (not regular)
- Date above clock: **17pt**, secondary color
- Notifications below clock: **glass cards, 12dp radius**
- Shortcuts: **two circular buttons** bottom corners

**Current AppleUX issues:**
- Clock is centered vertically (50%) — should be at **33%**
- Date format includes comma ("Wed, Apr 1") — iOS 27 has **no comma** ("Wed Apr 1")
- Notification cards use **custom drawing** — should use **glass card component**

### Gap 4: Missing "Today View" (Swipe Right from Home)

**iOS 27:** Swiping right from the first home page shows the **Today View** (widgets: Calendar, Weather, News, Maps, Tips, Batteries, Stocks, Screen Time)

**Current AppleUX:** Swiping right goes to **previous page** only — no Today View

### Gap 5: Missing "Search or Ask" (Swipe Down from Top-Center)

**iOS 27:** Swiping down from top-center shows **Search or Ask** (Apple Intelligence panel)

**Current AppleUX:** Swiping down from center shows **Spotlight** (basic search) — missing the AI panel

### Gap 6: CC Page Dots Position

**iOS 27:** Page dots are **vertical on the right edge**, centered vertically

**Current AppleUX:** Page dots are horizontal at the bottom — should be **vertical right edge**

### Gap 7: Notification Shade Layout

**iOS 27 (from IPSW):**
- Header: **52dp**, app name + icon
- Grouped notifications: **stacked under app label**
- Media card: **large, with controls**
- Clear All: **bottom center, blue text**
- Edit button: **bottom right**

**Current AppleUX issues:**
- Missing the **media player card** with controls
- Missing the **Edit** button in footer
- Grouping is correct but missing the **stacked card** visual

### Gap 8: Home Screen Search Pill

**iOS 27:** Search pill is **centered, floating above dock**, says "Search" with magnifying glass

**Current AppleUX:** Search pill says "Q Search" — should say **"Search"** with proper iOS styling

### Gap 9: Status Bar Layout

**iOS 27 (notch devices):**
- Time: **centered** (on notch/Dynamic Island devices)
- Left: **nothing** (or carrier on older devices)
- Right: **signal, Wi-Fi, battery**

**Current AppleUX:** Time on **left** — should be **centered** for notch devices

### Gap 10: Missing iOS 27 Animation Curves

**iOS 27 (from IPSW):**
- Spring damping: **0.8**
- Spring frequency: **12Hz**
- Press scale: **0.94** (not 0.88)
- Press spring: damping **0.6**, stiffness **500**
- Icon jiggle: **±2° rotation**, spring physics
- Page slide: **0.4s**, easeInOutCubic

**Current AppleUX issues:**
- Press scale is **0.88** — should be **0.94**
- Missing icon **jiggle animation** on edit mode
- Missing **page slide animation** with iOS curve

---

## 3. Priority Fix List

| Priority | Gap | Effort | Impact |
|----------|-----|--------|--------|
| P0 | CC vertical sliders (brightness + volume) | Medium | High |
| P0 | CC 2-column circular tile grid | Medium | High |
| P0 | CC media card (Now Playing) | Low | High |
| P0 | Lock screen clock at 33% | Low | High |
| P0 | Status bar time centered | Low | High |
| P1 | Today View (widgets) | Medium | High |
| P1 | CC 40+ tiles | Medium | High |
| P1 | CC page dots vertical right | Low | Medium |
| P1 | Notification media player card | Medium | Medium |
| P1 | Icon jiggle animation | Low | Medium |
| P2 | Search pill styling | Low | Low |
| P2 | CC focus tile with icon | Low | Low |
| P2 | Date format (remove comma) | Low | Low |
| P2 | Press scale 0.94 | Low | Low |
| P2 | Page slide animation curve | Low | Low |

---

## 4. Data Availability

| Data | Available | Source |
|------|-----------|--------|
| CC module list | YES | control-center/*_ios27_Info.plist |
| CC icons | YES | cc-images_*/ |
| CC layout | YES | IPSW analysis |
| Lock screen layout | YES | LockScreenTokens.kt |
| Widget specs | YES | DefaultIconState.plist |
| Animation curves | YES | MotionTokens.kt |
| Color tokens | YES | IPSW plists |
| Spacing tokens | YES | IPSW plists |
| App icons | YES | app-assets_*/ |
| System symbols | YES | framework-assets_*/ |

**All data is available. No blockers.**

---

*Generated from real iOS 27 IPSW analysis + AppleUX source code review.*
