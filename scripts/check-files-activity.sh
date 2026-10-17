#!/bin/bash
# Fast isolated typecheck for FilesActivity.kt only.
#
# The full scripts/build-emulator-apks.sh compiles every launcher source in one
# kotlinc invocation, so a break in any sibling Activity hides the state of this
# one file. This harness compiles the same source set but filters the reported
# diagnostics down to FilesActivity.kt, so a change here can be verified in
# isolation without waiting for (or being blocked by) unrelated work.
#
#   scripts/check-files-activity.sh          # FilesActivity.kt diagnostics only
#   scripts/check-files-activity.sh --all    # every launcher diagnostic
#
# Exit status is 0 when FilesActivity.kt has no errors, 1 otherwise.

set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$ROOT/tools/envsetup.sh" >/dev/null 2>&1

SDK="$ANDROID_HOME"
PLATFORM="$SDK/platforms/android-36/android.jar"
GEN="$ROOT/out/gen"
LIBS="$ROOT/thirdparty/libs"
KOTLINC="$ROOT/tools/kotlin/kotlinc/bin/kotlinc"

CP="$PLATFORM"
for j in "$LIBS"/*.jar; do CP="$CP:$j"; done
if [ -d "$GEN/launcher-r-classes" ]; then CP="$CP:$GEN/launcher-r-classes"; fi

if [ ! -d "$GEN/launcher-r-classes" ]; then
  echo "no generated R classes; run scripts/build-emulator-apks.sh once first" >&2
  exit 2
fi

LOG="$GEN/files-activity-check.log"

"$KOTLINC" \
  -nowarn -jvm-target 17 -no-stdlib \
  -classpath "$CP" \
  -d "$GEN/files-activity-check-out" \
  "$ROOT/appleux/launcher/src/main/java" \
  "$ROOT/appleux/performance/src/main/java" \
  "$ROOT/appleux/design-system/src/main/java" \
  > "$LOG" 2>&1

if [ "${1:-}" = "--all" ]; then
  grep -E "^.+error:" "$LOG" || echo "(no errors)"
  exit 0
fi

if grep -E "FilesActivity\.kt:[0-9]+:[0-9]+: error:" "$LOG"; then
  exit 1
fi

echo "FilesActivity.kt: clean ($(grep -cE 'error:' "$LOG") diagnostics elsewhere in launcher)"
exit 0