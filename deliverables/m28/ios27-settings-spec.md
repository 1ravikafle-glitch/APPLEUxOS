# iOS 27 Settings — Exact Implementation Spec for AppleUX

**Source:** Extracted from iOS 27 IPSW (`/home/elfakiris/ios-ui-analysis/`),
ground-truth photos (`design-reference/iphone11-ios27/drive/`), and
`design/spec/ios27-baseline.md`.

**Purpose:** This file is the single source of truth for implementing AppleUX
Settings to match iOS 27 exactly. Another session can follow this spec
without re-researching.

**Device frame:** 392.73 × 875.64 dp (440 dpi, POCO M5 measured).

---

## Layout Tokens (from ground truth + baseline)

```
Grouped card radius:        20 dp
Grouped card background:    #F2F2F7 (light) / #1C1C1E (dark)
Side margin:                16 dp
Inter-group gap:           24 dp
Row height:                 44 dp
Row icon size:              29 dp
Row icon corner radius:     6 dp (squircle-ish)
Row title font:             SF Text 17sp, regular
Row value font:             SF Text 17sp, regular, #8E8E93 (gray)
Chevron:                    #C7C7CC, 13pt, "›"
Toggle:                     iOS green #34C759 when on
Search pill:                floating, bottom, 356×36 dp, #323232 bg at 85% opacity
Hero card (sub-screen):     20dp radius, icon 50dp, title 22sp bold, subtitle 13sp gray
Section header:             SF Text 13sp, #8E8E93, uppercase, 16dp margin
Account card:               full-width, avatar 56dp circle, name 17sp bold, subtitle 13sp gray
```

---

## Root Screen — Exact Row Order

### Group 0: Account Card

| # | Row | Type | Icon | Value | Target |
|---|---|---|---|---|---|
| 0 | Apple Account, iCloud, and more | ACCOUNT_CARD | avatar (generic person) | "Ravi Kafle" (or system account) | → AccountSettingsActivity |

### Group 1: Connectivity

| # | Row | Type | Icon | Value | Target |
|---|---|---|---|---|---|
| 1 | Airplane Mode | TOGGLE | system_airplane (orange) | on/off | Internal: toggle |
| 2 | Wi-Fi | NAVIGATION | system_wifi (blue) | SSID name or "Off" | → WifiSettingsActivity |
| 3 | Bluetooth | NAVIGATION | system_bluetooth (blue) | "Not Connected" / device | → BluetoothSettingsActivity |
| 4 | Cellular | NAVIGATION | system_cellular (green) | carrier name | → CellularSettingsActivity |
| 5 | Hotspot | NAVIGATION | system_hotspot (green) | "Off" | → Settings.TETHER_SETTINGS intent |
| 6 | Battery | NAVIGATION | system_battery (green) | "81%" | → BatterySettingsActivity |

### Group 2: Apps & Features

| # | Row | Type | Icon | Value | Target |
|---|---|---|---|---|---|
| 7 | Appearance | NAVIGATION | system_appearance (dark) | "Light" / "Dark" / "Automatic" | → AppearanceSettingsActivity |
| 8 | Camera | NAVIGATION | system_camera (gray) | — | → CameraSettingsActivity |
| 9 | Control Center | NAVIGATION | system_controlcenter (gray) | — | → ControlCenterSettingsActivity |
| 10 | Display | NAVIGATION | system_display (blue) | — | → DisplaySettingsActivity |
| 11 | Home | NAVIGATION | system_home (orange) | — | → HomeSettingsActivity |
| 12 | Search | NAVIGATION | system_search (gray) | — | → SearchSettingsActivity |
| 13 | Siri | NAVIGATION | system_siri (purple) | — | → SiriSettingsActivity |
| 14 | StandBy | NAVIGATION | system_standby (dark) | — | → StandBySettingsActivity |
| 15 | Wallpaper | NAVIGATION | system_wallpaper (blue) | — | → WallpaperSettingsActivity |

### Group 3: Notifications

