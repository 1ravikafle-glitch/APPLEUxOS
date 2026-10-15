#!/usr/bin/env bash
# AppleUX storage report — READ-ONLY adb inventory for the 64 GB / 4 GB target.
#
# Answers the storage half of the POCO M5 problem: on this device a disabled
# package costs twice — APK/odex bytes on /data AND a process that can be
# resident in a 3 GB budget. This script measures the first half so the
# debloat list in deliverables/m2/keep-remove-disable-list.md can be argued
# from numbers instead of intuition (see deliverables/m2/storage-ram-baseline.md,
# which is currently ESTIMATES and needs replacing with this output).
#
# Reports:
#   1. /data filesystem usage (total / used / free)
#   2. per-app data+cache sizes, top N
#   3. per-app cache sizes, top N (the cheapest reclaimable win)
#   4. installed APK sizes, top N (disable/uninstall candidate sizing)
#   5. /data partition subdirectory totals (app, dalvik-cache, local/tmp)
#   6. reclaimable headline (cache total)
#
# READ-ONLY GUARANTEE: every command below is `df`, `du`, `stat`, `pm list`,
# `getprop` or `cat`. There is no `rm`, no `pm clear`, no `pm uninstall`, no
# `--user` mutation, and no push. Nothing this script does can lose data.
#
# usage: storage-report.sh [serial] [-n TOP_N] [--apk-only] [--data-only]
#   serial   adb device serial (default: emulator-5554, or $ANDROID_SERIAL if set)
#   -n N     rows per table (default 20)
#
# examples:
#   ./storage-report.sh emulator-5554
#   ./storage-report.sh -n 40
#   ANDROID_SERIAL=emulator-5554 ./storage-report.sh --data-only
#
# exit codes: 0 ok, 2 usage, 127 adb missing, 1 device unreachable/unusable

set -euo pipefail

SERIAL="${ANDROID_SERIAL:-emulator-5554}"
TOP_N=20
WANT_APK=1
WANT_DATA=1

usage() { awk 'NR>1 && /^#/ { sub(/^# ?/, ""); print; next } NR>1 { exit }' "$0"; }

while [ "$#" -gt 0 ]; do
  case "$1" in
    -n) [ "$#" -ge 2 ] || { echo "error: -n needs a value" >&2; exit 2; }; TOP_N="$2"; shift ;;
    --top) [ "$#" -ge 2 ] || { echo "error: --top needs a value" >&2; exit 2; }; TOP_N="$2"; shift ;;
    --apk-only)  WANT_DATA=0 ;;
    --data-only) WANT_APK=0 ;;
    -h|--help)   usage; exit 0 ;;
    -*)          echo "error: unknown option: $1" >&2; usage >&2; exit 2 ;;
    *)           SERIAL="$1" ;;
  esac
  shift
done

if ! [[ "$TOP_N" =~ ^[0-9]+$ ]] || [ "$TOP_N" -lt 1 ]; then
  echo "error: -n must be a positive integer (got '$TOP_N')" >&2; exit 2
fi

ADB_BIN="${ADB:-adb}"
command -v "$ADB_BIN" >/dev/null 2>&1 || {
  echo "error: adb not found in PATH (set ADB=/path/to/adb to override)" >&2; exit 127; }

adbq() { "$ADB_BIN" -s "$SERIAL" "$@"; }

state="$(adbq get-state 2>/dev/null || true)"
if [ "$state" != "device" ]; then
  echo "error: device '$SERIAL' is not ready (adb get-state: '${state:-no such device}')" >&2
  echo "       start the emulator or plug the device, then re-run." >&2
  exit 1
fi

hr()  { printf '%s\n' "------------------------------------------------------------"; }
tab() { printf '%10s  %s\n' "$1" "$2"; }

# Device-side helpers run in ONE adb round-trip each; parsing happens locally.
# toybox `du` prints "<kb>\t<path>"; we only need those two fields.
du_data() {
  adbq shell 'du -k -s /data/user/0/* 2>/dev/null' 2>/dev/null | tr -d '\r' || true
}
du_cache() {
  adbq shell 'du -k -s /data/user/0/*/cache 2>/dev/null' 2>/dev/null | tr -d '\r' || true
}
# `stat -c %s` with a `wc -c` fallback for builds whose toybox lacks it.
apk_sizes() {
  adbq shell 'pm list packages -f 2>/dev/null | sed "s/^package://" | while IFS= read -r f; do
               s=$(stat -c %s "$f" 2>/dev/null) || s=""
               [ -n "$s" ] || s=$(wc -c < "$f" 2>/dev/null || echo 0)
               [ -n "$s" ] || s=0
               echo "$s $f"
             done' 2>/dev/null | tr -d '\r' || true
}

