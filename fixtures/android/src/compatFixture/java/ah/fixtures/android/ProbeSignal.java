package ah.fixtures.android;

import ah.runtime.bootstrap.ClassLoaderProbe;
import java.util.HashMap;
import java.util.Map;

/** Parent-loader test bridge; contains no payload implementation. */
public final class ProbeSignal {
    private static final Map<String, Integer> FACTORY_COUNTS = new HashMap<>();
    private static String providerMarker;
    private static String activityMarker;
    private static String jniMarker;
    private static boolean failActivityDelegation;
    private static boolean failFactoryConstruction;
    private static String classLoaderHookMode = "normal";

    private ProbeSignal() {}

    public static synchronized void recordFactoryInvocation(String componentType) {
        FACTORY_COUNTS.put(componentType, factoryCount(componentType) + 1);
    }

    public static synchronized int factoryCount(String componentType) {
        Integer count = FACTORY_COUNTS.get(componentType);
        return count == null ? 0 : count;
    }

    public static synchronized void recordProviderMarker(String marker) {
        providerMarker = marker;
    }

    public static synchronized void recordActivityMarker(String marker) {
        activityMarker = marker;
    }

    public static synchronized String providerMarker() {
        return providerMarker;
    }

    public static synchronized String activityMarker() {
        return activityMarker;
    }

    public static synchronized void applicationOnCreate(Class<?> applicationClass) {
        ClassLoaderProbe.recordApplicationOnCreate(applicationClass);
    }

    public static synchronized void jniLoaded(Class<?> callerClass, String marker) {
        jniMarker = marker;
        ClassLoaderProbe.recordJniLoaded(callerClass);
    }

    public static synchronized String jniMarker() {
        return jniMarker;
    }

    public static synchronized void setFailActivityDelegation(boolean fail) {
        failActivityDelegation = fail;
    }

    public static synchronized boolean shouldFailActivityDelegation() {
        return failActivityDelegation;
    }

    public static synchronized void setFailFactoryConstruction(boolean fail) {
        failFactoryConstruction = fail;
    }

    public static synchronized boolean shouldFailFactoryConstruction() {
        return failFactoryConstruction;
    }

    public static synchronized void setClassLoaderHookMode(String mode) {
        classLoaderHookMode = mode;
    }

    public static synchronized String classLoaderHookMode() {
        return classLoaderHookMode;
    }

    public static synchronized void resetFailureInjection() {
        failActivityDelegation = false;
        failFactoryConstruction = false;
        classLoaderHookMode = "normal";
        ClassLoaderProbe.setFailSessionCloseForTesting(false);
    }
}
