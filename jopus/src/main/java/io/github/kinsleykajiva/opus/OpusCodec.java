package io.github.kinsleykajiva.opus;

import io.github.kinsleykajiva.G711Utils;

import java.io.File;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.nio.ByteOrder;

import static io.github.kinsleykajiva.opus.opus_h.*;

public class OpusCodec {

    private static final int SAMPLE_RATE = 8000;
    private static final int CHANNELS = 1;
    // OPUS_APPLICATION_VOIP = 2048 (from standard, verified in headers usually)
    // But we should use generated constants if possible.
    // Checking opus_h.java constants: OPUS_APPLICATION_VOIP should be there.

    // G.711 Tables and logic
    private static final short[] ALAW_TO_PCM = new short[256];
    private static final short[] ULAW_TO_PCM = new short[256];

    static {
        loadNativeLibraries();
        generateALawTable();
        generateULawTable();
    }

    private static volatile boolean isLibsLoaded = false;

    public static void loadNativeLibraries() {
        if (isLibsLoaded)
            return;
        synchronized (OpusCodec.class) {
            if (isLibsLoaded)
                return;

            String osName = System.getProperty("os.name").toLowerCase();
            boolean isWindows = osName.contains("win");
            boolean isLinux = osName.contains("linux") || osName.contains("nix") || osName.contains("nux");
            String extension = isWindows ? ".dll" : ".so";

            // Define library list based on OS
            String[] libs = isWindows
                    ? new String[] { "ogg", "opus", "opusenc", "opusfile" }
                    : new String[] { "ogg", "opus", "opusenc", "opusfile", "opusurl" };

            File tempDir = new File(System.getProperty("java.io.tmpdir"), "jopus-natives-" + System.nanoTime());
            if (!tempDir.exists() && !tempDir.mkdirs()) {
                System.err
                        .println("Warning: Could not create temp directory for natives: " + tempDir.getAbsolutePath());
            }
            tempDir.deleteOnExit();

            for (String libName : libs) {
                String fullLibName = (isWindows ? "" : "lib") + libName + extension;
                try {
                    // 1. Try loading from local file system (working directory)
                    File localFile = new File(fullLibName);
                    if (localFile.exists()) {
                        // System.out.println("Loading native library from local path: " +
                        // localFile.getAbsolutePath());
                        System.load(localFile.getAbsolutePath());
                        continue;
                    }

                    // 2. Try loading from classpath (resources)
                    try (java.io.InputStream is = OpusCodec.class.getResourceAsStream("/" + fullLibName)) {
                        if (is != null) {
                            File tempFile = new File(tempDir, fullLibName);
                            java.nio.file.Files.copy(is, tempFile.toPath(),
                                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                            // System.out.println("Loading native library from resources: " +
                            // tempFile.getAbsolutePath());
                            System.load(tempFile.getAbsolutePath());
                            tempFile.deleteOnExit();
                            continue;
                        }
                    }

                    // 3. Fallback: Try loading from system library path (LD_LIBRARY_PATH or
                    // java.library.path)
                    try {
                        // System.out.println("Attempting to load library from system path: " +
                        // libName);
                        System.loadLibrary(libName);
                        continue;
                    } catch (UnsatisfiedLinkError e) {
                        if (libName.equals("opus") || libName.equals("opusenc")) {
                            throw new RuntimeException("Critical failure: Could not find or load " + fullLibName
                                    + " from local path, resources, or system path.", e);
                        }
                        System.err.println("Warning: Could not load " + fullLibName + " (Optional or dependency)");
                    }
                } catch (Exception e) {
                    System.err.println("Error processing library " + libName + ": " + e.getMessage());
                    if (libName.equals("opus") || libName.equals("opusenc")) {
                        throw new RuntimeException("Critical failure: Could not load " + fullLibName, e);
                    }
                }
            }
            isLibsLoaded = true;
        }
    }

    private static void generateALawTable() {
        for (int i = 0; i < 256; i++) {
            int input = i ^ 0x55;
            int mantissa = (input & 0x0F) << 4;
            int segment = (input & 0x70) >> 4;
            int value = mantissa + 8;
            if (segment >= 1) {
                value += 0x100;
            }
            if (segment > 1) {
                value <<= (segment - 1);
            }
            int sign = (input & 0x80);
            ALAW_TO_PCM[i] = (short) ((sign == 0) ? value : -value);
        }
    }

    private static void generateULawTable() {
        // simplified u-law expansion
        for (int i = 0; i < 256; i++) {
            int mu = 255;
            int v = ~i;
            int sign = (v & 0x80) >> 7;
            int exponent = (v & 0x70) >> 4;
            int mantissa = v & 0x0F;
            int sample = (mu << 3) + 4 + (mantissa << 4); // wait, standard formula is cleaner
            // Standard Biased
            sample = ((mantissa << 3) + 132) << exponent;
            sample -= 132;
            if (sign != 0)
                sample = -sample;
            ULAW_TO_PCM[i] = (short) sample;
        }
    }

    // Direct G.711 Decoding
    private static short decodeALaw(byte b) {
        return ALAW_TO_PCM[b & 0xFF];
    }

    private static short decodeULaw(byte b) {
        return ULAW_TO_PCM[b & 0xFF];
    }

    /**
     * Converts a Base64 encoded G.711 string to a Base64 encoded Opus string.
     * 
     * @param base64Input Base64 encoded G.711 audio.
     * @param isALaw      True for A-law, False for u-law.
     * @return Base64 encoded Opus audio.
     */
    public static String convertG711ToOpus(String base64Input, boolean isALaw) {
        byte[] g711Data = Base64.getDecoder().decode(base64Input);

        // Convert to PCM 16-bit
        short[] pcmData = new short[g711Data.length];
        for (int i = 0; i < g711Data.length; i++) {
            pcmData[i] = isALaw ? decodeALaw(g711Data[i]) : decodeULaw(g711Data[i]);
        }

        try (Arena arena = Arena.ofConfined()) {
            // Create Encoder
            MemorySegment errorPtr = arena.allocate(C_INT);
            // OPUS_APPLICATION_VOIP = 2048
            MemorySegment encoder = opus_encoder_create(SAMPLE_RATE, CHANNELS, 2048, errorPtr);

            if (encoder.equals(MemorySegment.NULL)) {
                throw new RuntimeException("Failed to create Opus encoder: Error code " + errorPtr.get(C_INT, 0));
            }

            // Encode (Assuming simple one-shot or frame-based loop if large?)
            // Opus requires frame sizes. 20ms at 8000Hz = 160 samples.
            int frameSize = 160;
            if (pcmData.length % frameSize != 0) {
                // Padding or handling remainder? For simplicity, we process only full frames or
                // pad.
                // Let's pad with silence if needed.
            }

            int maxDataBytes = 4000; // ample space per frame

            // Output buffer for all frames
            // Simplification: We concat opus packets? Or just return one packet?
            // Usually RTP payload is one packet. But if input is long, it's multiple
            // packets.
            // Requirement says "smaller audio chucks on the fly".
            // If input is > 20ms, return multiple packets? Or just the encoded bytes
            // concat?
            // Usually simply concatenated Opus frames is valid for files/streams if framed,
            // but Base64 String output implies a single blob.
            // We will concatenate the raw Opus frames.

            java.io.ByteArrayOutputStream opusOutputStream = new java.io.ByteArrayOutputStream();

            // Native buffers
            MemorySegment pcmBuffer = arena.allocate(C_SHORT, frameSize);
            MemorySegment outBuffer = arena.allocate(C_CHAR, maxDataBytes);

            int offset = 0;
            while (offset + frameSize <= pcmData.length) {
                // Copy frame to native memory
                MemorySegment.copy(pcmData, offset, pcmBuffer, C_SHORT, 0, frameSize);

                int len = opus_encode(encoder, pcmBuffer, frameSize, outBuffer, maxDataBytes);

                if (len < 0) {
                    throw new RuntimeException("Opus encode error: " + len);
                }

                // Copy output to Java stream
                byte[] encodedFrame = new byte[len];
                MemorySegment.copy(outBuffer, 0, MemorySegment.ofArray(encodedFrame), 0, len);
                opusOutputStream.write(encodedFrame, 0, len);

                offset += frameSize;
            }

            opus_encoder_destroy(encoder);

            return Base64.getEncoder().encodeToString(opusOutputStream.toByteArray());
        }
    }

    /**
     * Converts a Base64 encoded Opus string to a Base64 encoded G.711 string.
     * 
     * @param base64Input Base64 encoded Opus audio (single packet or Ogg file).
     * @param isALaw      True for A-law, False for u-law.
     * @return Base64 encoded G.711 audio.
     */
    public static String convertOpusToG711(String base64Input, boolean isALaw) {
        byte[] opusData = Base64.getDecoder().decode(base64Input);

        // Check if it's an Ogg file (starts with 'OggS')
        if (opusData.length > 4 && opusData[0] == 'O' && opusData[1] == 'g' && opusData[2] == 'g'
                && opusData[3] == 'S') {
            return decodeOggToG711(opusData, isALaw);
        }

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment errorPtr = arena.allocate(C_INT);
            MemorySegment decoder = opus_decoder_create(SAMPLE_RATE, CHANNELS, errorPtr);

            if (decoder.equals(MemorySegment.NULL)) {
                throw new RuntimeException("Failed to create Opus decoder: Error code " + errorPtr.get(C_INT, 0));
            }

            // Max frame size for 120ms at 48kHz is 5760, but at 8kHz it's 960.
            int maxFrameSize = 960;
            MemorySegment pcmBuffer = arena.allocate(C_SHORT, maxFrameSize);
            MemorySegment opusBuffer = arena.allocateFrom(C_CHAR, opusData);

            int samplesDecoded = opus_decode(decoder, opusBuffer, opusData.length, pcmBuffer, maxFrameSize, 0);

            if (samplesDecoded < 0) {
                opus_decoder_destroy(decoder);
                throw new RuntimeException("Opus decode error: " + samplesDecoded);
            }

            byte[] pcmBytes = new byte[samplesDecoded * 2];
            for (int i = 0; i < samplesDecoded; i++) {
                short s = pcmBuffer.getAtIndex(C_SHORT, i);
                pcmBytes[i * 2] = (byte) (s & 0xFF);
                pcmBytes[i * 2 + 1] = (byte) ((s >> 8) & 0xFF);
            }

            opus_decoder_destroy(decoder);

            byte[] g711Data = isALaw ? G711Utils.pcmToAlaw(pcmBytes) : G711Utils.pcmToUlaw(pcmBytes);
            return Base64.getEncoder().encodeToString(g711Data);
        }
    }

