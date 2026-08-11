package ah.runtime.bootstrap;

import android.app.AppComponentFactory;
import android.content.pm.ApplicationInfo;
import ah.runtime.guard.RuntimeStartupGuard;
import ah.runtime.guard.VerifiedPayloadSession;
import ah.runtime.guard.VerifiedStartupConfiguration;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;

/** Process-wide, fail-closed bootstrap state machine. */
final class HardeningBootstrap {
    enum State {
        NEW,
        INSTALLING,
        READY,
        FAILED
    }

    interface BootstrapSession extends AutoCloseable {
        ClassLoader provisionalClassLoader();

        String originalFactoryClassNameOrNull();

        int containerMajor();

        int containerMinor();

        int signerPolicyVersion();

        int riskPolicyVersion();

        VerifiedPayloadSession verifiedSession();

        @Override
        void close();
    }

    interface SessionOpener {
        BootstrapSession open(ClassLoader shellLoader, ApplicationInfo applicationInfo);
    }

    interface FactoryAdapter {
        AppComponentFactory create(ClassLoader provisionalLoader, String factoryClassName);

        ClassLoader delegate(
                AppComponentFactory factory,
                ClassLoader provisionalLoader,
                ApplicationInfo applicationInfo);

        void validate(
                ClassLoader finalLoader,
                AppComponentFactory factory,
                String factoryClassName);
    }

    static final class Coordinator {
        private final SessionOpener sessionOpener;
        private final FactoryAdapter factoryAdapter;
        private State state = State.NEW;
        private BootstrapResult terminalResult;
        private String pendingFailureCategory;
        private int failedCloseAttempts;

        Coordinator(SessionOpener sessionOpener, FactoryAdapter factoryAdapter) {
            if (sessionOpener == null || factoryAdapter == null) {
                throw BootstrapFailure.create(BootstrapFailure.ARGUMENT);
            }
            this.sessionOpener = sessionOpener;
            this.factoryAdapter = factoryAdapter;
        }

        synchronized BootstrapResult install(
                ClassLoader shellLoader,
                ApplicationInfo applicationInfo) {
            if (state == State.READY || state == State.FAILED) {
                return terminalResult;
            }
            if (state == State.INSTALLING) {
                state = State.FAILED;
                pendingFailureCategory = BootstrapFailure.REENTRANT;
                return BootstrapResult.failure(pendingFailureCategory, null);
            }
            state = State.INSTALLING;

            BootstrapSession session = null;
            boolean committed = false;
            String failureCategory = null;
            String cleanupCategory = null;
            try {
                if (shellLoader == null || applicationInfo == null) {
                    throw BootstrapFailure.create(BootstrapFailure.ARGUMENT);
                }
                session = sessionOpener.open(shellLoader, applicationInfo);
                if (session == null) {
                    throw BootstrapFailure.create(BootstrapFailure.GUARD);
                }
                requireStillInstalling();
                validateConfiguration(session);
                ClassLoader provisionalLoader = session.provisionalClassLoader();
                if (provisionalLoader == null) {
                    throw BootstrapFailure.create(BootstrapFailure.GUARD);
                }

                String factoryName = session.originalFactoryClassNameOrNull();
                AppComponentFactory originalFactory = null;
                ClassLoader finalLoader = provisionalLoader;
                if (factoryName != null) {
                    originalFactory = factoryAdapter.create(provisionalLoader, factoryName);
                    requireStillInstalling();
                    finalLoader = factoryAdapter.delegate(
                            originalFactory, provisionalLoader, applicationInfo);
                    requireStillInstalling();
                    if (finalLoader == null) {
                        throw BootstrapFailure.create(BootstrapFailure.FACTORY_NULL);
                    }
                    factoryAdapter.validate(finalLoader, originalFactory, factoryName);
                }
                requireStillInstalling();
                terminalResult = BootstrapResult.ready(
                        session, provisionalLoader, finalLoader, originalFactory);
                state = State.READY;
                pendingFailureCategory = null;
                committed = true;
                return terminalResult;
            } catch (Throwable failure) {
                failureCategory = pendingFailureCategory != null
                        ? pendingFailureCategory
                        : classify(failure);
            } finally {
                if (!committed && session != null) {
                    failedCloseAttempts++;
                    try {
                        session.close();
                    } catch (Throwable cleanupFailure) {
                        cleanupCategory = BootstrapFailure.CLEANUP;
                    }
                }
            }

            state = State.FAILED;
            pendingFailureCategory = null;
            terminalResult = BootstrapResult.failure(
                    failureCategory == null ? BootstrapFailure.INTERNAL : failureCategory,
                    cleanupCategory);
            return terminalResult;
        }

