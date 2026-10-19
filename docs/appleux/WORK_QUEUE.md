# WORK QUEUE — the standing task board

Update your task's status line when you start and finish. Claims go in
`SESSION_CHAT.md`; this file is the durable record.

**Status key:** 🔴 blocked · 🟡 in progress · 🟢 done (evidence linked) · ⚪ not started

## B's tasks (image analyst)

| # | Task | Status | Evidence / notes |
|---|---|---|---|
| B1 | Rebuild + reinstall the 10 fixed-theme APKs on `emulator-5554` | 🟡 reinstall in progress | rebuild DONE (B, 17:14): 14/14 APKs clean incl. theme fix + COW crash fix; reinstall underway |
| B2 | Visual QA: home (grid/dock/badges/labels) | ⚪ | after B1 — report RENDERED-OK / MISSING / WRONG-LOOKING |
| B3 | Visual QA: Settings (iOS 27 grouped cards + Customization page) | ⚪ | after B1 |
| B4 | Visual QA: each of the 10 apps (translucent window, iOS chrome) | ⚪ | after B1 — watch for the AppCompat crash recurrence in logcat |
| B5 | Visual QA: volume capsule + dark mode toggle | ⚪ | after B1 |
| B6 | Side-by-side vs ground-truth photos (`design-reference/iphone11-ios27/`) — top 5 visual gaps | ⚪ | B's unique capability; one line per gap |

## A's tasks (platform engineering)

| # | Task | Status | Evidence / notes |
|---|---|---|---|
| A1 | Lock screen host: wire `AppleuxLockScreen` + `AppleuxPasscodeView` into keyguard | 🟡 claimed by A | started 17:40; design note before SystemUI keyguard touch |
| A2 | Framework-matched guest: build `aosp_cf_x86_64_only_phone` from our tree | ⚪ | unlocks runtime verification of the built SystemUI (17 guest is a dead end — named drift) |
| A3 | Settings full iOS 27 root (28 rows + General branch + search pill + account card) | ⚪ | spec in UI_COVERAGE_MATRIX |
| A4 | Widget hybrid host (analog clock, weather via Open-Meteo, 4 sizes, gallery) | ⚪ | design done, not built |
| A5 | Typography tracking fix (measure once on device, fix divisor) | ⚪ | needs B's side-by-side input from B6 |
| A6 | Badge publisher side: SystemUI → launcher counts (extend AppleuxBadgeStore seam) | ⚪ | launcher render side done |
| A7 | CC tile registry (populate tiles; use AppleuxCCTileOrder) | ⚪ | CC layout built, tiles empty |
| A8 | Full rock image build (`m`) → flash to physical M5 (needs bootloader unlock decision) | ⚪ | device tree audited, ready |

## C's tasks (design fix only — no Kotlin logic edits)

| # | Task | Status | Evidence / notes |
|---|---|---|---|
| C1 | Typography tracking fix (letters ~60x too tight; measure vs ground truth, fix divisor) | 🟡 claimed by C 18:43 | measurement-first: divisor number → B verdict → token fix |
| C2 | Widget card look (adaptive light/dark, glass per LiquidGlassTokens) | ⚪ | |
| C3 | Dock glass bar visual (B 18:21: not evident — verify + polish) | ⚪ | may need B for layout logic |
| C4 | Dynamic Island width dimen (measured 542px = 50% screen; iOS ≈30-32%) | ⚪ | C candidate; needs B verdict before token change |

## Shared / backlog (claim in chat before starting)

| # | Task | Notes |
|---|---|---|
| S1 | Recents: wire `AppleuxRecentsView` + empty state | dead component |
| S2 | NC banner / expanded / footer (3 dead components) | heads-up + actions |
| S3 | System-wide volume HUD (`AppleuxSystemHUD`) | launcher-only today |
| S4 | NC cutout inset one-liner (`AppleuxDisplayInsets.applyTopInset`) | known gap |
| S5 | Multi-folder naming (addToFolder hardcodes "Folder") | |
| S6 | Boot animation (`bootanimation.zip` + product wiring) | binary exists, no zip |
| S7 | SoundRegistry call sites (ringtone, lock/unlock, shutter) | sounds linked, sites partial |

## Done ledger (move rows here with evidence)

**2026-10-20 session round (all committed, riding the pending rebuild):**
- 🟢 A11 page-0-only widgets + per-page capacity (LauncherActivity) — B verdict drove it
- 🟢 A12 tile reflow timing (post-layout; B cross-check pending: immediate vs late)
- 🟢 A13 status-bar inset in applySystemBars (fixes title/clock collision)
- 🟢 A3 COMPLETE: account card + live value text + floating search pill (Settings)
- 🟢 Stock-app hide-list (grid-only STOCK_REPLACED; stock stays installed)
- 🟢 A1 lock-screen host (visual layer; auth 100% AOSP; B verify after PIN)
- 🟢 B5/B6 (by B, A-approved): half-half Clock|Battery pair; pill page-0-only
- 🟢 C1 typography -0.0253em (by C, A lane-approved)
- 🟢 SHUTDOWN system_ext allowlist (found by B, fixed by A)
- 🟢 Emulator infra: zombie-qemu teardown; warp-taskbar suspended (0% CPU)

- 🟢 **B/A n/a** — 12 appleux APKs installed on emulator-5554; launcher is
  HOME and rendering; Settings opens (focus verified, 2026-10-19 ~13:00).
- 🟢 **A** — `m SystemUI` built (32 min, 0 errors; all chrome classes verified
  in APK). Evidence: PREVIOUS_WORK_DONE §1.
- 🟢 **B** — COW iterator bug fixed (10 removeIf sites; was crashing
  launcher on every onDestroy + probable cause of 213× SurfaceFlinger
  out-of-order spam). Evidence: chat 17:14, verified in source by A.
- 🟢 **A** — SDK build lock (flock) added after B's race diagnosis.
- 🟢 **A** — theme parent fixed in source (`Theme.Appleux.Base` →
  `Theme.AppCompat.Light.NoActionBar`); B's rebuild delivers it to the device.
