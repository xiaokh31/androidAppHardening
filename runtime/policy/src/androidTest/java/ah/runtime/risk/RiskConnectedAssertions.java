package ah.runtime.risk;

import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.os.SystemClock;

/** Device-only assertions invoked by the fixed policy instrumentation runner. */
public final class RiskConnectedAssertions {
    private RiskConnectedAssertions() {}

    public static String run(ApplicationInfo applicationInfo) {
        long maxNanos = 0;
        RiskReportV1 last = null;
        for (int index = 0; index < 1000; index++) {
            long started = SystemClock.elapsedRealtimeNanos();
            last = EnvironmentRiskEngine.evaluate(applicationInfo);
            long elapsed = SystemClock.elapsedRealtimeNanos() - started;
            if (elapsed > maxNanos) maxNanos = elapsed;
            require(elapsed <= 50_000_000L, "risk-budget");
        }
        require(last != null && last.version() == 1, "risk-version");
        require(last.signals().size() == 5, "risk-signals");
        require(last.action() != null && last.level() != null, "risk-decision");
        require(last.action() != RiskAction.ALLOW || last.level() == RiskLevel.LOW,
                "allow-low-only");
        require(last.action() != RiskAction.DEGRADE || last.level() != RiskLevel.LOW,
                "degrade-nonlow-only");
        require(last.signals().stream().noneMatch(signal -> signal.id().name().contains("ABI")),
                "abi-not-signal");

        RiskReportV1 injectionAndDebugger = RiskPolicyV1.evaluate(new RiskSnapshot(
                SignalState.CLEAR,
                SignalState.DETECTED,
                SignalState.CLEAR,
                SignalState.DETECTED,
                1,
                SignalState.CLEAR,
                0));
        require(injectionAndDebugger.level() == RiskLevel.HIGH
                        && injectionAndDebugger.action() == RiskAction.DEGRADE,
                "injection-debugger-high");
        RiskReportV1 debuggableOnly = RiskPolicyV1.evaluate(new RiskSnapshot(
                SignalState.CLEAR,
                SignalState.CLEAR,
                SignalState.DETECTED,
                SignalState.CLEAR,
                0,
                SignalState.CLEAR,
                0));
        require(debuggableOnly.level() == RiskLevel.LOW
                        && debuggableOnly.action() == RiskAction.ALLOW,
                "debuggable-low");

        String summary = "risk_connected=true version=1 api=" + Build.VERSION.SDK_INT
                + " total=" + last.totalScore()
                + " level=" + last.level().name()
                + " action=" + last.action().name()
                + " max_us=" + (maxNanos / 1000L)
                + " signals=" + last.signals().size();
        require(!summary.contains("/proc") && !summary.contains("/data/")
                        && !summary.contains("maps"),
                "summary-redacted");
        return summary;
    }

    private static void require(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
}
