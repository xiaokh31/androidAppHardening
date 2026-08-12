package ah.runtime;

import ah.runtime.loader.MemoryProfile;
import ah.runtime.risk.RiskAction;
import ah.runtime.risk.RiskLevel;

public final class MemoryControlsSelfTest {
    private MemoryControlsSelfTest() {}

    public static void main(String[] args) {
        equal(MemoryProfile.BASELINE, MemoryControls.profileFor(RiskLevel.LOW, RiskAction.ALLOW));
        equal(MemoryProfile.ELEVATED, MemoryControls.profileFor(RiskLevel.MEDIUM, RiskAction.DEGRADE));
        equal(MemoryProfile.HIGH, MemoryControls.profileFor(RiskLevel.HIGH, RiskAction.DEGRADE));
        reject(RiskLevel.LOW, RiskAction.DEGRADE);
        reject(RiskLevel.MEDIUM, RiskAction.ALLOW);
        reject(RiskLevel.HIGH, RiskAction.ALLOW);

        MemoryProtectionReport report =
                new MemoryProtectionReport(true, 131072L, false, RiskLevel.HIGH);
        require(report.dontDump(), "dontDump");
        require(report.lockedBytes() == 131072L, "lockedBytes");
        require(!report.processDumpable(), "processDumpable");
        equal(RiskLevel.HIGH, report.level());
        expect("AAH-RUNTIME-MEMORY-CAPABILITY", () ->
                new MemoryProtectionReport(true, 1024L * 1024L + 1L, true, RiskLevel.LOW));
        System.out.println("M2-06 memory policy self-test PASS cases=11");
    }

    private static void reject(RiskLevel level, RiskAction action) {
        expect("AAH-RUNTIME-MEMORY-POLICY", () -> MemoryControls.profileFor(level, action));
    }

    private static void expect(String message, Runnable action) {
        try {
            action.run();
            throw new AssertionError("expected " + message);
        } catch (IllegalArgumentException expected) {
            require(message.equals(expected.getMessage()), "message");
        }
    }

    private static void equal(Object expected, Object actual) {
        require(expected.equals(actual), "expected=" + expected + " actual=" + actual);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
