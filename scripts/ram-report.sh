#!/usr/bin/env bash
# AppleUX RAM report — READ-ONLY adb memory inventory for the 4 GB target.
#
# The POCO M5 has 4 GB physical RAM but AGENTS.md sets the operating budget at
# 3 GB. Every disabled package is a resident process competing for that budget,
# so this script measures the RAM half of the same problem storage-report.sh
# measures the storage half.
#
# Reports:
#   1. /proc/meminfo — total vs available, plus swap/zram accounting
#   2. dumpsys meminfo — platform view of RAM, per-process category breakdown
#   3. per-AppleUX-package dumpsys meminfo (launcher, SystemUI, Settings,
#      performance controller, design-system) — the modules that must hold
#      their budgets
#   4. process count + top processes by RSS
#   5. LMKD activity, if visible (dumpsys activity lmk, lmkd debugfs, logcat)
#   6. reclaimable RAM headline (cached + swap)
#
# READ-ONLY GUARANTEE: every command below is `dumpsys`, `ps`, `cat`, `getprop`
# or `logcat -d` (dump, not a reader). There is no `am force-stop`, no
# `kill`, no `settings put`, no `stop`/`start` of any service, and no push.
# Nothing this script does can change device behaviour.
#
# usage: ram-report.sh [serial] [--deep]
#   serial   adb device serial (default: emulator-5554, or $ANDROID_SERIAL if set)
#   --deep   also dump the full `dumpsys meminfo` table, not just the summary
#
# examples:
#   ./ram-report.sh emulator-5554
#   ./ram-report.sh --deep > /tmp/ram.txt
#
# exit codes: 0 ok, 2 usage, 127 adb missing, 1 device unreachable

set -euo pipefail

SERIAL="${ANDROID_SERIAL:-emulator-5554}"
DEEP=0

usage() { awk 'NR>1 && /^#/ { sub(/^# ?/, ""); print; next } NR>1 { exit }' "$0"; }

while [ "$#" -gt 0 ]; do
  case "$1" in
    --deep)    DEEP=1 ;;
    -h|--help) usage; exit 0 ;;
    -*)        echo "error: unknown option: $1" >&2; usage >&2; exit 2 ;;
    *)         SERIAL="$1" ;;
  esac
  shift
done

ADB_BIN="${ADB:-adb}"
command -v "$ADB_BIN" >/dev/null 2>&1 || {
  echo "error: adb not found in PATH (set ADB=/path/to/adb to override)" >&2; exit 127; }

adbq() { "$ADB_BIN" -s "$SERIAL" "$@"; }
sh_()  { adbq shell "$@" 2>/dev/null | tr -d '\r' || true; }

state="$(adbq get-state 2>/dev/null || true)"
if [ "$state" != "device" ]; then
  echo "error: device '$SERIAL' is not ready (adb get-state: '${state:-no such device}')" >&2
  echo "       start the emulator or plug the device, then re-run." >&2
  exit 1
fi

# AppleUX module packages (see appleux/*/src/main/AndroidManifest.xml).
APPLEUX_PKGS="com.appleux.launcher com.appleux.systemui com.appleux.settings com.appleux.performance com.appleux.design"

hr()  { printf '%s\n' "------------------------------------------------------------"; }
kv()  { printf '  %-26s %s\n' "$1" "$2"; }
mb()  { awk -v k="${1:-0}" 'BEGIN { printf "%.1f MB", k/1024 }'; }

echo "============================================================"
echo " AppleUX RAM report  —  serial=$SERIAL  (READ-ONLY)"
echo " $(sh_ getprop ro.build.release) / $(sh_ getprop ro.product.model)  (target: 4 GB physical, 3 GB budget)"
echo "============================================================"

# ------------------------------------------------------- 1. /proc/meminfo
echo
echo "[1] /proc/meminfo — total vs available"
hr
mi="$(sh_ cat /proc/meminfo)"
if [ -z "$mi" ]; then
  echo "  (unavailable)"
