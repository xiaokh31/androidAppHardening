package ah.fixtures.android;

import ah.runtime.bootstrap.ClassLoaderProbe;
import ah.runtime.bootstrap.ProbeEvent;
import ah.runtime.bootstrap.M004ClassLoaderFactory;
import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** Dependency-free instrumentation runner for the bounded M0-04 feasibility gate. */
public final class ClassLoaderPocRunner extends Instrumentation {
    private static final String TEST_CLASS = ClassLoaderPocRunner.class.getName();
    private static final String TEST_NAME = "m0_04_public_classloader_gate";
    private static final String TARGET_PACKAGE = "ah.fixtures.android.classloaderpoc";
    private static final String PAYLOAD_ACTIVITY =
            "ah.fixtures.android.payload.PayloadActivity";
    private static final String PAYLOAD_API =
            "ah.fixtures.android.payload.PayloadOnlyApi";
    private static final String PAYLOAD_ENTRY = "assets/ah/poc/classes.dex";

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
        String diagnostic = verifyCallbackOrderLoaderIdentityAndPayloadOnlyMethod();
        verifyInvalidPayloadsFailClosed();
        verifyFailureDoesNotExposeSourcePath();
        verifyNoPlaintextPayloadWasWrittenToAppStorage();
        return diagnostic
                + "; negative_payloads=4; source_path_redacted=true; plaintext_files=0";
    }

    private String verifyCallbackOrderLoaderIdentityAndPayloadOnlyMethod() throws Exception {
        Activity activity = launchPayloadActivity();
        try {
            ProbeEvent factory = firstEvent(ClassLoaderProbe.FACTORY_ENTER);
            ProbeEvent loader = firstEvent(ClassLoaderProbe.LOADER_CREATED);
            ProbeEvent application = firstEvent(ClassLoaderProbe.APPLICATION_CREATED);
            ProbeEvent createdActivity = lastEvent(ClassLoaderProbe.ACTIVITY_CREATED);

            require(factory.sequence() < loader.sequence(), "factory must precede loader");
            require(loader.sequence() < application.sequence(), "loader must precede application");
            require(
                    application.sequence() < createdActivity.sequence(),
                    "application must precede activity");
            equal(
                    "dalvik.system.InMemoryDexClassLoader",
                    loader.classLoaderName(),
                    "loader type");
            same(loader.classLoader(), application.classLoader(), "application loader");
            same(loader.classLoader(), createdActivity.classLoader(), "activity event loader");
            same(loader.classLoader(), activity.getClass().getClassLoader(), "activity loader");

            try {
                factory.classLoader().loadClass(PAYLOAD_API);
                throw new AssertionError("parent resolved the payload-only class");
            } catch (ClassNotFoundException expected) {
                // The marker exists only in the in-memory payload.
            }

            Class<?> markerClass = loader.classLoader().loadClass(PAYLOAD_API);
            Method marker = markerClass.getMethod("marker");
            equal("M0-04-IN-MEMORY", marker.invoke(null), "payload-only marker");
            return "FACTORY_ENTER<LOADER_CREATED<APPLICATION_CREATED<ACTIVITY_CREATED"
                    + "; loader="
                    + loader.classLoaderName()
                    + "; identity="
                    + System.identityHashCode(loader.classLoader())
                    + "; payload_marker=M0-04-IN-MEMORY";
        } finally {
            activity.finish();
        }
    }

    private void verifyInvalidPayloadsFailClosed() throws Exception {
        File missing = createStoredZip(null);
        byte[] corruptBytes = new byte[112];
        Arrays.fill(corruptBytes, (byte) 0x5a);
        File corrupt = createStoredZip(corruptBytes);
        File empty = createStoredZip(new byte[0]);
        File duplicate = createDuplicateStoredZip();
        try {
            requireP001(missing);
            requireP001(corrupt);
            requireP001(empty);
            requireP001(duplicate);
        } finally {
            missing.delete();
            corrupt.delete();
            empty.delete();
            duplicate.delete();
        }
    }

    private void verifyFailureDoesNotExposeSourcePath() {
        File sourceApk =
                new File(
                        getTargetContext().getCacheDir(),
                        "SENSITIVE-M0-04-PATH-does-not-exist.apk");
        sourceApk.delete();
        ApplicationInfo applicationInfo = new ApplicationInfo();
        applicationInfo.sourceDir = sourceApk.getAbsolutePath();
        applicationInfo.nativeLibraryDir =
                getTargetContext().getApplicationInfo().nativeLibraryDir;
        try {
            new M004ClassLoaderFactory()
                    .instantiateClassLoader(getClass().getClassLoader(), applicationInfo);
            throw new AssertionError("missing APK produced a ClassLoader");
        } catch (IllegalStateException expected) {
            String trace = stackTrace(expected);
            require(trace.contains("AAH-P001:"), "stable failure code is missing");
            require(
                    !trace.contains(sourceApk.getAbsolutePath())
                            && !trace.contains("SENSITIVE-M0-04-PATH"),
                    "failure exposed sourceDir");
        }
    }

    private void verifyNoPlaintextPayloadWasWrittenToAppStorage() throws Exception {
        byte[] packagedHash = packagedPayloadHash();
        long packagedSize = packagedPayloadSize();
        List<File> roots = new ArrayList<>();
        roots.add(getTargetContext().getFilesDir().getParentFile());
        File external = getTargetContext().getExternalFilesDir(null);
        if (external != null) {
            roots.add(external);
        }

        Set<String> forbiddenExtensions = new HashSet<>(Arrays.asList(".dex", ".jar", ".odex"));
        List<String> violations = new ArrayList<>();
        for (File root : roots) {
            scan(root, packagedHash, packagedSize, forbiddenExtensions, violations);
        }
        require(violations.isEmpty(), "plaintext payload files found: " + violations);
    }

    private Activity launchPayloadActivity() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName(TARGET_PACKAGE, PAYLOAD_ACTIVITY);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return startActivitySync(intent);
    }

    private ProbeEvent firstEvent(String type) {
        for (ProbeEvent event : ClassLoaderProbe.snapshot()) {
            if (type.equals(event.type())) {
                return event;
            }
        }
        throw new AssertionError("missing event " + type);
    }

    private ProbeEvent lastEvent(String type) {
        List<ProbeEvent> events = ClassLoaderProbe.snapshot();
        for (int index = events.size() - 1; index >= 0; index--) {
            if (type.equals(events.get(index).type())) {
                return events.get(index);
            }
        }
        throw new AssertionError("missing event " + type);
    }

    private void requireP001(File sourceApk) {
        ApplicationInfo applicationInfo = new ApplicationInfo();
        applicationInfo.sourceDir = sourceApk.getAbsolutePath();
        applicationInfo.nativeLibraryDir = getTargetContext().getApplicationInfo().nativeLibraryDir;
        try {
            new M004ClassLoaderFactory()
                    .instantiateClassLoader(getClass().getClassLoader(), applicationInfo);
            throw new AssertionError("invalid payload produced a ClassLoader");
        } catch (IllegalStateException expected) {
            require(
                    expected.getMessage() != null
                            && expected.getMessage().startsWith("AAH-P001:"),
                    "unexpected failure: " + expected);
        }
    }

    private File createStoredZip(byte[] payload) throws Exception {
        File output = File.createTempFile("m0-04-negative-", ".apk", getTargetContext().getCacheDir());
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(output))) {
            if (payload != null) {
                CRC32 crc = new CRC32();
                crc.update(payload);
                ZipEntry entry = new ZipEntry(PAYLOAD_ENTRY);
                entry.setMethod(ZipEntry.STORED);
                entry.setSize(payload.length);
                entry.setCompressedSize(payload.length);
                entry.setCrc(crc.getValue());
                zip.putNextEntry(entry);
                zip.write(payload);
                zip.closeEntry();
            }
        }
        return output;
    }

    private File createDuplicateStoredZip() throws Exception {
        File output =
                File.createTempFile(
                        "m0-04-duplicate-", ".apk", getTargetContext().getCacheDir());
        byte[] name = PAYLOAD_ENTRY.getBytes(StandardCharsets.UTF_8);
        byte[] payload = new byte[112];
        CRC32 crc = new CRC32();
        crc.update(payload);
        long localRecordSize = 30L + name.length + payload.length;
        long centralOffset = localRecordSize * 2L;
        long centralRecordSize = 46L + name.length;

        try (FileOutputStream stream = new FileOutputStream(output)) {
            writeLocalEntry(stream, name, payload, crc.getValue());
            writeLocalEntry(stream, name, payload, crc.getValue());
            writeCentralEntry(stream, name, payload.length, crc.getValue(), 0L);
            writeCentralEntry(
                    stream, name, payload.length, crc.getValue(), localRecordSize);
            writeIntLe(stream, 0x06054b50L);
            writeShortLe(stream, 0);
            writeShortLe(stream, 0);
            writeShortLe(stream, 2);
            writeShortLe(stream, 2);
            writeIntLe(stream, centralRecordSize * 2L);
            writeIntLe(stream, centralOffset);
            writeShortLe(stream, 0);
        }
        return output;
    }

    private void writeLocalEntry(
            OutputStream output, byte[] name, byte[] payload, long crc32)
            throws Exception {
        writeIntLe(output, 0x04034b50L);
        writeShortLe(output, 20);
        writeShortLe(output, 0);
        writeShortLe(output, ZipEntry.STORED);
        writeShortLe(output, 0);
        writeShortLe(output, 0);
        writeIntLe(output, crc32);
        writeIntLe(output, payload.length);
        writeIntLe(output, payload.length);
        writeShortLe(output, name.length);
        writeShortLe(output, 0);
        output.write(name);
        output.write(payload);
    }

    private void writeCentralEntry(
            OutputStream output, byte[] name, int payloadSize, long crc32, long localOffset)
            throws Exception {
        writeIntLe(output, 0x02014b50L);
        writeShortLe(output, 20);
        writeShortLe(output, 20);
        writeShortLe(output, 0);
        writeShortLe(output, ZipEntry.STORED);
        writeShortLe(output, 0);
        writeShortLe(output, 0);
        writeIntLe(output, crc32);
        writeIntLe(output, payloadSize);
        writeIntLe(output, payloadSize);
        writeShortLe(output, name.length);
        writeShortLe(output, 0);
        writeShortLe(output, 0);
        writeShortLe(output, 0);
        writeShortLe(output, 0);
        writeIntLe(output, 0);
        writeIntLe(output, localOffset);
        output.write(name);
    }

    private void writeShortLe(OutputStream output, int value) throws Exception {
        output.write(value & 0xff);
        output.write((value >>> 8) & 0xff);
    }

    private void writeIntLe(OutputStream output, long value) throws Exception {
        output.write((int) (value & 0xff));
        output.write((int) ((value >>> 8) & 0xff));
        output.write((int) ((value >>> 16) & 0xff));
        output.write((int) ((value >>> 24) & 0xff));
    }

    private byte[] packagedPayloadHash() throws Exception {
        try (ZipFile apk = new ZipFile(getTargetContext().getApplicationInfo().sourceDir)) {
            ZipEntry payload = apk.getEntry(PAYLOAD_ENTRY);
            require(payload != null, "packaged payload is missing");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            try (java.io.InputStream input = apk.getInputStream(payload)) {
                for (int read; (read = input.read(buffer)) >= 0; ) {
                    if (read > 0) {
                        digest.update(buffer, 0, read);
                    }
                }
            }
            return digest.digest();
        }
    }

    private long packagedPayloadSize() throws Exception {
        try (ZipFile apk = new ZipFile(getTargetContext().getApplicationInfo().sourceDir)) {
            ZipEntry payload = apk.getEntry(PAYLOAD_ENTRY);
            require(payload != null, "packaged payload is missing");
            return payload.getSize();
        }
    }

    private void scan(
            File file,
            byte[] packagedHash,
            long packagedSize,
            Set<String> forbiddenExtensions,
            List<String> violations)
            throws Exception {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    scan(child, packagedHash, packagedSize, forbiddenExtensions, violations);
                }
            }
            return;
        }

        String lowerName = file.getName().toLowerCase(java.util.Locale.ROOT);
        for (String extension : forbiddenExtensions) {
            if (lowerName.endsWith(extension)) {
                violations.add(file.getName());
                return;
            }
        }
        if (file.length() == packagedSize && Arrays.equals(packagedHash, sha256(file))) {
            violations.add(file.getName());
        }
    }

    private byte[] sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[8192];
        try (FileInputStream input = new FileInputStream(file)) {
            for (int read; (read = input.read(buffer)) >= 0; ) {
                if (read > 0) {
                    digest.update(buffer, 0, read);
                }
            }
        }
        return digest.digest();
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

    private static void equal(Object expected, Object actual, String label) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        }
    }

    private static void same(Object expected, Object actual, String label) {
        if (expected != actual) {
            throw new AssertionError(label + ": objects differ");
        }
    }

    private static String stackTrace(Throwable failure) {
        StringWriter output = new StringWriter();
        failure.printStackTrace(new PrintWriter(output));
        return output.toString();
    }
}
