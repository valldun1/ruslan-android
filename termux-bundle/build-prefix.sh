#!/bin/bash
set -euo pipefail

# Build Termux prefix with Ruslan Agent for ARM64
# Output: usr.tar.zst
#
# CHANGELOG (2026-06-26 v3):
#   - Removed all `pkg install` calls (pkg unavailable in Docker ubuntu:22.04)
#   - Use python from Termux bootstrap via proot (no host python required)
#   - git clone ruslan-agent on HOST (x86), then copy to prefix
#   - Removed strip on ARM64 binaries (cross-arch strip is unsafe)

TERMUX_APK_URL="https://f-droid.org/repo/com.termux_118.apk"
PREFIX_DIR="/prefix"
OUTPUT_DIR="${OUTPUT_DIR:-/output}"
PIP_TARGET_DIR="${PIP_TARGET_DIR:-$PREFIX_DIR/site-packages}"

echo "=== Termux Bootstrap Setup ==="

# Download Termux APK and extract bootstrap
echo "Downloading Termux APK..."
curl -sL -o termux.apk "$TERMUX_APK_URL"
echo "Extracting bootstrap from APK..."
unzip -q termux.apk -d termux_extract

# Extract bootstrap zip embedded in libtermux-bootstrap.so
tail -c +1393 termux_extract/lib/arm64-v8a/libtermux-bootstrap.so > bootstrap.zip

# Extract bootstrap
echo "Extracting prefix..."
rm -rf "$PREFIX_DIR"
mkdir -p "$PREFIX_DIR"
unzip -q bootstrap.zip -d "$PREFIX_DIR"

# Cleanup
rm -rf termux.apk termux_extract bootstrap.zip

echo "=== Setup proot helper ==="

# Helper: run a command in the prefix using proot
proot_run() {
    command proot -0 -r "$PREFIX_DIR" -b /dev -b /proc -b /sys "$@"
}

# Verify bootstrap python works
echo "Verifying bootstrap python..."
proot_run /bin/python3 --version || {
    echo "ERROR: bootstrap python not found"
    exit 1
}

echo "=== Installing Python Dependencies ==="

# Install Python dependencies into the prefix's site-packages
# Use bootstrap python (not host python) — proot isolates to Termux rootfs
proot_run /bin/python3 -m pip install --upgrade pip 2>&1 | tail -3 || true

# Install build dependencies first (for Rust compilation)
proot_run /bin/python3 -m pip install --no-cache-dir \
    --target "$PIP_TARGET_DIR" \
    maturin setuptools-rust wheel 2>&1 | tail -5 || true

# Install pinned dependencies
if [ -f /build/pin/pip.txt ]; then
    echo "Installing from pip.txt..."
    proot_run /bin/python3 -m pip install --no-cache-dir \
        --target "$PIP_TARGET_DIR" \
        -r /build/pin/pip.txt 2>&1 | tail -5 || true
fi

if [ -f /build/pin/python.txt ]; then
    echo "Installing from python.txt..."
    proot_run /bin/python3 -m pip install --no-cache-dir \
        --target "$PIP_TARGET_DIR" \
        -r /build/pin/python.txt 2>&1 | tail -5 || true
fi

echo "=== Installing Ruslan Agent ==="

# Clone Ruslan Agent on HOST (x86 faster, no qemu overhead) then copy to prefix
RUSLAN_VERSION="${RUSLAN_VERSION:-0.17.0}"
git clone --depth 1 --branch "v${RUSLAN_VERSION}" \
    https://github.com/valldun1/ruslan.git /tmp/ruslan 2>/dev/null || \
    git clone --depth 1 \
    https://github.com/valldun1/ruslan.git /tmp/ruslan

# Install into prefix site-packages
proot_run /bin/python3 -m pip install --no-cache-dir \
    --target "$PIP_TARGET_DIR" \
    -e /tmp/ruslan 2>&1 | tail -5 || true

echo "=== Cleanup ==="

# Remove unnecessary files to reduce size
rm -rf "$PREFIX_DIR/tmp"/* 2>/dev/null || true
rm -rf "$PREFIX_DIR/var/cache"/* 2>/dev/null || true
find "$PREFIX_DIR" -name "__pycache__" -type d -exec rm -rf {} + 2>/dev/null || true
find "$PREFIX_DIR" -name "*.pyc" -delete 2>/dev/null || true
find "$PREFIX_DIR" -name "*.pyo" -delete 2>/dev/null || true

# Note: skip strip on cross-arch binaries (aarch64 binaries stripped from x86 host
# can break. Do strip in release pipeline or on-device if needed.)

echo "=== Creating Archive ==="

# Create output directory
mkdir -p "$OUTPUT_DIR"

# Create compressed tarball
echo "Compressing prefix (this may take a while)..."
tar -C "$PREFIX_DIR" -cf - . | zstd -19 -T0 -o "$OUTPUT_DIR/usr.tar.zst"

# Generate hash
cd "$OUTPUT_DIR"
sha256sum usr.tar.zst > usr.tar.zst.sha256

echo "=== Done ==="
echo "Output: $OUTPUT_DIR/usr.tar.zst"
echo "Size: $(du -h usr.tar.zst | cut -f1)"
echo "SHA256: $(cat usr.tar.zst.sha256 | cut -d' ' -f1)"
