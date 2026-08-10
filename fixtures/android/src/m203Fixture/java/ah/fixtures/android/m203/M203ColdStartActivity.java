package ah.fixtures.android.m203;

import ah.runtime.guard.RuntimeStartupGuard;
import ah.runtime.guard.VerifiedPayloadSession;
import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Synthetic Release/R8 cold-start owner used only by M2-03 device acceptance. */
public final class M203ColdStartActivity extends Activity {
    private static final String TAG = "AAH-M2-03";
    private static VerifiedPayloadSession session;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        String runToken = runToken();
        int lookupCount = 0;
        boolean sessionPublished = false;
        try {
            VerifiedPayloadSession opened =
                    RuntimeStartupGuard.openVerifiedPayload(getApplicationInfo(), getClassLoader());
            sessionPublished = true;
            ClassLoader loader = opened.provisionalClassLoader();
            lookupCount++;
            Class<?> secondary = loader.loadClass("ah.fixtures.android.payload.SecondaryApi");
            Method marker = secondary.getMethod("marker", String.class);
            require("M0-05-CLASSES2:COLD".equals(marker.invoke(null, "COLD")), "secondary DEX");
            lookupCount++;
            Class<?> payloadJni = loader.loadClass("ah.fixtures.android.payload.PayloadJni");
            Method loadJni = payloadJni.getDeclaredMethod("loadAndReadMarker");
            loadJni.setAccessible(true);
            require("M0-05-JNI-FIXED".equals(invoke(loadJni)), "JNI marker");
            session = opened;
            Log.i(TAG, "startup_verified run_token=" + runToken
                    + " lookup_count=" + lookupCount + " session_published=true");
        } catch (Throwable failure) {
            String code = stableCode(failure);
            Log.e(TAG, "startup_rejected run_token=" + runToken
                    + " code=" + code
                    + " lookup_count=" + lookupCount
                    + " session_published=" + sessionPublished);
            throw new IllegalStateException("M2-03 cold-start fixture rejected: " + code);
        }
    }

    private String runToken() {
        String token = getIntent().getStringExtra("aah_m2_03_run_token");
        return token != null && token.matches("[0-9a-f]{16}") ? token : "invalid";
    }

    @Override
    protected void onDestroy() {
        VerifiedPayloadSession closing = session;
        session = null;
        if (closing != null) {
            closing.close();
        }
        super.onDestroy();
    }

    private static Object invoke(Method method) throws Exception {
        try {
            return method.invoke(null);
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw failure;
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private static String stableCode(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.matches("AAH-RUNTIME-INTEGRITY-[A-Z_]+")) {
                return message;
            }
            current = current.getCause();
        }
        return "AAH-RUNTIME-INTEGRITY-INTERNAL";
    }
}
