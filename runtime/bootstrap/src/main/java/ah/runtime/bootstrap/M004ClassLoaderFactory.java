package ah.runtime.bootstrap;

import android.app.Activity;
import android.app.AppComponentFactory;
import android.app.Application;
import android.content.pm.ApplicationInfo;
import dalvik.system.InMemoryDexClassLoader;
import java.nio.ByteBuffer;

/** Preserves the already-accepted M0-04 fixture while M0-05 evolves the frozen shell name. */
public final class M004ClassLoaderFactory extends AppComponentFactory {
    @Override
    public ClassLoader instantiateClassLoader(
            ClassLoader classLoader,
            ApplicationInfo applicationInfo) {
        ClassLoaderProbe.record(ClassLoaderProbe.FACTORY_ENTER, null, classLoader);
        try {
            ByteBuffer payload = StoredDexReader.read(applicationInfo.sourceDir);
            ClassLoader result =
                    new InMemoryDexClassLoader(
                            new ByteBuffer[] {payload},
                            applicationInfo.nativeLibraryDir,
                            classLoader);
            ClassLoaderProbe.record(ClassLoaderProbe.LOADER_CREATED, null, result);
            return result;
        } catch (RuntimeException exception) {
            if (PocFailure.isPocFailure(exception)) {
                throw exception;
            }
            throw PocFailure.create("ClassLoader creation failed");
        }
    }

    @Override
    public Application instantiateApplication(ClassLoader classLoader, String className)
            throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        Application application = super.instantiateApplication(classLoader, className);
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
            android.content.Intent intent)
            throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        Activity activity = super.instantiateActivity(classLoader, className, intent);
        ClassLoaderProbe.record(
                ClassLoaderProbe.ACTIVITY_CREATED,
                className,
                activity.getClass().getClassLoader());
        return activity;
    }
}
