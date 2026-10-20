#!/usr/bin/env bash
# arm-watcher.sh — ALWAYS-ARMED fast-mode watcher for the A/B/C session chat.
# Usage (run as a BACKGROUND command in YOUR session):
#     bash /home/elfakiris/OsProject/docs/appleux/arm-watcher.sh A   # you are A
#     bash ... B | C likewise
#
# ALWAYS-ARMED design:
#   - On TIMEOUT: relaunches itself (exec) — the armed state never lapses.
#   - Per-session lock (flock): exactly one watcher per letter at a time.
#   - Triggers <=1s after ANY other session posts; prints their message and
#     exits — your harness wakes you. Your discipline: after processing,
#     re-run this same line as your FIRST action.
set -u
ME="${1:-A}"
CHAT="/home/elfakiris/OsProject/docs/appleux/SESSION_CHAT.md"
LOCK="/tmp/opencode/watcher-${ME}.lock"
mkdir -p /tmp/opencode

# Exactly one watcher per session letter. If an old one somehow still runs,
# wait briefly for it to finish (it exits on message or self-relaunches).
exec 8>"$LOCK"
flock -w 3 8 || { echo "[$ME] another watcher already armed — nothing to do."; exit 0; }

count_others() {
  local total=0 n
  for L in A B C; do
    [ "$L" = "$ME" ] && continue
    n=$(grep -c "^## \[$L |" "$CHAT" 2>/dev/null || true)
    total=$((total + ${n:-0}))
  done
  echo "$total"
}

N=$(count_others)
echo "[$ME] watcher ALWAYS-ARMED for [A/B/C minus me] (known: $N), 1s poll, self-relaunch on timeout"
for i in $(seq 1 14400); do
  sleep 1
  C=$(count_others)
  if [ "$C" -gt "$N" ]; then
    echo "=== NEW MESSAGE (after ${i}s) ==="
    for L in A B C; do
      [ "$L" = "$ME" ] && continue
      LN=$(grep -n "^## \[$L |" "$CHAT" | tail -1 | cut -d: -f1)
      [ -n "$LN" ] && echo "$LN newest_$L"
    done | sort -t_ -k2 -rn | head -1 | {
      read -r LN rest
      sed -n "${LN},\$p" "$CHAT" | head -80
    }
    exit 0
  fi
done
echo "[$ME] 4h cap reached — relaunching self (still armed)."
exec bash "$0" "$ME"
