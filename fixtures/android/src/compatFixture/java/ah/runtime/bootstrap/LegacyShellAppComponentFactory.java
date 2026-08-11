package ah.runtime.bootstrap;

import android.app.Activity;
import android.app.AppComponentFactory;
import android.app.Application;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentProvider;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import dalvik.system.InMemoryDexClassLoader;
import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;

/** M0-05 fixture-only compatibility proof; never used by the production runtime bootstrap. */
public final class LegacyShellAppComponentFactory extends AppComponentFactory {
    private EarlyConfigResult startupConfig;
    private AppComponentFactory originalFactory;
    private ClassLoader provisionalClassLoader;
    private ClassLoader payloadClassLoader;
    private PocPayloadSession retainedSession;
    private boolean startupInProgress;
    private String cachedFailureCode;
    private String cachedFailureDetail;
    private int lastFailedSessionCloseCount;
    private boolean lastFailedBuffersCleared;
    private boolean lastFailureReferencesCleared = true;

    @Override
    public synchronized ClassLoader instantiateClassLoader(
            ClassLoader classLoader,
            ApplicationInfo applicationInfo) {
        ClassLoaderProbe.record(ClassLoaderProbe.FACTORY_ENTER, null, classLoader);
        if (payloadClassLoader != null) {
            return payloadClassLoader;
        }
        if (cachedFailureCode != null) {
            throw PocFailure.create(cachedFailureCode, cachedFailureDetail);
        }
        if (startupInProgress) {
            throw PocFailure.create(
                    PocFailure.DELEGATE_CODE,
                    "recursive or reentrant Shell Factory startup was rejected");
        }

        startupInProgress = true;
        PocPayloadSession session = null;
        boolean ready = false;
        try {
            EarlySignerResult signer = EarlySignerProbe.verify(applicationInfo);
            ClassLoaderProbe.recordEarlySigner(signer);

            EarlyConfigResult config = EarlyConfigProbe.open(applicationInfo, signer);
            NativeLibrarySearchPath nativeSearchPath =
                    NativeLibrarySearchPathResolver.resolve(applicationInfo);
            ClassLoaderProbe.recordNativeLibrarySearchPath(nativeSearchPath);

            ByteBuffer[] payload = StoredDexReader.readContainer(applicationInfo.sourceDir);
            ClassLoader provisional =
                    new InMemoryDexClassLoader(
                            payload,
                            nativeSearchPath.classLoaderSearchPath(),
                            classLoader);
            ClassLoaderProbe.record(
                    ClassLoaderProbe.PROVISIONAL_LOADER_CREATED,
                    null,
                    provisional);
            session = new PocPayloadSession(payload, provisional);

            AppComponentFactory factory = null;
            ClassLoader finalLoader = provisional;
            if (config.hasOriginalFactory()) {
                factory = instantiateOriginalFactory(provisional, config.originalFactory());
                session.setOriginalFactory(factory);
                finalLoader =
                        delegateOriginalFactoryClassLoader(
                                factory,
                                provisional,
                                applicationInfo);
                session.setFinalLoader(finalLoader);
                validateFinalLoader(finalLoader, factory, config.originalFactory());
            }

            ClassLoaderProbe.record(ClassLoaderProbe.LOADER_CREATED, null, finalLoader);
            session.transferReady();
            startupConfig = config;
            originalFactory = factory;
            provisionalClassLoader = provisional;
            payloadClassLoader = finalLoader;
            retainedSession = session;
            ready = true;
            return finalLoader;
        } catch (RuntimeException | LinkageError failure) {
            IllegalStateException stable = normalizeStartupFailure(failure);
            cacheFailure(stable);
            throw stable;
        } finally {
            ClassLoaderProbe.clearOriginalFactoryHookForTesting();
            if (!ready && session != null) {
                try {
                    session.close();
                } catch (RuntimeException ignored) {
                    // Cleanup failure must never replace the primary startup failure.
                }
                lastFailedSessionCloseCount = session.closeCount();
                lastFailedBuffersCleared = session.buffersCleared();
                lastFailureReferencesCleared = !session.hasPartialReferences();
            }
            startupInProgress = false;
        }
    }

