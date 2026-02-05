#!/bin/bash
set -e

# Use absolute path for install prefix
ROOT_DIR=$(pwd)
installDir="$ROOT_DIR/install"

# Ensure clean install dir
if [ -d "$installDir" ]; then rm -rf "$installDir"; fi
mkdir -p "$installDir"

# Set paths for pkg-config to find our built libraries
export PKG_CONFIG_PATH="$installDir/lib/pkgconfig:$PKG_CONFIG_PATH"
export LD_LIBRARY_PATH="$installDir/lib:$LD_LIBRARY_PATH"

build_lib() {
    local name=$1
    local dirName=$2
    shift 2
    local extraArgs=("$@")

    echo "------------------------------------------------"
    echo "Building $name in $ROOT_DIR/$dirName..."
    echo "------------------------------------------------"
    
    if [ ! -d "$ROOT_DIR/$dirName" ]; then
        echo "Error: Directory $ROOT_DIR/$dirName does not exist!"
        exit 1
    fi

    if [ -f "$ROOT_DIR/$dirName/CMakeLists.txt" ]; then
        echo "Using CMake for $name..."
        buildDir="$ROOT_DIR/$dirName/build"
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
    elif [ -f "$ROOT_DIR/$dirName/autogen.sh" ] || [ -f "$ROOT_DIR/$dirName/configure" ]; then
        echo "Using Autotools for $name..."
        pushd "$ROOT_DIR/$dirName"
        if [ -f "autogen.sh" ]; then
            bash autogen.sh
        fi
        ./configure --prefix="$installDir" --enable-shared "${extraArgs[@]}"
        make
        make install
        popd
    else
        echo "Error: No CMakeLists.txt or configure script found in $ROOT_DIR/$dirName!"
        ls -la "$ROOT_DIR/$dirName"
        exit 1
    fi
}

# 1. Build libogg
build_lib "libogg" "ogg"

# 2. Build libopus
build_lib "libopus" "opus-1.6.1" \
    -DOPUS_BUILD_TESTING=OFF \
    -DOPUS_BUILD_PROGRAMS=OFF \
    --disable-extra-programs

# 3. Build libopusenc
build_lib "libopusenc" "libopusenc"

# 4. Build opusfile
build_lib "opusfile" "opusfile" \
    --disable-http \
    -DOP_DISABLE_HTTP=ON

# 5. Build opus-tools
build_lib "opus-tools" "opus-tools"

# Copy all .so files to root
echo "Copying share libraries to project root..."
find "$installDir/lib" -name "*.so*" -exec cp -L {} "$ROOT_DIR" \;

echo "Build Complete!"