| # | Row | Type | Icon | Value | Target |
|---|---|---|---|---|---|
| 16 | Notifications | NAVIGATION | system_notifications (red) | — | → NotificationsSettingsActivity |
| 17 | Sounds | NAVIGATION | system_sounds (pink) | — | → SoundsSettingsActivity |
| 18 | Focus | NAVIGATION | system_focus (indigo) | mode name | → FocusSettingsActivity |
| 19 | Screen Time | NAVIGATION | system_screentime (indigo) | "2h 15m" | → ScreenTimeSettingsActivity |

### Group 4: Security

| # | Row | Type | Icon | Value | Target |
|---|---|---|---|---|---|
| 20 | Face ID & Passcode | NAVIGATION | system_faceid (green) | — | → FaceIdSettingsActivity |
| 21 | Emergency SOS | NAVIGATION | system_sos (red) | — | → SOSSettingsActivity |
| 22 | Privacy & Security | NAVIGATION | system_privacy (blue) | — | → PrivacySettingsActivity |

### Group 5: Apple Services

| # | Row | Type | Icon | Value | Target |
|---|---|---|---|---|---|
| 23 | Game Center | NAVIGATION | system_gamecenter (blue) | — | → GameCenterSettingsActivity |
| 24 | iCloud | NAVIGATION | system_icloud (blue) | storage used | → ICloudSettingsActivity |
| 25 | Wallet | NAVIGATION | system_wallet (black) | — | → WalletSettingsActivity |
| 26 | Apps | NAVIGATION | system_apps (blue) | — | → AppsSettingsActivity |

### Bottom: Search Pill

Floating above dock. Tap → SearchSettingsActivity (full-screen search).

---

## Sub-Branch: General

**Hero card:** gear icon + "General" + "About, Software Update, and more"

| # | Row | Type | Icon | Value | Target |
|---|---|---|---|---|---|
| 1 | About | NAVIGATION | system_about (gray) | device name | → AboutActivity |
| 2 | Software Update | NAVIGATION | system_softwareupdate (gray) | "iOS 27.2" | → SoftwareUpdateActivity |
| 3 | iPhone Storage | NAVIGATION | system_storage (gray) | "64 GB (42 GB available)" | → StorageSettingsActivity |
| 4 | AppleCare | NAVIGATION | system_applecare (gray) | coverage status | → AppleCareActivity |
| 5 | AirDrop | NAVIGATION | system_airdrop (blue) | "Everyone for 10 Minutes" / "Receiving Off" | → AirDropActivity |
| 6 | AirPlay | NAVIGATION | system_airplay (blue) | — | → AirPlayActivity |
| 7 | Picture in Picture | TOGGLE | system_pip (green) | on/off | Internal |
| 8 | Screen Capture | TOGGLE | system_screencapture (gray) | on/off | Internal |
| 9 | CarPlay | NAVIGATION | system_carplay (gray) | connected cars | → CarPlayActivity |
| 10 | AutoFill | NAVIGATION | system_autofill (gray) | — | → AutoFillActivity |
| 11 | Background App Refresh | NAVIGATION | system_backgroundrefresh (blue) | "Wi-Fi" / "Off" | → BackgroundRefreshActivity |
| 12 | Date & Time | NAVIGATION | system_datetime (black) | — | → DateTimeActivity |
| 13 | Dictionary | NAVIGATION | system_dictionary (blue) | — | → DictionaryActivity |
| 14 | Fonts | NAVIGATION | system_fonts (blue) | — | → FontsActivity |
| 15 | Keyboard | NAVIGATION | system_keyboard (gray) | — | → KeyboardActivity |
| 16 | Language | NAVIGATION | system_language (blue) | "English" | → LanguageActivity |
| 17 | TV Provider | NAVIGATION | system_tvprovider (purple) | — | → TVProviderActivity |
| 18 | VPN & Device Management | NAVIGATION | system_vpn (blue) | "Not Connected" / config | → VPNActivity |
| 19 | Legal & Regulatory | NAVIGATION | system_legal (gray) | — | → LegalActivity |
| 20 | Transfer or Reset iPhone | NAVIGATION | system_reset (gray) | — | → ResetActivity |
| 21 | Shut Down | DESTRUCTION | — (no icon, blue text) | — | → PowerSliderActivity |

---

## Sub-Branch: Wi-Fi

**Hero card:** Wi-Fi icon + "Wi-Fi" + subtitle

