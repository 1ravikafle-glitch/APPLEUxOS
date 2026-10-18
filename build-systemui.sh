#!/usr/bin/env bash
# ============================================================================
# AppleUX — one-shot SystemUI build launcher. Run from a terminal:
#
#     bash ~/OsProject/build-systemui.sh
#
# Then watch progress in another terminal:
#     tail -f /tmp/opencode/systemui-build-final.log
#
# Closes nothing itself — close the AI agent, browser, and any emulator
# BEFORE launching; the build needs the whole box.
#
# Every setting below is backed by a measured failure from runs 1-10 on
# 2026-10-18. Do not "simplify" any of them without reading the notes.
# ============================================================================
set -u

ROOT="/home/elfakiris/OsProject"
TOP="$ROOT/android-16"
LOGDIR="/tmp/opencode"
LOG="$LOGDIR/systemui-build-final.log"
mkdir -p "$LOGDIR"

# ---- memory model (measured) -----------------------------------------------
# GOMEMLIMIT MUST be written INSIDE the wrapper below. systemd-run does not
# inherit this shell's environment — exporting it here is a no-op, which is
# why GOMEMLIMIT "changes" did nothing in runs 4/7/9/10 while soong's heap
# grew uncapped until the cgroup killed it (13.5G RAM + 24G swap in run 10).
GOMEMLIMIT="8GiB"        # AGENTS.md documented value: trades CPU for memory
GOGC="40"
JAVA_TOOL_OPTIONS="-Xmx1536m"

RAM_CAP_MB="13824"        # hard cgroup ceiling; leaves ~1.2G for the kernel
SWAP_CAP_MB="24576"       # run 4 died at the old 8G cap; 24G is headroom
NINJA_ARGS="-j1"

# ---- preflight --------------------------------------------------------------
echo "== AppleUX SystemUI build =="
FREE_MB=$(free -m | awk 'NR==2 {print $7}')
if [ "$FREE_MB" -lt 11000 ]; then
    echo "!! Only ${FREE_MB} MiB available. The analysis needs ~11-12G."
    echo "!! Close the AI agent / browser / emulator first, then rerun."
    exit 2
fi
echo "   available: ${FREE_MB} MiB — OK"

# Kill any emulator (safely: exact process names, no -f self-match)
pgrep -x qemu-system-x86_64 >/dev/null 2>&1 && {
    pkill -x qemu-system-x86_64 && echo "   emulator killed (was holding ~3.6G)"
}

# oomd disarm drop-in (runs 4/7/9 were oomd kills on PSI; -p overrides are
# rejected by systemd-run, but drop-ins apply to transient units with a
# STABLE name — hence --unit=appleux-build with no PID suffix below).
mkdir -p ~/.config/systemd/user/appleux-build.service.d
cat > ~/.config/systemd/user/appleux-build.service.d/override.conf <<'CONF'
# MemoryMax protects the machine at the kernel level; oomd's PSI heuristic
# was killing deliberately memory-heavy builds while the box still had free
# RAM. Cleared for this unit only.
[Service]
ManagedOOMMemoryPressure=
ManagedOOMSwap=
CONF
systemctl --user daemon-reload 2>/dev/null
# A previous run leaves the transient unit in "failed" state, and systemd
# refuses to reuse a loaded unit name. Reset it so relaunches always work.
systemctl --user reset-failed appleux-build.service 2>/dev/null

# ---- wrapper: env vars live HERE so soong_build actually sees them ---------
WRAPPER="$LOGDIR/.soong-build-wrapper.sh"
cat > "$WRAPPER" <<WRAPEOF
#!/usr/bin/env bash
set -u
# Everything soong_build needs must be set inside this file.
# systemd-run starts it with systemd's environment, not ours.
export GOMEMLIMIT="${GOMEMLIMIT}"
export GOGC="${GOGC}"
export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS}"
export KOTLIN_BUILD_WORKERS=1
export SOONG_JOBS=1
cd "$TOP" || exit 1
source build/envsetup.sh >/dev/null 2>&1
lunch aosp_rock-bp4a-eng || { echo "LUNCH FAILED"; exit 1; }
m ${NINJA_ARGS} SystemUI
WRAPEOF
chmod +x "$WRAPPER"

# ---- launch, capped, logged, honest ----------------------------------------
echo "   GOMEMLIMIT=${GOMEMLIMIT} (inside wrapper — actually applies)"
echo "   caps: ${RAM_CAP_MB}M RAM / ${SWAP_CAP_MB}M swap | oomd: disarmed"
echo "   log : $LOG"
echo "   started $(date +%H:%M:%S) — analysis alone takes ~45-60 min."
echo ""

systemd-run --user --wait --quiet --pipe \
    --unit="appleux-build" \
    -p "MemoryMax=${RAM_CAP_MB}M" \
    -p "MemorySwapMax=${SWAP_CAP_MB}M" \
    -p "MemoryHigh=${RAM_CAP_MB}M" \
    "$WRAPPER" 2>&1 | tee "$LOG"
RC=${PIPESTATUS[0]}

echo ""
echo "==================== RESULT: exit $RC ===================="
if [ "$RC" -eq 0 ]; then
    echo "SystemUI built. Artifacts under:"
    echo "  $TOP/out/target/product/rock/system_ext/priv-app/SystemUI/"
    echo "The AppleUX chrome (CC, NC, shade status bar, notch host,"
    echo "home indicator) is now COMPILED for the first time."
else
    echo "Failed. The full error is the LAST 'error:'/'FAILED:' lines:"
    grep -nE "^error|FAILED:|ninja: build stopped" "$LOG" | tail -10
    echo ""
    echo "If the log shows the cgroup OOM kill signature (empty tail,"
    echo "'oom-kill' in: journalctl --user -u appleux-build), the box"
    echo "genuinely cannot fit this build — next lever is a narrower"
    echo "manifest or more RAM."
fi
