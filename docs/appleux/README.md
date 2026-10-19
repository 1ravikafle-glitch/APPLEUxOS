# docs/appleux — HOW THIS PROJECT RUNS (entry point — read this first)

AppleUXOS documentation and two-session collaboration, all in one place.
**Working folder:** `/home/elfakiris/OsProject` (never changes).

## The three sessions (roles assigned by the user)

| | **A** | **B** | **C** |
|---|---|---|---|
| Context window | 1M (≈ **750K usable** — compacts after that) | **250K** (compacts after that) | (as provided) |
| Sees images | **No** (programmatic pixel analysis only) | **Yes** (native) | **No** (programmatic PIL + ground-truth diffing — MORE precise for measurement; visual verdicts come from B) |
| Strength | Android platform engineering, Soong builds, SystemUI, long multi-step work, docs keeper, **orchestrator (A > B > C, user-set hierarchy)** | Visual verification vs iOS 27 + **code implementation** (app layer, launcher logic) + **design acceptance gate: ALL C work needs B's visual verdict** | **Design fix ONLY**: tokens, res/XML (colors/typography/spacing/drawables/layouts), pixel-perfect iOS 27 fidelity — **DEEP by default** (see standing rule 9) |
| Owns | source fixes, builds, architecture, docs, **arbitration** | app-layer features + visual QA verdicts | design polish; needs logic changes → posts task for B, never edits Kotlin |

## The workflow (any session, any time)

1. **Read this README.** 2. **Chat tail** (last ~40 lines + LATEST block).
3. **WORK_QUEUE.md.** 4. **CLAIM** in chat. 5. **Work → DONE + evidence →
update queue.** 6. **Never edit the other's messages or claimed files.**

## The relay rule (user prompts)

**User prompts land on ONE session.** Whoever receives relays it to the
other IMMEDIATELY in the chat as `USER RELAY: <verbatim prompt>` + proposed
split, then both work their halves. No prompt stays on one side. We
self-divide per the capability table. After in-flight work resolves, the
mission is `AppleUXOS_Master_Prompt.md` worked from the beginning, with
IMPLEMENTATION_ROADMAP M0–M9 as execution order.

## Documentation map — which file owns which knowledge

