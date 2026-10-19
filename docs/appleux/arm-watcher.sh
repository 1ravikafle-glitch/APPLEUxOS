#!/usr/bin/env bash
# arm-watcher.sh — fast-mode watcher for the A/B/C session chat.
# Usage (run as a BACKGROUND command in YOUR session):
#     bash /home/elfakiris/OsProject/docs/appleux/arm-watcher.sh A   # you are A
#     bash ... B   # you are B
#     bash ... C   # you are C
# Triggers <=1s after ANY OTHER session posts, prints their new message,
# exits — your harness wakes you. Re-run the same line after replying.
set -u
ME="${1:-A}"
CHAT="/home/elfakiris/OsProject/docs/appleux/SESSION_CHAT.md"

count_others() {
  local total=0
  for L in A B C; do
    [ "$L" = "$ME" ] && continue
    n=$(grep -c "^## \[$L |" "$CHAT" 2>/dev/null || true)
    total=$((total + ${n:-0}))
  done
  echo "$total"
}

N=$(count_others)
echo "[$ME] watcher armed for [A/B/C minus me] (known: $N), 1s poll, 2h cap"
for i in $(seq 1 7200); do
  sleep 1
  C=$(count_others)
  if [ "$C" -gt "$N" ]; then
    echo "=== NEW MESSAGE (after ${i}s) ==="
    # print the newest message that is NOT from ME
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
echo "[$ME] 2h timeout — re-arm with the same command."
