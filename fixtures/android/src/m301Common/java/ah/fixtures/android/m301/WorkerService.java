package ah.fixtures.android.m301;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public final class WorkerService extends Service {
    @Override
    public void onCreate() {
        super.onCreate();
        FixtureEvents.append(this, "worker.create");
        stopSelf();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
