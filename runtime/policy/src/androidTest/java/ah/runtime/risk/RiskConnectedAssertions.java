package ah.runtime.risk;

import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.os.SystemClock;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Device-only assertions invoked by the fixed policy instrumentation runner. */
public final class RiskConnectedAssertions {
    private RiskConnectedAssertions() {}

    public static String run(android.content.Context context) throws Exception {
        return run(context, false);
    }

    public static String run(android.content.Context context, boolean expectDebugger)
            throws Exception {
        ApplicationInfo applicationInfo = context.getApplicationInfo();
        long maxNanos = 0;
        RiskReportV1 last = null;
        int evaluationCount = expectDebugger ? 1 : 1000;
        for (int index = 0; index < evaluationCount; index++) {
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
        if (expectDebugger) {
            RiskSignal jdwp = last.signals().stream()
                    .filter(signal -> signal.id() == RiskSignalId.JDWP)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("jdwp-signal-missing"));
            require(jdwp.state() == SignalState.DETECTED && jdwp.score() == 50,
                    "real-jdwp-detected");
        }

        AtomicLong timeoutClock = new AtomicLong(1L);
        RiskReportV1 timedOut = EnvironmentRiskEngine.evaluateWithDependencies(
                applicationInfo,
                () -> {
                    timeoutClock.set(50_000_002L);
                    return new int[] {1, 2, 2, 3};
                },
                timeoutClock::get);
        require(timedOut.totalScore() == 0, "timeout-score-zero");
        require(timedOut.signals().stream().allMatch(
                        signal -> signal.state() == SignalState.UNAVAILABLE && signal.score() == 0),
                "timeout-all-unavailable");

        RiskReportV1 mapped = evaluateMappedFixture(context, applicationInfo);
        RiskSignal mapping = mapped.signals().stream()
                .filter(signal -> signal.id() == RiskSignalId.INSTRUMENTATION_MAPPING)
                .findFirst()
                .orElseThrow(() -> new AssertionError("mapping-signal-missing"));
        require(mapping.state() == SignalState.DETECTED && mapping.score() == 80,
                "real-mapping-families");

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
                + " signals=" + last.signals().size()
                + " native_mapping_score=" + mapping.score()
                + " timeout_unavailable=true"
                + " jdwp=" + (expectDebugger ? "detected" : "not_required");
        require(!summary.contains("/proc") && !summary.contains("/data/")
                        && !summary.contains("maps"),
                "summary-redacted");
        return summary;
    }

    private static RiskReportV1 evaluateMappedFixture(
            android.content.Context context, ApplicationInfo applicationInfo) throws Exception {
        File directory = new File(context.getCodeCacheDir(), "m205-risk");
        require(directory.isDirectory() || directory.mkdirs(), "mapping-dir");
        File frida = new File(directory, "libfrida-agent-fixture.so");
        File xposed = new File(directory, "libxposed-fixture.so");
        extractRuntimeLibrary(applicationInfo.sourceDir, frida);
        extractRuntimeLibrary(applicationInfo.sourceDir, xposed);
        try (RandomAccessFile first = new RandomAccessFile(frida, "r");
                RandomAccessFile second = new RandomAccessFile(xposed, "r");
                FileChannel firstChannel = first.getChannel();
                FileChannel secondChannel = second.getChannel()) {
            MappedByteBuffer firstMapping = firstChannel.map(
                    FileChannel.MapMode.READ_ONLY, 0, firstChannel.size());
            MappedByteBuffer secondMapping = secondChannel.map(
                    FileChannel.MapMode.READ_ONLY, 0, secondChannel.size());
            require(firstMapping.get(0) == 0x7f && secondMapping.get(0) == 0x7f,
                    "mapped-elf");
            return EnvironmentRiskEngine.evaluate(applicationInfo);
        } finally {
            require(frida.delete() && xposed.delete(), "mapping-cleanup");
            require(directory.delete(), "mapping-dir-cleanup");
        }
    }

    private static void extractRuntimeLibrary(String apkPath, File output) throws Exception {
        try (ZipFile apk = new ZipFile(apkPath)) {
            ZipEntry selected = null;
            for (String abi : Build.SUPPORTED_ABIS) {
                ZipEntry candidate = apk.getEntry("lib/" + abi + "/libah_runtime.so");
                if (candidate != null) {
                    selected = candidate;
                    break;
                }
            }
            require(selected != null && selected.getSize() > 0, "runtime-library-entry");
            try (InputStream input = apk.getInputStream(selected);
                    FileOutputStream target = new FileOutputStream(output, false)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) target.write(buffer, 0, count);
            }
        }
    }

    private static void require(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
}
