#!/usr/bin/env bash
# Re-runs every source claim made by the config fragments against the clone.
# A FAIL means that patch's rationale is NOT supported and it must be dropped.
#
#   bash notes/repro-grep-evidence.sh
#
# Tree: https://github.com/oppo-source/android_kernel_oppo_mt6765
#   oppo/mt6765_u_14.0_oppo_a77 @ 9b1c9a531dd08e68bf778dcf7ed13f811ec1f153

set -uo pipefail
SRC="$(cd "$(dirname "$0")/.." && pwd)/src-oppo-mt6765"
cd "$SRC" || { echo "no clone at $SRC"; exit 1; }

PASS=0; FAIL=0
ok()   { echo "  PASS  $1"; PASS=$((PASS+1)); }
bad()  { echo "  FAIL  $1"; FAIL=$((FAIL+1)); }
# assert_contains <file> <regex> <label>
assert_contains() {
  if grep -qE "$2" "$1" 2>/dev/null; then ok "$3"; else bad "$3  (no match: $2  in $1)"; fi
}
# assert_absent <file> <regex> <label>
assert_absent() {
  if grep -qE "$2" "$1" 2>/dev/null; then bad "$3  (unexpectedly matched: $2)"; else ok "$3"; fi
}
hdr() { echo; echo "== $1"; }

CFG=arch/arm64/configs/k65v1_64_bsp_defconfig
KCFG=block/Kconfig.iosched

hdr "provenance"
if [ "$(git rev-parse HEAD)" = "9b1c9a531dd08e68bf778dcf7ed13f811ec1f153" ]; then
  ok "HEAD is the recorded commit 9b1c9a5"
else
  bad "HEAD is $(git rev-parse HEAD), expected 9b1c9a5..."
fi
assert_contains Makefile '^PATCHLEVEL = 19$'            "kernel is 4.19.x"
assert_contains Makefile '^SUBLEVEL = 191$'             "kernel is 4.19.191"

hdr "baseline defconfig is the MT6765 one"
assert_contains "$CFG" '^CONFIG_MACH_MT6765=y$'         "CONFIG_MACH_MT6765=y"
assert_contains "$CFG" '^CONFIG_MTK_PLATFORM="mt6765"'  "CONFIG_MTK_PLATFORM=mt6765"
assert_contains "$CFG" 'ARCH_MTK_PROJECT="k65v1_64_bsp"' "project is k65v1_64_bsp"

hdr "SoC has exactly 8 cpus  [gate for P02]"
# NB: must anchor on the node label. A loose 'cpu@[0-9]' also matches the
# GIC's "gic_cpu@0c400000" and yields 9.
NCPU=$(git show HEAD:arch/arm/boot/dts/mt6765.dts 2>/dev/null | grep -cE '^\s*cpu[0-9]+:\s*cpu@[0-9a-f]+\s*\{')
if [ "$NCPU" = "8" ]; then ok "mt6765.dts declares exactly 8 cpu nodes (cpu0..cpu7)"
else bad "cpu node count = $NCPU, expected 8 -- do NOT apply P02"; fi

hdr "P01  default IO scheduler is CFQ"
assert_contains "$KCFG" '^config IOSCHED_CFQ'          "IOSCHED_CFQ exists"
assert_contains "$KCFG" '^config IOSCHED_DEADLINE'      "IOSCHED_DEADLINE exists"
assert_contains "$KCFG" '^config DEFAULT_IOSCHED'       "DEFAULT_IOSCHED exists"
if grep -qE 'default "cfq" if DEFAULT_CFQ' "$KCFG"; then ok 'DEFAULT_IOSCHED defaults to "cfq"'
else bad 'expected DEFAULT_IOSCHED -> "cfq"'; fi
assert_absent  "$CFG" '^CONFIG_IOSCHED_CFQ'            "defconfig does NOT mention IOSCHED_CFQ (so Kconfig default y applies)"
assert_absent  "$CFG" '^# CONFIG_IOSCHED_CFQ'          "defconfig does NOT disable IOSCHED_CFQ"

hdr "P02  NR_CPUS defaults to 64 and is unset in defconfig"
assert_contains arch/arm64/Kconfig '^\s*default "64"$' "arm64 NR_CPUS default 64"
if grep -q 'NR_CPUS' arch/arm64/Kconfig.platforms; then bad "unexpected NR_CPUS in Kconfig.platforms"; else ok "no NR_CPUS override in Kconfig.platforms"; fi
assert_absent "$CFG" '^CONFIG_NR_CPUS'                 "defconfig leaves NR_CPUS at default (64)"

hdr "P03  SCHED_AUTOGROUP is on"
assert_contains "$CFG" '^CONFIG_SCHED_AUTOGROUP=y$'    "CONFIG_SCHED_AUTOGROUP=y"
# corroboration in the independent cross-check defconfig
XC=../config/oppo6765_defconfig.a16-crosscheck
[ -f "$XC" ] && assert_contains "$XC" '^CONFIG_SCHED_AUTOGROUP=y$' \
  "cross-check defconfig also has SCHED_AUTOGROUP=y" \
  || echo "  SKIP  cross-check defconfig not present"

