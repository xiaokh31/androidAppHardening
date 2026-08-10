package ah.runtime.guard;

/** Internal exception whose public message is only a stable, non-sensitive code. */
final class RuntimeIntegrityFailure extends IllegalStateException {
    static final String PREFIX = "AAH-RUNTIME-INTEGRITY-";

    private final IntegrityResult result;

    private RuntimeIntegrityFailure(String category, Throwable cause) {
        super(normalizeCategory(category), cause);
        result = IntegrityResult.rejected(category);
    }

    static RuntimeIntegrityFailure create(String category) {
        return new RuntimeIntegrityFailure(category, null);
    }

    static RuntimeIntegrityFailure create(String category, Throwable cause) {
        return new RuntimeIntegrityFailure(category, cause);
    }

    IntegrityResult result() {
        return result;
    }

    static String normalizeCategory(String category) {
        if (category == null || category.isEmpty()) {
            return PREFIX + "INTERNAL";
        }
        if (category.startsWith(PREFIX)) {
            category = category.substring(PREFIX.length());
        }
        switch (category) {
            case "APK_METADATA":
            case "ARGUMENT":
            case "BINDING":
            case "CLOSED":
            case "CONTAINER":
            case "INPUT_CHANGED":
            case "INTERNAL":
            case "LINEAGE_INVALID":
            case "LINEAGE_MISMATCH":
            case "METADATA":
            case "METADATA_HANDLE":
            case "MULTIPLE_CURRENT":
            case "PACKAGE_MISMATCH":
            case "SESSION":
            case "SIGNATURE_INVALID":
            case "SIGNER_FORMAT":
            case "SIGNER_MISMATCH":
            case "SNAPSHOT_CHANGED":
            case "SOURCE":
            case "UNSIGNED":
            case "VERSION":
                return PREFIX + category;
            default:
                return PREFIX + "INTERNAL";
        }
    }
}
