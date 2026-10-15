# Milestone M7b — AppleUX baked-image boot (2026-10-14)

## 1. Current state
- COMPLETED: own OS runs from the modified image. `osbuild/out/appleux-system.img`
  (4.4 GB) boots on the SDK emulator (AVD `appleux36`): 1 clean boot, adb
  `device`, AppleUX launcher/settings/systemui auto-installed from
  `/system/priv-app` at boot, AppleUX launcher set as default HOME
  (HOME key lands on it — screenshot `screenshots/appleux-baked-homekey-20261014-1015.png`).
  Verified boot ENFORCED with a regenerated hashtree (root f09970…).
- COMPLETED: all 5 emulator APKs build serially clean (`scripts/build-emulator-apks.sh`).
- COMPLETED: app-level verification on stock image (sibling pass): installs OK,
  settings cross-process wiring verified to exact expected colors, zero crashes.
- BLOCKED: full-device `bacon` (Soong analysis needs ~12 GB anon; OOM-killed
  twice on this 15 GB box — see `soong-unblock-log.md` §10). Needs serialized
  run or external builder.
- IN PROGRESS: Cuttlefish bring-up with prebuilt guest+host (build 16373615).
- IN PROGRESS: iOS 18 root-FS decrypt pipeline (IPSW downloading).

## 2. Architecture (who owns what)
- `appleux/design-system/` — tokens (Color/Motion/Type/Icon/Spacing/Sizing/Elevation),
  25 components, `AppleuxRuntimeConfig` (per-key ContentProvider reads).
- `appleux/performance/` — Adaptive Performance Controller (FrameTime/Pressure
  monitors, Q100→Q70 policy, MonitoringLevel L0–L3). Canonical thresholds in AGENTS.md.
- `appleux/systemui/` — service + shade + Control Center components.
- `appleux/launcher/` — grid, dock (first-4-alpha, persistence open), App Library,
  GestureHostLayout dispatch-touch observation (no overlay).
- `appleux/settings/` — catalog + `AppleuxConfigProvider`
  (`com.appleux.settings.config`, signature-guarded) as the cross-process store.
- `osbuild/` — rootless image surgery: inject-rootfs.sh (debugfs), assemble-image.py,
  avbtool.py; NEW: hashtree-regeneration flow (§5).
- `android-16/packages/apps/AppleUX*` + `frameworks/appleux/` — Soong wrappers for
  the same sources (analysis passes; device build pending §blocked).

## 3. Changed files (this session)
- `AGENTS.md` — real root `/home/elfakiris/OsProject`, disk table, Rock 4 GB
  device + 3 GB engineering budget, iOS-design directive, Soong/CF status.
- `tools/capture-evidence.sh`, `tools/capture-metrics.sh` — dead `/media` path → repo-relative.
- `android-16/vendor/lineage/build/soong/Android.bp` — export_cflags→cflags (4×).
- `android-16/device/xiaomi/rock/{Android.bp,aosp_rock.mk,AndroidProducts.mk,device.mk,rock-vendor.mk}` —
  header dedup, bp4a release wiring, namespace, IMS-blob disable, overlay path.
- `android-16/hardware/lineage/interfaces/{Android.bp,vibrator/1.0-lineage/Android.bp}` —
  soong_namespace, HIDL-vibrator disable.
- `android-16/vendor/lineage/kotlin/Android.bp` — compose-plugin disable (pending full prebuilts sync).
- `android-16/{frameworks/appleux,packages/apps/AppleUX*}/Android.bp` — dropped bogus `libs: android.frameworks`.
- `android-sdk/.../x86_64-appleux/{VerifiedBootParams.textproto (materialized: veritymode=enforcing, vbmeta size=512, digest=1d96…),kernel_cmdline.txt}`.
- `osbuild/out/appleux-system.img` — rebuilt: shrunk ext4 (242821 blk) + regenerated
  hashtree (no FEC) + flags-0 vbmeta; backup at `/tmp/opencode/ios-insp/appleux-system.bak.img`.