    @Override
    public Application instantiateApplication(ClassLoader classLoader, String className)
            throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        requireInstalled(classLoader);
        Application application;
        if (originalFactory == null) {
            application = super.instantiateApplication(classLoader, className);
        } else {
            try {
                application = originalFactory.instantiateApplication(classLoader, className);
            } catch (ClassNotFoundException
                    | IllegalAccessException
                    | InstantiationException
                    | RuntimeException
                    | LinkageError failure) {
                throw markDelegated(failure);
            }
            requireDelegatedComponent(application);
        }
        ClassLoaderProbe.record(
                ClassLoaderProbe.APPLICATION_CREATED,
                className,
                application.getClass().getClassLoader());
        return application;
    }

    @Override
    public Activity instantiateActivity(
            ClassLoader classLoader,
            String className,
            Intent intent)
            throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        requireInstalled(classLoader);
        Activity activity;
        if (originalFactory == null) {
            activity = super.instantiateActivity(classLoader, className, intent);
        } else {
            try {
                activity = originalFactory.instantiateActivity(classLoader, className, intent);
            } catch (ClassNotFoundException
                    | IllegalAccessException
                    | InstantiationException
                    | RuntimeException
                    | LinkageError failure) {
                throw markDelegated(failure);
            }
            requireDelegatedComponent(activity);
        }
        ClassLoaderProbe.record(
                ClassLoaderProbe.ACTIVITY_CREATED,
                className,
                activity.getClass().getClassLoader());
        return activity;
    }

    @Override
    public Service instantiateService(ClassLoader classLoader, String className, Intent intent)
            throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        requireInstalled(classLoader);
        Service service;
        if (originalFactory == null) {
            service = super.instantiateService(classLoader, className, intent);
        } else {
            try {
                service = originalFactory.instantiateService(classLoader, className, intent);
            } catch (ClassNotFoundException
                    | IllegalAccessException
                    | InstantiationException
                    | RuntimeException
                    | LinkageError failure) {
                throw markDelegated(failure);
            }
            requireDelegatedComponent(service);
        }
        ClassLoaderProbe.record(
                ClassLoaderProbe.SERVICE_CREATED,
                className,
                service.getClass().getClassLoader());
        return service;
    }

    @Override
    public BroadcastReceiver instantiateReceiver(
            ClassLoader classLoader,
            String className,
            Intent intent)
            throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        requireInstalled(classLoader);
        BroadcastReceiver receiver;
        if (originalFactory == null) {
            receiver = super.instantiateReceiver(classLoader, className, intent);
        } else {
            try {
                receiver = originalFactory.instantiateReceiver(classLoader, className, intent);
            } catch (ClassNotFoundException
                    | IllegalAccessException
                    | InstantiationException
                    | RuntimeException
                    | LinkageError failure) {
                throw markDelegated(failure);
            }
            requireDelegatedComponent(receiver);
        }
        ClassLoaderProbe.record(
                ClassLoaderProbe.RECEIVER_CREATED,
                className,
                receiver.getClass().getClassLoader());
        return receiver;
    }

    @Override
    public ContentProvider instantiateProvider(ClassLoader classLoader, String className)
            throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        requireInstalled(classLoader);
        ContentProvider provider;
        if (originalFactory == null) {
            provider = super.instantiateProvider(classLoader, className);
        } else {
            try {
                provider = originalFactory.instantiateProvider(classLoader, className);
            } catch (ClassNotFoundException
                    | IllegalAccessException
                    | InstantiationException
                    | RuntimeException
                    | LinkageError failure) {
                throw markDelegated(failure);
            }
            requireDelegatedComponent(provider);
        }
        ClassLoaderProbe.record(
                ClassLoaderProbe.PROVIDER_CREATED,
                className,
                provider.getClass().getClassLoader());
        return provider;
    }

    /** Synthetic fixture diagnostic; never used to make a production startup decision. */
    public synchronized int testOnlyLastFailedSessionCloseCount() {
        return lastFailedSessionCloseCount;
    }

    /** Synthetic fixture diagnostic; never used to make a production startup decision. */
    public synchronized boolean testOnlyLastFailedBuffersCleared() {
        return lastFailedBuffersCleared;
    }

    /** Synthetic fixture diagnostic; reports only reference ownership, never objects. */
    public synchronized boolean testOnlyFailureReferencesCleared() {
        return lastFailureReferencesCleared
                && startupConfig == null
                && originalFactory == null
                && provisionalClassLoader == null
                && payloadClassLoader == null
                && retainedSession == null;
    }

    private AppComponentFactory instantiateOriginalFactory(
            ClassLoader classLoader,
            String className) {
        try {
            Class<?> factoryClass = classLoader.loadClass(className);
            if (!AppComponentFactory.class.isAssignableFrom(factoryClass)) {
                throw PocFailure.create(
                        PocFailure.FACTORY_CODE,
                        "configured original Factory has the wrong base type");
            }
            AppComponentFactory factory =
                    (AppComponentFactory) factoryClass.getDeclaredConstructor().newInstance();
            ClassLoaderProbe.record(
                    ClassLoaderProbe.ORIGINAL_FACTORY_CREATED,
                    className,
                    factory.getClass().getClassLoader());
            return factory;
        } catch (ClassNotFoundException
                | NoSuchMethodException
                | IllegalAccessException
                | InstantiationException
                | InvocationTargetException
                | LinkageError exception) {
            throw PocFailure.create(
                    PocFailure.FACTORY_CODE,
                    "configured original Factory cannot be created");
        }
    }

    private ClassLoader delegateOriginalFactoryClassLoader(
            AppComponentFactory factory,
            ClassLoader provisional,
            ApplicationInfo applicationInfo) {
        final ClassLoader delegated;
        ClassLoaderProbe.setOriginalFactoryHookForTesting(this, provisional, applicationInfo);
        try {
            delegated = factory.instantiateClassLoader(provisional, applicationInfo);
        } catch (RuntimeException | LinkageError failure) {
            throw PocFailure.create(
                    PocFailure.DELEGATE_CODE,
                    "original Factory ClassLoader hook failed",
                    failure);
        } finally {
            ClassLoaderProbe.clearOriginalFactoryHookForTesting();
        }
        if (delegated == null) {
            throw PocFailure.create(
                    PocFailure.DELEGATE_CODE,
                    "original Factory ClassLoader hook returned null");
        }
        ClassLoaderProbe.record(
                ClassLoaderProbe.ORIGINAL_FACTORY_CLASSLOADER_DELEGATED,
                factory.getClass().getName(),
                delegated);
        return delegated;
    }

    private static void validateFinalLoader(
            ClassLoader finalLoader,
            AppComponentFactory factory,
            String factoryName) {
        try {
            if (finalLoader.loadClass(factoryName) != factory.getClass()) {
                throw PocFailure.create(
                        PocFailure.DELEGATE_CODE,
                        "final loader resolves a different original Factory class");
            }
        } catch (ClassNotFoundException | LinkageError failure) {
            throw PocFailure.create(
                    PocFailure.DELEGATE_CODE,
                    "final loader cannot resolve the original Factory",
                    failure);
        }
    }

    private void requireInstalled(ClassLoader classLoader) {
        if (startupConfig == null
                || payloadClassLoader == null
                || retainedSession == null
                || !retainedSession.isReady()
                || classLoader != payloadClassLoader) {
            throw PocFailure.create(
                    PocFailure.FACTORY_CODE,
                    "component creation occurred before a verified final loader was installed");
        }
    }

    private void cacheFailure(IllegalStateException failure) {
        String message = failure.getMessage();
        int separator = message == null ? -1 : message.indexOf(": ");
        if (separator <= 0) {
            cachedFailureCode = PocFailure.PAYLOAD_CODE;
            cachedFailureDetail = "startup failed";
        } else {
            cachedFailureCode = message.substring(0, separator);
            cachedFailureDetail = message.substring(separator + 2);
        }
    }

    private static IllegalStateException normalizeStartupFailure(Throwable failure) {
        if (failure instanceof IllegalStateException && isStablePocFailure(failure)) {
            return (IllegalStateException) failure;
        }
        return PocFailure.create(PocFailure.PAYLOAD_CODE, "startup failed");
    }

    private static void requireDelegatedComponent(Object component) throws InstantiationException {
        if (component == null) {
            throw new InstantiationException(
                    PocFailure.DELEGATE_CODE + ": original Factory returned a null component");
        }
    }

    private static InstantiationException markDelegated(Throwable failure) {
        InstantiationException marked =
                new InstantiationException(PocFailure.DELEGATE_CODE + ": original Factory failed");
        marked.initCause(failure);
        return marked;
    }

    private static boolean isStablePocFailure(Throwable failure) {
        return PocFailure.hasCode(failure, PocFailure.PAYLOAD_CODE)
                || PocFailure.hasCode(failure, PocFailure.FACTORY_CODE)
                || PocFailure.hasCode(failure, PocFailure.DELEGATE_CODE)
                || PocFailure.hasCode(failure, PocFailure.JNI_CODE)
                || PocFailure.hasCode(failure, PocFailure.SIGNER_UNREADABLE_CODE)
                || PocFailure.hasCode(failure, PocFailure.SIGNER_INVALID_CODE)
                || PocFailure.hasCode(failure, PocFailure.SIGNER_NON_UNIQUE_CODE)
                || PocFailure.hasCode(failure, PocFailure.SIGNER_MISMATCH_CODE)
                || PocFailure.hasCode(failure, PocFailure.CONFIG_CODE)
                || PocFailure.hasCode(failure, PocFailure.CONFIG_AUTH_CODE);
    }
}
