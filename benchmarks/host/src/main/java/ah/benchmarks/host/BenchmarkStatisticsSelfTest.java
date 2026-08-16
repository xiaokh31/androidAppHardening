package ah.benchmarks.host;

import java.util.List;

public final class BenchmarkStatisticsSelfTest {
    private BenchmarkStatisticsSelfTest() {}

    public static void main(String[] args) {
        var summary = BenchmarkStatistics.summarize(List.of(10L, 1L, 9L, 2L, 8L, 3L, 7L, 4L, 6L, 5L), 10);
        check(summary.p50() == 5L && summary.p95() == 10L, "percentiles");
        check(BenchmarkStatistics.withinRepeatability(100, 110), "ten percent boundary");
        check(!BenchmarkStatistics.withinRepeatability(100, 111), "ten percent overflow");
        expectFailure(() -> BenchmarkStatistics.summarize(List.of(1L), 2));
        expectFailure(() -> BenchmarkStatistics.summarize(List.of(-1L), 1));
        expectFailure(() -> BenchmarkStatistics.percentile(List.of(), 0.5));
        System.out.println("M3-05 statistics self-test PASS");
    }

    private static void expectFailure(Runnable action) {
        try {
            action.run();
            throw new AssertionError("expected failure");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
    }
}
