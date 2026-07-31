package ah.runtime.bootstrap;

import android.util.Log;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Read-only diagnostics for the API 29 ClassLoader feasibility gate. */
public final class ClassLoaderProbe {
    private static final String LOG_TAG = "AAH-M0-04";

    public static final String FACTORY_ENTER = "FACTORY_ENTER";
    public static final String LOADER_CREATED = "LOADER_CREATED";
    public static final String APPLICATION_CREATED = "APPLICATION_CREATED";
    public static final String ACTIVITY_CREATED = "ACTIVITY_CREATED";
    public static final String EARLY_SIGNER_VERIFIED = "EARLY_SIGNER_VERIFIED";
    public static final String EARLY_METADATA_VERIFIED = "EARLY_METADATA_VERIFIED";
    public static final String ORIGINAL_FACTORY_CREATED = "ORIGINAL_FACTORY_CREATED";
    public static final String PROVIDER_CREATED = "PROVIDER_CREATED";
    public static final String SERVICE_CREATED = "SERVICE_CREATED";
    public static final String RECEIVER_CREATED = "RECEIVER_CREATED";
    public static final String APPLICATION_ON_CREATE = "APPLICATION_ON_CREATE";
    public static final String JNI_LOADED = "JNI_LOADED";

    private static final int CAPACITY = 128;
    private static final ProbeEvent[] EVENTS = new ProbeEvent[CAPACITY];

    private static long nextSequence;
    private static int eventCount;
    private static int writeIndex;
    private static byte[] earlySignerSha256;
    private static NativeLibrarySearchPath nativeLibrarySearchPath;

    private ClassLoaderProbe() {}

    public static synchronized List<ProbeEvent> snapshot() {
        List<ProbeEvent> result = new ArrayList<>(eventCount);
        int first = eventCount == CAPACITY ? writeIndex : 0;
        for (int index = 0; index < eventCount; index++) {
            result.add(EVENTS[(first + index) % CAPACITY]);
        }
        return Collections.unmodifiableList(result);
    }

    static synchronized void record(
            String type,
            String componentClassName,
            ClassLoader classLoader) {
        Log.i(LOG_TAG, type);
        EVENTS[writeIndex] =
                new ProbeEvent(++nextSequence, type, componentClassName, classLoader);
        writeIndex = (writeIndex + 1) % CAPACITY;
        if (eventCount < CAPACITY) {
            eventCount++;
        }
    }

    static synchronized void recordEarlySigner(EarlySignerResult result) {
        earlySignerSha256 = result.certificateSha256();
        record(EARLY_SIGNER_VERIFIED, null, null);
    }

    static synchronized void recordNativeLibrarySearchPath(NativeLibrarySearchPath path) {
        nativeLibrarySearchPath = path;
    }

    /** Returns only the certificate digest, never certificate bytes or a source path. */
    public static synchronized byte[] earlySignerSha256() {
        return earlySignerSha256 == null ? null : earlySignerSha256.clone();
    }

    public static synchronized NativeLibrarySearchPath nativeLibrarySearchPath() {
        return nativeLibrarySearchPath;
    }

    /** Called by the synthetic payload fixture after the real Application callback starts. */
    public static void recordApplicationOnCreate(Class<?> applicationClass) {
        record(
                APPLICATION_ON_CREATE,
                applicationClass.getName(),
                applicationClass.getClassLoader());
    }

    /** Called by the synthetic payload fixture only after its fixed JNI marker returns. */
    public static void recordJniLoaded(Class<?> callerClass) {
        record(JNI_LOADED, callerClass.getName(), callerClass.getClassLoader());
    }
}
