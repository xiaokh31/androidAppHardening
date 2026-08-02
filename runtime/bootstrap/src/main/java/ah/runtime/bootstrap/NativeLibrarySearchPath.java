package ah.runtime.bootstrap;

import java.util.Objects;

/** Sanitized native lookup decision consumed by the API 29 three-argument loader. */
public final class NativeLibrarySearchPath {
    private final String selectedAbi;
    private final boolean extractedDirectoryIncluded;
    private final boolean apkDirectoryIncluded;
    private final String classLoaderSearchPath;

    NativeLibrarySearchPath(
            String selectedAbi,
            boolean extractedDirectoryIncluded,
            boolean apkDirectoryIncluded,
            String classLoaderSearchPath) {
        this.selectedAbi = Objects.requireNonNull(selectedAbi);
        this.extractedDirectoryIncluded = extractedDirectoryIncluded;
        this.apkDirectoryIncluded = apkDirectoryIncluded;
        this.classLoaderSearchPath = Objects.requireNonNull(classLoaderSearchPath);
    }

    public String selectedAbi() {
        return selectedAbi;
    }

    public boolean extractedDirectoryIncluded() {
        return extractedDirectoryIncluded;
    }

    public boolean apkDirectoryIncluded() {
        return apkDirectoryIncluded;
    }

    public String classLoaderSearchPath() {
        return classLoaderSearchPath;
    }
}
