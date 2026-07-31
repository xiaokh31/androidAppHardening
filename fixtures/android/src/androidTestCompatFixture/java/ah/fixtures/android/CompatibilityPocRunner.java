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
import android.os.Bundle;
import java.io.File;
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

    @Override
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
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
            verifyMetadataAndFactoryFailures();
            verifyDelegatedFailurePreservesCause();
            verifySignerFailures();
            verifyNativePathDecision();
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
            if (ProbeSignal.factoryCount("application") == 1
                    && ProbeSignal.factoryCount("activity") == 1
                    && ProbeSignal.factoryCount("service") == 1
                    && ProbeSignal.factoryCount("receiver") == 1
                    && ProbeSignal.factoryCount("provider") == 1) {
                return;
            }
            Thread.sleep(25L);
        }
        throw new AssertionError("component factory callbacks did not converge to one each");
    }

    private void verifyLifecycleAndDelegation(Activity activity) throws Exception {
        ProbeEvent signer = onlyEvent(ClassLoaderProbe.EARLY_SIGNER_VERIFIED);
        ProbeEvent metadata = onlyEvent(ClassLoaderProbe.EARLY_METADATA_VERIFIED);
        ProbeEvent loader = onlyEvent(ClassLoaderProbe.LOADER_CREATED);
        ProbeEvent factory = onlyEvent(ClassLoaderProbe.ORIGINAL_FACTORY_CREATED);
        ProbeEvent provider = onlyEvent(ClassLoaderProbe.PROVIDER_CREATED);
        ProbeEvent applicationOnCreate = onlyEvent(ClassLoaderProbe.APPLICATION_ON_CREATE);
        onlyEvent(ClassLoaderProbe.JNI_LOADED);
        onlyEvent(ClassLoaderProbe.APPLICATION_CREATED);
        onlyEvent(ClassLoaderProbe.ACTIVITY_CREATED);
        onlyEvent(ClassLoaderProbe.SERVICE_CREATED);
        onlyEvent(ClassLoaderProbe.RECEIVER_CREATED);

        require(signer.sequence() < metadata.sequence(), "signer must precede metadata");
        require(metadata.sequence() < loader.sequence(), "metadata must precede loader");
        require(loader.sequence() < factory.sequence(), "loader must precede original factory");
        require(factory.sequence() < provider.sequence(), "factory must precede provider");
        require(
                provider.sequence() < applicationOnCreate.sequence(),
                "provider must precede Application.onCreate");
        require(
                "dalvik.system.InMemoryDexClassLoader".equals(loader.classLoaderName()),
                "unexpected payload loader type");
        require(
                activity.getClass().getClassLoader() == loader.classLoader(),
                "activity did not use the payload loader");

        for (String component :
                new String[] {"application", "activity", "service", "receiver", "provider"}) {
            equal(1, ProbeSignal.factoryCount(component), component + " delegation count");
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

    private void verifyMetadataAndFactoryFailures() {
        ApplicationInfo actual = getTargetContext().getApplicationInfo();
        int loaderEventsBefore = countEvents(ClassLoaderProbe.LOADER_CREATED);

        ApplicationInfo missing = new ApplicationInfo(actual);
        missing.metaData = new Bundle(actual.metaData);
        missing.metaData.remove("ah.runtime.container_major");
        requireFactoryFailure(missing, "AAH-P009");

        ApplicationInfo wrongType = new ApplicationInfo(actual);
        wrongType.metaData = new Bundle(actual.metaData);
        wrongType.metaData.putString("ah.runtime.risk_policy_version", "1");
        requireFactoryFailure(wrongType, "AAH-P009");

        ApplicationInfo empty = new ApplicationInfo(actual);
        empty.metaData = null;
        requireFactoryFailure(empty, "AAH-P009");
        equal(loaderEventsBefore, countEvents(ClassLoaderProbe.LOADER_CREATED), "metadata failure loader count");

        ApplicationInfo badFactory = new ApplicationInfo(actual);
        badFactory.metaData = new Bundle(actual.metaData);
        badFactory.metaData.putString(
                "ah.runtime.original_app_component_factory",
                "ah.fixtures.android.payload.DoesNotExistFactory");
        requireFactoryFailure(badFactory, "AAH-P002");

        ApplicationInfo noFactory = new ApplicationInfo(actual);
        noFactory.metaData = new Bundle(actual.metaData);
        noFactory.metaData.putBoolean("ah.runtime.has_original_app_component_factory", false);
        noFactory.metaData.remove("ah.runtime.original_app_component_factory");
        ShellAppComponentFactory shell = new ShellAppComponentFactory();
        ClassLoader loader = shell.instantiateClassLoader(getClass().getClassLoader(), noFactory);
        try {
            require(
                    shell.instantiateActivity(loader, PAYLOAD_ACTIVITY, new Intent()) != null,
                    "platform-default factory path returned null");
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("platform-default factory path failed", failure);
        }
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

    private void verifyNativePathDecision() {
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
    }

    private String diagnostic() {
        NativeLibrarySearchPath path = ClassLoaderProbe.nativeLibrarySearchPath();
        return "EARLY_SIGNER_VERIFIED<EARLY_METADATA_VERIFIED<LOADER_CREATED"
                + "<ORIGINAL_FACTORY_CREATED<PROVIDER_CREATED<APPLICATION_ON_CREATE"
                + "; component_counts=1,1,1,1,1"
                + "; classes2=provider+activity"
                + "; jni=M0-05-JNI-FIXED"
                + "; abi="
                + path.selectedAbi()
                + "; extracted="
                + path.extractedDirectoryIncluded()
                + "; signer_digest_match=true"
                + "; metadata_negative=3"
                + "; signer_negative=2";
    }

    private void requireFactoryFailure(ApplicationInfo applicationInfo, String code) {
        try {
            new ShellAppComponentFactory()
                    .instantiateClassLoader(getClass().getClassLoader(), applicationInfo);
            throw new AssertionError(code + " failure did not occur");
        } catch (IllegalStateException expected) {
            requireCode(expected, code);
        }
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
