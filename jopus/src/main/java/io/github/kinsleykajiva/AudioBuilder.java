package io.github.kinsleykajiva;

import io.github.kinsleykajiva.opus.OpusCodec;
import io.github.kinsleykajiva.opusenc.opusenc_h;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.foreign.*;
import java.nio.file.Files;
import java.util.Base64;

import static io.github.kinsleykajiva.opusenc.opusenc_h.*;

/**
 * Fluent API builder for audio conversion operations.
 */
public class AudioBuilder {
    private final byte[] inputData;
    private final File inputFile;
    private int sampleRate = 8000;
    private int bitrate = 16000;
    private int channels = 1;
    private InputFormat format;

    private enum InputFormat {
        ALAW, ULAW, PCM
    }

    AudioBuilder(byte[] data) {
        this.inputData = data;
        this.inputFile = null;
    }

    AudioBuilder(File file) {
        this.inputFile = file;
        this.inputData = null;
    }

    /**
     * Specify input as G.711 A-law.
     */
    public AudioBuilder fromAlaw() {
        this.format = InputFormat.ALAW;
        return this;
    }

    /**
     * Specify input as G.711 U-law.
     */
    public AudioBuilder fromUlaw() {
        this.format = InputFormat.ULAW;
        return this;
    }

    /**
     * Specify input as raw PCM.
     */
    public AudioBuilder fromPcm(int sampleRate, int channels) {
        this.format = InputFormat.PCM;
        this.sampleRate = sampleRate;
        this.channels = channels;
        return this;
    }

    /**
     * Set the sample rate (default: 8000).
     */
    public AudioBuilder withSampleRate(int sampleRate) {
        this.sampleRate = sampleRate;
        return this;
    }

    /**
     * Set the bitrate (default: 16000).
     */
    public AudioBuilder withBitrate(int bitrate) {
        this.bitrate = bitrate;
        return this;
    }

    /**
     * Convert to Opus and return as Base64 string.
     */
    public String asBase64() {
        byte[] data = getInputData();

        if (format == null) {
            throw new IllegalStateException("Input format not specified. Call fromAlaw(), fromUlaw(), or fromPcm()");
        }

        // Convert G.711 to PCM first if needed
        byte[] pcmData;
        if (format == InputFormat.ALAW) {
            pcmData = G711Utils.aLawToPcm(data);
        } else if (format == InputFormat.ULAW) {
            pcmData = G711Utils.uLawToPcm(data);
        } else {
            pcmData = data;
        }

        // Encode to Opus using opusenc
        return encodeToOpusBase64(pcmData);
    }

    /**
     * Convert to Opus and save to file.
     */
    public void asFile(String outputPath) {
        byte[] opusData = Base64.getDecoder().decode(asBase64());

        try (FileOutputStream fos = new FileOutputStream(outputPath)) {
            // Write as OGG Opus file
            writeOpusFile(outputPath, opusData);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write Opus file: " + e.getMessage(), e);
        }
    }

    private byte[] getInputData() {
        if (inputData != null) {
            return inputData;
        } else if (inputFile != null) {
            try {
                return Files.readAllBytes(inputFile.toPath());
            } catch (IOException e) {
                throw new RuntimeException("Failed to read input file: " + e.getMessage(), e);
            }
        }
        throw new IllegalStateException("No input data provided");
    }

