# Baseline evidence log (MT6765)

Every factual claim made by the fragments in `config/fragments/` is derived
from the greps below. All file:line references are against:

    repo    : https://github.com/oppo-source/android_kernel_oppo_mt6765
    branch  : oppo/mt6765_u_14.0_oppo_a77
    commit  : 9b1c9a531dd08e68bf778dcf7ed13f811ec1f153
    subject : Synchronize code for OPPO CPH2385_14.0.0.110(EX01) Based on MTK
              release TAG:t-alps-release-s0.mp1.rc-V14.75
    kernel  : 4.19.191   (Makefile: VERSION=4 PATCHLEVEL=19 SUBLEVEL=191)

Cross-check tree used only to corroborate baseline values:

    branch  : oppo/mt6765_r_11.1.4_a16
    commit  : 220d8e1a728dd8a4d2f25e8aadb66be6f92b440a
    config  : arch/arm64/configs/oppo6765_defconfig

Why a cross-check was used: this is an OPPO-tuned tree full of
`CONFIG_OPLUS_*` features, so a single defconfig could reflect Oppo product
tuning rather than anything intrinsic to MT6765. Where a fragment depends on
a baseline value, that value had to appear in **both** independent MT6765
defconfigs before the fragment was allowed to assert it. Values that failed
that test are recorded below and were not used.

---

## 1. Which defconfig is the MT6765 baseline

`arch/arm64/configs/k65v1_64_bsp_defconfig` (749 lines) is the baseline. It
self-identifies:

    line  37: CONFIG_MACH_MT6765=y
    line  95: CONFIG_MTK_PLATFORM="mt6765"
    line  96: CONFIG_ARCH_MTK_PROJECT="k65v1_64_bsp"

`k65v1` is MediaTek's project name covering MT6761/6763/6765, i.e. the
Helio G99 family. `arch/arm64/configs/oppo6765_defconfig` on the a16 branch
is the per-chip equivalent and reaches the same conclusion.

Note for the integrator: this is a **reference** baseline for the SoC, not
the stock POCO M5 config. The AOSP sync in `../android-16` contains no kernel
tree at all (`kernel/` holds only `configs`, `prebuilts`, `tests`; there is
no `kernel/common`, `kernel/mediatek` or `device/mediatek`), so this tree
is a separate acquisition and a separate integration problem. See README.md.

## 2. SoC facts

    arch/arm/boot/dts/mt6765.dts  ->  cpu0 .. cpu7  (cpu@000 .. cpu@103)

Eight cores. This is the sole justification for P02's `CONFIG_NR_CPUS=8`.
Caveat carried in the P02 header: this is OPPO's unified MT6765 DTS, not the
POCO M5 DTS, so the count must be re-verified against the real target DTS
before that patch is used.

## 3. Baseline values the patches depend on (corroborated in both defconfigs)

| Symbol                          | k65v1_64_bsp | oppo6765 (a16) | used by |
|---------------------------------|--------------|----------------|---------|
| `CONFIG_SCHED_AUTOGROUP`        | `y` (:14)    | `y` (:9)       | P03     |
| `CONFIG_CPU_FREQ_GOV_POWERSAVE` | `y` (:49)    | `y` (:51)      | P04     |
| `CONFIG_CPU_FREQ_GOV_CONSERVATIVE` | `y` (:50) | `y` (:52)      | P04     |
| `CONFIG_CPU_FREQ_DEFAULT_GOV_SCHEDUTIL` | `y` (:48) | `y` (:50)   | context |
| `CONFIG_CMA_AREAS`              | `16` (:65)   | `16` (:68)     | (rejected, see 5) |
| no `CONFIG_LTO*`                | absent       | absent         | P05     |
| no `CONFIG_KSM`                 | absent       | absent         | (rejected, see 5) |
| no `CONFIG_T*`/THP              | absent       | absent         | (rejected, see 5) |
| `CONFIG_ZRAM`                   | `y` (:83)    | -              | context |
| `CONFIG_CC_OPTIMIZE_FOR_SIZE`   | `y` (:22)    | -              | context |

## 4. Source facts behind each patch

### P01 -- I/O scheduler is CFQ by default

    block/Kconfig.iosched:22  config IOSCHED_DEADLINE   tristate, default y
    block/Kconfig.iosched:26  config IOSCHED_CFQ        tristate, default y
    block/Kconfig.iosched:44  choice
    block/Kconfig.iosched:46          default DEFAULT_CFQ
    block/Kconfig.iosched:61  config DEFAULT_IOSCHED
    block/Kconfig.iosched:63          default "cfq" if DEFAULT_CFQ

