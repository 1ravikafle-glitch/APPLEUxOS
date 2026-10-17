#!/usr/bin/env bash
# AppleUX system-core build with hard RAM capping.
#
# Why this exists: soong_build is a Go 1.24 binary and its peak RSS during
# whole-tree analysis was measured at 9.8 GB on a 15 GB box. That is live
# heap state, not parallelism, so -j1 alone does not fix it — it just makes
# the same allocation take longer. GOMEMLIMIT (Go 1.19+) is the actual lever:
# it tells the runtime to trade CPU for memory and collect harder instead of
# growing the heap.
#
# Disk is not a constraint here (295 GB free). RAM is, and swap is 48 GB free,
# so we deliberately run serialized rather than fast.
#
# Usage: scripts/soong-capped.sh [targets...]      (default: the 3 AppleUX apps)

set -o pipefail

ROOT="/home/elfakiris/OsProject"
TOP="$ROOT/android-16"
LOGDIR="${LOGDIR:-/tmp/opencode}"

# --- RAM cap -------------------------------------------------------------
# 8 GiB soft cap. Physical is 15 GB total; leaving ~6 GB for ninja, aapt2,
# kotlinc and the desktop keeps the box responsive. Go will GC harder rather
# than exceed this.
export GOMEMLIMIT="${GOMEMLIMIT:-8GiB}"
# Paired with GOMEMLIMIT: low GOGC keeps the live set tight. Slower, but this
# build is correctness-gated, not throughput-gated.
export GOGC="${GOGC:-50}"

# --- Serialization -------------------------------------------------------
# One ninja job. One Soong bootstrap. Parallelism here costs RAM and buys
# nothing on a capped box.
export SOONG_JOBS="${SOONG_JOBS:-1}"
export NINJA_ARGS="${NINJA_ARGS:--j1}"

# Keep kotlinc/JVM workers down too — they are the other big consumer once
# ninja actually compiles our three modules.
export KOTLIN_BUILD_WORKERS="${KOTLIN_BUILD_WORKERS:-1}"
export GRADLE_OPTS="${GRADLE_OPTS:--Xmx2g}"

if [[ $# -gt 0 ]]; then
    TARGETS=("$@")
else
    # AppleUXSystemUI removed 2026-10-17 (deliverables/m23). SystemUI itself is now
# the target, because that is where the iOS chrome is compiled into.
TARGETS=(AppleUXLauncher AppleUXSettings SystemUI)
fi

echo "=== AppleUX capped build ==="
echo "targets : ${TARGETS[*]}"
echo "GOMEMLIMIT=$GOMEMLIMIT GOGC=$GOGC SOONG_JOBS=$SOONG_JOBS"
echo "mem before:"
free -h | sed -n '2p'

cd "$TOP" || exit 1
# shellcheck disable=SC1091
source build/envsetup.sh >/dev/null 2>&1
lunch aosp_rock-bp4a-eng >/dev/null 2>&1 || { echo "lunch FAILED"; exit 1; }

LOG="$LOGDIR/soong-capped-$(date +%H%M%S).log"
echo "log: $LOG"

# Report peak RSS of the build so a regression in memory behaviour is visible
# rather than silent.
( while true; do
    ps -eo rss,comm --no-headers 2>/dev/null \
      | awk '$2 ~ /soong_build|ninja|java|kotlinc|aapt2/ {s+=$1} END {print s}' \
      | while read -r kb; do
        [[ -n "$kb" && "$kb" -gt 0 ]] && echo "$kb" >> "$LOGDIR/.rss_peak"
      done
    sleep 15
  done ) &
MON_PID=$!
trap 'kill $MON_PID 2>/dev/null' EXIT

m "${NINJA_ARGS}" "${TARGETS[@]}" 2>&1 | tee "$LOG"
RC=${PIPESTATUS[0]}

echo
echo "=== result ==="
echo "exit: $RC"
if [[ -f "$LOGDIR/.rss_peak" ]]; then
    PEAK=$(sort -n "$LOGDIR/.rss_peak" | tail -1)
    echo "peak combined build RSS: $((PEAK / 1024)) MiB"
    rm -f "$LOGDIR/.rss_peak"
fi
free -h | sed -n '2p'
exit $RC