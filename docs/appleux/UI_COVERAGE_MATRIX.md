# AppleUXOS — UI COVERAGE MATRIX

> **Status: DRAFT v0 — PLANNED SURFACES ONLY. NOTHING HAS BEEN DISCOVERED YET.**
> No repository has been inspected. Source paths are candidates and marked `(verify)`. A row may move to DISCOVERED only after the path is confirmed in the real checkout. Do not mark any row TESTED unless the test was run and its outcome recorded.

Statuses: DISCOVERED, NOT_IMPLEMENTED, PLANNED, IN_PROGRESS, IMPLEMENTED, TESTED, BLOCKED, NOT_APPLICABLE.
Priority: P0 (boot/usability blockers), P1 (core experience), P2 (completeness).
Completion requires correct behavior, iOS 27-identical layout and motion, accessibility, integration, tier behavior (A = 3-4 GB, B = 2 GB) and a recorded test.

## A. System experience
| Component | Purpose | Candidate source (verify) | Proposed AppleUXOS design | Constraints / security | Pri | Test | Tier B behavior | Status |
|---|---|---|---|---|---|---|---|---|
| Boot animation | First visual at power-on | `bootanimation` in device/product config | iOS-style boot logo sequence from licensed assets | Boot-time cost; asset license | P2 | Boot capture, boot time | Static or shorter sequence | NOT_IMPLEMENTED |
| Setup wizard | First-boot setup | AOSP Setup Wizard (verify in tree) | iOS 27 setup flow | Google steps not available in pure AOSP | P1 | Fresh-flash setup completion | Same | NOT_IMPLEMENTED |
| Lock screen | Unlock, notifications, widgets | `frameworks/base/packages/SystemUI` | iOS 27 lock screen layout, clock, swipe-up unlock | Keyguard and credential security must not weaken | P0 | Unlock, PIN, biometric, timeout | Reduced effects | NOT_IMPLEMENTED |
| Always-on display | Glanceable info | SystemUI/doze (hardware dependent) | iOS-style dimmed lock | LCD panel may not support efficient AOD | P2 | Hardware check | Off | NOT_IMPLEMENTED |
| Home screen / dock | App grid, dock, pages | `packages/apps/Launcher3` (or replacement) | iOS 27 grid, dock, page dots, jiggle mode | Launcher is default home role | P0 | Layout, drag, folders, rotation | Fewer animated layers | NOT_IMPLEMENTED |
| App library | Auto-categorized apps | Launcher | iOS 27 app library | Categorization data source TBD | P1 | Category correctness | Static layout | NOT_IMPLEMENTED |
| Widgets | Home/lock widgets | Launcher + AppWidget framework | iOS-style widget sizes and stacks | AppWidget API compatibility | P1 | Add/remove/resize | Lower refresh | NOT_IMPLEMENTED |
| App switcher | Recents | Launcher/Quickstep (verify) | iOS 27 card carousel | Recents API | P0 | Gesture, memory under 10 apps | Simpler cards | NOT_IMPLEMENTED |
| Status bar | Time, signal, battery | SystemUI | iOS 27 status bar | Cutout/notch layout on POCO M5 | P0 | Layout across states | Same | NOT_IMPLEMENTED |
| Navigation gestures | Home, back, switcher | SystemUI + framework | iOS 27 bottom-bar gestures | Android back semantics | P0 | Gesture thresholds, app compatibility | Same | NOT_IMPLEMENTED |
| Notification shade and banners | Notifications | SystemUI | iOS 27 notification center and banners | Notification channels, delivery | P0 | Delivery, grouping, actions | Reduced blur | NOT_IMPLEMENTED |
| Control Center | Quick controls | SystemUI QS | iOS 27 Control Center modules | Toggle backends (Wi-Fi, BT, flashlight) | P0 | Each control works | Fewer live previews | NOT_IMPLEMENTED |
| Volume / brightness | Hardware controls UI | SystemUI | iOS 27 HUD and sliders | Audio focus | P1 | Hardware keys, slider | Same | NOT_IMPLEMENTED |
| Media controls | Now Playing | SystemUI | iOS 27 media card | MediaSession API | P1 | Playback control | Lighter artwork | NOT_IMPLEMENTED |
| Power / restart UI | Shutdown menu | SystemUI/framework | iOS-style slide/power UI | Safe shutdown | P2 | Power flows | Same | NOT_IMPLEMENTED |
| Global transitions | App open/close, window anims | framework shell transitions (verify) | Spring-based iOS transitions | Framework patch risk | P0 | Perfetto frame metrics | Reduced complexity | NOT_IMPLEMENTED |