| File | Owns | Who maintains |
|---|---|---|
| `SESSION_CHAT.md` | live coordination, claims, transient messages | both (append-only) |
| `WORK_QUEUE.md` | standing task board: task / owner / status / evidence | both |
| `AppleUXOS_Master_Prompt.md` | mission, constraints, targets | A |
| `ARCHITECTURE.md` | as-built module graph, locked decisions | A |
| `IMPLEMENTATION_ROADMAP.md` | M0–M9 milestone ledger | A |
| `BUILD_AND_TEST_STRATEGY.md` | proven build invocations, host rules, test discipline | A |
| `UI_COVERAGE_MATRIX.md` | surface-by-surface state (inputs from B's visual QA) | A + B |
| `DESIGN_SYSTEM.md` | tokens, provenance, motion | A |
| `ASSET_MANIFEST.md` | IPSW extraction ledger | A |
| `PERFORMANCE_AND_POWER_TUNING.md` | tuning levers + honest numbers | A |
| `RISK_REGISTER.md` | realized + open risks | A |
| `TUNING_LOG.md` | experiment log (change → result → verdict) | both |
| `PREVIOUS_WORK_DONE.md` | full migrated history (read ONLY if needed) | A |

## Standing rules (project law)

1. Zero Material Design anywhere (HIG charter).
2. No credit for components that exist but never render — every claim needs
   visual or logcat evidence.
3. No measured-performance claims without real capture data.
4. `git add -f` for new files under `android-16/` (the .gitignore trap).
5. `frameworks/base` is its own git repo — SystemUI edits commit there.
6. Our 16-built SystemUI only runs on a 16 framework — never test it on the
   17 stock guest (named drift: `sysuiLargePerfettoShmemBuffer`).
7. Working emulator is `emulator-5554` (API 36, framework-matched).
   Physical POCO M5 (`nb79nvba65tse6ug`) is the flash target — locked MIUI.
8. **Standby + sync**: finished your claim → STANDBY (armed, waiting for
   assignment). New cross-lane work needs a proper assign. Sync-free work
   only if it cannot affect the others (their claims, emulator, builds).
9. **C depth bar**: every C task must be DEEP — exact measurements with
   the math, every token consumer traced, all states checked (light/dark,
   Q100-Q70, reduce-motion, font scale, 2 GB tier), cross-surface pattern
   consistency, root-cause token (never site patches), ground-truth photos
   cited. Missing any = NOT DONE. Full form: SESSION_CHAT @C directive.
10. **Read receipts**: tick every message header you've read (✓A/✓B/✓C);
    both other letters = read by all. Tick before replying.
11. **Final division**: A = ALL coding + ALL builds + fixes every code
    error in any build. B = visual verdicts + light visual fixes.
    C = precise design/layout (token/dimen/res) fixes. B and C never
    touch code logic; A never bounces code fixes to them. Loop:
    A builds → B verdicts → C fixes design → A rebuilds → B re-verdicts.
12. **1-minute rule**: B/C @A a code ask → if A does not answer within
    1 minute, they may do the LIGHT fix themselves (typo, import,
    one-line crash fix, small XML+code bit) — never features or
    SystemUI/framework code — and post the diff for A's after-review.
13. **ALL-IS-OURS (Master Prompt §6) — the double-bar law**: every
    user-visible component WE control is OURS. NEVER keep, pad under, or
    stack stock chrome on a surface we own — a stock status bar above
    our app = WRONG by definition (the A13 error: it reserved space
    under the stock bar instead of hiding it and drawing ours). The
    permanent check, every surface, every pass: is there ANY stock
    element visible where we control the surface? Status bar over our
    apps, stock dialogs in our flows, stock toasts, stock volume panel,
    stock lock chrome — each = a WRONG verdict, no exceptions. (A13
    superseded by A17: hide stock statusBars via WindowInsetsController,
    draw AppleuxStatusBar — one bar, ours.)
14. **DEVICE-TRUTH law (POCO M5)**: all device-dependent design targets
    the MEASURED rock hardware (RockDisplayProfile) — waterdrop cutout
    100x76px top-center (NOT an iPhone pill), 1080x2408 @440dpi, top
    inset 76px, corners 106px, 30/50/60/90Hz. iPhone 11 ground-truth
    photos are LAYOUT inspiration, never literal geometry: C4's island
    fix must be scoped to OUR cutout (island wraps the waterdrop), not
    iPhone pill proportions.
15. **NO-IDLE (2-minute rule)**: waiting on a verdict → 2 minutes max,
    then self-assign the next safe task (your lane first, then unclaimed
    S-tasks; never others' claims/emulator/builds). Post the claim; tick
    the chat first. Verdicts take priority when they land. Lane+backlog
    empty → post "@ALL: idle, requesting assignment". Balanced load —
    nobody hoards, nobody idles silently.

## COMPACTION PROTOCOL (both sessions — this is how work survives)

Both sessions auto-compact when their window fills (A ≈750K, B ≈250K). The
docs on disk are the only durable memory — context is disposable.

**Rules:**
1. **Write early, write often.** Any finding, decision, or progress worth
   keeping goes into SESSION_CHAT.md / WORK_QUEUE.md / TUNING_LOG.md
   IMMEDIATELY — never held in context "until later".
2. **Before starting a long task**, post your plan to the chat (one screen).
   If you compact mid-task, the plan is on disk and any session can resume.
3. **After a compaction**, reorient with exactly three reads, in order:
   `README.md` → tail of `SESSION_CHAT.md` (last ~40 lines) →
   `WORK_QUEUE.md`. Do NOT read PREVIOUS_WORK_DONE.md unless a task
   explicitly needs history.
4. **The chat's briefing message is the resume point.** Keep the newest
   30-second briefing accurate — A refreshes it whenever project state
   changes materially.
5. Evidence lives in WORK_QUEUE rows and the Done ledger — screenshots get
   pixel-analysed (A) or judged (B) and the VERDICT is written down, not
   the pixels.
