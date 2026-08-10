package ah.runtime.bootstrap;

/** Stable, non-sensitive bootstrap failure. No original throwable is retained. */
final class BootstrapFailure extends IllegalStateException {
    static final String PREFIX = "AAH-RUNTIME-BOOT-";
    static final String ARGUMENT = "ARGUMENT";
    static final String CLEANUP = "CLEANUP";
    static final String COMPONENT = "COMPONENT";
    static final String CONFIG_FACTORY = "CONFIG_FACTORY";
    static final String CONFIG_VERSION = "CONFIG_VERSION";
    static final String FACTORY_CONSTRUCT = "FACTORY_CONSTRUCT";
    static final String FACTORY_HOOK = "FACTORY_HOOK";
    static final String FACTORY_NULL = "FACTORY_NULL";
    static final String FINAL_LOADER = "FINAL_LOADER";
    static final String GUARD = "GUARD";
    static final String INTERNAL = "INTERNAL";
    static final String REENTRANT = "REENTRANT";
    static final String RESOURCE = "RESOURCE";

    private final String category;

    private BootstrapFailure(String category) {
        super(message(category));
        this.category = normalize(category);
    }

    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }

    static BootstrapFailure create(String category) {
        return new BootstrapFailure(category);
    }

    static String message(String category) {
        return PREFIX + normalize(category);
    }

    String category() {
        return category;
    }

    private static String normalize(String category) {
        if (ARGUMENT.equals(category)
                || CLEANUP.equals(category)
                || COMPONENT.equals(category)
                || CONFIG_FACTORY.equals(category)
                || CONFIG_VERSION.equals(category)
                || FACTORY_CONSTRUCT.equals(category)
                || FACTORY_HOOK.equals(category)
                || FACTORY_NULL.equals(category)
                || FINAL_LOADER.equals(category)
                || GUARD.equals(category)
                || INTERNAL.equals(category)
                || REENTRANT.equals(category)
                || RESOURCE.equals(category)) {
            return category;
        }
        return INTERNAL;
    }
}
