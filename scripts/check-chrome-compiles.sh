#!/bin/bash
# Type-check the SystemUI chrome library (appleux/systemui/chrome).
#
# WHY THIS SCRIPT EXISTS
#
# The chrome module is compiled by SOONG into `com.android.systemui`
# (`appleux-systemui-chrome` in SystemUI's Android.bp static_libs), and Soong
# needs a warm AOSP tree — ~48 minutes of analysis on this box. Until then
# there is no cheap way to know whether an edit to chrome even compiles, and a
# Kotlin error in this module would only surface an hour into a build.
#
# This script closes that gap without pretending to be the real build:
#
#   * it compiles design-system + device-support/rock + sounds + chrome in ONE
#     kotlinc invocation, exactly as the apps are built, so the chrome sources
#     bind against the real design-system classes rather than a stale jar
#   * it reuses the R classes build-emulator-apks.sh already generated for
#     `com.appleux.design` (out/gen/design-system-r-classes). Regenerating them
#     here would mean a second aapt2 pass for no additional signal.
#   * it stops at `-d /tmp/...`: no dex, no packaging, no signing. This checks
#     TYPES. It does not produce an APK, and it does not prove SystemUI links —
#     only `m SystemUI` proves that.
#
# Run build-emulator-apks.sh first if out/gen/design-system-r-classes is
# missing; it generates the R classes this script consumes.
#
#   bash scripts/check-chrome-compiles.sh   -> "OK" and exit 0, or the
#                                              compiler's own errors, exit 1

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$ROOT/tools/envsetup.sh" >/dev/null 2>&1

SDK="$ANDROID_HOME"
PLATFORM="$SDK/platforms/android-36/android.jar"
KOTLINC="$ROOT/tools/kotlin/kotlinc/bin/kotlinc"
LIBS="$ROOT/thirdparty/libs"
R_CLASSES="$ROOT/out/gen/design-system-r-classes"
OUT="${TMPDIR:-/tmp}/appleux-chrome-check"

fail() { echo "FAIL: $*" >&2; exit 1; }

[ -x "$KOTLINC" ] || fail "kotlinc not found at $KOTLINC"
[ -f "$PLATFORM" ] || fail "android.jar not found at $PLATFORM"
[ -d "$R_CLASSES" ] || fail \
  "$R_CLASSES missing — run scripts/build-emulator-apks.sh first (it generates the R classes)"

CP="$PLATFORM:$R_CLASSES"
for j in "$LIBS"/*.jar; do CP="$CP:$j"; done

# Sources are passed as files, not directories, and symlinks are followed so a
# stale symlink cannot silently compile an empty module.
#
# appleux/systemui/src/main/java is deliberately NOT listed: the standalone
# com.appleux.systemui overlay APK was deleted on 2026-10-17 (deliverables/m23)
# and only chrome/ survives. Listing a path that no longer exists made `find`
# print an error and this check fail before it compiled anything.
SOURCE_DIRS=(
  "$ROOT/appleux/systemui/chrome/src/main/java"
  "$ROOT/appleux/design-system/src/main/java"
  "$ROOT/device-support/rock/src/main/java"
  "$ROOT/appleux/sounds/src/main/java"
)
for d in "${SOURCE_DIRS[@]}"; do
  [ -d "$d" ] || fail "source dir missing: $d"
done

# Files that CANNOT be type-checked here, with the reason. Each one imports
# classes that exist only in the platform's hidden API surface
# (`android.os.IPowerManager`, `android.os.ServiceManager`), which Soong exposes
# through `platform_apis: true` and the SDK android.jar deliberately does not
# export. There is no stub jar for them in this tree, so these files are
# compiled by `m SystemUI` and by nothing else.
#
# They are excluded, not skipped silently: the list is printed on every run so
# the coverage gap is visible, and `AppleuxNotchHost.kt` — the file this check
# was written for — is NOT in it.
EXCLUDE=(
  "appleux/systemui/chrome/src/main/java/com/appleux/systemui/chrome/AppleuxPowerControl.kt"
)

exclude_re=''
for e in "${EXCLUDE[@]}"; do
  rel="$ROOT/$e"
  [ -f "$rel" ] || fail "excluded file no longer exists: $e (remove it from EXCLUDE)"
  exclude_re="${exclude_re:+$exclude_re|}$(printf '%s' "$rel" | sed 's/[.[\*^$()+?{|\\]/\\&/g')"
done

echo "excluded from this check (hidden platform APIs, Soong-only):"
for e in "${EXCLUDE[@]}"; do echo "  - $e"; done

mapfile -t SRC < <(find -L "${SOURCE_DIRS[@]}" -name '*.kt' -print | grep -Ev "^($exclude_re)$" | sort)

# Per-directory counts, not a guessed total. An earlier version asserted
# "> 100 sources", which was simply wrong about this tree (82 is the real
# number: 72 design-system + 6 chrome + 2 rock + 2 sounds) and failed a
# perfectly good tree. Counting each dir catches the failure that matters —
# one symlinked module resolving to nothing — without hardcoding a total that
# goes stale the next time a file is added.
for d in "${SOURCE_DIRS[@]}"; do
  n=$(find -L "$d" -name '*.kt' | wc -l)
  [ "$n" -gt 0 ] || fail "no Kotlin sources under $d"
done
chrome_n=$(find -L "$ROOT/appleux/systemui/chrome/src/main/java" -name '*.kt' | wc -l)
[ "$chrome_n" -ge 4 ] || fail "chrome module yielded only $chrome_n sources"

echo "chrome type-check: ${#SRC[@]} Kotlin sources"
rm -rf "$OUT"; mkdir -p "$OUT"

# -J-Xmx6g: the launcher module needed this for the diagnostic-rendering OOM;
# chrome pulls in the same design-system, so the same ceiling applies.
set +e
"$KOTLINC" -J-Xmx6g -nowarn \
  -classpath "$CP" \
  -d "$OUT" \
  "${SRC[@]}" > "$OUT/kotlinc.log" 2>&1
rc=$?
set -e

if [ "$rc" -ne 0 ]; then
  echo "FAIL: kotlinc exited $rc; first errors:"
  grep -E "error:|warning:" "$OUT/kotlinc.log" | head -40
  echo "(full log: $OUT/kotlinc.log)"
  exit 1
fi

# A zero exit with no classes would mean the compiler wrote nothing, which is
# the failure mode this check exists to catch.
n=$(find "$OUT" -name 'Appleux*.class' | wc -l)
[ "$n" -gt 20 ] || fail "compile reported success but produced only $n Appleux classes"

echo "OK chrome compiles ($n Appleux classes in $OUT)"
