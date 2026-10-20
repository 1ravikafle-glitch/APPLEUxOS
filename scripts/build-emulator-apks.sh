#!/bin/bash
# AppleUX SDK-emulator build.
#
# Soong (Android.bp) cannot run without a full AOSP tree, which does not fit on
# this drive. This script compiles the same Kotlin sources with kotlinc against
# android.jar plus locally vendored AndroidX AARs, then packages APKs with the
# SDK's own aapt2/d8/apksigner.
#
# Each app is built as ONE unit:
#   1. aapt2 compile  app res + every library res dir
#   2. aapt2 link     all compiled res as positional inputs -> resource table,
#                     plus R.java for EVERY package present (app + com.appleux.design)
#   3. javac          all generated R.java
#   4. kotlinc        app sources + upstream library sources in one invocation,
#                     so library code binds to the app's own R ids
#   5. d8             all classes for the app, packaged as one jar so that
#                     package paths are preserved (no basename collisions)
#   6. zipalign + apksigner, then AndroidX runtime dex appended as classes2..N.dex
#
# Everything stays under $APPLEUX_ROOT. Nothing is written to /.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$ROOT/tools/envsetup.sh" >/dev/null 2>&1

SDK="$ANDROID_HOME"
PLATFORM="$SDK/platforms/android-36/android.jar"
BT="$SDK/build-tools/36.0.0"
AAPT2="$BT/aapt2"
D8="$BT/d8"
ZIPALIGN="$BT/zipalign"
APKSIGNER="$BT/apksigner"
JAR="$JAVA_HOME/bin/jar"
KOTLINC="$ROOT/tools/kotlin/kotlinc/bin/kotlinc"

LIBS="$ROOT/thirdparty/libs"
AAR_DIR="$ROOT/thirdparty/aar"
OUT="$ROOT/out/emulator"
GEN="$ROOT/out/gen"
STAMP="$OUT/.timestamps"

DS_SRC="$ROOT/appleux/design-system/src/main/java"
PERF_SRC="$ROOT/appleux/performance/src/main/java"
DS_RES="$ROOT/appleux/design-system/src/main/res"
# appleux-design-system declares `static_libs: ["appleux-device-support-rock"]`
# in its Android.bp, so this source tree is a hard dependency of the Kotlin
# compile, not an optional extra. It was MISSING from this list, which only
# appeared to work because a stale RockDisplayProfile.class was lying around
# in out/gen from an earlier layout; design-system/.../AppleuxDisplayInsets.kt
# imports com.appleux.device.rock.RockDisplayProfile and cannot compile
# without it. Every module that compiles DS_SRC needs ROCK_SRC too.
ROCK_SRC="$ROOT/device-support/rock/src/main/java"
# AppleUXSounds: the 366 IPSW-extracted sounds. Launcher only — settings
# has no playback call site, and shipping 7.7 MB of audio in an app that
# never plays it is exactly the dead-weight problem.
SOUNDS_SRC="$ROOT/appleux/sounds/src/main/java"
APPS_SRC="$ROOT/appleux/apps-shared/src/main/java"
SOUNDS_RES="$ROOT/appleux/sounds/src/main/res"

