package ah.runtime.loader;

import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.os.Process;
import dalvik.system.InMemoryDexClassLoader;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

final class PayloadClassLoaders {
    private static final int MAX_ZIP_ENTRIES = 4096;
    private static final int MAX_NAME_LENGTH = 512;

    private PayloadClassLoaders() {}

    static ByteBuffer[] requireReadOnlyDirect(
            ByteBuffer[] buffers,
            PayloadRuntime.OpenFailureProbe failureProbe,
            long nativeHandle) {
        if (buffers == null || buffers.length == 0 || buffers.length > 64) {
            throw PayloadLoadException.create("BUFFER");
        }
        ByteBuffer[] result = new ByteBuffer[buffers.length];
        for (int index = 0; index < buffers.length; index++) {
            ByteBuffer buffer = buffers[index];
            if (buffer == null || !buffer.isDirect() || buffer.capacity() == 0) {
                throw PayloadLoadException.create("BUFFER");
            }
            ByteBuffer readOnly = buffer.asReadOnlyBuffer();
            readOnly.position(0);
            result[index] = readOnly;
            if (failureProbe != null) {
                failureProbe.hit(PayloadRuntime.OpenStage.BUFFER_ELEMENT, nativeHandle);
            }
        }
        return result;
    }

    static String resolveNativeLibrarySearchPath(ApplicationInfo applicationInfo) {
        String[] processAbis =
                Process.is64Bit()
                        ? Build.SUPPORTED_64_BIT_ABIS
                        : Build.SUPPORTED_32_BIT_ABIS;
        if (processAbis == null || processAbis.length == 0) {
            throw PayloadLoadException.create("NATIVE-PATH");
        }

        Set<String> supported = new HashSet<>(Arrays.asList(processAbis));
        Set<String> apkAbis = new HashSet<>();
        Map<String, String> abiSpellings = new HashMap<>();
        Set<String> entryNames = new HashSet<>();
        int entryCount = 0;
        try (ZipFile apk = new ZipFile(applicationInfo.sourceDir)) {
            java.util.Enumeration<? extends ZipEntry> entries = apk.entries();
            while (entries.hasMoreElements()) {
                if (++entryCount > MAX_ZIP_ENTRIES) {
                    throw PayloadLoadException.create("NATIVE-PATH");
                }
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (name.length() > MAX_NAME_LENGTH || !entryNames.add(name)) {
                    throw PayloadLoadException.create("NATIVE-PATH");
                }
                if (!name.startsWith("lib/")) {
                    continue;
                }
                validateNativeEntry(name, entry.isDirectory());
                if (!entry.isDirectory()) {
                    String abi = name.substring(4, name.indexOf('/', 4));
                    String normalizedAbi = abi.toLowerCase(Locale.ROOT);
                    String priorSpelling = abiSpellings.putIfAbsent(normalizedAbi, abi);
                    if (priorSpelling != null && !priorSpelling.equals(abi)) {
                        throw PayloadLoadException.create("NATIVE-PATH");
                    }
                    if (supported.contains(abi)) {
                        apkAbis.add(abi);
                    }
                }
            }
        } catch (IOException | IndexOutOfBoundsException failure) {
            throw PayloadLoadException.create("NATIVE-PATH");
        }

        String selectedAbi = null;
        for (String abi : processAbis) {
            if (apkAbis.contains(abi)) {
                selectedAbi = abi;
                break;
            }
        }
        if (selectedAbi == null) {
            throw PayloadLoadException.create("NATIVE-PATH");
        }

        List<String> paths = new ArrayList<>(2);
        File nativeDirectory =
                applicationInfo.nativeLibraryDir == null
                        ? null
                        : new File(applicationInfo.nativeLibraryDir);
        boolean extractsNativeLibraries =
                (applicationInfo.flags & ApplicationInfo.FLAG_EXTRACT_NATIVE_LIBS) != 0;
        if (extractsNativeLibraries
                && nativeDirectory != null
                && nativeDirectory.isDirectory()
                && nativeDirectory.canRead()) {
            paths.add(nativeDirectory.getAbsolutePath());
        }

        paths.add(applicationInfo.sourceDir + "!/lib/" + selectedAbi);
        return String.join(File.pathSeparator, paths);
    }

    static ClassLoader create(
            ByteBuffer[] buffers,
            String nativeLibrarySearchPath,
            ClassLoader shellLoader) {
        if (nativeLibrarySearchPath == null || nativeLibrarySearchPath.isEmpty()) {
            throw PayloadLoadException.create("NATIVE-PATH");
        }
        return new InMemoryDexClassLoader(buffers, nativeLibrarySearchPath, shellLoader);
    }

    private static void validateNativeEntry(String name, boolean directory) {
        if (name.indexOf('\\') >= 0 || name.contains("//") || name.contains("/./")
                || name.contains("/../") || name.endsWith("/.") || name.endsWith("/..")) {
            throw PayloadLoadException.create("NATIVE-PATH");
        }
        String[] segments = name.split("/", -1);
        if (directory) {
            if (segments.length != 3 || !segments[2].isEmpty() || segments[1].isEmpty()) {
                throw PayloadLoadException.create("NATIVE-PATH");
            }
            return;
        }
        if (segments.length != 3
                || segments[1].isEmpty()
                || segments[2].isEmpty()
                || !segments[2].startsWith("lib")
                || !segments[2].endsWith(".so")) {
            throw PayloadLoadException.create("NATIVE-PATH");
        }
    }
}
