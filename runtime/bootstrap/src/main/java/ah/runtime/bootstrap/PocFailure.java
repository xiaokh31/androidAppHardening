package ah.runtime.bootstrap;

final class PocFailure {
    static final String CODE = "AAH-P001";

    private PocFailure() {}

    static IllegalStateException create(String detail) {
        return new IllegalStateException(CODE + ": " + detail);
    }

    static IllegalStateException create(String detail, Throwable cause) {
        return new IllegalStateException(CODE + ": " + detail, cause);
    }
}