CP="$PLATFORM"
for j in "$LIBS"/*.jar; do CP="$CP:$j"; done

# One build at a time: concurrent runs stomp the shared $GEN dir
# (seen live 2026-10-19: leftover + fresh run -> unresolved 'R').
exec 9>"/tmp/opencode/appleux-sdk-build.lock"
flock -n 9 || { echo "FATAL: another build-emulator-apks.sh is running." >&2; exit 3; }
mkdir -p "$OUT/apks" "$GEN" "$STAMP" "$GEN/empty-res"
rm -f "$STAMP/lib-res"   # resource inputs change often; always recompile

# ---------------------------------------------------- library R discovery ---
# Merged library resources end up under the app's package, so aapt2 emits only
# the app's R. Library bytecode still references its own <lib package>.R, so we
# must generate those too. Which libraries actually need one is discovered from
# the constant pools of the vendored classes, not guessed.
detect_r_packages() {
  # The stamp is keyed on a signature, not on a content hash, so adding a local
  # package has to force a rescan. Without this the sounds R was never generated
  # and the module failed to compile against a stale stamp.
  local want="local:design-system,sounds jars:$(ls "$LIBS" 2>/dev/null | wc -l)"
  if [ -f "$STAMP/r-packages" ] && [ "$(cat "$STAMP/r-packages.sig" 2>/dev/null)" = "$want" ]; then
    return
  fi
  echo "$want" > "$STAMP/r-packages.sig"
  echo "  scanning    library R references"
  local scratch="$GEN/rscan"
  rm -rf "$scratch"; mkdir -p "$scratch"
  for j in "$LIBS"/*.jar; do
    ( cd "$scratch" && unzip -o -q "$j" '*.class' 2>/dev/null || true )
  done
  : > "$STAMP/r-packages"
  # every package a vendored AAR declares is a candidate
  local aar pkg path
  for aar in "$AAR_DIR"/*.aar; do
    [ -f "$aar" ] || continue
    pkg=$(unzip -p "$aar" AndroidManifest.xml 2>/dev/null \
          | grep -o 'package="[^"]*"' | head -1 | sed 's/package="//;s/"//')
    [ -n "$pkg" ] || continue
    path="${pkg//./\/}"
    if grep -rqs -- "$path/R" "$scratch"; then
      echo "$pkg" >> "$STAMP/r-packages"
    fi
  done
  # design-system is ours, and always needed
  echo "com.appleux.design" >> "$STAMP/r-packages"
  # AppleUXSounds is ours too. SoundRegistry is a plain object of R.raw ids, so
  # without a generated com.appleux.sounds.R the whole module fails to compile —
  # which is exactly why these sounds shipped silent before.
  echo "com.appleux.sounds" >> "$STAMP/r-packages"
  sort -u "$STAMP/r-packages" -o "$STAMP/r-packages"
  rm -rf "$scratch"
  echo "  r packages: $(tr '\n' ' ' < "$STAMP/r-packages")"
}

# ------------------------------------------------------- library artefacts ---
compile_lib_res() {
  echo "  compiling   library resources"
  rm -f "$GEN"/lib-res-*.zip
  for d in "$ROOT"/thirdparty/extract/*/res; do
    [ -d "$d" ] || continue
    n=$(basename "$(dirname "$d")")
    "$AAPT2" compile --dir "$d" -o "$GEN/lib-res-$n.zip" >/dev/null 2>&1 || true
  done
  # design-system resources are consumed by every app, so they get their own zip
  "$AAPT2" compile --dir "$DS_RES" -o "$GEN/lib-res-design-system.zip"
  # Sounds are consumed by the launcher alone. Every file is .ogg — the .caf
  # twins live in sounds/extracted-reference/, because two files sharing a stem
  # both become R.raw.<stem> and aapt2 rejects that as a duplicate.
  "$AAPT2" compile --dir "$SOUNDS_RES" -o "$GEN/lib-res-sounds.zip"
}

