package ah.runtime.nativebridge;

import android.app.Instrumentation;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import ah.runtime.loader.PayloadLoadException;
import ah.runtime.loader.PayloadRuntime;

/** Non-empty connectedCheck contract for the production Native facade and JNI library. */
public final class NativeConnectedRunner extends Instrumentation {
    private static final String TEST_CLASS = "ah.runtime.nativebridge.NativeConnectedContract";
    private static final String TEST_NAME = "jniFailClosedSmoke";

    @Override
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        start();
    }

    @Override
    public void onStart() {
        sendStatus(1, status("\n" + TEST_CLASS + ":"));
        Bundle result = new Bundle();
        try {
            ApplicationInfo applicationInfo = getTargetContext().getApplicationInfo();
            require(applicationInfo.sourceDir != null
                            && new java.io.File(applicationInfo.sourceDir).isAbsolute(),
                    "connected target sourceDir is unavailable");
            expectCode(null, "AAH-RUNTIME-CONTAINER-ARGUMENT");
            expectCode(applicationInfo, "AAH-RUNTIME-CONTAINER-ZIP-MISSING");
            sendStatus(0, status("."));
            result.putString("stream", "\nTime: 0\n\nOK (1 test)\nnative_connected=true\n");
            finish(0, result);
        } catch (Throwable failure) {
            Bundle failed = status("F");
            failed.putString("stack", android.util.Log.getStackTraceString(failure));
            sendStatus(-2, failed);
            result.putString("stream", "\nFAILURES!!!\n" + android.util.Log.getStackTraceString(failure));
            finish(-1, result);
        }
    }

    private static void expectCode(ApplicationInfo applicationInfo, String code) {
        try {
            PayloadRuntime.inspectBinding(applicationInfo);
            throw new AssertionError("fail-closed JNI smoke returned for " + code);
        } catch (PayloadLoadException expected) {
            require(code.equals(expected.code()), "unexpected stable JNI code");
        }
    }

    private static Bundle status(String stream) {
        Bundle bundle = new Bundle();
        bundle.putString("id", "M2-02");
        bundle.putString("class", TEST_CLASS);
        bundle.putString("test", TEST_NAME);
        bundle.putInt("numtests", 1);
        bundle.putInt("current", 1);
        bundle.putString("stream", stream);
        return bundle;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
