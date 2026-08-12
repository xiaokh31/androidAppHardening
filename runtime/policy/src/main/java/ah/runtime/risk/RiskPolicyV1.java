package ah.runtime.risk;

import java.util.ArrayList;
import java.util.List;

final class RiskPolicyV1 {
    static final int TRACER_SCORE = 60;
    static final int JDWP_SCORE = 50;
    static final int DEBUGGABLE_SCORE = 20;
    static final int MAPPING_FAMILY_SCORE = 40;
    static final int MAPPING_CAP = 80;
    static final int EMULATOR_CHARACTERISTIC_SCORE = 10;
    static final int EMULATOR_CAP = 30;

    private RiskPolicyV1() {}

    static RiskReportV1 evaluate(RiskSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("AAH-RUNTIME-RISK-ARGUMENT");
        }
        List<RiskSignal> signals = new ArrayList<>(RiskSignalId.values().length);
        signals.add(signal(RiskSignalId.TRACER, snapshot.tracer, TRACER_SCORE));
        signals.add(signal(RiskSignalId.JDWP, snapshot.jdwp, JDWP_SCORE));
        signals.add(signal(RiskSignalId.DEBUGGABLE, snapshot.debuggable, DEBUGGABLE_SCORE));
        SignalState mappingState = snapshot.mappings == null
                        || snapshot.mappings == SignalState.UNAVAILABLE
                ? SignalState.UNAVAILABLE
                : snapshot.mappingFamilyMask == 0 ? SignalState.CLEAR : SignalState.DETECTED;
        int mappingScore = Math.min(
                MAPPING_CAP, Integer.bitCount(snapshot.mappingFamilyMask) * MAPPING_FAMILY_SCORE);
        signals.add(signal(RiskSignalId.INSTRUMENTATION_MAPPING, mappingState, mappingScore));
        SignalState emulatorState = snapshot.emulator == null
                        || snapshot.emulator == SignalState.UNAVAILABLE
                ? SignalState.UNAVAILABLE
                : snapshot.emulatorCharacteristicMask == 0
                        ? SignalState.CLEAR : SignalState.DETECTED;
        int emulatorScore = Math.min(
                EMULATOR_CAP,
                Integer.bitCount(snapshot.emulatorCharacteristicMask)
                        * EMULATOR_CHARACTERISTIC_SCORE);
        signals.add(signal(RiskSignalId.EMULATOR_COMPOSITE, emulatorState, emulatorScore));
        return new RiskReportV1(signals);
    }

    private static RiskSignal signal(RiskSignalId id, SignalState state, int detectedScore) {
        SignalState normalized = state == null ? SignalState.UNAVAILABLE : state;
        return new RiskSignal(
                id, normalized, normalized == SignalState.DETECTED ? detectedScore : 0);
    }
}