## B. Framework interfaces
| Component | Purpose | Candidate source (verify) | Proposed design | Constraints | Pri | Test | Status |
|---|---|---|---|---|---|---|---|
| Alerts / dialogs | Framework dialogs | `frameworks/base/core/res`, overlays | iOS alert and action-sheet | Must work for third-party apps | P1 | Dialog matrix | NOT_IMPLEMENTED |
| Permission prompts | Runtime permissions | PermissionController (verify) | iOS-style permission alerts | Permission enforcement unchanged | P0 | Grant, deny, one-time | NOT_IMPLEMENTED |
| App installer | Package install UI | PackageInstaller (verify) | iOS-style install sheets | Install security | P1 | Install, fail, update | NOT_IMPLEMENTED |
| Credential / biometric prompts | Auth UI | BiometricPrompt UI in SystemUI | iOS-style prompt | Must not weaken auth | P0 | PIN, fingerprint/face if available | NOT_IMPLEMENTED |
| Clipboard / privacy indicators | Mic/camera/clipboard dots | SystemUI | iOS-style indicators | Privacy semantics | P1 | Indicator accuracy | NOT_IMPLEMENTED |
| USB prompts | File transfer/charge | SystemUI USB UI | iOS-style prompt | USB security | P2 | Plug/unplug modes | NOT_IMPLEMENTED |
| Text selection / context menu | Copy/paste toolbar | framework widget | iOS-style callout | Widely used by apps | P1 | Text selection in apps | NOT_IMPLEMENTED |
| File picker / share sheet | Storage Access, ShareSheet | DocumentsUI, framework | iOS-style share sheet | Intent compatibility | P1 | Share to apps | NOT_IMPLEMENTED |
| Error / empty / recovery screens | Crash and ANR dialogs | framework | iOS-style | Crash handling | P1 | Forced crash/ANR | NOT_IMPLEMENTED |
| Battery / storage / network / update screens | Status UIs | Settings + SystemUI | iOS-style | OTA depends on update mechanism | P2 | Low battery, storage full | NOT_IMPLEMENTED |

## C. Settings
| Component | Candidate source (verify) | Notes | Pri | Status |
|---|---|---|---|---|
| Settings home and search | `packages/apps/Settings` | iOS 27 menu order and grouping | P0 | NOT_IMPLEMENTED |
| Network, Wi-Fi, Bluetooth | Settings | Wired to real connectivity services | P0 | NOT_IMPLEMENTED |
| Display and brightness | Settings | Refresh rate, dark mode, text size | P0 | NOT_IMPLEMENTED |
| Sound and haptics | Settings | Vibration motor limits | P1 | NOT_IMPLEMENTED |
| Battery / performance modes | Settings | No unmeasured claims | P1 | NOT_IMPLEMENTED |
| Apps, storage, default apps | Settings | Android default-role flows | P1 | NOT_IMPLEMENTED |
| Privacy, security, permissions | Settings | Security semantics preserved | P0 | NOT_IMPLEMENTED |
| Accounts, backup, sync | Settings | Google parts depend on GMS | P2 | NOT_IMPLEMENTED |
| Accessibility, language, region, input | Settings | Full accessibility parity | P1 | NOT_IMPLEMENTED |
| Developer options, About | Settings | Needed for development | P1 | NOT_IMPLEMENTED |

