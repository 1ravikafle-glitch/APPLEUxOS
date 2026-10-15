#!/usr/bin/env bash
# Prepare a freshly booted emulator/AVD for AppleUX testing.
#
# Both grants below are RUNTIME state — they are NOT baked into the system
# image. A clean boot has neither, and without them:
#   * SYSTEM_ALERT_WINDOW  -> Control Center and Notification Center cannot
#     be added to the window manager at all, so every panel gesture is a
#     silent no-op (milestone log §34, cost a full session to find).
#   * notification listener -> the shade is permanently empty.
#
# Usage: scripts/prep-emulator-grants.sh [serial]   (default: emulator-5554)
set -euo pipefail

SERIAL="${1:-emulator-5554}"
# Array, not a string: quoting "adb -s serial" makes it one command name.
ADB=(adb -s "${SERIAL}")

echo "==> waiting for ${SERIAL}"
"${ADB[@]}" wait-for-device
for _ in $(seq 1 60); do
  [ "$("${ADB[@]}" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ] && break
  sleep 5
done
echo "    boot_completed=$("${ADB[@]}" shell getprop sys.boot_completed | tr -d '\r')"

# 1. Overlay windows for Control Center / Notification Center.
"${ADB[@]}" shell appops set com.appleux.systemui SYSTEM_ALERT_WINDOW allow
echo "==> SYSTEM_ALERT_WINDOW = $("${ADB[@]}" shell appops get com.appleux.systemui SYSTEM_ALERT_WINDOW | tr -d '\r')"

# 2. Real notification feed for the shade.
"${ADB[@]}" shell cmd notification \
  allow_listener com.appleux.systemui/.AppleuxNotificationListener >/dev/null 2>&1 || true
echo "==> listeners:"
"${ADB[@]}" shell settings get secure enabled_notification_listeners \
  | tr -d '\r' | tr ':' '\n' | sed 's/^/    /'

# 3. Launcher needs the shared-config signature permission; that comes from
#    the signature-level manifest entry, so nothing to do here. Verify only.
"${ADB[@]}" shell dumpsys package com.appleux.systemui | grep -m1 codePath \
  | sed 's/^/    /'
echo "==> prep complete"