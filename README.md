# Jopus

**Jopus** provides high-performance Java bindings for the [Opus Interactive Audio Codec](https://opus-codec.org/) using [Project Panama](https://openjdk.org/projects/panama/) (Foreign Function & Memory API). It includes utilities for efficient G.711 (A-law/U-law) to Opus conversion, designed for VoIP and real-time audio applications.

## Prerequisites

- **Java**: JDK 22 or later (Currently using JDK 25 with Preview Features enabled).
- **CMake**: Version 3.10+ for building the native library.
- **Compiler**: MSVC (Windows), GCC, or Clang (Linux/macOS).
- **Maven**: For building the Java project.
- **jextract** (Optional): Required only if you intend to regenerate the Java bindings.

## Setup & Build

### 1. Build the Native Library
You must compile the Opus native library (`opus.dll` / `libopus.so`) before running the application.

**Windows (PowerShell):**
```powershell
./build_opus.ps1
```
This script configures CMake, builds the shared library, and copies `opus.dll` to the project root.

### 2. Compile the Java Project
```bash
mvn clean compile
```

### 3. Generate Bindings (Optional)
If you need to regenerate the Java bindings from `opus.h`:
```powershell
./generate_bindings.ps1
```
*Note: This requires `jextract` to be installed and available in your PATH.*

## Running the Application

Since this library uses Project Panama (Preview Feature in current JDKs) and native access, you **must** provide specific VM arguments when running.

**Run the Verification/Test Class:**
```bash
java --enable-preview --enable-native-access=ALL-UNNAMED -cp target/classes io.github.kinsleykajiva.opus.Main
```

## Usage

### G.711 to Opus Conversion
The `OpusCodec` class provides static utilities for converting Base64-encoded G.711 audio directly to Opus.

```java
import io.github.kinsleykajiva.opus.OpusCodec;

public class AudioConverter {
    public void processAudio(String g711Base64) {
        // Convert G.711 A-law to Opus
        boolean isALaw = true;
        String opusBase64 = OpusCodec.convertG711ToOpus(g711Base64, isALaw);
        
        System.out.println("Opus Output: " + opusBase64);
    }
}
```

### Native Opus API
You can also access the raw Opus C API via the generated bindings in `io.github.kinsleykajiva.opus.opus_h`.

```java
try (Arena arena = Arena.ofConfined()) {
    MemorySegment errorPtr = arena.allocate(C_INT);
    MemorySegment encoder = opus_encoder_create(48000, 2, OPUS_APPLICATION_AUDIO(), errorPtr);
    // ... usage ...
    opus_encoder_destroy(encoder);
}
```
```java
import io.github.kinsleykajiva.AudioLib;
import java.io.File;

// Convert A-law Base64 to Opus Base64
String opusBase64 = AudioLib.convert(base64Alaw)
    .fromAlaw()
    .withSampleRate(8000)
    .asBase64();

// Convert PCM file to Opus file
AudioLib.convert(new File("input.pcm"))
    .withSampleRate(48000)
    .withBitrate(128000)
    .asFile("output.opus");
```

## Build Requirements

- JDK 25+ (for Panama / Jextract)
- CMake (to build native libraries)
- Native libraries: `ogg.dll`, `opus.dll`, `opusenc.dll`, `opusfile.dll` must be in the library path.

## Building

Run `pwsh build_all.ps1` to build all dependencies.
Run `pwsh generate_bindings.ps1` to generate Java bindings.

## Project Structure

- `src/main/java/io/github/kinsleykajiva/opus/` - Generated bindings and utility classes.
- `opus-1.6.1/` - Native Opus source code.
- `build_opus.ps1` - Script to build the native Opus library.
- `generate_bindings.ps1` - Script to generate
