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
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.Arrays;

/** Test-only M2-02 transaction, loader, JNI and plaintext-on-disk acceptance runner. */
public final class M202DeviceRunner extends Instrumentation {
    private static final String TEST_CLASS = "ah.runtime.loader.M202DeviceAcceptance";
    private static final String TEST_NAME = "authenticatedLoaderTransaction";
    private static final byte[] DEX_MAGIC = {'d', 'e', 'x', '\n'};
    private Bundle arguments;

    @Override
    public void onCreate(Bundle arguments) {
        this.arguments = arguments;
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
        byte[] expectedBuildId = expectedHexArgument("m202_expected_build_id_hex", 16);
        byte[] expectedKeySlotId = expectedHexArgument("m202_expected_key_slot_id_hex", 16);

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

        final long[] committedHandle = {0};
        LoadedPayload payload =
                PayloadRuntime.openVerifiedForTesting(
                        target.getClassLoader(),
                        applicationInfo,
                        signer,
                        (current, nativeHandle) -> committedHandle[0] = nativeHandle);
        require(committedHandle[0] != 0, "committed handle was not captured");
        byte[] encodedMetadata =
                NativePayloadBridge.nativeAuthenticatedMetadata(committedHandle[0]);
        verifyMetadataParserMatrix(encodedMetadata);
        Arrays.fill(encodedMetadata, (byte) 0);
        verifyNativeSearchPath(applicationInfo);
        ClassLoader loader = payload.classLoader();
        require(loader.getParent() == target.getClassLoader(), "payload parent loader changed");
        AuthenticatedPayloadMetadata metadata = payload.authenticatedMetadata();
        verifyMetadataGolden(
                metadata, signer, expectedPackage, expectedBuildId, expectedKeySlotId);
        byte[] buildCopy = metadata.buildId();
        byte original = buildCopy[0];
        buildCopy[0] ^= 1;
        require(metadata.buildId()[0] == original, "metadata array is not defensive");
        byte[][] lineageCopy = metadata.signerLineageSha256();
        byte lineageOriginal = lineageCopy[0][0];
        lineageCopy[0][0] ^= 1;
        require(metadata.signerLineageSha256()[0][0] == lineageOriginal,
                "metadata lineage is not deeply defensive");

        LoadedPayload independent =
                PayloadRuntime.openVerified(target.getClassLoader(), applicationInfo, signer);
        requireMetadataEqual(metadata, independent.authenticatedMetadata());

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
        Class<?> independentSecondary =
                independent.classLoader().loadClass("ah.fixtures.android.payload.SecondaryApi");
        Method independentMarker = independentSecondary.getMethod("marker", String.class);
        require("M0-05-CLASSES2:M2-02".equals(independentMarker.invoke(null, "M2-02")),
                "closing one handle invalidated an independent handle");
        independent.close();
        independent.close();
        verifyJniCleanupBoundaries(loader, target, applicationInfo, signer);
        requireNoPlaintextDex(applicationInfo.dataDir, 0);
        if (applicationInfo.deviceProtectedDataDir != null) {
            requireNoPlaintextDex(applicationInfo.deviceProtectedDataDir, 0);
        }
        Arrays.fill(signer, (byte) 0);
        Arrays.fill(expectedPackage, (byte) 0);
        Arrays.fill(expectedBuildId, (byte) 0);
        Arrays.fill(expectedKeySlotId, (byte) 0);
        return "failure_injection=" + injected
                + " multidex=true jni=true native_path=true metadata=true"
                + " metadata_negative=true metadata_golden=true cross_handle=true"
                + " jni_cleanup=true plaintext_dex_files=0";
    }

