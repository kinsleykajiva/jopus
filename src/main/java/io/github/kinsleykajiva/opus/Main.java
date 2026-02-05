package io.github.kinsleykajiva.opus;

import java.util.Base64;

public class Main {
    public static void main(String[] args) {
        System.out.println("Starting Opus Codec Test...");
        try {
            // 1. Generate Dummy G.711 A-law Data ( Silence is 0xD5 in A-law
            // roughly/ideally, but 0x55 is 0 linear)
            // A-law silence is often 0x55 or 0xD5.
            byte[] dummyData = new byte[320]; // 2 frames of 160 samples (20ms) -> 40ms
            for (int i = 0; i < dummyData.length; i++) {
                dummyData[i] = (byte) 0xD5;
            }
            String base64Input = Base64.getEncoder().encodeToString(dummyData);
            System.out.println("Input Base64 (G.711): " + base64Input.substring(0, 50) + "...");

            // 2. Convert
            long start = System.nanoTime();
            String opusBase64 = OpusCodec.convertG711ToOpus(base64Input, true);
            long end = System.nanoTime();

            // 3. Output
            System.out.println("Conversion successful!");
            System.out.println("Time taken: " + (end - start) / 1000 + "us");
            System.out.println("Output Base64 (Opus): " + opusBase64);

            if (opusBase64.length() > 0) {
                System.out.println("Test PASSED: Output generated.");
            } else {
                System.out.println("Test FAILED: Empty output.");
            }

        } catch (Throwable t) {
            System.err.println("Test FAILED with exception:");
            t.printStackTrace();
        }
    }
}
