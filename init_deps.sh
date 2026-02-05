#!/bin/bash
set -e

# Get absolute root directory
ROOT_DIR=$(pwd)

# Dependencies to clone
DEPS=("ogg" "opus-1.6.1" "libopusenc" "opusfile" "opus-tools")
URLS=(
    "https://github.com/xiph/ogg.git"
    "https://github.com/xiph/opus.git"
    "https://github.com/xiph/libopusenc.git"
    "https://github.com/xiph/opusfile.git"
    "https://github.com/xiph/opus-tools.git"
)

# Clone dependencies (overwrite if exists)
for i in "${!DEPS[@]}"; do
    dir="${DEPS[$i]}"
    url="${URLS[$i]}"
    
    echo "Processing $dir..."
    if [ -d "$ROOT_DIR/$dir" ]; then
        echo "Removing existing directory: $ROOT_DIR/$dir"
        rm -rf "$ROOT_DIR/$dir"
    fi
    
    git clone --depth 1 "$url" "$ROOT_DIR/$dir"
    
    if [ ! -f "$ROOT_DIR/$dir/CMakeLists.txt" ] && [ "$dir" != "opus-tools" ]; then
        echo "Warning: $dir might not have a root CMakeLists.txt or clone failed."
    fi
done

echo "Dependencies initialized successfully in $ROOT_DIR"

echo "Dependencies initialized successfully."
