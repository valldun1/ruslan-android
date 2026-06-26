#!/bin/bash
set -euo pipefail

# Build Termux prefix with Ruslan Agent for ARM64
# Output: usr.tar.zst

TERMUX_APK_URL="https://f-droid.org/repo/com.termux_118.apk"
PREFIX_DIR="/prefix"
OUTPUT_DIR="${OUTPUT_DIR:-/output}"

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

echo "=== Installing Base Packages ==="

# Use proot to run commands in the prefix
proot() {
    command proot -0 -r "$PREFIX_DIR" -b /dev -b /proc -b /sys "$@"
}

# Update package lists
proot /usr/bin/apt update

# Install essential packages
proot /usr/bin/apt install -y --no-install-recommends \
    python \
    python-pip \
    git \
    rust \
    binutils \
    libxml2 \
    libxslt \
    openssl \
    ca-certificates \
    zlib \
    libffi

echo "=== Installing Python Dependencies ==="

# Upgrade pip
proot /usr/bin/python3 -m pip install --upgrade pip

# Install build dependencies first (for Rust compilation)
proot /usr/bin/python3 -m pip install --no-cache-dir \
    maturin \
    setuptools-rust \
    wheel

# Install pinned dependencies
if [ -f /build/pin/pip.txt ]; then
    echo "Installing from pip.txt..."
    proot /usr/bin/python3 -m pip install --no-cache-dir -r /build/pin/pip.txt
fi

if [ -f /build/pin/python.txt ]; then
    echo "Installing from python.txt..."
    proot /usr/bin/python3 -m pip install --no-cache-dir -r /build/pin/python.txt
fi

echo "=== Installing Ruslan Agent ==="

# Clone and install Ruslan Agent
RUSLAN_VERSION="${RUSLAN_VERSION:-0.17.0}"
proot /usr/bin/git clone --depth 1 --branch "v${RUSLAN_VERSION}" \
    https://github.com/valldun1/ruslan.git /tmp/ruslan 2>/dev/null || \
    proot /usr/bin/git clone --depth 1 \
    https://github.com/valldun1/ruslan.git /tmp/ruslan

proot -w /tmp/ruslan /usr/bin/python3 -m pip install --no-cache-dir -e .

echo "=== Cleanup ==="

# Remove unnecessary files to reduce size
rm -rf "$PREFIX_DIR/tmp"/*
rm -rf "$PREFIX_DIR/var/cache"/*
find "$PREFIX_DIR" -name "__pycache__" -type d -exec rm -rf {} + 2>/dev/null || true
find "$PREFIX_DIR" -name "*.pyc" -delete 2>/dev/null || true
find "$PREFIX_DIR" -name "*.pyo" -delete 2>/dev/null || true

# Strip binaries
find "$PREFIX_DIR/bin" -type f -executable -exec strip {} \; 2>/dev/null || true
find "$PREFIX_DIR/lib" -name "*.so" -exec strip {} \; 2>/dev/null || true

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
