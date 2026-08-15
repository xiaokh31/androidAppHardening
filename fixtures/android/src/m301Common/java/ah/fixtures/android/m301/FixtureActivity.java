package ah.fixtures.android.m301;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewTreeObserver;

import java.lang.reflect.Method;

public final class FixtureActivity extends Activity {
    private boolean interactiveReported;
    private boolean fullyDrawnReported;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        FixtureEvents.append(this, "activity.create");
        if (ah.fixtures.android.BuildConfig.M301_KOTLIN) verifyKotlin();
        if ("kotlin-multidex".equals(ah.fixtures.android.BuildConfig.FIXTURE_ID)) verifyMultidex();
        if (ah.fixtures.android.BuildConfig.M301_JNI) verifyJni();
        if (ah.fixtures.android.BuildConfig.M301_MULTI_PROCESS) startService(new Intent(this, WorkerService.class));

        // The fixture intentionally has no content view. Bind the fully-drawn
        // marker to the decor's first real traversal so StartupTimingMetric
        // observes both the UI frame and its associated RenderThread frame.
        View decorView = getWindow().getDecorView();
        decorView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                if (!fullyDrawnReported) {
                    fullyDrawnReported = true;
                    decorView.getViewTreeObserver().removeOnPreDrawListener(this);
                    reportFullyDrawn();
                }
                return true;
            }
        });
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && !interactiveReported) {
            interactiveReported = true;
            FixtureTimings.markInteractive(this);
        }
    }

    private void verifyKotlin() {
        try {
            Class<?> marker = Class.forName("ah.fixtures.android.m301.KotlinMarker", true, getClassLoader());
            Method value = marker.getMethod("value");
            if (!"kotlin".equals(value.invoke(null))) throw new IllegalStateException("Kotlin marker mismatch");
            FixtureEvents.append(this, "kotlin.marker");
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Kotlin marker unavailable", failure);
        }
    }

    private void verifyMultidex() {
        try {
            Class<?> secondary = Class.forName("ah.fixtures.android.m301.secondary.SecondaryMarker", true, getClassLoader());
            Object value = secondary.getMethod("value").invoke(null);
            if (!Integer.valueOf(30102).equals(value)) throw new IllegalStateException("multidex marker mismatch");
            FixtureEvents.append(this, "multidex.class");
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("multidex marker unavailable", failure);
        }
    }

    private void verifyJni() {
        if (!"M0-05-JNI-FIXED".equals(ah.fixtures.android.payload.PayloadJni.marker())) {
            throw new IllegalStateException("JNI marker mismatch");
        }
        FixtureEvents.append(this, "jni.marker");
    }
}
