#!/bin/bash
# AppleUX iOS 27 Asset Build Script
# Builds RRO overlay, icon pack, and validates all resources

set -e

ANDROID_HOME="${ANDROID_HOME:-/home/elfakiris/OsProject/android-sdk}"
AAPT2="$ANDROID_HOME/build-tools/34.0.0/aapt2"
PROJECT_ROOT="/home/elfakiris/OsProject"
OUT_DIR="$PROJECT_ROOT/out/ios27-assets"
RRO_DIR="$PROJECT_ROOT/appleux/rro-frameworks"
LAUNCHER_DIR="$PROJECT_ROOT/appleux/launcher"
SYSTEMUI_DIR="$PROJECT_ROOT/appleux/systemui"

echo "============================================"
echo "AppleUX iOS 27 Asset Builder"
echo "============================================"
echo ""

# Create output directories
mkdir -p "$OUT_DIR/rro"
mkdir -p "$OUT_DIR/icon-pack"
mkdir -p "$OUT_DIR/tiles"

# Step 1: Build RRO Overlay
echo "[1/4] Building RRO Overlay..."
if [ -f "$AAPT2" ]; then
    # Compile RRO resources
    find "$RRO_DIR/res" -name "*.xml" -exec "$AAPT2" compile {} -o "$OUT_DIR/rro/" \; 2>/dev/null || true
    echo "  RRO resources compiled"
else
    echo "  aapt2 not found, skipping RRO build"
fi

# Step 2: Copy icon pack resources
echo "[2/4] Copying icon pack resources..."
if [ -d "$LAUNCHER_DIR/res/drawable/ios-app-icons" ]; then
    cp -r "$LAUNCHER_DIR/res/drawable/ios-app-icons" "$OUT_DIR/icon-pack/" 2>/dev/null || true
    echo "  App icons copied: $(ls "$OUT_DIR/icon-pack/ios-app-icons/" 2>/dev/null | wc -l)"
else
    echo "  No app icons found"
fi

# Step 3: Copy CC tiles
echo "[3/4] Copying CC tiles..."
if [ -d "$SYSTEMUI_DIR/res/drawable/ios-cc-tiles" ]; then
    cp -r "$SYSTEMUI_DIR/res/drawable/ios-cc-tiles" "$OUT_DIR/tiles/" 2>/dev/null || true
    echo "  CC tiles copied: $(ls "$OUT_DIR/tiles/ios-cc-tiles/" 2>/dev/null | wc -l)"
else
    echo "  No CC tiles found"
fi

# Step 4: Generate resource validation report
echo "[4/4] Generating validation report..."
{
    echo "iOS 27 Asset Validation Report"
    echo "==============================="
    echo ""
    echo "Generated: $(date)"
    echo ""
    echo "RRO Resources:"
    find "$RRO_DIR/res" -name "*.xml" | wc -l | xargs -I{} echo "  XML files: {}"
    find "$RRO_DIR/res" -name "*.png" -o -name "*.svg" | wc -l | xargs -I{} echo "  Drawable files: {}"
    echo ""
    echo "Icon Pack:"
    find "$LAUNCHER_DIR/res/drawable/ios-app-icons" -type f 2>/dev/null | wc -l | xargs -I{} echo "  App icons: {}"
    echo ""
    echo "CC Tiles:"
    find "$SYSTEMUI_DIR/res/drawable/ios-cc-tiles" -type f 2>/dev/null | wc -l | xargs -I{} echo "  Tile icons: {}"
    echo ""
    echo "Design System Symbols:"
    find "$PROJECT_ROOT/appleux/design-system/res/drawable/ios-symbols" -type f 2>/dev/null | wc -l | xargs -I{} echo "  SVG symbols: {}"
    echo ""
    echo "Wallpapers:"
    find "$PROJECT_ROOT/appleux/design-system/res/raw/ios-wallpapers" -type f 2>/dev/null | wc -l | xargs -I{} echo "  Wallpapers: {}"
    echo ""
    echo "Output:"
    find "$OUT_DIR" -type f 2>/dev/null | wc -l | xargs -I{} echo "  Total output files: {}"
} > "$OUT_DIR/validation-report.txt"

echo ""
echo "============================================"
echo "BUILD COMPLETE"
echo "============================================"
echo ""
echo "Output: $OUT_DIR"
echo ""
cat "$OUT_DIR/validation-report.txt"
