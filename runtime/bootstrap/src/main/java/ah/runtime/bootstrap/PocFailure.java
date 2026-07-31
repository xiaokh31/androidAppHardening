package ah.runtime.bootstrap;

final class PocFailure {
    static final String CODE = "AAH-P001";

    private PocFailure() {}

    static IllegalStateException create(String detail) {
        return new IllegalStateException(CODE + ": " + detail);
    }

    static boolean isPocFailure(Throwable failure) {
        return failure instanceof IllegalStateException
                && failure.getMessage() != null
                && failure.getMessage().startsWith(CODE + ":");
    }
}
