package ah.runtime.profile;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.os.Bundle;
import android.os.Process;
import java.lang.reflect.Method;

/** Reads the profile-only observer after the real protected AppComponentFactory startup. */
public final class M210StartupProfileRunner extends Instrumentation {
    private static final int POINT_COUNT = 7;

    @Override
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        start();
    }

    @Override
    public void onStart() {
        Bundle result = new Bundle();
        try {
            long[] points = readPoints(getTargetContext());
            String marker = marker(points);
            result.putString("stream", "\nTime: 0\n\nOK (1 test)\n" + marker + "\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable failure) {
            result.putString("stream", "\nFAILURES!!!\n" + failure + "\n");
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    private static long[] readPoints(Context target) throws Exception {
        Class<?> observer = target.getClassLoader().loadClass(
                "ah.runtime.profile.M210StartupTimingObserver");
        Method snapshot = observer.getMethod("snapshot");
        Object value = snapshot.invoke(null);
        if (!(value instanceof long[])) {
            throw new IllegalStateException("M2-10 observer returned the wrong type");
        }
        long[] points = (long[]) value;
        if (points.length != POINT_COUNT) {
            throw new IllegalStateException("M2-10 observer returned the wrong point count");
        }
        for (int index = 0; index < points.length; index++) {
            if (points[index] <= 0L || (index > 0 && points[index] < points[index - 1])) {
                throw new IllegalStateException("M2-10 observer returned a non-monotonic sequence");
            }
        }
        return points;
    }

    private static String marker(long[] points) {
        StringBuilder value = new StringBuilder("m2_10_profile={\"pid\":")
                .append(Process.myPid())
                .append(",\"points_ns\":[");
        for (int index = 0; index < points.length; index++) {
            if (index > 0) value.append(',');
            value.append(points[index]);
        }
        long stageSum = points[points.length - 1] - points[0];
        value.append("],\"runtime_ns\":").append(stageSum).append('}');
        return value.toString();
    }
}
