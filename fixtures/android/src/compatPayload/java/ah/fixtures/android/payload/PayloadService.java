package ah.fixtures.android.payload;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public final class PayloadService extends Service {
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
