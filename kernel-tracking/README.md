# kernel-tracking -- MT6765V (Helio G99) kernel performance hypotheses

**Target device:** POCO M5 / Redmi 11 Prime 4G, MediaTek Helio G99
(MT6765V), 4 GB RAM, 64 GB storage.

---

## THE ONE THING TO READ FIRST

**Nothing in this directory has been built, booted, or measured.**

There is no device here. There is no flashing path. There are no baseline
measurements from the target. Consequently:

- There is **no** benchmark number, frame-rate figure, boot-time figure,
  memory figure or battery figure anywhere in these files.
- Every patch is labelled `EXPERIMENTAL / UNVERIFIED`.
- Every patch carries a written rationale for why it is *expected* to help,
  derived from reading kernel source, plus the specific measurement that
  would confirm **or refute** it.
- Some of those rationales may simply be wrong. A source-reading argument
  produces a hypothesis, never a result.

Producing confident performance numbers here would be fabrication, and any
number that did appear should be treated as a bug in the process rather than
as a result. `VALIDATION.md` exists to turn these hypotheses into knowledge,
or to refute them.

---

## What was acquired

| | |
|---|---|
| Repository | `https://github.com/oppo-source/android_kernel_oppo_mt6765` |
| Branch | `oppo/mt6765_u_14.0_oppo_a77` |
| Commit | `9b1c9a531dd08e68bf778dcf7ed13f811ec1f173` |
| Commit date | 2023-12-13 |
| Commit subject | `Synchronize code for OPPO CPH2385_14.0.0.110(EX01) Based on MTK release TAG:t-alps-release-s0.mp1.rc-V14.75` |
| Kernel version | Linux **4.19.191** |
| Clone | blobless shallow (`--depth 1 --filter=blob:none`), sparse worktree |

Cross-check tree, used only to corroborate baseline config values:

| | |
|---|---|
| Repository | same |
| Branch | `oppo/mt6765_r_11.1.4_a16` |
| Commit | `220d8e1a728dd8a4d2f25e8aadb66be6f92b440a` |
| Config used | `arch/arm64/configs/oppo6765_defconfig` |

### Why this tree

MediaTek's own `opensource.mediatek.com` is not a browsable Git remote that
can be cloned non-interactively, and the `mtk-android-kernel` repository is
multi-gigabyte. This OPPO opensource mirror is a genuine vendor release of
MTK code for exactly this SoC (`t-alps-release-s0.mp1.rc-V14.75`), carries an
explicit `CONFIG_MACH_MT6765=y` defconfig, and could be acquired blobless at
a workable size.

### Why a cross-check tree

This is an OPPO-tuned tree carrying a large amount of `CONFIG_OPLUS_*`
product tuning. A single defconfig therefore cannot be assumed to reflect
anything intrinsic to MT6765. The rule applied throughout: **a baseline value
had to appear in two independent MT6765 defconfigs before any patch was
allowed to assert it.** That test is what excluded several changes (see
`REJECTED.md`).

### This is a separate acquisition from the AOSP sync

`../android-16` contains **no kernel tree**. `kernel/` there holds only
`configs/`, `prebuilts/` and `tests/`; there is no `kernel/common`, no
`kernel/mediatek`, no `device/mediatek`. Obtaining and integrating this tree
is a distinct piece of work from the AOSP sync and is not addressed by these
patches.

---

## What was found

The baseline defconfig is:

```
arch/arm64/configs/k65v1_64_bsp_defconfig      (749 lines)
    CONFIG_MACH_MT6765=y
    CONFIG_MTK_PLATFORM="mt6765"
    CONFIG_ARCH_MTK_PROJECT="k65v1_64_bsp"
```

`k65v1` is MediaTek's project covering MT6761/6763/6765 -- the Helio G99
family. `oppo6765_defconfig` on the a16 branch is the per-chip equivalent.

A copy is at `config/baseline-k65v1_64_bsp_defconfig`, with the cross-check
at `config/oppo6765_defconfig.a16-crosscheck`.

Important caveat: this is a **reference baseline for the SoC**, not the stock
POCO M5 configuration. Where the target's own DTS or stock config differs,
the target wins.

---

## What is proposed -- six .config fragments

All are in `config/fragments/`, ordered by ascending risk in `patches/series`.