| # | Row | Type | Value | Target |
|---|---|---|---|---|
| 1 | Wi-Fi | TOGGLE | on/off | Internal |
| 2 | Network list | NAVIGATION_SECTION | SSID names, signal strength, lock icon | Tap → connect |
| 3 | Private Wi-Fi Address | TOGGLE | on/off | Internal |
| 4 | Auto-Join Hotspot | NAVIGATION | "Automatic" / "Off" | → |

---

## Sub-Branch: Bluetooth

**Hero card:** Bluetooth icon + "Bluetooth" + subtitle

| # | Row | Type | Value |
|---|---|---|---|
| 1 | Bluetooth | TOGGLE | on/off |
| 2 | My Devices | NAVIGATION_SECTION | paired device names |
| 3 | Other Devices | NAVIGATION_SECTION | discovered devices |

---

## Sub-Branch: Cellular

**Hero card:** cellular icon + "Cellular" + carrier

| # | Row | Type | Value |
|---|---|---|---|
| 1 | Cellular Data | TOGGLE | on/off |
| 2 | Data Mode | NAVIGATION | "Allow More Data on 5G" / "Standard" / "Low Data Mode" |
| 3 | Data Roaming | TOGGLE | on/off |
| 4 | SIMs | NAVIGATION_SECTION | carrier name, plan |
| 5 | SIM PIN | TOGGLE | on/off |

---

## Sub-Branch: Battery

**Hero card:** battery icon + "Battery" + percentage

| # | Row | Type | Value |
|---|---|---|---|
| 1 | Battery Level | TOGGLE | on/off (show % in status bar) |
| 2 | Low Power Mode | TOGGLE | on/off |
| 3 | Battery Health | NAVIGATION | "Maximum Capacity 100%" |
| 4 | Charging | TOGGLE | "Optimized Battery Charging" |
| 5 | Activity graph | CHART | last 24h usage |

---

## Sub-Branch: Display

**Hero card:** display icon + "Display"

| # | Row | Type | Value |
|---|---|---|---|
| 1 | Appearance | NAVIGATION | "Light" / "Dark" / "Automatic" |
| 2 | Brightness | SEEK_BAR | 0–100% |
| 3 | Night Shift | NAVIGATION | schedule, color temp |
| 4 | True Tone | TOGGLE | on/off |
| 5 | Display Zoom | NAVIGATION | "Default" / "Larger Text" |
| 6 | Auto-Lock | NAVIGATION | "30 Seconds" / "1 Minute" / etc. |
| 7 | Raise to Wake | TOGGLE | on/off |
| 8 | Text Size | NAVIGATION | slider with preview |

---

## Sub-Branch: Appearance

**Hero card:** appearance icon + "Appearance"

| # | Row | Type | Value |
|---|---|---|---|
| 1 | Light | SELECTOR | radio button |
| 2 | Dark | SELECTOR | radio button |
| 3 | Automatic | SELECTOR | radio button + schedule row |

---

## Sub-Branch: Wallpaper

**Hero card:** wallpaper icon + "Wallpaper"

| # | Row | Type | Value |
|---|---|---|---|
| 1 | Current wallpaper | PREVIEW | thumbnail |
| 2 | Add New Wallpaper | NAVIGATION | → picker |
| 3 | Stills | NAVIGATION_SECTION | iOS wallpaper thumbnails |
| 4 | Photos | NAVIGATION_SECTION | photo picker |
| 5 | Color Shuffle | NAVIGATION | color picker |
| 6 | Depth Effect | TOGGLE | on/off |
| 7 | Perspective Zoom | TOGGLE | on/off |

---

## Sub-Branch: Sounds

**Hero card:** speaker icon + "Sounds"

| # | Row | Type | Value |
|---|---|---|---|
| 1 | Ringtones | NAVIGATION | current ringtone name |
| 2 | Text Tone | NAVIGATION | "Note" / etc. |
| 3 | New Mail | NAVIGATION | sound name |
| 4 | Sent Mail | NAVIGATION | "Swoosh" |
| 5 | Calendar Alerts | NAVIGATION | sound name |
| 6 | Reminder Alerts | NAVIGATION | sound name |
| 7 | AirDrop | NAVIGATION | sound name |
| 8 | Keyboard Clicks | TOGGLE | on/off |
| 9 | Lock Sound | TOGGLE | on/off |
| 10 | Haptic Feedback | NAVIGATION | "Default" / "Off" |

