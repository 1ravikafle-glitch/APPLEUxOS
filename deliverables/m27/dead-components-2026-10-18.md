# The 13 remaining unreferenced components — what each one is waiting for

2026-10-18. Replaces the bare "16 dead components" line in the gap table.

A component with no references compiles cleanly, fails silently, and reads as
done, so this list is a live assertion rather than a note:

```bash
cd android-16/frameworks/appleux
for x in AppleuxCCTileOrder AppleuxJiggle ...; do
  k=$(grep -rl "\b$x\b" --include=*.kt --include=*.xml . | grep -v "/$x\." | wc -l)
  [ "$k" -eq 0 ] && echo "DEAD: $x"
done
```

**16 → 13 this session.** Wired: `AppleuxJiggle` (iOS edit mode),
`AppleuxPanelDismissHandler` (SystemUI shade host), `AppleuxNotificationBus`
(first subscriber). Deleted one outright — see the last section.

The remaining 13 are **blocked on a host or a permission, not on code.** Each is
listed with the specific thing it needs, so none of them is just "TODO".

---

## Blocked on a SystemUI host (4)

These render inside `com.android.systemui` and need the shade/nav container.

| component | lines | what it needs |
|---|---|---|
| `AppleuxNotificationBanner` | 129 | The expanded-shade container. Now reachable — `AppleuxShadeHost.attach(shadeContainer)` exists and compiles; it needs a caller with a valid container reference. |
| `AppleuxNotificationExpanded` | 39 | same |
| `AppleuxNotificationFooter` | 18 | same |
| `AppleuxCCTileOrder` | — | CC tile ordering. Same host. Needs the drag-sort interaction wired into `AppleuxControlCenter`. |

**One line from here.** All four are waiting on the same call.

## Blocked on a lock-screen host (2)

| component | lines | what it needs |
|---|---|---|
| `AppleuxPasscodeView` | 134 | A keyguard host. AROM's `KeyguardViewMediator` owns the lock screen, and replacing it means keeping its behaviour (keyguard auth, IME, trust/managed flows) — the charter's "delete the chrome, keep the behaviour" rule. Doable, but it is its own project, not a wiring change. |
| `AppleuxLockCustomizeSheet` | 35 | Same host. It is the iOS "customise lock screen" sheet and sits behind a Settings toggle, which does not exist yet. |

## Blocked on a privileged permission (1)

| component | lines | what it needs |
|---|---|---|
| `AppleuxPowerSlider` | 39 | Slide-to-power-off / slide-to-restart. `AppleUXSettings` holds **only** `ACCESS_WIFI_STATE` and `WRITE_SETTINGS` — no `REBOOT`, no `SHUTDOWN`, and `MASTER_CLEAR` is absent from the Settings provider on this ROM. |

**Deliberately not wired.** Wiring it to `Process.killProcess(ownPid())` or any
other fake would violate two rules the project already holds: `SettingsCatalog`'s
"no placeholder values — a row whose backend needs a privileged write the app
cannot hold is a navigation row, not a toggle that pretends to work", and the
charter's "never break functionality for looks". The Shut Down row is omitted
from the Settings catalog for exactly this reason and stays omitted.

It becomes wireable the moment the ROM grants `REBOOT` to Settings — a
`privapp-permissions` line, not a code change.

## Blocked on a host that does not exist yet (4)

| component | lines | what it needs |
|---|---|---|
| `AppleuxRecentsView` | 68 | Custom Recents. Charter says Recents stays AOSP QuickStep "until our task-switcher exists" — this file IS that task-switcher, so wiring it to itself would be circular. |
| `AppleuxScreenshotToolbar` | 51 | The screenshot editor's floating toolbar, which AOSP shows from `ScreenshotOverlay`. Needs a replacement of AOSP's screenshot flow, same shape as the SystemUI takeover. |
| `AppleuxSystemHUD` | 48 | A custom HUD surface (volume/brightness replacement). Needs its own SystemUI window; the volume HUD is owned by `VolumeUI`, a separate APK. |
| `AppleuxSOSCountdown` | 49 | The SOS countdown. Needs the emergency-call flow, which is carrier- and SIM-dependent and cannot be verified here. |

## Deliberately NOT to be wired (1)

| component | lines | why |
|---|---|---|
| `AppleuxTouchFeedbackOverlay` | 156 | **This one must stay unreferenced.** |

`AGENTS.md`, "Touch Handling": `GestureHandler` observes the touch stream in
`GestureHostLayout.dispatchTouchEvent` *instead of adding a transparent overlay*.
An overlay must be clickable to receive events, so it sits above the grid and
dock and swallows every tap, "which made app icons impossible to launch."

Wiring this would reintroduce the exact regression it was written to avoid. It
is the only component on this list whose correct state is to stay dead.

---

## Deleted outright (1)

`launcher/src/main/java/com/appleux/launcher/ui/JiggleAnimation.kt` — a duplicate
of `AppleuxJiggle` with **fabricated provenance**. Its header read:

```
 * Parameters from IPSW CoreAnimation analysis:
 * - Rotation: ±2° (0.035 rad)
 * - Spring damping: 0.6
 * - Spring stiffness: 500
```

That analysis is impossible from this harvest. `m SystemUI` aside, the IPSW is a
**firmware-only image**: 122 entries, no `Payload/`, no dyld cache, no
`UIKitCore`. There is no compiled iOS animation constant on this machine to
analyse. The numbers were invented and labelled as extracted.

Kept `AppleuxJiggle`, which states its parameters and honours the Reduce Motion
setting, and wired it to iOS edit mode (long-press → icons jiggle), which is the
behaviour the other file was pretending to describe.

## On the SF Symbols font, re-verified

`AppleSymbols.ttf` is a real face — 4,400 `glyf` glyphs at 2048 upem — but its
`post` 2.0 table holds **4,163 names, all `uniXXXX`**, and **zero** SF Symbol
names (`checkmark.circle.fill` and friends). Its 3,641 codepoints are Apple's
private-use assignment, which is unpublished.

So the font cannot identify a glyph reliably, and the four faces were deleted
rather than wired. `design-reference/ios27/sf27-catalog.json` (32 categories, 340
new-in-iOS-27 symbols) is the inventory to extend the **vendored vector** set
from — that is the only route that can name a glyph.

## What would actually move these

In order of unblocking value:

1. **`m SystemUI`** — the hardware ceiling, not code. Needs more RAM or a
   narrower manifest so Soong analyses fewer of the 14,149 `Android.bp` paths.
2. **One line: `AppleuxShadeHost.attach(shadeContainer)`** — unblocks 4.
3. **A ROM image to test on** — every item above says "unverified at runtime"
   for the same reason.