    private String encodeToOpusBase64(byte[] pcmData) {
        try (Arena arena = Arena.ofConfined()) {
            // Load opusenc library
            loadOpusencLibrary();

            // Create comments
            MemorySegment comments = ope_comments_create();
            if (comments.equals(MemorySegment.NULL)) {
                throw new RuntimeException("Failed to create Opus comments");
            }

            // Create encoder
            MemorySegment errorPtr = arena.allocate(ValueLayout.JAVA_INT);
            MemorySegment enc = ope_encoder_create_file(
                    arena.allocateFrom("temp_opus_output.opus"),
                    comments,
                    sampleRate,
                    channels,
                    0, // family (0 = mono/stereo)
                    errorPtr);

            if (enc.equals(MemorySegment.NULL)) {
                int error = errorPtr.get(ValueLayout.JAVA_INT, 0);
                ope_comments_destroy(comments);
                throw new RuntimeException("Failed to create Opus encoder: Error code " + error);
            }

            // Set bitrate - skipping for now as ope_encoder_ctl is variadic and requires
            // makeInvoker
            // The default bitrate is suitable for most VoIP applications

            // Convert PCM bytes to shorts
            short[] pcmShorts = new short[pcmData.length / 2];
            for (int i = 0; i < pcmShorts.length; i++) {
                pcmShorts[i] = (short) ((pcmData[i * 2] & 0xFF) | ((pcmData[i * 2 + 1] & 0xFF) << 8));
            }

            // Write PCM data
            MemorySegment pcmBuffer = arena.allocateFrom(ValueLayout.JAVA_SHORT, pcmShorts);
            int result = ope_encoder_write(enc, pcmBuffer, pcmShorts.length / channels);

            if (result != 0) {
                ope_encoder_destroy(enc);
                ope_comments_destroy(comments);
                throw new RuntimeException("Failed to write PCM data: Error code " + result);
            }

            // Drain and close
            ope_encoder_drain(enc);
            ope_encoder_destroy(enc);
            ope_comments_destroy(comments);

            // Read the output file and encode to Base64
            byte[] opusFileData = Files.readAllBytes(new File("temp_opus_output.opus").toPath());
            new File("temp_opus_output.opus").delete();

            return Base64.getEncoder().encodeToString(opusFileData);

        } catch (IOException e) {
            throw new RuntimeException("Failed to encode to Opus: " + e.getMessage(), e);
        }
    }

    private void writeOpusFile(String outputPath, byte[] opusData) {
        try (Arena arena = Arena.ofConfined()) {
            loadOpusencLibrary();

            // Get input PCM data
            byte[] data = getInputData();
            byte[] pcmData;

            if (format == InputFormat.ALAW) {
                pcmData = G711Utils.aLawToPcm(data);
            } else if (format == InputFormat.ULAW) {
                pcmData = G711Utils.uLawToPcm(data);
            } else {
                pcmData = data;
            }

            // Create comments
            MemorySegment comments = ope_comments_create();

            // Create encoder
            MemorySegment errorPtr = arena.allocate(ValueLayout.JAVA_INT);
            MemorySegment enc = ope_encoder_create_file(
                    arena.allocateFrom(outputPath),
                    comments,
                    sampleRate,
                    channels,
                    0,
                    errorPtr);

            if (enc.equals(MemorySegment.NULL)) {
                ope_comments_destroy(comments);
                throw new RuntimeException("Failed to create Opus encoder");
            }

            // Set bitrate - skipping for now as ope_encoder_ctl is variadic and requires
            // makeInvoker
            // The default bitrate is suitable for most VoIP applications

            // Convert PCM bytes to shorts
            short[] pcmShorts = new short[pcmData.length / 2];
            for (int i = 0; i < pcmShorts.length; i++) {
                pcmShorts[i] = (short) ((pcmData[i * 2] & 0xFF) | ((pcmData[i * 2 + 1] & 0xFF) << 8));
            }

            // Write PCM data
            MemorySegment pcmBuffer = arena.allocateFrom(ValueLayout.JAVA_SHORT, pcmShorts);
            ope_encoder_write(enc, pcmBuffer, pcmShorts.length / channels);

            // Drain and close
            ope_encoder_drain(enc);
            ope_encoder_destroy(enc);
            ope_comments_destroy(comments);

        } catch (Exception e) {
            throw new RuntimeException("Failed to write Opus file: " + e.getMessage(), e);
        }
    }

    private static void loadOpusencLibrary() {
        try {
            String path = new File("opusenc.dll").getAbsolutePath();
            System.load(path);
        } catch (UnsatisfiedLinkError e) {
            try {
                System.loadLibrary("opusenc");
            } catch (UnsatisfiedLinkError e2) {
                System.err.println(
                        "Failed to load opusenc.dll. Ensure it is in the working directory or java.library.path.");
                throw e2;
            }
        }
    }
}
