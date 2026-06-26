# Dockerfile for building Termux prefix with Ruslan Agent
FROM ubuntu:22.04

# Install dependencies
RUN apt-get update && apt-get install -y \
    qemu-user-static \
    binfmt-support \
    zstd \
    tar \
    wget \
    unzip \
    curl \
    python3 \
    python3-pip \
    && rm -rf /var/lib/apt/lists/*

# Enable ARM64 emulation
RUN update-binfmts --enable qemu-aarch64

# Working directory
WORKDIR /build

# Copy build scripts
COPY build-prefix.sh /build/
COPY pin/ /build/pin/
RUN chmod +x /build/build-prefix.sh

# Default command
CMD ["/build/build-prefix.sh"]
