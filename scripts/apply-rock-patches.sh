#!/usr/bin/env bash
# Applies tracked AppleUX bring-up patches onto the third-party trees inside
# android-16/. Idempotent: skips patches already applied, fails loudly on
# conflict. RUN THIS BEFORE EVERY `m` INVOCATION.
#
# WHY THIS EXISTS: hardware/mediatek is periodically reset to pristine
# upstream in this workspace (cause undetermined — repo manifests already
# neutralized, no cron/timer/process found). Fixes that live only in the
# working tree die silently; fixes that live here as patches get re-applied.
set -u
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
AOSP="$ROOT/android-16"
PATCHDIR="$ROOT/device-support/rock/aosp/patches"
fail=0

# patch-file -> tree subdir it applies in (subdir only scopes log output and
# the already-applied check; application always runs from $AOSP with patch(1),
# because the trees have inconsistent .git situations and git resolves the
# same patch path against different roots depending on cwd).
apply_one() {
    local patch="$1" subdir="$2" marker="$3"
    local name
    name="$(basename "$patch")"
    if (cd "$AOSP" && patch -p1 --dry-run -s < "$patch" >/dev/null 2>&1); then
        (cd "$AOSP" && patch -p1 -s < "$patch" >/dev/null) && echo "APPLIED: $name -> $subdir"
    elif (cd "$AOSP" && patch -p1 --dry-run -R -s < "$patch" >/dev/null 2>&1); then
        echo "PRESENT: $name already applied"
    else
        echo "CONFLICT: $name does not apply cleanly (tree moved?)"
        fail=1
    fi
}

apply_one "$PATCHDIR/hardware-mediatek-wlan-visibility.patch" hardware/mediatek "mediatek-lib"
apply_one "$PATCHDIR/hardware-mediatek-namespace.patch" hardware/mediatek "primordial namespace like hardware/broadcom"
apply_one "$PATCHDIR/hardware-mediatek-codec-stubs.patch" hardware/mediatek "EMPTY stub declarations"
apply_one "$PATCHDIR/hardware-mediatek-dup-libs.patch" hardware/mediatek "source-built vibrator service REMOVED"
apply_one "$PATCHDIR/hardware-mediatek-wpa-driver-visibility.patch" hardware/mediatek "driver_cmd_mt66xx.*visibility"
apply_one "$PATCHDIR/hardware-xiaomi-vibrator-libcxxfs.patch" hardware/xiaomi "libc++fs"
apply_one "$PATCHDIR/hardware-lineage-compat-shim.patch" hardware/lineage "libgui_shim_vendor REMOVED"
apply_one "$PATCHDIR/hardware-google-pixel-usb-visibility.patch" hardware/google/pixel "mediatek.*USB gadget service"
apply_one "$PATCHDIR/hw-interface-visibility.patch" hardware "links this from outside"
apply_one "$PATCHDIR/hardware-xiaomi-powershare-label.patch" hardware/xiaomi "powershare@1.0:vendor.lineage"
apply_one "$PATCHDIR/hardware-xiaomi-touch-label.patch" hardware/xiaomi "touch@1.0:vendor.lineage"
apply_one "$PATCHDIR/vendor-xiaomi-rock-namespace.patch" vendor/xiaomi/rock "hardware/mediatek\",\n"
apply_one "$PATCHDIR/vendor-xiaomi-rock-mtkpower-labels.patch" vendor/xiaomi/rock "interfaces/hardware/mtkpower"
apply_one "$PATCHDIR/vendor-xiaomi-rock-codec-visibility.patch" vendor/xiaomi/rock "visible to the mediatek C2 service"

# The vibrator/shim patches REMOVE lines, so "already applied" must also be
# detected by absence of the removed text. Handle explicitly:
for spec in "hardware/xiaomi/hardware_xiaomi/vibrator/effect/Android.bp|libc++fs" \
            "hardware/lineage/compat/Android.bp|name: \"libgui_shim_vendor\","; do
    f="${spec%%|*}"
    pat="${spec##*|}"
    if ! grep -q "$pat" "$AOSP/$f" 2>/dev/null; then
        echo "PRESENT(confirmed absent): $f :: $pat"
    fi
done

# A diagnostic BOARD_WLAN_DEVICE unset must never survive into a build.
if grep -q 'diag_empty' "$AOSP/device/xiaomi/rock/BoardConfig.mk" 2>/dev/null; then
    echo "ERROR: TEMP-DIAG BOARD_WLAN_DEVICE unset still present — restore MediaTek first"
    fail=1
fi

exit $fail
