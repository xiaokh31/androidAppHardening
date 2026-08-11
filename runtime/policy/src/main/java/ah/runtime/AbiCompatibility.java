package ah.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Immutable distinction between Runtime build ABIs and one output APK's effective ABIs. */
public final class AbiCompatibility {
    private final Set<String> runtimeAvailableAbis;
    private final Set<String> inputNativeAbis;
    private final Set<String> outputEffectiveAbis;
    private final List<String> limitations;

    AbiCompatibility(
            Set<String> runtimeAvailableAbis,
            Set<String> inputNativeAbis,
            Set<String> outputEffectiveAbis,
            List<String> limitations) {
        this.runtimeAvailableAbis = immutableCopy(runtimeAvailableAbis);
        this.inputNativeAbis = immutableCopy(inputNativeAbis);
        this.outputEffectiveAbis = immutableCopy(outputEffectiveAbis);
        this.limitations = Collections.unmodifiableList(new ArrayList<>(limitations));
    }

    public Set<String> runtimeAvailableAbis() {
        return runtimeAvailableAbis;
    }

    public Set<String> inputNativeAbis() {
        return inputNativeAbis;
    }

    public Set<String> outputEffectiveAbis() {
        return outputEffectiveAbis;
    }

    public List<String> limitations() {
        return limitations;
    }

    private static Set<String> immutableCopy(Set<String> values) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(values));
    }
}
