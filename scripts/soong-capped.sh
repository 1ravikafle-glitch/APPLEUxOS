#!/usr/bin/env bash
# AppleUX system-core build with a HARD RAM cap.
#
# ======================================================================
# WHY THIS FILE EXISTS (and why it was rewritten on 2026-10-17)
# ======================================================================
# The previous version of this script claimed to cap RAM and did not.
#
# It set GOMEMLIMIT=6GiB and then ran a loop that *recorded* peak RSS to a
# file. Reporting is not capping. GOMEMLIMIT is a SOFT limit: it is a hint to
# the Go garbage collector, and soong_build exceeded it routinely — a measured
# peak of 13.2 GiB was observed against a 6 GiB GOMEMLIMIT. Nothing stopped it,
# so the kernel OOM killer chose a victim, the build died with no error, and
# the whole desktop went to swap at the same time.
#
# The symptom that made this unavoidable to fix: a build launched with this
# script stopped dead after "Globs changed, rerunning soong..." with an empty
# error section and no soong_build process left. That was an OOM kill being
# reported as a silent disappearance.
#
# ======================================================================
# THE CAP NOW: a cgroup v2 memory limit
# ======================================================================
# Verified on this box before being relied on:
#
#   * /sys/fs/cgroup is cgroup2fs
#   * cgroup.controllers lists "memory"
#   * user.slice's subtree lists cpu, memory, pids  (delegated, no sudo needed)
#   * systemd-run --user --scope -p MemoryMax=... works unprivileged
#   * the limit is genuinely enforced: allocating 1.5 GB under
#     MemoryMax=512M kills the process (systemd-run exits 1, no output file
#     written), while the same allocation uncapped succeeds.
#
# A cgroup limit is enforced by the kernel and cannot be exceeded, which is
# what GOMEMLIMIT could never do. The OOM killer then has nowhere to go but the
# largest process in the scope — the build — instead of the desktop.
#
# If systemd-run is unavailable the script refuses to run uncapped rather than
# silently doing the thing that crashed the box.

set -o pipefail

ROOT="/home/elfakiris/OsProject"
TOP="$ROOT/android-16"
LOGDIR="${LOGDIR:-/tmp/opencode}"
mkdir -p "$LOGDIR"

# --- the cap ---------------------------------------------------------------
# Total RAM is 15 GiB. A hard ceiling of 9 GiB leaves ~6 GiB for the desktop,
# the page cache and everything else, so the machine stays usable DURING a
# build rather than only after it.
RAM_CAP_MB="${RAM_CAP_MB:-9216}"
# Swap headroom inside the scope. Non-zero so a spike spills to swap instead of
# killing the build outright, but far below the 47 GB the box has, so a runaway
# build cannot fill it and wedge the machine.
SWAP_CAP_MB="${SWAP_CAP_MB:-6144}"

# --- Go soft limits, set BELOW the hard cap --------------------------------
# These are an optimisation, not the safety net. Go is told to start collecting
# before the kernel would have to kill anything, which turns "OOM kill" into
# "slower build that finishes". Deliberately conservative.
export GOMEMLIMIT="${GOMEMLIMIT:-7GiB}"
export GOGC="${GOGC:-40}"

# --- JVMs ------------------------------------------------------------------
# kotlinc and the java prebuilts are the second big consumer. -Xmx is a real
# cap on the Java heap, unlike Go's soft limit, so these are meaningful.
export KOTLIN_BUILD_WORKERS="${KOTLIN_BUILD_WORKERS:-1}"
export GRADLE_OPTS="${GRADLE_OPTS:--Xmx1536m}"
export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:--Xmx1536m}"

# --- Serialization ---------------------------------------------------------
export SOONG_JOBS="${SOONG_JOBS:-1}"
export NINJA_ARGS="${NINJA_ARGS:--j1}"

