package ah.benchmarks.host;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class BenchmarkStatistics {
    private BenchmarkStatistics() {}

    public static Summary summarize(List<Long> rawSamples, int expectedCount) {
        if (rawSamples == null || rawSamples.size() != expectedCount || expectedCount < 1) {
            throw new IllegalArgumentException("wrong benchmark sample count");
        }
        ArrayList<Long> samples = new ArrayList<>(rawSamples.size());
        for (Long sample : rawSamples) {
            if (sample == null || sample < 0) throw new IllegalArgumentException("invalid benchmark sample");
            samples.add(sample);
        }
        Collections.sort(samples);
        return new Summary(List.copyOf(rawSamples), percentile(samples, 0.50), percentile(samples, 0.95));
    }

    public static long percentile(List<Long> sortedSamples, double quantile) {
        if (sortedSamples.isEmpty() || !(quantile > 0.0 && quantile <= 1.0)) {
            throw new IllegalArgumentException("invalid percentile input");
        }
        int index = Math.max(0, (int) Math.ceil(quantile * sortedSamples.size()) - 1);
        return sortedSamples.get(index);
    }

    public static boolean withinRepeatability(long first, long second) {
        if (first < 0 || second < 0) return false;
        long denominator = Math.max(1L, Math.min(first, second));
        return Math.abs(first - second) <= denominator / 10L;
    }

    public record Summary(List<Long> samples, long p50, long p95) {}
}
