# Jopus

**Jopus** provides high-performance Java bindings for the [Opus Interactive Audio Codec](https://opus-codec.org/) using [Project Panama](https://openjdk.org/projects/panama/) (Foreign Function & Memory API). It includes utilities for efficient G.711 (A-law/U-law) to Opus conversion, designed for VoIP and real-time audio applications.

## Project Structure

This is a multi-module Maven project:

- **jopus-core** - Core library with Opus bindings and audio utilities
- **jopus-demo-app** - Demo application showing library usage

## Prerequisites

- **Java**: JDK 25 or later with Project Panama support
- **CMake**: Version 3.10+ for building native libraries
- **Compiler**: MSVC (Windows), GCC, or Clang (Linux/macOS)
- **Maven**: For building the Java project
- **jextract** (Optional): Required only if regenerating Java bindings

## Setup & Build

### 1. Build Native Libraries

Build all required native libraries (ogg, opus, opusenc, opusfile):

**Windows (PowerShell):**
```powershell
./build_all.ps1
```

This creates: `ogg.dll`, `opus.dll`, `opusenc.dll`, `opusfile.dll` in the project root.

### 2. Build the Java Project

```bash
mvn clean install
```

This builds:
- `jopus-core/target/jopus-1.0.jar` - Core library
- `jopus-demo-app/target/jopus-demo-app-1.0.jar` - Demo application

### 3. Generate Bindings (Optional)

To regenerate Java bindings from C headers:
```powershell
./generate_bindings.ps1
```

*Note: Requires `jextract` in your PATH.*

## Using the Library

### As a Maven Dependency

Add to your `pom.xml`:

```xml
<dependency>
    <groupId>io.github.kinsleykajiva</groupId>
    <artifactId>jopus</artifactId>
    <version>1.0</version>
</dependency>
```

### Fluent API Usage

```java
import io.github.kinsleykajiva.AudioLib;

// Convert A-law Base64 to Opus Base64
String opusBase64 = AudioLib.convert(base64Alaw)
    .fromAlaw()
    .withSampleRate(8000)
    .asBase64();

// Convert U-law to Opus file
AudioLib.convert(ulawData)
    .fromUlaw()
    .withSampleRate(8000)
    .withBitrate(16000)
    .asFile("output.opus");
```

### Running the Demo

From the project root:

```bash
cd jopus-demo-app
java "-Djava.library.path=.." -cp "target/jopus-demo-app-1.0.jar;../jopus-core/target/jopus-1.0.jar" io.github.kinsleykajiva.demo.Main
```

**Important**: Native DLLs must be in the library path or working directory.

## API Reference

### AudioLib (Entry Point)

- `AudioLib.convert(String base64)` - Convert from Base64 string
- `AudioLib.convert(byte[] data)` - Convert from byte array
- `AudioLib.convert(File file)` - Convert from file

### AudioBuilder (Fluent Interface)

**Input Formats:**
- `.fromAlaw()` - G.711 A-law input
- `.fromUlaw()` - G.711 U-law input
- `.fromPcm(int sampleRate, int channels)` - Raw PCM input

**Configuration:**
- `.withSampleRate(int rate)` - Set sample rate (default: 8000)
- `.withBitrate(int bitrate)` - Set bitrate (default: 16000)

**Output:**
- `.asBase64()` - Returns Base64 encoded Opus
- `.asFile(String path)` - Writes to Opus file

### G711Utils

Low-level utilities for G.711 conversion:

```java
import io.github.kinsleykajiva.G711Utils;

// A-law to PCM
short[] pcm = G711Utils.alawToPcm(alawBytes);

// U-law to PCM
short[] pcm = G711Utils.ulawToPcm(ulawBytes);
```

## Native Library Dependencies

The following native libraries must be available at runtime:

- `ogg.dll` / `libogg.so` - Ogg container format
- `opus.dll` / `libopus.so` - Opus codec
- `opusenc.dll` / `libopusenc.so` - High-level Opus encoding
- `opusfile.dll` / `libopusfile.so` - Opus file reading

Place these in:
- Project root directory, or
- System library path, or
- Specify with `-Djava.library.path=<path>`

## Building from Source

### Complete Build

```bash
# 1. Build native libraries
pwsh build_all.ps1

# 2. Generate Java bindings (optional)
pwsh generate_bindings.ps1

# 3. Build Java modules
mvn clean install
```

### Module Structure

```
jopus/
├── pom.xml (parent)
├── jopus-core/
│   ├── pom.xml
│   └── src/main/java/io/github/kinsleykajiva/
│       ├── AudioLib.java
│       ├── AudioBuilder.java
│       ├── G711Utils.java
│       └── [generated bindings]
├── jopus-demo-app/
│   ├── pom.xml
│   └── src/main/java/io/github/kinsleykajiva/demo/
│       └── Main.java
├── install/ (native libs output)
└── [build scripts]
```

## License

This project uses the Opus codec libraries which are licensed under BSD.

## Credits

- [Opus Codec](https://opus-codec.org/)
- [Xiph.Org Foundation](https://xiph.org/)
- Native libraries: ogg, opus, libopusenc, opusfile
