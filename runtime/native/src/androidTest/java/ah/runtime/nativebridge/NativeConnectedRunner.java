package ah.runtime.nativebridge;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import ah.runtime.loader.PayloadLoadException;
import ah.runtime.loader.PayloadRuntime;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

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
            String runtimeAbi = runtimeAbi(applicationInfo);
            String expectedAbi = getArguments() == null
                    ? null
                    : getArguments().getString("m204_expected_abi");
            require(expectedAbi == null || expectedAbi.equals(runtimeAbi),
                    "runtime ABI mismatch expected=" + expectedAbi + " actual=" + runtimeAbi);
            sendStatus(0, status("."));
            result.putString("stream", "\nTime: 0\n\nOK (1 test)\nnative_connected=true"
                    + "\nruntime_abi=" + runtimeAbi + "\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable failure) {
            Bundle failed = status("F");
            failed.putString("stack", android.util.Log.getStackTraceString(failure));
            sendStatus(-2, failed);
            result.putString("stream", "\nFAILURES!!!\n" + android.util.Log.getStackTraceString(failure));
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    private static String runtimeAbi(ApplicationInfo applicationInfo) throws IOException {
        File runtime = new File(applicationInfo.nativeLibraryDir, "libah_runtime.so");
        require(runtime.isFile() && runtime.canRead(), "Runtime ELF is unavailable");
        byte[] header = new byte[20];
        try (FileInputStream input = new FileInputStream(runtime)) {
            int offset = 0;
            while (offset < header.length) {
                int read = input.read(header, offset, header.length - offset);
                require(read > 0, "Runtime ELF header is truncated");
                offset += read;
            }
        }
        require(header[0] == 0x7f && header[1] == 'E' && header[2] == 'L' && header[3] == 'F',
                "Runtime ELF magic mismatch");
        require(header[5] == 1, "Runtime ELF must be little-endian");
        int elfClass = header[4] & 0xff;
        int machine = (header[18] & 0xff) | ((header[19] & 0xff) << 8);
        if (elfClass == 1 && machine == 40) return "armeabi-v7a";
        if (elfClass == 2 && machine == 183) return "arm64-v8a";
        if (elfClass == 1 && machine == 3) return "x86";
        if (elfClass == 2 && machine == 62) return "x86_64";
        throw new AssertionError("unsupported Runtime ELF class/machine");
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
