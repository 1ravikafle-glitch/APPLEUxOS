#!/bin/bash
# Strip stock UI packages from the system_ext extent inside a super image.
#
# Naked-base doctrine: SystemUIGoogle, NexusLauncherRelease (+QuickStep
# inside it) and SettingsGoogle are deleted from the image; our components
# are the only UI. system_ext mounts WITHOUT dm-verity (our vbmeta carries
# a hashtree descriptor for the system partition only), so no re-sign is
# needed after this surgery.
#
# usage: strip-system-ext.sh [image]   (default osbuild/out/appleux-system.img)
# The emulator MUST be stopped: the guest holds system_ext mounted and
# writing under a live mount corrupts reads. e2fsck must report clean or
# the script aborts BEFORE touching the image (work file only).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
IMG="${1:-$ROOT/osbuild/out/appleux-system.img}"
WORK="$ROOT/osbuild/work/stx-strip.bin"

# system_ext extent: ext4 label 'system_ext' at 0x3f400000, 243351552 bytes
# (located once with osbuild/find-ext4.py; asserted by size below).
BASE=0x3f400000
SIZE=243351552

python3 - "$IMG" "$WORK" "$BASE" "$SIZE" << 'EOF'
import struct, sys
imgpath, workpath, base, size = sys.argv[1], sys.argv[2], int(sys.argv[3], 0), int(sys.argv[4])
with open(imgpath, "rb") as f:
    f.seek(base)
    blob = f.read(size)
assert len(blob) == size, f"short read {len(blob)}"
magic = struct.unpack("<H", blob[0x400 + 0x38:0x400 + 0x3A])[0]
assert magic == 0xEF53, f"not ext4 at {base:#x}"
label = blob[0x400 + 0x78:0x400 + 0x88].split(b"\x00")[0]
assert label == b"system_ext", label
open(workpath, "wb").write(blob)
print(f"extracted system_ext ({size} bytes)")
EOF

rm -f "$WORK.cmds"
for pkg in SystemUIGoogle NexusLauncherRelease SettingsGoogle; do
    for sub in "" "/oat/x86_64" "/oat"; do :; done
    {
        echo "rm /priv-app/$pkg/$pkg.apk"
        echo "rm /priv-app/$pkg/oat/x86_64/$pkg.odex"
        echo "rm /priv-app/$pkg/oat/x86_64/$pkg.vdex"
        echo "rmdir /priv-app/$pkg/oat/x86_64"
        echo "rmdir /priv-app/$pkg/oat"
        echo "rmdir /priv-app/$pkg"
    } >> "$WORK.cmds"
done
debugfs -w -f "$WORK.cmds" "$WORK" 2>&1 | grep -viE '^(debugfs|$)' | head -5 || true
leftovers=$(debugfs -R 'ls /priv-app' "$WORK" 2>/dev/null | grep -ciE 'systemuigoogle|nexuslauncher|settingsgoogle' || true)
[ "$leftovers" = "0" ] || { echo "FATAL: stock dirs remain"; exit 1; }
echo "stock UI dirs gone"
e2fsck -y -f "$WORK" > "$ROOT/osbuild/work/stx-fsck.log" 2>&1 || true
tail -2 "$ROOT/osbuild/work/stx-fsck.log"

python3 - "$IMG" "$WORK" "$BASE" << 'EOF'
import sys
imgpath, workpath, base = sys.argv[1], sys.argv[2], int(sys.argv[3], 0)
blob = open(workpath, "rb").read()
with open(imgpath, "r+b") as img:
    img.seek(base)
    img.write(blob)
    img.flush()
    import os
    os.fsync(img.fileno())
print(f"wrote back {len(blob)} bytes at {base:#x}")
EOF
rm -f "$WORK" "$WORK.cmds"
echo "STRIPPED: $IMG"
