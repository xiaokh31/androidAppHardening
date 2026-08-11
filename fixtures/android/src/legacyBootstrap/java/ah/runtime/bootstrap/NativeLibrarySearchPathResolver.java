package ah.runtime.bootstrap;

import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.os.Process;
import java.io.File;
import java.io.IOException;
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

/** Legacy fixture-only native lookup path resolver. */
public final class NativeLibrarySearchPathResolver {
    private static final int MAX_ZIP_ENTRIES = 4096;
    private static final int MAX_NAME_LENGTH = 512;

    private NativeLibrarySearchPathResolver() {}

    public static NativeLibrarySearchPath resolve(ApplicationInfo applicationInfo) {
        if (applicationInfo == null
                || applicationInfo.sourceDir == null
                || applicationInfo.sourceDir.isEmpty()) {
            throw PocFailure.create(PocFailure.JNI_CODE, "Framework sourceDir is unavailable");
        }

        String[] processAbis =
                Process.is64Bit()
                        ? Build.SUPPORTED_64_BIT_ABIS
                        : Build.SUPPORTED_32_BIT_ABIS;
        if (processAbis == null || processAbis.length == 0) {
            throw PocFailure.create(PocFailure.JNI_CODE, "process ABI list is empty");
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
                    throw failure("APK entry count exceeds the PoC bound");
                }
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (name.length() > MAX_NAME_LENGTH || !entryNames.add(name)) {
                    throw failure("APK contains an overlong or duplicate entry name");
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
                        throw failure("APK contains duplicate ABI aliases");
                    }
                    if (supported.contains(abi)) {
                        apkAbis.add(abi);
                    }
                }
            }
        } catch (IOException exception) {
            throw failure("APK native library directory cannot be inspected");
        }

        String selectedAbi = null;
        for (String abi : processAbis) {
            if (apkAbis.contains(abi)) {
                selectedAbi = abi;
                break;
            }
        }
        if (selectedAbi == null) {
            throw failure("APK has no native library for the current process ABI");
        }

        List<String> paths = new ArrayList<>(2);
        boolean extracted = false;
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
            extracted = true;
        }

        paths.add(applicationInfo.sourceDir + "!/lib/" + selectedAbi);
        return new NativeLibrarySearchPath(
                selectedAbi,
                extracted,
                true,
                String.join(File.pathSeparator, paths));
    }

    private static void validateNativeEntry(String name, boolean directory) {
        if (name.indexOf('\\') >= 0 || name.contains("//") || name.contains("/./")
                || name.contains("/../") || name.endsWith("/.") || name.endsWith("/..")) {
            throw failure("APK contains a non-canonical native path");
        }
        String[] segments = name.split("/", -1);
        if (directory) {
            if (segments.length != 3 || !segments[2].isEmpty() || segments[1].isEmpty()) {
                throw failure("APK contains a non-canonical native directory");
            }
            return;
        }
        if (segments.length != 3
                || segments[1].isEmpty()
                || segments[2].isEmpty()
                || !segments[2].startsWith("lib")
                || !segments[2].endsWith(".so")) {
            throw failure("APK contains a non-canonical native library path");
        }
    }

    private static IllegalStateException failure(String detail) {
        return PocFailure.create(PocFailure.JNI_CODE, detail);
    }
}