Neither defconfig mentions `IOSCHED_*`, so Kconfig defaults apply and **CFQ
is the default scheduler**. The CFQ help text in this tree even says "This is
the default I/O scheduler."

### P02 -- NR_CPUS defaults to 64, SoC has 8

    arch/arm64/Kconfig:764  config NR_CPUS
    arch/arm64/Kconfig:768          default "64"
    arch/arm64/Kconfig.platforms    no NR_CPUS override (grep: no match)
    neither defconfig sets CONFIG_NR_CPUS  -> 64 is in force

### P03 -- SCHED_AUTOGROUP is on

Both defconfigs say `=y`. Android's actual grouping mechanisms
(`CONFIG_UCLAMP_TASK`, `CONFIG_UCLAMP_TASK_GROUP`, `CONFIG_SCHED_TUNE`,
`CONFIG_CPUSETS`, `CONFIG_BLK_CGROUP`, `CONFIG_MTK_SCHED_EXTENSION`) are all
present and are not touched by P03.

### P04 -- extra governors compiled; schedutil already default

    k65v1_64_bsp_defconfig:48  CONFIG_CPU_FREQ_DEFAULT_GOV_SCHEDUTIL=y
    k65v1_64_bsp_defconfig:49  CONFIG_CPU_FREQ_GOV_POWERSAVE=y
    k65v1_64_bsp_defconfig:50  CONFIG_CPU_FREQ_GOV_CONSERVATIVE=y
    k65v1_64_bsp_defconfig:108 CONFIG_MTK_CPU_FREQ=y

    drivers/block/zram/Kconfig:18  config ZRAM_WRITEBACK
    drivers/block/zram/Kconfig:21          default n
    init/Kconfig:258  config SWAP  ... default y   (zram is usable)

### P05 -- LTO is off but supported

    grep -in lto k65v1_64_bsp_defconfig   -> no match
    grep -in lto oppo6765_defconfig       -> no match
    arch/arm64/Kconfig:60  select ARCH_SUPPORTS_LTO_CLANG
    arch/arm64/Kconfig:61  select ARCH_SUPPORTS_THINLTO
    k65v1_64_bsp_defconfig:22  CONFIG_CC_OPTIMIZE_FOR_SIZE=y

### P06 -- MTK scheduler tracers are on

    k65v1_64_bsp_defconfig:30  CONFIG_MTK_SCHED_TRACERS=y

The defconfig line proves the symbol exists and is enabled. It does **not**
prove what the symbol gates. That is why P06 is marked GATED with a mandatory
`grep -rn MTK_SCHED_TRACERS` before use. P06 was deliberately NOT written as
an unconditional recommendation.

---

## 5. Investigated and rejected -- with the evidence that killed it

This section exists because most of these are things a generic kernel-tuning
guide tells you to turn on. On this platform the source says otherwise.

**KSM -- rejected.** `grep KSM` is absent from both defconfigs, and
`mm/Kconfig` defines it as a plain opt-in bool that is not selected by
anything here. It stays off. Android apps are separate processes with
distinct, largely private address spaces and little fork-sharing to dedupe;
KSM's periodic scanning is CPU spent for very little reclaim. Turning it on
for "RAM compression" is cargo-cult.

**Transparent HugePages -- rejected.** `mm/Kconfig:378 config
TRANSPARENT_HUGEPAGE` has **no `default` statement**, so per Kconfig
semantics for a bool it resolves to `n`, and neither defconfig sets it. THP
therefore is already off, and P05/P02 do not turn it on. Direct compaction
and page-fault stalls from THP are a well-known source of latency spikes
under memory pressure on mobile, which is the opposite of what we want on a
4 GB device.

**ZSWAP -- rejected.** `mm/Kconfig:535 config ZSWAP ... depends on FRONTSWAP
&& CRYPTO=y`, `default n`. ZRAM is already enabled (`CONFIG_ZRAM=y`), and
ZRAM already compresses the anon pages in its own backing store. Adding a
second compression tier in front of it spends CPU on a device with 6 A55s to
solve a problem ZRAM is already handling. Rejected as double work.

**zram writeback -- rejected as a change, kept as a pinned-off safety.**
`drivers/block/zram/Kconfig:21 default n`. Enabling writeback would spill
compressed pages to eMMC and add flash writes/wear. P04 only *states* it off
so an upstream default flip cannot silently introduce it.