---

## Sub-Branch: Notifications

**Hero card:** bell icon + "Notifications"

| # | Row | Type | Value |
|---|---|---|---|
| 1 | Scheduled Summary | TOGGLE | on/off |
| 2 | Display As | NAVIGATION | "Count" / "Stack" / "List" |
| 3 | Show Previews | NAVIGATION | "Always" / "When Unlocked" / "Never" |
| 4 | Notification style | SECTION_HEADER | per-app list |
| 5+ | [App rows] | NAVIGATION | app name → per-app notification settings |

---

## Sub-Branch: Focus

**Hero card:** moon icon + "Focus"

| # | Row | Type | Value |
|---|---|---|---|
| 1 | Do Not Disturb | TOGGLE | on/off |
| 2 | Sleep | NAVIGATION | schedule, allowed apps |
| 3 | Work | NAVIGATION | schedule, allowed apps |
| 4 | Personal | NAVIGATION | schedule, allowed apps |
| 5 | Driving | NAVIGATION | auto-detect, allowed apps |
| 6 | Add Focus | NAVIGATION | custom focus |

---

## Sub-Branch: Screen Time

**Hero card:** hourglass icon + "Screen Time" + daily average

| # | Row | Type | Value |
|---|---|---|---|
| 1 | Screen Time | TOGGLE | on/off |
| 2 | Downtime | NAVIGATION | schedule |
| 3 | App Limits | NAVIGATION | add/edit limits |
| 4 | Communication Limits | NAVIGATION | who can contact |
| 5 | Always Allowed | NAVIGATION | app list |
| 6 | Content & Privacy Restrictions | TOGGLE | on/off |
| 7 | Activity chart | CHART | daily/weekly usage |

---

## Sub-Branch: Face ID & Passcode

**Hero card:** faceid icon + "Face ID & Passcode"

| # | Row | Type | Value |
|---|---|---|---|
| 1 | iPhone Unlock | TOGGLE | on/off |
| 2 | Apple Pay | TOGGLE | on/off |
| 3 | Password Autofill | TOGGLE | on/off |
| 4 | Today View and Search | TOGGLE | on/off |
| 5 | Passcode | NAVIGATION | change, 6-digit/4-digit/custom |
| 6 | Attention Aware Features | TOGGLE | on/off |
| 7 | Erase Data | TOGGLE | on/off (erase after 10 failed attempts) |

---

## Sub-Branch: Emergency SOS

**Hero card:** sos icon + "Emergency SOS"

| # | Row | Type | Value |
|---|---|---|---|
| 1 | Call with Side Button | TOGGLE | on/off |
| 2 | Call with 5 Presses | TOGGLE | on/off |
| 3 | Auto Call | TOGGLE | on/off |
| 4 | Emergency Contacts | NAVIGATION | contact list |
| 5 | Countdown Sound | TOGGLE | on/off |

---

## Sub-Branch: Privacy & Security

**Hero card:** hand icon + "Privacy & Security"

| # | Row | Type | Value |
|---|---|---|---|
| 1 | Location Services | NAVIGATION | per-app |
| 2 | Tracking | TOGGLE | "Allow Apps to Request to Track" |
| 3 | Analytics & Improvements | NAVIGATION | share iPhone analytics |
| 4 | Apple Advertising | TOGGLE | personalized ads |
| 5 | App Privacy Report | NAVIGATION | 7-day report |
| 6 | Safety Check | NAVIGATION | emergency reset |

---

## Sub-Branch: Camera

**Hero card:** camera icon + "Camera"

| # | Row | Type | Value |
|---|---|---|---|
| 1 | Formats | NAVIGATION | "High Efficiency" / "Most Compatible" |
| 2 | Apple ProRAW | NAVIGATION | resolution |
| 3 | Grid | TOGGLE | on/off |
| 4 | Mirror Front Camera | TOGGLE | on/off |
| 5 | View Outside the Frame | TOGGLE | on/off |
| 6 | Lens Correction | TOGGLE | on/off |
| 7 | Scene Detection | TOGGLE | on/off |
| 8 | Preserve Settings | NAVIGATION | camera mode, filters |

