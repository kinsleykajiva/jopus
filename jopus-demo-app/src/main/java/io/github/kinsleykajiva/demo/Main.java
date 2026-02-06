package io.github.kinsleykajiva.demo;

import io.github.kinsleykajiva.AudioLib;

import java.util.Arrays;
import java.util.Base64;

public class Main {
    public static void main(String[] args) {
        System.out.println("--- Jopus Library Benchmark ---");

        try {
            // Setup data: 20ms chunk of silence
            int chunkSize = 160;
            byte[] alawChunk = new byte[chunkSize];
            Arrays.fill(alawChunk, (byte) 0xD5);

            // 1. Correctness Check
            System.out.println("\n[Correctness Check]");
            String checkEncoded = AudioLib.convert(Base64.getEncoder().encodeToString(alawChunk))
                    .fromAlaw().withSampleRate(8000).asBase64();
            System.out.println("Legacy One-Shot Output Length: " + checkEncoded.length());

            io.github.kinsleykajiva.AudioBuilder.initializePool(4);
            try (var encoder = io.github.kinsleykajiva.AudioBuilder.stream()) {
                byte[] streamEncoded = encoder.encodeAlaw(alawChunk);
                System.out.println("Streaming Output Length: " + streamEncoded.length);
            }

            // 2. Performance Check
            int iterations = 5000;
            System.out.println("\n[Performance Benchmark: " + iterations + " iterations]");

            // A) Legacy
            long startLegacy = System.nanoTime();
            for (int i = 0; i < iterations; i++) {
                AudioLib.convert(alawChunk)
                        .fromAlaw()
                        .withSampleRate(8000)
                        .asBase64();
            }
            long endLegacy = System.nanoTime();
            double durLegacy = (endLegacy - startLegacy) / 1_000_000_000.0;
            double fpsLegacy = iterations / durLegacy;
            System.out.printf("Legacy API:   %8.2f chunks/sec (Freq checking natives + File I/O)%n", fpsLegacy);

            // B) Streaming (Optimized)
            long startStream = System.nanoTime();
            try (var encoder = io.github.kinsleykajiva.AudioBuilder.stream()) {
                for (int i = 0; i < iterations; i++) {
                    encoder.encodeAlaw(alawChunk);
                }
            }
            long endStream = System.nanoTime();
            double durStream = (endStream - startStream) / 1_000_000_000.0;
            double fpsStream = iterations / durStream;
            System.out.printf("Streaming API: %8.2f chunks/sec (Pooled + In-memory)%n", fpsStream);

            System.out.printf("Speedup: %.1fx%n", fpsStream / fpsLegacy);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
