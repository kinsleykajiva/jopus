#!/bin/bash
set -e

# Use absolute path for install prefix
rootDir=$(pwd)
installDir="$rootDir/install"

# Ensure clean install dir
if [ -d "$installDir" ]; then rm -rf "$installDir"; fi
mkdir -p "$installDir"

build_lib() {
    local name=$1
    local dir=$2
    shift 2
    local extraArgs=("$@")

    echo "------------------------------------------------"
    echo "Building $name..."
    echo "------------------------------------------------"
    
    buildDir="$dir/build"
    if [ -d "$buildDir" ]; then rm -rf "$buildDir"; fi
    mkdir -p "$buildDir"

    pushd "$buildDir"
    
    cmake .. \
        -DCMAKE_BUILD_TYPE=Release \
        -DBUILD_SHARED_LIBS=ON \
        -DCMAKE_INSTALL_PREFIX="$installDir" \
        "${extraArgs[@]}"

    cmake --build . --config Release
    cmake --install . --config Release

    popd
}

# 1. Build libogg
build_lib "libogg" "ogg"

# 2. Build libopus
build_lib "libopus" "opus-1.6.1" \
    -DOPUS_BUILD_TESTING=OFF \
    -DOPUS_BUILD_PROGRAMS=OFF

# 3. Build libopusenc
build_lib "libopusenc" "libopusenc" \
    -DCMAKE_PREFIX_PATH="$installDir" \
    -DOPUS_INCLUDE_DIR="$installDir/include" \
    -DOPUS_LIBRARY="$installDir/lib/libopus.so" \
    -DOGG_INCLUDE_DIR="$installDir/include" \
    -DOGG_LIBRARY="$installDir/lib/libogg.so"

# 4. Build opusfile
build_lib "opusfile" "opusfile" \
    -DCMAKE_PREFIX_PATH="$installDir" \
    -DOPUS_INCLUDE_DIR="$installDir/include" \
    -DOPUS_LIBRARY="$installDir/lib/libopus.so" \
    -DOGG_INCLUDE_DIR="$installDir/include" \
    -DOGG_LIBRARY="$installDir/lib/libogg.so" \
    -DOP_DISABLE_HTTP=ON \
    -DOP_DISABLE_DOCS=ON

# Copy all .so files to root
echo "Copying share libraries to project root..."
find "$installDir/lib" -name "*.so*" -exec cp {} "$rootDir" \;

echo "Build Complete!"
