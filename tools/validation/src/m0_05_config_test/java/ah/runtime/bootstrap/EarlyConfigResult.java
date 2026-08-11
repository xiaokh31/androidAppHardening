package ah.runtime.bootstrap;

/** Legacy validation-only authenticated ConfigV2 view. */
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
