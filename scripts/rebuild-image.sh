#!/bin/bash
# Rebuild the bootable AppleUX system image from current build outputs.
#
# Full proven flow (rootless, no sudo, no loop mounts):
#   1. dd-extract the system logical partition from the current image
#   2. inject current APKs + RROs via debugfs (inject-rootfs.sh)
#   3. e2fsck, shrink, re-expand with hashtree headroom
#   4. avbtool add_hashtree_footer (fresh salt, no FEC) + flags-0 vbmeta
#   5. assemble-image.py back into the target + update VerifiedBootParams
#
# usage: rebuild-image.sh
# Idempotent: safe to re-run; keeps one rotating backup of the target image.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OSB="$ROOT/osbuild"
SYSDIR="$ROOT/android-sdk/system-images/android-36/google_apis/x86_64-appleux"
TARGET="$OSB/out/appleux-system.img"
WORK="$OSB/work"
APKDIR="$ROOT/out/emulator/apks"
RRODIR="$ROOT/out/emulator/rro"

SUPER_OFF=2097152
ROOTFS_LBA=2048
ROOTFS_SECTORS=2050712
ROOTFS_OFF=$((SUPER_OFF + ROOTFS_LBA * 512))
ROOTFS_LEN=$((ROOTFS_SECTORS * 512))

PART="$WORK/sys-part-rebuild.bin"

echo "==> backup target"
cp -f "$TARGET" "$TARGET.prev"
echo "    backup: $TARGET.prev"

echo "==> extract system partition"
dd if="$TARGET" of="$PART" bs=512 skip=6144 count="$ROOTFS_SECTORS" status=none
[ "$(stat -c%s "$PART")" = "$ROOTFS_LEN" ] || { echo "FATAL: bad extract size"; exit 1; }

echo "==> inject APKs + RROs"
bash "$OSB/inject-rootfs.sh" "$PART" "$APKDIR" "$WORK" "$RRODIR" 2>&1 | tail -n 3

echo "==> shrink for hashtree headroom"
e2fsck -f -y "$PART" >/dev/null 2>&1 || true
MIN_BLOCKS=$(resize2fs -M "$PART" 2>&1 | grep -o -E "to [0-9]+ " | grep -o -E "[0-9]+")
[ -n "$MIN_BLOCKS" ] || { echo "FATAL: resize2fs -M failed"; exit 1; }
GROW_BLOCKS=$((MIN_BLOCKS + 4096))
resize2fs "$PART" "$GROW_BLOCKS" 2>&1 | tail -n 1

echo "==> hashtree + vbmeta"
# Boot-loop fix (2026-10-16, kernel-proof): the previous vbmeta was UNSIGNED
# (Algorithm NONE). init's AvbHandle::Open rejects ERROR_VERIFICATION on this
# locked emulator build ("PUBLIC_KEY_REJECTED isn't allowed") and reboots at
# ~2s forever, so adb never comes up. The stock image carries a SIGNED vbmeta
# (SHA256_RSA4096, 6848 bytes); userspace validate_vbmeta_public_key is a
# no-op, so ANY valid signature passes. Sign with the in-tree AOSP test key,
# keep flags 0 (verity enforcing) — no source change needed.
SALT=$(python3 -c "import secrets;print(secrets.token_hex(32))")
echo "$SALT" > "$WORK/verity-salt.txt"
SIGN_KEY="$ROOT/android-16/external/avb/test/data/testkey_rsa4096.pem"
[ -f "$SIGN_KEY" ] || { echo "FATAL: missing avb sign key $SIGN_KEY"; exit 1; }
python3 "$OSB/tools/avbtool.py" add_hashtree_footer \
  --image "$PART" --partition_name system \
  --partition_size "$ROOTFS_LEN" --do_not_generate_fec \
  --hash_algorithm sha256 --salt "$SALT" 2>&1 | tail -n 2
[ "$(stat -c%s "$PART")" = "$ROOTFS_LEN" ] || { echo "FATAL: footer changed size"; exit 1; }
python3 "$OSB/tools/avbtool.py" make_vbmeta_image \
  --output "$WORK/vbmeta-new.img" --flags 0 \
  --key "$SIGN_KEY" --algorithm SHA256_RSA4096 \
  --include_descriptors_from_image "$PART" >/dev/null 2>&1
DIGEST=$(python3 "$OSB/tools/avbtool.py" calculate_vbmeta_digest \
  --image "$WORK/vbmeta-new.img" 2>&1 | tr -d ' \n')
VBMETA_SIZE=$(stat -c%s "$WORK/vbmeta-new.img")
echo "    vbmeta: $VBMETA_SIZE bytes digest=$DIGEST"

echo "==> assemble"
python3 "$OSB/assemble-image.py" "$TARGET" "$PART" "$WORK/vbmeta-new.img" 2>&1 | tail -n 2

echo "==> VerifiedBootParams"
TP="$SYSDIR/VerifiedBootParams.textproto"
python3 - "$TP" "$DIGEST" "$VBMETA_SIZE" << 'EOF'
import re, sys
p, digest, size = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p).read()
s = re.sub(r'androidboot\.vbmeta\.size=\d+', f'androidboot.vbmeta.size={size}', s)
s = re.sub(r'androidboot\.vbmeta\.digest=[0-9a-f]+', f'androidboot.vbmeta.digest={digest}', s)
if 'veritymode' not in s:
    s = s.rstrip() + '\nparam: "androidboot.veritymode=enforcing"\n'
open(p, 'w').write(s)
print(open(p).read())
EOF

echo
echo "REBUILT: $TARGET (backup: $TARGET.prev)"
