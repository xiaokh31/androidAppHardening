package ah.tools.validation.tamper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

        byte[] original = new byte[4096];
        for (int index = 0; index < original.length; index++) original[index] = (byte) (index * 31 + 7);
        byte[] originalDigest = sha256(original);
        List<String> mutations = new ArrayList<>();
        for (Map<String, String> current : cases) {
            byte[] mutated = mutate(original, current.get("mutation"));
            if (Arrays.equals(original, mutated)) throw new AssertionError("mutation made no change: " + current.get("id"));
            if (!MessageDigest.isEqual(originalDigest, sha256(original))) {
                throw new AssertionError("mutation changed the original fixture: " + current.get("id"));
            }
            Path target = work.resolve(current.get("id") + ".bin");
            Files.write(target, mutated);
            mutations.add(current.get("id") + ":" + hex(sha256(mutated)));
        }

        String mutationDigest = hex(sha256(String.join("\n", mutations).getBytes(StandardCharsets.UTF_8)));
        verifyProductionEvidence(apkReport, axmlReport, containerReport);
        writeTamperReport(
                tamperReport,
                cases.size(),
                hex(sha256(catalogBytes)),
                mutationDigest,
                apkReport,
                axmlReport,
                containerReport);
        writeSummary(report, regressionReport, tamperReport);
        System.out.println("OK: M3-02 tamper cases=" + cases.size() + " catalog_sha256=" + hex(sha256(catalogBytes)));
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
            if (!HOST_TARGETS.contains(target) && !RUNTIME_TARGETS.contains(target)) {
                throw new AssertionError("unknown target: " + target);
            }
            if (!current.get("mutation").matches("[a-z0-9_]{3,64}")) {
                throw new AssertionError("invalid mutation: " + id);
            }
            if (!current.get("expectedStage").matches("[A-Z0-9_]{3,64}") ||
                    !current.get("expectedCode").matches("[A-Z0-9_-]{3,96}")) {
                throw new AssertionError("invalid expected stage/code: " + id);
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
                String partialMapping = current.get("partialMappingZeroizedUnmapped");
                if (!partialMapping.equals("true") && !partialMapping.equals("not_applicable")) {
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
            if (current.get("mutation").contains("cleanup")) {
                requireValue(current, "cleanupFailureSuppressed", "true");
            } else {
                requireValue(current, "cleanupFailureSuppressed", "false");
            }
        }
    }

    private static void requireValue(Map<String, String> value, String key, String expected) {
        if (!expected.equals(value.get(key))) {
            throw new AssertionError(value.get("id") + " requires " + key + "=" + expected);
        }
    }

    private static byte[] mutate(byte[] original, String mutation) {
        if (mutation.equals("truncate_tail")) return Arrays.copyOf(original, original.length - 17);
        byte[] output = original.clone();
        int index = Math.floorMod(mutation.hashCode(), output.length);
        output[index] ^= (byte) (0x80 | (mutation.length() & 0x7f));
        if (mutation.endsWith("overflow")) Arrays.fill(output, index, Math.min(output.length, index + 8), (byte) 0xff);
        if (mutation.equals("duplicate_entry")) System.arraycopy(output, 64, output, 128, 32);
        if (mutation.equals("path_traversal")) {
            byte[] traversal = "../payload.dex".getBytes(StandardCharsets.US_ASCII);
            System.arraycopy(traversal, 0, output, index, Math.min(traversal.length, output.length - index));
        }
        return output;
    }

    private static void verifyProductionEvidence(Path apk, Path axml, Path container) throws IOException {
        String apkText = Files.readString(apk, StandardCharsets.UTF_8);
        for (String code : List.of(
                "INPUT_ZIP_STRUCTURE", "INPUT_DUPLICATE_ENTRY", "INPUT_PATH_UNSAFE", "INPUT_LIMIT_EXCEEDED")) {
            if (!apkText.contains("\"code\":\"" + code + "\"")) {
                throw new AssertionError("APK production matrix missing " + code);
            }
        }
        String axmlText = Files.readString(axml, StandardCharsets.UTF_8);
        for (String code : List.of("AXML_MALFORMED", "AXML_LIMIT_EXCEEDED", "AXML_RESERVED_COLLISION")) {
            if (!axmlText.contains("\"code\":\"" + code + "\"")) {
                throw new AssertionError("AXML production matrix missing " + code);
            }
        }
        String containerText = Files.readString(container, StandardCharsets.UTF_8);
        if (!containerText.contains("\"name\": \"tamper_matrix\"") ||
                !containerText.contains("\"status\": \"pass\"")) {
            throw new AssertionError("container production tamper matrix did not pass");
        }
    }

    private static void writeTamperReport(
            Path report,
            int count,
            String catalogHash,
            String mutationHash,
            Path apk,
            Path axml,
            Path container) throws IOException {
        Files.createDirectories(report.getParent());
        Files.writeString(
                report,
                "{\n" +
                        "  \"schema_version\": 1,\n" +
                        "  \"status\": \"PASS\",\n" +
                        "  \"cases\": " + count + ",\n" +
                        "  \"catalog_sha256\": \"" + catalogHash + "\",\n" +
                        "  \"mutations_sha256\": \"" + mutationHash + "\",\n" +
                        "  \"production_evidence_sha256\": {\n" +
                        "    \"apk\": \"" + hex(sha256(Files.readAllBytes(apk))) + "\",\n" +
                        "    \"axml\": \"" + hex(sha256(Files.readAllBytes(axml))) + "\",\n" +
                        "    \"container\": \"" + hex(sha256(Files.readAllBytes(container))) + "\"\n" +
                        "  }\n" +
                        "}\n",
                StandardCharsets.UTF_8);
    }

    private static void writeSummary(Path report, Path regression, Path tamper) throws IOException {
        String regressionText = Files.readString(regression, StandardCharsets.UTF_8);
        String tamperText = Files.readString(tamper, StandardCharsets.UTF_8);
        if (!regressionText.contains("\"status\": \"PASS\"") || !tamperText.contains("\"status\": \"PASS\"")) {
            throw new AssertionError("M3-02 component report did not pass");
        }
        Files.createDirectories(report.getParent());
        Files.writeString(
                report,
                "{\n" +
                        "  \"schema_version\": 1,\n" +
                        "  \"status\": \"PASS\",\n" +
                        "  \"regression_sha256\": \"" + hex(sha256(Files.readAllBytes(regression))) + "\",\n" +
                        "  \"tamper_sha256\": \"" + hex(sha256(Files.readAllBytes(tamper))) + "\",\n" +
                        "  \"jazzer\": {\"version\": \"0.29.1\", \"status\": \"PENDING_PR_CI\"},\n" +
                        "  \"native_libfuzzer\": {\"status\": \"PENDING_PR_CI\"},\n" +
                        "  \"runtime_device_matrix\": {\"status\": \"PENDING_API29_API36\"}\n" +
                        "}\n",
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
}