## D. System applications
| App | Candidate source (verify) | Plan | Pri | Status |
|---|---|---|---|---|
| Launcher | `packages/apps/Launcher3` | Redesign or replace | P0 | NOT_IMPLEMENTED |
| Phone / dialer | `packages/apps/Dialer` or telephony UI (verify) | Redesign | P1 | NOT_IMPLEMENTED |
| Contacts | `packages/apps/Contacts` (verify) | Redesign | P1 | NOT_IMPLEMENTED |
| Messaging | `packages/apps/Messaging` (verify) | Redesign/create | P1 | NOT_IMPLEMENTED |
| Photos / Gallery | Gallery app (verify) | Create or redesign | P1 | NOT_IMPLEMENTED |
| Files | DocumentsUI | Redesign | P1 | NOT_IMPLEMENTED |
| Camera | Camera app + vendor camera HAL | Depends on camera stack support | P1 | NOT_IMPLEMENTED |
| Clock | `packages/apps/DeskClock` (verify) | Redesign | P2 | NOT_IMPLEMENTED |
| Calculator | Calculator (verify) | Redesign | P2 | NOT_IMPLEMENTED |
| Calendar | Calendar (verify) | Redesign | P2 | NOT_IMPLEMENTED |
| Browser | Browser/WebView shell (verify) | Create or integrate | P2 | NOT_IMPLEMENTED |
| Keyboard / IME | LatinIME (verify) | iOS-style keyboard | P1 | NOT_IMPLEMENTED |
| Downloads | DownloadProvider UI | Redesign | P2 | NOT_IMPLEMENTED |
| Updater | OTA client if supported | Depends on update mechanism | P2 | NOT_IMPLEMENTED |

## E. Edge states to cover on every surface
Permission denied, no network, airplane mode, low battery, storage full, app crash/ANR, failed install, interrupted animation, rotation, font scale at maximum, dark mode, reduced motion, hardware unavailable, memory pressure (Tier B).

## F. Familiarity check (side-by-side vs iOS 27)
For each IMPLEMENTED surface record: reference used, differences found, deviation-log entry, reviewer.

---

## ACTUAL STATE (2026-10-19) — surface-by-surface ledger

From the final file-verified audit + this session's fixes (full detail:
PREVIOUS_WORK_DONE §1, §6):

| Surface | State |
|---|---|
| Home (grid/dock/labels/dock-clearance) | ~95% — geometry from MEASURED panel |
| Icon badges | DONE this cycle (dispatchDraw overlay + AppleuxBadgeStore seam; SystemUI publish side pending) |
| Gestures | Zones fixed: CC/NC only from top-edge strip; below = Spotlight; NC single-open |
| Spotlight | App + settings index + web row live in launcher |
| Control Center | Layout exact, COMPILED into SystemUI; runtime unverified (needs matched guest); tile registry empty (AppleuxCCTileOrder still unused) |
| Notification Center | Shade + bus + swipe-dismiss complete, COMPILED; banner/expanded/footer dead; cutout inset one-liner still open |
| Status bar | Ours in home + shade (compiled); stock in other apps until SystemUI ships |
| Notch / Dynamic Island / Home indicator | Wired + compiled (NotchHost system-wide, HomeIndicator via navigation_bar.xml) |
| Volume | iOS capsule restyle (ids preserved), launcher-side HUD; system-wide HUD dead |
| Power | AppleuxPowerControl compiled (IPowerManager route); no UI host yet |
| Settings | iOS 27 root order + Customization page; ~6/28 rows pre-restructure; General branch 0/21; no search pill yet |
| Lock screen | ABSENT — AppleuxLockScreen + AppleuxPasscodeView have no keyguard host (top dead surface) |
| Widgets | 3-widget closed set; hybrid host design done (native canvas first-party, AppWidgetHost third-party, analog clock, weather via Open-Meteo) — not built |
| Recents | Invisible switcher (<2 history = silent); AppleuxRecentsView dead |
| 10 system apps | Real APKs, built; need reinstall over stale versions on any test guest |
| Typography | Tracking ~60× too tight — measure once on device, fix the divisor (open) |
| Sounds/haptics | 366 IPSW sounds linked; SoundRegistry call sites partially wired |
| Material Design | Zero live: AlertDialog.Builder 0 (29 stock sites in frameworks are pre-takeover), Toast 0, SeekBar→AppleuxSlider, android.R.drawable replaced in appleux modules |