---

## Sub-Branch: Home

**Hero card:** house icon + "Home"

| # | Row | Type | Value |
|---|---|---|---|
| 1 | Home Hub | NAVIGATION | connected hub |
| 2 | Home Settings | NAVIGATION | rooms, accessories |
| 3 | Camera & Doorbell | NAVIGATION | activity zones |

---

## Sub-Branch: Siri

**Hero card:** siri icon + "Siri"

| # | Row | Type | Value |
|---|---|---|---|
| 1 | Ask Siri | TOGGLE | on/off |
| 2 | Listen for "Hey Siri" | TOGGLE | on/off |
| 3 | Press Side Button for Siri | TOGGLE | on/off |
| 4 | Allow Siri When Locked | TOGGLE | on/off |
| 5 | Siri Voice | NAVIGION | voice 1–5 |
| 6 | Siri Responses | NAVIGATION | silent/voice |
| 7 | Language | NAVIGATION | English, etc. |

---

## Sub-Branch: StandBy

**Hero card:** standby icon + "StandBy"

| # | Row | Type | Value |
|---|---|---|---|
| 1 | StandBy | TOGGLE | on/off |
| 2 | Night Mode | TOGGLE | on/off |
| 3 | Show Widgets | TOGGLE | on/off |
| 4 | Show Notifications | TOGGLE | on/off |
| 5 | Show Preview | TOGGLE | on/off |
| 6 | Always Show Time | TOGGLE | on/off |

---

## Sub-Branch: Control Center

**Hero card:** controlcenter icon + "Control Center"

| # | Row | Type | Value |
|---|---|---|---|
| 1 | Access Within Apps | TOGGLE | on/off |
| 2 | Access on Lock Screen | TOGGLE | on/off |
| 3 | Show in App Switcher | TOGGLE | on/off |
| 4 | Customize Controls | NAVIGATION | add/remove/reorder |
| 5 | Included Controls | SECTION_HEADER | list |
| 6+ | [Control rows] | NAVIGATION | tap → expanded sheet |

---

## Sub-Branch: Game Center

**Hero card:** gamecenter icon + "Game Center"

| # | Row | Type | Value |
|---|---|---|---|
| 1 | Game Center | TOGGLE | on/off |
| 2 | Nickname | NAVIGATION | player name |
| 3 | Profile | NAVIGATION | avatar, friend code |
| 4 | Friends | NAVIGATION | friend list |
| 5 | Friend Requests | NAVIGATION | pending |

---

## Sub-Branch: iCloud

**Hero card:** icloud icon + "iCloud" + storage bar

| # | Row | Type | Value |
|---|---|---|---|
| 1 | iCloud Storage | PROGRESS_BAR | "64 GB of 2 TB used" |
| 2 | iCloud Backup | TOGGLE | on/off |
| 3 | iCloud Drive | TOGGLE | on/off |
| 4 | iCloud Photos | TOGGLE | on/off |
| 5 | iCloud Keychain | TOGGLE | on/off |
| 6 | iCloud Mail | TOGGLE | on/off |
| 7 | Find My iPhone | TOGGLE | on/off |
| 8 | App list | SECTION_HEADER | per-app sync toggles |

---

## Sub-Branch: Wallet

**Hero card:** wallet icon + "Wallet"

| # | Row | Type | Value |
|---|---|---|---|
| 1 | Apple Pay | NAVIGATION | add card |
| 2 | Transaction Defaults | NAVIGATION | default card |
| 3 | Allow Payments on Mac | TOGGLE | on/off |
| 4 | Card list | NAVIGATION_SECTION | payment cards |
| 5 | Apple Card | NAVIGATION | balance, transactions |

---

## Sub-Branch: Apps

**Hero card:** apps icon + "Apps"

| # | Row | Type | Value |
|---|---|---|---|
| 1 | Sort Apps | NAVIGATION | "Name" / "Date Added" |
| 2 | Offload Unused Apps | TOGGLE | on/off |
| 3 | Automatic Downloads | NAVIGATION | app updates |
| 4 | Default Apps | NAVIGATION | browser, mail, maps |
| 5 | App list | SECTION_HEADER | installed apps |
| 6+ | [App rows] | NAVIGATION | tap → app info |

