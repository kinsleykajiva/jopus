package io.github.kinsleykajiva.demo;

import io.github.kinsleykajiva.AudioLib;

import java.util.Arrays;
import java.util.Base64;

public class Main {
    public static void main(String[] args) {
        System.out.println("--- Jopus Library Test ---");

        try {
            // Create dummy A-law data (silence-like)
            byte[] alawData = new byte[160 * 600]; // 20ms at 8kHz
            Arrays.fill(alawData, (byte) 0xD5);
            String base64Alaw = Base64.getEncoder().encodeToString(alawData);

            System.out.println("Converting G.711 A-law to Opus...");

            String opusBase64 = AudioLib.convert(base64Alaw)
                    .fromAlaw()
                    .withSampleRate(8000)
                    .asBase64();
            System.out.println("Success! Opus Base64 length: " + opusBase64.length());
            System.out.println("Opus Base64: " + opusBase64);

            // Test file output
            AudioLib.convert(alawData)
                    .fromAlaw()
                    .withSampleRate(8000)
                    .asFile("test_output.opus");
            System.out.println("Saved test_output.opus");

        } catch (Exception e) {
            System.err.println("Test failed!");
            e.printStackTrace();
        }
    }
}
