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
    @Override
    public Application instantiateApplication(ClassLoader loader, String className)
            throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        ProbeSignal.recordFactoryInvocation("application");
        return super.instantiateApplication(loader, className);
    }

    @Override
    public Activity instantiateActivity(ClassLoader loader, String className, Intent intent)
            throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        ProbeSignal.recordFactoryInvocation("activity");
        if (ProbeSignal.shouldFailActivityDelegation()) {
            throw new InstantiationException("synthetic delegated activity failure");
        }
        return super.instantiateActivity(loader, className, intent);
    }

    @Override
    public Service instantiateService(ClassLoader loader, String className, Intent intent)
            throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        ProbeSignal.recordFactoryInvocation("service");
        return super.instantiateService(loader, className, intent);
    }

    @Override
    public BroadcastReceiver instantiateReceiver(
            ClassLoader loader,
            String className,
            Intent intent)
            throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        ProbeSignal.recordFactoryInvocation("receiver");
        return super.instantiateReceiver(loader, className, intent);
    }

    @Override
    public ContentProvider instantiateProvider(ClassLoader loader, String className)
            throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        ProbeSignal.recordFactoryInvocation("provider");
        return super.instantiateProvider(loader, className);
    }
}