---

## Sub-Branch: Search

**Hero card:** search icon + "Search"

| # | Row | Type | Value |
|---|---|---|---|
| 1 | Search | TOGGLE | on/off |
| 2 | Spotlight Suggestions | TOGGLE | on/off |
| 3 | Siri Suggestions | TOGGLE | on/off |
| 4 | Search Engine | NAVIGATION | "Google" / "DuckDuckGo" / etc. |
| 5 | Search Domains | NAVIGATION | app, contact, web |

---

## Sub-Branch: About (under General)

**Hero card:** device icon + "About"

| # | Row | Type | Value |
|---|---|---|---|
| 1 | Name | VALUE | device name |
| 2 | iOS Version | VALUE | "27.2" |
| 3 | Model Name | VALUE | "iPhone 11" |
| 4 | Model Number | VALUE | "A2111" |
| 5 | Serial Number | VALUE | serial |
| 6 | Storage | VALUE | "64 GB" |
| 7 | Available | VALUE | "42 GB" |
| 8 | Carrier | VALUE | carrier name |
| 9 | IMEI | VALUE | IMEI number |

---

## Sub-Branch: Software Update (under General)

**Hero card:** gear icon + "Software Update"

| # | Row | Type | Value |
|---|---|---|---|
| 1 | iOS Version | VALUE | "iOS 27.2" |
| 2 | Version | VALUE | "24B5099f" |
| 3 | Automatic Updates | TOGGLE | on/off |
| 4 | Download iOS Updates | TOGGLE | on/off |
| 5 | Install Security Responses | TOGGLE | on/off |
| 6 | Beta Updates | NAVIGATION | enroll |

---

## Sub-Branch: Storage (under General)

**Hero card:** storage icon + "iPhone Storage" + bar

| # | Row | Type | Value |
|---|---|---|---|
| 1 | Storage bar | PROGRESS_BAR | "64 GB of 64 GB" |
| 2 | System | VALUE | "12 GB" |
| 3 | Apps | VALUE | "28 GB" |
| 4 | Photos | VALUE | "8 GB" |
| 5 | Media | VALUE | "6 GB" |
| 6 | Mail | VALUE | "2 GB" |
| 7 | Offload Unused Apps | TOGGLE | on/off |

---

## Icon Mapping (SF Symbols → AppleUX vendored vectors)

Our vendored vectors live in `extracted/` (102 `sf_*`/`il_ic_*` files). Use
those for all rows. If a symbol is missing, draw a simple SF-weight
equivalent — never use Material icons.

| iOS Icon Name | AppleUX Vector |
|---|---|
| system_airplane | sf_airplane |
| system_wifi | sf_wifi |
| system_bluetooth | sf_bluetooth |
| system_cellular | sf_cellularbars |
| system_battery | sf_battery_100 |
| system_camera | sf_camera |
| system_display | sf_display |
| system_home | sf_house |
| system_search | sf_magnifyingglass |
| system_siri | sf_waveform |
| system_standby | sf_moon_zzz |
| system_wallpaper | sf_photo |
| system_notifications | sf_bell |
| system_sounds | sf_speaker_wave_2 |
| system_focus | sf_moon |
| system_screentime | sf_hourglass |
| system_faceid | sf_faceid |
| system_sos | sf_cross |
| system_privacy | sf_hand_raised |
| system_gamecenter | sf_gamecontroller |
| system_icloud | sf_cloud |
| system_wallet | sf_creditcard |
| system_apps | sf_square_grid_3x3 |
| system_about | sf_info_circle |
| system_softwareupdate | sf_arrow_down_circle |
| system_storage | sf_internaldrive |
| system_applecare | sf_heart |
| system_airdrop | sf_airdrop |
| system_airplay | sf_airplayvideo |
| system_pip | sf_pip |
| system_screencapture | sf_viewfinder |
| system_carplay | sf_car |
| system_autofill | sf_key |
| system_backgroundrefresh | sf_arrow_triangle_2_circlepath |
| system_datetime | sf_calendar |
| system_dictionary | sf_book |
| system_fonts | sf_textformat |
| system_keyboard | sf_keyboard |
| system_language | sf_globe |
| system_tvprovider | sf_tv |
| system_vpn | sf_lock_shield |
| system_legal | sf_doc_text |
| system_reset | sf_arrow_triangle_2_circlepath |

