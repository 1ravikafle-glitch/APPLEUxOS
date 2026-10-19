# AppleUXOS — IMPLEMENTATION ROADMAP

> **Status: DRAFT v0 — NO MILESTONE HAS STARTED.**
> Durations are intentionally omitted: they depend on audit results and host build speed. Update with measured data after Milestone 1.

## M0 — Repository audit
- **Prerequisites:** repository access, host tools.
- **Deliverables:** audit report; verified paths in all `docs/appleux/` files; resource report (RAM, swap, threads, disk).
- **Source locations:** repo root, `.repo/manifests`, device/vendor/kernel trees for `rock`, `frameworks/base`, `packages/apps`.
- **Tests:** none (read-only).
- **Risks:** partial or custom tree; missing vendor blobs.
- **Acceptance:** every item in prompt section 9 answered with evidence; open questions listed.

## M1 — Bootable foundation
- **Prerequisites:** M0; enough disk and swap.
- **Deliverables:** reproducible build script; first successful emulator (Cuttlefish) build if feasible; initial 3-4 GB and 2 GB product configs.
- **Tests:** build twice, compare; boot to home; record boot time.
- **Risks:** host RAM limits, Cuttlefish unsupported on host, vendor tree gaps.
- **Acceptance:** recorded successful boot with logs; build time and resource use documented.

## M2 — Design System
- **Prerequisites:** M1; licensed asset delivery (see `ASSET_MANIFEST.md`).
- **Deliverables:** token source and generator; shared UI library; framework resource overlays; motion spec with measured spring values.
- **Tests:** component gallery app; screenshot tests light/dark/font-scale; frame-time test on emulator.
- **Risks:** missing assets; continuous-corner and blur cost.
- **Acceptance:** every component in `DESIGN_SYSTEM.md` section 7 has spec, implementation and test.

## M3 — Core system interface
- **Prerequisites:** M2.
- **Deliverables:** launcher, lock screen, status bar, shade, Control Center, volume, app switcher, navigation gestures, global transitions.
- **Tests:** gesture matrix; notification delivery; lock/unlock and credential tests; Perfetto frame metrics.
- **Risks:** shell-transition patches, gesture conflicts with apps.
- **Acceptance:** UI Coverage rows A-section P0 reach TESTED with recorded results.

## M4 — Framework-level interfaces
- **Prerequisites:** M3.
- **Deliverables:** dialogs, permission UI, installer, credential/biometric prompts, privacy indicators, text selection, share sheet, error screens.
- **Tests:** third-party app compatibility sample; permission grant/deny; install failure paths.
- **Risks:** compatibility with apps expecting stock behavior.
- **Acceptance:** B-section P0/P1 rows TESTED; patch ledger up to date.

## M5 — Settings and onboarding
- **Prerequisites:** M2-M4.
- **Deliverables:** full Settings in iOS 27 structure; first-boot flow.
- **Tests:** every setting toggles real backend; fresh-flash onboarding.
- **Risks:** Google-owned flows unavailable.
- **Acceptance:** C-section rows TESTED; no stock-Android Settings screens reachable.

## M6 — System applications
- **Prerequisites:** M2, relevant M4 components.
- **Deliverables:** dialer, contacts, messaging, files, gallery, clock, calculator, calendar, keyboard, others per audit.
- **Tests:** per-app functional tests; intent and share integration.
- **Risks:** camera/telephony depend on vendor stack.
- **Acceptance:** D-section rows reach TESTED or documented BLOCKED/NOT_APPLICABLE.

## M7 — Integration and coverage audit
- **Prerequisites:** M3-M6.
- **Deliverables:** full surface sweep; remaining-stock-screen list; fixes; familiarity report vs iOS 27.
- **Tests:** end-to-end user journeys; regression suite.
- **Risks:** hidden AOSP screens.
- **Acceptance:** coverage matrix has no unexplained DISCOVERED/NOT_IMPLEMENTED rows.

## M8 — Device integration (POCO M5)
- **Prerequisites:** stable emulator build; verified `rock` trees and vendor image.
- **Deliverables:** device build, install and recovery procedure; hardware checklist (display, touch, audio, radio, camera, sensors, fingerprint).
- **Tests:** flash on device with backup and recovery path verified first; hardware matrix.
- **Risks:** bricking, missing blobs, Verified Boot and bootloader state, vendor HAL mismatch.
- **Acceptance:** device boots and passes the hardware matrix; recovery tested.

## M9 — Performance and release
- **Prerequisites:** M8.
- **Deliverables:** measured results for 3 GB and 2 GB configurations; tuned LMKD/zRAM/scheduler/Power HAL settings; security review; release notes with limitations.
- **Tests:** see `BUILD_AND_TEST_STRATEGY.md` performance section and `PERFORMANCE_AND_POWER_TUNING.md` (sections 13 and 15).
- **Risks:** Helio G99 GPU limits; the gap to iOS-level smoothness.
- **Acceptance:** targets met or gap documented with numbers; no unmeasured claims; security checks pass.

## Cross-cutting
- Documentation updated at each milestone.
- Patch ledger maintained.
- Licensed-asset provenance kept current.

---

## ACTUAL STATE (2026-10-19) — milestone ledger

Work predating this roadmap lives in `PREVIOUS_WORK_DONE.md` (read it first).
Mapping of that work onto these milestones:

- **M0 (repo audit)** — DONE long ago: device tree, vendor, kernel audited
  (see PREVIOUS_WORK_DONE §7); design-source precedence locked.
- **M1 (bootable foundation)** — Soong analysis + `m SystemUI` PROVEN
  (32 min, 0 errors). A bootable full image NOT yet built; next concrete step
  is `m` for `aosp_cf_x86_64_only_phone` (product exists in
  `device/google/cuttlefish`) to get a framework-matched guest — the Android 17
  stock guest cannot run our 16-built SystemUI (named method:
  `sysuiLargePerfettoShmemBuffer`).
- **M2 (design system)** — source complete: tokens, 52 components, squircle,
  motion engine, 366 IPSW sounds; HIG replacement charter binding
  (zero Material Design anywhere).
- **M3 (core system interface)** — source complete AND COMPILED:
  chrome module linked into SystemUI-core + SystemUI-application (classes
  verified in the 179 MB APK). Runtime verification blocked on M1's
  framework-matched guest. Volume capsule + shade status bar + notch host +
  notification bus all wired at source level.
- **M4 (framework interfaces)** — partial: squircle `config_icon_mask`,
  volume/side-key config overlays, framework-res restyles; single-volume etc.
  in FrameworkOverlayRock.
- **M5 (Settings)** — iOS 27 root order + Customization page landed; detail
  pages exist; still short of the full 28-row root + General branch (spec in
  UI_COVERAGE_MATRIX).
- **M6 (system apps)** — 10 apps are real APKs (own packages, LAUNCHER
  category, role intents, iOS icons) + apps-shared library; SDK route builds
  all 14 APKs.
- **M7 (integration audit)** — final audit ledger in
  PREVIOUS_WORK_DONE §6 (dead components + open gaps).
- **M8 (POCO M5)** — physical device CONNECTED (serial `nb79nvba65tse6ug`),
  audited, flash-target status. Needs bootloader unlock decision + full image.
- **M9 (performance)** — controller implemented (Q100→Q70, frame budgets
  scaled 30–90 Hz); no measured device numbers exist (emulator numbers are
  SwiftShader, not representative). 3-mode power design
  (Performance/Normal/Auto + Battery Saver) documented, kernel-gated.