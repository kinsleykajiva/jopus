package io.github.kinsleykajiva.demo;

import io.github.kinsleykajiva.AudioLib;

import java.util.Arrays;
import java.util.Base64;

public class MainPerfomanceTests {
    public static void main(String[] args) {
        System.out.println("--- Jopus Library Test ---");

        try {
            // Create dummy A-law data (silence-like)
            int chunkSize = 160; // 20ms at 8kHz
            byte[] alawChunk = new byte[chunkSize];
            Arrays.fill(alawChunk, (byte) 0xD5);

            // Warm-up and basic test
            System.out.println("1. Basic Functionality Test...");
            String base64Alaw = Base64.getEncoder().encodeToString(alawChunk);
            String opusBase64 = AudioLib.convert(base64Alaw)
                    .fromAlaw()
                    .withSampleRate(8000)
                    .asBase64();
            System.out.println("   Success! Opus Base64 length: " + opusBase64.length());

            // Performance Test
            int iterations = 10000;
            System.out.println("\n--- Performance Benchmark (" + iterations + " chunks) ---");

            // Legacy Method (One-shot)
            long startLegacy = System.nanoTime();
            for (int i = 0; i < iterations; i++) {
                // Determine typical usage pattern: new builder per request
                AudioLib.convert(alawChunk)
                        .fromAlaw()
                        .withSampleRate(8000)
                        .asBase64();
            }
            long endLegacy = System.nanoTime();
            double fpsLegacy = iterations / ((endLegacy - startLegacy) / 1_000_000_000.0);
            System.out.printf("Legacy One-shot API: %.2f chunks/sec%n", fpsLegacy);

            // Optimized Streaming Method
            System.out.println("Initializing Pool...");
            io.github.kinsleykajiva.AudioBuilder.initializePool(4); // simulate 4 concurrent cores

            long startStream = System.nanoTime();
            // Simulate a session
            try (var encoder = io.github.kinsleykajiva.AudioBuilder.stream()) {
                for (int i = 0; i < iterations; i++) {
                    byte[] opusBytes = encoder.encodeAlaw(alawChunk);
                    // Simulate consuming the bytes (e.g. sending to network)
                    if (opusBytes.length == 0)
                        throw new RuntimeException("Empty output");
                }
            }
            long endStream = System.nanoTime();
            double fpsStream = iterations / ((endStream - startStream) / 1_000_000_000.0);
            System.out.printf("Optimized Streaming API: %.2f chunks/sec%n", fpsStream);
            System.out.printf("Speedup Factor: %.2fx%n", fpsStream / fpsLegacy);

        } catch (Exception e) {
            System.err.println("Test failed!");
            e.printStackTrace();
        }
    }
}
