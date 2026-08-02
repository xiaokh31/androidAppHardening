package ah.fixtures.android.payload;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class PayloadReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        // Component construction is the behavior under test.
    }
}
