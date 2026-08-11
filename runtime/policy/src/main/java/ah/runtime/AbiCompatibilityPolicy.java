package ah.runtime;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** ADR-0005 ABI selection policy. CPU architecture alone is never a risk signal. */
public final class AbiCompatibilityPolicy {
    private static final List<String> ABI_ORDER =
            List.of("armeabi-v7a", "arm64-v8a", "x86", "x86_64");
    private static final Set<String> AVAILABLE =
            Collections.unmodifiableSet(new LinkedHashSet<>(ABI_ORDER));
    private static final String LIMITATION = "OUTPUT_LIMITED_TO_INPUT_NATIVE_ABIS";

    private AbiCompatibilityPolicy() {}

    public static AbiCompatibility evaluate(Set<String> inputNativeAbis) {
        if (inputNativeAbis == null || inputNativeAbis.stream().anyMatch(value -> value == null)) {
            throw new IllegalArgumentException("AAH-RUNTIME-ABI-ARGUMENT");
        }
        if (!AVAILABLE.containsAll(inputNativeAbis)) {
            throw new IllegalArgumentException("AAH-RUNTIME-ABI-UNSUPPORTED");
        }
        LinkedHashSet<String> canonicalInput = canonical(inputNativeAbis);
        LinkedHashSet<String> effective = canonicalInput.isEmpty()
                ? new LinkedHashSet<>(ABI_ORDER)
                : new LinkedHashSet<>(canonicalInput);
        List<String> limitations = canonicalInput.isEmpty() ? List.of() : List.of(LIMITATION);
        return new AbiCompatibility(AVAILABLE, canonicalInput, effective, limitations);
    }

    private static LinkedHashSet<String> canonical(Set<String> values) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String abi : ABI_ORDER) {
            if (values.contains(abi)) {
                result.add(abi);
            }
        }
        return result;
    }
}
