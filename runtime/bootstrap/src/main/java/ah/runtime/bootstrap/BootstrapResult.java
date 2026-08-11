package ah.runtime.bootstrap;

import android.app.AppComponentFactory;
import ah.runtime.guard.VerifiedPayloadSession;

/** Immutable terminal bootstrap result retained for the lifetime of one process. */
final class BootstrapResult {
    enum Status {
        READY,
        FAILURE
    }

    private final Status status;
    private final HardeningBootstrap.BootstrapSession owner;
    private final VerifiedPayloadSession verifiedSession;
    private final ClassLoader provisionalClassLoader;
    private final ClassLoader finalClassLoader;
    private final AppComponentFactory originalFactory;
    private final String errorCategory;
    private final String cleanupErrorCategory;

    private BootstrapResult(
            Status status,
            HardeningBootstrap.BootstrapSession owner,
            VerifiedPayloadSession verifiedSession,
            ClassLoader provisionalClassLoader,
            ClassLoader finalClassLoader,
            AppComponentFactory originalFactory,
            String errorCategory,
            String cleanupErrorCategory) {
        this.status = status;
        this.owner = owner;
        this.verifiedSession = verifiedSession;
        this.provisionalClassLoader = provisionalClassLoader;
        this.finalClassLoader = finalClassLoader;
        this.originalFactory = originalFactory;
        this.errorCategory = errorCategory;
        this.cleanupErrorCategory = cleanupErrorCategory;
    }

    static BootstrapResult ready(
            HardeningBootstrap.BootstrapSession owner,
            ClassLoader provisionalClassLoader,
            ClassLoader finalClassLoader,
            AppComponentFactory originalFactory) {
        return new BootstrapResult(
                Status.READY,
                owner,
                owner.verifiedSession(),
                provisionalClassLoader,
                finalClassLoader,
                originalFactory,
                null,
                null);
    }

    static BootstrapResult failure(String errorCategory, String cleanupErrorCategory) {
        return new BootstrapResult(
                Status.FAILURE,
                null,
                null,
                null,
                null,
                null,
                errorCategory,
                cleanupErrorCategory);
    }

    Status status() {
        return status;
    }

    ClassLoader provisionalClassLoader() {
        return provisionalClassLoader;
    }

    ClassLoader finalClassLoader() {
        return finalClassLoader;
    }

    AppComponentFactory originalFactory() {
        return originalFactory;
    }

    VerifiedPayloadSession verifiedSession() {
        return verifiedSession;
    }

    HardeningBootstrap.BootstrapSession owner() {
        return owner;
    }

    String errorCode() {
        return errorCategory == null ? null : BootstrapFailure.message(errorCategory);
    }

    String cleanupErrorCode() {
        return cleanupErrorCategory == null
                ? null
                : BootstrapFailure.message(cleanupErrorCategory);
    }

    BootstrapFailure failure() {
        return BootstrapFailure.create(
                errorCategory == null ? BootstrapFailure.INTERNAL : errorCategory);
    }
}
