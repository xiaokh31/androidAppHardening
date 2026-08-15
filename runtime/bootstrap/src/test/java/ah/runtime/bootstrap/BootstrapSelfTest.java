package ah.runtime.bootstrap;

import android.app.AppComponentFactory;
import android.content.pm.ApplicationInfo;
import ah.runtime.guard.VerifiedPayloadSession;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Dependency-free JVM contract suite for the M2-01 bootstrap state machine. */
public final class BootstrapSelfTest {
    private static final ClassLoader SHELL = BootstrapSelfTest.class.getClassLoader();

    private BootstrapSelfTest() {}

    public static void main(String[] args) throws Exception {
        readyWithoutFactory();
        customFactoryAndCache();
        secondShellStateMatrix();
        concurrentInstallOnce();
        failuresCloseOnceAndCache();
        cleanupFailureDoesNotReplacePrimary();
        configurationFailures();
        reentryFailsClosed();
        metadataIsIgnored();
        System.out.println("M2-09 bootstrap self-test PASS (10 groups)");
    }

    private static void readyWithoutFactory() {
        FakeSession session = new FakeSession(null);
        FakeAdapter adapter = new FakeAdapter();
        HardeningBootstrap.Coordinator coordinator = coordinator(session, adapter);
        BootstrapResult result = coordinator.install(SHELL, info(null));
        equal(BootstrapResult.Status.READY, result.status(), "ready status");
        same(session.loader, result.provisionalClassLoader(), "provisional loader");
        same(session.loader, result.finalClassLoader(), "final loader");
        equal(0, adapter.createCount.get(), "default factory count");
        equal(0, session.closeCount.get(), "ready ownership close");
        same(result, coordinator.install(SHELL, info(null)), "ready cache identity");
    }

    private static void customFactoryAndCache() {
        FakeSession session = new FakeSession("a.b.OriginalFactory");
        FakeAdapter adapter = new FakeAdapter();
        HardeningBootstrap.Coordinator coordinator = coordinator(session, adapter);
        BootstrapResult result = coordinator.install(SHELL, info(new Object()));
        equal(BootstrapResult.Status.READY, result.status(), "factory ready");
        equal(1, adapter.createCount.get(), "factory construct once");
        equal(1, adapter.delegateCount.get(), "factory hook once");
        equal(1, adapter.validateCount.get(), "final loader validation once");
        same(adapter.finalLoader, result.finalClassLoader(), "delegated final loader");
        same(adapter.factory, result.originalFactory(), "retained original factory");
        require(result.owner() != null, "READY did not retain session owner");
    }

