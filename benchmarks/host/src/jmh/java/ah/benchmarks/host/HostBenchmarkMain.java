package ah.benchmarks.host;

import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;

public final class HostBenchmarkMain {
    private static final long MIB = 1024L * 1024L;
    private static final List<String> FIXTURES = List.of("java-single-dex", "kotlin-multidex", "jni-four-abi");

    private HostBenchmarkMain() {}

    public static void main(String[] args) throws Exception {
        Path work = Path.of(required("m305.work")).toAbsolutePath().normalize();
        deleteTree(work);
        Files.createDirectories(work);
        boolean quick = Boolean.getBoolean("m305.quick");
        int warmups = quick ? 1 : 3;
        int measurements = quick ? 2 : 10;
        new Runner(new OptionsBuilder()
            .include(ProtectionBenchmark.class.getSimpleName())
            .mode(Mode.SingleShotTime)
            .warmupIterations(warmups)
            .measurementIterations(measurements)
            .warmupBatchSize(1)
            .measurementBatchSize(1)
            .forks(1)
            .shouldFailOnError(true)
            .build()).run();

        String environment = required("m305.environment");
        ArrayList<Map<String, Object>> results = new ArrayList<>();
        ArrayList<Object> artifactSizes = new ArrayList<>();
        Map<String, Object> stress = null;
        for (String id : List.of("java-single-dex", "kotlin-multidex", "jni-four-abi", "synthetic-100mib")) {
            Path trial = work.resolve("trials").resolve(id);
            List<String> rows = Files.readAllLines(trial.resolve("samples.csv"), StandardCharsets.UTF_8);
            ArrayList<Long> times = new ArrayList<>();
            ArrayList<Long> rss = new ArrayList<>();
            for (String row : rows) {
                String[] fields = row.split(",");
                times.add(Long.parseLong(fields[0]));
                rss.add(Long.parseLong(fields[1]));
            }
            var time = BenchmarkStatistics.summarize(times, measurements);
            var memory = BenchmarkStatistics.summarize(rss, measurements);
            long timeBudget = 60_000L;
            long rssBudget = 1024L * MIB;
            results.add(result(id, environment, "hostProcessMs", time, timeBudget));
            results.add(result(id, environment, "hostPeakRssBytes", memory, rssBudget));
            Map<String, Object> sizes = cast(parseSimpleJson(Files.readString(trial.resolve("artifact-sizes.json"), StandardCharsets.UTF_8)));
            if (id.equals("synthetic-100mib")) {
                stress = linked("inputSha256Before", Files.readString(trial.resolve("input.sha256")).trim(),
                    "inputSha256After", sha256(trial.resolve("input-signed.apk")), "medianMs", time.p50(),
                    "peakRssBytes", memory.p95(), "timeBudgetMs", timeBudget, "rssBudgetBytes", rssBudget,
                    "pass", time.p50() <= timeBudget && memory.p95() <= rssBudget);
            } else {
                artifactSizes.add(sizes);
            }
        }
        boolean allPass = results.stream().allMatch(row -> Boolean.TRUE.equals(row.get("pass")))
            && artifactSizes.stream().map(HostBenchmarkMain::cast).allMatch(row -> Boolean.TRUE.equals(row.get("pass")))
            && Boolean.TRUE.equals(stress.get("pass"));
        Map<String, Object> report = linked("schemaVersion", 1, "environmentId", environment,
            "results", results, "artifactSizes", artifactSizes, "stress100MiB", stress,
            "allBudgetsPass", allPass, "sizeClaim", "overhead-controlled-output-not-guaranteed-smaller");
        Path reportPath = work.resolve("benchmark-results.json");
        Files.writeString(reportPath, json(report) + "\n", StandardCharsets.UTF_8);
        Path summary = work.resolve("benchmark-summary.md");
        Files.writeString(summary, markdown(report, results), StandardCharsets.UTF_8);
        Files.writeString(work.resolve("sha256-manifest.txt"), sha256(reportPath) + "  benchmark-results.json\n" +
            sha256(summary) + "  benchmark-summary.md\n", StandardCharsets.UTF_8);
        if (!allPass) throw new IllegalStateException("M3-05 Host budget failed");
        System.out.println("M3-05 Host benchmark PASS: " + reportPath);
    }

