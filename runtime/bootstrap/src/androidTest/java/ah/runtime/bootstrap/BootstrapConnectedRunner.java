package ah.runtime.bootstrap;

import android.app.Instrumentation;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;

/** Non-empty connectedCheck contract for the public-API bootstrap boundary. */
public final class BootstrapConnectedRunner extends Instrumentation {
    private static final String TEST_CLASS =
            "ah.runtime.bootstrap.BootstrapConnectedContract";
    private static final String TEST_NAME = "signerAndFactorySmoke";

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
            require(new ShellAppComponentFactory() != null, "Shell Factory construction failed");
            require(ClassLoaderProbe.snapshot().isEmpty(),
                    "connected smoke inherited unexpected bootstrap events");
            ApplicationInfo applicationInfo = getTargetContext().getApplicationInfo();
            try {
                EarlySignerProbe.verify(applicationInfo);
                throw new AssertionError("zero-placeholder signer policy was accepted");
            } catch (IllegalStateException expected) {
                require(expected.getMessage() != null
                                && expected.getMessage().startsWith("AAH-P008:"),
                        "unexpected early signer failure");
            }
            sendStatus(0, status("."));
            result.putString("stream", "\nTime: 0\n\nOK (1 test)\nbootstrap_connected=true\n");
            finish(0, result);
        } catch (Throwable failure) {
            Bundle failed = status("F");
            failed.putString("stack", android.util.Log.getStackTraceString(failure));
            sendStatus(-2, failed);
            result.putString("stream", "\nFAILURES!!!\n" + android.util.Log.getStackTraceString(failure));
            finish(-1, result);
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
