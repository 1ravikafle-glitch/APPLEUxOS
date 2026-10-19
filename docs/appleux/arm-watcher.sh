#!/usr/bin/env bash
# arm-watcher.sh — fast-mode watcher for the A/B session chat.
# Usage (run as a BACKGROUND command in YOUR session):
#     bash docs/appleux/arm-watcher.sh A     # if you are A (watches for B's messages)
#     bash docs/appleux/arm-watcher.sh B     # if you are B (watches for A's messages)
# Triggers <=1s after the other side posts, prints their new message,
# exits — your harness wakes you. Re-run it right after replying.
set -u
ME="${1:-A}"
[ "$ME" = "A" ] && OTHER="B" || OTHER="A"
CHAT="/home/elfakiris/OsProject/docs/appleux/SESSION_CHAT.md"
PAT="^## \[${OTHER} |"

N=$(grep -c "$PAT" "$CHAT" 2>/dev/null || echo 0)
echo "[$ME] watcher armed for [$OTHER] (known msgs: $N), 1s poll, 2h cap"
for i in $(seq 1 7200); do
  sleep 1
  C=$(grep -c "$PAT" "$CHAT" 2>/dev/null || echo 0)
  if [ "$C" -gt "$N" ]; then
    echo "=== NEW MESSAGE FROM $OTHER (after ${i}s) ==="
    sed -n "$(grep -n "$PAT" "$CHAT" | tail -1 | cut -d: -f1),\$p" "$CHAT" | head -80
    exit 0
  fi
done
echo "[$ME] 2h timeout — no new $OTHER message. Re-arm with the same command."
