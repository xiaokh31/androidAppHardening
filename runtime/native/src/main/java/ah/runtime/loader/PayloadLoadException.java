package ah.runtime.loader;

/** Stable fail-closed error from the authenticated payload loading boundary. */
public final class PayloadLoadException extends IllegalStateException {
    private static final String PREFIX = "AAH-RUNTIME-CONTAINER-";

    private final String code;

    PayloadLoadException(String code) {
        super(normalize(code));
        this.code = normalize(code);
    }

    PayloadLoadException(String code, Throwable cause) {
        super(normalize(code), cause);
        this.code = normalize(code);
    }

    public String code() {
        return code;
    }

    static PayloadLoadException create(String category) {
        return new PayloadLoadException(PREFIX + category);
    }

    static PayloadLoadException create(String category, Throwable cause) {
        return new PayloadLoadException(PREFIX + category, cause);
    }

    private static String normalize(String value) {
        if (value == null || !value.startsWith(PREFIX)) {
            return PREFIX + "INTERNAL";
        }
        return value;
    }
}