        synchronized State state() {
            return state;
        }

        synchronized int failedCloseAttempts() {
            return failedCloseAttempts;
        }

        synchronized boolean failedCleanupCompleted() {
            return terminalResult != null
                    && terminalResult.status() == BootstrapResult.Status.FAILURE
                    && terminalResult.cleanupErrorCode() == null
                    && failedCloseAttempts == 1;
        }

        synchronized boolean failedReferencesCleared() {
            return terminalResult != null
                    && terminalResult.status() == BootstrapResult.Status.FAILURE
                    && terminalResult.owner() == null
                    && terminalResult.verifiedSession() == null
                    && terminalResult.provisionalClassLoader() == null
                    && terminalResult.finalClassLoader() == null
                    && terminalResult.originalFactory() == null;
        }

        private void requireStillInstalling() {
            if (state != State.INSTALLING) {
                throw BootstrapFailure.create(
                        pendingFailureCategory == null
                                ? BootstrapFailure.REENTRANT
                                : pendingFailureCategory);
            }
        }
    }

    private static final String SHELL_FACTORY =
            "ah.runtime.bootstrap.ShellAppComponentFactory";
    private static final int MAX_FACTORY_UTF8_BYTES = 512;
    private static final Coordinator PRODUCTION =
            new Coordinator(HardeningBootstrap::openGuardSession, new ReflectionFactoryAdapter());

    private HardeningBootstrap() {}

    static BootstrapResult install(
            ClassLoader shellLoader,
            ApplicationInfo applicationInfo) {
        return PRODUCTION.install(shellLoader, applicationInfo);
    }

    static Coordinator productionCoordinator() {
        return PRODUCTION;
    }

    private static BootstrapSession openGuardSession(
            ClassLoader shellLoader,
            ApplicationInfo applicationInfo) {
        VerifiedPayloadSession session = null;
        boolean committed = false;
        try {
            session = RuntimeStartupGuard.openVerifiedPayload(applicationInfo, shellLoader);
            BootstrapSession wrapped = new GuardBootstrapSession(session);
            committed = true;
            return wrapped;
        } finally {
            if (!committed && session != null) {
                try {
                    session.close();
                } catch (Throwable ignored) {
                    // The Guard failure remains primary; no fallback is permitted.
                }
            }
        }
    }

    private static void validateConfiguration(BootstrapSession session) {
        if (session.containerMajor() != 2
                || session.containerMinor() != 0
                || session.signerPolicyVersion() != 1
                || session.riskPolicyVersion() != 1) {
            throw BootstrapFailure.create(BootstrapFailure.CONFIG_VERSION);
        }
        String factoryName = session.originalFactoryClassNameOrNull();
        if (factoryName == null) {
            return;
        }
        byte[] bytes = factoryName.getBytes(StandardCharsets.UTF_8);
        if (bytes.length == 0
                || bytes.length > MAX_FACTORY_UTF8_BYTES
                || SHELL_FACTORY.equals(factoryName)
                || !isJavaClassName(factoryName)) {
            throw BootstrapFailure.create(BootstrapFailure.CONFIG_FACTORY);
        }
    }

