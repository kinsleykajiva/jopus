package io.github.kinsleykajiva.demo;

import io.github.kinsleykajiva.opus.opus_h;
import io.github.kinsleykajiva.opus.OpusCodec;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteOrder;
import java.util.Arrays;

public class MainCriticalTest {
    public static void main(String[] args) {
        System.out.println("Starting Critical Linker Test...");

        // Ensure native libraries are loaded
        OpusCodec.loadNativeLibraries();

        // Constants
        int sampleRate = 48000;
        int channels = 1;
        int application = opus_h.OPUS_APPLICATION_VOIP();
        int frameSize = 960; // 20ms at 48kHz
        int maxDataBytes = 4000;

        try (Arena arena = Arena.ofConfined()) {
            // Initialize Encoder
            MemorySegment errorPtr = arena.allocate(ValueLayout.JAVA_INT);
            MemorySegment encoder = opus_h.opus_encoder_create(sampleRate, channels, application, errorPtr);
            int error = errorPtr.get(ValueLayout.JAVA_INT, 0);
            if (error != opus_h.OPUS_OK()) {
                throw new RuntimeException("Failed to create encoder: " + error);
            }
            System.out.println("Encoder created successfully.");

            // Initialize Decoder
            MemorySegment decoder = opus_h.opus_decoder_create(sampleRate, channels, errorPtr);
            error = errorPtr.get(ValueLayout.JAVA_INT, 0);
            if (error != opus_h.OPUS_OK()) {
                throw new RuntimeException("Failed to create decoder: " + error);
            }
            System.out.println("Decoder created successfully.");

            // Create Dummy PCM Data
            short[] pcmIn = new short[frameSize * channels];
            for (int i = 0; i < pcmIn.length; i++) {
                pcmIn[i] = (short) (Math.sin(i * 0.01) * 32767);
            }

            // Encode (Critical)
            byte[] encodedData = new byte[maxDataBytes];
            long startTime = System.nanoTime();
            int len = opus_h.opus_encode_critical(encoder, pcmIn, frameSize, encodedData, maxDataBytes);
            long endTime = System.nanoTime();

            if (len < 0) {
                String errStr = opus_h.opus_strerror(len).getString(0);
                throw new RuntimeException("Encoding failed: " + errStr + " (" + len + ")");
            }
            System.out.println("Encoded " + len + " bytes in " + (endTime - startTime) / 1000 + "us");

            // Decode (Critical)
            short[] pcmOut = new short[frameSize * channels];
            // We need to pass the actual length of valid data, not the whole buffer
            // Since we don't resize the array, we can pass the whole array for buffer but
            // len param matters.
            // Wait, opus_decode takes `len` which is number of bytes in compressed data.
            // And data pointer.
            // The critical wrapper takes byte[] data.
            // If we intentionally pass the whole array but tell it len is smaller, it
            // should work fine
            // because `MemorySegment.ofArray(data)` covers the whole array but the native
            // function
            // only reads up to `len`.

            startTime = System.nanoTime();
            int frameSizeOut = opus_h.opus_decode_critical(decoder, encodedData, len, pcmOut, frameSize, 0);
            endTime = System.nanoTime();

            if (frameSizeOut < 0) {
                String errStr = opus_h.opus_strerror(frameSizeOut).getString(0);
                throw new RuntimeException("Decoding failed: " + errStr + " (" + frameSizeOut + ")");
            }
            System.out.println("Decoded " + frameSizeOut + " samples in " + (endTime - startTime) / 1000 + "us");

            // Verify basic correctness (not silence)
            boolean hasSignal = false;
            for (short s : pcmOut) {
                if (s != 0) {
                    hasSignal = true;
                    break;
                }
            }

            if (!hasSignal) {
                System.out.println("WARNING: Decoded output is silence (zeros).");
            } else {
                System.out.println("Decoded output contains signal.");
            }

            // Cleanup
            opus_h.opus_encoder_destroy(encoder);
            opus_h.opus_decoder_destroy(decoder);

            System.out.println("Critical Linker Test Passed!");
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
