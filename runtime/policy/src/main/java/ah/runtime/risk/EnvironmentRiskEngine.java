package ah.runtime.risk;

import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.os.Debug;
import android.os.SystemClock;
import java.util.Locale;

/**
 * Collects bypassable local cost signals. This report is not an integrity proof and never denies
 * startup; signer, AEAD and authenticated-integrity failures remain separate fail-closed gates.
 */
public final class EnvironmentRiskEngine {
    private static final long BUDGET_NANOS = 50_000_000L;
    // Class initialization happens before evaluate() enters, so one-time library loading is not
    // misclassified as signal collection time.
    private static final boolean NATIVE_READY = NativeRiskSignals.available();

    private EnvironmentRiskEngine() {}

    public static RiskReportV1 evaluate(ApplicationInfo applicationInfo) {
        return evaluateWithDependencies(
                applicationInfo,
                EnvironmentRiskEngine::collectNative,
                SystemClock::elapsedRealtimeNanos);
    }

    static RiskReportV1 evaluateWithDependencies(
            ApplicationInfo applicationInfo, NativeCollector nativeCollector, NanoClock clock) {
        if (applicationInfo == null) {
            throw new IllegalArgumentException("AAH-RUNTIME-RISK-ARGUMENT");
        }
        if (nativeCollector == null || clock == null) {
            throw new IllegalArgumentException("AAH-RUNTIME-RISK-ARGUMENT");
        }
        long started = clock.now();
        int[] nativeResult;
        try {
            nativeResult = nativeCollector.collect();
        } catch (RuntimeException | LinkageError unavailable) {
            nativeResult = new int[0];
        }
        if (nativeResult == null || nativeResult.length != 4) nativeResult = new int[0];
        SignalState tracer = nativeState(nativeResult, 1);
        SignalState mappings = nativeState(nativeResult, 2);
        int mappingMask = nativeResult.length == 4 && nativeResult[0] == 1 ? nativeResult[3] : 0;

        boolean debugger = false;
        SignalState jdwp = SignalState.UNAVAILABLE;
        try {
            debugger = Debug.isDebuggerConnected() || Debug.waitingForDebugger();
            jdwp = debugger ? SignalState.DETECTED : SignalState.CLEAR;
        } catch (RuntimeException | LinkageError unavailable) {
            jdwp = SignalState.UNAVAILABLE;
        }
        boolean debuggable = (applicationInfo.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        int emulatorMask = emulatorCharacteristics();
        SignalState emulator = emulatorMask == 0 ? SignalState.CLEAR : SignalState.DETECTED;

        long elapsed = clock.now() - started;
        if (elapsed < 0 || elapsed > BUDGET_NANOS) {
            return RiskPolicyV1.evaluate(new RiskSnapshot(
                    SignalState.UNAVAILABLE,
                    SignalState.UNAVAILABLE,
                    SignalState.UNAVAILABLE,
                    SignalState.UNAVAILABLE,
                    0,
                    SignalState.UNAVAILABLE,
                    0));
        }
        return RiskPolicyV1.evaluate(new RiskSnapshot(
                tracer,
                jdwp,
                debuggable ? SignalState.DETECTED : SignalState.CLEAR,
                mappings,
                mappingMask,
                emulator,
                emulatorMask));
    }

    interface NativeCollector {
        int[] collect();
    }

    interface NanoClock {
        long now();
    }

    private static int[] collectNative() {
        if (!NATIVE_READY) return new int[0];
        try {
            int[] result = NativeRiskSignals.collect();
            return result != null && result.length == 4 ? result : new int[0];
        } catch (RuntimeException | LinkageError unavailable) {
            return new int[0];
        }
    }

    private static SignalState nativeState(int[] result, int index) {
        if (result.length != 4 || result[0] != 1) return SignalState.UNAVAILABLE;
        if (result[index] == 2) return SignalState.DETECTED;
        if (result[index] == 1) return SignalState.CLEAR;
        return SignalState.UNAVAILABLE;
    }

    private static int emulatorCharacteristics() {
        int mask = 0;
        String fingerprint = lower(Build.FINGERPRINT);
        String model = lower(Build.MODEL);
        String hardware = lower(Build.HARDWARE);
        if (fingerprint.startsWith("generic") || fingerprint.contains("emulator")) mask |= 1;
        if (model.contains("emulator") || model.contains("android sdk built for")) mask |= 2;
        if (hardware.contains("ranchu") || hardware.contains("goldfish")) mask |= 4;
        return mask;
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