    private static void secondShellStateMatrix() {
        FakeSession session = new FakeSession("a.b.OriginalFactory");
        FakeAdapter adapter = new FakeAdapter();
        AtomicInteger opens = new AtomicInteger();
        HardeningBootstrap.Coordinator coordinator = new HardeningBootstrap.Coordinator(
                (loader, info) -> {
                    opens.incrementAndGet();
                    return session;
                }, adapter);
        expectComponentFailure(shell(coordinator), SHELL, "NEW second Shell");
        equal(0, opens.get(), "NEW second Shell opened Guard");
        BootstrapResult ready = coordinator.install(SHELL, info(null));
        ShellAppComponentFactory secondShell = shell(coordinator);
        same(ready, invokeRequireReady(secondShell, adapter.finalLoader),
                "terminal READY attachment identity");
        expectComponentFailure(secondShell, new ClassLoader(adapter.finalLoader) {},
                "mismatched second Shell loader");
        equal(1, opens.get(), "READY attachment reopened Guard");
        equal(1, adapter.createCount.get(), "READY attachment reconstructed factory");
        equal(1, adapter.delegateCount.get(), "READY attachment repeated factory hook");

        HardeningBootstrap.Coordinator[] installing = new HardeningBootstrap.Coordinator[1];
        AtomicInteger installingOpens = new AtomicInteger();
        FakeSession installingSession = new FakeSession(null);
        installing[0] = new HardeningBootstrap.Coordinator((loader, info) -> {
            installingOpens.incrementAndGet();
            expectComponentFailure(shell(installing[0]), loader,
                    "INSTALLING second Shell");
            return installingSession;
        }, new FakeAdapter());
        equal(BootstrapResult.Status.READY,
                installing[0].install(SHELL, info(null)).status(), "installing terminal status");
        equal(1, installingOpens.get(), "INSTALLING second Shell reopened Guard");

        FakeSession failedSession = new FakeSession("a.b.OriginalFactory");
        FakeAdapter failedAdapter = new FakeAdapter();
        failedAdapter.mode = "hook";
        AtomicInteger failedOpens = new AtomicInteger();
        HardeningBootstrap.Coordinator failed = new HardeningBootstrap.Coordinator(
                (loader, info) -> {
                    failedOpens.incrementAndGet();
                    return failedSession;
                }, failedAdapter);
        equal(BootstrapResult.Status.FAILURE,
                failed.install(SHELL, info(null)).status(), "failed terminal status");
        expectComponentFailure(shell(failed), adapter.finalLoader,
                "FAILED second Shell");
        equal(BootstrapResult.Status.FAILURE,
                failed.install(SHELL, info(null)).status(), "failed cached status");
        equal(1, failedOpens.get(), "FAILED second Shell retried Guard");
        equal(1, failedAdapter.createCount.get(), "FAILED second Shell reconstructed factory");
        equal(1, failedAdapter.delegateCount.get(), "FAILED second Shell repeated factory hook");
    }

    private static BootstrapResult invokeRequireReady(
            ShellAppComponentFactory shell,
            ClassLoader loader) {
        try {
            Method method = ShellAppComponentFactory.class.getDeclaredMethod(
                    "requireReady", ClassLoader.class);
            method.setAccessible(true);
            return (BootstrapResult) method.invoke(shell, loader);
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw new AssertionError("second Shell invocation failed", cause);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("second Shell reflection failed", failure);
        }
    }

    private static ShellAppComponentFactory shell(HardeningBootstrap.Coordinator coordinator) {
        ShellAppComponentFactory shell = allocate(ShellAppComponentFactory.class);
        try {
            Field field = ShellAppComponentFactory.class.getDeclaredField("coordinator");
            field.setAccessible(true);
            field.set(shell, coordinator);
            return shell;
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("second Shell allocation failed", failure);
        }
    }

    private static void expectComponentFailure(
            ShellAppComponentFactory shell,
            ClassLoader loader,
            String label) {
        try {
            invokeRequireReady(shell, loader);
            throw new AssertionError(label + " unexpectedly attached");
        } catch (BootstrapFailure expected) {
            equal(BootstrapFailure.message(BootstrapFailure.COMPONENT), expected.getMessage(),
                    label + " code");
        }
    }

    private static void concurrentInstallOnce() throws Exception {
        FakeSession session = new FakeSession(null);
        AtomicInteger opens = new AtomicInteger();
        HardeningBootstrap.Coordinator coordinator = new HardeningBootstrap.Coordinator(
                (loader, info) -> {
                    opens.incrementAndGet();
                    return session;
                },
                new FakeAdapter());
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();
        List<BootstrapResult> results = new ArrayList<>();
        for (int index = 0; index < 8; index++) {
            Thread thread = new Thread(() -> {
                await(start);
                BootstrapResult result = coordinator.install(SHELL, info(null));
                synchronized (results) {
                    results.add(result);
                }
            });
            threads.add(thread);
            thread.start();
        }
        start.countDown();
        for (Thread thread : threads) {
            thread.join();
        }
        equal(1, opens.get(), "concurrent Guard open count");
        equal(8, results.size(), "concurrent result count");
        for (BootstrapResult result : results) {
            same(results.get(0), result, "concurrent terminal identity");
        }
    }

