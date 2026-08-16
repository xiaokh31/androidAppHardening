package ah.fixtures.android.m301;

import android.app.Application;

public final class BenchmarkFixtureApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        FixtureTimings.begin(this);
    }
}
