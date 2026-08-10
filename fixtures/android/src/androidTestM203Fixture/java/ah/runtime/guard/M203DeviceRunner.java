package ah.runtime.guard;

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

/** Real M2-03 signer, metadata, Guard ownership and loader acceptance runner. */
public final class M203DeviceRunner extends Instrumentation {
    private static final byte[] DEX_MAGIC = {'d', 'e', 'x', '\n'};

    @Override
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        start();
    }

    @Override
    public void onStart() {
        Bundle result = new Bundle();
        try {
            String summary = runAcceptance();
            result.putString("stream", "\nTime: 0\n\nOK (1 test)\n" + summary + "\n");
            finish(0, result);
        } catch (Throwable failure) {
            result.putString("stream", "\nFAILURES!!!\n" + stackTrace(failure));
            finish(-1, result);
        }
    }

    private String runAcceptance() throws Exception {
        Context target = getTargetContext();
        ApplicationInfo applicationInfo = target.getApplicationInfo();
        byte[] expectedSigner = installedSigner(target);
        int injected = 0;
        for (RuntimeStartupGuard.GuardStage stage : RuntimeStartupGuard.GuardStage.values()) {
            int[] closeCount = {0};
            try {
                RuntimeStartupGuard.openVerifiedPayloadForTesting(
                        applicationInfo,
                        target.getClassLoader(),
                        new RuntimeStartupGuard.GuardFailureProbe() {
                            @Override
                            public void hit(RuntimeStartupGuard.GuardStage current) {
                                if (current == stage) {
                                    throw new OutOfMemoryError("synthetic-" + stage.name());
                                }
                            }

                            @Override
                            public void closed() {
                                closeCount[0]++;
                            }
                        });
                throw new AssertionError("failure stage returned: " + stage);
            } catch (OutOfMemoryError expected) {
                require(closeCount[0] == 1, "Guard close count: " + stage);
                injected++;
            }
        }

        VerifiedPayloadSession session =
                RuntimeStartupGuard.openVerifiedPayload(applicationInfo, target.getClassLoader());
        require(
                Arrays.equals(session.signer().currentSignerSha256(), expectedSigner),
                "measured signer mismatch");
        require(session.signer().currentSignerAuditPrefix().length() == 12, "audit prefix");
        require(session.startupConfiguration().containerMajor() == 2, "container major");
        require(session.startupConfiguration().containerMinor() == 0, "container minor");
        require(session.startupConfiguration().signerPolicyVersion() == 1, "signer version");
        require(session.startupConfiguration().riskPolicyVersion() == 1, "risk version");
        ClassLoader loader = session.provisionalClassLoader();
        Class<?> secondary = loader.loadClass("ah.fixtures.android.payload.SecondaryApi");
        Method marker = secondary.getMethod("marker", String.class);
        require("M0-05-CLASSES2:M2-03".equals(marker.invoke(null, "M2-03")), "classes2");
        Class<?> payloadJni = loader.loadClass("ah.fixtures.android.payload.PayloadJni");
        Method loadJni = payloadJni.getDeclaredMethod("loadAndReadMarker");
        loadJni.setAccessible(true);
        require("M0-05-JNI-FIXED".equals(invoke(loadJni)), "JNI marker");
        session.close();
        session.close();
        expectClosed(session);

        VerifiedPayloadSession reopened =
                RuntimeStartupGuard.openVerifiedPayload(applicationInfo, target.getClassLoader());
        reopened.close();
        requireNoPlaintextDex(applicationInfo.dataDir);
        if (applicationInfo.deviceProtectedDataDir != null) {
            requireNoPlaintextDex(applicationInfo.deviceProtectedDataDir);
        }
        Arrays.fill(expectedSigner, (byte) 0);
        return "guard_failure_injection=" + injected
                + " signer=true metadata=true session_close=true multidex=true jni=true"
                + " plaintext_dex_files=0";
    }

    private static byte[] installedSigner(Context context) throws Exception {
        PackageInfo info = context.getPackageManager().getPackageInfo(
                context.getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);
        require(info.signingInfo != null, "signing info");
        Signature[] current = info.signingInfo.getApkContentsSigners();
        require(current != null && current.length == 1, "signer count");
        return MessageDigest.getInstance("SHA-256").digest(current[0].toByteArray());
    }

    private static void expectClosed(VerifiedPayloadSession session) {
        try {
            session.signer();
            throw new AssertionError("closed session remained accessible");
        } catch (IllegalStateException expected) {
            require(
                    "AAH-RUNTIME-INTEGRITY-CLOSED".equals(expected.getMessage()),
                    "closed code");
        }
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

    private static void requireNoPlaintextDex(String rootPath) throws Exception {
        if (rootPath == null) {
            return;
        }
        File root = new File(rootPath);
        File[] children = root.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                requireNoPlaintextDex(child.getAbsolutePath());
            } else if (child.length() >= DEX_MAGIC.length) {
                byte[] prefix = new byte[DEX_MAGIC.length];
                try (FileInputStream input = new FileInputStream(child)) {
                    int read = input.read(prefix);
                    require(read != DEX_MAGIC.length || !Arrays.equals(prefix, DEX_MAGIC),
                            "plaintext DEX: " + child.getName());
                }
            }
        }
    }

    private static String stackTrace(Throwable failure) {
        StringWriter output = new StringWriter();
        failure.printStackTrace(new PrintWriter(output));
        return output.toString();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
