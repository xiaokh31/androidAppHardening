package ah.runtime.loader;

/** Immutable observation of best-effort current-process memory protections. */
public final class MemoryProtectionCapabilities {
    private final boolean dontDump;
    private final long lockedBytes;
    private final boolean processDumpable;
    private final long jitterMillis;

    MemoryProtectionCapabilities(
            boolean dontDump, long lockedBytes, boolean processDumpable, long jitterMillis) {
        if (lockedBytes < 0 || lockedBytes > 1024L * 1024L) {
            throw new IllegalArgumentException("AAH-RUNTIME-MEMORY-CAPABILITY");
        }
        if (jitterMillis != 0 && (jitterMillis < 20 || jitterMillis > 50)) {
            throw new IllegalArgumentException("AAH-RUNTIME-MEMORY-CAPABILITY");
        }
        this.dontDump = dontDump;
        this.lockedBytes = lockedBytes;
        this.processDumpable = processDumpable;
        this.jitterMillis = jitterMillis;
    }

    public boolean dontDump() {
        return dontDump;
    }

    public long lockedBytes() {
        return lockedBytes;
    }

    public boolean processDumpable() {
        return processDumpable;
    }

    long jitterMillis() {
        return jitterMillis;
    }
}
