package ah.runtime.loader;

import java.nio.ByteBuffer;

/** Owns the provisional payload loader and its Native anonymous DEX mappings. */
public final class LoadedPayload implements AutoCloseable {
    private PayloadMemoryHandle memoryHandle;
    private ByteBuffer[] dexBuffers;
    private ClassLoader classLoader;
    private AuthenticatedPayloadMetadata authenticatedMetadata;
    private boolean closed;

    LoadedPayload(
            long nativeHandle,
            ByteBuffer[] dexBuffers,
            ClassLoader classLoader,
            AuthenticatedPayloadMetadata authenticatedMetadata) {
        this.memoryHandle = new PayloadMemoryHandle(nativeHandle);
        this.dexBuffers = dexBuffers;
        this.classLoader = classLoader;
        this.authenticatedMetadata = authenticatedMetadata;
    }

    public synchronized ClassLoader classLoader() {
        requireOpen();
        return classLoader;
    }

    public synchronized AuthenticatedPayloadMetadata authenticatedMetadata() {
        requireOpen();
        return authenticatedMetadata;
    }

    synchronized MemoryProtectionCapabilities applyMemoryProfile(MemoryProfile profile) {
        if (closed || memoryHandle == null) {
            throw new IllegalStateException("AAH-RUNTIME-MEMORY-HANDLE");
        }
        return memoryHandle.applyMemoryProfile(profile);
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        PayloadMemoryHandle closingHandle = memoryHandle;
        try {
            if (closingHandle != null) {
                closingHandle.close();
            }
        } finally {
            memoryHandle = null;
            dexBuffers = null;
            classLoader = null;
            authenticatedMetadata = null;
        }
    }

    private void requireOpen() {
        if (closed) {
            throw PayloadLoadException.create("CLOSED");
        }
    }
}