    private static boolean isJavaClassName(String value) {
        boolean segmentStart = true;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '.') {
                if (segmentStart) {
                    return false;
                }
                segmentStart = true;
                continue;
            }
            boolean first = (character >= 'A' && character <= 'Z')
                    || (character >= 'a' && character <= 'z')
                    || character == '_'
                    || character == '$';
            boolean rest = first || (character >= '0' && character <= '9');
            if ((segmentStart && !first) || (!segmentStart && !rest)) {
                return false;
            }
            segmentStart = false;
        }
        return !segmentStart && value.indexOf('.') > 0;
    }

    private static String classify(Throwable failure) {
        if (failure instanceof BootstrapFailure) {
            return ((BootstrapFailure) failure).category();
        }
        if (failure instanceof OutOfMemoryError) {
            return BootstrapFailure.RESOURCE;
        }
        String message;
        try {
            message = failure.getMessage();
        } catch (Throwable ignored) {
            // Throwable is untrusted at this boundary; classification must be total.
            return BootstrapFailure.INTERNAL;
        }
        if (message != null && message.startsWith("AAH-RUNTIME-INTEGRITY-")) {
            return BootstrapFailure.GUARD;
        }
        return BootstrapFailure.INTERNAL;
    }

    private static final class GuardBootstrapSession implements BootstrapSession {
        private VerifiedPayloadSession session;
        private boolean closed;

        GuardBootstrapSession(VerifiedPayloadSession session) {
            if (session == null) {
                throw BootstrapFailure.create(BootstrapFailure.GUARD);
            }
            this.session = session;
        }

        @Override
        public synchronized ClassLoader provisionalClassLoader() {
            return requireOpen().provisionalClassLoader();
        }

        @Override
        public synchronized String originalFactoryClassNameOrNull() {
            return configuration().originalFactoryClassNameOrNull();
        }

        @Override
        public synchronized int containerMajor() {
            return configuration().containerMajor();
        }

        @Override
        public synchronized int containerMinor() {
            return configuration().containerMinor();
        }

        @Override
        public synchronized int signerPolicyVersion() {
            return configuration().signerPolicyVersion();
        }

        @Override
        public synchronized int riskPolicyVersion() {
            return configuration().riskPolicyVersion();
        }

        @Override
        public synchronized VerifiedPayloadSession verifiedSession() {
            return requireOpen();
        }

        @Override
        public synchronized void close() {
            if (closed) {
                return;
            }
            closed = true;
            VerifiedPayloadSession closing = session;
            session = null;
            if (closing != null) {
                closing.close();
            }
        }

        private VerifiedPayloadSession requireOpen() {
            if (closed || session == null) {
                throw BootstrapFailure.create(BootstrapFailure.GUARD);
            }
            return session;
        }

        private VerifiedStartupConfiguration configuration() {
            return requireOpen().startupConfiguration();
        }
    }

    private static final class ReflectionFactoryAdapter implements FactoryAdapter {
        @Override
        public AppComponentFactory create(
                ClassLoader provisionalLoader,
                String factoryClassName) {
            try {
                Class<?> factoryClass = provisionalLoader.loadClass(factoryClassName);
                if (factoryClass.getClassLoader() != provisionalLoader
                        || !AppComponentFactory.class.isAssignableFrom(factoryClass)) {
                    throw BootstrapFailure.create(BootstrapFailure.FACTORY_CONSTRUCT);
                }
                return (AppComponentFactory) factoryClass.getDeclaredConstructor().newInstance();
            } catch (BootstrapFailure failure) {
                throw failure;
            } catch (ClassNotFoundException
                    | NoSuchMethodException
                    | IllegalAccessException
                    | InstantiationException
                    | InvocationTargetException
                    | LinkageError failure) {
                throw BootstrapFailure.create(BootstrapFailure.FACTORY_CONSTRUCT);
            }
        }

        @Override
        public ClassLoader delegate(
                AppComponentFactory factory,
                ClassLoader provisionalLoader,
                ApplicationInfo applicationInfo) {
            try {
                return factory.instantiateClassLoader(provisionalLoader, applicationInfo);
            } catch (Throwable failure) {
                throw BootstrapFailure.create(BootstrapFailure.FACTORY_HOOK);
            }
        }

        @Override
        public void validate(
                ClassLoader finalLoader,
                AppComponentFactory factory,
                String factoryClassName) {
            try {
                if (finalLoader.loadClass(factoryClassName) != factory.getClass()) {
                    throw BootstrapFailure.create(BootstrapFailure.FINAL_LOADER);
                }
            } catch (ClassNotFoundException | LinkageError failure) {
                throw BootstrapFailure.create(BootstrapFailure.FINAL_LOADER);
            }
        }
    }
}
