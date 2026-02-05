package io.github.kinsleykajiva;

import io.github.kinsleykajiva.opus.opus_h;
import io.github.kinsleykajiva.opusenc.opusenc_h;
import io.github.kinsleykajiva.opusenc.OpusEncCallbacks;
import io.github.kinsleykajiva.opusenc.ope_write_func;
import io.github.kinsleykajiva.opusenc.ope_close_func;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.file.Files;
import java.util.Base64;

public class AudioBuilder {
    private byte[] inputData;
    private File inputFile;
    private int sampleRate = 48000;
    private int channels = 1;
    private int bitrate = 64000;
    private boolean isAlaw = false;
    private boolean isUlaw = false;
    private boolean isPcm = true;

    public AudioBuilder(byte[] data) {
        this.inputData = data;
    }

    public AudioBuilder(File file) {
        this.inputFile = file;
    }

    public AudioBuilder fromAlaw() {
        this.isAlaw = true;
        this.isPcm = false;
        return this;
    }

    public AudioBuilder fromUlaw() {
        this.isUlaw = true;
        this.isPcm = false;
        return this;
    }

    public AudioBuilder withSampleRate(int rate) {
        this.sampleRate = rate;
        return this;
    }

    public AudioBuilder withChannels(int channels) {
        this.channels = channels;
        return this;
    }

    public AudioBuilder withBitrate(int bitrate) {
        this.bitrate = bitrate;
        return this;
    }

    public byte[] toOpus() {
        try (Arena arena = Arena.ofConfined()) {
            byte[] pcmData = getPcmData();

            // Output buffer for Ogg/Opus stream
            ByteArrayOutputStream bos = new ByteArrayOutputStream();

            // libopusenc callbacks
            ope_write_func.Function writeFunc = (userData, ptr, len) -> {
                byte[] buffer = new byte[len];
                MemorySegment.copy(ptr.reinterpret(len), ValueLayout.JAVA_BYTE, 0, buffer, 0, len);
                try {
                    bos.write(buffer);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                return 0;
            };

            ope_close_func.Function closeFunc = (userData) -> 0;

            MemorySegment callbacks = OpusEncCallbacks.allocate(arena);
            OpusEncCallbacks.write(callbacks, ope_write_func.allocate(writeFunc, arena));
            OpusEncCallbacks.close(callbacks, ope_close_func.allocate(closeFunc, arena));

            MemorySegment comments = opusenc_h.ope_comments_create();
            MemorySegment errorPtr = arena.allocate(ValueLayout.JAVA_INT);

            MemorySegment encoder = opusenc_h.ope_encoder_create_callbacks(callbacks, MemorySegment.NULL, comments,
                    sampleRate, channels, 0, errorPtr);

            if (encoder.equals(MemorySegment.NULL)) {
                throw new RuntimeException("Failed to create Opus encoder: " + errorPtr.get(ValueLayout.JAVA_INT, 0));
            }

            // Set bitrate
            MemorySegment bitrateSeg = arena.allocate(ValueLayout.JAVA_INT, bitrate);
            opusenc_h.ope_encoder_ctl.makeInvoker(ValueLayout.ADDRESS).apply(encoder, 4002, bitrateSeg); // OPUS_SET_BITRATE

            // Write PCM data
            MemorySegment pcmSeg = arena.allocate(ValueLayout.JAVA_SHORT, pcmData.length / 2);
            for (int i = 0; i < pcmData.length / 2; i++) {
                short s = (short) ((pcmData[i * 2] & 0xFF) | (pcmData[i * 2 + 1] << 8));
                pcmSeg.setAtIndex(ValueLayout.JAVA_SHORT, i, s);
            }

            int ret = opusenc_h.ope_encoder_write(encoder, pcmSeg, pcmData.length / (2 * channels));
            if (ret != 0) {
                opusenc_h.ope_encoder_destroy(encoder);
                throw new RuntimeException("Opus encoding failed: " + ret);
            }

            opusenc_h.ope_encoder_drain(encoder);
            opusenc_h.ope_encoder_destroy(encoder);
            opusenc_h.ope_comments_destroy(comments);

            return bos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Conversion to Opus failed", e);
        }
    }

    public String asBase64() {
        return Base64.getEncoder().encodeToString(toOpus());
    }

    public void asFile(String path) throws IOException {
        Files.write(new File(path).toPath(), toOpus());
    }

    private byte[] getPcmData() throws IOException {
        byte[] rawData = inputData;
        if (rawData == null && inputFile != null) {
            rawData = Files.readAllBytes(inputFile.toPath());
        }

        if (isAlaw)
            return G711Utils.aLawToPcm(rawData);
        if (isUlaw)
            return G711Utils.uLawToPcm(rawData);
        return rawData;
    }
}
