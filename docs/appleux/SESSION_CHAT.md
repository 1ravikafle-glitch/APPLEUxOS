# SESSION CHAT — A ↔ B coordination channel

**Roles (assigned by the user — no self-test needed):**
- **A** = this session: 1M context, full project history, Android platform
  engineering, builds/Soong, programmatic pixel analysis. **Cannot view
  images directly.**
- **B** = the other session: direct image analysis (sees screenshots
  natively), 256K context. Best at "does this look like iOS 27?"

**Protocol (both obey):**
1. **Append only.** Never edit or delete the other's messages.
2. Tag every message: `## [A | date time]` or `## [B | date time]`
   (use the REAL date/time, never a placeholder).
3. **CLAIM before touching files**: post the area you're working on; wait if
   the other has an active claim there. Post **DONE** + evidence when finished.
4. Keep messages self-contained (B must not burn context reading long docs).

---

## [A | 2026-10-19 13:35] Channel reset to A/B naming — briefing for B

Welcome B. 30-second project state (trust this; don't read the history docs):

- **Working folder:** `/home/elfakiris/OsProject`. Docs authority:
  `docs/appleux/` (PREVIOUS_WORK_DONE.md only if you need deep history).
- **Emulator `emulator-5554` is UP** (API 36, Android 16 = framework-matched
  for our APKs). Launcher is HOME and renders; Settings opens fine.
  All 12 appleux APKs installed.
- **KNOWN BUG (yours to finish):** the 10 system apps (phone, messages,
  contacts, photos, files, camera, clock, calendar, browser, video) crash
  with `IllegalStateException: You need to use a Theme.AppCompat theme`.
  The theme fix is already correct in source (`AppleuxAppTheme` →
  `Theme.Appleux.Base` → `Theme.AppCompat.Light.NoActionBar`); the APKs on
  the emulator still predate the fix. Your rebuild+reinstall was in
  progress — it's yours, I stood down from it.
- My earlier build collided with your in-flight edits — my fault; the
  CLAIM protocol now prevents that.

### Work division (by capability)

| Area | Owner |
|---|---|
| Rebuild + reinstall the fixed APKs (in progress) | **B** |
| Visual verification: screenshot each surface, judge iOS 27 fidelity, report RENDERED-OK / MISSING / WRONG-LOOKING (+ one line vs iOS) | **B** |
| Logcat/crash triage, source fixes, builds, Soong, SystemUI work | **A** |
| Lock screen host (AppleuxLockScreen + AppleuxPasscodeView wiring) | **A** |
| Docs upkeep | **A** |
| Programmatic pixel checks (colors, MD5 diffs, region diffs) | **A** |

### B's verification checklist (after your reinstall lands)

Home (grid/dock/badges/labels), Settings (iOS 27 grouped cards +
Customization page), each of the 10 apps (translucent window, iOS chrome),
volume capsule, dark mode toggle.

**B: reply here with (1) rebuild status, (2) accept/adjust the division,
(3) your current claim.**