| ID | Area | Change | Risk | One-line rationale |
|---|---|---|---|---|
| P01 | I/O | default scheduler CFQ -> deadline | low | Both `default y` in Kconfig and the choice defaults to CFQ; CFQ keeps per-process service trees, deadline is described in the same file as "simple and compact". |
| P02 | Memory | `CONFIG_NR_CPUS` 64 -> 8 | low | arm64 Kconfig defaults NR_CPUS to 64; the DTS has exactly 8 cpu nodes (cpu0-cpu7). Per-CPU arrays and possible-CPU masks scale with it. |
| P03 | Sched | drop `CONFIG_SCHED_AUTOGROUP` | low | On in both defconfigs; a desktop login-session feature. Android groups via cgroups/cpusets/SCHED_TUNE/uclamp instead. Expected small. |
| P04 | Power | drop `powersave` + `conservative` governors; pin zram writeback off | low | schedutil is already the default and `CONFIG_MTK_CPU_FREQ=y`; removing the extra governors closes an unintended escape hatch. |
| P05 | Build | `CONFIG_LTO_THIN=y` | **high** | LTO is off in both defconfigs but `ARCH_SUPPORTS_THINLTO` is selected. Expect vendor-module link failures. |
| P06 | Sched | drop `CONFIG_MTK_SCHED_TRACERS` | **gated** | Instrumentation in the scheduler hot path -- but a mandatory `grep` gate must pass first or the patch is dropped. |

Each fragment header states: what it changes, the baseline source evidence
with `file:line`, why it should help on a 4 GB MT6765V, the exact measurement
that verifies or refutes it, and its risk.

### Deliberately absent

These are forbidden by the project charter because they produce misleading
benchmarks or a worse device:

- **Anything that removes or weakens thermal throttling.** A number obtained
  with throttling disabled describes a device that does not exist in the
  hand, and repeated no-throttle benchmarking makes later real behaviour
  *worse*.
- **Anything that disables or weakens the lowmemorykiller/lmkd path.** On
  4 GB, aggressive reclaim and killing is what keeps the device alive;
  softening it converts smooth degradation into user-visible OOM kills.
- **Anything that forces maximum clocks.** Benchmarking a clock-pinned phone
  measures a part with no battery, no skin temperature and no thermal
  ceiling.

The patch set contains none of these, and none should be added later. See
`REJECTED.md` section (A) for the full reasoning.

Also investigated and rejected on source evidence -- KSM, THP, ZSWAP, BFQ,
Kyber, mq-deadline, CMA sizing, 16 KB pages, stripping ftrace/kprobes,
NUMA, debug-option strips. Notably **`CONFIG_CMA_SIZE_MBYTES` does not exist
in this tree at all**, so the usual "CMA sizing" tuning patch would be a
silent no-op here. See `REJECTED.md` section (B).

---

## Directory layout

```
kernel-tracking/
  README.md                         <- you are here
  REJECTED.md                       what was excluded, and why
  VALIDATION.md                     how to measure on a real POCO M5
  config/
    baseline-k65v1_64_bsp_defconfig     the baseline, copied verbatim
    oppo6765_defconfig.a16-crosscheck   cross-check defconfig
    BASELINE_COMMIT.txt
    fragments/
      01-io-sched-deadline.config
      02-mem-nr-cpus-8.config
      03-sched-no-autogroup.config
      04-cpufreq-governors-minimal.config
      05-lto-thin.config
      06-sched-no-mtk-sched-tracers.config
  patches/
    0000-MT6765V-experimental-perf-cover.txt
    series
  notes/
    baseline-evidence.md            every grep and file:line behind every claim
    repro-grep-evidence.sh          re-runnable proof for all of the above
  src-oppo-mt6765/                  blobless shallow clone (sparse worktree)
```

---

## How to reproduce the evidence

```bash
bash notes/repro-grep-evidence.sh
```

This re-runs every grep quoted in the patch headers against the clone and
prints PASS/FAIL per claim. If a claim fails, that patch's rationale is not
supported and the patch should be dropped.

---

## What hardware access is required to promote any of this to verified

1. **A physical POCO M5 / Redmi 11 Prime 4G.** Nothing else substitutes.
   An emulator, a cloud instance or a different MTK device does not test this
   kernel on this hardware.
2. **An unlocked bootloader with working `fastboot`**, and a verified
   recovery route. `VALIDATION.md` section 9 must be satisfied *before* the
   first flash, including pulling `boot` off the device and storing the
   official stock image.
3. **A working baseline build.** Measure the unmodified kernel first and
   collect the entire battery in `VALIDATION.md` section 3. There is no
   delta without a baseline.
4. **A repeatable workload and a noise floor.** Every reported number needs a
   baseline, a sample count, and the noise floor it was measured against.
   A bare percentage is not a result.
5. **Controlled thermal conditions.** If the thermal profile differs between
   runs, the comparison is void. Check `VALIDATION.md` section 7. The fix is
   better methodology -- never removing throttling.
6. **Time.** Some of these measurements are slow: 10 cold boots per build,
   3 overnight drain runs per build, and thermal soak periods. Budget days,
   not hours.

### Minimum bar

Not all six patches need to pass. "Some confirmed, some refuted, some
inconclusive" is the realistic best outcome and is publishable. So is "all
inconclusive -- these changes are below the measurement noise floor and
should not ship."

What is **not** acceptable is shipping this as "optimised" or
"performance-improved" on the strength of the written rationale alone. The
rationale is a hypothesis. `VALIDATION.md` is what tests it.
