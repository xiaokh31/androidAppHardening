package ah.runtime.risk;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Dependency-free M2-05 policy matrix executed by :runtime:policy:test. */
public final class EnvironmentRiskEngineSelfTest {
    private int cases;

    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 1) throw new AssertionError("report directory required");
        EnvironmentRiskEngineSelfTest self = new EnvironmentRiskEngineSelfTest();
        self.run(Path.of(arguments[0]));
        System.out.println("M2-05 risk policy matrix PASS cases=" + self.cases);
    }

    private void run(Path reportDirectory) throws Exception {
        verify(0, RiskLevel.LOW, RiskAction.ALLOW, clear());
        verify(20, RiskLevel.LOW, RiskAction.ALLOW,
                snapshot(0, 0, 1, 0, 0));
        verify(30, RiskLevel.LOW, RiskAction.ALLOW,
                snapshot(0, 0, 0, 0, 7));
        verify(40, RiskLevel.MEDIUM, RiskAction.DEGRADE,
                snapshot(0, 0, 0, 1, 0));
        verify(50, RiskLevel.MEDIUM, RiskAction.DEGRADE,
                snapshot(0, 1, 0, 0, 0));
        verify(60, RiskLevel.MEDIUM, RiskAction.DEGRADE,
                snapshot(1, 0, 0, 0, 0));
        verify(70, RiskLevel.MEDIUM, RiskAction.DEGRADE,
                snapshot(0, 1, 1, 0, 0));
        verify(80, RiskLevel.HIGH, RiskAction.DEGRADE,
                snapshot(1, 0, 1, 0, 0));
        verify(80, RiskLevel.HIGH, RiskAction.DEGRADE,
                snapshot(0, 0, 0, 3, 0));
        verify(90, RiskLevel.HIGH, RiskAction.DEGRADE,
                snapshot(0, 1, 0, 1, 0));
        verify(100, RiskLevel.HIGH, RiskAction.DEGRADE,
                snapshot(1, 1, 1, 3, 7));

        RiskReportV1 unavailable = RiskPolicyV1.evaluate(new RiskSnapshot(
                SignalState.UNAVAILABLE,
                SignalState.UNAVAILABLE,
                SignalState.UNAVAILABLE,
                SignalState.UNAVAILABLE,
                3,
                SignalState.UNAVAILABLE,
                7));
        check(unavailable.totalScore() == 0, "unavailable-zero");
        check(unavailable.signals().stream().allMatch(signal -> signal.score() == 0),
                "unavailable-scores");
        RiskReportV1 duplicateFamilies = RiskPolicyV1.evaluate(snapshot(0, 0, 0, 1, 0));
        check(duplicateFamilies.totalScore() == 40, "family-dedup");
        RiskReportV1 emulatorCap = RiskPolicyV1.evaluate(snapshot(0, 0, 0, 0, 7));
        check(emulatorCap.totalScore() == 30, "emulator-cap");
        check(RiskSignalId.values().length == 5, "signal-count");
        check(unavailable.version() == 1, "version");
        check(unavailable.signals().stream().noneMatch(signal -> signal.id().name().contains("ABI")),
                "abi-zero-contribution");
        expectUnsupported(unavailable.signals());
        try {
            RiskPolicyV1.evaluate(null);
            throw new AssertionError("null accepted");
        } catch (IllegalArgumentException expected) {
            check("AAH-RUNTIME-RISK-ARGUMENT".equals(expected.getMessage()), "stable-error");
        }

        Files.createDirectories(reportDirectory);
        RiskReportV1 sampleReport = RiskPolicyV1.evaluate(snapshot(0, 0, 1, 0, 0));
        StringBuilder sampleBuilder = new StringBuilder("{\n  \"version\": 1,\n  \"signals\": [");
        for (int index = 0; index < sampleReport.signals().size(); index++) {
            RiskSignal signal = sampleReport.signals().get(index);
            if (index > 0) sampleBuilder.append(',');
            sampleBuilder.append("{\"id\":\"").append(signal.id().name())
                    .append("\",\"state\":\"").append(signal.state().name())
                    .append("\",\"hit\":").append(signal.hit())
                    .append(",\"score\":").append(signal.score()).append('}');
        }
        String sample = sampleBuilder.append("],\n  \"total_score\": ")
                .append(sampleReport.totalScore())
                .append(",\n  \"level\": \"").append(sampleReport.level().name())
                .append("\",\n  \"action\": \"").append(sampleReport.action().name())
                .append("\"\n}\n").toString();
        check(!sample.contains("/proc") && !sample.contains("/data/") && !sample.contains("\\\\"),
                "sample-redacted");
        Files.write(reportDirectory.resolve("risk-report-v1.json"),
                sample.getBytes(StandardCharsets.UTF_8));
    }

    private void verify(int score, RiskLevel level, RiskAction action, RiskSnapshot snapshot) {
        RiskReportV1 report = RiskPolicyV1.evaluate(snapshot);
        check(report.totalScore() == score, "score-" + score);
        check(report.level() == level, "level-" + score);
        check(report.action() == action, "action-" + score);
        check(report.signals().size() == 5, "signals-" + score);
    }

    private static RiskSnapshot clear() {
        return snapshot(0, 0, 0, 0, 0);
    }

    private static RiskSnapshot snapshot(int tracer, int jdwp, int debuggable,
                                         int mappingMask, int emulatorMask) {
        return new RiskSnapshot(
                state(tracer), state(jdwp), state(debuggable),
                mappingMask == 0 ? SignalState.CLEAR : SignalState.DETECTED,
                mappingMask,
                emulatorMask == 0 ? SignalState.CLEAR : SignalState.DETECTED,
                emulatorMask);
    }

    private static SignalState state(int detected) {
        return detected == 0 ? SignalState.CLEAR : SignalState.DETECTED;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void expectUnsupported(List value) {
        try {
            value.add(null);
            throw new AssertionError("mutable report");
        } catch (UnsupportedOperationException expected) {
            check(true, "immutable-report");
        }
    }

    private void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        cases++;
    }
}