    private static Object invoke(Method method, Object... arguments) throws Exception {
        try {
            return method.invoke(null, arguments);
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
        requireClosedHandle(handle, stage.name());
    }

    private static void requireClosedHandle(long handle, String context) {
        try {
            NativePayloadBridge.nativeAuthenticatedMetadata(handle);
            throw new AssertionError("native handle survived: " + context);
        } catch (PayloadLoadException expected) {
            require(expected.code().equals("AAH-RUNTIME-CONTAINER-HANDLE"),
                    "unexpected handle error: " + context);
        }
    }

    private static void verifyJniCleanupBoundaries(
            ClassLoader payloadLoader,
            Context target,
            ApplicationInfo applicationInfo,
            byte[] signer) throws Exception {
        Class<?> hooks = payloadLoader.loadClass("ah.fixtures.android.payload.PayloadJni");
        Method throwWithCleanup = hooks.getDeclaredMethod("throwWithCleanupForTesting");
        Method unmapDirectBuffer =
                hooks.getDeclaredMethod("unmapDirectBufferForTesting", ByteBuffer.class);
        throwWithCleanup.setAccessible(true);
        unmapDirectBuffer.setAccessible(true);
        try {
            invoke(throwWithCleanup);
            throw new AssertionError("JNI cleanup aggregation returned");
        } catch (PayloadLoadException primary) {
            require(primary.code().equals("AAH-RUNTIME-CONTAINER-INJECTED"),
                    "JNI aggregation replaced the primary code");
            requireCleanupSuppressed(primary, "JNI aggregation");
        }

        final long[] rollbackHandle = {0};
        try {
            PayloadRuntime.openVerifiedForTesting(
                    target.getClassLoader(),
                    applicationInfo,
                    signer,
                    (stage, nativeHandle) -> {
                        rollbackHandle[0] = nativeHandle;
                        if (stage == PayloadRuntime.OpenStage.BEFORE_RETURN) {
                            ByteBuffer[] buffers =
                                    NativePayloadBridge.nativeDexBuffers(nativeHandle);
                            invokeUnmap(unmapDirectBuffer, buffers[0]);
                            Arrays.fill(buffers, null);
                            throw PayloadLoadException.create("INJECTED");
                        }
                    });
            throw new AssertionError("cleanup-suppressed rollback returned");
        } catch (PayloadLoadException primary) {
            require(primary.code().equals("AAH-RUNTIME-CONTAINER-INJECTED"),
                    "rollback cleanup replaced the primary code");
            requireCleanupSuppressed(primary, "rollback cleanup");
        }
        require(rollbackHandle[0] != 0, "rollback cleanup did not capture a handle");
        requireClosedHandle(rollbackHandle[0], "rollback cleanup");

        final long[] closeHandle = {0};
        LoadedPayload damaged =
                PayloadRuntime.openVerifiedForTesting(
                        target.getClassLoader(),
                        applicationInfo,
                        signer,
                        (stage, nativeHandle) -> closeHandle[0] = nativeHandle);
        ByteBuffer[] buffers = NativePayloadBridge.nativeDexBuffers(closeHandle[0]);
        invokeUnmap(unmapDirectBuffer, buffers[0]);
        Arrays.fill(buffers, null);
        try {
            damaged.close();
            throw new AssertionError("explicit cleanup failure returned");
        } catch (PayloadLoadException cleanup) {
            require(cleanup.code().equals("AAH-RUNTIME-CONTAINER-CLEANUP"),
                    "explicit close did not expose the cleanup code");
            require(cleanup.getSuppressed().length == 0,
                    "explicit cleanup failure unexpectedly had suppressed errors");
        }
        requireClosedHandle(closeHandle[0], "explicit cleanup");
        damaged.close();
    }

    private static void requireCleanupSuppressed(Throwable primary, String context) {
        Throwable[] suppressed = primary.getSuppressed();
        require(suppressed.length == 1 && suppressed[0] instanceof PayloadLoadException,
                context + " did not attach exactly one stable cleanup failure");
        require(((PayloadLoadException) suppressed[0]).code()
                        .equals("AAH-RUNTIME-CONTAINER-CLEANUP"),
                context + " attached the wrong cleanup code");
    }

    private static void invokeUnmap(Method method, ByteBuffer buffer) {
        try {
            invoke(method, buffer);
        } catch (Exception failure) {
            throw new AssertionError("direct-buffer unmap injection failed", failure);
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

    private static void verifyMetadataParserMatrix(byte[] encoded) {
        require(AuthenticatedPayloadMetadata.parse(encoded) != null, "valid metadata rejected");
        expectMetadataReject(null);
        expectMetadataReject(Arrays.copyOf(encoded, encoded.length - 1));
        for (int offset : new int[] {0, 4, 6, 8, 10, 12, 14, 16, 18, 20}) {
            byte[] mutated = encoded.clone();
            mutated[offset] ^= 1;
            expectMetadataReject(mutated);
        }
        byte[] zeroLineage = encoded.clone();
        putU16(zeroLineage, 18, 0);
        expectMetadataReject(zeroLineage);
        byte[] excessiveLineage = encoded.clone();
        putU16(excessiveLineage, 18, 17);
        expectMetadataReject(excessiveLineage);
        expectMetadataReject(withFactory(encoded, new byte[] {(byte) 0xc3, 0x28}));
        expectMetadataReject(withFactory(encoded, new byte[] {'a', 0, 'b'}));
        expectMetadataReject(withFactory(
                encoded,
                "ah.runtime.bootstrap.ShellAppComponentFactory"
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        byte[] validFactory = withFactory(
                encoded,
                "ah.fixture.RealFactory".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        require("ah.fixture.RealFactory".equals(
                        AuthenticatedPayloadMetadata.parse(validFactory)
                                .originalFactoryClassNameOrNull()),
                "valid Factory metadata rejected");
        Arrays.fill(validFactory, (byte) 0);
    }

    private static byte[] withFactory(byte[] encoded, byte[] factory) {
        int oldFactoryLength = u16(encoded, 16);
        int lineageCount = u16(encoded, 18);
        int lineageBytes = lineageCount * 32;
        byte[] result = new byte[120 + factory.length + lineageBytes];
        System.arraycopy(encoded, 0, result, 0, 120);
        putU16(result, 6, result.length);
        putU16(result, 16, factory.length);
        System.arraycopy(factory, 0, result, 120, factory.length);
        System.arraycopy(encoded, 120 + oldFactoryLength,
                result, 120 + factory.length, lineageBytes);
        return result;
    }

    private static void expectMetadataReject(byte[] encoded) {
        try {
            AuthenticatedPayloadMetadata.parse(encoded);
            throw new AssertionError("invalid metadata was accepted");
        } catch (PayloadLoadException expected) {
            require(expected.getMessage().contains("METADATA"), "unexpected metadata error");
        } finally {
            if (encoded != null) {
                Arrays.fill(encoded, (byte) 0);
            }
        }
    }

    private static void requireMetadataEqual(
            AuthenticatedPayloadMetadata left,
            AuthenticatedPayloadMetadata right) {
        require(left.containerMajor() == right.containerMajor()
                        && left.containerMinor() == right.containerMinor()
                        && left.signerPolicyVersion() == right.signerPolicyVersion()
                        && left.riskPolicyVersion() == right.riskPolicyVersion()
                        && java.util.Objects.equals(
                                left.originalFactoryClassNameOrNull(),
                                right.originalFactoryClassNameOrNull())
                        && Arrays.equals(left.buildId(), right.buildId())
                        && Arrays.equals(left.keySlotId(), right.keySlotId())
                        && Arrays.equals(left.packageNameSha256(), right.packageNameSha256())
                        && Arrays.equals(left.currentSignerSha256(), right.currentSignerSha256())
                        && Arrays.deepEquals(
                                left.signerLineageSha256(), right.signerLineageSha256()),
                "cross-handle metadata mismatch");
    }

    private static void verifyMetadataGolden(
            AuthenticatedPayloadMetadata metadata,
            byte[] signer,
            byte[] expectedPackage,
            byte[] expectedBuildId,
            byte[] expectedKeySlotId) {
        byte[][] lineage = metadata.signerLineageSha256();
        require(metadata.containerMajor() == 2, "golden container major mismatch");
        require(metadata.containerMinor() == 0, "golden container minor mismatch");
        require(metadata.signerPolicyVersion() == 1, "golden signer policy mismatch");
        require(metadata.riskPolicyVersion() == 1, "golden risk policy mismatch");
        require(metadata.originalFactoryClassNameOrNull() == null,
                "golden original Factory mismatch");
        require(Arrays.equals(metadata.buildId(), expectedBuildId),
                "golden build ID mismatch");
        require(Arrays.equals(metadata.keySlotId(), expectedKeySlotId),
                "golden key-slot ID mismatch");
        require(Arrays.equals(metadata.packageNameSha256(), expectedPackage),
                "golden package digest mismatch");
        require(Arrays.equals(metadata.currentSignerSha256(), signer),
                "golden current signer mismatch");
        require(lineage.length == 1 && Arrays.equals(lineage[0], signer),
                "golden signer lineage mismatch");
    }

    private byte[] expectedHexArgument(String name, int expectedBytes) {
        String value = arguments == null ? null : arguments.getString(name);
        require(value != null && value.length() == expectedBytes * 2,
                "missing golden argument: " + name);
        byte[] decoded = new byte[expectedBytes];
        for (int index = 0; index < decoded.length; index++) {
            int high = Character.digit(value.charAt(index * 2), 16);
            int low = Character.digit(value.charAt(index * 2 + 1), 16);
            require(high >= 0 && low >= 0, "invalid golden argument: " + name);
            decoded[index] = (byte) ((high << 4) | low);
        }
        return decoded;
    }

    private static int u16(byte[] value, int offset) {
        return (value[offset] & 0xff) | ((value[offset + 1] & 0xff) << 8);
    }

    private static void putU16(byte[] value, int offset, int number) {
        value[offset] = (byte) number;
        value[offset + 1] = (byte) (number >>> 8);
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
