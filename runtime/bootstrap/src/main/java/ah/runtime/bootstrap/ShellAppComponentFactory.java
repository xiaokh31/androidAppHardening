package ah.runtime.bootstrap;

import android.app.Activity;
import android.app.AppComponentFactory;
import android.app.Application;
import android.content.pm.ApplicationInfo;
import dalvik.system.InMemoryDexClassLoader;
import java.nio.ByteBuffer;

/**
 * API 29 public-entry proof of concept. This class is not a production runtime implementation.
 */
public final class ShellAppComponentFactory extends AppComponentFactory {
    @Override
    public ClassLoader instantiateClassLoader(
            ClassLoader classLoader,
            ApplicationInfo applicationInfo) {
        ClassLoaderProbe.record(ClassLoaderProbe.FACTORY_ENTER, null, classLoader);
        try {
            ByteBuffer payload = StoredDexReader.read(applicationInfo.sourceDir);
            String librarySearchPath = applicationInfo.nativeLibraryDir;
            ClassLoader result =
                    new InMemoryDexClassLoader(
                            new ByteBuffer[] {payload},
                            librarySearchPath,
                            classLoader);
            ClassLoaderProbe.record(ClassLoaderProbe.LOADER_CREATED, null, result);
            return result;
        } catch (IllegalStateException exception) {
            if (exception.getMessage() != null
                    && exception.getMessage().startsWith(PocFailure.CODE + ":")) {
                throw exception;
            }
            throw PocFailure.create("ClassLoader creation failed", exception);
        } catch (RuntimeException exception) {
            throw PocFailure.create("ClassLoader creation failed", exception);
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
