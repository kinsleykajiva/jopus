$ErrorActionPreference = "Stop"

# Ensure output directory exists
$outputDir = "src/main/java"
if (!(Test-Path -Path $outputDir)) {
    New-Item -ItemType Directory -Path $outputDir | Out-Null
}

# Jextract commands for each library
function Generate-Binding ($name, $header, $pkg, $libs) {
    Write-Host "Generating bindings for $name in $pkg..."
    
    # Construct arguments array
    $args = @(
        "--output", "src/main/java",
        "--target-package", "io.github.kinsleykajiva.$pkg",
        "-I", "install/include",
        "-I", "install/include/opus"
    )
    
    foreach ($lib in $libs) {
        $args += "-l"
        $args += $lib
    }
    
    $args += $header
    
    # Run jextract with splatted arguments
    # Using & to run the command with the array of arguments
    & jextract @args
}

Generate-Binding "Ogg" "install/include/ogg/ogg.h" "ogg" @("ogg")
Generate-Binding "Opus" "install/include/opus/opus.h" "opus" @("opus")
Generate-Binding "OpusEnc" "install/include/opus/opusenc.h" "opusenc" @("opusenc")
Generate-Binding "OpusFile" "install/include/opus/opusfile.h" "opusfile" @("opusfile")

Write-Host "All bindings generated!"
