package ah.runtime;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Dependency-free M2-04 ABI compatibility and report-contract matrix. */
public final class AbiCompatibilitySelfTest {
    private static final List<String> ALL =
            List.of("armeabi-v7a", "arm64-v8a", "x86", "x86_64");

    private AbiCompatibilitySelfTest() {}

    public static void main(String[] arguments) throws Exception {
        require(arguments.length == 1, "report directory");
        int cases = 0;
        AbiCompatibility javaOnly = AbiCompatibilityPolicy.evaluate(Set.of());
        check(javaOnly.runtimeAvailableAbis().equals(new LinkedHashSet<>(ALL)), "runtime-all"); cases++;
        check(javaOnly.inputNativeAbis().isEmpty(), "java-input-empty"); cases++;
        check(javaOnly.outputEffectiveAbis().equals(new LinkedHashSet<>(ALL)), "java-output-all"); cases++;
        check(javaOnly.limitations().isEmpty(), "java-limitations-empty"); cases++;

        for (String abi : ALL) {
            AbiCompatibility single = AbiCompatibilityPolicy.evaluate(Set.of(abi));
            check(single.inputNativeAbis().equals(Set.of(abi)), "single-input-" + abi); cases++;
            check(single.outputEffectiveAbis().equals(Set.of(abi)), "single-output-" + abi); cases++;
            check(single.limitations().equals(List.of("OUTPUT_LIMITED_TO_INPUT_NATIVE_ABIS")),
                    "single-limitation-" + abi); cases++;
        }

        AbiCompatibility armOnly = AbiCompatibilityPolicy.evaluate(
                Set.of("armeabi-v7a", "arm64-v8a"));
        check(armOnly.outputEffectiveAbis().equals(
                new LinkedHashSet<>(List.of("armeabi-v7a", "arm64-v8a"))), "arm-only"); cases++;
        check(!armOnly.outputEffectiveAbis().contains("x86"), "arm-not-converted-to-x86"); cases++;
        for (String abi : List.of("x86", "x86_64")) {
            AbiCompatibility x86 = AbiCompatibilityPolicy.evaluate(Set.of(abi));
            check(x86.limitations().stream().noneMatch(value ->
                    value.contains("RISK") || value.contains("EMULATOR")), "x86-zero-risk-" + abi);
            cases++;
        }

        expect("AAH-RUNTIME-ABI-ARGUMENT", () -> AbiCompatibilityPolicy.evaluate(null)); cases++;
        expect("AAH-RUNTIME-ABI-ARGUMENT", () -> {
            Set<String> values = new LinkedHashSet<>();
            values.add(null);
            AbiCompatibilityPolicy.evaluate(values);
        }); cases++;
        expect("AAH-RUNTIME-ABI-UNSUPPORTED", () ->
                AbiCompatibilityPolicy.evaluate(Set.of("mips"))); cases++;
        expectUnsupported(javaOnly.runtimeAvailableAbis()); cases++;
        expectUnsupported(javaOnly.outputEffectiveAbis()); cases++;
        expectUnsupported(javaOnly.limitations()); cases++;

        Path reportDirectory = Path.of(arguments[0]);
        Files.createDirectories(reportDirectory);
        String json = "{\n"
                + "  \"runtime_available_abis\": [\"armeabi-v7a\", \"arm64-v8a\", \"x86\", \"x86_64\"],\n"
                + "  \"input_native_abis\": [\"armeabi-v7a\", \"arm64-v8a\"],\n"
                + "  \"output_effective_abis\": [\"armeabi-v7a\", \"arm64-v8a\"],\n"
                + "  \"limitations\": [\"OUTPUT_LIMITED_TO_INPUT_NATIVE_ABIS\"],\n"
                + "  \"x86_risk_contribution\": 0,\n"
                + "  \"x86_64_risk_contribution\": 0\n"
                + "}\n";
        Files.write(reportDirectory.resolve("abi-compatibility.json"),
                json.getBytes(StandardCharsets.UTF_8));
        System.out.println("M2-04 ABI compatibility matrix PASS cases=" + cases);
    }

    private static void expect(String message, ThrowingRunnable action) throws Exception {
        try {
            action.run();
            throw new AssertionError("missing " + message);
        } catch (IllegalArgumentException expected) {
            check(message.equals(expected.getMessage()), "wrong error");
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void expectUnsupported(Object value) {
        try {
            if (value instanceof Set set) set.add("mips");
            else if (value instanceof List list) list.add("RISK");
            throw new AssertionError("mutable result");
        } catch (UnsupportedOperationException expected) {
            // Expected.
        }
    }

    private static void check(boolean condition, String label) {
        require(condition, label);
    }

    private static void require(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
