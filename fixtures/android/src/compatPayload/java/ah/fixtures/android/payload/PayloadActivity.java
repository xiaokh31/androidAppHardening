package ah.fixtures.android.payload;

import ah.fixtures.android.ProbeSignal;
import android.app.Activity;
import android.os.Bundle;

public final class PayloadActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        ProbeSignal.recordActivityMarker(SecondaryApi.marker("activity"));
    }
}
