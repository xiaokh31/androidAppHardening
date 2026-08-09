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
}