    private static String decodeOggToG711(byte[] oggData, boolean isALaw) {
        loadNativeLibraries();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment errorPtr = arena.allocate(C_INT);
            MemorySegment oggPtr = arena.allocateFrom(C_CHAR, oggData);

            MemorySegment of = io.github.kinsleykajiva.opusfile.opusfile_h.op_open_memory(oggPtr, oggData.length,
                    errorPtr);
            if (of.equals(MemorySegment.NULL)) {
                throw new RuntimeException("Failed to open Ogg Opus from memory: Error " + errorPtr.get(C_INT, 0));
            }

            java.io.ByteArrayOutputStream pcmStream = new java.io.ByteArrayOutputStream();
            // 8kHz * 2 channels * 10s buffer max per read? Let's use 16000 samples.
            int bufSize = 16000;
            MemorySegment pcmBuf = arena.allocate(C_SHORT, bufSize);

            while (true) {
                int samplesRead = io.github.kinsleykajiva.opusfile.opusfile_h.op_read(of, pcmBuf, bufSize,
                        MemorySegment.NULL);
                if (samplesRead <= 0)
                    break;

                for (int i = 0; i < samplesRead; i++) {
                    short s = pcmBuf.getAtIndex(C_SHORT, i);
                    pcmStream.write(s & 0xFF);
                    pcmStream.write((s >> 8) & 0xFF);
                }
            }

            io.github.kinsleykajiva.opusfile.opusfile_h.op_free(of);

            byte[] pcmBytes = pcmStream.toByteArray();
            byte[] g711Data = isALaw ? G711Utils.pcmToAlaw(pcmBytes) : G711Utils.pcmToUlaw(pcmBytes);
            return Base64.getEncoder().encodeToString(g711Data);
        }
    }

    /**
     * Creates a new native Opus decoder.
     * 
     * @return MemorySegment pointer to the decoder
     */
    public static MemorySegment createDecoder() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment errorPtr = arena.allocate(C_INT);
            MemorySegment decoder = opus_decoder_create(SAMPLE_RATE, CHANNELS, errorPtr);

            if (decoder.equals(MemorySegment.NULL)) {
                throw new RuntimeException("Failed to create Opus decoder: Error code " + errorPtr.get(C_INT, 0));
            }
            return decoder;
        }
    }

    /**
     * Destroys a native Opus decoder.
     * 
     * @param decoder The decoder pointer to destroy
     */
    public static void destroyDecoder(MemorySegment decoder) {
        if (decoder != null && !decoder.equals(MemorySegment.NULL)) {
            opus_decoder_destroy(decoder);
        }
    }

    /**
     * Creates a new native Opus encoder.
     * 
     * @return MemorySegment pointer to the encoder
     */
    public static MemorySegment createEncoder() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment errorPtr = arena.allocate(C_INT);
            // OPUS_APPLICATION_VOIP = 2048
            MemorySegment encoder = opus_encoder_create(SAMPLE_RATE, CHANNELS, 2048, errorPtr);

            if (encoder.equals(MemorySegment.NULL)) {
                throw new RuntimeException("Failed to create Opus encoder: Error code " + errorPtr.get(C_INT, 0));
            }
            return encoder;
        }
    }

    /**
     * Destroys a native Opus encoder.
     * 
     * @param encoder The encoder pointer to destroy
     */
    public static void destroyEncoder(MemorySegment encoder) {
        if (encoder != null && !encoder.equals(MemorySegment.NULL)) {
            opus_encoder_destroy(encoder);
        }
    }

    /**
     * Encodes a chunk of PCM data using a pre-existing encoder.
     * Optimized for high-throughput streaming.
     *
     * @param encoder   The native encoder pointer
     * @param pcmData   16-bit PCM data (byte array)
     * @param outBuffer Pre-allocated output buffer (should be large enough, e.g.
     *                  4000 bytes)
     * @return The number of bytes written to outBuffer
     */
    public static int encodeChunk(MemorySegment encoder, byte[] pcmData, byte[] outBuffer) {
        // Assume frame size 20ms @ 8000Hz = 160 samples
        int frameSize = 160;
        int maxDataBytes = outBuffer.length;

        // Convert bytes to shorts
        short[] pcmShorts = new short[pcmData.length / 2];
        for (int i = 0; i < pcmShorts.length; i++) {
            pcmShorts[i] = (short) ((pcmData[i * 2] & 0xFF) | ((pcmData[i * 2 + 1] & 0xFF) << 8));
        }

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment pcmNative = arena.allocate(C_SHORT, frameSize);
            MemorySegment outNative = arena.allocate(C_CHAR, maxDataBytes);

            int offset = 0;
            int totalEncoded = 0;

            // Process frames
            while (offset + frameSize <= pcmShorts.length) {
                MemorySegment.copy(pcmShorts, offset, pcmNative, C_SHORT, 0, frameSize);

                int len = opus_encode(encoder, pcmNative, frameSize, outNative, maxDataBytes);
                if (len < 0) {
                    // Error or DTX
                    return len;
                }

                MemorySegment.copy(outNative, 0, MemorySegment.ofArray(outBuffer), totalEncoded, len);
                totalEncoded += len;
                offset += frameSize;
            }
            return totalEncoded;
        }
    }

    /**
     * Converts G.711 chunk to Opus using a pooled/cached encoder.
     */
    public static int convertG711Chunk(MemorySegment encoder, byte[] g711Data, boolean isALaw, byte[] outBuffer) {
        // Decode G.711 to PCM
        short[] pcmData = new short[g711Data.length];
        for (int i = 0; i < g711Data.length; i++) {
            pcmData[i] = isALaw ? decodeALaw(g711Data[i]) : decodeULaw(g711Data[i]);
        }

        // Use the PCM encoding logic (inline or call helper)
        // Assume frame size 20ms @ 8000Hz = 160 samples
        int frameSize = 160;
        int maxDataBytes = outBuffer.length;

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment pcmNative = arena.allocate(C_SHORT, frameSize);
            MemorySegment outNative = arena.allocate(C_CHAR, maxDataBytes);

            int offset = 0;
            int totalEncoded = 0;

            while (offset + frameSize <= pcmData.length) {
                MemorySegment.copy(pcmData, offset, pcmNative, C_SHORT, 0, frameSize);

                int len = opus_encode(encoder, pcmNative, frameSize, outNative, maxDataBytes);
                if (len < 0)
                    return len;

                MemorySegment.copy(outNative, 0, MemorySegment.ofArray(outBuffer), totalEncoded, len);
                totalEncoded += len;
                offset += frameSize;
            }
            return totalEncoded;
        }
    }

    /**
     * Decodes a chunk of Opus data using a pre-existing decoder.
     * Optimized for high-throughput streaming.
     *
     * @param decoder   The native decoder pointer
     * @param opusData  Opus packet bytes
     * @param outBuffer Pre-allocated output buffer for PCM (short array or byte
     *                  array converted)
     * @return The number of bytes written to outBuffer
     */
    public static int decodeChunk(MemorySegment decoder, byte[] opusData, byte[] outBuffer) {
        int maxFrameSize = 960; // 120ms @ 8kHz

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment opusNative = arena.allocateFrom(C_CHAR, opusData);
            MemorySegment pcmNative = arena.allocate(C_SHORT, maxFrameSize);

            int samplesDecoded = opus_decode(decoder, opusNative, opusData.length, pcmNative, maxFrameSize, 0);
            if (samplesDecoded < 0) {
                return samplesDecoded;
            }

            for (int i = 0; i < samplesDecoded; i++) {
                short s = pcmNative.getAtIndex(C_SHORT, i);
                outBuffer[i * 2] = (byte) (s & 0xFF);
                outBuffer[i * 2 + 1] = (byte) ((s >> 8) & 0xFF);
            }
            return samplesDecoded * 2;
        }
    }

    /**
     * Converts Opus chunk to G.711 using a pooled/cached decoder.
     */
    public static int convertOpusChunk(MemorySegment decoder, byte[] opusData, boolean isALaw, byte[] outBuffer) {
        byte[] pcmBuffer = new byte[1920]; // 960 samples * 2 bytes
        int pcmLen = decodeChunk(decoder, opusData, pcmBuffer);
        if (pcmLen < 0)
            return pcmLen;

        byte[] g711Data = isALaw ? G711Utils.pcmToAlaw(pcmBuffer) : G711Utils.pcmToUlaw(pcmBuffer);
        // Only take the actual decoded length
        int g711Len = pcmLen / 2;
        System.arraycopy(g711Data, 0, outBuffer, 0, g711Len);
        return g711Len;
    }

    // --- Encoder Pool ---

    public static class OpusEncoderPool {
        private final java.util.concurrent.BlockingQueue<MemorySegment> pool;
        private final int capacity;

        public OpusEncoderPool(int capacity) {
            this.capacity = capacity;
            this.pool = new java.util.concurrent.ArrayBlockingQueue<>(capacity);
            initialize();
        }

        private void initialize() {
            for (int i = 0; i < capacity; i++) {
                pool.offer(createEncoder());
            }
        }

        public MemorySegment borrowEncoder() {
            try {
                // If pool is empty, take() will block, effectively throttling
                return pool.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for Opus encoder", e);
            }
        }

        public void returnEncoder(MemorySegment encoder) {
            if (encoder != null) {
                pool.offer(encoder);
            }
        }

        public void close() {
            MemorySegment encoder;
            while ((encoder = pool.poll()) != null) {
                destroyEncoder(encoder);
            }
        }
    }

    // --- Decoder Pool ---

    public static class OpusDecoderPool {
        private final java.util.concurrent.BlockingQueue<MemorySegment> pool;
        private final int capacity;

        public OpusDecoderPool(int capacity) {
            this.capacity = capacity;
            this.pool = new java.util.concurrent.ArrayBlockingQueue<>(capacity);
            initialize();
        }

        private void initialize() {
            for (int i = 0; i < capacity; i++) {
                pool.offer(createDecoder());
            }
        }

        public MemorySegment borrowDecoder() {
            try {
                return pool.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for Opus decoder", e);
            }
        }

        public void returnDecoder(MemorySegment decoder) {
            if (decoder != null) {
                pool.offer(decoder);
            }
        }

        public void close() {
            MemorySegment decoder;
            while ((decoder = pool.poll()) != null) {
                destroyDecoder(decoder);
            }
        }
    }
}
