package ah.runtime.risk;

final class RiskSnapshot {
    final SignalState tracer;
    final SignalState jdwp;
    final SignalState debuggable;
    final SignalState mappings;
    final int mappingFamilyMask;
    final SignalState emulator;
    final int emulatorCharacteristicMask;

    RiskSnapshot(
            SignalState tracer,
            SignalState jdwp,
            SignalState debuggable,
            SignalState mappings,
            int mappingFamilyMask,
            SignalState emulator,
            int emulatorCharacteristicMask) {
        this.tracer = tracer;
        this.jdwp = jdwp;
        this.debuggable = debuggable;
        this.mappings = mappings;
        this.mappingFamilyMask = mappingFamilyMask & 0x3;
        this.emulator = emulator;
        this.emulatorCharacteristicMask = emulatorCharacteristicMask & 0x7;
    }
}
