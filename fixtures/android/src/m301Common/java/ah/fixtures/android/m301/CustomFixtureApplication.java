package ah.fixtures.android.m301;

import android.app.Application;

public final class CustomFixtureApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        FixtureEvents.append(this, "custom_application.create");
    }
}
