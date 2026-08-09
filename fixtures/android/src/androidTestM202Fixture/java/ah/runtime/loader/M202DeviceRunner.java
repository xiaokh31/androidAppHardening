package ah.runtime.loader;

import android.app.Instrumentation;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Bundle;
import java.io.File;
import java.io.FileInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.util.Arrays;

/** Test-only M2-02 transaction, loader, JNI and plaintext-on-disk acceptance runner. */
public final class M202DeviceRunner extends Instrumentation {
    private static final String TEST_CLASS = "ah.runtime.loader.M202DeviceAcceptance";
    private static final String TEST_NAME = "authenticatedLoaderTransaction";
    private static final byte[] DEX_MAGIC = {'d', 'e', 'x', '\n'};

    @Override
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        start();
    }

    @Override
    public void onStart() {
        sendTestStatus(1, "\n" + TEST_CLASS + ":");
        Bundle result = new Bundle();
        try {
            String summary = runAcceptance();
            sendTestStatus(0, ".");
            result.putString("stream", "\nTime: 0\n\nOK (1 test)\n" + summary + "\n");
            finish(0, result);
        } catch (Throwable failure) {
            sendTestStatus(-2, "F");
            result.putString("stream", "\nFAILURES!!!\n" + stackTrace(failure));
            finish(-1, result);
        }
    }

    private String runAcceptance() throws Exception {
        Context target = getTargetContext();
        ApplicationInfo applicationInfo = target.getApplicationInfo();
        byte[] signer = installedSigner(target);
        byte[] expectedPackage =
                MessageDigest.getInstance("SHA-256")
                        .digest(applicationInfo.packageName.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        int injected = 0;
        for (PayloadRuntime.OpenStage stage : PayloadRuntime.OpenStage.values()) {
            final long[] capturedHandle = {0};
            try {
                PayloadRuntime.openVerifiedForTesting(
                        target.getClassLoader(),
                        applicationInfo,
                        signer,
                        (current, nativeHandle) -> {
                            capturedHandle[0] = nativeHandle;
                            if (current == stage) {
                                throw new OutOfMemoryError("synthetic-" + stage.name());
                            }
                        });
                throw new AssertionError("failure stage returned: " + stage);
            } catch (OutOfMemoryError expected) {
                require(capturedHandle[0] != 0, "failure stage did not capture handle: " + stage);
                requireClosedHandle(capturedHandle[0], stage);
                injected++;
            }
        }

        LoadedPayload payload =
                PayloadRuntime.openVerified(target.getClassLoader(), applicationInfo, signer);
        verifyNativeSearchPath(applicationInfo);
        ClassLoader loader = payload.classLoader();
        require(loader.getParent() == target.getClassLoader(), "payload parent loader changed");
        AuthenticatedPayloadMetadata metadata = payload.authenticatedMetadata();
        require(metadata.containerMajor() == 2 && metadata.containerMinor() == 0,
                "container version mismatch");
        require(metadata.signerPolicyVersion() == 1 && metadata.riskPolicyVersion() == 1,
                "policy version mismatch");
        require(Arrays.equals(metadata.packageNameSha256(), expectedPackage),
                "package digest mismatch");
        require(Arrays.equals(metadata.currentSignerSha256(), signer), "signer digest mismatch");
        byte[][] lineage = metadata.signerLineageSha256();
        require(lineage.length == 1 && Arrays.equals(lineage[0], signer), "lineage mismatch");
        byte[] buildCopy = metadata.buildId();
        byte original = buildCopy[0];
        buildCopy[0] ^= 1;
        require(metadata.buildId()[0] == original, "metadata array is not defensive");

        Class<?> secondary = loader.loadClass("ah.fixtures.android.payload.SecondaryApi");
        Method marker = secondary.getMethod("marker", String.class);
        require("M0-05-CLASSES2:M2-02".equals(marker.invoke(null, "M2-02")),
                "classes2 lookup failed");
        Class<?> payloadJni = loader.loadClass("ah.fixtures.android.payload.PayloadJni");
        Method loadJni = payloadJni.getDeclaredMethod("loadAndReadMarker");
        loadJni.setAccessible(true);
        require("M0-05-JNI-FIXED".equals(invoke(loadJni)), "payload JNI marker mismatch");

        payload.close();
        payload.close();
        try {
            payload.classLoader();
            throw new AssertionError("closed payload remained accessible");
        } catch (PayloadLoadException expected) {
            require(expected.getMessage().contains("CLOSED"), "unexpected closed error");
        }
        requireNoPlaintextDex(applicationInfo.dataDir, 0);
        if (applicationInfo.deviceProtectedDataDir != null) {
            requireNoPlaintextDex(applicationInfo.deviceProtectedDataDir, 0);
        }
        Arrays.fill(signer, (byte) 0);
        Arrays.fill(expectedPackage, (byte) 0);
        return "failure_injection=" + injected
                + " multidex=true jni=true native_path=true metadata=true plaintext_dex_files=0";
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

    private static void requireClosedHandle(long handle, PayloadRuntime.OpenStage stage) {
        try {
            NativePayloadBridge.nativeAuthenticatedMetadata(handle);
            throw new AssertionError("native handle survived injected stage: " + stage);
        } catch (PayloadLoadException expected) {
            require(expected.getMessage().contains("HANDLE"), "unexpected handle error: " + stage);
        }
    }

    private static byte[] installedSigner(Context context) throws Exception {
        PackageInfo info =
                context.getPackageManager().getPackageInfo(
                        context.getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);
        require(info.signingInfo != null, "signing info missing");
        Signature[] current = info.signingInfo.getApkContentsSigners();
        require(current != null && current.length == 1, "fixture must have exactly one signer");
        return MessageDigest.getInstance("SHA-256").digest(current[0].toByteArray());
    }

    private static void verifyNativeSearchPath(ApplicationInfo applicationInfo) {
        String searchPath = PayloadClassLoaders.resolveNativeLibrarySearchPath(applicationInfo);
        String apkPrefix = applicationInfo.sourceDir + "!/lib/";
        String apkPathPattern =
                java.util.regex.Pattern.quote(apkPrefix) + "(?:arm64-v8a|x86_64)";
        boolean extracted =
                (applicationInfo.flags & ApplicationInfo.FLAG_EXTRACT_NATIVE_LIBS) != 0;
        if (extracted) {
            require(applicationInfo.nativeLibraryDir != null, "extracted Native directory missing");
            String extractedPrefix = applicationInfo.nativeLibraryDir + File.pathSeparator;
            require(searchPath.startsWith(extractedPrefix), "extracted Native path not first");
            require(new File(applicationInfo.nativeLibraryDir, "libah_runtime.so").isFile(),
                    "extracted Runtime SO missing");
            require(searchPath.substring(extractedPrefix.length()).matches(apkPathPattern),
                    "extracted fallback path mismatch");
        } else {
            require(searchPath.matches(apkPathPattern), "direct Native path mismatch");
        }
    }

    private static void requireNoPlaintextDex(String path, int depth) throws Exception {
        if (path == null || depth > 16) {
            return;
        }
        File file = new File(path);
        if (!file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    requireNoPlaintextDex(child.getAbsolutePath(), depth + 1);
                }
            }
            return;
        }
        if (!file.isFile() || file.length() < DEX_MAGIC.length) {
            return;
        }
        byte[] prefix = new byte[DEX_MAGIC.length];
        try (FileInputStream input = new FileInputStream(file)) {
            if (input.read(prefix) == prefix.length && Arrays.equals(prefix, DEX_MAGIC)) {
                throw new AssertionError("plaintext DEX file created in private storage");
            }
        }
    }

    private void sendTestStatus(int status, String stream) {
        Bundle bundle = new Bundle();
        bundle.putString("id", "AndroidJUnitRunner");
        bundle.putString("class", TEST_CLASS);
        bundle.putString("test", TEST_NAME);
        bundle.putInt("current", 1);
        bundle.putInt("numtests", 1);
        bundle.putString("stream", stream);
        sendStatus(status, bundle);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static String stackTrace(Throwable failure) {
        StringWriter output = new StringWriter();
        failure.printStackTrace(new PrintWriter(output));
        return output.toString();
    }
}
