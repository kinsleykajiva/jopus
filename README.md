# Jopus

**Jopus** provides high-performance Java bindings for the [Opus Interactive Audio Codec](https://opus-codec.org/) using [Project Panama](https://openjdk.org/projects/panama/) (Foreign Function & Memory API). It includes utilities for efficient G.711 (A-law/U-law) to Opus conversion, designed for VoIP and real-time audio applications.

## Project Structure

This is a multi-module Maven project:

- **jopus** - Core library with Opus bindings and audio utilities
- **jopus-demo-app** - Demo application showing library usage

## Prerequisites

- **Java**: JDK 24 or later with Project Panama support
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
- `jopus/target/jopus-1.0.2.jar` - Core library (with bundled native libs)
- `jopus-demo-app/target/jopus-demo-app-1.0.2.jar` - Demo application

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
    <version>1.1.0</version>
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
# From project root after build
java -cp "jopus-demo-app/target/jopus-demo-app-1.0.2.jar;jopus/target/jopus-1.0.2.jar" io.github.kinsleykajiva.demo.Main
```

**Note**: Starting from version 1.0.2, native DLLs are bundled within the JAR and extracted automatically at runtime. Manual setup of the library path is no longer strictly required.

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

The following native libraries must be available at runtime:

- **Windows**: `ogg.dll`, `opus.dll`, `opusenc.dll`, `opusfile.dll`
- **Linux**: `libogg.so`, `libopus.so`, `libopusenc.so`, `libopusfile.so`

Jopus 1.1.0+ automatically bundles and loads these from the JAR for both platforms.

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
