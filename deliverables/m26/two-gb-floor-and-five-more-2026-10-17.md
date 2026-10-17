# m26 — 2 GB floor, real iOS motion, live sounds, iOS dialogs, hard RAM cap

2026-10-17. One session, six problems, all of which had been recorded as "known
gap" or "done" and were not.

---

## 1. The 2 GB tier was never implemented — there was no RAM awareness at all

`grep -rn "memoryClass\|isLowRamDevice" frameworks/appleux` returned **nothing**
outside one `MemAvailable` parse in `PressureMonitor`. The Adaptive Performance
Controller could lower quality, but only *after* frames were already being
dropped. It reacted to damage; it never sized itself to the machine.

Consequence on a low-memory phone: boot at Q100, render every expensive effect
on the first frames — the frames with least headroom — and discover the cost by
janking. Nothing sized a cache to the device either.

`performance/.../device/DeviceProfile.kt` is new. It publishes the tier, the
cache bounds and the baseline quality, and the controller clamps to it.

### The tiering axis was wrong twice before it was right

| attempt | rule | failure |
|---|---|---|
| 1 | `memoryClass` only | `memoryClass` is the *per-process heap* and does not track physical RAM. A 4 GB phone can be granted 512 MB and a 6 GB phone 384 MB, so this called the 4 GB device roomier than the 6 GB device. |
| 2 | `minOf(ram, heap)` | Sound in principle — "lower tier wins" — but it let a **4 GB** phone with a 384 MB heap be demoted to `TIER_STANDARD`, i.e. *below* a 3 GB phone. It was implemented as `if (byRam.level > byHeap.level)`, which is the opposite of what the comment said. |
| 3 | RAM primary, heap as a floor guard | Correct. |

The guard can only ever push **down** to `TIER_LOW`. It is a guard; it must not
also throttle.

Final table, computed by re-implementing the shipped rule:

| physical | memClass | tier | icons | pool | heap | baseline | blur |
|---|---|---|---|---|---|---|---|
| 2 GB | 128 MB | `TIER_LOW` | 10 | 1 MB | 55% | Q75 | **off** |
| 2 GB | 192 MB | `TIER_LOW` | 10 | 1 MB | 55% | Q75 | **off** |
| 3 GB | 256 MB | `TIER_STANDARD` | 20 | 4 MB | 70% | Q90 | on |
| 4 GB | 384 MB | `TIER_ROOMY` | 40 | 10 MB | 80% | Q100 | on |
| 4 GB | 160 MB | `TIER_LOW` | 10 | 1 MB | 55% | Q75 | **off** |
| 6 GB, `isLowRamDevice` | 768 MB | `TIER_LOW` | 10 | 1 MB | 55% | Q75 | **off** |

`isLowRamDevice` wins outright, because the platform sets it for reasons
`memoryClass` does not capture.

Two bugs found on the way: `MemoryTier` had named constructor arguments but **no
primary constructor**; and a stray `}` closed `DeviceProfile` before its own
companion object, which surfaced as `modifier 'companion' is not applicable
inside 'file'` 200 lines from the cause.

**Blur is off on `TIER_LOW` with no extra switch** — the ceiling is Q75 and the
blur gate requires Q90, so the clamp alone does it. That matters: one rule, and
no late recovery pass can switch the effect back on.

---

## 2. The RAM cap did not cap anything

`scripts/soong-capped.sh` set `GOMEMLIMIT=6GiB` and then ran a loop that
*recorded* peak RSS to a file. Reporting is not capping. `GOMEMLIMIT` is a hint
to the Go GC and was **measured exceeding it: 13.2 GiB against a 6 GiB limit**.

Nothing stopped it, so the kernel OOM killer picked a victim and the build died
with no error. The symptom this session: a build stopped dead after `Globs
changed, rerunning soong...` with an empty error section and no `soong_build`
process left.

The fix is a **cgroup v2 `MemoryMax`**, verified before being relied on:

