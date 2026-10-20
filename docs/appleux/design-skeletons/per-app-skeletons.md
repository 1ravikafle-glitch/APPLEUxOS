# Per-App iOS 27 Skeletons

**Directive:** A 02:07 — "ALL 10 APPS must match their iOS 27 EQUIVALENTS"  
**Rule:** Each app matches ITS iOS counterpart (Phone↔iOS Phone, Messages↔iOS Messages, etc.)  
**Owner:** C designs (this file), B verdicts (after A build), A builds

## Common Chrome (all 10 apps)

Every app shares:
- **Status bar:** transparent, white content over glass (AppleuxStatusBar)
- **Window:** translucent, Liquid Glass Regular material
- **Navigation:** iOS 27 push/pop with large-title collapse
- **Typography:** `TypographyTokens` (SF Pro, all weights)
- **Colors:** `ColorTokens` semantic roles (light/dark adaptive)
- **Corner radius:** `LiquidGlassTokens.REGULAR_CORNER_RADIUS_DP = 34pt` for cards/sheets
- **Icons:** SF Symbols 27 primary (A16), vendored vectors fallback

## 1. Phone (iOS Phone)

**Tab bar:** Favorites, Recents, Contacts, Keypad, Voicemail  
**Keypad:** 3×4 grid, circular buttons (70dp), digit + letters below  
**Call screen:** full-screen glass, contact name/photo, mute/keypad/speaker/add-call/hold buttons in 3×2 grid  
**Recents:** list with red "Info" button, call type icons  
**Contacts:** alphabetical list with section headers, search pill  

## 2. Messages (iOS Messages)

**List:** conversation previews (avatar, name, last message, timestamp, unread dot)  
**Thread:** blue/gray bubbles, timestamp separators, input bar with camera/mic  
**Input:** pill-shaped text field + iMessage blue send button  
**Navigation:** back chevron + conversation count in large title  

## 3. Contacts (iOS Contacts)

