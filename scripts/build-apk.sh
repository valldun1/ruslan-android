#!/bin/bash
set -euo pipefail

# Build APK script for Ruslan Agent Android
# Usage: ./build-apk.sh [--debug|--release]

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
ANDROID_DIR="$PROJECT_ROOT/android-app"
ASSETS_DIR="$ANDROID_DIR/app/src/main/assets"
BUILD_TYPE="${1:-debug}"

echo "=== Ruslan Agent APK Build ==="
echo "Build type: $BUILD_TYPE"

# Step 1: Build Termux prefix if needed
if [ ! -f "$ASSETS_DIR/usr.tar.zst" ]; then
    echo "Termux prefix not found, building..."
    "$PROJECT_ROOT/termux-bundle/build-prefix.sh"
fi

# Step 2: Copy assets
echo "Copying assets..."
mkdir -p "$ASSETS_DIR/scripts" "$ASSETS_DIR/config"

# Copy scripts
cp "$PROJECT_ROOT/android/scripts/"*.sh "$ASSETS_DIR/scripts/" 2>/dev/null || true

# Copy config templates
cp "$PROJECT_ROOT/android/config/"*.example "$ASSETS_DIR/config/" 2>/dev/null || true

# Step 3: Build APK
echo "Building APK..."
cd "$ANDROID_DIR"

if [ "$BUILD_TYPE" == "release" ]; then
    ./gradlew assembleRelease
    APK_PATH="app/build/outputs/apk/release/app-release-unsigned.apk"
    
    # Sign if keystore exists
    if [ -f "$PROJECT_ROOT/signing/ruslan.keystore" ]; then
        echo "Signing APK..."
        "$SCRIPT_DIR/sign-apk.sh" \
            "$APK_PATH" \
            "$PROJECT_ROOT/signing/ruslan.keystore" \
            "ruslan" \
            "$KEYSTORE_PASS" \
            "$KEY_PASS" \
            "$PROJECT_ROOT/ruslan-agent.apk"
    else
        echo "Warning: No keystore found, APK is unsigned"
        cp "$APK_PATH" "$PROJECT_ROOT/ruslan-agent-unsigned.apk"
    fi
else
    ./gradlew assembleDebug
    APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
    cp "$APK_PATH" "$PROJECT_ROOT/ruslan-agent-debug.apk"
fi

echo "=== Build Complete ==="
echo "APK location: $PROJECT_ROOT/ruslan-agent*.apk"
ls -lh "$PROJECT_ROOT"/ruslan-agent*.apk