    static Map<String, Object> artifactSizes(Path root, String fixtureId, Path input, Path output, Path signedOutput) throws Exception {
        long bootstrap = zipEntrySize(output, "classes.dex");
        long runtimes = zipEntriesSize(output, "lib/", "/libah_runtime.so");
        byte[] container = zipEntryBytes(output, "assets/ah/runtime/payload.ahdc");
        long encryptedPayload = container.length >= 40
            ? ByteBuffer.wrap(container, 32, 8).order(ByteOrder.LITTLE_ENDIAN).getLong() : 0;
        if (encryptedPayload < 0 || encryptedPayload > container.length) throw new IllegalStateException("invalid AHDC payload size");
        long containerMetadata = container.length - encryptedPayload;
        Path bundle = root.resolve("integration-tests/build/generated/m3-01/runtime-bundle/ah/runtime");
        long fourAbi = 0;
        for (String abi : List.of("armeabi-v7a", "arm64-v8a", "x86", "x86_64")) fourAbi += Files.size(bundle.resolve(abi).resolve("libah_runtime.so"));
        long inputBytes = Files.size(input);
        long outputBytes = Files.size(output);
        long zipDelta = outputBytes - inputBytes - bootstrap - runtimes - containerMetadata - encryptedPayload;
        long budget = Math.max(12L * MIB, Math.round(inputBytes * 0.15d));
        long delta = outputBytes - inputBytes;
        Map<String, Object> breakdown = linked("bootstrapDexBytes", bootstrap, "selectedRuntimeAbiBytes", runtimes,
            "fourAbiRuntimeBaselineBytes", fourAbi, "containerMetadataBytes", containerMetadata,
            "encryptedPayloadBytes", encryptedPayload, "zipStructureDeltaBytes", zipDelta);
        if (inputBytes + bootstrap + runtimes + containerMetadata + encryptedPayload + zipDelta != outputBytes) {
            throw new IllegalStateException("size breakdown does not reconcile");
        }
        return linked("fixtureId", fixtureId, "inputSignedApkBytes", inputBytes,
            "outputUnsignedApkBytes", outputBytes, "outputExternallySignedApkBytes", Files.size(signedOutput),
            "unsignedDeltaBytes", delta, "unsignedDeltaPercent", inputBytes == 0 ? 0 : delta * 100.0d / inputBytes,
            "unsignedDeltaBudgetBytes", budget, "pass", delta <= budget, "sizeBreakdown", breakdown,
            "inputSha256", sha256(input), "outputUnsignedSha256", sha256(output),
            "outputExternallySignedSha256", sha256(signedOutput));
    }

    private static Map<String, Object> result(String fixture, String environment, String metric,
                                               BenchmarkStatistics.Summary summary, long budget) {
        return linked("fixtureId", fixture, "environmentId", environment,
            "measurementMode", null, "observedRiskLevel", null, "observedRiskAction", null,
            "riskObservationTiming", null, "metric", metric,
            "samples", summary.samples(), "p50", summary.p50(), "p95", summary.p95(),
            "baseline", 0, "delta", summary.p50(), "budget", budget,
            "pass", summary.p50() <= budget && summary.p95() <= budget,
            "claimType", null, "freshProcess", null, "sameHandle", null,
            "lookupCountBeforeUpgrade", null, "lookupCountAfterUpgrade", null,
            "cleanupPassed", null, "nativeJitterMs", null);
    }

    private static String markdown(Map<String, Object> report, List<Map<String, Object>> results) {
        StringBuilder text = new StringBuilder("# M3-05 benchmark summary\n\n")
            .append("Environment: `").append(report.get("environmentId")).append("`\n\n")
            .append("APK size budgets control added overhead; protected output is not guaranteed to be smaller than input.\n\n")
            .append("| Fixture | Metric | P50 | P95 | Budget | Pass |\n|---|---:|---:|---:|---:|---|\n");
        for (Map<String, Object> row : results) text.append("| ").append(row.get("fixtureId")).append(" | ")
            .append(row.get("metric")).append(" | ").append(row.get("p50")).append(" | ")
            .append(row.get("p95")).append(" | ").append(row.get("budget")).append(" | ")
            .append(row.get("pass")).append(" |\n");
        return text.toString();
    }

