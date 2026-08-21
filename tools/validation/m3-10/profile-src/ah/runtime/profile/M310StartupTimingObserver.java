package ah.runtime.profile;

import android.os.Process;
import android.os.SystemClock;
import android.util.Log;

/** Test-only observer inserted post-build into the two M3-10 profile APKs. */
public final class M310StartupTimingObserver {
    private static final String TAG = "AAH-M3-10";
    private static final long[] OUTER = new long[16];
    private static final long[] INNER = new long[9];
    private static final long[] CALIBRATION = new long[15];
    private static final long[] CALIBRATION_SINK = new long[15];
    private static int nextOuter = 1;
    private static int nextInner;
    private static int nextCalibration;
    private static int calibrationPid = -1;
    private static int ownerPid = -1;
    private static boolean invalid;
    private static boolean calibrationInvalid;
    private static boolean emitted;

    private M310StartupTimingObserver() {}

    public static void p1() { outer(1); }
    public static void p2() { outer(2); }
    public static void p3() { outer(3); }
    public static void p4() { outer(4); }
    public static void p5() { outer(5); }
    public static void p6() { outer(6); }
    public static void p7() { outer(7); }
    public static void p8() { outer(8); }
    public static void p9() { outer(9); }
    public static void p10() { outer(10); }
    public static void p11() { outer(11); }
    public static void p12() { outer(12); }
    public static void p13() { outer(13); }
    public static void p14() { outer(14); }

    public static void h0() { inner(0); }
    public static void h1() { inner(1); }
    public static void h2() { inner(2); }
    public static void h3() { inner(3); }
    public static void h4() { inner(4); }
    public static void h5() { inner(5); }
    public static void h6() { inner(6); }
    public static void h7() { inner(7); }
    public static void h8() { inner(8); }

    public static void p15(boolean focused) {
        if (!focused) {
            return;
        }
        synchronized (M310StartupTimingObserver.class) {
            if (emitted) {
                return;
            }
            markOuterLocked(15);
            emitted = true;
        }
        for (int index = 0; index < CALIBRATION.length; index++) {
            long before = SystemClock.elapsedRealtimeNanos();
            calibrationPoint(index);
            long after = SystemClock.elapsedRealtimeNanos();
            CALIBRATION[index] = after - before;
        }
        Log.i(TAG, "m3_10_profile=" + snapshotJson());
    }

    private static synchronized void outer(int index) {
        markOuterLocked(index);
    }

    private static void markOuterLocked(int index) {
        int pid = Process.myPid();
        if (ownerPid < 0) {
            ownerPid = pid;
            OUTER[0] = Process.getStartElapsedRealtime() * 1_000_000L;
        }
        long now = SystemClock.elapsedRealtimeNanos();
        if (invalid || pid != ownerPid || index != nextOuter || now < OUTER[index - 1]) {
            invalid = true;
            return;
        }
        OUTER[index] = now;
        nextOuter++;
    }

    private static synchronized void inner(int index) {
        int pid = Process.myPid();
        if (ownerPid < 0) {
            ownerPid = pid;
            OUTER[0] = Process.getStartElapsedRealtime() * 1_000_000L;
        }
        long now = SystemClock.elapsedRealtimeNanos();
        if (invalid || pid != ownerPid || index != nextInner ||
                (index > 0 && now < INNER[index - 1])) {
            invalid = true;
            return;
        }
        INNER[index] = now;
        nextInner++;
    }

    private static synchronized void calibrationPoint(int index) {
        int pid = Process.myPid();
        long now = SystemClock.elapsedRealtimeNanos();
        if (calibrationPid < 0) {
            calibrationPid = pid;
        }
        if (calibrationInvalid || pid != calibrationPid || index != nextCalibration ||
                (index > 0 && now < CALIBRATION_SINK[index - 1])) {
            calibrationInvalid = true;
            return;
        }
        CALIBRATION_SINK[index] = now;
        nextCalibration++;
    }

    private static synchronized String snapshotJson() {
        boolean protectedPath = nextInner != 0;
        if (invalid || calibrationInvalid || nextCalibration != CALIBRATION.length ||
                nextOuter != OUTER.length || (protectedPath && nextInner != INNER.length)) {
            return "{\"schemaVersion\":1,\"valid\":false}";
        }
        StringBuilder value = new StringBuilder(768);
        value.append("{\"schemaVersion\":1,\"valid\":true,\"clock\":\"CLOCK_BOOTTIME\",");
        value.append("\"pid\":").append(ownerPid).append(",\"protected\":").append(protectedPath);
        value.append(",\"outerNs\":");
        appendArray(value, OUTER);
        value.append(",\"innerNs\":");
        if (protectedPath) {
            appendArray(value, INNER);
        } else {
            value.append("null");
        }
        value.append(",\"calibrationNs\":");
        appendArray(value, CALIBRATION);
        value.append('}');
        return value.toString();
    }

    private static void appendArray(StringBuilder output, long[] values) {
        output.append('[');
        for (int index = 0; index < values.length; index++) {
            if (index > 0) {
                output.append(',');
            }
            output.append(values[index]);
        }
        output.append(']');
    }
}
