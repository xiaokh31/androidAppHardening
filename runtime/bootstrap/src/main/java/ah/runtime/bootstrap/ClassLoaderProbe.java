package ah.runtime.bootstrap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Read-only diagnostics for the API 29 ClassLoader feasibility gate. */
public final class ClassLoaderProbe {
    public static final String FACTORY_ENTER = "FACTORY_ENTER";
    public static final String LOADER_CREATED = "LOADER_CREATED";
    public static final String APPLICATION_CREATED = "APPLICATION_CREATED";
    public static final String ACTIVITY_CREATED = "ACTIVITY_CREATED";

    private static final int CAPACITY = 128;
    private static final ProbeEvent[] EVENTS = new ProbeEvent[CAPACITY];

    private static long nextSequence;
    private static int eventCount;
    private static int writeIndex;

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
        EVENTS[writeIndex] =
                new ProbeEvent(++nextSequence, type, componentClassName, classLoader);
        writeIndex = (writeIndex + 1) % CAPACITY;
        if (eventCount < CAPACITY) {
            eventCount++;
        }
    }
}
