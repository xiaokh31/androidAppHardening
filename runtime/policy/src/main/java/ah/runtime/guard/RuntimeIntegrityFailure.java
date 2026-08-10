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
            return category;
        }
        for (int index = 0; index < category.length(); index++) {
            char value = category.charAt(index);
            if (!((value >= 'A' && value <= 'Z') || value == '_')) {
                return PREFIX + "INTERNAL";
            }
        }
        return PREFIX + category;
    }
}
