#!/usr/bin/env bash
# ============================================================================
# AppleUX SystemUI build — DIRECT RUN.
#
#     bash ~/OsProject/build-systemui-direct.sh
#
# This reproduces the ONE configuration that ever completed the soong
# analysis on this box (AGENTS.md, 2026-10-14: emulator killed, m -j1,
# 48:21): a plain interactive-shell build with NO systemd-run, NO cgroup
# caps. The 50 GB swap absorbs the overflow; the desktop may get slow —
# that is the memory-for-patience trade working as intended. Do NOT run
# anything else while it goes.
#
# Why not the capped launcher: every capped run died to a kill — oomd
# (runs 4/7/9), cgroup swap cap (10), or a SIGTERM at box saturation
# (the 20:32 run). Cgroups protect the desktop by KILLING the build; a
# direct run protects the build by ACCEPTING slowness. Your box, your
# call — this script is the second one.
# ============================================================================
cd /home/elfakiris/OsProject/android-16 || exit 1

FREE_MB=$(free -m | awk 'NR==2 {print $7}')
if [ "$FREE_MB" -lt 11000 ]; then
    echo "!! Only ${FREE_MB} MiB free — close the AI agent, browser, and"
    echo "!! any emulator, then rerun. The analysis alone wants ~11-12G."
    exit 2
fi
pgrep -x qemu-system-x86_64 >/dev/null 2>&1 && {
    pkill -x qemu-system-x86_64 && echo "emulator killed (~3.6G freed)"
}
echo "free: ${FREE_MB} MiB | swap: $(free -h | awk 'NR==3{print $2}') total"
echo "log : /tmp/opencode/systemui-direct.log"
echo "started $(date +%H:%M:%S). Expect 60-90 min at -j1. Box will get heavy — that is normal."
echo ""

source build/envsetup.sh || { echo "envsetup FAILED"; exit 1; }
lunch aosp_rock-bp4a-eng || { echo "lunch FAILED"; exit 1; }

# GOMEMLIMIT may or may not survive soong_ui's `env -i` for soong_build —
# pass it anyway (8GiB is the AGENTS-documented value), keep the JVMs
# small, and let swap do the rest.
GOMEMLIMIT=8GiB GOGC=40 JAVA_TOOL_OPTIONS=-Xmx1536m \
    m -j1 SystemUI 2>&1 | tee /tmp/opencode/systemui-direct.log
RC=${PIPESTATUS[0]}

echo ""
echo "================ RESULT: exit $RC ================"
if [ "$RC" -eq 0 ]; then
    echo "SystemUI built — the AppleUX chrome is compiled for the first time."
    echo "Artifact: out/target/product/rock/system_ext/priv-app/SystemUI/"
else
    echo "Died. Real errors:"
    grep -nE "^error|FAILED:|ninja: build stopped" /tmp/opencode/systemui-direct.log | tail -10
    echo ""
    echo "If this is a kernel OOM (dmesg shows 'Out of memory'), the honest"
    echo "conclusion is that 15G is under AOSP's own 16G minimum and the"
    echo "next lever is trimming the tree (cts/ is 2031 of 14156 bp files)."
fi
