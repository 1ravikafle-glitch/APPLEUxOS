# AppleUXOS — BUILD AND TEST STRATEGY

> **Status: DRAFT v0 — NOTHING HAS BEEN BUILT OR RUN.**
> Commands below are what to run during discovery; results must be pasted into `docs/appleux/logs/` and summarized here. No claim of success is valid without a log.

## 1. Evidence levels (kept separate)
1. Static source inspection
2. Successful compilation
3. Emulator test
4. Physical-device (POCO M5) validation
A feature is TESTED only at the level actually run, and the level must be recorded.

## 2. Host environment discovery (read-only, run first)
```
free -h; swapon --show; nproc; df -h; lscpu | head -20
uname -a; cat /etc/os-release
java -version; python3 --version; git --version; repo --version
cd <source-root>; repo info; repo status; du -sh .
ls device vendor kernel 2>/dev/null
```
Record: RAM, swap, thread count, free disk, source size, tool versions.

## 3. Build sizing rules for the 16 GB host
- Do not assume unlimited memory. Choose `-j` from measured RAM, swap and observed memory use during a build; start low and raise only if no OOM or heavy swapping occurs.
- Ensure adequate swap (size from measurement) on the NVMe SSD; monitor with `free -h` during builds.
- Use ccache and record hit rates; keep `out/` on the SSD.
- Prefer incremental module builds (`m <module>`) over full builds once a base exists.
- Never run a full build during the discovery phase.

## 4. Build targets
1. Emulator first: Cuttlefish target (`aosp_cf_*` lunch target, exact name verified in tree), if host and tree support it.
2. Device: `rock` product, only after M8 prerequisites (verified device, vendor and kernel trees; recovery plan).
3. Product configs: Tier A (3-4 GB) and Tier B (2 GB, low-RAM) variants.

## 5. Reproducibility
- Pin the manifest revision; record branch, manifest hash and local patches.
- Build script committed to the repo; clean build twice and compare outputs where practical.
- Record build time, peak RAM and disk use.

## 6. Test matrix
| Area | What to verify | Level |
|---|---|---|
| Build | Clean and incremental builds succeed | 2 |
| Boot / setup | Boots, setup completes, reaches home | 3, 4 |
| UI consistency | Screen-by-screen vs iOS 27 reference | 3, 4 |
| Navigation / gestures | Thresholds, app compatibility | 3, 4 |
| Notifications | Delivery in foreground, background, doze | 3, 4 |
| Permissions | Grant, deny, one-time, revoke | 3 |
| Accounts / sync | Where available | 3, 4 |
| Install / update | Install, fail, update, uninstall | 3 |
| Network / Bluetooth | Connect, disconnect, airplane mode | 4 |
| Lock / auth | PIN, biometric, timeout, keystore | 3, 4 |
| Accessibility | TalkBack, font scale max, contrast, reduce motion | 3, 4 |
| App compatibility | Sample of popular third-party apps | 4 |
| Crash handling | Forced crash, ANR, SystemUI restart recovery | 3, 4 |
| Security | SELinux enforcing, no new permissive domains, Verified Boot state documented | 3, 4 |
| Memory pressure | Low-memory behavior, LMKD kills, Tier B | 3, 4 |
| Low storage | Storage-full flows | 3, 4 |

## 7. Performance measurement (POCO M5, both tiers)
Tools: Perfetto, systrace, `dumpsys gfxinfo`, `dumpsys meminfo`, `dumpsys SurfaceFlinger`, simpleperf, `am start -W` for launch time, boot time from logs.
Metrics and targets (targets set after baseline; record baseline first):
| Metric | Method | Baseline | Target |
|---|---|---|---|
| Janky frames % in core flows | gfxinfo / Perfetto | TBD | TBD |
| p90 / p99 frame time | Perfetto | TBD | TBD |
| App cold / warm start | `am start -W` | TBD | TBD |
| Launcher return time | Perfetto | TBD | TBD |
| Gesture latency | high-speed capture or Perfetto | TBD | TBD |
| Boot time | logcat/boot timestamps | TBD | TBD |
| Idle free memory | meminfo | TBD | TBD |
| Cached apps under typical use | dumpsys activity | TBD | TBD |
| Idle and scenario battery draw | batterystats | TBD | TBD |
Process: measure stock Android baseline on the same device first, then AppleUXOS builds; compare and record the gap. No performance claim without a measurement and method.

## 8. Tuning workflow
Policy, safety guardrails and per-subsystem levers: `PERFORMANCE_AND_POWER_TUNING.md`. Results: `TUNING_LOG.md`. Use Fixed Performance Mode for repeatable benchmarks and normal dynamic mode for real-world numbers.

Change one variable at a time (LMKD thresholds, zRAM size/algorithm, ART filters, scheduler/uclamp, Power HAL boosts, effect quality), re-measure, keep or revert, log in `docs/appleux/TUNING_LOG.md`.

## 9. Device-flash safety (M8 only)
- Verify bootloader state, backup of stock partitions, working recovery and stock-firmware restore path before any flash.
- Never flash during initial phase.

## 10. Reporting rules
- Every test result: date, build ID, device/emulator, tier, steps, outcome, log path.
- Failures include exact error, source path, diagnostic attempted, next safe step.

---

## ACTUAL STATE (2026-10-19) — proven invocations and hard rules

Read `PREVIOUS_WORK_DONE.md` §3 first — the 12-run saga. What is PROVEN:

- **Soong SystemUI build works**: `bash ~/OsProject/build-systemui-direct.sh`
  — direct shell (NO systemd-run, NO cgroup; every capped attempt died to a
  kill, never exhaustion), `GOMEMLIMIT=8GiB` written INSIDE the wrapper.
  ~32 min incremental; whole-tree analysis ~45 min at ~8–12.6 G RSS.
- **Host facts (measured)**: 16 GB RAM + 50 GB swap, 8 threads, Zorin.
  Emulator must be dead before building. Never run two builds together.
- **SDK route**: `scripts/build-emulator-apks.sh` → 14 APKs (aapt2/kotlinc/
  d8/apksigner, no Gradle), verified on the API 36 emulator.
- **Soong env truth**: `systemd-run` does not inherit parent env; soong_build
  runs under `env -i`. Env vars for the build must be inside the wrapper.
- **Cuttlefish staging (working)**: host pkg `/tmp/opencode/cvd-host`, stock
  image `/tmp/opencode/cf-img`, HOME `/tmp/opencode/cvd-home`,
  `sudo touch /manager.sock` for the crosvm support gate, sandbox layout
  `instances/cvd-1/logs/launcher.log` must pre-exist (O_APPEND, no O_CREAT).
- **THE runtime rule**: our 16-built SystemUI cannot run on the 17 guest
  (`NoSuchMethodError sysuiLargePerfettoShmemBuffer`). Test matrix MUST use a
  framework-matched guest: build `aosp_cf_x86_64_only_phone` from our tree, or
  the API 36 SDK emulator for app-level APKs.
- **Test evidence discipline**: screenshots must be pixel/MD5-analysed
  (byte-identical to home = nothing opened; 100% black + focus=null = crashed/
  degraded). Crash verdicts require the named deepest `Caused by:` line.
- **Repo safety**: `.gitignore` ignores all of `android-16/` (use
  `git add -f` for new files); `frameworks/base` is a nested git repo.