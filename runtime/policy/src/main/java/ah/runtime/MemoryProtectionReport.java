package ah.runtime;

import ah.runtime.risk.RiskLevel;

/** Immutable, redacted summary of best-effort memory exposure controls. */
public final class MemoryProtectionReport {
    private final boolean dontDump;
    private final long lockedBytes;
    private final boolean processDumpable;
    private final RiskLevel level;

    MemoryProtectionReport(
            boolean dontDump, long lockedBytes, boolean processDumpable, RiskLevel level) {
        if (lockedBytes < 0 || lockedBytes > 1024L * 1024L) {
            throw new IllegalArgumentException("AAH-RUNTIME-MEMORY-CAPABILITY");
        }
        this.dontDump = dontDump;
        this.lockedBytes = lockedBytes;
        this.processDumpable = processDumpable;
        if (level == null) {
            throw new IllegalArgumentException("AAH-RUNTIME-MEMORY-CAPABILITY");
        }
        this.level = level;
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

    public RiskLevel level() {
        return level;
    }
}