    private static long zipEntrySize(Path zip, String name) throws IOException {
        try (ZipFile file = new ZipFile(zip.toFile())) { var entry = file.getEntry(name); if (entry == null) throw new IOException("missing " + name); return entry.getSize(); }
    }
    private static long zipEntriesSize(Path zip, String prefix, String suffix) throws IOException {
        try (ZipFile file = new ZipFile(zip.toFile())) { return file.stream().filter(e -> e.getName().startsWith(prefix) && e.getName().endsWith(suffix)).mapToLong(java.util.zip.ZipEntry::getSize).sum(); }
    }
    private static byte[] zipEntryBytes(Path zip, String name) throws IOException {
        try (ZipFile file = new ZipFile(zip.toFile())) { var entry = file.getEntry(name); if (entry == null) throw new IOException("missing " + name); try (var input = file.getInputStream(entry)) { return input.readAllBytes(); } }
    }

    static String json(Object value) {
        if (value == null) return "null";
        if (value instanceof String text) return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof Map<?, ?> map) {
            StringBuilder out = new StringBuilder("{"); boolean first = true;
            for (var entry : map.entrySet()) { if (!first) out.append(','); first = false; out.append(json(entry.getKey().toString())).append(':').append(json(entry.getValue())); }
            return out.append('}').toString();
        }
        if (value instanceof Iterable<?> values) {
            StringBuilder out = new StringBuilder("["); boolean first = true;
            for (Object item : values) { if (!first) out.append(','); first = false; out.append(json(item)); }
            return out.append(']').toString();
        }
        throw new IllegalArgumentException("unsupported JSON value " + value.getClass());
    }

    // Reads only the flat/nested JSON emitted by this package, never untrusted product input.
    private static Object parseSimpleJson(String text) { return new Parser(text).parse(); }
    @SuppressWarnings("unchecked") private static Map<String, Object> cast(Object value) { return (Map<String, Object>) value; }
    static Map<String, Object> linked(Object... values) { LinkedHashMap<String, Object> out = new LinkedHashMap<>(); for (int i = 0; i < values.length; i += 2) out.put((String) values[i], values[i + 1]); return out; }
    private static String required(String name) { return java.util.Objects.requireNonNull(System.getProperty(name), name); }
    private static String sha256(Path path) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))); }
    private static void deleteTree(Path path) throws IOException { if (!Files.exists(path)) return; try (var stream = Files.walk(path)) { stream.sorted(Comparator.reverseOrder()).forEach(item -> { try { Files.delete(item); } catch (IOException failure) { throw new java.io.UncheckedIOException(failure); } }); } }

    private static final class Parser {
        private final String text; private int at;
        Parser(String text) { this.text = text; }
        Object parse() { skip(); Object value = value(); skip(); if (at != text.length()) throw new IllegalArgumentException("trailing JSON"); return value; }
        private Object value() { skip(); char c = text.charAt(at); if (c == '{') return object(); if (c == '[') return array(); if (c == '"') return string(); if (text.startsWith("true", at)) { at += 4; return true; } if (text.startsWith("false", at)) { at += 5; return false; } if (text.startsWith("null", at)) { at += 4; return null; } return number(); }
        private Map<String, Object> object() { at++; LinkedHashMap<String, Object> out = new LinkedHashMap<>(); skip(); if (take('}')) return out; do { String key = string(); skip(); expect(':'); out.put(key, value()); skip(); } while (take(',')); expect('}'); return out; }
        private List<Object> array() { at++; ArrayList<Object> out = new ArrayList<>(); skip(); if (take(']')) return out; do { out.add(value()); skip(); } while (take(',')); expect(']'); return out; }
        private String string() { expect('"'); StringBuilder out = new StringBuilder(); while (true) { char c = text.charAt(at++); if (c == '"') return out.toString(); if (c == '\\') { char e = text.charAt(at++); out.append(e == 'n' ? '\n' : e); } else out.append(c); } }
        private Number number() {
            int start = at;
            while (at < text.length() && "-+.eE0123456789".indexOf(text.charAt(at)) >= 0) at++;
            String token = text.substring(start, at);
            if (token.contains(".") || token.contains("e") || token.contains("E")) return Double.parseDouble(token);
            return Long.parseLong(token);
        }
        private boolean take(char c) { skip(); if (at < text.length() && text.charAt(at) == c) { at++; return true; } return false; }
        private void expect(char c) { skip(); if (at >= text.length() || text.charAt(at++) != c) throw new IllegalArgumentException("invalid JSON"); }
        private void skip() { while (at < text.length() && Character.isWhitespace(text.charAt(at))) at++; }
    }
}
