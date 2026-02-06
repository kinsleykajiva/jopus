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
    local cmakeArgs=()
    local autoArgs=()

    # Separate arguments
    for arg in "$@"; do
        if [[ $arg == -D* ]]; then
            cmakeArgs+=("$arg")
        elif [[ $arg == --enable-* ]] || [[ $arg == --disable-* ]]; then
            autoArgs+=("$arg")
        else
            # Default to both if unsure, or you can add more logic
            cmakeArgs+=("$arg")
            autoArgs+=("$arg")
        fi
    done

    echo "------------------------------------------------"
    echo "Building $name in $ROOT_DIR/$dirName..."
    echo "------------------------------------------------"
    
    if [ ! -d "$ROOT_DIR/$dirName" ]; then
        echo "Error: Directory $ROOT_DIR/$dirName does not exist!"
        exit 1
    fi

    if [ -f "$ROOT_DIR/$dirName/autogen.sh" ] || [ -f "$ROOT_DIR/$dirName/configure" ]; then
        echo "Using Autotools for $name..."
        pushd "$ROOT_DIR/$dirName"
        if [ -f "autogen.sh" ]; then
            bash autogen.sh
        fi
        ./configure --prefix="$installDir" --enable-shared "${autoArgs[@]}"
        make
        make install
        popd
    elif [ -f "$ROOT_DIR/$dirName/CMakeLists.txt" ]; then
        echo "Using CMake for $name..."
        buildDir="$ROOT_DIR/$dirName/build"
        if [ -d "$buildDir" ]; then rm -rf "$buildDir"; fi
        mkdir -p "$buildDir"
        pushd "$buildDir"
        cmake .. \
            -DCMAKE_BUILD_TYPE=Release \
            -DBUILD_SHARED_LIBS=ON \
            -DCMAKE_INSTALL_PREFIX="$installDir" \
            "${cmakeArgs[@]}"
        cmake --build . --config Release
        cmake --install . --config Release
        popd
    else
        echo "Error: No CMakeLists.txt or configure script found in $ROOT_DIR/$dirName!"
        ls -la "$ROOT_DIR/$dirName"
        exit 1
    fi
}

# 1. Build libogg
build_lib "libogg" "ogg"
# Fix ogg version if unknown
sed -i 's/Version: unknown/Version: 1.3.5/' "$installDir/lib/pkgconfig/ogg.pc"

# 2. Build libopus
build_lib "libopus" "opus-1.6.1" \
    -DOPUS_BUILD_TESTING=OFF \
    -DOPUS_BUILD_PROGRAMS=OFF \
    --disable-extra-programs
# Fix opus version if unknown
sed -i 's/Version: unknown/Version: 1.6.1/' "$installDir/lib/pkgconfig/opus.pc"

# 3. Build libopusenc
# Pass explicit flags to bypass pkg-config version check failures if they persist
OPUS_CFLAGS="-I$installDir/include/opus" \
OPUS_LIBS="-L$installDir/lib -lopus" \
OGG_CFLAGS="-I$installDir/include" \
OGG_LIBS="-L$installDir/lib -logg" \
build_lib "libopusenc" "libopusenc"

# 4. Build opusfile
OPUS_CFLAGS="-I$installDir/include/opus" \
OPUS_LIBS="-L$installDir/lib -lopus" \
OGG_CFLAGS="-I$installDir/include" \
OGG_LIBS="-L$installDir/lib -logg" \
build_lib "opusfile" "opusfile" \
    --disable-http \
    -DOP_DISABLE_HTTP=ON
# Fix opusfile version if unknown
sed -i 's/Version: unknown/Version: 0.12/' "$installDir/lib/pkgconfig/opusfile.pc"
sed -i 's/Version: unknown/Version: 0.12/' "$installDir/lib/pkgconfig/opusurl.pc"

# 5. Build opus-tools
OPUS_CFLAGS="-I$installDir/include/opus" \
OPUS_LIBS="-L$installDir/lib -lopus" \
OGG_CFLAGS="-I$installDir/include" \
OGG_LIBS="-L$installDir/lib -logg" \
LIBOPUSENC_CFLAGS="-I$installDir/include/opus" \
LIBOPUSENC_LIBS="-L$installDir/lib -lopusenc" \
OPUSFILE_CFLAGS="-I$installDir/include/opus" \
OPUSFILE_LIBS="-L$installDir/lib -lopusfile" \
OPUSURL_CFLAGS="-I$installDir/include/opus" \
OPUSURL_LIBS="-L$installDir/lib -lopusurl -lopusfile" \
build_lib "opus-tools" "opus-tools" --disable-flac

# Copy all .so files to resources
TARGET_DIR="$ROOT_DIR/jopus/src/main/resources"
if [ ! -d "$TARGET_DIR" ]; then
    mkdir -p "$TARGET_DIR"
fi

echo "Copying shared libraries to $TARGET_DIR..."
# We only want the base .so files, not the versioned ones
# If it's a symlink, -L will follow it. We copy to the base name.
for lib in ogg opus opusenc opusfile opusurl; do
    find "$installDir/lib" -name "lib${lib}.so*" \( -type f -o -type l \) | head -n 1 | xargs -I {} cp -vL {} "$TARGET_DIR/lib${lib}.so"
done

echo "Build Complete!"
