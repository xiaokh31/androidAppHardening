package ah.fixtures.android.payload;

import ah.fixtures.android.ProbeSignal;
import android.app.Application;

public final class PayloadApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        ProbeSignal.applicationOnCreate(getClass());
        ProbeSignal.jniLoaded(getClass(), PayloadJni.loadAndReadMarker());
    }
}
