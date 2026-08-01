package ah.fixtures.android;

import ah.runtime.bootstrap.ClassLoaderProbe;
import ah.runtime.bootstrap.EarlySignerProbe;
import ah.runtime.bootstrap.NativeLibrarySearchPath;
import ah.runtime.bootstrap.ProbeEvent;
import ah.runtime.bootstrap.ShellAppComponentFactory;
import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Dependency-free on-device acceptance runner for the bounded M0-05 PoC. */
public final class CompatibilityPocRunner extends Instrumentation {
    private static final String TEST_CLASS = CompatibilityPocRunner.class.getName();
    private static final String TEST_NAME = "m0_05_compatibility_gate";
    private static final String PAYLOAD_ACTIVITY =
            "ah.fixtures.android.payload.PayloadActivity";
    private static final String PAYLOAD_SERVICE =
            "ah.fixtures.android.payload.PayloadService";
    private static final String PAYLOAD_RECEIVER =
            "ah.fixtures.android.payload.PayloadReceiver";
    private static final String SECONDARY_API =
            "ah.fixtures.android.payload.SecondaryApi";
    private boolean expectedOriginalFactory = true;
    private File externalNegativeDirectory;

    @Override
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        if (arguments != null && arguments.containsKey("expected_factory")) {
            expectedOriginalFactory =
                    Boolean.parseBoolean(arguments.getString("expected_factory", "true"));
        }
        if (arguments != null && arguments.containsKey("negative_dir")) {
            externalNegativeDirectory = new File(arguments.getString("negative_dir"));
        }
        start();
    }

    @Override
    public void onStart() {
        sendTestStatus(1, "");
        Bundle result = new Bundle();
        try {
            String diagnostic = runGate();
            sendTestStatus(0, diagnostic);
            result.putString("stream", "\n" + diagnostic + "\nOK (1 test)\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable failure) {
            String trace = stackTrace(failure);
            sendTestStatus(-2, trace);
            result.putString("stream", "\nFAILURES!!!\n" + trace);
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    private String runGate() throws Exception {
        Activity activity = launchPayloadActivity();
        try {
            startAndStopOtherComponents();
            waitForFactoryCounts();
            verifyLifecycleAndDelegation(activity);
            verifySignerCrossCheck();
            verifyMetadataIndependence();
            if (expectedOriginalFactory) {
                verifyStartupFailureCleanup();
                verifyDelegatedFailurePreservesCause();
            }
            verifySignerFailures();
            verifyExternalStartupFailures();
            verifyNativePathDecisionAndFailures();
            verifyNoPlaintextDexFiles();
            return diagnostic();
        } finally {
            activity.finish();
            getTargetContext().stopService(
                    new Intent().setClassName(getTargetContext(), PAYLOAD_SERVICE));
        }
    }

    private Activity launchPayloadActivity() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName(getTargetContext().getPackageName(), PAYLOAD_ACTIVITY);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return startActivitySync(intent);
    }

    private void startAndStopOtherComponents() {
        Intent service = new Intent();
        service.setClassName(getTargetContext(), PAYLOAD_SERVICE);
        require(getTargetContext().startService(service) != null, "service did not start");

        Intent receiver = new Intent("ah.fixtures.android.M0_05_RECEIVER");
        receiver.setClassName(getTargetContext(), PAYLOAD_RECEIVER);
        getTargetContext().sendBroadcast(receiver);
    }

    private void waitForFactoryCounts() throws Exception {
        long deadline = System.nanoTime() + 5_000_000_000L;
        while (System.nanoTime() < deadline) {
            boolean componentEvents =
                    countEvents(ClassLoaderProbe.APPLICATION_CREATED) == 1
                            && countEvents(ClassLoaderProbe.ACTIVITY_CREATED) == 1
                            && countEvents(ClassLoaderProbe.SERVICE_CREATED) == 1
                            && countEvents(ClassLoaderProbe.RECEIVER_CREATED) == 1
                            && countEvents(ClassLoaderProbe.PROVIDER_CREATED) == 1;
            int expectedCount = expectedOriginalFactory ? 1 : 0;
            boolean factoryCounts =
                    ProbeSignal.factoryCount("classloader") == expectedCount
                            && ProbeSignal.factoryCount("application") == expectedCount
                            && ProbeSignal.factoryCount("activity") == expectedCount
                            && ProbeSignal.factoryCount("service") == expectedCount
                            && ProbeSignal.factoryCount("receiver") == expectedCount
                            && ProbeSignal.factoryCount("provider") == expectedCount;
            if (componentEvents && factoryCounts) {
                return;
            }
            Thread.sleep(25L);
        }
        throw new AssertionError("component factory callbacks did not converge to one each");
    }

    private void verifyLifecycleAndDelegation(Activity activity) throws Exception {
        ProbeEvent signer = onlyEvent(ClassLoaderProbe.EARLY_SIGNER_VERIFIED);
        ProbeEvent parsed = onlyEvent(ClassLoaderProbe.EARLY_CONFIG_PARSED);
        ProbeEvent authenticated =
                onlyEvent(ClassLoaderProbe.EARLY_CONFIG_APK_AUTHENTICATED);
        ProbeEvent provisional = onlyEvent(ClassLoaderProbe.PROVISIONAL_LOADER_CREATED);
        ProbeEvent loader = onlyEvent(ClassLoaderProbe.LOADER_CREATED);
        ProbeEvent provider = onlyEvent(ClassLoaderProbe.PROVIDER_CREATED);
        ProbeEvent applicationOnCreate = onlyEvent(ClassLoaderProbe.APPLICATION_ON_CREATE);
        onlyEvent(ClassLoaderProbe.JNI_LOADED);
        onlyEvent(ClassLoaderProbe.APPLICATION_CREATED);
        onlyEvent(ClassLoaderProbe.ACTIVITY_CREATED);
        onlyEvent(ClassLoaderProbe.SERVICE_CREATED);
        onlyEvent(ClassLoaderProbe.RECEIVER_CREATED);

        require(signer.sequence() < parsed.sequence(), "signer must precede ConfigV2 parse");
        require(parsed.sequence() < authenticated.sequence(), "ConfigV2 parse must precede auth");
        require(
                authenticated.sequence() < provisional.sequence(),
                "ConfigV2 auth must precede provisional loader");
        if (expectedOriginalFactory) {
            ProbeEvent factory = onlyEvent(ClassLoaderProbe.ORIGINAL_FACTORY_CREATED);
            ProbeEvent hook =
                    onlyEvent(ClassLoaderProbe.ORIGINAL_FACTORY_CLASSLOADER_DELEGATED);
            require(
                    provisional.sequence() < factory.sequence(),
                    "provisional loader must precede original Factory");
            require(factory.sequence() < hook.sequence(), "Factory creation must precede hook");
            require(hook.sequence() < loader.sequence(), "Factory hook must precede final loader");
            require(
                    hook.classLoader() == loader.classLoader(),
                    "delegated and final loader identity differs");
        } else {
            require(
                    countEvents(ClassLoaderProbe.ORIGINAL_FACTORY_CREATED) == 0
                            && countEvents(
                                            ClassLoaderProbe
                                                    .ORIGINAL_FACTORY_CLASSLOADER_DELEGATED)
                                    == 0,
                    "no-Factory ConfigV2 emitted original Factory events");
        }
        require(loader.sequence() < provider.sequence(), "final loader must precede provider");
        require(
                provider.sequence() < applicationOnCreate.sequence(),
                "provider must precede Application.onCreate");
        require(
                "dalvik.system.InMemoryDexClassLoader".equals(loader.classLoaderName()),
                "unexpected payload loader type");
        require(
                activity.getClass().getClassLoader() == loader.classLoader(),
                "activity did not use the payload loader");
        require(
                provisional.classLoader() == loader.classLoader(),
                "provisional and final loader identity differs");

        for (String component :
                new String[] {
                    "classloader", "application", "activity", "service", "receiver", "provider"
                }) {
            equal(
                    expectedOriginalFactory ? 1 : 0,
                    ProbeSignal.factoryCount(component),
                    component + " delegation count");
        }
        equal("M0-05-CLASSES2:provider", ProbeSignal.providerMarker(), "provider classes2 marker");
        equal("M0-05-CLASSES2:activity", ProbeSignal.activityMarker(), "activity classes2 marker");
        equal("M0-05-JNI-FIXED", ProbeSignal.jniMarker(), "JNI marker");

        try {
            getClass().getClassLoader().loadClass(SECONDARY_API);
            throw new AssertionError("parent loader resolved classes2-only type");
        } catch (ClassNotFoundException expected) {
            // Expected: the marker exists only in the second in-memory DEX.
        }
        equal(
                SECONDARY_API,
                loader.classLoader().loadClass(SECONDARY_API).getName(),
                "classes2 lookup");
    }

    private void verifySignerCrossCheck() throws Exception {
        byte[] early = ClassLoaderProbe.earlySignerSha256();
        require(early != null && early.length == 32, "early signer digest is absent");
        PackageInfo packageInfo =
                getTargetContext()
                        .getPackageManager()
                        .getPackageInfo(
                                getTargetContext().getPackageName(),
                                PackageManager.GET_SIGNING_CERTIFICATES);
        android.content.pm.Signature[] current = packageInfo.signingInfo.getApkContentsSigners();
        require(current.length == 1, "SigningInfo current signer is not unique");
        byte[] observed = MessageDigest.getInstance("SHA-256").digest(current[0].toByteArray());
        require(MessageDigest.isEqual(early, observed), "early and post-start signer differ");
        EarlySignerProbe.verify(getTargetContext().getApplicationInfo()).requireMatches(observed);
        try {
            EarlySignerProbe.verify(getTargetContext().getApplicationInfo()).requireMatches(new byte[32]);
            throw new AssertionError("signer mismatch did not fail");
        } catch (IllegalStateException expected) {
            requireCode(expected, "AAH-P008");
        }
    }

    private void verifyMetadataIndependence() {
        ApplicationInfo actual = getTargetContext().getApplicationInfo();
        ApplicationInfo noMetadata = new ApplicationInfo(actual);
        noMetadata.metaData = null;
        ClassLoader noMetadataLoader =
                new ShellAppComponentFactory()
                        .instantiateClassLoader(getClass().getClassLoader(), noMetadata);

        ApplicationInfo arbitraryMetadata = new ApplicationInfo(actual);
        arbitraryMetadata.metaData = new Bundle();
        arbitraryMetadata.metaData.putString("fixture.unrelated", "ignored");
        ClassLoader arbitraryMetadataLoader =
                new ShellAppComponentFactory()
                        .instantiateClassLoader(getClass().getClassLoader(), arbitraryMetadata);
        require(noMetadataLoader != null, "null metadata changed the positive path");
        require(arbitraryMetadataLoader != null, "unrelated metadata changed the positive path");
    }

    private void verifyStartupFailureCleanup() {
        ApplicationInfo actual = getTargetContext().getApplicationInfo();
        assertStartupFailure(actual, true, "normal", false, "AAH-P002");
        assertStartupFailure(actual, false, "null", false, "AAH-P003");
        assertStartupFailure(actual, false, "exception", false, "AAH-P003");
        assertStartupFailure(actual, false, "reentry", false, "AAH-P003");
        assertStartupFailure(actual, false, "invalid-final", false, "AAH-P003");
        assertStartupFailure(actual, false, "exception", true, "AAH-P003");
    }

    private void verifySignerFailures() throws Exception {
        ApplicationInfo unreadable = new ApplicationInfo();
        unreadable.sourceDir =
                new File(getTargetContext().getCacheDir(), "m0-05-does-not-exist.apk")
                        .getAbsolutePath();
        requireSignerFailure(unreadable, "AAH-P005");

        File invalid = File.createTempFile("m0-05-invalid-signer-", ".apk", getTargetContext().getCacheDir());
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(invalid))) {
            zip.putNextEntry(new ZipEntry("AndroidManifest.xml"));
            zip.write(new byte[] {1, 2, 3, 4});
            zip.closeEntry();
        }
        try {
            ApplicationInfo unsigned = new ApplicationInfo();
            unsigned.sourceDir = invalid.getAbsolutePath();
            requireSignerFailure(unsigned, "AAH-P006");
        } finally {
            invalid.delete();
        }
    }

    private void verifyExternalStartupFailures() {
        if (externalNegativeDirectory == null) {
            return;
        }
        String[][] cases =
                new String[][] {
                    {"m0-05-config-major.apk", "AAH-P009"},
                    {"m0-05-config-reserved.apk", "AAH-P009"},
                    {"m0-05-config-signer-mismatch.apk", "AAH-P010"},
                    {"m0-05-config-factory-flags.apk", "AAH-P009"},
                    {"m0-05-config-invalid-utf8.apk", "AAH-P009"},
                    {"m0-05-config-nul.apk", "AAH-P009"},
                    {"m0-05-config-slot-tail.apk", "AAH-P009"},
                    {"m0-05-config-deflate.apk", "AAH-P009"},
                    {"m0-05-config-descriptor.apk", "AAH-P009"},
                    {"m0-05-config-crc.apk", "AAH-P009"},
                    {"m0-05-config-length.apk", "AAH-P009"},
                    {"m0-05-payload-corrupt.apk", "AAH-P001"},
                    {"m0-05-wrong-signer.apk", "AAH-P008"},
                    {"m0-05-multi-signer.apk", "AAH-P007"},
                    {"m0-05-config-duplicate-unsigned.apk", "AAH-P006"},
                    {"m0-05-truncated-zip-unsigned.apk", "AAH-P006"},
                    {"m0-05-no-factory-unsigned.apk", "AAH-P006"}
                };
        ApplicationInfo actual = getTargetContext().getApplicationInfo();
        for (String[] testCase : cases) {
            File apk = new File(externalNegativeDirectory, testCase[0]);
            require(apk.isFile() && apk.canRead(), "external negative APK is unavailable: " + testCase[0]);
            ApplicationInfo mutated = new ApplicationInfo(actual);
            mutated.sourceDir = apk.getAbsolutePath();
            int loadersBefore = countEvents(ClassLoaderProbe.LOADER_CREATED);
            try {
                new ShellAppComponentFactory()
                        .instantiateClassLoader(getClass().getClassLoader(), mutated);
                throw new AssertionError(testCase[0] + " did not fail");
            } catch (IllegalStateException expected) {
                requireCode(expected, testCase[1]);
            }
            equal(
                    loadersBefore,
                    countEvents(ClassLoaderProbe.LOADER_CREATED),
                    testCase[0] + " loader count");
        }
    }

    private void verifyDelegatedFailurePreservesCause() {
        ApplicationInfo actual = getTargetContext().getApplicationInfo();
        ShellAppComponentFactory shell = new ShellAppComponentFactory();
        ClassLoader loader = shell.instantiateClassLoader(getClass().getClassLoader(), actual);
        ProbeSignal.setFailActivityDelegation(true);
        try {
            shell.instantiateActivity(loader, PAYLOAD_ACTIVITY, new Intent());
            throw new AssertionError("delegated failure did not propagate");
        } catch (InstantiationException expected) {
            requireCode(expected, "AAH-P003");
            require(expected.getCause() instanceof InstantiationException, "delegate cause was lost");
            require(
                    "synthetic delegated activity failure".equals(expected.getCause().getMessage()),
                    "delegate cause message changed");
        } catch (ClassNotFoundException | IllegalAccessException unexpected) {
            throw new AssertionError("delegated failure changed checked type", unexpected);
        } finally {
            ProbeSignal.setFailActivityDelegation(false);
        }
    }

    private void verifyNativePathDecisionAndFailures() throws Exception {
        NativeLibrarySearchPath path = ClassLoaderProbe.nativeLibrarySearchPath();
        require(path != null, "native search path decision is absent");
        require(path.apkDirectoryIncluded(), "APK native directory was not included");
        boolean extracts =
                (getTargetContext().getApplicationInfo().flags
                                & ApplicationInfo.FLAG_EXTRACT_NATIVE_LIBS)
                        != 0;
        equal(extracts, path.extractedDirectoryIncluded(), "extracted native path decision");
        require(
                path.classLoaderSearchPath().contains("!/lib/" + path.selectedAbi()),
                "APK native path has the wrong ABI");

        String[] processAbis =
                Process.is64Bit() ? Build.SUPPORTED_64_BIT_ABIS : Build.SUPPORTED_32_BIT_ABIS;
        require(processAbis != null && processAbis.length > 0, "process ABI list is empty");
        File missing = createNativeProbeApk("lib/not-a-process-abi/libfixture_jni.so");
        File nonCanonical =
                createNativeProbeApk("lib/" + processAbis[0] + "/../libfixture_jni.so");
        try {
            requireNativeFailure(missing);
            requireNativeFailure(nonCanonical);
        } finally {
            missing.delete();
            nonCanonical.delete();
        }
    }

    private void verifyNoPlaintextDexFiles() throws Exception {
        File[] roots =
                new File[] {
                    getTargetContext().getFilesDir(),
                    getTargetContext().getCacheDir(),
                    getTargetContext().getCodeCacheDir(),
                    getTargetContext().getNoBackupFilesDir(),
                    getTargetContext().getExternalFilesDir(null),
                    getTargetContext().getExternalCacheDir()
                };
        int scanned = 0;
        for (File root : roots) {
            scanned += scanForDexMagic(root, 0);
        }
        require(scanned >= 0, "filesystem scan did not complete");
    }

    private String diagnostic() {
        NativeLibrarySearchPath path = ClassLoaderProbe.nativeLibrarySearchPath();
        return "EARLY_SIGNER_VERIFIED<EARLY_CONFIG_PARSED"
                + "<EARLY_CONFIG_APK_AUTHENTICATED<PROVISIONAL_LOADER_CREATED"
                + (expectedOriginalFactory
                        ? "<ORIGINAL_FACTORY_CREATED<ORIGINAL_FACTORY_CLASSLOADER_DELEGATED"
                        : "")
                + "<LOADER_CREATED<PROVIDER_CREATED<APPLICATION_ON_CREATE"
                + "; original_factory="
                + expectedOriginalFactory
                + "; component_counts="
                + (expectedOriginalFactory ? "1,1,1,1,1,1" : "0,0,0,0,0,0")
                + "; classes2=provider+activity"
                + "; jni=M0-05-JNI-FIXED"
                + "; abi="
                + path.selectedAbi()
                + "; extracted="
                + path.extractedDirectoryIncluded()
                + "; signer_digest_match=true"
                + "; metadata_independence=2"
                + "; startup_cleanup_negative="
                + (expectedOriginalFactory ? 6 : 0)
                + "; signer_negative=2"
                + "; external_startup_negative="
                + (externalNegativeDirectory == null ? 0 : 17)
                + "; native_negative=2"
                + "; plaintext_dex_files=0";
    }

    private void assertStartupFailure(
            ApplicationInfo applicationInfo,
            boolean failConstruction,
            String hookMode,
            boolean failClose,
            String code) {
        int finalLoadersBefore = countEvents(ClassLoaderProbe.LOADER_CREATED);
        ShellAppComponentFactory shell = new ShellAppComponentFactory();
        ProbeSignal.setFailFactoryConstruction(failConstruction);
        ProbeSignal.setClassLoaderHookMode(hookMode);
        ClassLoaderProbe.setFailSessionCloseForTesting(failClose);
        try {
            try {
                shell.instantiateClassLoader(getClass().getClassLoader(), applicationInfo);
                throw new AssertionError(code + " startup failure did not occur");
            } catch (IllegalStateException expected) {
                requireCode(expected, code);
            }
        } finally {
            ProbeSignal.resetFailureInjection();
        }
        equal(1, shell.testOnlyLastFailedSessionCloseCount(), "failed session close count");
        require(shell.testOnlyLastFailedBuffersCleared(), "failed session buffers were not cleared");
        require(shell.testOnlyFailureReferencesCleared(), "failed startup retained partial references");
        equal(finalLoadersBefore, countEvents(ClassLoaderProbe.LOADER_CREATED), "failed final loader count");
        try {
            shell.instantiateClassLoader(getClass().getClassLoader(), applicationInfo);
            throw new AssertionError("cached startup failure did not recur");
        } catch (IllegalStateException repeated) {
            requireCode(repeated, code);
            require(repeated.getCause() == null, "cached failure retained a Throwable cause");
        }
        equal(1, shell.testOnlyLastFailedSessionCloseCount(), "cached failure closed twice");
    }

    private File createNativeProbeApk(String entryName) throws Exception {
        File result = File.createTempFile("m0-05-native-", ".apk", getTargetContext().getCacheDir());
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(result))) {
            zip.putNextEntry(new ZipEntry(entryName));
            zip.write(new byte[] {1});
            zip.closeEntry();
        }
        return result;
    }

    private void requireNativeFailure(File apk) {
        ApplicationInfo info = new ApplicationInfo(getTargetContext().getApplicationInfo());
        info.sourceDir = apk.getAbsolutePath();
        info.nativeLibraryDir = null;
        try {
            ah.runtime.bootstrap.NativeLibrarySearchPathResolver.resolve(info);
            throw new AssertionError("AAH-P004 native path failure did not occur");
        } catch (IllegalStateException expected) {
            requireCode(expected, "AAH-P004");
        }
    }

    private int scanForDexMagic(File file, int depth) throws Exception {
        if (file == null || !file.exists() || depth > 16) {
            return 0;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null) {
                return 0;
            }
            int count = 0;
            for (File child : children) {
                count += scanForDexMagic(child, depth + 1);
                if (count > 4096) {
                    throw new AssertionError("filesystem scan exceeded the fixture bound");
                }
            }
            return count;
        }
        byte[] prefix = new byte[4];
        try (FileInputStream input = new FileInputStream(file)) {
            int read = input.read(prefix);
            if (read == 4
                    && prefix[0] == 'd'
                    && prefix[1] == 'e'
                    && prefix[2] == 'x'
                    && prefix[3] == '\n') {
                throw new AssertionError("plaintext DEX file was written by the fixture runtime");
            }
        }
        return 1;
    }

    private void requireSignerFailure(ApplicationInfo applicationInfo, String code) {
        try {
            EarlySignerProbe.verify(applicationInfo);
            throw new AssertionError(code + " failure did not occur");
        } catch (IllegalStateException expected) {
            requireCode(expected, code);
        }
    }

    private ProbeEvent onlyEvent(String type) {
        ProbeEvent result = null;
        for (ProbeEvent event : ClassLoaderProbe.snapshot()) {
            if (type.equals(event.type())) {
                if (result != null) {
                    throw new AssertionError("duplicate event " + type);
                }
                result = event;
            }
        }
        if (result == null) {
            throw new AssertionError("missing event " + type);
        }
        return result;
    }

    private int countEvents(String type) {
        int count = 0;
        for (ProbeEvent event : ClassLoaderProbe.snapshot()) {
            if (type.equals(event.type())) {
                count++;
            }
        }
        return count;
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

    private static void requireCode(Throwable failure, String code) {
        require(
                failure.getMessage() != null && failure.getMessage().startsWith(code + ":"),
                "unexpected failure: " + failure);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void equal(Object expected, Object actual, String label) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        }
    }

    private static String stackTrace(Throwable failure) {
        StringWriter output = new StringWriter();
        failure.printStackTrace(new PrintWriter(output));
        return output.toString();
    }
}