    private static void failuresCloseOnceAndCache() {
        for (String mode : new String[] {
                "construct", "hook", "null", "validate", "oom", "hostile-message"
        }) {
            FakeSession session = new FakeSession("a.b.OriginalFactory");
            FakeAdapter adapter = new FakeAdapter();
            adapter.mode = mode;
            HardeningBootstrap.Coordinator coordinator = coordinator(session, adapter);
            BootstrapResult first = coordinator.install(SHELL, info(null));
            equal(BootstrapResult.Status.FAILURE, first.status(), mode + " status");
            equal(1, session.closeCount.get(), mode + " close count");
            require(first.owner() == null, mode + " retained owner");
            require(first.verifiedSession() == null, mode + " retained verified session");
            require(first.provisionalClassLoader() == null, mode + " retained provisional");
            require(first.finalClassLoader() == null, mode + " retained final");
            require(first.originalFactory() == null, mode + " retained factory");
            if ("hostile-message".equals(mode)) {
                equal(BootstrapFailure.message(BootstrapFailure.INTERNAL), first.errorCode(),
                        "hostile Throwable classification");
            }
            same(first, coordinator.install(SHELL, info(null)), mode + " failure cache identity");
            equal(1, session.closeCount.get(), mode + " cached close count");
        }
    }

    private static void cleanupFailureDoesNotReplacePrimary() {
        FakeSession session = new FakeSession("a.b.OriginalFactory");
        session.failClose = true;
        FakeAdapter adapter = new FakeAdapter();
        adapter.mode = "hook";
        BootstrapResult result = coordinator(session, adapter).install(SHELL, info(null));
        equal(BootstrapFailure.message(BootstrapFailure.FACTORY_HOOK), result.errorCode(),
                "primary failure");
        equal(BootstrapFailure.message(BootstrapFailure.CLEANUP), result.cleanupErrorCode(),
                "cleanup failure");
        equal(1, session.closeCount.get(), "cleanup attempted once");
    }

    private static void configurationFailures() {
        assertConfigFailure(new FakeSession("ah.runtime.bootstrap.ShellAppComponentFactory"));
        assertConfigFailure(new FakeSession("not-qualified"));
        assertConfigFailure(new FakeSession("a." + "x".repeat(513)));
        FakeSession version = new FakeSession(null);
        version.major = 3;
        assertConfigFailure(version);
        FakeSession policy = new FakeSession(null);
        policy.signerPolicy = 2;
        assertConfigFailure(policy);
    }

    private static void reentryFailsClosed() {
        HardeningBootstrap.Coordinator[] holder = new HardeningBootstrap.Coordinator[1];
        FakeSession session = new FakeSession(null);
        holder[0] = new HardeningBootstrap.Coordinator(
                (loader, info) -> {
                    BootstrapResult nested = holder[0].install(loader, info);
                    equal(BootstrapFailure.message(BootstrapFailure.REENTRANT), nested.errorCode(),
                            "nested reentry code");
                    return session;
                },
                new FakeAdapter());
        BootstrapResult result = holder[0].install(SHELL, info(null));
        equal(BootstrapFailure.message(BootstrapFailure.REENTRANT), result.errorCode(),
                "outer reentry code");
        equal(1, session.closeCount.get(), "reentry close count");
    }

    private static void metadataIsIgnored() {
        BootstrapResult absent = coordinator(new FakeSession(null), new FakeAdapter())
                .install(SHELL, info(null));
        BootstrapResult present = coordinator(new FakeSession(null), new FakeAdapter())
                .install(SHELL, info(new Object()));
        equal(absent.status(), present.status(), "metadata equivalence");
        equal(absent.errorCode(), present.errorCode(), "metadata error equivalence");
    }

