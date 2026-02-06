$ErrorActionPreference = "Stop"

# Use absolute path for install prefix to avoid CMake finding issues
$ROOT_DIR = Get-Location
$installDir = "$ROOT_DIR/install"

# Ensure clean install dir
if (Test-Path $installDir) { Remove-Item -Recurse -Force $installDir }
New-Item -ItemType Directory -Path $installDir | Out-Null

function Build-Lib ($name, $dirName, $extraArgs) {
    Write-Host "------------------------------------------------"
    Write-Host "Building $name in $ROOT_DIR/$dirName..."
    Write-Host "------------------------------------------------"
    
    if (!(Test-Path "$ROOT_DIR/$dirName")) {
        throw "Error: Directory $ROOT_DIR/$dirName does not exist!"
    }

    if (!(Test-Path "$ROOT_DIR/$dirName/CMakeLists.txt")) {
        Get-ChildItem "$ROOT_DIR/$dirName"
        throw "Error: $ROOT_DIR/$dirName/CMakeLists.txt not found!"
    }

    $buildDir = "$ROOT_DIR/$dirName/build"
    if (Test-Path $buildDir) { Remove-Item -Recurse -Force $buildDir }
    New-Item -ItemType Directory -Path $buildDir | Out-Null

    Push-Location $buildDir
    
    # Common flags: Release, Shared Libs
    $cmakeArgs = @(
        "..",
        "-DCMAKE_BUILD_TYPE=Release",
        "-DBUILD_SHARED_LIBS=ON",
        "-DCMAKE_INSTALL_PREFIX=$installDir"
    ) + $extraArgs

    cmake @cmakeArgs
    if ($LASTEXITCODE -ne 0) { throw "CMake configuration failed for $name" }
    cmake --build . --config Release
    if ($LASTEXITCODE -ne 0) { throw "Build failed for $name" }
    cmake --install . --config Release
    if ($LASTEXITCODE -ne 0) { throw "Install failed for $name" }

    Pop-Location
}

# 1. Build libogg
# libogg usually has simple CMake support
Build-Lib "libogg" "ogg" @()

# 2. Build libopus
# Disable testing/programs
Build-Lib "libopus" "opus-1.6.1" @(
    "-DOPUS_BUILD_TESTING=OFF", 
    "-DOPUS_BUILD_PROGRAMS=OFF"
)

# 3. Build libopusenc
# Needs to find Ogg and Opus. We point PREDIX_PATH to our install dir.
Build-Lib "libopusenc" "libopusenc" @(
    "-DCMAKE_PREFIX_PATH=$installDir",
    "-DOPUS_INCLUDE_DIR=$installDir/include", 
    "-DOPUS_LIBRARY=$installDir/lib/opus.lib",
    "-DOGG_INCLUDE_DIR=$installDir/include", 
    "-DOGG_LIBRARY=$installDir/lib/ogg.lib"
)

# 4. Build opusfile
# Needs ogg/opus.
# Note: opusfile might enable http/ssl by default which can fail without deps. We disable.
Build-Lib "opusfile" "opusfile" @(
    "-DCMAKE_PREFIX_PATH=$installDir",
    "-DOPUS_INCLUDE_DIR=$installDir/include", 
    "-DOPUS_LIBRARY=$installDir/lib/opus.lib",
    "-DOGG_INCLUDE_DIR=$installDir/include", 
    "-DOGG_LIBRARY=$installDir/lib/ogg.lib",
    "-DOP_DISABLE_HTTP=ON",
    "-DOP_DISABLE_DOCS=ON"
)

# Copy all DLLs to resources
$TARGET_DIR = "$ROOT_DIR/jopus/src/main/resources"
if (!(Test-Path $TARGET_DIR)) { New-Item -ItemType Directory -Path $TARGET_DIR | Out-Null }

Write-Host "Copying DLLs to $TARGET_DIR..."
Get-ChildItem -Path "$installDir/bin" -Filter "*.dll" | Copy-Item -Destination $TARGET_DIR -Force

Write-Host "Build Complete!"
