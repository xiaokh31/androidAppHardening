package ah.runtime.bootstrap;

import android.app.AppComponentFactory;
import java.nio.ByteBuffer;

/** Legacy fixture-only M0-05 ownership proof. */
final class PocPayloadSession implements AutoCloseable {
    private ByteBuffer[] payloadBuffers;
    private ClassLoader provisionalLoader;
    private ClassLoader finalLoader;
    private AppComponentFactory originalFactory;
    private boolean ready;
    private boolean closed;
    private int closeCount;
    private boolean buffersCleared;

    PocPayloadSession(ByteBuffer[] payloadBuffers, ClassLoader provisionalLoader) {
        this.payloadBuffers = payloadBuffers.clone();
        this.provisionalLoader = provisionalLoader;
    }

    void setOriginalFactory(AppComponentFactory originalFactory) {
        this.originalFactory = originalFactory;
    }

    void setFinalLoader(ClassLoader finalLoader) {
        this.finalLoader = finalLoader;
    }

    void transferReady() {
        ready = true;
    }

    boolean isReady() {
        return ready;
    }

    int closeCount() {
        return closeCount;
    }

    boolean buffersCleared() {
        return buffersCleared;
    }

    boolean hasPartialReferences() {
        return payloadBuffers != null
                || provisionalLoader != null
                || finalLoader != null
                || originalFactory != null;
    }

    @Override
    public void close() {
        if (closed || ready) {
            return;
        }
        closed = true;
        closeCount++;
        if (payloadBuffers != null) {
            for (ByteBuffer payload : payloadBuffers) {
                if (payload == null || payload.isReadOnly()) {
                    continue;
                }
                ByteBuffer clear = payload.duplicate();
                clear.clear();
                while (clear.hasRemaining()) {
                    clear.put((byte) 0);
                }
            }
        }
        buffersCleared = true;
        payloadBuffers = null;
        provisionalLoader = null;
        finalLoader = null;
        originalFactory = null;
        if (ClassLoaderProbe.shouldFailSessionCloseForTesting()) {
            throw new IllegalStateException("synthetic session close failure");
        }
    }
}
