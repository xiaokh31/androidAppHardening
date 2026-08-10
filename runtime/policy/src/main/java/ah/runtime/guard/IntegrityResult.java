package ah.runtime.guard;

/** Immutable machine-readable result used at the runtime integrity boundary. */
public final class IntegrityResult {
    public enum Status {
        VERIFIED,
        REJECTED
    }

    private final Status status;
    private final String code;

    private IntegrityResult(Status status, String code) {
        this.status = status;
        this.code = code;
    }

    public static IntegrityResult verified() {
        return new IntegrityResult(Status.VERIFIED, RuntimeIntegrityFailure.PREFIX + "VERIFIED");
    }

    public static IntegrityResult rejected(String category) {
        return new IntegrityResult(
                Status.REJECTED, RuntimeIntegrityFailure.normalizeCategory(category));
    }

    public Status status() {
        return status;
    }

    public String code() {
        return code;
    }
}
