#!/bin/bash
# Fast windowed emulator launcher for appleux36.
#
# Why this exists (bring-up log 2026-10-16):
#   * The AVD was pinned to swiftshader_indirect (pure CPU raster) despite a
#     working Intel Iris Xe (/dev/dri/renderD128 readable) -> cold boot took
#     25-45+ min. Host GPU turns that into a few minutes.
#   * The on-disk quick-boot snapshot was corrupt (goldfish_pipe state
#     error), so every boot silently cold-booted anyway. Delete it once; the
#     first CLEAN exit re-saves it and later boots are ~30-60s.
#   * CLI used to pass -no-snapshot -writable-system -show-kernel -memory
#     2048: no fast boot, extra logging overhead, RAM capped below AVD config.
#     Removed all; config.ini is the single source of truth (3072 MB / 6 cores).
#   * GPU fallback ladder: host -> angle -> swiftshader_indirect. Intel+Mesa
#     on Xwayland normally takes the first rung; the ladder exists so a driver
#     hiccup can never leave us with no emulator at all.
#
# Launch DETACHED so an opencode restart cannot kill a boot in progress:
#   setsid /home/elfakiris/OsProject/scripts/run-emulator.sh &
set -u
source /home/elfakiris/OsProject/tools/envsetup.sh >/dev/null 2>&1

LOG=/tmp/opencode/emu-launch.log
mkdir -p /tmp/opencode
: > "$LOG"

# Kill old qemu AND the emulator wrapper (it holds the AVD lock through a
# 20s graceful shutdown). Bracket tricks ([q]emu) keep pkill from matching
# this script's own command line.
pkill -f '[q]emu-system.*appleux36' 2>/dev/null
pkill -f '[e]mulator -avd appleux36' 2>/dev/null
# Wait for the AVD running-lock to clear, else the new instance FATALs with
# "multiple emulators with the same AVD".
for _ in $(seq 1 30); do
  pgrep -f 'emulator -avd appleux36' >/dev/null 2>&1 || break
  sleep 1
done
sleep 1
# Broken snapshot (state load error) is worse than none: delete, cold boot
# once, emulator re-saves a clean one on exit.
rm -rf "$ANDROID_AVD_HOME/appleux36.avd/snapshots/default_boot"

GPU_MODE=host
for attempt in 1 2 3; do
  echo "=== attempt $attempt gpu=$GPU_MODE $(date -Is) DISPLAY=$DISPLAY ===" >> "$LOG"
  # SHOW_KERNEL=1 adds the guest kernel console (diagnose boot stalls); costs
  # some overhead but is the ONLY visibility into guest boot without adb.
  # Boot experience flags: -no-boot-anim and -no-audio are INTENTIONALLY
  # absent. AppleUX ships its own bootanimation.zip (squircle + chime in
  # /system/media); suppressing them would hide the product boot screen.
  # Kept for speed: -no-metrics only.
  emulator -avd appleux36 -gpu "$GPU_MODE" -no-metrics \
    ${SHOW_KERNEL:+-show-kernel} >> "$LOG" 2>&1 &
  EMU_PID=$!
  for _ in $(seq 1 45); do
    sleep 2
    kill -0 "$EMU_PID" 2>/dev/null || break          # emulator died -> try next gpu
    # Require qemu to SURVIVE 5s: during an old instance's shutdown a dying
    # qemu satisfies pgrep and once caused a false "OK" + lock conflict.
    if pgrep -f 'qemu-system.*appleux36' >/dev/null; then
      sleep 5
      if pgrep -f 'qemu-system.*appleux36' >/dev/null && \
         ! grep -q 'FATAL' "$LOG" 2>/dev/null; then
        echo "OK qemu up (gpu=$GPU_MODE) pid=$EMU_PID" >> "$LOG"
        exit 0
      fi
      echo "attempt $attempt: qemu died or FATAL, retrying" >> "$LOG"
    fi
  done
  kill "$EMU_PID" 2>/dev/null; pkill -f 'qemu-system.*appleux36' 2>/dev/null; sleep 3
  case $GPU_MODE in
    host) GPU_MODE=angle ;;
    angle) GPU_MODE=swiftshader_indirect ;;
  esac
done
echo "FATAL: all gpu attempts failed" >> "$LOG"
exit 1
