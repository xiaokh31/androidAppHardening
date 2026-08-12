package ah.runtime.risk;

/** Package-private JNI boundary. Native output contains only states and a family bitmask. */
final class NativeRiskSignals {
    private static final boolean AVAILABLE;

    static {
        boolean loaded = false;
        try {
            System.loadLibrary("ah_runtime");
            loaded = true;
        } catch (RuntimeException | LinkageError unavailable) {
            // The policy layer converts a missing Native capability to UNAVAILABLE/zero.
        }
        AVAILABLE = loaded;
    }

    private NativeRiskSignals() {}

    static boolean available() {
        return AVAILABLE;
    }

    static native int[] collect();
}
