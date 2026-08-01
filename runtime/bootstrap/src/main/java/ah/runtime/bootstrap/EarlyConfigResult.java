package ah.runtime.bootstrap;

/** Authenticated M0-05 ConfigV2 view; no field is exposed before signer binding succeeds. */
public final class EarlyConfigResult {
    private final String originalFactory;

    EarlyConfigResult(String originalFactory) {
        this.originalFactory = originalFactory;
    }

    public boolean hasOriginalFactory() {
        return originalFactory != null;
    }

    public String originalFactory() {
        return originalFactory;
    }
}