---

## Implementation Notes

1. **Row types:** `NAVIGATION`, `TOGGLE`, `SEEK_BAR`, `VALUE`, `SELECTOR`,
   `DESTRUCTION`, `PROGRESS_BAR`, `CHART`, `SECTION_HEADER`, `PREVIEW`,
   `ACCOUNT_CARD`, `NAVIGATION_SECTION`.

2. **Nav targets:** Each row maps to either an internal `Activity` (our iOS
   replacement) or an `Intent` action (stock AOSP fallback). Use
   `NavTarget.Internal(activity)` or `NavTarget.Intent(action)`.

3. **Value text:** Gray (`#8E8E93`), right-aligned, 17sp. Always present on
   `NAVIGATION` rows — even "Off" or "Not Connected". Never omit.

4. **Grouped cards:** All rows in a group share one rounded background.
   20dp radius, `#F2F2F7` (light) / `#1C1C1E` (dark). 16dp side margin.
   24dp gap between groups.

5. **Sub-screen chrome:** Circular back chevron top-left, centered small title,
   hero card (50dp icon + 22sp bold title + 13sp gray subtitle), then groups.

6. **Search pill:** Floating above dock, `356×36 dp`, `#323232` at 85% opacity,
   magnifier + "Search" + mic. Tap → full-screen search activity.

7. **Scrolled root title:** When scrolled, show small centered "Settings"
   title at top. When at top, show large bold "Settings" title.

8. **Account card:** Full-width, 56dp circular avatar, 17sp bold name,
   13sp gray subtitle ("Apple Account, iCloud, and more"). Tap → account page.

9. **Shut Down row:** Blue text (`#007AFF`), no icon, centered in its own group.
   Tap → `AppleuxPowerSlider` activity (slide to power off).

10. **Stock AOSP handoffs:** For branches we haven't built yet, use
    `NavTarget.Intent(action)` with the appropriate AOSP settings action.
    This is intentional — iOS has no equivalent for some AOSP-only settings.

---

## File Targets (where to implement)

| File | What to change |
|---|---|
| `settings/.../ui/SettingsListActivity.kt` | Root catalog: add all 28 rows in exact order |
| `settings/.../ui/SettingsItem.kt` | Add new types: `ACCOUNT_CARD`, `VALUE`, `PROGRESS_BAR`, `CHART`, `SELECTOR`, `DESTRUCTION` |
| `settings/.../ui/SettingsDetailActivity.kt` | Sub-screen chrome: hero card, back chevron, title |
| `settings/.../SettingsCatalog.kt` | New file: define all sub-branch catalogs as static lists |
| `settings/.../SettingsAccountCardView.kt` | New file: account card component |
| `settings/.../SettingsSearchActivity.kt` | New file: full-screen search |
| `settings/.../SettingsPowerSliderActivity.kt` | New file: slide to power off |
| `settings/res/layout/settings_row.xml` | Add support for all new row types |
| `settings/res/layout/settings_root.xml` | Add search pill, scrolled title |
| `settings/res/layout/settings_subscreen.xml` | Hero card layout |

---

## Verification Checklist

- [ ] Root screen has all 28 rows in exact iOS 27 order
- [ ] All 5 groups have correct headers and spacing
- [ ] Account card renders with avatar, name, subtitle
- [ ] Search pill floats above dock
- [ ] Scrolled title appears on scroll
- [ ] Every nav row shows value text (never empty)
- [ ] General sub-branch has all 21 rows
- [ ] Shut Down row is blue, no icon, triggers power slider
- [ ] Sub-screens have hero card + back chevron
- [ ] All icons use vendored SF vectors (no Material icons)
- [ ] Grouped cards: 20dp radius, correct bg color, 16dp margin, 24dp gap
- [ ] Toggles use iOS green #34C759
- [ ] Chevron #C7C7CC on every nav row
