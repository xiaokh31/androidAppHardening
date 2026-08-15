package ah.runtime.bootstrap;

import android.app.Activity;
import android.app.AppComponentFactory;
import android.app.Application;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentProvider;
import android.content.Intent;
import android.content.pm.ApplicationInfo;

/** Public API 29 entry point for the authenticated in-memory runtime. */
public final class ShellAppComponentFactory extends AppComponentFactory {
    private final HardeningBootstrap.Coordinator coordinator;
    private volatile BootstrapResult installed;

    public ShellAppComponentFactory() {
        this(HardeningBootstrap.productionCoordinator());
    }

    ShellAppComponentFactory(HardeningBootstrap.Coordinator coordinator) {
        if (coordinator == null) {
            throw BootstrapFailure.create(BootstrapFailure.ARGUMENT);
        }
        this.coordinator = coordinator;
    }

    @Override
    public ClassLoader instantiateClassLoader(
            ClassLoader shellLoader,
            ApplicationInfo applicationInfo) {
        BootstrapResult result = coordinator.install(shellLoader, applicationInfo);
        if (result.status() != BootstrapResult.Status.READY) {
            throw result.failure();
        }
        installed = result;
        return result.finalClassLoader();
    }

    @Override
    public Application instantiateApplication(ClassLoader loader, String className)
            throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        BootstrapResult result = requireReady(loader);
        AppComponentFactory delegate = result.originalFactory();
        if (delegate == null) {
            return requireComponent(super.instantiateApplication(loader, className));
        }
        try {
            return requireComponent(delegate.instantiateApplication(loader, className));
        } catch (Throwable failure) {
            throw componentFailure();
        }
    }

    @Override
    public Activity instantiateActivity(ClassLoader loader, String className, Intent intent)
            throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        BootstrapResult result = requireReady(loader);
        AppComponentFactory delegate = result.originalFactory();
        if (delegate == null) {
            return requireComponent(super.instantiateActivity(loader, className, intent));
        }
        try {
            return requireComponent(delegate.instantiateActivity(loader, className, intent));
        } catch (Throwable failure) {
            throw componentFailure();
        }
    }

    @Override
    public Service instantiateService(ClassLoader loader, String className, Intent intent)
            throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        BootstrapResult result = requireReady(loader);
        AppComponentFactory delegate = result.originalFactory();
        if (delegate == null) {
            return requireComponent(super.instantiateService(loader, className, intent));
        }
        try {
            return requireComponent(delegate.instantiateService(loader, className, intent));
        } catch (Throwable failure) {
            throw componentFailure();
        }
    }

    @Override
    public BroadcastReceiver instantiateReceiver(
            ClassLoader loader,
            String className,
            Intent intent)
            throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        BootstrapResult result = requireReady(loader);
        AppComponentFactory delegate = result.originalFactory();
        if (delegate == null) {
            return requireComponent(super.instantiateReceiver(loader, className, intent));
        }
        try {
            return requireComponent(delegate.instantiateReceiver(loader, className, intent));
        } catch (Throwable failure) {
            throw componentFailure();
        }
    }

    @Override
    public ContentProvider instantiateProvider(ClassLoader loader, String className)
            throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        BootstrapResult result = requireReady(loader);
        AppComponentFactory delegate = result.originalFactory();
        if (delegate == null) {
            return requireComponent(super.instantiateProvider(loader, className));
        }
        try {
            return requireComponent(delegate.instantiateProvider(loader, className));
        } catch (Throwable failure) {
            throw componentFailure();
        }
    }

    private BootstrapResult requireReady(ClassLoader loader) {
        BootstrapResult result = installed;
        if (result == null) {
            result = coordinator.readyResult();
        }
        if (result == null
                || loader == null
                || loader != result.finalClassLoader()) {
            throw BootstrapFailure.create(BootstrapFailure.COMPONENT);
        }
        installed = result;
        return result;
    }

    private static <T> T requireComponent(T component) throws InstantiationException {
        if (component == null) {
            throw componentFailure();
        }
        return component;
    }

    private static InstantiationException componentFailure() {
        return new InstantiationException(BootstrapFailure.message(BootstrapFailure.COMPONENT));
    }
}
