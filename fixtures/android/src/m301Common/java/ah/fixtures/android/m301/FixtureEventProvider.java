package ah.fixtures.android.m301;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Build;
import android.os.Process;

import java.lang.reflect.Method;
import java.util.Locale;

public final class FixtureEventProvider extends ContentProvider {
    @Override
    public boolean onCreate() {
        if (getContext() == null) return false;
        FixtureEvents.append(getContext(), "provider.ready");
        if (ah.fixtures.android.BuildConfig.M301_STARTUP_PROVIDER) {
            FixtureEvents.append(getContext(), "startup_provider.create");
        }
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        MatrixCursor cursor = new MatrixCursor(new String[] {
                "sequence", "event", "sdk", "process_abi", "supported_abis", "is_64_bit",
                "process_start_ms", "application_on_create_ms", "interactive_ms",
                "observed_level", "observed_action"
        });
        if (getContext() == null) return cursor;
        int sequence = 0;
        String processAbi = processAbi();
        String supportedAbis = String.join("|", Build.SUPPORTED_ABIS);
        long[] timings = FixtureTimings.read(getContext());
        String[] observation = observeEnvironment();
        for (String event : FixtureEvents.read(getContext())) {
            cursor.addRow(new Object[] {
                    sequence++, event, Build.VERSION.SDK_INT, processAbi, supportedAbis, Process.is64Bit(),
                    timings[0], timings[1], timings[2], observation[0], observation[1]
            });
        }
        return cursor;
    }

    /** Records the shipped policy after startup; the unsigned baseline has no Runtime classes. */
    private String[] observeEnvironment() {
        try {
            ClassLoader loader = getClass().getClassLoader();
            Class<?> engine = Class.forName("ah.runtime.risk.EnvironmentRiskEngine", true, loader);
            Method evaluate = engine.getMethod("evaluate", android.content.pm.ApplicationInfo.class);
            Object report = evaluate.invoke(null, getContext().getApplicationInfo());
            Method level = report.getClass().getMethod("level");
            Method action = report.getClass().getMethod("action");
            return new String[] {String.valueOf(level.invoke(report)), String.valueOf(action.invoke(report))};
        } catch (ReflectiveOperationException | LinkageError unavailable) {
            return new String[] {"UNAVAILABLE", "UNAVAILABLE"};
        }
    }

    private static String processAbi() {
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        if (arch.equals("aarch64") || arch.equals("arm64")) return "arm64-v8a";
        if (arch.startsWith("arm")) return "armeabi-v7a";
        if (arch.equals("amd64") || arch.equals("x86_64")) return "x86_64";
        if (arch.equals("x86") || arch.equals("i686")) return "x86";
        throw new IllegalStateException("unsupported Android process architecture");
    }

    @Override public String getType(Uri uri) { return "vnd.android.cursor.dir/vnd.ah.m301.event"; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException("read-only"); }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { throw new UnsupportedOperationException("read-only"); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { throw new UnsupportedOperationException("read-only"); }
}
