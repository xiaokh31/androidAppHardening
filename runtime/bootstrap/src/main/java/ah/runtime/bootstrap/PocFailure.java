package ah.runtime.bootstrap;

final class PocFailure {
    static final String PAYLOAD_CODE = "AAH-P001";
    static final String FACTORY_CODE = "AAH-P002";
    static final String DELEGATE_CODE = "AAH-P003";
    static final String JNI_CODE = "AAH-P004";
    static final String SIGNER_UNREADABLE_CODE = "AAH-P005";
    static final String SIGNER_INVALID_CODE = "AAH-P006";
    static final String SIGNER_NON_UNIQUE_CODE = "AAH-P007";
    static final String SIGNER_MISMATCH_CODE = "AAH-P008";
    static final String METADATA_CODE = "AAH-P009";

    private PocFailure() {}

    static IllegalStateException create(String detail) {
        return create(PAYLOAD_CODE, detail);
    }

    static IllegalStateException create(String code, String detail) {
        return new IllegalStateException(code + ": " + detail);
    }

    static boolean isPocFailure(Throwable failure) {
        return failure instanceof IllegalStateException
                && failure.getMessage() != null
                && failure.getMessage().startsWith(PAYLOAD_CODE + ":");
    }

    static boolean hasCode(Throwable failure, String code) {
        return failure instanceof IllegalStateException
                && failure.getMessage() != null
                && failure.getMessage().startsWith(code + ":");
    }
}
