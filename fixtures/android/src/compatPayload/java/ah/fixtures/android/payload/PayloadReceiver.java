package ah.fixtures.android.payload;

import ah.fixtures.android.ProbeSignal;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

public final class PayloadReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (isOrderedBroadcast()) {
            Bundle result = getResultExtras(true);
            result.putInt("classloader_count", ProbeSignal.factoryCount("classloader"));
            result.putInt("application_count", ProbeSignal.factoryCount("application"));
            result.putInt("receiver_count", ProbeSignal.factoryCount("receiver"));
            result.putString("jni_marker", ProbeSignal.jniMarker());
            result.putString("receiver_loader", getClass().getClassLoader().getClass().getName());
            result.putString("secondary_marker", SecondaryApi.marker("secondary"));
        }
    }
}