    private static void assertConfigFailure(FakeSession session) {
        BootstrapResult result = coordinator(session, new FakeAdapter()).install(SHELL, info(null));
        require(result.errorCode().startsWith(BootstrapFailure.PREFIX + "CONFIG_"),
                "unexpected config error " + result.errorCode());
        equal(1, session.closeCount.get(), "config close count");
    }

    private static HardeningBootstrap.Coordinator coordinator(
            FakeSession session, FakeAdapter adapter) {
        return new HardeningBootstrap.Coordinator((loader, info) -> session, adapter);
    }

    private static ApplicationInfo info(Object metadataMarker) {
        ApplicationInfo info = allocate(ApplicationInfo.class);
        if (metadataMarker != null) {
            info.metaData = allocate(android.os.Bundle.class);
        }
        return info;
    }

    private static final class FakeSession implements HardeningBootstrap.BootstrapSession {
        final ClassLoader loader = new ClassLoader(SHELL) {};
        final String factoryName;
        final AtomicInteger closeCount = new AtomicInteger();
        int major = 2;
        int minor;
        int signerPolicy = 1;
        int riskPolicy = 1;
        boolean failClose;

        FakeSession(String factoryName) {
            this.factoryName = factoryName;
        }

        @Override public ClassLoader provisionalClassLoader() { return loader; }
        @Override public String originalFactoryClassNameOrNull() { return factoryName; }
        @Override public int containerMajor() { return major; }
        @Override public int containerMinor() { return minor; }
        @Override public int signerPolicyVersion() { return signerPolicy; }
        @Override public int riskPolicyVersion() { return riskPolicy; }
        @Override public VerifiedPayloadSession verifiedSession() { return null; }
        @Override public void close() {
            closeCount.incrementAndGet();
            if (failClose) throw new IllegalStateException("synthetic cleanup detail");
        }
    }

    private static final class FakeAdapter implements HardeningBootstrap.FactoryAdapter {
        final AtomicInteger createCount = new AtomicInteger();
        final AtomicInteger delegateCount = new AtomicInteger();
        final AtomicInteger validateCount = new AtomicInteger();
        final AppComponentFactory factory = allocate(AppComponentFactory.class);
        final ClassLoader finalLoader = new ClassLoader(SHELL) {};
        String mode = "ready";

        @Override public AppComponentFactory create(ClassLoader loader, String name) {
            createCount.incrementAndGet();
            if ("construct".equals(mode)) throw BootstrapFailure.create(BootstrapFailure.FACTORY_CONSTRUCT);
            if ("oom".equals(mode)) throw new OutOfMemoryError("synthetic");
            return factory;
        }

        @Override public ClassLoader delegate(AppComponentFactory value, ClassLoader loader,
                ApplicationInfo info) {
            delegateCount.incrementAndGet();
            if ("hook".equals(mode)) throw BootstrapFailure.create(BootstrapFailure.FACTORY_HOOK);
            if ("hostile-message".equals(mode)) {
                throw new RuntimeException() {
                    @Override public String getMessage() {
                        throw new AssertionError("hostile Throwable message accessor");
                    }
                };
            }
            if ("null".equals(mode)) return null;
            return finalLoader;
        }

        @Override public void validate(ClassLoader loader, AppComponentFactory value, String name) {
            validateCount.incrementAndGet();
            if ("validate".equals(mode)) throw BootstrapFailure.create(BootstrapFailure.FINAL_LOADER);
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted");
        }
    }

    private static <T> T allocate(Class<T> type) {
        try {
            Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
            Field field = unsafeClass.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            Object unsafe = field.get(null);
            Method allocate = unsafeClass.getMethod("allocateInstance", Class.class);
            return type.cast(allocate.invoke(unsafe, type));
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("test allocation failed", failure);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void same(Object expected, Object actual, String label) {
        if (expected != actual) throw new AssertionError(label);
    }

    private static void equal(Object expected, Object actual, String label) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        }
    }
}
