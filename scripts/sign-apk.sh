#!/bin/bash
set -euo pipefail

# Sign APK script
# Usage: ./sign-apk.sh <input.apk> <keystore> <alias> <keystore_pass> <key_pass> <output.apk>

INPUT_APK="$1"
KEYSTORE="$2"
ALIAS="$3"
KEYSTORE_PASS="$4"
KEY_PASS="$5"
OUTPUT_APK="$6"

echo "Signing APK: $INPUT_APK"

# Align APK
UNSIGNED_ALIGNED="${INPUT_APK%.apk}-aligned.apk"
zipalign -p -f 4 "$INPUT_APK" "$UNSIGNED_ALIGNED"

# Sign APK
apksigner sign \
    --ks "$KEYSTORE" \
    --ks-key-alias "$ALIAS" \
    --ks-pass "pass:$KEYSTORE_PASS" \
    --key-pass "pass:$KEY_PASS" \
    --out "$OUTPUT_APK" \
    "$UNSIGNED_ALIGNED"

# Verify
apksigner verify --verbose "$OUTPUT_APK"

echo "Signed APK: $OUTPUT_APK"

# Cleanup
rm -f "$UNSIGNED_ALIGNED"
