package ah.tools.validation.fuzz;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public final class RegressionFuzzRunner {
    private RegressionFuzzRunner() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 4) throw new IllegalArgumentException("expected corpus, regressions, work, report");
        Path corpus = Path.of(args[0]).toAbsolutePath().normalize();
        Path regressions = Path.of(args[1]).toAbsolutePath().normalize();
        Path work = Path.of(args[2]).toAbsolutePath().normalize();
        Path report = Path.of(args[3]).toAbsolutePath().normalize();
        Files.createDirectories(work);
        System.setProperty("ah.m302.workDir", work.toString());

        List<Seed> seeds = new ArrayList<>();
        collect(corpus, seeds);
        collect(regressions, seeds);
        if (seeds.isEmpty()) throw new AssertionError("M3-02 corpus must not be empty");
        seeds.sort(Comparator.comparing(seed -> seed.path.toString()));
        long nativeInputs = seeds.stream()
                .filter(seed -> seed.root.relativize(seed.path).getName(0).toString().equals("native"))
                .count();
        long jvmInputs = seeds.size() - nativeInputs;

        List<String> first = run(seeds);
        List<String> second = run(seeds);
        if (!first.equals(second)) throw new AssertionError("regression outcomes differ between identical runs");

        StringBuilder canonical = new StringBuilder();
        first.forEach(value -> canonical.append(value).append('\n'));
        long structuredMutations = first.stream().filter(value -> value.startsWith("structured:")).count();
        String resultHash = FuzzSupport.hex(FuzzSupport.sha256(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        Files.createDirectories(report.getParent());
        Files.writeString(
                report,
                "{\n" +
                        "  \"schema_version\": 1,\n" +
                        "  \"status\": \"PASS\",\n" +
                        "  \"runs\": 2,\n" +
                        "  \"inputs\": " + seeds.size() + ",\n" +
                        "  \"jvm_inputs_executed\": " + jvmInputs + ",\n" +
                        "  \"native_inputs_deferred_to_sanitizer\": " + nativeInputs + ",\n" +
                        "  \"structured_mutations_executed\": " + structuredMutations + ",\n" +
                        "  \"result_sha256\": \"" + resultHash + "\"\n" +
                        "}\n",
                StandardCharsets.UTF_8);
        System.out.println("OK: M3-02 regression inputs=" + seeds.size() + " runs=2 sha256=" + resultHash);
    }

    private static void collect(Path root, List<Seed> output) throws IOException {
        if (!Files.isDirectory(root)) throw new AssertionError("missing fuzz resource directory: " + root.getFileName());
        try (var paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile).forEach(path -> output.add(new Seed(root, path)));
        }
    }

    private static List<String> run(List<Seed> seeds) throws Exception {
        List<String> outcomes = new ArrayList<>();
        for (Seed seed : seeds) {
            byte[] bytes = Files.readAllBytes(seed.path);
            byte[] before = FuzzSupport.sha256(bytes);
            String group = seed.root.relativize(seed.path).getName(0).toString();
            switch (group) {
                case "apk" -> {
                    ApkInspectorFuzzTarget.fuzzerTestOneInput(bytes);
                    if (seed.path.getFileName().toString().equals("valid-m301.apk")) {
                        for (byte[] mutation : structuredApkMutations(bytes)) {
                            ApkInspectorFuzzTarget.fuzzerTestOneInput(mutation);
                            outcomes.add("structured:apk:" + FuzzSupport.hex(FuzzSupport.sha256(mutation)));
                        }
                    }
                }
                case "axml" -> {
                    BinaryAxmlFuzzTarget.fuzzerTestOneInput(bytes);
                    if (seed.path.getFileName().toString().equals("valid-manifest.axml")) {
                        for (byte[] mutation : structuredAxmlMutations(bytes)) {
                            BinaryAxmlFuzzTarget.fuzzerTestOneInput(mutation);
                            outcomes.add("structured:axml:" + FuzzSupport.hex(FuzzSupport.sha256(mutation)));
                        }
                    }
                }
                case "native" -> {
                    // Native inputs are executed twice by the ASan/UBSan libFuzzer CI target.
                }
                default -> throw new AssertionError("unknown JVM corpus target: " + group);
            }
            byte[] after = Files.readAllBytes(seed.path);
            if (!MessageDigest.isEqual(before, FuzzSupport.sha256(after))) {
                throw new AssertionError("corpus input modified: " + seed.path.getFileName());
            }
            outcomes.add(group + ":" + FuzzSupport.hex(before));
        }
        return outcomes;
    }

    private static List<byte[]> structuredApkMutations(byte[] valid) {
        byte[] truncated = Arrays.copyOf(valid, valid.length - 12);
        byte[] centralDirectoryOffset = valid.clone();
        int eocd = findSignature(centralDirectoryOffset, 0x06054b50);
        if (eocd < 0) throw new AssertionError("valid APK seed lacks EOCD");
        Arrays.fill(centralDirectoryOffset, eocd + 16, eocd + 20, (byte) 0xff);
        byte[] localHeader = valid.clone();
        int local = findSignature(localHeader, 0x04034b50);
        if (local < 0) throw new AssertionError("valid APK seed lacks local header");
        Arrays.fill(localHeader, local + 18, local + 26, (byte) 0xff);
        return List.of(truncated, centralDirectoryOffset, localHeader);
    }

    private static List<byte[]> structuredAxmlMutations(byte[] valid) {
        byte[] truncated = Arrays.copyOf(valid, valid.length - 1);
        byte[] oversizedRoot = valid.clone();
        Arrays.fill(oversizedRoot, 4, 8, (byte) 0xff);
        byte[] childSize = valid.clone();
        if (childSize.length < 16) throw new AssertionError("valid AXML seed is too short");
        Arrays.fill(childSize, 12, 16, (byte) 0xff);
        return List.of(truncated, oversizedRoot, childSize);
    }

    private static int findSignature(byte[] value, int signature) {
        for (int index = value.length - 4; index >= 0; index--) {
            int candidate = (value[index] & 0xff) | ((value[index + 1] & 0xff) << 8) |
                    ((value[index + 2] & 0xff) << 16) | ((value[index + 3] & 0xff) << 24);
            if (candidate == signature) return index;
        }
        return -1;
    }

    private record Seed(Path root, Path path) {}
}