build_androidx_dex() {
  if [ -f "$STAMP/androidx-dex" ] && ls "$GEN"/androidx-dex/*.dex >/dev/null 2>&1; then
    return
  fi
  echo "  dexing      androidx runtime"
  rm -rf "$GEN/androidx-dex" "$GEN/androidx-in"
  mkdir -p "$GEN/androidx-dex" "$GEN/androidx-in"
  for j in "$LIBS"/*.jar; do
    ( cd "$GEN/androidx-in" && unzip -o -q "$j" '*.class' 2>/dev/null || true )
  done
  # META-INF holds module-info.class, which d8 rejects as a program input
  local classes
  classes=$(find "$GEN/androidx-in" -name '*.class' -not -path '*/META-INF/*')
  [ -n "$classes" ] || { echo "  FAILED no runtime classes"; exit 1; }
  if ! "$D8" --min-api 26 --release --lib "$PLATFORM" \
      --output "$GEN/androidx-dex" $classes > "$GEN/androidx-d8.log" 2>&1; then
    grep -m 5 -E "^Error|Compilation failed|Unsupported" "$GEN/androidx-d8.log" \
      | sed 's|^|    d8: |' || true
    echo "  FAILED androidx dex"
    exit 1
  fi
  local i=1
  for f in "$GEN/androidx-dex"/*.dex; do
    i=$((i + 1)); mv "$f" "$GEN/androidx-dex/classes$i.dex"
  done
  rm -rf "$GEN/androidx-in"
  touch "$STAMP/androidx-dex"
}

# ------------------------------------------------------------------ build ---
build_app() {
  local module="$1" apk_name="$2"
  local manifest="$ROOT/appleux/$module/src/main/AndroidManifest.xml"
  local resdir="$ROOT/appleux/$module/src/main/res"
  local srcdir="$ROOT/appleux/$module/src/main/java"

  echo "==> $module"
  rm -f "$OUT/apks/$apk_name"        # never leave a stale APK behind

  # 1. app resources
  rm -f "$GEN/$module-res.zip"
  if [ -d "$resdir" ]; then
    "$AAPT2" compile --dir "$resdir" -o "$GEN/$module-res.zip" >/dev/null
  fi

  # 2. link: every compiled res archive is a POSITIONAL input to aapt2 link.
  #    (-R is for XML overlays and silently drops compiled archives.)
  local res_inputs=""
  for z in "$GEN"/lib-res-*.zip; do
    [ -f "$z" ] || continue
    # design-system supplies its own res when it is the module being built
    [ "$module" = "design-system" ] && [ "$z" = "$GEN/lib-res-design-system.zip" ] && continue
    # NOTE: lib-res-sounds.zip is no longer excluded from any module. It used to
    # be launcher-only, which was correct while only the launcher had a playback
    # call site. design-system now consumes AppleUXSounds (the Settings confirm
    # tick goes through AppleuxComponentsWiring.settingsCard), so every module
    # compiles design-system's sources and needs the sounds on its classpath and
    # in its res, or com.appleux.sounds.R has no raw ids at all.

    res_inputs="$res_inputs $z"
  done
  [ -f "$GEN/$module-res.zip" ] && res_inputs="$GEN/$module-res.zip $res_inputs"

  rm -f "$GEN/$module-unsigned.apk"
  rm -rf "$GEN/$module-r" "$GEN/$module-r-classes"
  # shellcheck disable=SC2086
  "$AAPT2" link \
    -o "$GEN/$module-unsigned.apk" \
    -I "$PLATFORM" \
    --manifest "$manifest" \
    --java "$GEN/$module-r" \
    --min-sdk-version 26 \
    --target-sdk-version 36 \
    --auto-add-overlay \
    $res_inputs \
    2>&1 | grep -v "^$" | sed "s|^|    link: |" || true
  [ -f "$GEN/$module-unsigned.apk" ] || { echo "  FAILED aapt2 link"; exit 1; }

# 2b. Generate R.java for every library package whose bytecode references it.
  #     Each pass reuses the IDENTICAL input list, so every R agrees on ids.
  rm -rf "$GEN/$module-r-libs"
  mkdir -p "$GEN/$module-r-libs"
  local rpkg
  while read -r rpkg; do
    [ -n "$rpkg" ] || continue
    # the app's own R is already produced by the main link
    [ "$rpkg" = "com.appleux.design" ] && [ "$module" = "design-system" ] && continue
    local outdir="$GEN/$module-r-libs/${rpkg//./\/}"
    mkdir -p "$outdir"
    # shellcheck disable=SC2086
    "$AAPT2" link \
      -o "$GEN/$module-rlib.apk" \
      -I "$PLATFORM" \
      --manifest "$manifest" \
      --java "$outdir" \
      --min-sdk-version 26 \
      --target-sdk-version 36 \
      --auto-add-overlay \
      --custom-package "$rpkg" \
      $res_inputs \
      >/dev/null 2>&1 || true
  done < "$STAMP/r-packages"
  rm -f "$GEN/$module-rlib.apk"

  # 3. javac the app R plus every library R
  mkdir -p "$GEN/$module-r-classes"
  find "$GEN/$module-r" "$GEN/$module-r-libs" -name '*.java' \
    > "$GEN/$module-r-sources.txt" 2>/dev/null || true
  if [ -s "$GEN/$module-r-sources.txt" ]; then
    "$JAVA_HOME/bin/javac" -nowarn -source 17 -target 17 \
      -classpath "$PLATFORM" -d "$GEN/$module-r-classes" \
      @"$GEN/$module-r-sources.txt" 2>&1 | grep -v "^Note:" | sed "s|^|    r: |" || true
  fi
  echo "    r classes: $(cd "$GEN/$module-r-classes" && find . -name 'R.class' | sed 's|/R.class||;s|^\./||;s|/|.|g' | tr '\n' ' ')"

  # 4. kotlinc: app sources plus upstream library sources, compiled together so
  #    library code resolves against THIS app's resource ids.
  local srcdirs="$srcdir"
  case "$module" in
    # design-system consumes AppleUXSounds (AppleuxComponentsWiring plays the
    # Settings confirm tick), so the sources have to be on its compile line too.
    design-system) srcdirs="$srcdir $SOUNDS_SRC $ROCK_SRC" ;;
    performance)   srcdirs="$srcdir $SOUNDS_SRC $DS_SRC $ROCK_SRC" ;;
    # Only the launcher plays a sound (camera shutter, toggle confirm).
    launcher)      srcdirs="$srcdir $SOUNDS_SRC $PERF_SRC $DS_SRC $ROCK_SRC" ;;
    phone|messages|contacts|photos|files|camera|clock|calendar|browser|video|apps-shared)
                   srcdirs="$srcdir $SOUNDS_SRC $PERF_SRC $DS_SRC $ROCK_SRC $APPS_SRC" ;;
    *)             srcdirs="$srcdir $SOUNDS_SRC $PERF_SRC $DS_SRC $ROCK_SRC" ;;
  esac

  rm -rf "$GEN/$module-classes"; mkdir -p "$GEN/$module-classes"
  # kotlinc's default heap is small enough that compiling every app shell in
  # one invocation dies with OutOfMemoryError. Worse, it dies WHILE RENDERING a
  # diagnostic (RenderIrElementKt.renderTypeInner), so the heap is exhausted
  # building the error message and the actual error is never printed - the
  # build only reports "Backend Internal error". Host has 15 GB and ~10 GB
  # available; -nowarn also matters because a missing module suppresses the
  # type information that makes those renderings so expensive.
  # shellcheck disable=SC2086
  "$KOTLINC" -J-Xmx"${KOTLINC_HEAP:-6g}" -J-XX:MaxMetaspaceSize=1g \
    -classpath "$CP:$GEN/$module-r-classes" -jvm-target 17 -nowarn \
    -d "$GEN/$module-classes" $srcdirs 2> "$GEN/$module-kotlinc.log" || true
  grep "error:" "$GEN/$module-kotlinc.log" | sed "s|appleux/||" \
    | sed "s|^|    kt: |" | head -40 || true
  if grep -q "error:" "$GEN/$module-kotlinc.log"; then
    echo "  FAILED     $module kotlinc"
    exit 1
  fi

  # 5. dex every class in one jar (preserves package paths)
  rm -rf "$GEN/$module-dex"; mkdir -p "$GEN/$module-dex"
  local alljar="$GEN/$module-all.jar"
  rm -f "$alljar"
  "$JAR" cf "$alljar" -C "$GEN/$module-classes" . -C "$GEN/$module-r-classes" .
  if ! "$D8" --min-api 26 --release --lib "$PLATFORM" \
      --output "$GEN/$module-dex" "$alljar" > "$GEN/$module-d8.log" 2>&1; then
    grep -m 5 -E "^Error|Compilation failed" "$GEN/$module-d8.log" | sed 's|^|    d8: |' || true
    echo "  FAILED d8"
    exit 1
  fi
  ls "$GEN/$module-dex"/*.dex >/dev/null 2>&1 || { echo "  FAILED no dex"; exit 1; }

  local n=1
  for f in "$GEN/$module-dex"/*.dex; do
    if [ "$(basename "$f")" != "classes.dex" ]; then
      n=$((n + 1)); mv "$f" "$GEN/$module-dex/classes$n.dex"
    fi
  done

  ( cd "$GEN/$module-dex"    && zip -q -X "$GEN/$module-unsigned.apk" ./*.dex )
  ( cd "$GEN/androidx-dex"    && zip -q -X "$GEN/$module-unsigned.apk" ./*.dex )

  # 6. align + sign
  "$ZIPALIGN" -f 4 "$GEN/$module-unsigned.apk" "$GEN/$module-aligned.apk"
  "$APKSIGNER" sign \
    --ks "$ROOT/tools/debug.keystore" --ks-pass pass:android --key-pass pass:android \
    --ks-key-alias androiddebugkey \
    --out "$OUT/apks/$apk_name" "$GEN/$module-aligned.apk" >/dev/null 2>&1

  [ -f "$OUT/apks/$apk_name" ] \
    && echo "  OK         $apk_name ($(du -h "$OUT/apks/$apk_name" | cut -f1))" \
    || { echo "  FAILED     $apk_name"; exit 1; }
}

echo "AppleUX emulator build"
echo "  SDK:      $SDK"
echo "  kotlinc:  $("$KOTLINC" -version 2>&1 | head -1)"
echo "  libs:     $(ls "$LIBS"/*.jar | wc -l) jars"
echo

compile_lib_res
build_androidx_dex
detect_r_packages

# The com.appleux.systemui APK was DELETED on 2026-10-17 (deliverables/m23).
# It was a separate app whose foreground service floated two full-screen
# TYPE_APPLICATION_OVERLAY windows over SystemUI. The iOS chrome is now a
# library (appleux/systemui/chrome -> module appleux-systemui-chrome) compiled
# INTO com.android.systemui by Soong, so there is no fifth APK to build here.
# Building it here is not merely unnecessary: its TYPE_APPLICATION_OVERLAY
# windows are exactly the per-frame cost the takeover removes.
#
# NOTE: this SDK route cannot verify the SystemUI takeover. It compiles apps
# only; the integration is proved by `m SystemUI`, which has not been run.
build_app design-system appleux-design-system.apk
build_app performance   appleux-performance.apk
build_app launcher      appleux-launcher.apk
build_app settings      appleux-settings.apk
build_app phone         appleux-phone.apk
build_app messages      appleux-messages.apk
build_app contacts      appleux-contacts.apk
build_app photos        appleux-photos.apk
build_app files         appleux-files.apk
build_app camera        appleux-camera.apk
build_app clock         appleux-clock.apk
build_app calendar      appleux-calendar.apk
build_app browser       appleux-browser.apk
build_app video         appleux-video.apk

echo
echo "APKs in $OUT/apks:"
ls -la "$OUT/apks"/*.apk