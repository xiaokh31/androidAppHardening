package ah.tools.validation.tamper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TamperCatalogRunner {
    private static final List<String> FIELDS = List.of(
            "id", "target", "mutation", "expectedStage", "expectedCode", "payloadLoaded",
            "payloadClassLookupAttempted", "nativeHandleAcquired", "loadedPayloadPublished",
            "verifiedPayloadSessionPublished", "byteBuffersPublished", "nativeCloseCount",
            "partialJavaReferencesCleared", "partialGuardReferencesCleared",
            "completedMappingsZeroizedUnmapped", "partialMappingZeroizedUnmapped",
            "primaryCodePreserved", "cleanupFailureSuppressed");
    private static final Set<String> HOST_TARGETS = Set.of("apk", "axml", "container-host");
    private static final Set<String> RUNTIME_TARGETS = Set.of("runtime-prehandle", "runtime-posthandle", "guard");
    private static final Map<String, String> APK_FIXTURES = Map.of(
            "truncate_tail", "central-local-length-conflict.apk",
            "duplicate_entry", "duplicate-entry.apk",
            "path_traversal", "path-traversal.apk",
            "inflate_size_limit", "compression-bomb.apk");
    private static final Map<String, String> AXML_FIXTURES = Map.of(
            "chunk_size_overflow", "oversized-root",
            "string_length_overflow", "string-length-truncated",
            "resource_map_collision", "resource-id-collision",
            "nesting_overflow", "nesting-limit");

    private TamperCatalogRunner() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 8) {
            throw new IllegalArgumentException(
                    "expected catalog, work, tamper report, regression report, APK report, AXML report, container report, summary");
        }
        Path catalog = Path.of(args[0]).toAbsolutePath().normalize();
        Path work = Path.of(args[1]).toAbsolutePath().normalize();
        Path tamperReport = Path.of(args[2]).toAbsolutePath().normalize();
        Path regressionReport = Path.of(args[3]).toAbsolutePath().normalize();
        Path apkReport = Path.of(args[4]).toAbsolutePath().normalize();
        Path axmlReport = Path.of(args[5]).toAbsolutePath().normalize();
        Path containerReport = Path.of(args[6]).toAbsolutePath().normalize();
        Path report = Path.of(args[7]).toAbsolutePath().normalize();
        Files.createDirectories(work);
        byte[] catalogBytes = Files.readAllBytes(catalog);
        List<Map<String, String>> cases = parse(catalogBytes);
        validate(cases);

        String apkText = Files.readString(apkReport, StandardCharsets.UTF_8);
        String axmlText = Files.readString(axmlReport, StandardCharsets.UTF_8);
        String containerText = Files.readString(containerReport, StandardCharsets.UTF_8);
        List<ObservedHostCase> observed = new ArrayList<>();
        for (Map<String, String> current : cases) {
            if (!HOST_TARGETS.contains(current.get("target"))) continue;
            observed.add(observeHostCase(current, apkText, axmlText, containerText));
        }
        if (observed.size() != 12) throw new AssertionError("expected 12 exact Host catalog cases");
        String canonical = observed.stream().map(ObservedHostCase::canonical).reduce("", (left, right) -> left + right + "\n");
        writeTamperReport(
                tamperReport,
                cases.size(),
                observed,
                hex(sha256(catalogBytes)),
                hex(sha256(canonical.getBytes(StandardCharsets.UTF_8))),
                apkReport,
                axmlReport,
                containerReport);
        writeSummary(report, regressionReport, tamperReport);
        System.out.println("OK: M3-02 Host tamper cases=" + observed.size() +
                " runtime_pending=" + (cases.size() - observed.size()) +
                " catalog_sha256=" + hex(sha256(catalogBytes)));
    }

    private static ObservedHostCase observeHostCase(
            Map<String, String> current, String apk, String axml, String container) {
        String target = current.get("target");
        String mutation = current.get("mutation");
        String code = current.get("expectedCode");
        String stage = current.get("expectedStage");
        String inputHash;
        if (target.equals("apk")) {
            String fixture = APK_FIXTURES.get(mutation);
            if (fixture == null) throw new AssertionError("unmapped APK catalog mutation: " + mutation);
            Matcher match = Pattern.compile("\\{\\\"name\\\":\\\"" + Pattern.quote(fixture) +
                    "\\\",\\\"sha256\\\":\\\"([0-9a-f]{64})\\\",\\\"code\\\":\\\"" +
                    Pattern.quote(code) + "\\\"").matcher(apk);
            if (!match.find()) throw new AssertionError("APK evidence mismatch for " + current.get("id"));
            inputHash = match.group(1);
        } else if (target.equals("axml")) {
            String fixture = AXML_FIXTURES.get(mutation);
            if (fixture == null) throw new AssertionError("unmapped AXML catalog mutation: " + mutation);
            Matcher match = Pattern.compile("\\{\\\"case\\\":\\\"" + Pattern.quote(fixture) +
                    "\\\",\\\"sha256\\\":\\\"([0-9a-f]{64})\\\",\\\"code\\\":\\\"" +
                    Pattern.quote(code) + "\\\"}").matcher(axml);
            if (!match.find()) throw new AssertionError("AXML evidence mismatch for " + current.get("id"));
            inputHash = match.group(1);
        } else {
            String fixture = mutation.replace('_', '-');
            Matcher match = Pattern.compile("\\{\\\"name\\\": \\\"" + Pattern.quote(fixture) +
                    "\\\", \\\"stage\\\": \\\"" + Pattern.quote(stage) +
                    "\\\", \\\"code\\\": \\\"" + Pattern.quote(code) +
                    "\\\", \\\"input_sha256\\\": \\\"([0-9a-f]{64})\\\", \\\"result\\\": \\\"PASS\\\"}")
                    .matcher(container);
            if (!match.find()) throw new AssertionError("container evidence mismatch for " + current.get("id"));
            inputHash = match.group(1);
        }
        return new ObservedHostCase(current.get("id"), stage, code, inputHash);
    }

    private static List<Map<String, String>> parse(byte[] bytes) {
        List<Map<String, String>> cases = new ArrayList<>();
        Map<String, String> current = null;
        for (String raw : new String(bytes, StandardCharsets.UTF_8).split("\\R")) {
            String line = raw.stripTrailing();
            if (line.isBlank() || line.stripLeading().startsWith("#") || line.equals("cases:")) continue;
            boolean newCase = line.startsWith("  - ");
            String content = newCase ? line.substring(4) : line.stripLeading();
            int colon = content.indexOf(':');
            if (colon <= 0) throw new AssertionError("invalid catalog line");
            if (newCase) {
                current = new LinkedHashMap<>();
                cases.add(current);
            }
            if (current == null) throw new AssertionError("catalog field before first case");
            String key = content.substring(0, colon).trim();
            String value = content.substring(colon + 1).trim();
            if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
                value = value.substring(1, value.length() - 1);
            }
            if (current.put(key, value) != null) throw new AssertionError("duplicate catalog field: " + key);
        }
        return cases;
    }

    private static void validate(List<Map<String, String>> cases) {
        if (cases.size() < 20) throw new AssertionError("tamper catalog must contain at least 20 cases");
        Set<String> ids = new HashSet<>();
        for (Map<String, String> current : cases) {
            if (!new ArrayList<>(current.keySet()).equals(FIELDS)) {
                throw new AssertionError("catalog fields/order mismatch for " + current.get("id"));
            }
            String id = current.get("id");
            if (!id.matches("m302-[a-z0-9-]+") || !ids.add(id)) throw new AssertionError("invalid/duplicate id: " + id);
            String target = current.get("target");
            if (!HOST_TARGETS.contains(target) && !RUNTIME_TARGETS.contains(target)) throw new AssertionError("unknown target: " + target);
            if (!current.get("mutation").matches("[a-z0-9_]{3,64}") ||
                    !current.get("expectedStage").matches("[A-Z0-9_]{3,64}") ||
                    !current.get("expectedCode").matches("[A-Z0-9_-]{3,96}")) {
                throw new AssertionError("invalid mutation/stage/code: " + id);
            }
            requireValue(current, "payloadLoaded", "false");
            if (HOST_TARGETS.contains(target)) {
                for (String field : FIELDS.subList(6, FIELDS.size())) requireValue(current, field, "not_applicable");
                continue;
            }
            requireValue(current, "payloadClassLookupAttempted", "false");
            requireValue(current, "verifiedPayloadSessionPublished", "false");
            requireValue(current, "primaryCodePreserved", "true");
            if (target.equals("runtime-prehandle")) {
                requireValue(current, "nativeHandleAcquired", "false");
                requireValue(current, "loadedPayloadPublished", "false");
                requireValue(current, "byteBuffersPublished", "false");
                requireValue(current, "nativeCloseCount", "0");
                requireValue(current, "partialJavaReferencesCleared", "not_applicable");
                requireValue(current, "partialGuardReferencesCleared", "not_applicable");
                requireValue(current, "completedMappingsZeroizedUnmapped", "not_applicable");
                if (!Set.of("true", "not_applicable").contains(current.get("partialMappingZeroizedUnmapped"))) {
                    throw new AssertionError(id + " has invalid partial mapping contract");
                }
            } else if (target.equals("runtime-posthandle")) {
                requireValue(current, "nativeHandleAcquired", "true");
                requireValue(current, "loadedPayloadPublished", "false");
                requireValue(current, "byteBuffersPublished", "false");
                requireValue(current, "nativeCloseCount", "1");
                requireValue(current, "partialJavaReferencesCleared", "true");
                requireValue(current, "partialGuardReferencesCleared", "not_applicable");
                requireValue(current, "completedMappingsZeroizedUnmapped", "true");
                requireValue(current, "partialMappingZeroizedUnmapped", "true");
            } else {
                requireValue(current, "nativeHandleAcquired", "true");
                requireValue(current, "loadedPayloadPublished", "true");
                requireValue(current, "byteBuffersPublished", "true");
                requireValue(current, "nativeCloseCount", "1");
                requireValue(current, "partialJavaReferencesCleared", "not_applicable");
                requireValue(current, "partialGuardReferencesCleared", "true");
                requireValue(current, "completedMappingsZeroizedUnmapped", "true");
                requireValue(current, "partialMappingZeroizedUnmapped", "not_applicable");
            }
            requireValue(current, "cleanupFailureSuppressed",
                    current.get("mutation").contains("cleanup") ? "true" : "false");
        }
    }

    private static void requireValue(Map<String, String> value, String key, String expected) {
        if (!expected.equals(value.get(key))) throw new AssertionError(value.get("id") + " requires " + key + "=" + expected);
    }

    private static void writeTamperReport(
            Path report, int count, List<ObservedHostCase> observed, String catalogHash,
            String evidenceHash, Path apk, Path axml, Path container) throws IOException {
        Files.createDirectories(report.getParent());
        StringBuilder cases = new StringBuilder();
        for (int index = 0; index < observed.size(); index++) {
            cases.append("    ").append(observed.get(index).json());
            if (index != observed.size() - 1) cases.append(',');
            cases.append('\n');
        }
        Files.writeString(report,
                "{\n  \"schema_version\": 1,\n  \"status\": \"PASS_PENDING_DEVICE\",\n" +
                "  \"cases\": " + count + ",\n  \"host_cases_passed\": " + observed.size() + ",\n" +
                "  \"runtime_cases_pending\": " + (count - observed.size()) + ",\n" +
                "  \"catalog_sha256\": \"" + catalogHash + "\",\n" +
                "  \"host_evidence_sha256\": \"" + evidenceHash + "\",\n  \"host_cases\": [\n" + cases + "  ],\n" +
                "  \"production_evidence_sha256\": {\n" +
                "    \"apk\": \"" + hex(sha256(Files.readAllBytes(apk))) + "\",\n" +
                "    \"axml\": \"" + hex(sha256(Files.readAllBytes(axml))) + "\",\n" +
                "    \"container\": \"" + hex(sha256(Files.readAllBytes(container))) + "\"\n  }\n}\n",
                StandardCharsets.UTF_8);
    }

    private static void writeSummary(Path report, Path regression, Path tamper) throws IOException {
        String regressionText = Files.readString(regression, StandardCharsets.UTF_8);
        String tamperText = Files.readString(tamper, StandardCharsets.UTF_8);
        if (!regressionText.contains("\"status\": \"PASS\"") ||
                !tamperText.contains("\"status\": \"PASS_PENDING_DEVICE\"")) {
            throw new AssertionError("M3-02 component report did not pass its local boundary");
        }
        Files.createDirectories(report.getParent());
        Files.writeString(report,
                "{\n  \"schema_version\": 1,\n  \"status\": \"PASS_PENDING_REMOTE\",\n" +
                "  \"regression_sha256\": \"" + hex(sha256(Files.readAllBytes(regression))) + "\",\n" +
                "  \"tamper_sha256\": \"" + hex(sha256(Files.readAllBytes(tamper))) + "\",\n" +
                "  \"jazzer\": {\"version\": \"0.29.1\", \"status\": \"PENDING_PR_CI\"},\n" +
                "  \"native_libfuzzer\": {\"status\": \"PENDING_PR_CI\"},\n" +
                "  \"runtime_device_matrix\": {\"status\": \"PENDING_API29_API36\"}\n}\n",
                StandardCharsets.UTF_8);
    }

    private static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static String hex(byte[] value) {
        StringBuilder output = new StringBuilder(value.length * 2);
        for (byte current : value) output.append(String.format("%02x", current & 0xff));
        return output.toString();
    }

    private record ObservedHostCase(String id, String stage, String code, String inputHash) {
        String canonical() { return id + ":" + stage + ":" + code + ":" + inputHash; }
        String json() {
            return "{\"id\":\"" + id + "\",\"observedStage\":\"" + stage +
                    "\",\"observedCode\":\"" + code + "\",\"input_sha256\":\"" + inputHash +
                    "\",\"result\":\"PASS\"}";
        }
    }
}
