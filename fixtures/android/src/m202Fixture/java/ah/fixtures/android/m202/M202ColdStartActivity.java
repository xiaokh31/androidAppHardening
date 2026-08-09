package ah.fixtures.android.m202;

import ah.runtime.loader.AuthenticatedPayloadMetadata;
import ah.runtime.loader.LoadedPayload;
import ah.runtime.loader.PayloadRuntime;
import android.app.Activity;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Bundle;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.util.Arrays;

/** Synthetic Release/R8 cold-start owner used only by M2-02 device acceptance. */
public final class M202ColdStartActivity extends Activity {
    private static LoadedPayload payload;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            ApplicationInfo applicationInfo = getApplicationInfo();
            byte[] signer = installedSigner();
            LoadedPayload opened =
                    PayloadRuntime.openVerified(getClassLoader(), applicationInfo, signer);
            AuthenticatedPayloadMetadata metadata = opened.authenticatedMetadata();
            require(Arrays.equals(metadata.currentSignerSha256(), signer), "signer mismatch");
            ClassLoader loader = opened.classLoader();
            Class<?> secondary = loader.loadClass("ah.fixtures.android.payload.SecondaryApi");
            Method marker = secondary.getMethod("marker", String.class);
            require("M0-05-CLASSES2:COLD".equals(marker.invoke(null, "COLD")),
                    "secondary DEX mismatch");
            Class<?> payloadJni = loader.loadClass("ah.fixtures.android.payload.PayloadJni");
            Method loadJni = payloadJni.getDeclaredMethod("loadAndReadMarker");
            loadJni.setAccessible(true);
            require("M0-05-JNI-FIXED".equals(invoke(loadJni)), "JNI mismatch");
            payload = opened;
            Arrays.fill(signer, (byte) 0);
        } catch (Exception failure) {
            throw new IllegalStateException("M2-02 cold-start fixture failed", failure);
        }
    }

    @Override
    protected void onDestroy() {
        LoadedPayload closing = payload;
        payload = null;
        if (closing != null) {
            closing.close();
        }
        super.onDestroy();
    }

    private byte[] installedSigner() throws Exception {
        PackageInfo info =
                getPackageManager().getPackageInfo(
                        getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);
        require(info.signingInfo != null, "signing info missing");
        Signature[] current = info.signingInfo.getApkContentsSigners();
        require(current != null && current.length == 1, "signer count");
        return MessageDigest.getInstance("SHA-256").digest(current[0].toByteArray());
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
