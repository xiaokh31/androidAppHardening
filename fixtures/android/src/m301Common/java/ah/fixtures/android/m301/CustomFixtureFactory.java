package ah.fixtures.android.m301;

import android.app.AppComponentFactory;
import android.app.Application;
import android.content.pm.ApplicationInfo;

public final class CustomFixtureFactory extends AppComponentFactory {
    private static volatile String dataDir;

    @Override
    public ClassLoader instantiateClassLoader(ClassLoader classLoader, ApplicationInfo applicationInfo) {
        dataDir = applicationInfo.dataDir;
        FixtureEvents.appendDataDir(dataDir, "factory.classloader");
        return classLoader;
    }

    @Override
    public Application instantiateApplication(ClassLoader classLoader, String className)
            throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        if (dataDir != null) FixtureEvents.appendDataDir(dataDir, "factory.application");
        return super.instantiateApplication(classLoader, className);
    }
}
