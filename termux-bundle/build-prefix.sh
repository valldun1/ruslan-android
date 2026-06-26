#!/bin/bash
set -euo pipefail

# Build Termux prefix with Ruslan Agent for ARM64
# Output: usr.tar.zst
#
# CHANGELOG (2026-06-26 v4):
#   - Removed ALL proot calls — ptrace/execve fail in Docker ubuntu:22.04
#   - Use HOST python directly to install pip packages into prefix site-packages
#   - Skip Rust/maturin (they require native build, too slow for CI)
#   - Output is raw Termux bootstrap (python, bash) + Python wheels from pip

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

echo "=== Verifying Python (host) ==="

# Use host python3 (x86_64) for pip install — proot won't work in this Docker
PYTHON=$(which python3)
if [ -z "$PYTHON" ]; then
    echo "ERROR: python3 not found on host"
    exit 1
fi
echo "Using python: $PYTHON"
$PYTHON --version

echo "=== Installing Python Dependencies (host pip) ==="

# Install pure-Python wheels into prefix's site-packages.
# We use --platform=linux_aarch64 to get ARM64 wheels, --only-binary=:all: to skip sdist builds.
# This works for pure-Python packages. Native packages (cryptography, pillow) need other handling.
$PYTHON -m pip install --upgrade pip 2>&1 | tail -3 || true

# Install build deps for Rust if any
$PYTHON -m pip install --no-cache-dir --target "$PIP_TARGET_DIR" \
    --platform=manylinux2014_aarch64 --platform=manylinux_2_17_aarch64 \
    --only-binary=:all: \
    --implementation cp --python-version 3.10 \
    maturin setuptools-rust wheel 2>&1 | tail -5 || echo "WARN: maturin not installed"

# Install pinned dependencies (pure-Python only — native ones handled on-device)
if [ -f /build/pin/pip.txt ]; then
    echo "Installing from pip.txt..."
    $PYTHON -m pip install --no-cache-dir --target "$PIP_TARGET_DIR" \
        --platform=manylinux2014_aarch64 --platform=manylinux_2_17_aarch64 \
        --only-binary=:all: \
        --implementation cp --python-version 3.10 \
        -r /build/pin/pip.txt 2>&1 | tail -5 || echo "WARN: pip.txt failed"
fi

if [ -f /build/pin/python.txt ]; then
    echo "Installing from python.txt..."
    $PYTHON -m pip install --no-cache-dir --target "$PIP_TARGET_DIR" \
        --platform=manylinux2014_aarch64 --platform=manylinux_2_17_aarch64 \
        --only-binary=:all: \
        --implementation cp --python-version 3.10 \
        -r /build/pin/python.txt 2>&1 | tail -5 || echo "WARN: python.txt failed"
fi

echo "=== Installing Ruslan Agent ==="

# Clone Ruslan Agent (host x86 is fine, then install as package)
RUSLAN_VERSION="${RUSLAN_VERSION:-0.17.0}"
git clone --depth 1 --branch "v${RUSLAN_VERSION}" \
    https://github.com/valldun1/ruslan.git /tmp/ruslan 2>/dev/null || \
    git clone --depth 1 \
    https://github.com/valldun1/ruslan.git /tmp/ruslan

# Install ruslan into prefix
$PYTHON -m pip install --no-cache-dir --target "$PIP_TARGET_DIR" \
    --platform=manylinux2014_aarch64 --platform=manylinux_2_17_aarch64 \
    --only-binary=:all: \
    --implementation cp --python-version 3.10 \
    -e /tmp/ruslan 2>&1 | tail -5 || echo "WARN: ruslan install failed"

echo "=== Cleanup ==="

# Remove unnecessary files to reduce size
rm -rf "$PREFIX_DIR/tmp"/* 2>/dev/null || true
rm -rf "$PREFIX_DIR/var/cache"/* 2>/dev/null || true
find "$PREFIX_DIR" -name "__pycache__" -type d -exec rm -rf {} + 2>/dev/null || true
find "$PREFIX_DIR" -name "*.pyc" -delete 2>/dev/null || true
find "$PREFIX_DIR" -name "*.pyo" -delete 2>/dev/null || true

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
