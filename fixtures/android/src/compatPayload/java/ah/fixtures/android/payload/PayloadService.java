package ah.fixtures.android.payload;

import ah.fixtures.android.ProbeSignal;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public final class PayloadService extends Service {
    @Override
    public void onCreate() {
        super.onCreate();
        ProbeSignal.recordServiceMarker(SecondaryApi.marker("service"));
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
