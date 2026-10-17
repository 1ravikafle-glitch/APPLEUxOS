#!/bin/bash
# Stock UI removal runs BEFORE/AFTER image surgery as noted.
#
# Phase 1 — live, reversible, emulator can stay up:
#   pm disable-user <stock pkg>   (persists across reboot, lost on wipe)
#
# Phase 2 — image level, durable across wipe. EMULATOR MUST BE STOPPED
# (guest holds system_ext mounted; writing under it corrupts live reads):
#   python3 surgery below removes the APK dir from the system_ext extent
#   inside osbuild/out/appleux-system.img. system_ext mounts WITHOUT
#   dm-verity (only the system partition has a hashtree descriptor in our
#   vbmeta), so no re-sign is needed for system_ext edits.
#
# Deletion gates (charter: never break functionality for looks):
#   launcher  DONE — ours is HOME holder (role, priority 1000), provisioned,
#             recents implemented in our systemui overlay.
#   settings  GATED — our Settings handles android.settings.SETTINGS only;
#             stock SettingsGoogle stays until per-action coverage is audited
#             (our own launcher fires WIFI/DISPLAY/SOUND/APPS settings).
#   systemui  GATED — ours is an overlay architecture over the stock
#             pipeline (status bar, keyguard, notifications, volume, power
#             menu all stock-provided). Deleting SystemUIGoogle bricks all of
#             those until we own each surface. Track per-surface, not wholesale.
set -euo pipefail

STOCK_LAUNCHER=comoogle.android.apps.nexuslauncher
STOCK_LAUNCHER=com.google.android.apps.nexuslauncher
A=/home/elfakiris/OsProject/android-sdk/platform-tools/adb

phase1_launcher() {
    # Instant fallback removal, reversible with `pm enable`.
    $A shell "pm disable-user --user 0 $STOCK_LAUNCHER" | tail -1
    $A shell "dumpsys role 2>/dev/null" | grep -A3 'name=android.app.role.HOME' | grep holders
}

phase2_image_delete() {
    # $1 = apk dir name under /priv-app in system_ext (e.g. NexusLauncherRelease)
    # $2 = image path (default osbuild/out/appleux-system.img)
    python3 - "$1" "${2:-osbuild/out/appleux-system.img}" << 'EOF'
import sys
dirname, imgpath = sys.argv[1], sys.argv[2]
base, size = 0x3f400000, 243351552  # system_ext extent (ext4, no verity)
assert size % 512 == 0
img = open(imgpath, "r+b")
img.seek(base)
blob = bytearray(img.read(size))
assert len(blob) == size, len(blob)
open("/tmp/opencode/stx-work.bin", "wb").write(blob)
print("extracted; now run debugfs rm by hand:")
print(f"  debugfs -w -f cmds /tmp/opencode/stx-work.bin   # rm /priv-app/{dirname}")
print("  e2fsck -y -f /tmp/opencode/stx-work.bin          # must be clean")
print(f"  then write back at offset {base:#x} and verify")
EOF
}

case "${1:-}" in
    phase1_launcher) phase1_launcher ;;
    phase2_image_delete) phase2_image_delete "${2:-NexusLauncherRelease}" "${3:-}" ;;
    *) echo "usage: $0 phase1_launcher | phase2_image_delete [dirname] [image]"; exit 2 ;;
esac
