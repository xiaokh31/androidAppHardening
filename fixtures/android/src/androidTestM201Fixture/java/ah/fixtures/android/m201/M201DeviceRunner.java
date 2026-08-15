package ah.fixtures.android.m201;

import ah.fixtures.android.ProbeSignal;
import android.app.Activity;
import android.app.Instrumentation;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import java.io.File;
import java.io.FileInputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Real platform-callback acceptance for the M2-01 production bootstrap chain. */
public final class M201DeviceRunner extends Instrumentation {
    private static final String APPLICATION = "ah.fixtures.android.payload.PayloadApplication";
    private static final String ACTIVITY = "ah.fixtures.android.payload.PayloadActivity";
    private static final String SERVICE = "ah.fixtures.android.payload.PayloadService";
    private static final String RECEIVER = "ah.fixtures.android.payload.PayloadReceiver";
    private static final String SECONDARY = "ah.fixtures.android.payload.SecondaryApi";
    private boolean originalFactoryExpected = true;

    @Override
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        originalFactoryExpected = arguments == null
                || !"false".equals(arguments.getString("original_factory"));
        start();
    }

    @Override
    public void onStart() {
        sendStatus(1, new Bundle());
        Bundle result = new Bundle();
        try {
            String summary = runAcceptance();
            Bundle status = new Bundle();
            status.putString("stream", summary + "\nOK (1 test)\n");
            sendStatus(0, status);
            result.putString("summary", summary);
            finish(Activity.RESULT_OK, result);
        } catch (Throwable failure) {
            Bundle status = new Bundle();
            status.putString("stream", "\nFAILURES!!!\n" + failure + "\n");
            sendStatus(-2, status);
            result.putString("summary", "m2_01_device=false");
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    private String runAcceptance() throws Exception {
        Context target = getTargetContext();
        Object application = target.getApplicationContext();
        require(application != null && APPLICATION.equals(application.getClass().getName()),
                "original Application was not restored");
        ClassLoader payloadLoader = application.getClass().getClassLoader();
        require(payloadLoader != null
                        && "dalvik.system.InMemoryDexClassLoader".equals(
                                payloadLoader.getClass().getName()),
                "Application did not use the in-memory payload loader");

        ApplicationInfo withMetadata = target.getPackageManager().getApplicationInfo(
                target.getPackageName(), PackageManager.GET_META_DATA);
        require(withMetadata.metaData == null, "fixture metadata must be null");
        int expectedFactoryCallbacks = originalFactoryExpected ? 1 : 0;
        equal(expectedFactoryCallbacks, ProbeSignal.factoryCount("classloader"),
                "main classloader hook count");
        equal(expectedFactoryCallbacks, ProbeSignal.factoryCount("application"),
                "main application count");
        equal(expectedFactoryCallbacks, ProbeSignal.factoryCount("provider"),
                "main provider count");
        equal("M0-05-CLASSES2:provider", ProbeSignal.providerMarker(), "provider cross-DEX");
        waitForJniMarker();

        Activity activity = launchActivity(target);
        Activity relaunched = null;
        try {
            require(activity.getClass().getClassLoader() == payloadLoader,
                    "Activity loader differs from final loader");
            equal(expectedFactoryCallbacks, ProbeSignal.factoryCount("activity"), "activity count");
            equal("M0-05-CLASSES2:activity", ProbeSignal.activityMarker(), "activity cross-DEX");
            relaunched = relaunchActivity(activity);
            require(relaunched.getClass().getClassLoader() == payloadLoader,
                    "relaunched Activity loader differs from final loader");
            equal(originalFactoryExpected ? 2 : 0,
                    ProbeSignal.factoryCount("activity"), "relaunch activity count");
            Intent service = new Intent().setClassName(target, SERVICE);
            require(target.startService(service) != null, "service did not start");
            waitForServiceMarker();
            verifySecondaryProcess(target);
            equal(expectedFactoryCallbacks, ProbeSignal.factoryCount("classloader"),
                    "unexpected main factory callback count");
            equal(expectedFactoryCallbacks, ProbeSignal.factoryCount("service"), "service count");
            require(payloadLoader.loadClass(SECONDARY).getClassLoader() == payloadLoader,
                    "classes2 type did not use final loader");
            equal(0, countPlaintextDex(target.getApplicationInfo().dataDir),
                    "plaintext DEX files");
        } finally {
            (relaunched == null ? activity : relaunched).finish();
            target.stopService(new Intent().setClassName(target, SERVICE));
        }
        return "m2_01_device=true platform_callbacks=6 main_install=1 secondary_install=1 "
                + "original_factory=" + originalFactoryExpected
                + " factory_callbacks=" + expectedFactoryCallbacks + " "
                + "custom_application=true early_provider=true multidex=true jni=true "
                + "configuration_relaunch=true metadata_null=true plaintext_dex_files=0";
    }

    private Activity launchActivity(Context target) {
        Intent intent = new Intent().setClassName(target, ACTIVITY);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return startActivitySync(intent);
    }

    private Activity relaunchActivity(Activity activity) {
        ActivityMonitor monitor = addMonitor(ACTIVITY, null, false);
        try {
            runOnMainSync(activity::recreate);
            Activity relaunched = monitor.waitForActivityWithTimeout(5_000L);
            require(relaunched != null, "configuration relaunch timed out");
            return relaunched;
        } finally {
            removeMonitor(monitor);
        }
    }

    private void verifySecondaryProcess(Context target) throws Exception {
        CountDownLatch completed = new CountDownLatch(1);
        Bundle[] observed = new Bundle[1];
        BroadcastReceiver finalReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                observed[0] = getResultExtras(false);
                completed.countDown();
            }
        };
        Intent intent = new Intent("ah.fixtures.android.M2_01_SECONDARY");
        intent.setClassName(target, RECEIVER);
        target.sendOrderedBroadcast(intent, null, finalReceiver, null, 0, null, null);
        require(completed.await(10, TimeUnit.SECONDS), "secondary process receiver timed out");
        Bundle result = observed[0];
        require(result != null, "secondary process returned no result");
        int expectedFactoryCallbacks = originalFactoryExpected ? 1 : 0;
        equal(expectedFactoryCallbacks, result.getInt("classloader_count", -1),
                "secondary classloader hook count");
        equal(expectedFactoryCallbacks, result.getInt("application_count", -1),
                "secondary application count");
        equal(expectedFactoryCallbacks, result.getInt("receiver_count", -1),
                "secondary receiver count");
        equal("M0-05-JNI-FIXED", result.getString("jni_marker"), "secondary JNI marker");
        equal("M0-05-CLASSES2:secondary", result.getString("secondary_marker"),
                "secondary cross-DEX marker");
        equal("dalvik.system.InMemoryDexClassLoader", result.getString("receiver_loader"),
                "secondary receiver loader");
    }

    private static void waitForServiceMarker() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if ("M0-05-CLASSES2:service".equals(ProbeSignal.serviceMarker())) return;
            Thread.sleep(25L);
        }
        throw new AssertionError("service lifecycle marker did not converge");
    }

    private static void waitForJniMarker() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if ("M0-05-JNI-FIXED".equals(ProbeSignal.jniMarker())) return;
            Thread.sleep(25L);
        }
        throw new AssertionError("main JNI marker did not converge");
    }

    private static int countPlaintextDex(String rootPath) throws Exception {
        File root = new File(rootPath);
        File[] files = root.listFiles();
        if (files == null) return 0;
        int count = 0;
        for (File file : files) {
            if (file.isDirectory()) {
                count += countPlaintextDex(file.getAbsolutePath());
            } else if (file.isFile() && file.length() >= 8) {
                byte[] header = new byte[8];
                try (FileInputStream input = new FileInputStream(file)) {
                    if (input.read(header) == header.length
                            && header[0] == 'd' && header[1] == 'e' && header[2] == 'x'
                            && header[3] == '\n' && header[7] == 0) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private static void equal(Object expected, Object actual, String label) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(label + ": expected=" + expected + " actual=" + actual);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
