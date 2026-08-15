package ah.fixtures.android.m301;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Build;
import android.os.Process;

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
                "sequence", "event", "sdk", "process_abi", "supported_abis", "is_64_bit"
        });
        if (getContext() == null) return cursor;
        int sequence = 0;
        String processAbi = processAbi();
        String supportedAbis = String.join("|", Build.SUPPORTED_ABIS);
        for (String event : FixtureEvents.read(getContext())) {
            cursor.addRow(new Object[] {
                    sequence++, event, Build.VERSION.SDK_INT, processAbi, supportedAbis, Process.is64Bit()
            });
        }
        return cursor;
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
