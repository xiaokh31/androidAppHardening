package ah.fixtures.android.m301;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

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
        MatrixCursor cursor = new MatrixCursor(new String[] {"sequence", "event"});
        if (getContext() == null) return cursor;
        int sequence = 0;
        for (String event : FixtureEvents.read(getContext())) cursor.addRow(new Object[] {sequence++, event});
        return cursor;
    }

    @Override public String getType(Uri uri) { return "vnd.android.cursor.dir/vnd.ah.m301.event"; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException("read-only"); }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { throw new UnsupportedOperationException("read-only"); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { throw new UnsupportedOperationException("read-only"); }
}
