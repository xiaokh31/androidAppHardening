package ah.fixtures.android.payload;

import ah.fixtures.android.ProbeSignal;
import android.app.Activity;
import android.app.AppComponentFactory;
import android.app.Application;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentProvider;
import android.content.Intent;

public final class OriginalAppComponentFactory extends AppComponentFactory {
    public OriginalAppComponentFactory() {
        if (ProbeSignal.shouldFailFactoryConstruction()) {
            throw new IllegalStateException("synthetic original Factory construction failure");
        }
    }

    @Override
    public ClassLoader instantiateClassLoader(
            ClassLoader loader,
            android.content.pm.ApplicationInfo applicationInfo) {
        ProbeSignal.recordFactoryInvocation("classloader");
        String mode = ProbeSignal.classLoaderHookMode();
        if ("null".equals(mode)) {
            return null;
        }
        if ("exception".equals(mode)) {
            throw new IllegalStateException("synthetic original Factory ClassLoader failure");
        }
        if ("reentry".equals(mode)) {
            return ah.runtime.bootstrap.ClassLoaderProbe.reenterOriginalFactoryHookForTesting();
        }
        if ("invalid-final".equals(mode)) {
            return loader.getParent();
        }
        return loader;
    }

    @Override
    public Application instantiateApplication(ClassLoader loader, String className)
            throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        ProbeSignal.recordFactoryInvocation("application");
        if (shouldReturnNull("application")) {
            return null;
        }
        throwIfConfigured("application");
        return super.instantiateApplication(loader, className);
    }

    @Override
    public Activity instantiateActivity(ClassLoader loader, String className, Intent intent)
            throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        ProbeSignal.recordFactoryInvocation("activity");
        if (ProbeSignal.shouldFailActivityDelegation()) {
            throw new InstantiationException("synthetic delegated activity failure");
        }
        if (shouldReturnNull("activity")) {
            return null;
        }
        throwIfConfigured("activity");
        return super.instantiateActivity(loader, className, intent);
    }

    @Override
    public Service instantiateService(ClassLoader loader, String className, Intent intent)
            throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        ProbeSignal.recordFactoryInvocation("service");
        if (shouldReturnNull("service")) {
            return null;
        }
        throwIfConfigured("service");
        return super.instantiateService(loader, className, intent);
    }

    @Override
    public BroadcastReceiver instantiateReceiver(
            ClassLoader loader,
            String className,
            Intent intent)
            throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        ProbeSignal.recordFactoryInvocation("receiver");
        if (shouldReturnNull("receiver")) {
            return null;
        }
        throwIfConfigured("receiver");
        return super.instantiateReceiver(loader, className, intent);
    }

    @Override
    public ContentProvider instantiateProvider(ClassLoader loader, String className)
            throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        ProbeSignal.recordFactoryInvocation("provider");
        if (shouldReturnNull("provider")) {
            return null;
        }
        throwIfConfigured("provider");
        return super.instantiateProvider(loader, className);
    }

    private static boolean shouldReturnNull(String component) {
        return (component + ":null").equals(ProbeSignal.componentDelegationMode());
    }

    private static void throwIfConfigured(String component) {
        String mode = ProbeSignal.componentDelegationMode();
        if ((component + ":runtime").equals(mode)) {
            throw new IllegalStateException("synthetic delegated " + component + " runtime failure");
        }
        if ((component + ":linkage").equals(mode)) {
            throw new LinkageError("synthetic delegated " + component + " linkage failure");
        }
    }
}