**List:** alphabetical sections (A-Z + #), letter scrubber on right edge  
**Detail:** large photo, name, action buttons (message/call/facetime/info)  
**Groups:** Favorites, All iCloud, recent adds  
**Search:** pill at top, filters live  

## 4. Photos (iOS Photos)

**Grid:** square thumbnails, 3 columns, pinch to zoom  
**Years/Months/Days/All Photos:** hierarchical zoom  
**Albums:** card grid with cover photo + count  
**For You:** a vertical FEED of section cards (C, 2026-10-21 — the contract
below)  
**Tab bar:** Library, For You, Albums, Search  

### The Photos contract (C, 2026-10-21)

**PROVENANCE.** Apple publishes **no** Photos structure — not in the HIG (there
is no photos-app page), not in the kit. The tab bar and the sections are
Apple's shipping behaviour, observed in the product, so this section is a
**reconstruction**, and every number in it is ours. B's 23:40 read of our
build (Albums, Collections, Day, Deleted, Hidden as tabs) is the stock-AOSP
structure and it goes: by the ALL-IS-OURS law the surface is ours, and the
stock strip is a foreign shape.

**The four tabs, and what each one carries:**
- **Library** — the Years/Months/Days/All Photos hierarchy, the
  **Collections tiles** (Memories, People, Places, Trips, Media types,
  Featured) and the album list;
- **For You** — the same sections, **expanded into a vertical feed**;
- **Albums** — the album list (cards with cover + count), **including Hidden
  and Recently Deleted**;
- **Search** — the search field with live filters. **The 4th item is not a
  labelled tab:** iOS 27's tab bar keeps search as a **trailing standalone
  magnifier capsule** separate from the main group (the kit's `Type =
  Search Role`: the capsule SPLITS into the main group + a trailing capsule
  whose symbol is the magnifier, `U+1002AB`, and that button carries **no
  label**). So our strip is a main group capsule (**Library · For You ·
  Albums**) plus a **separate magnifier capsule** for Search — not four
  equal labelled tabs. (`Separate Search: True/False` is an **iOS 26**
  structure: the iOS 27 kit does not have it, and our tree models nothing by
  that name ✓.)

**Hidden and Recently Deleted are ALBUMS, not tabs.** iOS 18 lists them in the
album list, so they belong to the **Albums** tab's list and to the Library's
album section. They must be reachable **before** the AOSP tabs are removed —
B's ordering ✓ (Library and Albums gain them first, then the strip changes).

**For You is ONE content model in TWO presentations, never a tab that points
at nothing:**
- the sections are a single source — **Recents, Memories, People, Places,
  Trips, Media types, Featured**;
- the Library shows them as iOS's **Collections tile row**; **For You** shows
  the same sections expanded, as a **vertical feed of section cards** — that
  is exactly how iOS uses the same collection kinds in both places, so this
  is one model in two presentations, not a duplicated surface;
- **For You always leads with Recents** (the last days and weeks, computed
  from the media store), so the tab is never a placeholder on a device that
  has photos;
- a section with **no content is hidden** (iOS's behaviour), not shown as an
  empty card;
- if the surface has **nothing at all** (no photos on the device), it shows
  the HIG's empty-state idiom — HIG → Writing, *"Provide clear next steps on
  any blank screens"*: a title, one useful line and an action — never a
  blank page.

**DEVIATION (recorded in `DESIGN_SYSTEM.md` §14):** the earlier spec line
"**For You: memory cards with parallax**" describes a real memory card, which
needs a memories engine we do not have. Until that engine exists the feed is
a plain section feed — **cards, no parallax**. The parallax belongs to the
memory card, not to the tab.

**The build order (B owns it, C's contract):** (1) the Library's album list
gains Hidden + Recently Deleted, and the Collections tiles + the
Years/Months/Days hierarchy are in place; (2) **For You** ships with Recents
leading; (3) the strip becomes the four tabs and the AOSP rows go; (4) B reads
the result on device. The strip change alone is not enough — a For You tab
that points at nothing is the failure mode.

## 5. Files (iOS Files)

**Browse:** card list (iDrive, On My iPhone, Recently Deleted, third-party)  
**Locations:** sidebar with shortcuts  
**Grid/List toggle:** top-right  
**File actions:** long-press context menu (share, rename, move, delete)  

## 6. Camera (iOS Camera)

**Viewfinder:** full-screen, no chrome  
**Mode slider:** Photo, Video, Portrait, Pano (scrollable)  
**Shutter:** large circle, red dot when recording  
**Flip:** front/rear toggle  
**Settings:** gear icon top-left  

## 7. Clock (iOS Clock)

**World Clock:** list of cities with analog/digital toggle  
**Alarms:** list with toggle switches, + button  
**Stopwatch:** large digits, lap list  
**Timers:** preset circles + custom, progress ring  
**Tab bar:** World Clock, Alarms, Stopwatch, Timers  

## 8. Calendar (iOS Calendar)

**Day view:** hour grid, event blocks colored by calendar  
**Week view:** 7-day columns  
**Month view:** grid with dot indicators  
**Event detail:** title, time, location, notes, invitees  
**+ button:** bottom-right, floating  

## 9. Safari (iOS Browser)

**Address bar:** bottom pill, intelligent tracking prevention  
**Tab overview:** grid of tab cards  
**Favorites:** icon grid on new tab  
**Reader mode:** Aa button in address bar  

## 10. Video (iOS Videos)

**Library:** thumbnail grid  
**Player:** full-screen, scrubber, AirPlay  
**Details:** metadata card below player  

## Implementation Priority

1. **Phone** — core function, highest visibility
2. **Messages** — daily use
3. **Contacts** — pairs with Phone
4. **Photos** — high visibility
5. **Clock** — utility
6. **Calendar** — utility
7. **Files** — utility
8. **Safari** — browser
9. **Camera** — hardware-dependent
10. **Video** — media

## Cross-App Patterns

- **List rows:** 54pt height, `TypographyTokens.body` title, `TypographyTokens.subheadline` subtitle
- **Section headers:** `TypographyTokens.footnote` semibold, gray, uppercase
- **Search:** pill-shaped, `LiquidGlassTokens.REGULAR` fill, placeholder gray
- **Tab bars:** 49pt height, `LiquidGlassTokens.REGULAR` fill, active blue `ColorTokens.systemBlue`
- **Nav bars:** transparent, large title collapses to inline on scroll
- **Context menus:** `LiquidGlass` material, rounded corners, SF Symbol icons