- `design-reference/ios27/` — SF Symbols 27 catalog (9524 symbols, 340 new), Xcode
  SDK color/font plists, online-notes.md (33-row token map).
- `cuttlefish/dl/` — guest+host build 16373615 (verified sizes/sha).
- `screenshots/appleux-baked-home{,key}-20261014-1015.png` — NEW proof shots.

## 4. Performance (measured, emulator/SwiftShader — NOT device numbers)
- Prior captures (SwiftShader, re-run, don't quote as stable): launcher p50 ~150 ms,
  settings p50 ~150 ms, ~100%/95% janky — software-GL artifact only.
- Boot: 1 clean boot, no loop; adb online; 0 com.appleux crashes in logcat sweep.
- No 3 GB-device numbers exist. Frame work on hardware pending CF/device.

## 5. Memory configuration
- No zRAM/LMKD changes yet (needs target kernel + measurements; charter §6).
- Rock tree today: density 420 confirmed; panel res/fps NOT in tree (no kernel DTS
  synced) — do not assume 90 Hz; verify on hardware/vendor before enabling.
- powerhint.json + thermal_info_config.json present (MediaTek dvfsrc/userspace gov).

## 6. Testing (commands run)
- `scripts/build-emulator-apks.sh` → 5× OK (2.8 MB each).
- Emulator matrix: boot-loop root-caused 4× (dead qcow2 backing → verity-restart →
  unknown-veritymode → stale-userdata cryptfs fail); each fixed and re-tested.
  Final: `emulator -avd appleux36 ... -writable-system`, 1 boot, adb device.
- `adb shell pm list packages | grep appleux` → launcher/settings/systemui.
- `cmd package set-home-activity com.appleux.launcher/.LauncherActivity` → Success;
  HOME key → AppleUX (dumpsys topResumedActivity + screenshot).
- CF: guest+host extracted; launch blocked stepwise (crosvm/qemukvm gate →
  capability-query shim via LD_PRELOAD → cvdnetwork chgrp via `sg`), retry in flight.

## 7. Compatibility
- API 36 (min 26), androidx closure vendored, debug.keystore signatures match
  across APKs (signature permission grantable). GMS untouched (charter: only if feasible).

## 8. Remaining work (prioritized)
1. CF launch → install APKs → charter §10 measurement battery on CF.
2. iOS-27 visual pass from `design-reference/` (squircle mask, SF-weight mapping,
   grouped Settings, switch color) + consume `reduce_motion` + dock persistence.
3. Bacon/device images via serialized or external full build (vendor blobs needed).
4. zRAM/LMKD/power tuning against real Rock kernel measurements.
5. Debloat audit vs `deliverables/m2/keep-remove-disable-list.md` (prior work).

## 9. Build instructions
- APKs: `source tools/envsetup.sh && bash scripts/build-emulator-apks.sh`
- Image: `osbuild` inject → shrink → `avbtool add_hashtree_footer` →
  `make_vbmeta_image` → `assemble-image.py` → update `VerifiedBootParams.textproto`
  (digest/size) — full command trail in session log; script it before next use.
- Boot: `emulator -avd appleux36 -no-snapshot -no-audio -gpu swiftshader_indirect -no-boot-anim -memory 2048 -show-kernel -writable-system`

## 10. Rollback
- Image: `/tmp/opencode/ios-insp/appleux-system.bak.img` (pre-hashtree) — copy back
  to `osbuild/out/appleux-system.img`; textproto: restore stock digest/size/params
  from `../x86_64/VerifiedBootParams.textproto` (delete materialized copies to
  restore symlinks if desired).
- AVD qcow2s: originals in `/tmp/opencode/ios-insp/avd-bak/`.
- Soong/tree edits: all commented with `NOTE (AppleUX, 2026-10-14)` + rationale;
  revert per `soong-unblock-log.md` §1–9.

## 11. Cuttlefish validation (2026-10-14, same day)
- Guest: aosp_cf_x86_64_only_phone build 16373615 (Android 17/SDK 37, userdebug),
  3072 MB, 2 CPUs, qemu_cli + KVM. Host bring-up needed: vhost ACLs (sudo),
  cvdnetwork group, pre-created taps (cvd-etap-01/cvd-mtap-01), LD_PRELOAD shim
  for missing cuttlefish-common capability script (see §6 notes).
- Guest boots to BOOT_COMPLETED (Bluetooth dep missing — cosmetic).
  adb via manual socket_vsock_proxy bridge (run_cvd-spawned proxies died).
- AppleUX launcher/settings/systemui installed OK; launcher set as HOME.
  Screenshot: `screenshots/appleux-cf-home2-20261014-1120.png` (720x1280).
  NOTE: CF icons render squarish (legacy masks) vs circular on Pixel emulator —
  icon-mask normalization is an open visual-pass item.
- §6A baseline on 3 GB guest: MemTotal 2.95 GB, MemAvailable 1.76 GB,
  Used 1.55 GB, ZRAM 36 MB phys/86 MB swapped (2.2 GB pool), status normal.
  Launcher PSS TOTAL 64 MB (40 MB private dirty); system_server 219 MB.
- First CF frame numbers (STATIC screen, 8 frames, software GL — NOT device
  numbers, do not quote): 75% janky, p50 250 ms. CF panel runs 75 Hz here.

## 12. Visual pass + widgets (2026-10-14, same day, CF-verified unless noted)
- iOS wallpaper pair live: Safari Blue (light) + iOS17 red Dark, auto-swapped
  with theme (LauncherActivity wallpaperView; grid transparent).
- Token corrections from online-notes.md Part B (HIGH/MED only): grouped/secondary
  bg hexes, largeTitle/title1/title2 weight 200/300->400, headlineEmphasized
  700->600, +easingIOSDefault (0.42,0,0.58,1). Pink/teal/cyan/mint + spring
  retune + Dynamic-Type left UNVERIFIED per research flags.
- Switches green (#34C759) in Settings + AppleuxToggle default; SeekBars blue.
- Glass: AppleuxGlass engine (API31+ RenderEffect, tint-only fallback),
  user-adjustable 0-100 via new Settings slider (shared provider flag
  appleux_glass_intensity), applied to dock glass bar + library scrim.
  Glass-bar lesson: RenderEffect on a container blurs children too — blur lives
  on a background-only sibling sized to the icon strip (DockView.syncGlassSize).
- Dock layout lesson: match_parent glass child inflates wrap parents to
  fullscreen (ghost icons); glass bar now mirrors the strip (bottom-centered).
- 46 real Apple SF Symbols vectors (wght 400, fontTools CFF2 pipeline) shipped
  as sf_*.xml + AppleuxIconRegistry.getDrawableRes() with hand-path fallback.
- Squircle icon mask (~28% rounded-rect clip, AppleUX-chosen) on AppIconView.
- Widget pack: Clock/Battery/Calendar providers (S/M/L, zero polling, zero new
  permissions) + launcher host (spans, gallery on root long-press, persistence,
  battery push). Long-press moved to GestureHandler (RV long-click unreliable).

## 13. Image v2 + RRO + widgets (2026-10-14)
- scripts/rebuild-image.sh: full pipeline scripted (extract -> inject ->
  e2fsck -> shrink/grow -> hashtree -> vbmeta -> assemble -> VerifiedBootParams).
  scripts/build-rro-apks.sh: RRO builder (own GEN/OUT, testkey-signed).
- RRO v1 (com.appleux.overlay.frameworks): accent_device_default light/dark ->
  iOS blue, dialog_corner_radius 2dp -> 13dp. Install-time signature lesson:
  platform-target RROs need the platform key (testkey != CI keys, so adb
  install path is dead on CF); preinstalled /system/overlay is the vehicle.
  SELinux lesson: every created dir needs explicit uid/gid/mode/label
  (parent /system/overlay was untraversable without emit_inode).
- Widget pack verified: gallery on root long-press, system bind consent,
  Clock pinned live, persistence + redraw across force-stop, dynamic dock
  clearance, themed text colors. Touch lesson: RecyclerView long-click is
  unreliable -> GestureHandler owns long-press (500ms/24px on UP).
- Grid lesson: match_parent child inflates wrap parents fullscreen (ghost
  icons); glass bar mirrors the strip (DockView.syncGlassSize).

## 14. RRO verdict (2026-10-14)
- com.appleux.overlay.frameworks (accent -> iOS blue, dialog radius 13dp)
  injects cleanly to /system/overlay with correct labels and boots fine, but
  stays INERT: framework-res declares no <overlayable> groups, so a
  non-platform-signed overlay targeting `android` is rejected even
  preinstalled. CI emulator/CF images use Google keys we don't have.
- Correct home for the pack is the bacon ROM (platform = our keys, like the
  existing *OverlayRock entries in device.mk). Parked, not deleted.
- Lesson: verify signature-fit BEFORE building overlay UX around it.

## 15. Serial pass: intent defaulting, debloat, automation (2026-10-14)
- ACTION_SETTINGS intent-filter live on AppleUX Settings (resolver-table
  verified); sole-candidate-wins is the ROM endgame (no chooser hacking).
- Debloat v1 (scripts/debloat-disable.sh, reversible): 4 pkgs disabled on the
  emulator (traceur, bookmarkprovider, dreams, livepicker); launcher+settings
  verified fine, Free RAM stable. ROM-side removals wait for replacements.
- scripts/cf-up.sh: one-command CF bring-up (taps check, shim, sg, launch,
  vsock proxy, adb). scripts/rebuild-image.sh + build-rro-apks.sh complete
  the automation set.

## 16. SystemUI bring-up (2026-10-14, building)
- Audit: shade/CC views were constructed but never attached; tiles had empty
  handlers; notifications were hardcoded demo data.
- Rewrite: WindowManager APPLICATION_OVERLAY attach (SYSTEM_ALERT_WINDOW),
  foreground service (specialUse), real tile actions (Wi-Fi/BT panels,
  airplane w/ privileged+fallback, torch, DND w/ grant flow, brightness cycle),
  state refresh via ControlCenter.setTileActive (new API), BOOT_COMPLETED +
  launcher autostart, bogus BIND_SYSTEM_UI_SERVICES permission removed.
- Documented platform boundary: stock status bar NOT replaced (STATUS_BAR is
  platform-signature; bacon-ROM item). Real notifications (listener service)
  and lock overlay are the next two increments.

## 17. One-VM rule + cf-up.sh (2026-10-14)
- This box cannot host the emulator and Cuttlefish together with builds
  running (15 GB; CF qemu died twice under combined load, no OOM log —
  run_cvd cleans up after guest death). SERIALIZE: one VM at a time.
- scripts/cf-up.sh tested path: taps -> shim -> sg -> launch -> vsock proxy
  -> adb. CF guest boots to BOOT_COMPLETED; adb via manual proxy
  (run_cvd-spawned proxies die).

## 18. Control Center entry + FGS rules (2026-10-14)
- iOS zones: swipe down top-right 40% toggles CC (SHOW/HIDE_ALL), elsewhere
  closes library (GestureHandler.onSwipeDown now carries xFraction).
- FGS lesson: `am startservice` from shell is BLOCKED (background-uid rule);
  explicit startService from the foreground launcher is allowed. Test triggers
  must go through a foreground caller, never bare shell am commands.
- Close path also hides CC (single dismiss gesture).

## 19. Gesture-zone + CC trigger lessons (2026-10-14)
- Top-edge swipe-down belongs to the platform shade window on stock images;
  an in-activity gesture can never reliably claim it. iOS zones need either
  platform ownership (ROM stage) or a sanctioned trigger.
- AppleuxControlTileService (QS tile, BIND_QUICK_SETTINGS_TILE) is the
  working trigger: tap -> unlockAndRun -> TOGGLE_CONTROL_CENTER to our
  foreground service. Test preset via `settings put secure sysui_qs_tiles`.
- Fixed en route: AppleuxIcon.iconSize NPE on pre-attach views (tiles are the
  first programmatic construction path; grid cells always had params).

## 20. CC trigger architecture (2026-10-14)
- Verified: platform shade owns all top-edge swipes on stock images; no
  in-activity gesture can claim the iOS zones there. ROM stage restores zones
  with platform ownership.
- Working triggers (all verified or verifying): QS tile (bound, paged),
  widget tap -> provider onReceive -> foreground-service SHOW (user tap =
  allowed caller), launcher zone swipe (works below the shade region).
- Custom QS tile renders only after SystemUI binds it (paged last); tile
  visuals MUST be set in onStartListening or the tile stays blank.

## 21. Overlay black-screen isolation (2026-10-14)
- Symptom: systemui overlay windows attach (dumpsys confirms) but render pure
  black; killing the service restores the launcher underneath. No crash.
- Isolation: red canary TextView as first CC child — if it draws, layout and
  attach work and the fault is in content components (header/tiles); if not,
  the fault is window/surface-level.

## 22. Overlay content lessons (2026-10-14)
- Black fullscreen overlay + red canary drawn = attach/layout fine, content
  at fault. Canary-first bisects window-level vs content-level in one cycle.
- GridLayout children with width/height 0 collapse to 0x0 even with specs;
  tiles need WRAP_CONTENT. (Latent since Oct 3: tiles were never displayed.)
- AppleuxControlCenterHeader is an empty stub (padding only) — needs real
  content in the visual pass.

## 23. Control Center live (2026-10-14, CF-verified with screenshot)
- Widget tap -> provider onReceive -> foreground-service SHOW works end to end.
- Frosted card + dim scrim + 2-col grid + live states (Bluetooth blue = really
  on) all render; tiles use real Apple vectors.
- Bug chain closed: service-theme sync (was black), tile 0x0 LP, NPE on
  pre-attach LP, missing QS visuals. Remaining polish: tile widths (minWidth
  fix building), header content, brightness/dnd/flashlight live-toggle proof.

## 24. Tile label truncation (2026-10-14)
- "Wi-/Blu-/Airp-" was NOT text ellipsis but VIEW clipping: the tile's inner
  vertical container wrapped to the icon width (44px), so full-width labels
  were cut mid-glyph. Fix: container MATCH_PARENT + centered text gravity.
  Rule: in centered vertical stacks, text views must be MATCH_PARENT, or the
  narrowest sibling dictates everyone's width.

## 25. Control Center verified live (2026-10-14, CF screenshots)
- Frosted card + dim + 2-col grid, full labels, real Apple vectors.
- States live (Bluetooth blue = really on). Tap Wi-Fi tile -> official
  Settings.Panel opens (topResumedActivity proof). No fake switches.
- Entry points working: widget tap, QS tile (bound), launcher top-right zone
  (below shade region). Top-edge zones stay platform-owned on stock images.

## 26. Single-source-of-truth for panel state (2026-10-14)
- Launcher-side mirrored flags (controlCenterShown/shadeShown) desync across
  process restarts and left the panels stuck. Rule: the SERVICE owns toggle
  state; callers send TOGGLE (self-correcting) or absolute HIDE_ALL.
  Deterministic SHOW/HIDE from a stateless caller is worse than toggle.
- Companion mystery solved: service lastActivity only moves on (re)starts —
  it is not a delivery receipt. Steady-state starts do not touch it.

## 27. Fullscreen overlay touch ownership (2026-10-14)
- A fullscreen overlay window CONSUMES all touches inside its bounds, even
  with a non-clickable root: unhandled touches are dropped, NOT forwarded to
  lower windows. (NOT_TOUCH_MODAL only lets outside-bounds touches through.)
- Consequence: every "dead touch" mystery of the day was an open overlay
  eating gestures, never a dispatch bug. Panels must be modal BY DESIGN:
  background tap dismisses (CC + shade both), never transparent pass-through.
- Corollary for testing: always account for open overlays before concluding
  a gesture path is broken.

## 28. Gesture forensics so far (2026-10-14, unresolvedchanical)
- Swipe-up (library) and swipe-down-close verified working repeatedly.
- Swipe-down zones (CC right / shade left) fire unreliably: no zone log, no
  delivery, yet the same handler evaluates other directions. Widget-area and
  empty-area down-swipes both affected at different times.
- Working triggers (all proven): launcher autostart (plain intent), widget
  tap via provider onReceive, QS tile via unlockAndRun. Failing: zone
  intents from the activity. Same API, same target — caller-side or
  timing-sensitive, exact cause still open.
- Added second QS tile (Alerts) so the shade no longer depends on zones.

## 29. Animation-vs-reliability (2026-10-14)
- Slide animations keyed on measured height break for never-laid-out GONE
  views (height 0 -> 0 distance, stuck translationY). Panels now snap
  visibility directly; motion returns only after show/hide is bulletproof.
- Rule: never derive animation endpoints from live measurements on views
  that may never have been laid out.

## 30. Clean-room rule + image v3 (2026-10-14, late)
- 4.5-hour CF session accumulated too much stale state (installs, force-stops,
  reboots, wedged keyguard) to trust trigger forensics. Rule: verify panel
  triggers only on fresh boot + fresh install.
- Open mystery: a downward swipe from the status bar opened OUR LIBRARY
  (up-action). Suspect swipe coordinate inversion in this CF/adb-input path;
  clean-room test will confirm or kill it (zone log + screenshot).
- Image v3 rebuild in progress: current APKs (glass, widgets, squircle,
  vectors, CC, shade pipeline, tiles) + RRO baked with fresh hashtree.

## 31. Stale /data/app shadows the baked /system APK (2026-10-14, late)
- The AVD's `image.sysdir.1 .../x86_64-appleux/system.img` is a SYMLINK to
  `osbuild/out/appleux-system.img` — verify this whenever "the baked image
  has no effect". Here the image WAS live.
- Real cause: `adb install -r` from earlier sessions left `/data/app/...`
  copies with lastUpdateTime 09:56, which take precedence over
  `/system/priv-app`. The device then ran an OLD build while the image on
  disk was new — the exact opposite of what the screen suggested.
- RULE: after a rebuild, check `dumpsys package <pkg> | grep codePath`.
  `/data/app` => stale install wins, image ignored. Confirm with
  lastUpdateTime vs the APK mtime.
- Verification harness must be a WIPED device (fresh userdata, no
  writable-system qcow leftovers), otherwise installs accumulate and
  every test result is ambiguous.
- Also: `-writable-system` qcow2 overlays accumulate per-AVD. Delete
  system.img.qcow2 before a clean boot or the old overlay's state persists.

## 32. Fullscreen panel dead end: swipe-up dismiss (2026-10-15)
- §27 fixed "tap dead on open panel" with a background-tap dismiss, but a
  SWIPE never fires onClick (exceeds touch slop), so an open panel still
  swallowed every gesture with no way out — including the very swipe that
  should have closed it. This is why "swipe-down does nothing" looked like a
  delivery failure: the panel was already open and ate it.
- Fix matches iOS: swipe UP dismisses. `AppleuxPanelDismissHandler` observes
  DOWN/MOVE (density-correct slop, mostly-vertical, upward) and never
  consumes, so tiles and notification rows keep working. Attached to both
  panels; all closes route through `hideControlCenter()` /
  `hideNotificationShade()` so state has one owner (§26).
- LESSON: when debugging "gesture does nothing", FIRST check whether an
  overlay is open. It is cheaper than tracing dispatch, and it was the
  second time fullscreen-overlay ownership cost hours.

## 33. Cold-boot cost on the emulator (2026-10-15)
- Fresh userdata (no stale /data/app installs) + SwiftShader at 1080x2400 =
  >20 min to adb, qemu pegged at 100% CPU but still reading the image.
  -wipe-data fails here: it deletes userdata-qemu.img and cannot recreate it
  on this AVD (custom sysdir). Delete the .qcow2 overlay instead.
- Budget ~25 min for a clean-room emulator boot; verify progress with
  /proc/<qemu-pid>/io rchar deltas rather than assuming a hang.

## 34. ROOT CAUSE of "gesture does nothing": SYSTEM_ALERT_WINDOW appop (2026-10-15)
- The zone handler was never broken. It logged correctly
  (`AppleuxZone: swipe-down frac=0.2777 lib=false`), the intent was delivered,
  and the service handled it. `wm.addView` threw
  `SYSTEM_ALERT_WINDOW: ...permission` every time, so the panels NEVER
  existed and every toggle was a silent no-op.
- The appop is runtime state, NOT baked into the image: on CF it had been
  granted in an earlier session and survived; on a clean boot it is
  `default` and rejects. That is exactly why the same code "worked" on CF
  and "didn't" on the emulator.
- DIAGNOSTIC ORDER (cost-ordered, use next time):
  1. `appops get <pkg> SYSTEM_ALERT_WINDOW`  <- checks THIS first
  2. `logcat | grep "overlay attach needs"`   <- confirms the throw
  3. `dumpsys window windows | grep mOwnerUid` <- are the overlay windows there
  4. only then trace intents/dispatch
- A silent catch around `addView` hid the failure for hours. Overlay attach
  failures MUST be visible (log + user-facing state), never swallowed.

## 35. Verified on clean boot, baked image, 1080x2400 (2026-10-15)
- codePath=/system/priv-app for launcher/settings/systemui (no /data/app
  shadowing) — the baked image is authoritative.
- Notification shade shows the REAL feed via NotificationListenerService
  (posted "Clean Room", plus live platform notifications) with Clear All.
- Swipe-up dismiss closes the shade and returns to the launcher (§32).
- Emulator boot needs: appops allow + notification listener grant. Neither is
  in the image; a test plan must grant both explicitly.

## 36. weight=1 inside a FrameLayout is inert (2026-10-15)
- AppleuxNotificationShade added header, RecyclerView and Clear All as
  siblings of a FrameLayout. RecyclerView carried layoutParams.weight=1,
  which only resolves inside a LinearLayout, so all three stacked at the
  same origin: the date/time rendered straight through the first
  notification row.
- The symptom (text overlapping text) reads like a z-order/alpha problem,
  but it is a container-type problem. RULE: if children need weights, the
  direct parent must be a LinearLayout — check the parent before tuning
  elevation, alpha or bringToFront.
- Shade is now a vertical column: header (wrap) / list (0dp, weight 1) /
  Clear All (wrap footer). Clear All was a BOTTOM-gravity FrameLayout
  overlay and could sit on top of the last row; as a footer it cannot.

## 37. Emulator prep is a script, not tribal knowledge (2026-10-15)
- `scripts/prep-emulator-grants.sh` grants SYSTEM_ALERT_WINDOW and the
  notification listener on a freshly booted device. Both are runtime state
  and neither is in the image, so every clean boot must run it.

## 38. Fullscreen overlays and the display cutout (2026-10-15)
- With FLAG_LAYOUT_IN_SCREEN alone the panel window was still cutout-inset:
  1080x2209 on a 1080x2400 display, leaking the launcher through the top
  band. Fix: layoutInDisplayCutoutMode=ALWAYS + explicit TOP|START gravity.
- A window requesting MATCH_PARENT is not proof it covers MATCH_PARENT —
  check `Requested w/h` in dumpsys when a panel "leaks" the layer beneath.

## 39. RenderEffect is NOT frosted glass (2026-10-15)
- AppleuxGlass applies a RenderEffect, which blurs a window's OWN drawables.
  It cannot touch the layer behind the window. On a fullscreen translucent
  overlay the result was the launcher showing straight through the Control
  Center card, with launcher labels colliding with tile labels.
- The platform primitive for the iOS "Liquid Glass" material is
  FLAG_BLUR_BEHIND + LayoutParams.blurBehindRadius (API 31+), which blurs
  the content BEHIND the window. Added to the shared overlayParams so both
  panels get it.
- blurBehindRadius is in PIXELS, so it must be scaled from dp or the
  material will look different on the 420 dpi POCO M5 vs the emulator.
- RULE: any glass surface over live content needs blur-BEHIND. A RenderEffect
  alone only ever frosts the surface's own fill — never what is under it.