if [[ $# -gt 0 ]]; then
    TARGETS=("$@")
else
    # AppleUXSystemUI removed 2026-10-17 (deliverables/m23). SystemUI is a
    # target because that is where the iOS chrome is compiled into.
    TARGETS=(AppleUXLauncher AppleUXSettings SystemUI)
fi

# --- fail closed, never uncapped ------------------------------------------
if ! command -v systemd-run >/dev/null 2>&1; then
    echo "FATAL: systemd-run not found." >&2
    echo "Refusing to run uncapped — that is what OOM-killed the box on" >&2
    echo "2026-10-17. Fix the cap (install systemd) or run the build by hand" >&2
    echo "with 'systemd-run --user -p MemoryMax=${RAM_CAP_MB}M -- <cmd>'." >&2
    exit 2
fi
if [[ ! -w /sys/fs/cgroup/cgroup.subtree_control ]] && \
   [[ ! -f /sys/fs/cgroup/user.slice/cgroup.subtree_control ]]; then
    echo "FATAL: cgroup v2 memory controller not writable for this user." >&2
    echo "Refusing to run uncapped. See the header for the check to repeat." >&2
    exit 2
fi

echo "=== AppleUX capped build ==="
echo "targets        : ${TARGETS[*]}"
echo "HARD cap       : ${RAM_CAP_MB} MiB RAM + ${SWAP_CAP_MB} MiB swap (cgroup)"
echo "GOMEMLIMIT     : $GOMEMLIMIT   GOGC=$GOGC   (soft, below the hard cap)"
echo "JAVA_TOOL_OPTS : $JAVA_TOOL_OPTIONS"
echo "mem before:"; free -h | sed -n '2p'

cd "$TOP" || exit 1
# shellcheck disable=SC1091
source build/envsetup.sh >/dev/null 2>&1
lunch aosp_rock-bp4a-eng >/dev/null 2>&1 || { echo "lunch FAILED"; exit 1; }

LOG="$LOGDIR/soong-capped-$(date +%H%M%S).log"
PEAKFILE="$LOGDIR/.rss_peak.$$"
echo "log            : $LOG"

# --- peak RSS reporter -----------------------------------------------------
# Reporting only, explicitly NOT the cap — that is the cgroup's job now.
( while true; do
    ps -eo rss,comm --no-headers 2>/dev/null \
      | awk '$2 ~ /soong_build|ninja|java|kotlinc|aapt2/ {s+=$1} END {print s+0}' \
      >> "$PEAKFILE" 2>/dev/null
    sleep 10
  done ) &
MON_PID=$!
trap 'kill $MON_PID 2>/dev/null; rm -f "$PEAKFILE"' EXIT

# --- the capped build ------------------------------------------------------
# The wrapper matters. `m` is a shell FUNCTION defined by build/envsetup.sh, and
# systemd-run does NOT inherit this shell's environment or functions — it starts
# the command with systemd's own environment. Running `m` directly under
# systemd-run therefore fails instantly with exit 1 and an empty log, which looks
# exactly like a build failure and is not one.
WRAPPER="$LOGDIR/.soong-wrapper-$$.sh"
cat > "$WRAPPER" <<WRAPEOF
#!/usr/bin/env bash
cd "$TOP" || exit 1
source build/envsetup.sh >/dev/null 2>&1
lunch aosp_rock-bp4a-eng >/dev/null 2>&1 || { echo "lunch FAILED"; exit 1; }
m ${NINJA_ARGS} ${TARGETS[*]}
WRAPEOF
chmod +x "$WRAPPER"

# --wait so the script blocks and propagates the exit status. Without it
# systemd-run returns immediately and the build appears to vanish, which is a
# second way to get the silent-death symptom this script exists to fix.
systemd-run --user --wait --quiet \
    --unit="appleux-build-$$" \
    -p "MemoryMax=${RAM_CAP_MB}M" \
    -p "MemorySwapMax=${SWAP_CAP_MB}M" \
    -p "MemoryHigh=${RAM_CAP_MB}M" \
    "$WRAPPER" 2>&1 | tee "$LOG"
RC=${PIPESTATUS[0]}
rm -f "$WRAPPER"

echo
echo "=== result ==="
echo "exit: $RC"
if [[ -s "$PEAKFILE" ]]; then
    PEAK=$(sort -n "$PEAKFILE" | tail -1)
    echo "peak combined build RSS: $((PEAK / 1024)) MiB (cap ${RAM_CAP_MB} MiB)"
fi
if [[ $RC -ne 0 ]]; then
    # The signature of a cgroup kill is an EMPTY log: the kernel takes the
    # largest process in the scope and nothing gets to print. Recognising that
    # is the difference between "raise the cap" and chasing a code error that
    # does not exist.
    empty_log=no
    [[ ! -s "$LOG" ]] && empty_log=yes
    if [[ $RC -eq 1 ]] && { [[ "$empty_log" == yes ]] || grep -qiE "memory|max|oom|killed" "$LOG" 2>/dev/null; }; then
        echo ""
        echo "=========================================================="
        echo " THE CAP KILLED THE BUILD, not a code error."
        if [[ "$empty_log" == yes ]]; then
            echo " The log is EMPTY, which is what a cgroup OOM kill looks"
            echo " like: the kernel took soong_build and nothing printed."
        fi
        echo " Peak was $(( ${PEAK:-0} / 1024 )) MiB against a ${RAM_CAP_MB} MiB cap."
        echo " Soong analysis of this tree is known to want ~13 GiB."
        echo ""
        echo " To finish the build rather than be killed:"
        echo "   RAM_CAP_MB=12800 SWAP_CAP_MB=8192 scripts/soong-capped.sh ${TARGETS[*]}"
        echo " Keep the desktop idle first so the headroom is real."
        echo "=========================================================="
    fi
    echo ""
    echo "--- last 30 lines of $LOG ---"
    tail -30 "$LOG"
fi
free -h | sed -n '2p'
exit $RC