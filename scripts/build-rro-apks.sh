#!/bin/bash
# AppleUX RRO (runtime resource overlay) builder.
#
# RROs carry no code: aapt2 link-only against android.jar, then zipalign +
# apksigner with the shared debug key. Separate GEN/OUT dirs so this never
# races scripts/build-emulator-apks.sh.
#
# Test on userdebug (CF/emulator): adb install -r <apk> &&
#   adb shell cmd overlay enable --user 0 <package>
# For the ROM, add the built APK to PRODUCT_PACKAGES (preinstalled overlays
# apply at boot without manual enable).

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$ROOT/tools/envsetup.sh" >/dev/null 2>&1

SDK="$ANDROID_HOME"
PLATFORM="$SDK/platforms/android-36/android.jar"
BT="$SDK/build-tools/36.0.0"
AAPT2="$BT/aapt2"
ZIPALIGN="$BT/zipalign"
APKSIGNER="$BT/apksigner"

GEN="$ROOT/out/gen-rro"
OUT="$ROOT/out/emulator/rro"
mkdir -p "$GEN" "$OUT"

# RROs targeting platform packages (android, com.android.systemui, …) must be
# signed with the PLATFORM key, or the overlay manager rejects them
# (overlay/target certificate mismatch). userdebug builds (CF, emulator) use
# the AOSP testkey, so RROs take it — app APKs stay on debug.keystore.
TESTKEY="$ROOT/android-16/build/target/product/security/testkey"
[ -f "$TESTKEY.pk8" ] || { echo "  FAILED testkey missing at $TESTKEY.pk8"; exit 1; }

build_rro() {
  local module="$1" pkg="$2"
  local manifest="$ROOT/appleux/$module/AndroidManifest.xml"
  local resdir="$ROOT/appleux/$module/res"
  echo "==> $module ($pkg)"
  rm -f "$GEN/$module-res.zip" "$GEN/$module-unsigned.apk"
  "$AAPT2" compile --dir "$resdir" -o "$GEN/$module-res.zip" >/dev/null
  "$AAPT2" link \
    -o "$GEN/$module-unsigned.apk" \
    -I "$PLATFORM" \
    --manifest "$manifest" \
    --min-sdk-version 26 \
    --target-sdk-version 36 \
    --auto-add-overlay \
    "$GEN/$module-res.zip" 2>&1 | grep -v "^$" | sed "s|^|    link: |" || true
  [ -f "$GEN/$module-unsigned.apk" ] || { echo "  FAILED aapt2 link"; exit 1; }
  "$ZIPALIGN" -f 4 "$GEN/$module-unsigned.apk" "$GEN/$module-aligned.apk"
  "$APKSIGNER" sign \
    --key "$TESTKEY.pk8" --cert "$TESTKEY.x509.pem" \
    --out "$OUT/$pkg.apk" "$GEN/$module-aligned.apk" >/dev/null 2>&1
  [ -f "$OUT/$pkg.apk" ] \
    && echo "  OK         $pkg.apk ($(du -h "$OUT/$pkg.apk" | cut -f1))" \
    || { echo "  FAILED     $pkg.apk"; exit 1; }
}

echo "AppleUX RRO build"
build_rro rro-frameworks com.appleux.overlay.frameworks
echo
ls -la "$OUT"/*.apk
