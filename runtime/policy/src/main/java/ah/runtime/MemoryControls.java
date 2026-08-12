package ah.runtime;

import ah.runtime.loader.LoadedPayload;
import ah.runtime.loader.MemoryProfile;
import ah.runtime.loader.MemoryProtectionCapabilities;
import ah.runtime.loader.PayloadRuntime;
import ah.runtime.risk.RiskAction;
import ah.runtime.risk.RiskLevel;
import ah.runtime.risk.RiskReportV1;

/** Maps the frozen M2-05 risk result to monotonic best-effort memory controls. */
public final class MemoryControls {
    private MemoryControls() {}

    public static MemoryProtectionReport apply(LoadedPayload payload, RiskReportV1 risk) {
        if (payload == null || risk == null) {
            throw new IllegalArgumentException("AAH-RUNTIME-MEMORY-ARGUMENT");
        }
        MemoryProfile profile = profileFor(risk.level(), risk.action());
        MemoryProtectionCapabilities capabilities =
                PayloadRuntime.applyMemoryProfile(payload, profile);
        return new MemoryProtectionReport(
                capabilities.dontDump(),
                capabilities.lockedBytes(),
                capabilities.processDumpable(),
                risk.level());
    }

    static MemoryProfile profileFor(RiskLevel level, RiskAction action) {
        if (level == RiskLevel.LOW && action == RiskAction.ALLOW) {
            return MemoryProfile.BASELINE;
        }
        if (level == RiskLevel.MEDIUM && action == RiskAction.DEGRADE) {
            return MemoryProfile.ELEVATED;
        }
        if (level == RiskLevel.HIGH && action == RiskAction.DEGRADE) {
            return MemoryProfile.HIGH;
        }
        throw new IllegalArgumentException("AAH-RUNTIME-MEMORY-POLICY");
    }
}