```
/sys/fs/cgroup                -> cgroup2fs
cgroup.controllers            -> cpuset cpu io memory hugetlb pids rdma misc dmem
user.slice subtree            -> cpu memory pids      (delegated, no sudo)
systemd-run --user --scope -p MemoryMax=... works unprivileged

enforcement test:
  1.5 GB alloc, uncapped                -> succeeds
  1.5 GB alloc, MemoryMax=512M          -> process killed, systemd-run exits 1
```

Defaults 9 GiB RAM + 6 GiB swap, keeping ~6 GiB for the desktop so the machine
stays usable *during* a build. `GOMEMLIMIT=7GiB` / `GOGC=40` stay as soft hints
below the hard cap so Go collects before the kernel has to kill anything.

**The script now fails closed.** If `systemd-run` or the memory controller is
unavailable it exits 2 rather than running uncapped — running uncapped is what
crashed the box. A cap failure that is not enforced is worse than no cap, because
it is silent.

---

## 3. The sounds never linked, and 91 MB of audio shipped silent

`sounds/src/main/res` held **733** files, statically linked into **both** launcher
and settings. `SoundRegistry` had zero references outside its own module and
there was no `SoundPool` anywhere in the tree.

The root cause was not neglect. All **366** sounds had **both** a `.caf` and an
`.ogg` twin, and both compile to `R.raw.<stem>` — a duplicate-resource error. So
`AppleUXSounds` could never be built by `build-emulator-apks.sh` at all. That is
why it was never wired.

| encoding | total | |
|---|---|---|
| `.caf` (uncompressed PCM) | **82.2 MB** | → `sounds/extracted-reference/` |
| `.ogg` | **6.9 MB** | → shipped |

**91 MB → 7.7 MB**, and `R.raw.<stem>` is now unambiguous. Nothing was lost: every
sound still has its `.ogg`.

`AppleUXSoundPlayer` loads **lazily** — each sample is decoded the first time it
is played, cached after, bounded to 24 resident — because decoding 733 sounds up
front on a 2 GB device is tens of megabytes of native heap the Java-heap
accounting does not show you. It never throws, following the `AppleuxHaptics`
precedent: a notification chime is never worth a crash.

Wired to the camera shutter (on `ACTION_UP`, where the capture actually happens,
not on press) and a toggle confirm. **`AppleUXSounds` dropped from settings**,
which has no playback call site and was paying 7.7 MB for nothing.

Launcher 4.6 MB → 12 MB. Design-system, performance and settings unchanged.

---

## 4. `AppleuxDialog` was dead because it could not be presented

`AppleuxDialog` extends `FrameLayout`. Its `show()` toggles visibility and
nothing else — there is no WindowManager path anywhere in the class. So it could
not be shown *and* nothing could host it, which is why it had zero consumers
while **26** stock `AlertDialog`s ran.

`AppleuxDialogs` is the missing host. It attaches the dialog to the Activity's
content view — the same in-view approach as `AppleuxToast`, so an alert costs no
extra window and no extra composited layer — and exposes `alert` / `prompt` /
`choose` / `dismiss`, named for the `UIAlertController` method each maps to.
That naming is the mechanism that keeps `setView` out of the codebase for good:
the 12 `setView` sites were all "rename"/"new folder"/"new group", which is
`prompt`.

**11 of 26 converted.** Remaining: 8 use `.create()` + `dialog.show()` as
separate statements, 3 pass a non-`EditText` to `setView`.

### The converter destroyed files, and the dry run is why that is known

`scripts/convert-alertdialogs.py` parses the chain properly — top-level comma
splitting with `()[]{}` and string-literal tracking, lambda capture by brace
matching. The first working version had an invariant violation: text was only
flushed into the output at *conversion* points, so every skip advanced the
cursor with no flush and **silently deleted everything between the last
conversion and the last skip**.

It printed `converted 11, left alone 15` — plausible, cheerful — while:

