$ErrorActionPreference = "Stop"

$opusDir = "opus-1.6.1"
$buildDir = "$opusDir/build"

Write-Host "Creating build directory..."
if (!(Test-Path -Path $buildDir)) {
    New-Item -ItemType Directory -Path $buildDir | Out-Null
}

Push-Location $buildDir

Write-Host "Configuring CMake..."
# Disable testing and usage of programs to speed up build and avoid dependencies
cmake .. -DOPUS_BUILD_TESTING=OFF -DOPUS_BUILD_PROGRAMS=OFF -DCMAKE_BUILD_TYPE=Release -DBUILD_SHARED_LIBS=ON

Write-Host "Building Opus..."
cmake --build . --config Release

Pop-Location

# Locate and copy the DLL
$dllSource = "$buildDir/Release/opus.dll"
if (!(Test-Path $dllSource)) {
    # Try finding it in just build dir if not in Release (depends on generator)
    $dllSource = "$buildDir/opus.dll"
}

if (Test-Path $dllSource) {
    Write-Host "Copying opus.dll to project root..."
    Copy-Item -Path $dllSource -Destination "opus.dll" -Force
    Write-Host "Build success! opus.dll is at $(Get-Location)/opus.dll"
}
else {
    Write-Error "Could not find opus.dll after build!"
}
