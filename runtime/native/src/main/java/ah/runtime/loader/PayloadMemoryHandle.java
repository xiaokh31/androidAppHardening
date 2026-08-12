package ah.runtime.loader;

final class PayloadMemoryHandle {
    private long value;

    PayloadMemoryHandle(long value) {
        if (value == 0) {
            throw PayloadLoadException.create("HANDLE");
        }
        this.value = value;
    }

    void close() {
        long closing = value;
        if (closing == 0) {
            return;
        }
        value = 0;
        NativePayloadBridge.nativeClosePayload(closing);
    }

    MemoryProtectionCapabilities applyMemoryProfile(MemoryProfile profile) {
        if (profile == null) {
            throw new IllegalArgumentException("AAH-RUNTIME-MEMORY-ARGUMENT");
        }
        long current = value;
        if (current == 0) {
            throw new IllegalStateException("AAH-RUNTIME-MEMORY-HANDLE");
        }
        long[] values = NativePayloadBridge.nativeApplyMemoryProfile(current, profile.ordinal());
        if (values == null || values.length != 4) {
            throw new IllegalStateException("AAH-RUNTIME-MEMORY-CAPABILITY");
        }
        return new MemoryProtectionCapabilities(
                values[0] != 0, values[1], values[2] != 0, values[3]);
    }
}
