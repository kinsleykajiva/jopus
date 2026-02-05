$ErrorActionPreference = "Stop"

# Ensure output directory exists
$outputDir = "src/main/java"
if (!(Test-Path -Path $outputDir)) {
    New-Item -ItemType Directory -Path $outputDir | Out-Null
}

# Jextract command
# -I: include directory
# -t: target package
# -l: library name (opus)
# --output: source root
Write-Host "Generating bindings..."
jextract --output src/main/java `
    --target-package io.github.kinsleykajiva.opus `
    -I opus-1.6.1/include `
    -l opus `
    opus-1.6.1/include/opus.h

Write-Host "Bindings generated in src/main/java/io/github/kinsleykajiva/opus"
