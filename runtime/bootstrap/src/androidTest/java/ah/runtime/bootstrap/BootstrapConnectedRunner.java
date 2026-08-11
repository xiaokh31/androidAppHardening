package ah.runtime.bootstrap;

import android.app.Activity;
import android.app.AppComponentFactory;
import android.app.Application;
import android.app.Instrumentation;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Non-empty device contract for the six API 29 AppComponentFactory entry points. */
public final class BootstrapConnectedRunner extends Instrumentation {
    private static final String TEST_CLASS = "ah.runtime.bootstrap.BootstrapConnectedContract";
    private static final String TEST_NAME = "sixEntryOwnershipAndProcessMatrix";

    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        start();
    }

    @Override public void onStart() {
        sendStatus(1, status("\n" + TEST_CLASS + ":"));
        Bundle result = new Bundle();
        try {
            ProbeSummary main = runMainProcessProbe();
            require(main.installCount == 1, "main process installed more than once");
            require(main.classLoaderHookCount == 1, "original factory hook count changed");
            require(main.componentCount == 5, "five component delegation count changed");
            require(main.closeCount == 0, "READY session was closed");
            verifyFailureOwnership(getTargetContext().getApplicationInfo());
            verifySecondaryProcess();
            sendStatus(0, status("."));
            result.putString("stream", "\nTime: 0\n\nOK (1 test)\n"
                    + "m2_01_connected=true\n"
                    + "six_entries=true\n"
                    + "factory_hook_once=true\n"
                    + "five_components=true\n"
                    + "main_process_install_once=true\n"
                    + "secondary_process_install_once=true\n"
                    + "failure_close_once=true\n"
                    + "failure_buffers_zeroed=true\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable failure) {
            Bundle failed = status("F");
            failed.putString("stack", android.util.Log.getStackTraceString(failure));
            sendStatus(-2, failed);
            result.putString("stream", "\nFAILURES!!!\n"
                    + android.util.Log.getStackTraceString(failure));
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    private ProbeSummary runMainProcessProbe() throws Exception {
        ProbeSummary[] summary = new ProbeSummary[1];
        Throwable[] failure = new Throwable[1];
        ApplicationInfo applicationInfo = getTargetContext().getApplicationInfo();
        runOnMainSync(() -> {
            try {
                summary[0] = runProcessProbe(applicationInfo);
            } catch (Throwable caught) {
                failure[0] = caught;
            }
        });
        if (failure[0] != null) {
            if (failure[0] instanceof Exception) throw (Exception) failure[0];
            if (failure[0] instanceof Error) throw (Error) failure[0];
            throw new AssertionError(failure[0]);
        }
        require(summary[0] != null, "main process result absent");
        return summary[0];
    }

    static ProbeSummary runProcessProbe(ApplicationInfo applicationInfo) throws Exception {
        FakeSession session = new FakeSession("ah.runtime.bootstrap.BootstrapConnectedRunner$OriginalFactory");
        OriginalFactory factory = new OriginalFactory();
        FakeAdapter adapter = new FakeAdapter(factory);
        AtomicInteger installs = new AtomicInteger();
        HardeningBootstrap.Coordinator coordinator = new HardeningBootstrap.Coordinator(
                (loader, info) -> {
                    installs.incrementAndGet();
                    return session;
                }, adapter);
        ShellAppComponentFactory shell = new ShellAppComponentFactory(coordinator);
        ClassLoader finalLoader = shell.instantiateClassLoader(
                BootstrapConnectedRunner.class.getClassLoader(), applicationInfo);
        same(adapter.finalLoader, finalLoader, "final loader identity");
        same(finalLoader, shell.instantiateClassLoader(
                BootstrapConnectedRunner.class.getClassLoader(), applicationInfo),
                "cached final loader identity");
        require(shell.instantiateApplication(finalLoader, PayloadApplication.class.getName())
                instanceof PayloadApplication, "application delegation");
        require(shell.instantiateActivity(finalLoader, PayloadActivity.class.getName(), new Intent())
                instanceof PayloadActivity, "activity delegation");
        require(shell.instantiateService(finalLoader, PayloadService.class.getName(), new Intent())
                instanceof PayloadService, "service delegation");
        require(shell.instantiateReceiver(finalLoader, PayloadReceiver.class.getName(), new Intent())
                instanceof PayloadReceiver, "receiver delegation");
        require(shell.instantiateProvider(finalLoader, PayloadProvider.class.getName())
                instanceof PayloadProvider, "provider delegation");
        return new ProbeSummary(installs.get(), factory.classLoaderHooks.get(),
                factory.components.get(), session.closeCount.get());
    }

    private void verifyFailureOwnership(ApplicationInfo info) {
        for (String mode : new String[] {"construct", "hook", "null", "validate", "oom"}) {
            FakeSession session = new FakeSession("a.b.Factory");
            FakeAdapter adapter = new FakeAdapter(new OriginalFactory());
            adapter.mode = mode;
            ShellAppComponentFactory shell = new ShellAppComponentFactory(
                    new HardeningBootstrap.Coordinator((loader, app) -> session, adapter));
            try {
                shell.instantiateClassLoader(getClass().getClassLoader(), info);
                throw new AssertionError(mode + " unexpectedly succeeded");
            } catch (IllegalStateException expected) {
                require(expected.getMessage().startsWith(BootstrapFailure.PREFIX),
                        mode + " returned an unstable error");
            }
            require(session.closeCount.get() == 1, mode + " close count");
            require(session.handleClosed, mode + " native-handle observer");
            require(session.buffer.get(0) == 0 && session.buffer.get(31) == 0,
                    mode + " buffer was not zeroed");
        }
    }

    private void verifySecondaryProcess() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        ProbeSummary[] summary = new ProbeSummary[1];
        Throwable[] failure = new Throwable[1];
        Intent intent = new Intent("ah.runtime.bootstrap.M201_PROCESS_PROBE");
        intent.setComponent(new ComponentName(
                getContext().getPackageName(), BootstrapSecondaryProcessReceiver.class.getName()));
        getContext().sendOrderedBroadcast(intent, null, new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent received) {
                try {
                    Bundle extras = getResultExtras(false);
                    require(extras != null, "secondary result absent");
                    summary[0] = new ProbeSummary(
                            extras.getInt("install_count"),
                            extras.getInt("hook_count"),
                            extras.getInt("component_count"),
                            extras.getInt("close_count"));
                } catch (Throwable caught) {
                    failure[0] = caught;
                } finally {
                    done.countDown();
                }
            }
        }, null, Activity.RESULT_CANCELED, null, null);
        require(done.await(20, TimeUnit.SECONDS), "secondary process timed out");
        if (failure[0] != null) throw new AssertionError("secondary process result", failure[0]);
        require(summary[0] != null && summary[0].installCount == 1,
                "secondary install count");
        require(summary[0].classLoaderHookCount == 1, "secondary hook count");
        require(summary[0].componentCount == 5, "secondary component count");
    }

    static final class ProbeSummary {
        final int installCount;
        final int classLoaderHookCount;
        final int componentCount;
        final int closeCount;
        ProbeSummary(int installCount, int classLoaderHookCount, int componentCount, int closeCount) {
            this.installCount = installCount;
            this.classLoaderHookCount = classLoaderHookCount;
            this.componentCount = componentCount;
            this.closeCount = closeCount;
        }
    }

    private static final class FakeSession implements HardeningBootstrap.BootstrapSession {
        final ClassLoader loader = new ClassLoader(BootstrapConnectedRunner.class.getClassLoader()) {};
        final String factoryName;
        final AtomicInteger closeCount = new AtomicInteger();
        final ByteBuffer buffer = ByteBuffer.allocateDirect(32);
        boolean handleClosed;
        FakeSession(String factoryName) {
            this.factoryName = factoryName;
            for (int i = 0; i < buffer.capacity(); i++) buffer.put(i, (byte) 0x5a);
        }
        @Override public ClassLoader provisionalClassLoader() { return loader; }
        @Override public String originalFactoryClassNameOrNull() { return factoryName; }
        @Override public int containerMajor() { return 2; }
        @Override public int containerMinor() { return 0; }
        @Override public int signerPolicyVersion() { return 1; }
        @Override public int riskPolicyVersion() { return 1; }
        @Override public ah.runtime.guard.VerifiedPayloadSession verifiedSession() { return null; }
        @Override public void close() {
            if (closeCount.getAndIncrement() != 0) return;
            handleClosed = true;
            for (int i = 0; i < buffer.capacity(); i++) buffer.put(i, (byte) 0);
        }
    }

    private static final class FakeAdapter implements HardeningBootstrap.FactoryAdapter {
        final OriginalFactory factory;
        final ClassLoader finalLoader = BootstrapConnectedRunner.class.getClassLoader();
        String mode = "ready";
        FakeAdapter(OriginalFactory factory) { this.factory = factory; }
        @Override public AppComponentFactory create(ClassLoader loader, String name) {
            if ("construct".equals(mode)) throw BootstrapFailure.create(BootstrapFailure.FACTORY_CONSTRUCT);
            if ("oom".equals(mode)) throw new OutOfMemoryError("synthetic");
            return factory;
        }
        @Override public ClassLoader delegate(AppComponentFactory value, ClassLoader loader,
                ApplicationInfo info) {
            if ("hook".equals(mode)) throw BootstrapFailure.create(BootstrapFailure.FACTORY_HOOK);
            if ("null".equals(mode)) return null;
            factory.classLoaderHooks.incrementAndGet();
            return finalLoader;
        }
        @Override public void validate(ClassLoader loader, AppComponentFactory value, String name) {
            if ("validate".equals(mode)) throw BootstrapFailure.create(BootstrapFailure.FINAL_LOADER);
        }
    }

    public static final class OriginalFactory extends AppComponentFactory {
        final AtomicInteger classLoaderHooks = new AtomicInteger();
        final AtomicInteger components = new AtomicInteger();
        @Override public Application instantiateApplication(ClassLoader loader, String name) {
            components.incrementAndGet(); return new PayloadApplication();
        }
        @Override public Activity instantiateActivity(ClassLoader loader, String name, Intent intent) {
            components.incrementAndGet(); return new PayloadActivity();
        }
        @Override public Service instantiateService(ClassLoader loader, String name, Intent intent) {
            components.incrementAndGet(); return new PayloadService();
        }
        @Override public BroadcastReceiver instantiateReceiver(ClassLoader loader, String name,
                Intent intent) {
            components.incrementAndGet(); return new PayloadReceiver();
        }
        @Override public ContentProvider instantiateProvider(ClassLoader loader, String name) {
            components.incrementAndGet(); return new PayloadProvider();
        }
    }

    public static final class PayloadApplication extends Application {}
    public static final class PayloadActivity extends Activity {}
    public static final class PayloadService extends Service {
        @Override public IBinder onBind(Intent intent) { return null; }
    }
    public static final class PayloadReceiver extends BroadcastReceiver {
        @Override public void onReceive(Context context, Intent intent) {}
    }
    public static final class PayloadProvider extends ContentProvider {
        @Override public boolean onCreate() { return true; }
        @Override public Cursor query(Uri uri, String[] projection, String selection,
                String[] selectionArgs, String sortOrder) { return null; }
        @Override public String getType(Uri uri) { return null; }
        @Override public Uri insert(Uri uri, ContentValues values) { return null; }
        @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
        @Override public int update(Uri uri, ContentValues values, String selection,
                String[] selectionArgs) { return 0; }
    }

    private static Bundle status(String stream) {
        Bundle bundle = new Bundle();
        bundle.putString("id", "M2-01");
        bundle.putString("class", TEST_CLASS);
        bundle.putString("test", TEST_NAME);
        bundle.putInt("numtests", 1);
        bundle.putInt("current", 1);
        bundle.putString("stream", stream);
        return bundle;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static void same(Object expected, Object actual, String label) {
        if (expected != actual) throw new AssertionError(label);
    }
}
