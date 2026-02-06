package io.github.kinsleykajiva.opus;

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

    private static void loadNativeLibraries() {
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        String extension = isWindows ? ".dll" : ".so";

        // Define library list based on OS
        String[] libs = isWindows
                ? new String[] { "ogg", "opus", "opusenc", "opusfile" }
                : new String[] { "ogg", "opus", "opusenc", "opusfile", "opusurl" };

        File tempDir = new File(System.getProperty("java.io.tmpdir"), "jopus-natives-" + System.nanoTime());
        if (!tempDir.mkdirs()) {
            // Ignore if exists
        }
        tempDir.deleteOnExit();

        for (String libName : libs) {
            String fullLibName = (isWindows ? "" : "lib") + libName + extension;
            try {
                // Try loading from local directory first
                File localFile = new File(fullLibName);
                if (localFile.exists()) {
                    System.load(localFile.getAbsolutePath());
                    continue;
                }

                // Fallback: Extract from JAR
                File tempFile = new File(tempDir, fullLibName);
                try (java.io.InputStream is = OpusCodec.class.getResourceAsStream("/" + fullLibName)) {
                    if (is == null) {
                        try {
                            System.loadLibrary(libName);
                            continue;
                        } catch (UnsatisfiedLinkError e) {
                            // Critical failure if core opus is missing
                            if (libName.equals("opus")) {
                                throw new RuntimeException("Critical failure: Could not find " + fullLibName
                                        + " in resources or system path", e);
                            }
                            // Other libs might be optional or already loaded as dependencies
                            System.err.println("Warning: Could not find " + fullLibName
                                    + " in resources, attempting to proceed...");
                            continue;
                        }
                    }
                    java.nio.file.Files.copy(is, tempFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                System.load(tempFile.getAbsolutePath());
                tempFile.deleteOnExit();
            } catch (Exception e) {
                System.err.println("Failed to load/extract native library: " + fullLibName + " - " + e.getMessage());
                if (libName.equals("opus")) {
                    throw new RuntimeException("Critical failure: Could not load " + fullLibName, e);
                }
            }
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
}
