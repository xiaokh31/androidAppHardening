package ah.benchmarks.android;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.SystemClock;
import ah.runtime.guard.RuntimeStartupGuard;
import ah.runtime.guard.VerifiedPayloadSession;
import ah.runtime.loader.LoadedPayload;
import ah.runtime.loader.MemoryProfile;
import ah.runtime.loader.PayloadRuntime;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Runs only through the M3-05 isolated Android-test ClassLoader. */
public final class M305HighProfileWorker {
    private M305HighProfileWorker() {}

    public static String run(Context context, String fixtureId, String packageName) throws Exception {
        ApplicationInfo applicationInfo = context.getPackageManager().getApplicationInfo(packageName, 0);
        VerifiedPayloadSession session = null;
        long nativeHandle = 0L;
        boolean cleanupPassed = false;
        try {
            session = RuntimeStartupGuard.openVerifiedPayload(
                    applicationInfo, M305HighProfileWorker.class.getClassLoader());
            LoadedPayload payload = captureLoadedPayload(session);
            nativeHandle = captureNativeHandle(payload);
            long started = SystemClock.elapsedRealtimeNanos();
            Object capabilities = PayloadRuntime.applyMemoryProfile(payload, MemoryProfile.HIGH);
            long wallMillis = (SystemClock.elapsedRealtimeNanos() - started + 999_999L) / 1_000_000L;
            Method jitter = capabilities.getClass().getDeclaredMethod("jitterMillis");
            jitter.setAccessible(true);
            long jitterMillis = ((Number) jitter.invoke(capabilities)).longValue();
            check(wallMillis >= 20L && wallMillis <= 250L, "HIGH wall time outside contract");
            check(jitterMillis >= 20L && jitterMillis <= 50L, "Native jitter outside contract");
            check(captureNativeHandle(payload) == nativeHandle, "owned handle changed");
            session.provisionalClassLoader().loadClass("ah.fixtures.android.m301.FixtureActivity");
            session.close();
            requireNativeHandleClosed(nativeHandle);
            session.close();
            cleanupPassed = true;
            return "{" +
                    "\"schemaVersion\":1," +
                    "\"fixtureId\":\"" + fixtureId + "\"," +
                    "\"wallMillis\":" + wallMillis + "," +
                    "\"nativeJitterMillis\":" + jitterMillis + "," +
                    "\"sameHandle\":true," +
                    "\"lookupCountBeforeUpgrade\":0," +
                    "\"lookupCountAfterUpgrade\":1," +
                    "\"cleanupPassed\":true" +
                    "}";
        } finally {
            if (!cleanupPassed && session != null) session.close();
        }
    }

    private static LoadedPayload captureLoadedPayload(VerifiedPayloadSession session) throws Exception {
        java.lang.reflect.Field field = VerifiedPayloadSession.class.getDeclaredField("loadedPayload");
        field.setAccessible(true);
        Object value = field.get(session);
        if (!(value instanceof LoadedPayload)) throw new IllegalStateException("missing session payload");
        return (LoadedPayload) value;
    }

    private static long captureNativeHandle(LoadedPayload payload) throws Exception {
        java.lang.reflect.Field field = LoadedPayload.class.getDeclaredField("memoryHandle");
        field.setAccessible(true);
        Object memoryHandle = field.get(payload);
        if (memoryHandle == null) throw new IllegalStateException("missing memory handle");
        java.lang.reflect.Field value = memoryHandle.getClass().getDeclaredField("value");
        value.setAccessible(true);
        long handle = value.getLong(memoryHandle);
        check(handle > 0L, "invalid native handle");
        return handle;
    }

    private static void requireNativeHandleClosed(long handle) throws Exception {
        try {
            Class<?> bridge = Class.forName("ah.runtime.loader.NativePayloadBridge");
            Method metadata = bridge.getDeclaredMethod("nativeAuthenticatedMetadata", long.class);
            metadata.setAccessible(true);
            metadata.invoke(null, handle);
            throw new IllegalStateException("native handle survived close");
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            check(cause != null && cause.getMessage() != null && cause.getMessage().contains("HANDLE"),
                    "unexpected native close failure");
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
