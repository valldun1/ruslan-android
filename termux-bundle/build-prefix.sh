#!/bin/bash
set -euo pipefail

# Build Termux prefix with Ruslan Agent for ARM64
# Output: usr.tar.zst
#
# CHANGELOG (2026-06-26):
#   - Replaced apt-get with pkg install (Termux native package manager)
#   - proot used only for chroot/sandbox isolation, not for package install
#   - Python deps installed via pip install --target=$PREFIX_DIR/site-packages

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

echo "=== Installing Base Packages (via pkg, native Termux) ==="

# Termux's bootstrap already has pkg + python. Install additional packages
# using `pkg` (not apt-get — Termux uses pkg).
# We call pkg directly on the host, then chroot into prefix via proot to use them.
pkg update -y
pkg install -y python git openssl ca-certificates libxml2 libxslt zlib libffi binutils rust 2>&1 | tail -20 || true

# Sync the packages into the prefix by running pkg inside proot
echo "Syncing packages into prefix via proot..."

# Helper: run a command in the prefix using proot
proot_run() {
    command proot -0 -r "$PREFIX_DIR" -b /dev -b /proc -b /sys \
        -b /data/data/com.termux/files/usr:/host-usr \
        "$@"
}

# Verify Termux bootstrap has working python
echo "Verifying bootstrap python..."
proot_run /host-usr/bin/python3 --version

echo "=== Installing Python Dependencies ==="

# Install Python dependencies into the prefix's site-packages
# We do this from the host (where pkg put python) but target the prefix
PIP_TARGET="$PIP_TARGET_DIR" /usr/bin/python3 -m pip install --upgrade pip 2>&1 | tail -5 || true

# Install build dependencies first (for Rust compilation)
PIP_TARGET="$PIP_TARGET_DIR" /usr/bin/python3 -m pip install --no-cache-dir \
    maturin \
    setuptools-rust \
    wheel 2>&1 | tail -10 || true

# Install pinned dependencies
if [ -f /build/pin/pip.txt ]; then
    echo "Installing from pip.txt..."
    PIP_TARGET="$PIP_TARGET_DIR" /usr/bin/python3 -m pip install --no-cache-dir -r /build/pin/pip.txt 2>&1 | tail -10 || true
fi

if [ -f /build/pin/python.txt ]; then
    echo "Installing from python.txt..."
    PIP_TARGET="$PIP_TARGET_DIR" /usr/bin/python3 -m pip install --no-cache-dir -r /build/pin/python.txt 2>&1 | tail -10 || true
fi

echo "=== Installing Ruslan Agent ==="

# Clone Ruslan Agent into the prefix
RUSLAN_VERSION="${RUSLAN_VERSION:-0.17.0}"
git clone --depth 1 --branch "v${RUSLAN_VERSION}" \
    https://github.com/valldun1/ruslan.git /tmp/ruslan 2>/dev/null || \
    git clone --depth 1 \
    https://github.com/valldun1/ruslan.git /tmp/ruslan

PIP_TARGET="$PIP_TARGET_DIR" /usr/bin/python3 -m pip install --no-cache-dir -e /tmp/ruslan 2>&1 | tail -10 || true

echo "=== Cleanup ==="

# Remove unnecessary files to reduce size
rm -rf "$PREFIX_DIR/tmp"/* 2>/dev/null || true
rm -rf "$PREFIX_DIR/var/cache"/* 2>/dev/null || true
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
