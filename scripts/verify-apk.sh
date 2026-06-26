#!/bin/bash
set -euo pipefail

# Verify APK script
# Usage: ./verify-apk.sh <apk>

APK="$1"

echo "Verifying APK: $APK"

# Check file exists
if [ ! -f "$APK" ]; then
    echo "Error: APK not found: $APK"
    exit 1
fi

# Verify signature
if ! apksigner verify "$APK"; then
    echo "Error: APK signature verification failed"
    exit 1
fi

# Check contents
echo "APK contents:"
aapt2 dump badging "$APK" | head -10

# Check size
SIZE=$(du -h "$APK" | cut -f1)
echo "APK size: $SIZE"

# Check for required assets
if unzip -l "$APK" | grep -q "usr.tar.zst"; then
    echo "✓ Termux prefix found"
else
    echo "✗ Termux prefix NOT found"
    exit 1
fi

echo "Verification complete!"