else
  getk() { awk -v k="$1" '$1 == k":" { print $2; found=1 } END { if (!found) print 0 }' <<<"$mi"; }
  TOTAL=$(getk MemTotal); AVAIL=$(getk MemAvailable); FREE=$(getk MemFree)
  CACHED=$(getk Cached); BUFFERS=$(getk Buffers); SWAP_T=$(getk SwapTotal); SWAP_F=$(getk SwapFree)
  USED=$(( TOTAL - AVAIL ))
  kv "MemTotal"     "$(mb "$TOTAL")   <- the 4 GB ceiling"
  kv "MemAvailable" "$(mb "$AVAIL")   <- what an app may actually use"
  kv "MemFree"      "$(mb "$FREE")"
  kv "MemUsed (T-A)" "$(mb "$USED")"
  kv "Buffers"      "$(mb "$BUFFERS")"
  kv "Cached"       "$(mb "$CACHED")"
  kv "SwapTotal"    "$(mb "$SWAP_T")"
  kv "SwapFree"     "$(mb "$SWAP_F")"
  if [ "$TOTAL" -gt 0 ]; then
    kv "available %" "$(awk -v a="$AVAIL" -v t="$TOTAL" 'BEGIN { printf "%.1f%%", 100*a/t }')"
  fi
fi

# ---------------------------------------------------- 2. dumpsys meminfo
echo
echo "[2] dumpsys meminfo — platform view"
hr
dmi="$(sh_ dumpsys meminfo)"
if [ -z "$dmi" ]; then
  echo "  (unavailable)"
else
  # Header table: Total RAM / Free RAM / Used RAM / Lost RAM
  awk '/Total RAM:/ { exit } /Total RAM|Free RAM|Used RAM|Lost RAM/ { print "  " $0 }' <<<"$dmi"
  echo
  echo "  --- largest processes by PSS ---"
  printf '%10s  %s\n' "MB" "PROCESS"
  awk '
    /^ *App Summary/ { inapp=1; next }
    /^[0-9]+: / { }
    /Total PSS:/ { inapp=0 }
    inapp && /^ *Total PSS:/ { }
  ' <<<"$dmi" >/dev/null 2>&1 || true
  # Robust extraction: the "Total PSS by process" section lists "  12345 Pss Total: ..."
  sed -n '/Total PSS by process/,/^$/p' <<<"$dmi" \
    | grep -E 'Pss Total:' \
    | awk '{ for (i=1;i<=NF;i++) if ($i ~ /^[0-9]+$/ && $(i+1)=="Pss") { print $(i)/1024, $1; break } }' \
    | sort -rn | head -15 | awk '{ printf "%10.1f  %s\n", $1, $2 }' \
    || echo "  (per-process table not present in this dumpsys build)"
fi

if [ "$DEEP" -eq 1 ]; then
  echo
  echo "[2b] full dumpsys meminfo"
  hr
  printf '%s\n' "$dmi"
fi

# ------------------------------------------ 3. per-AppleUX-package meminfo
echo
echo "[3] AppleUX module meminfo"
hr
printf '%-28s %10s  %s\n' "PACKAGE" "PSS(MB)" "NOTE"
for p in $APPLEUX_PKGS; do
  if ! sh_ pm list packages | grep -qxF "package:$p"; then
    printf '%-28s %10s  %s\n' "$p" "-" "not installed"
    continue
  fi
  m="$(sh_ dumpsys meminfo "$p")"
  # "TOTAL PSS:" line -> TOTAL row, or "App Summary" line
  pss_kb="$(awk '/^[[:space:]]*TOTAL[[:space:]]+PSS:/ { print $3; found=1 } END { if(!found) print "" }' <<<"$m")"
  [ -n "${pss_kb:-}" ] || pss_kb="$(awk '/App Summary/ { print $3; found=1 } END { if(!found) print "" }' <<<"$m")"
  if [ -n "${pss_kb:-}" ]; then
    printf '%-28s %10s  %s\n' "$p" "$(mb "$pss_kb")" ""
  else
    printf '%-28s %10s  %s\n' "$p" "?" "installed, no PSS row (never ran?)"
  fi
