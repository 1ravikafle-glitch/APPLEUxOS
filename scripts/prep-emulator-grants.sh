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
# com.appleux.systemui was DELETED 2026-10-17 (deliverables/m23). It needed
# SYSTEM_ALERT_WINDOW because it floated TYPE_APPLICATION_OVERLAY windows over
# SystemUI; inside SystemUI there are no overlay windows and no such grant.
echo "==> (no SYSTEM_ALERT_WINDOW grant needed: the iOS chrome is inside com.android.systemui)"

# 2. Real notification feed for the shade.
"${ADB[@]}" shell cmd notification \
  # The notification listener moved INTO com.android.systemui and is declared
  # in its AndroidManifest.xml. BIND_NOTIFICATION_LISTENER_SERVICE is a
  # signature permission SystemUI already holds, so the platform connects it
  # and there is no consent to grant. See the <service> entry added there.
echo "==> listeners:"
"${ADB[@]}" shell settings get secure enabled_notification_listeners \
  | tr -d '\r' | tr ':' '\n' | sed 's/^/    /'

# 3. Launcher needs the shared-config signature permission; that comes from
#    the signature-level manifest entry, so nothing to do here. Verify only.
"${ADB[@]}" shell dumpsys package com.android.systemui | grep -m1 codePath \
  | sed 's/^/    /'
echo "==> prep complete"