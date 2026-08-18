package ah.runtime.profile;

import android.os.Process;
import android.os.SystemClock;
import java.util.Arrays;

/** Test-only monotonic observer included exclusively in the M2-10 profiling variant. */
public final class M210StartupTimingObserver {
    private static final int POINT_COUNT = 7;
    private static final long[] POINTS = new long[POINT_COUNT];
    private static int nextIndex;
    private static int ownerPid = -1;
    private static boolean invalid;

    private M210StartupTimingObserver() {}

    public static synchronized void mark(int index) {
        if (nextIndex == POINT_COUNT) {
            return;
        }
        if (invalid || index != nextIndex) {
            invalid = true;
            return;
        }
        int currentPid = Process.myPid();
        if (index == 0) {
            ownerPid = currentPid;
        } else if (currentPid != ownerPid) {
            invalid = true;
            return;
        }
        long timestamp = SystemClock.elapsedRealtimeNanos();
        if (index > 0 && timestamp < POINTS[index - 1]) {
            invalid = true;
            return;
        }
        POINTS[index] = timestamp;
        nextIndex++;
    }

    public static synchronized long[] snapshot() {
        if (invalid || nextIndex != POINT_COUNT) {
            throw new IllegalStateException("M2-10 startup timing sequence is incomplete");
        }
        return Arrays.copyOf(POINTS, POINTS.length);
    }
}
