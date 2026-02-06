package io.github.kinsleykajiva.demo;

import io.github.kinsleykajiva.AudioLib;
import java.util.Arrays;
import java.util.Base64;

public class MainG711Conversion {
    public static void main(String[] args) {
        System.out.println("--- Jopus G.711 <-> Opus Conversion Test ---");

        try {
            // 1. Setup silence chunk (0xD5 is silence in A-law)
            int chunkSize = 160;
            byte[] originalAlaw = new byte[chunkSize];
            Arrays.fill(originalAlaw, (byte) 0xD5);
            String base64Alaw = Base64.getEncoder().encodeToString(originalAlaw);

            System.out.println("Original A-law chunk (Base64): " + base64Alaw.substring(0, 10) + "...");

            // 2. Convert A-law -> Opus (Raw Packet)
            System.out.println("\n[Test 1] A-law -> Opus (Raw Packet)");
            io.github.kinsleykajiva.AudioBuilder.initializePool(1);
            byte[] opusPacket;
            try (var encoder = io.github.kinsleykajiva.AudioBuilder.stream()) {
                opusPacket = encoder.encodeAlaw(originalAlaw);
            }
            String opusBase64 = Base64.getEncoder().encodeToString(opusPacket);
            System.out.println("Opus Output (Base64): "
                    + (opusBase64.length() > 20 ? opusBase64.substring(0, 20) : opusBase64) + "...");

            // 3. Convert Opus -> G.711 (Round-trip)
            System.out.println("\n[Test 2] Opus -> A-law (Round-trip)");
            String roundTripAlaw = AudioLib.convert(opusBase64)
                    .fromOpus()
                    .asAlawBase64();
            System.out.println("Round-trip A-law (Base64): "
                    + (roundTripAlaw.length() > 10 ? roundTripAlaw.substring(0, 10) : roundTripAlaw) + "...");

            if (roundTripAlaw.length() > 0) {
                System.out.println("SUCCESS: Round-trip G.711 A-law conversion completed.");
            }

            // 4. Convert Opus -> U-law
            System.out.println("\n[Test 3] Opus -> U-law");
            String ulawBase64 = AudioLib.convert(opusBase64)
                    .fromOpus()
                    .asUlawBase64();
            System.out.println("U-law Output (Base64): "
                    + (ulawBase64.length() > 10 ? ulawBase64.substring(0, 10) : ulawBase64) + "...");

            if (ulawBase64.length() > 0) {
                System.out.println("SUCCESS: Opus to U-law conversion completed.");
            }

        } catch (Exception e) {
            System.err.println("FAILURE: An error occurred during conversion.");
            e.printStackTrace();
        }
    }
}
