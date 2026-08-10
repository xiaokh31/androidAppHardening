package ah.fixtures.android.m203;

import ah.runtime.guard.RuntimeStartupGuard;
import ah.runtime.guard.VerifiedPayloadSession;
import android.app.Activity;
import android.os.Bundle;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Synthetic Release/R8 cold-start owner used only by M2-03 device acceptance. */
public final class M203ColdStartActivity extends Activity {
    private static VerifiedPayloadSession session;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            VerifiedPayloadSession opened =
                    RuntimeStartupGuard.openVerifiedPayload(getApplicationInfo(), getClassLoader());
            ClassLoader loader = opened.provisionalClassLoader();
            Class<?> secondary = loader.loadClass("ah.fixtures.android.payload.SecondaryApi");
            Method marker = secondary.getMethod("marker", String.class);
            require("M0-05-CLASSES2:COLD".equals(marker.invoke(null, "COLD")), "secondary DEX");
            Class<?> payloadJni = loader.loadClass("ah.fixtures.android.payload.PayloadJni");
            Method loadJni = payloadJni.getDeclaredMethod("loadAndReadMarker");
            loadJni.setAccessible(true);
            require("M0-05-JNI-FIXED".equals(invoke(loadJni)), "JNI marker");
            session = opened;
        } catch (Exception failure) {
            throw new IllegalStateException("M2-03 cold-start fixture failed", failure);
        }
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
}
