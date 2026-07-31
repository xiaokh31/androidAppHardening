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

/** M0-05 public-API compatibility proof; not the production runtime bootstrap. */
public final class ShellAppComponentFactory extends AppComponentFactory {
    private StartupMetadata startupMetadata;
    private AppComponentFactory originalFactory;
    private ClassLoader payloadClassLoader;

    @Override
    public synchronized ClassLoader instantiateClassLoader(
            ClassLoader classLoader,
            ApplicationInfo applicationInfo) {
        ClassLoaderProbe.record(ClassLoaderProbe.FACTORY_ENTER, null, classLoader);
        if (payloadClassLoader != null) {
            return payloadClassLoader;
        }

        EarlySignerResult signer = EarlySignerProbe.verify(applicationInfo);
        ClassLoaderProbe.recordEarlySigner(signer);

        StartupMetadata metadata = StartupMetadata.read(applicationInfo);
        ClassLoaderProbe.record(ClassLoaderProbe.EARLY_METADATA_VERIFIED, null, null);

        NativeLibrarySearchPath nativeSearchPath =
                NativeLibrarySearchPathResolver.resolve(applicationInfo);
        ClassLoaderProbe.recordNativeLibrarySearchPath(nativeSearchPath);

        final ClassLoader newPayloadLoader;
        try {
            ByteBuffer[] payload = StoredDexReader.readContainer(applicationInfo.sourceDir);
            newPayloadLoader =
                    new InMemoryDexClassLoader(
                            payload,
                            nativeSearchPath.classLoaderSearchPath(),
                            classLoader);
        } catch (RuntimeException exception) {
            if (isStablePocFailure(exception)) {
                throw exception;
            }
            throw PocFailure.create("ClassLoader creation failed");
        }

        ClassLoaderProbe.record(ClassLoaderProbe.LOADER_CREATED, null, newPayloadLoader);
        AppComponentFactory newOriginalFactory =
                metadata.hasOriginalFactory
                        ? instantiateOriginalFactory(newPayloadLoader, metadata.originalFactory)
                        : null;
        startupMetadata = metadata;
        originalFactory = newOriginalFactory;
        payloadClassLoader = newPayloadLoader;
        return newPayloadLoader;
    }

    @Override
    public Application instantiateApplication(ClassLoader classLoader, String className)
            throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        StartupMetadata metadata = requireInstalled(classLoader);
        Application application;
        if (originalFactory == null) {
            application = super.instantiateApplication(classLoader, metadata.originalApplication);
        } else {
            try {
                application =
                        originalFactory.instantiateApplication(
                                classLoader,
                                metadata.originalApplication);
            } catch (ClassNotFoundException | IllegalAccessException | InstantiationException failure) {
                throw markDelegated(failure);
            }
        }
        ClassLoaderProbe.record(
                ClassLoaderProbe.APPLICATION_CREATED,
                metadata.originalApplication,
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
            } catch (ClassNotFoundException | IllegalAccessException | InstantiationException failure) {
                throw markDelegated(failure);
            }
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
            } catch (ClassNotFoundException | IllegalAccessException | InstantiationException failure) {
                throw markDelegated(failure);
            }
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
            } catch (ClassNotFoundException | IllegalAccessException | InstantiationException failure) {
                throw markDelegated(failure);
            }
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
            } catch (ClassNotFoundException | IllegalAccessException | InstantiationException failure) {
                throw markDelegated(failure);
            }
        }
        ClassLoaderProbe.record(
                ClassLoaderProbe.PROVIDER_CREATED,
                className,
                provider.getClass().getClassLoader());
        return provider;
    }

    private AppComponentFactory instantiateOriginalFactory(
            ClassLoader classLoader,
            String className) {
        try {
            Class<?> factoryClass = classLoader.loadClass(className);
            if (!AppComponentFactory.class.isAssignableFrom(factoryClass)) {
                throw PocFailure.create(
                        PocFailure.FACTORY_CODE,
                        "configured original factory has the wrong base type");
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
                    "configured original factory cannot be created");
        }
    }

    private StartupMetadata requireInstalled(ClassLoader classLoader) {
        if (startupMetadata == null
                || payloadClassLoader == null
                || classLoader != payloadClassLoader) {
            throw PocFailure.create(
                    PocFailure.FACTORY_CODE,
                    "component creation occurred before a verified loader was installed");
        }
        return startupMetadata;
    }

    private static InstantiationException markDelegated(Exception failure) {
        InstantiationException marked =
                new InstantiationException(PocFailure.DELEGATE_CODE + ": original factory failed");
        marked.initCause(failure);
        return marked;
    }

    private static boolean isStablePocFailure(RuntimeException exception) {
        return PocFailure.isPocFailure(exception)
                || PocFailure.hasCode(exception, PocFailure.FACTORY_CODE)
                || PocFailure.hasCode(exception, PocFailure.JNI_CODE)
                || PocFailure.hasCode(exception, PocFailure.SIGNER_UNREADABLE_CODE)
                || PocFailure.hasCode(exception, PocFailure.SIGNER_INVALID_CODE)
                || PocFailure.hasCode(exception, PocFailure.SIGNER_NON_UNIQUE_CODE)
                || PocFailure.hasCode(exception, PocFailure.SIGNER_MISMATCH_CODE)
                || PocFailure.hasCode(exception, PocFailure.METADATA_CODE);
    }
}