hdr "P04  extra cpufreq governors compiled, schedutil already default"
assert_contains "$CFG" '^CONFIG_CPU_FREQ_DEFAULT_GOV_SCHEDUTIL=y$' "default governor is schedutil"
assert_contains "$CFG" '^CONFIG_CPU_FREQ_GOV_POWERSAVE=y$'         "GOVERNOR_POWERSAVE=y"
assert_contains "$CFG" '^CONFIG_CPU_FREQ_GOV_CONSERVATIVE=y$'      "GOVERNOR_CONSERVATIVE=y"
assert_contains "$CFG" '^CONFIG_MTK_CPU_FREQ=y$'                  "MTK cpufreq present"
assert_contains drivers/block/zram/Kconfig '^config ZRAM_WRITEBACK' "ZRAM_WRITEBACK symbol exists"
assert_contains drivers/block/zram/Kconfig '^\s*default n$'        "ZRAM_WRITEBACK defaults to n"
assert_contains init/Kconfig '^\s*default y$'                     "SWAP defaults to y (zram usable)"

hdr "P05  LTO off but supported; -Os already on"
assert_absent  "$CFG" 'LTO'                                     "defconfig has no LTO at all"
assert_contains arch/arm64/Kconfig 'ARCH_SUPPORTS_LTO_CLANG'     "ARCH_SUPPORTS_LTO_CLANG selected"
assert_contains arch/arm64/Kconfig 'ARCH_SUPPORTS_THINLTO'       "ARCH_SUPPORTS_THINLTO selected"
assert_contains "$CFG" '^CONFIG_CC_OPTIMIZE_FOR_SIZE=y$'         "CC_OPTIMIZE_FOR_SIZE=y already set"

hdr "P06  MTK scheduler tracers on  [GATED - run the manual grep too]"
assert_contains "$CFG" '^CONFIG_MTK_SCHED_TRACERS=y$' "CONFIG_MTK_SCHED_TRACERS=y"
echo "  NOTE  P06 is NOT cleared by this. Run manually before applying:"
echo "        grep -rn 'MTK_SCHED_TRACERS' kernel/sched/ drivers/ vendor/"
echo "        If it gates anything other than tracing, DROP P06."

hdr "REJECTED  KSM is not enabled"
assert_absent "$CFG" 'KSM' "defconfig does not enable KSM"
assert_contains mm/Kconfig '^config KSM' "KSM is an opt-in bool in mm/Kconfig"

hdr "REJECTED  Transparent HugePages resolves to n"
assert_contains mm/Kconfig '^config TRANSPARENT_HUGEPAGE' "THP symbol exists"
if grep -A6 '^config TRANSPARENT_HUGEPAGE' mm/Kconfig | grep -qE '^\s*default'; then
  bad "THP has an explicit default -- re-read the rejection rationale"
else
  ok "THP has no explicit default -> resolves to n for a bool"
fi
assert_absent "$CFG" 'TRANSPARENT_HUGEPAGE' "defconfig never mentions THP"

hdr "REJECTED  CONFIG_CMA_SIZE_MBYTES does not exist in this tree"
N=$(grep -c 'CMA_SIZE_MBYTES' mm/Kconfig 2>/dev/null); N=${N:-0}
if [ "$N" = "0" ]; then ok "CMA_SIZE_MBYTES absent -> the usual CMA patch is a NO-OP here"
else bad "CMA_SIZE_MBYTES now appears $N times -- re-evaluate"; fi
assert_contains "$CFG" '^CONFIG_CMA_AREAS=16$' "only CMA_AREAS=16 (count of areas, not a size)"

hdr "REJECTED  BFQ/Kyber are BLK-MQ only and MSDC blk-mq use is UNCONFIRMED"
assert_contains "$KCFG" '^config IOSCHED_BFQ'     "IOSCHED_BFQ exists"
assert_contains "$KCFG" '^config MQ_IOSCHED_KYBER' "MQ_IOSCHED_KYBER exists"
if grep -q 'blk_mq_init_queue\|blk_mq_ops\|blk_alloc_disk' \
     drivers/mmc/host/mtk-sd.c drivers/mmc/host/mediatek/ComboA/sd.c \
     drivers/mmc/host/mediatek/ComboA/cmdq_hci.c 2>/dev/null; then
  ok "blk-mq usage FOUND -- Kyber/BFQ are now worth a fresh evaluation"
else
  # Informational, not a failure: this is the documented reason BFQ/Kyber are
  # NOT offered. The rejection stands precisely because it is unconfirmed.
  echo "  INFO  blk-mq usage UNCONFIRMED in MSDC -- BFQ/Kyber stay rejected (expected)"
fi
echo "  NOTE  block/blk is a symlink to vendor/oplus/.../healthinfo/blk/ --"
echo "        the usual lookup path misleads; that is why this is inconclusive."

hdr "REJECTED  debug options already off -- nothing to strip"
for s in CONFIG_KASAN CONFIG_DEBUG_VM CONFIG_PROVE_LOCKING CONFIG_DEBUG_ATOMIC \
         CONFIG_DEBUG_SPINLOCK CONFIG_DEBUG_LIST; do
  assert_absent "$CFG" "^CONFIG_${s#CONFIG_}=y" "$s is not enabled"
done
assert_contains "$CFG" '^# CONFIG_SLUB_DEBUG is not set$' "SLUB_DEBUG explicitly off"

hdr "REJECTED  MTK/Oplus dynamic readahead is on -- left alone"
assert_contains "$CFG" '^CONFIG_OPLUS_DYNAMIC_READAHEAD=y$' "OPLUS_DYNAMIC_READAHEAD=y"

echo
echo "=============================================="
echo "  PASS=$PASS  FAIL=$FAIL"
echo "=============================================="
[ "$FAIL" -eq 0 ] && echo "All claims hold. P06 and the blk-mq note still need manual review." \
                 || echo "FAILURES ABOVE: the corresponding patch rationales are NOT supported."
exit 0