| file | before | after |
|---|---|---|
| ContactsActivity.kt | 3069 | **1696** |
| PhoneActivity.kt | 2046 | **995** |
| PhotosActivity.kt | 2967 | **1076** |

The script was only ever run against a copy. It now has a flush-before-advance
invariant and a hard write guard that refuses to save a file that lost more than
a line per site. **Any tool that rewrites source in bulk needs a dry run and a
line-count guard, or it will eat the tree and report success.**

---

## 5. Motion: the Material ramp is gone

Recorded in full in `deliverables/m25`. The load-bearing finding is that **the
IPSW contains no motion data at all** — it is a firmware-only image with no
`Payload/`, and the harvest's 30,637 plists are `Info.plist`s whose 420
"spring/curve" hits are all `springboard` path fragments.

What replaced the Material ramp:

- the four **UIKit bezier control points**, transcribed
- `easingStandard` is now an **alias of the real `easeInOut`** instead of
  Material fast-out-slow-in, so the call sites that asked for "the iOS curve"
  stop getting Material
- **springs govern anything the user caused**, because since iOS 17 the default
  animation *is* a spring and velocity continuity cannot be expressed as a
  duration
- `springTo()` cancels the outgoing spring before starting the next, which is
  what preserves velocity

Three of the four documented `SpringSpec`s had damping values that did not match
their own stated character (`springDismiss` was **stiffer** than the default
while its comment said softer). All eight ratios are now recomputed and verified:

| spec | k | ζ | undamped period |
|---|---|---|---|
| `springSmooth` | 1066 | 0.995 | 0.192 s |
| `springSnappy` | 1581 | 0.905 | 0.158 s |
| `springBouncy` | 1206 | 0.705 | 0.181 s |
| `springStandard` | 131 | 0.830 | **0.549 s** |

`springStandard`'s period is the solved stiffness for the 0.55 s response both
sources agree on. The one contested parameter — SwiftUI's default damping, 0.825
vs 0.75 — is recorded as **both** constants rather than quietly resolved.

---

## 6. Documentation that was wrong

`AGENTS.md` claimed the graph-only check was `m -j1 out/soong/build.ninja`. That
file **does not exist** in this tree:

```
error: ninja: unknown target 'out/soong/build.ninja'
```

This tree suffixes everything with the product name — `combined-aosp_rock.ninja`,
`build.aosp_rock.ninja` (485 MB), `build.aosp_rock.0-9.ninja`,
`build.aosp_rock.incremental.ninja` (2.9 GB). The check is `m -j1 nothing`.
Corrected in `AGENTS.md` and in `m24`, which had printed the same bad command.

`AGENTS.md`'s "Other Known Gaps" also still claimed the SystemUI overlay was
"unstarted" while an earlier section described the takeover as done. Replaced
with a **measured** table, including the `Toast` count that turned out to be 0
(the one grep hit is the word inside `AppleuxToast`'s own docstring).

---

## Verified

```
build-emulator-apks.sh
  ==> design-system  OK 3.6M
  ==> performance    OK 3.7M
  ==> launcher       OK 12M     (366 res/raw .ogg, 0 .caf)
  ==> settings       OK 3.7M

launcher APK   res/raw count 366, .caf count 0, DEX references AppleuxSoundPlayer
AlertDialog.Builder   26 real sites -> 11 converted
check-token-drift.py  OK: 28 tokens match in both appearances
```

## Not done

- **11 of 26 dialogs** remain (8 `.create()`, 3 non-`EditText` `setView`).
- **33** `android.R.drawable` references and **6** `<SeekBar>` remain.
- **16** declared components still have no reference.
- Control Center is still orphaned; `toggleControlCenter()` still calls AOSP
  `expandSettingsPanel()`, and `AppleuxNotificationBus` still has no subscriber.
- Typography tracking is very likely ~60× too tight and is **unresolved** — it
  needs one measurement of real iPhone 11 text width against the emulator's.
- **`m SystemUI` has still never run**, so the entire m23 takeover remains
  statically verified and zero verified at runtime.