echo "============================================================"
echo " AppleUX storage report  —  serial=$SERIAL  (READ-ONLY)"
echo " $(adbq shell getprop ro.build.release 2>/dev/null | tr -d '\r' || echo '?') / $(adbq shell getprop ro.product.model 2>/dev/null | tr -d '\r' || echo '?')"
echo "============================================================"

# ---------------------------------------------------------------- 1. /data df
echo
echo "[1] /data filesystem"
hr
dfout="$(adbq shell 'df -k /data 2>/dev/null' 2>/dev/null | tr -d '\r' || true)"
if [ -z "$dfout" ]; then
  echo "  (df unavailable on this build)"
else
  # last line: Filesystem 1K-blocks Used Available Use% Mounted-on
  printf '%s\n' "$dfout" | tail -n 1 | awk '{
    printf "  total      %8.1f GB\n", $2/1048576;
    printf "  used       %8.1f GB\n", $3/1048576;
    printf "  available  %8.1f GB\n", $4/1048576;
    printf "  use%%       %8s\n", $5;
  }'
fi

# ------------------------------------------------- 2. per-app data + cache
if [ "$WANT_DATA" -eq 1 ]; then
  echo
  echo "[2] per-app data + cache, top $TOP_N  (/data/user/0/<pkg>)"
  hr
  printf '%10s  %s\n' "MB" "PACKAGE"
  du_data | awk -v n="$TOP_N" '
    NF >= 2 { k=$1; p=$2; sub(/.*\//,"",p); printf "%12.1f  %s\n", k/1024, p }
  ' | sort -rn | head -n "$TOP_N"
fi

# -------------------------------------------------------- 3. cache only
if [ "$WANT_DATA" -eq 1 ]; then
  echo
  echo "[3] per-app cache, top $TOP_N   (cheapest reclaimable)"
  hr
  printf '%10s  %s\n' "MB" "PACKAGE"
  du_cache | awk '
    NF >= 2 { k=$1; p=$2; sub(/^.*\//,"",p); printf "%12.1f  %s\n", k/1024, p }
  ' | sort -rn | head -n "$TOP_N"
fi

# ------------------------------------------------------- 4. APK sizes
if [ "$WANT_APK" -eq 1 ]; then
  echo
  echo "[4] installed APK sizes, top $TOP_N  (size of a disable candidate)"
  hr
  printf '%10s  %s\n' "MB" "PATH"
  apk_sizes | awk -v n="$TOP_N" '
    NF >= 2 { b=$1; f=$2; printf "%12.1f  %s\n", b/1048576, f }
  ' | sort -rn | head -n "$TOP_N"
fi

# --------------------------------------------------- 5. partition totals
echo
echo "[5] /data subdirectory totals"
hr
printf '%10s  %s\n' "MB" "PATH"
adbq shell 'du -k -s /data/app /data/dalvik-cache /data/local/tmp /data/media 2>/dev/null' \
  2>/dev/null | tr -d '\r' \
  | awk 'NF >= 2 { printf "%12.1f  %s\n", $1/1024, $2 }' \
  | sort -rn || echo "  (du unavailable)"

# ------------------------------------------------------ 6. reclaimable
echo
echo "[6] headline"
hr
cache_total="$(du_cache | awk '{ s += $1 } END { printf "%.1f", s/1024 }' 2>/dev/null || echo 0)"
data_total="$(du_data  | awk '{ s += $1 } END { printf "%.1f", s/1024 }' 2>/dev/null || echo 0)"
printf '  per-app data+cache total   %10s MB\n' "${data_total:-0}"
printf '  per-app cache (reclaimable)%10s MB\n' "${cache_total:-0}"
echo
echo "  Reclaiming cache needs no debloat decision — it is free."
echo "  APK bytes are reclaimed by DISABLE, not by clearing data:"
echo "  see scripts/debloat-disable.sh."
echo
echo "NOTE: no device measurement has been taken on the POCO M5 yet."
echo "      Run this on the device and paste the output into"
echo "      deliverables/m2/storage-ram-baseline.md to replace its estimates."