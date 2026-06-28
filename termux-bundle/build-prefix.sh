#!/bin/bash
set -euo pipefail

# ============================================================
# Build-prefix v5: Minimal bootstrap + download aarch64 wheels
# Wheels are installed on-device (fast from local files)
# ============================================================

TERMUX_APK_URL="https://f-droid.org/repo/com.termux_118.apk"
PREFIX_DIR="/prefix"
OUTPUT_DIR="${OUTPUT_DIR:-/output}"
WHEELS_DIR="$PREFIX_DIR/wheels"

echo "=== Termux Bootstrap Setup v5 ==="

# Download Termux APK
echo "Downloading Termux APK..."
curl -sL -o termux.apk "$TERMUX_APK_URL" 2>&1 | tail -1
echo "Extracting APK..."
unzip -q termux.apk -d termux_extract

# Extract bootstrap from libtermux-bootstrap.so
tail -c +1393 termux_extract/lib/arm64-v8a/libtermux-bootstrap.so > bootstrap.zip

# Extract prefix
echo "Extracting prefix..."
rm -rf "$PREFIX_DIR"
mkdir -p "$PREFIX_DIR"
unzip -q bootstrap.zip -d "$PREFIX_DIR"

# Cleanup APK files
rm -rf termux.apk termux_extract bootstrap.zip

# Fix Python binary
chmod +x "$PREFIX_DIR/bin/python3" 2>/dev/null || true

# === Download aarch64 wheels for offline install ===
echo "Downloading aarch64 wheels..."
mkdir -p "$WHEELS_DIR"

PYTHON=$(which python3)
if [ -z "$PYTHON" ]; then
    echo "ERROR: python3 not found"
    exit 1
fi

# Upgrade pip for better platform support
$PYTHON -m pip install --upgrade pip 2>&1 | tail -3

# Download wheels for aarch64 — pure Python will have noarch wheels,
# native packages (cryptography, pydantic-core) will have manylinux_2_17_aarch64
# We use --platform to target ARM64 and --only-binary to avoid source builds
$PYTHON -m pip download \
    --platform=manylinux2014_aarch64 \
    --platform=manylinux_2_17_aarch64 \
    --platform=linux_aarch64 \
    --only-binary=:all: \
    --dest "$WHEELS_DIR" \
    --no-deps \
    httpx pyyaml python-dotenv pydantic uvicorn 2>&1 | tail -5 || true

# Also try downloading ruslan-agent/hermes-cli
$PYTHON -m pip download \
    --platform=manylinux2014_aarch64 \
    --platform=manylinux_2_17_aarch64 \
    --platform=linux_aarch64 \
    --only-binary=:all: \
    --dest "$WHEELS_DIR" \
    --no-deps \
    ruslan-agent 2>&1 | tail -5 || true

# List what we got
echo "Downloaded wheels:"
ls -la "$WHEELS_DIR/" 2>/dev/null || echo "(none — will install from PyPI on device)"

# Create home directory with initial config
mkdir -p "$PREFIX_DIR/home/.config/hermes"

# Create .env template
cat > "$PREFIX_DIR/home/.env" << 'ENVEOF'
# Ruslan Agent Configuration
# Fill in your API key via the app Settings > Providers
PROVIDER=opencode-go
MODEL=deepseek-v4-flash
GATEWAY_URL=http://127.0.0.1:9123
ENVEOF

# Create minimal config.yaml
cat > "$PREFIX_DIR/home/.config/hermes/config.yaml" << 'YMLEOF'
provider: opencode-go
model:
  default: deepseek-v4-flash
  provider: opencode-go
  base_url: https://opencode.ai/zen/go/v1
gateway:
  host: "127.0.0.1"
  port: 9123
YMLEOF

# Keep wheels (will be installed on-device by setup-agent.sh)
# Don't clean wheels directory

echo "=== Cleanup ==="
rm -rf "$PREFIX_DIR/tmp"/* 2>/dev/null || true
rm -rf "$PREFIX_DIR/var/cache"/* 2>/dev/null || true

echo "=== Creating Archive ==="
mkdir -p "$OUTPUT_DIR"
echo "Compressing prefix..."
tar -C "$PREFIX_DIR" -cf - . | zstd -19 -T0 -o "$OUTPUT_DIR/usr.tar.zst"
cd "$OUTPUT_DIR"
sha256sum usr.tar.zst > usr.tar.zst.sha256

echo "=== Done ==="
echo "Output: $OUTPUT_DIR/usr.tar.zst"
echo "Size: $(du -h usr.tar.zst | cut -f1)"
echo "Wheels included: $(ls "$WHEELS_DIR/" 2>/dev/null | wc -l)"
echo "Version: v5+"
