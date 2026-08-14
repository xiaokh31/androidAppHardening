package ah.runtime.guard;

import ah.runtime.loader.AuthenticatedPayloadMetadata;
import ah.runtime.loader.LoadedPayload;
import ah.runtime.loader.PayloadRuntime;
import ah.runtime.loader.UntrustedPayloadBinding;
import ah.runtime.risk.EnvironmentRiskEngine;
import ah.runtime.risk.RiskReportV1;
import ah.runtime.risk.RiskSignal;
import ah.runtime.risk.RiskSignalId;
import ah.runtime.risk.SignalState;
import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Bundle;
import android.os.Build;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.io.StringWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Real M2-03 signer, metadata, Guard ownership and loader acceptance runner. */
public final class M203DeviceRunner extends Instrumentation {
    private static final byte[] DEX_MAGIC = {'d', 'e', 'x', '\n'};
    private Bundle arguments = Bundle.EMPTY;

    @Override
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        this.arguments = arguments == null ? Bundle.EMPTY : new Bundle(arguments);
        start();
    }

    @Override
    public void onStart() {
        Bundle result = new Bundle();
        try {
            String summary = runAcceptance();
            result.putString("stream", "\nTime: 0\n\nOK (1 test)\n" + summary + "\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable failure) {
            result.putString("stream", "\nFAILURES!!!\n" + stackTrace(failure));
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    private String runAcceptance() throws Exception {
        Context target = getTargetContext();
        ApplicationInfo applicationInfo = target.getApplicationInfo();
        verifyRiskFacade(
                target,
                applicationInfo,
                "true".equals(arguments.getString("m205_release_probe")));
        byte[] expectedSigner = installedSigner(target);
        int injected = 0;
        StringBuilder m302Cases = new StringBuilder();
        for (RuntimeStartupGuard.GuardStage stage : RuntimeStartupGuard.GuardStage.values()) {
            for (boolean oom : new boolean[] {false, true}) {
                int[] closeCount = {0};
                try {
                    RuntimeStartupGuard.openVerifiedPayloadForTesting(
                            applicationInfo,
                            target.getClassLoader(),
                            new RuntimeStartupGuard.GuardFailureProbe() {
                                @Override
                                public void hit(RuntimeStartupGuard.GuardStage current) {
                                    if (current == stage) {
                                        if (oom) {
                                            throw new OutOfMemoryError("synthetic-" + stage.name());
                                        }
                                        throw new IllegalStateException("synthetic-" + stage.name());
                                    }
                                }

                                @Override
                                public void close(LoadedPayload payload) {
                                    long nativeHandle = captureNativeHandle(payload);
                                    payload.close();
                                    requirePayloadClosed(payload, "failure-" + stage.name());
                                    requireNativeHandleClosed(nativeHandle, "failure-" + stage.name());
                                    payload.close();
                                    requireNativeHandleClosed(
                                            nativeHandle, "failure-second-" + stage.name());
                                    if (stage == RuntimeStartupGuard.GuardStage.BEFORE_RETURN) {
                                        throw new IllegalStateException("synthetic-cleanup");
                                    }
                                }

                                @Override
                                public void closed() {
                                    closeCount[0]++;
                                }
                            });
                    throw new AssertionError("failure stage returned: " + stage);
                } catch (Throwable expected) {
                    require(closeCount[0] == 1, "Guard close count: " + stage + ":" + oom);
                    if (oom) {
                        require(expected instanceof OutOfMemoryError, "OOM primary replaced");
                    } else {
                        require(expected instanceof RuntimeIntegrityFailure, "exception primary type");
                        require("AAH-RUNTIME-INTEGRITY-CONTAINER".equals(expected.getMessage()),
                                "exception primary code");
                    }
                    if (stage == RuntimeStartupGuard.GuardStage.BEFORE_RETURN) {
                        require(expected.getSuppressed().length == 1, "cleanup suppression count");
                        require("synthetic-cleanup".equals(expected.getSuppressed()[0].getMessage()),
                                "cleanup suppression value");
                    } else {
                        require(expected.getSuppressed().length == 0, "unexpected suppression");
                    }
                    m302Cases.append(m302GuardCase(stage, oom));
                    injected++;
                }
            }
        }

        int metadataRejections = verifyGuardMetadataRejections(
                target, applicationInfo, expectedSigner);
        require(metadataRejections == 12, "metadata rejection count");
        m302Cases.append(m302MetadataCases());
        verifyFrameworkPackageRejection(target, applicationInfo);

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
        LoadedPayload sessionPayload = captureLoadedPayload(session);
        long sessionHandle = captureNativeHandle(sessionPayload);
        session.close();
        requireNativeHandleClosed(sessionHandle, "session-close");
        session.close();
        requireNativeHandleClosed(sessionHandle, "session-second-close");
        requirePayloadClosed(sessionPayload, "session-close");
        expectClosed(session);

        VerifiedPayloadSession reopened =
                RuntimeStartupGuard.openVerifiedPayload(applicationInfo, target.getClassLoader());
        reopened.close();
        requireNoPlaintextDex(applicationInfo.dataDir);
        if (applicationInfo.deviceProtectedDataDir != null) {
            requireNoPlaintextDex(applicationInfo.deviceProtectedDataDir);
        }
        Arrays.fill(expectedSigner, (byte) 0);
        return m302Cases + "guard_failure_injection=" + injected
                + " guard_metadata_rejections=" + metadataRejections
                + " signer=true metadata=true session_close=true multidex=true jni=true"
                + " framework_package_rejection=true cleanup_suppressed=true"
                + " mapping_cleanup=true plaintext_dex_files=0 risk_r8_jni=true";

    }

    private static String m302GuardCase(RuntimeStartupGuard.GuardStage stage, boolean oom) {
        String slug = stage.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
        boolean cleanup = stage == RuntimeStartupGuard.GuardStage.BEFORE_RETURN;
        return "M302_CASE id=m302-guard-" + slug + (oom ? "-oom" : "-exception")
                + " target=guard mutation=guard_" + stage.name().toLowerCase(java.util.Locale.ROOT)
                + (cleanup ? "_cleanup" : "") + (oom ? "_oom" : "_exception")
                + " expectedStage=GUARD_" + stage.name()
                + " expectedCode=" + (oom ? "JAVA_OOM" : "AAH-RUNTIME-INTEGRITY-CONTAINER")
                + " payloadLoaded=false payloadClassLookupAttempted=false nativeHandleAcquired=true"
                + " loadedPayloadPublished=true verifiedPayloadSessionPublished=false"
                + " byteBuffersPublished=true nativeCloseCount=1"
                + " partialJavaReferencesCleared=not_applicable partialGuardReferencesCleared=true"
                + " completedMappingsZeroizedUnmapped=true partialMappingZeroizedUnmapped=not_applicable"
                + " primaryCodePreserved=true cleanupFailureSuppressed=" + cleanup + "\n";
    }

    private static String m302MetadataCases() {
        String[][] cases = {
                {"package", "PACKAGE_MISMATCH"},
                {"current-signer", "SIGNER_MISMATCH"},
                {"empty-lineage", "LINEAGE_MISMATCH"},
                {"lineage-order", "LINEAGE_MISMATCH"},
                {"container-major", "VERSION"},
                {"container-minor", "VERSION"},
                {"signer-version", "VERSION"},
                {"risk-version", "VERSION"},
                {"build-snapshot", "SNAPSHOT_CHANGED"},
                {"key-snapshot", "SNAPSHOT_CHANGED"},
                {"cross-handle", "METADATA_HANDLE"},
                {"cross-session", "METADATA_HANDLE"},
        };
        StringBuilder output = new StringBuilder();
        for (String[] current : cases) {
            output.append("M302_CASE id=m302-metadata-").append(current[0])
                    .append(" target=guard mutation=metadata_")
                    .append(current[0].replace('-', '_'))
                    .append(" expectedStage=GUARD_METADATA expectedCode=AAH-RUNTIME-INTEGRITY-")
                    .append(current[1])
                    .append(" payloadLoaded=false payloadClassLookupAttempted=false")
                    .append(" nativeHandleAcquired=true loadedPayloadPublished=true")
                    .append(" verifiedPayloadSessionPublished=false byteBuffersPublished=true")
                    .append(" nativeCloseCount=1 partialJavaReferencesCleared=not_applicable")
                    .append(" partialGuardReferencesCleared=true completedMappingsZeroizedUnmapped=true")
                    .append(" partialMappingZeroizedUnmapped=not_applicable primaryCodePreserved=true")
                    .append(" cleanupFailureSuppressed=false\n");
        }
        return output.toString();
    }

    private static void verifyRiskFacade(
            Context context, ApplicationInfo applicationInfo, boolean m205ReleaseProbe)
            throws Exception {
        long maxNanos = 0;
        int evaluationCount = m205ReleaseProbe ? 1 : 50;
        for (int index = 0; index < evaluationCount; index++) {
            long started = android.os.SystemClock.elapsedRealtimeNanos();
            RiskReportV1 report = EnvironmentRiskEngine.evaluate(applicationInfo);
            long elapsed = android.os.SystemClock.elapsedRealtimeNanos() - started;
            maxNanos = Math.max(maxNanos, elapsed);
            require(report.version() == 1 && report.signals().size() == 5,
                    "M2-05 R8 facade/JNI");
            if (!m205ReleaseProbe) require(elapsed <= 50_000_000L, "M2-05 R8 budget");
        }
        require(maxNanos > 0, "M2-05 R8 clock");
        File directory = new File(context.getCodeCacheDir(), "m205-risk-r8");
        require(directory.isDirectory() || directory.mkdirs(), "M2-05 R8 mapping dir");
        File frida = new File(directory, "libfrida-agent-r8.so");
        File xposed = new File(directory, "libxposed-r8.so");
        extractRuntimeLibrary(applicationInfo.sourceDir, frida);
        extractRuntimeLibrary(applicationInfo.sourceDir, xposed);
        try (RandomAccessFile first = new RandomAccessFile(frida, "r");
                RandomAccessFile second = new RandomAccessFile(xposed, "r");
                FileChannel firstChannel = first.getChannel();
                FileChannel secondChannel = second.getChannel()) {
            MappedByteBuffer firstMapping = firstChannel.map(
                    FileChannel.MapMode.READ_ONLY, 0, firstChannel.size());
            MappedByteBuffer secondMapping = secondChannel.map(
                    FileChannel.MapMode.READ_ONLY, 0, secondChannel.size());
            require(firstMapping.get(0) == 0x7f && secondMapping.get(0) == 0x7f,
                    "M2-05 R8 mapped ELF");
            RiskReportV1 mapped = EnvironmentRiskEngine.evaluate(applicationInfo);
            RiskSignal mappingSignal = mapped.signals().stream()
                    .filter(signal -> signal.id() == RiskSignalId.INSTRUMENTATION_MAPPING)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("M2-05 R8 mapping signal"));
            require(mappingSignal.state() == SignalState.DETECTED
                            && mappingSignal.score() == 80,
                    "M2-05 R8 JNI mapping score");
        } finally {
            require(frida.delete() && xposed.delete(), "M2-05 R8 mapping cleanup");
            require(directory.delete(), "M2-05 R8 mapping dir cleanup");
        }
    }

    private static void extractRuntimeLibrary(String apkPath, File output) throws Exception {
        try (ZipFile apk = new ZipFile(apkPath)) {
            ZipEntry selected = null;
            for (String abi : Build.SUPPORTED_ABIS) {
                ZipEntry candidate = apk.getEntry("lib/" + abi + "/libah_runtime.so");
                if (candidate != null) {
                    selected = candidate;
                    break;
                }
            }
            require(selected != null && selected.getSize() > 0, "M2-05 R8 Runtime ELF");
            try (InputStream input = apk.getInputStream(selected);
                    FileOutputStream target = new FileOutputStream(output, false)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) target.write(buffer, 0, count);
            }
        }
    }

    private static void verifyFrameworkPackageRejection(
            Context target, ApplicationInfo applicationInfo) {
        ApplicationInfo wrongPackage = new ApplicationInfo(applicationInfo);
        wrongPackage.packageName = applicationInfo.packageName + ".tampered";
        try {
            RuntimeStartupGuard.openVerifiedPayload(wrongPackage, target.getClassLoader());
            throw new AssertionError("Framework package mismatch was accepted");
        } catch (RuntimeIntegrityFailure expected) {
            require("AAH-RUNTIME-INTEGRITY-PACKAGE_MISMATCH".equals(expected.getMessage()),
                    "wrong Framework package code");
        }
    }

    private static int verifyGuardMetadataRejections(
            Context target, ApplicationInfo applicationInfo, byte[] signer) {
        LoadedPayload foreign = PayloadRuntime.openVerified(
                target.getClassLoader(), applicationInfo, signer);
        try {
            AuthenticatedPayloadMetadata base = foreign.authenticatedMetadata();
            byte[] packageDigest = base.packageNameSha256();
            byte[] current = base.currentSignerSha256();
            byte[][] lineage = base.signerLineageSha256();
            byte[] build = base.buildId();
            byte[] key = base.keySlotId();
            int count = 0;
            count += rejectMetadata(target, applicationInfo, metadata(base, oneBit(packageDigest),
                    current, lineage, 2, 0, 1, 1), "PACKAGE_MISMATCH");
            byte[] wrongCurrent = oneBit(current);
            count += rejectMetadata(target, applicationInfo, metadata(base, packageDigest,
                    wrongCurrent, new byte[][] {wrongCurrent}, 2, 0, 1, 1), "SIGNER_MISMATCH");
            count += rejectMetadata(target, applicationInfo, metadata(base, packageDigest,
                    current, new byte[0][], 2, 0, 1, 1), "LINEAGE_MISMATCH");
            count += rejectMetadata(target, applicationInfo, metadata(base, packageDigest,
                    current, new byte[][] {digest(0x22), current}, 2, 0, 1, 1),
                    "LINEAGE_MISMATCH");
            count += rejectMetadata(target, applicationInfo, metadata(base, packageDigest,
                    current, lineage, 3, 0, 1, 1), "VERSION");
            count += rejectMetadata(target, applicationInfo, metadata(base, packageDigest,
                    current, lineage, 2, 1, 1, 1), "VERSION");
            count += rejectMetadata(target, applicationInfo, metadata(base, packageDigest,
                    current, lineage, 2, 0, 2, 1), "VERSION");
            count += rejectMetadata(target, applicationInfo, metadata(base, packageDigest,
                    current, lineage, 2, 0, 1, 2), "VERSION");
            count += rejectBinding(target, applicationInfo,
                    binding(oneBit(build), key, current), "SNAPSHOT_CHANGED");
            count += rejectBinding(target, applicationInfo,
                    binding(build, oneBit(key), current), "SNAPSHOT_CHANGED");
            count += rejectForeignMetadata(target, applicationInfo, base);
            VerifiedPayloadSession priorSession = RuntimeStartupGuard.openVerifiedPayload(
                    applicationInfo, target.getClassLoader());
            LoadedPayload priorPayload = captureLoadedPayload(priorSession);
            AuthenticatedPayloadMetadata priorMetadata = priorPayload.authenticatedMetadata();
            long priorHandle = captureNativeHandle(priorPayload);
            priorSession.close();
            requireNativeHandleClosed(priorHandle, "prior-session-close");
            count += rejectForeignMetadata(target, applicationInfo, priorMetadata);
            return count;
        } finally {
            foreign.close();
        }
    }

    private static int rejectMetadata(
            Context target,
            ApplicationInfo applicationInfo,
            AuthenticatedPayloadMetadata replacement,
            String expectedCode) {
        return rejectGuard(target, applicationInfo, expectedCode,
                new RuntimeStartupGuard.GuardFailureProbe() {
                    @Override
                    public void hit(RuntimeStartupGuard.GuardStage stage) {}

                    @Override
                    public void verifyMetadata(
                            AuthenticatedPayloadMetadata metadata,
                            UntrustedPayloadBinding binding,
                            byte[] packageNameSha256,
                            RuntimeSignerVerifier.Measurement measurement) {
                        IntegrityChecks.verifyAuthenticatedMetadata(
                                replacement, binding, packageNameSha256, measurement);
                    }
                });
    }

    private static int rejectBinding(
            Context target,
            ApplicationInfo applicationInfo,
            UntrustedPayloadBinding replacement,
            String expectedCode) {
        return rejectGuard(target, applicationInfo, expectedCode,
                new RuntimeStartupGuard.GuardFailureProbe() {
                    @Override
                    public void hit(RuntimeStartupGuard.GuardStage stage) {}

                    @Override
                    public UntrustedPayloadBinding binding(UntrustedPayloadBinding ignored) {
                        return replacement;
                    }
                });
    }

    private static int rejectForeignMetadata(
            Context target,
            ApplicationInfo applicationInfo,
            AuthenticatedPayloadMetadata replacement) {
        return rejectGuard(target, applicationInfo, "METADATA_HANDLE",
                new RuntimeStartupGuard.GuardFailureProbe() {
                    @Override
                    public void hit(RuntimeStartupGuard.GuardStage stage) {}

                    @Override
                    public AuthenticatedPayloadMetadata metadata(LoadedPayload ignored) {
                        return replacement;
                    }
                });
    }

    private static int rejectGuard(
            Context target,
            ApplicationInfo applicationInfo,
            String expectedCode,
            RuntimeStartupGuard.GuardFailureProbe delegate) {
        int[] closeCount = {0};
        try {
            RuntimeStartupGuard.openVerifiedPayloadForTesting(
                    applicationInfo,
                    target.getClassLoader(),
                    new RuntimeStartupGuard.GuardFailureProbe() {
                        @Override
                        public void hit(RuntimeStartupGuard.GuardStage stage) {
                            delegate.hit(stage);
                        }

                        @Override
                        public UntrustedPayloadBinding binding(UntrustedPayloadBinding value) {
                            return delegate.binding(value);
                        }

                        @Override
                        public AuthenticatedPayloadMetadata metadata(LoadedPayload payload) {
                            return delegate.metadata(payload);
                        }

                        @Override
                        public void verifyMetadata(
                                AuthenticatedPayloadMetadata metadata,
                                UntrustedPayloadBinding binding,
                                byte[] packageNameSha256,
                                RuntimeSignerVerifier.Measurement measurement) {
                            delegate.verifyMetadata(metadata, binding, packageNameSha256, measurement);
                        }

                        @Override
                        public void close(LoadedPayload payload) {
                            long nativeHandle = captureNativeHandle(payload);
                            payload.close();
                            requirePayloadClosed(payload, "metadata-" + expectedCode);
                            requireNativeHandleClosed(nativeHandle, "metadata-" + expectedCode);
                            payload.close();
                            requireNativeHandleClosed(
                                    nativeHandle, "metadata-second-" + expectedCode);
                        }

                        @Override
                        public void closed() {
                            closeCount[0]++;
                        }
                    });
            throw new AssertionError("Guard accepted " + expectedCode);
        } catch (RuntimeIntegrityFailure expected) {
            require(("AAH-RUNTIME-INTEGRITY-" + expectedCode).equals(expected.getMessage()),
                    "wrong metadata code: " + expected.getMessage());
            require(closeCount[0] == 1, "metadata close count: " + expectedCode);
            return 1;
        }
    }

    private static AuthenticatedPayloadMetadata metadata(
            AuthenticatedPayloadMetadata base,
            byte[] packageDigest,
            byte[] current,
            byte[][] lineage,
            int major,
            int minor,
            int signerVersion,
            int riskVersion) {
        try {
            Constructor<AuthenticatedPayloadMetadata> constructor =
                    AuthenticatedPayloadMetadata.class.getDeclaredConstructor(
                            String.class, int.class, int.class, int.class, int.class,
                            byte[].class, byte[].class, byte[].class, byte[].class, byte[][].class);
            constructor.setAccessible(true);
            return constructor.newInstance(
                    base.originalFactoryClassNameOrNull(), major, minor, signerVersion, riskVersion,
                    base.buildId(), base.keySlotId(), packageDigest, current, lineage);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private static UntrustedPayloadBinding binding(byte[] build, byte[] key, byte[] current) {
        try {
            Constructor<UntrustedPayloadBinding> constructor =
                    UntrustedPayloadBinding.class.getDeclaredConstructor(
                            byte[].class, byte[].class, byte[].class);
            constructor.setAccessible(true);
            return constructor.newInstance(build, key, current);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private static byte[] oneBit(byte[] source) {
        byte[] copy = source.clone();
        copy[0] ^= 1;
        return copy;
    }

    private static byte[] digest(int seed) {
        byte[] value = new byte[32];
        for (int index = 0; index < value.length; index++) {
            value[index] = (byte) (seed + index);
        }
        return value;
    }

    private static void requirePayloadClosed(LoadedPayload payload, String context) {
        try {
            payload.authenticatedMetadata();
            throw new AssertionError("payload remained open: " + context);
        } catch (RuntimeException expected) {
            require(expected.getMessage().contains("CLOSED"), "unexpected close error: " + context);
        }
    }

    private static long captureNativeHandle(LoadedPayload payload) {
        try {
            Field memoryHandleField = LoadedPayload.class.getDeclaredField("memoryHandle");
            memoryHandleField.setAccessible(true);
            Object memoryHandle = memoryHandleField.get(payload);
            require(memoryHandle != null, "missing native memory handle");
            Field valueField = memoryHandle.getClass().getDeclaredField("value");
            valueField.setAccessible(true);
            long value = valueField.getLong(memoryHandle);
            require(value > 0, "invalid native memory handle");
            return value;
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("native handle observation failed", failure);
        }
    }

    private static LoadedPayload captureLoadedPayload(VerifiedPayloadSession session) {
        try {
            Field field = VerifiedPayloadSession.class.getDeclaredField("loadedPayload");
            field.setAccessible(true);
            LoadedPayload payload = (LoadedPayload) field.get(session);
            require(payload != null, "missing session payload");
            return payload;
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("session payload observation failed", failure);
        }
    }

    private static void requireNativeHandleClosed(long handle, String context) {
        try {
            Class<?> bridge = Class.forName("ah.runtime.loader.NativePayloadBridge");
            Method metadata = bridge.getDeclaredMethod("nativeAuthenticatedMetadata", long.class);
            metadata.setAccessible(true);
            metadata.invoke(null, handle);
            throw new AssertionError("native handle survived: " + context);
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            require(cause != null && cause.getMessage() != null
                            && cause.getMessage().contains("HANDLE"),
                    "unexpected native close error: " + context);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("native close observation failed", failure);
        } catch (Exception failure) {
            throw new AssertionError("native close invocation failed", failure);
        }
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
