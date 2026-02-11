package io.github.kinsleykajiva.demo;

import io.github.kinsleykajiva.opus.opus_h;
import io.github.kinsleykajiva.opus.OpusCodec;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class BenchmarkCritical {

    static final int SAMPLE_RATE = 48000;
    static final int OPUS_APPLICATION_VOIP = 2048;
    static final int ITERATIONS = 15000;
    static final int WARMUP = 5000;

    static class Result {
        String label;
        double frameSizeMs;
        int channels;
        int bitrate;
        double stdAvgUs;
        double critAvgUs;

        double getImprovement() {
            return stdAvgUs / critAvgUs;
        }
    }

    public static void main(String[] args) {
        OpusCodec.loadNativeLibraries();
        System.out.println("Starting Comprehensive Jopus Benchmark...");

        double[] frameSizes = { 2.5, 5, 10, 20, 40, 60 };
        int[] channelCounts = { 1, 2 };
        int[] bitrates = { 16000, 32000, 64000 };

        List<Result> results = new ArrayList<>();

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment errorPtr = arena.allocate(ValueLayout.JAVA_INT);

            for (int channels : channelCounts) {
                for (int bitrate : bitrates) {
                    // Create encoder once per (channels, application)
                    MemorySegment encoder = opus_h.opus_encoder_create(SAMPLE_RATE, channels, OPUS_APPLICATION_VOIP,
                            errorPtr);
                    if (encoder.equals(MemorySegment.NULL))
                        continue;

                    for (double ms : frameSizes) {
                        int frameSize = (int) (SAMPLE_RATE * ms / 1000.0);
                        System.out.printf("Testing: %.1fms, %d channels, %d bps... ", ms, channels, bitrate);

                        Result res = new Result();
                        res.label = String.format("%.1fms-%dch-%dkbps", ms, channels, bitrate / 1000);
                        res.frameSizeMs = ms;
                        res.channels = channels;
                        res.bitrate = bitrate;

                        short[] pcmData = new short[frameSize * channels];
                        byte[] outData = new byte[4000];

                        // Warmup
                        for (int i = 0; i < WARMUP; i++) {
                            runStandard(encoder, pcmData, frameSize, outData, arena);
                            runCritical(encoder, pcmData, frameSize, outData);
                        }

                        // Standard Benchmark
                        long start = System.nanoTime();
                        for (int i = 0; i < ITERATIONS; i++) {
                            runStandard(encoder, pcmData, frameSize, outData, arena);
                        }
                        res.stdAvgUs = (System.nanoTime() - start) / (double) ITERATIONS / 1000.0;

                        // Critical Benchmark
                        start = System.nanoTime();
                        for (int i = 0; i < ITERATIONS; i++) {
                            runCritical(encoder, pcmData, frameSize, outData);
                        }
                        res.critAvgUs = (System.nanoTime() - start) / (double) ITERATIONS / 1000.0;

                        results.add(res);
                        System.out.printf("Std: %.2f us, Crit: %.2f us (%.2fx improvement)%n", res.stdAvgUs,
                                res.critAvgUs, res.getImprovement());
                    }
                    opus_h.opus_encoder_destroy(encoder);
                }
            }

            generateHtml(results);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static int runStandard(MemorySegment encoder, short[] pcm, int frameSize, byte[] out, Arena arena) {
        // In "Standard" approach, users typically have to allocate or use pre-allocated
        // segments.
        // We reuse pcmNative and outNative in a real app, but copy cost remains.
        // For this benchmark, we'll use a local segment to simulate the cost of
        // "copying from heap".
        MemorySegment pcmNative = arena.allocate(ValueLayout.JAVA_SHORT, pcm.length);
        MemorySegment outNative = arena.allocate(ValueLayout.JAVA_BYTE, out.length);

        MemorySegment.copy(pcm, 0, pcmNative, ValueLayout.JAVA_SHORT, 0, pcm.length);
        int len = opus_h.opus_encode(encoder, pcmNative, frameSize, outNative, out.length);
        if (len > 0) {
            MemorySegment.copy(outNative, ValueLayout.JAVA_BYTE, 0, out, 0, len);
        }
        return len;
    }

    static int runCritical(MemorySegment encoder, short[] pcm, int frameSize, byte[] out) {
        return opus_h.opus_encode_critical(encoder, pcm, frameSize, out, out.length);
    }

    static void generateHtml(List<Result> results) {
        StringBuilder labels = new StringBuilder();
        StringBuilder stdData = new StringBuilder();
        StringBuilder critData = new StringBuilder();
        StringBuilder tableRows = new StringBuilder();

        for (Result r : results) {
            labels.append("'").append(r.label).append("',");
            stdData.append(String.format("%.2f", r.stdAvgUs)).append(",");
            critData.append(String.format("%.2f", r.critAvgUs)).append(",");

            tableRows.append(String.format("<tr><td>%s</td><td>%.2f</td><td>%.2f</td><td>%.2fx</td></tr>",
                    r.label, r.stdAvgUs, r.critAvgUs, r.getImprovement()));
        }

        String html = """
                <!DOCTYPE html>
                <html>
                <head>
                    <title>Jopus Advanced Benchmark Results</title>
                    <script src="https://cdn.jsdelivr.net/npm/chart.js"></script>
                    <style>
                        body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; padding: 40px; background: #f8f9fa; color: #333; }
                        .card { background: white; border-radius: 8px; box-shadow: 0 4px 6px rgba(0,0,0,0.1); padding: 20px; margin-bottom: 30px; }
                        .chart-container { position: relative; height: 500px; width: 100%%; }
                        h1 { text-align: center; color: #2c3e50; }
                        table { width: 100%%; border-collapse: collapse; margin-top: 20px; }
                        th, td { padding: 12px; border: 1px solid #ddd; text-align: right; }
                        th { background-color: #f2f2f2; }
                        .best { color: #27ae60; font-weight: bold; }
                        .header-row { text-align: left; }
                    </style>
                </head>
                <body>
                    <h1>Jopus: Critical Linker Optimization Analysis</h1>

                    <div class="card">
                        <div class="chart-container">
                            <canvas id="perfChart"></canvas>
                        </div>
                    </div>

                    <div class="card">
                        <h2>Detailed Data (Average Time in Microseconds)</h2>
                        <table>
                            <thead>
                                <tr>
                                    <th class="header-row">Configuration</th>
                                    <th>Standard (Copy)</th>
                                    <th>Critical (Zero-Copy)</th>
                                    <th>Improvement</th>
                                </tr>
                            </thead>
                            <tbody>
                                %s
                            </tbody>
                        </table>
                    </div>

                    <script>
                        const ctx = document.getElementById('perfChart').getContext('2d');
                        new Chart(ctx, {
                            type: 'line',
                            data: {
                                labels: [%s],
                                datasets: [{
                                    label: 'Standard (µs)',
                                    data: [%s],
                                    borderColor: 'rgba(231, 76, 60, 1)',
                                    backgroundColor: 'rgba(231, 76, 60, 0.1)',
                                    fill: true,
                                    tension: 0.1
                                }, {
                                    label: 'Critical (µs)',
                                    data: [%s],
                                    borderColor: 'rgba(46, 204, 113, 1)',
                                    backgroundColor: 'rgba(46, 204, 113, 0.1)',
                                    fill: true,
                                    tension: 0.1
                                }]
                            },
                            options: {
                                responsive: true,
                                maintainAspectRatio: false,
                                plugins: {
                                    title: { display: true, text: 'Latency Comparison (Lower is Better)' },
                                    tooltip: { mode: 'index', intersect: false }
                                },
                                scales: {
                                    y: {
                                        beginAtZero: false,
                                        title: { display: true, text: 'Microseconds (µs)' }
                                    }
                                }
                            }
                        });
                    </script>
                </body>
                </html>
                """
                .formatted(tableRows.toString(), labels.toString(), stdData.toString(), critData.toString());

        try (FileWriter fw = new FileWriter("benchmark_chart.html")) {
            fw.write(html);
            System.out.println("\\nGenerated benchmark_chart.html");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