**CMA sizing -- REJECTED AS A KCONFIG CHANGE. This one is worth reading.**
`grep -c CMA_SIZE_MBYTES mm/Kconfig` returns **0**. The symbol
`CONFIG_CMA_SIZE_MBYTES` does not exist in this tree. The defconfig sets
`CONFIG_CMA_AREAS=16` (the *number of areas*, not a size). On this platform
CMA region sizes are not configured through Kconfig at all -- they come from
the device tree / bootargs. Any tuning guide that tells you to set
`CONFIG_CMA_SIZE_MBYTES=512` to change CMA here is wrong, and that patch
would be a silent no-op. Tuning CMA on MT6765 means editing the DTS
reserved-memory or the bootargs, which is a different and riskier change
than a Kconfig fragment, and is out of scope for this patch set.

**BFQ / Kyber / mq-deadline -- rejected, and this one cost real effort.**
All three are the BLK-MQ variants in this Kconfig
(`block/Kconfig.iosched`: `config MQ_IOSCHED_KYBER`, `config IOSCHED_BFQ`,
`config MQ_IOSCHED_DEADLINE`). To recommend them I needed to know whether
MTK's MSDC storage driver actually uses the multi-queue block layer. I could
not establish it:

    grep -nE 'blk_mq_ops|blk_mq_init_queue|blk_alloc_disk' \
        drivers/mmc/host/mtk-sd.c                            -> no match
    ... drivers/mmc/host/mediatek/ComboA/sd.c                -> no match
    ... drivers/mmc/host/mediatek/ComboA/cmdq_hci.c          -> no match

`block/blk` in this tree is a **symlink** to
`vendor/oplus/kernel/oplus_performance/healthinfo/blk/`, which is why the
usual lookup path misleads. `block/Makefile` builds `blk-mq.o`
*unconditionally* here (MTK dropped the `CONFIG_BLK_MQ` gate entirely --
`config BLK_MQ` is not declared anywhere in this tree's Kconfig), so the core
exists, but the driver's use of it is unconfirmed.

Rather than guess, the BLK-MQ schedulers are not offered, and P01 sticks to
deadline, which is single-queue and unconditionally present. If someone
later confirms blk-mq on the MSDC path, Kyber becomes worth a fresh
evaluation. The exact gate command is in P01's header.

**ftrace / kprobes / perfetto -- rejected.** `k65v1_64_bsp_defconfig:47`
sets `CONFIG_KPROBES=y`, and ftrace/BPF tracing is present. A size-focused
guide would strip these. On Android they are load-bearing: `atrace` and the
perfetto kernel data sources depend on them. Removing them would silently
break system tracing and any profiling pipeline built on it, in exchange for
a small size win. Not worth it, and not our call to make unilaterally.

**16 KB pages -- rejected.** arm64 builds 4 KB pages here. Going to 16 KB
reduces TLB pressure, which is a real effect, but it is a page-size change
made at platform level with userland and all vendor modules affected, on a
device where the DT and vendor tree were validated at 4 KB. That is a
porting project, not a config fragment, and it is not something to attempt
blind on hardware we cannot test.

**NUMA -- rejected.** `config NUMA` is not enabled and MT6765 is a single
memory-controller SoC; there is no second node to be NUMA-aware about.

**Debug-option strips -- rejected as unnecessary.** Already off in this
baseline, so there is nothing to strip: no `CONFIG_KASAN`,
`CONFIG_DEBUG_VM`, `CONFIG_PROVE_LOCKING`, `CONFIG_DEBUG_ATOMIC`,
`CONFIG_DEBUG_SPINLOCK`, `CONFIG_SLUB_DEBUG` (`=n` at line 33),
`CONFIG_DEBUG_LIST`. Proposing removals for options that are already absent
would be padding the patch set with changes that cannot do anything. They
are listed here so it is clear they were checked rather than overlooked.

**Confidential-computing / vendor "healthinfo" machinery -- not touched.**
This tree wires `block/blk` to `vendor/oplus/.../healthinfo/blk/`, and the
defconfig carries `CONFIG_OPLUS_DYNAMIC_READAHEAD=y` (line 749,
`mm/dynamic_readhead.c`). MTK/Oplus adaptive readahead is an existing,
non-trivial mechanism that already tries to reduce storage I/O relative to
fixed readahead. Replacing or second-guessing it from a config fragment is
exactly the kind of change that looks clever and cannot be validated here, so
it is left alone.