done
echo
echo "  Targets from AGENTS.md (3 GB budget, DEVICE NUMBERS NOT YET MEASURED):"
echo "    Launcher      <= 150 MB PSS"
echo "    SystemUI      <= 150 MB PSS"
echo "    settings      <= 150 MB PSS"
echo "    system_server <= 300 MB RSS"

# ---------------------------------------------------- 4. process count
echo
echo "[4] process count and top RSS"
hr
pcount="$(sh_ 'ps -A 2>/dev/null | wc -l')"
ucount="$(sh_ 'ps -A -u 0 2>/dev/null | wc -l')"
kv "processes (all users)" "$pcount"
kv "processes (uid 0)"     "$ucount"
echo
printf '%10s  %s\n' "RSS(MB)" "PROCESS"
sh_ 'ps -A -o rss=,args= 2>/dev/null' \
  | awk 'NF >= 2 { r=$1; $1=""; sub(/^[ \t]+/,""); printf "%10.1f  %s\n", r/1024, $0 }' \
  | sort -rn | head -15 \
  || echo "  (ps -o not supported by this toybox)"

# ---------------------------------------------------- 5. LMKD activity
echo
echo "[5] LMKD (low memory killer) activity — best effort, may be invisible"
hr
lmk="$(sh_ dumpsys activity lmk)"
if [ -n "$lmk" ]; then
  printf '%s\n' "$lmk" | sed 's/^/  /' | head -20
else
  echo "  dumpsys activity lmk: no output"
fi
echo
echo "  lmkd stats (debugfs, usually root-only):"
dbg="$(sh_ 'for f in /sys/kernel/mm/lmkd/lmkd_*; do [ -r "$f" ] && echo "$f=$(cat "$f")"; done 2>/dev/null')"
if [ -n "$dbg" ]; then
  printf '%s\n' "$dbg" | sed 's/^/  /'
else
  echo "    (not readable without root — expected on a retail build)"
fi
echo
echo "  recent lmkd kill log lines:"
kl="$(sh_ 'logcat -d -b all 2>/dev/null | grep -i "lmkd\|lowmemorykiller" | tail -n 15')"
if [ -n "$kl" ]; then
  printf '%s\n' "$kl" | sed 's/^/    /'
else
  echo "    (no lmkd lines in the logcat ring buffer, or logcat unreadable)"
fi

# ---------------------------------------------------- 6. reclaimable
echo
echo "[6] reclaimable RAM headline"
hr
mi2="$(sh_ cat /proc/meminfo)"
if [ -z "$mi2" ]; then
  echo "  (unavailable)"
else
  getk2() { awk -v k="$1" '$1 == k":" { print $2; found=1 } END { if (!found) print 0 }' <<<"$mi2"; }
  C2=$(getk2 Cached); S2=$(getk2 SwapFree); A2=$(getk2 MemAvailable)
  kv "page cache (Cached)"   "$(mb "$C2")"
  kv "free swap"             "$(mb "$S2")"
  kv "MemAvailable"          "$(mb "$A2")"
  echo
  zr="$(sh_ 'cat /sys/block/zram0/mm_stat 2>/dev/null')"
  if [ -n "$zr" ]; then
    echo "  zram mm_stat (orig_data_size compr_data_size mem_used_total ...):"
    echo "    $zr"
  fi
fi
echo
echo "  Resident-process reduction is the point of scripts/debloat-disable.sh:"
echo "  a disabled package is both an APK (storage-report.sh) and a resident"
echo "  process (this script)."
echo
echo "NOTE: these are EMULATOR numbers. Do not quote them as device results;"
echo "      replace the estimates in deliverables/m2/storage-ram-baseline.md"
echo "      with output captured on the POCO M5."