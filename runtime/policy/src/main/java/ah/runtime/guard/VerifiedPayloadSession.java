package ah.runtime.guard;

import ah.runtime.loader.LoadedPayload;

/** Owns the authenticated provisional loader and its native anonymous mappings. */
public final class VerifiedPayloadSession implements AutoCloseable {
    private LoadedPayload loadedPayload;
    private VerifiedSignerIdentity signer;
    private VerifiedStartupConfiguration startupConfiguration;
    private boolean closed;

    VerifiedPayloadSession(
            LoadedPayload loadedPayload,
            VerifiedSignerIdentity signer,
            VerifiedStartupConfiguration startupConfiguration) {
        if (loadedPayload == null || signer == null || startupConfiguration == null) {
            throw RuntimeIntegrityFailure.create("SESSION");
        }
        this.loadedPayload = loadedPayload;
        this.signer = signer;
        this.startupConfiguration = startupConfiguration;
    }

    public synchronized ClassLoader provisionalClassLoader() {
        requireOpen();
        return loadedPayload.classLoader();
    }

    public synchronized VerifiedSignerIdentity signer() {
        requireOpen();
        return signer;
    }

    public synchronized VerifiedStartupConfiguration startupConfiguration() {
        requireOpen();
        return startupConfiguration;
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        LoadedPayload closing = loadedPayload;
        try {
            if (closing != null) {
                closing.close();
            }
        } finally {
            loadedPayload = null;
            signer = null;
            startupConfiguration = null;
        }
    }

    private void requireOpen() {
        if (closed) {
            throw RuntimeIntegrityFailure.create("CLOSED");
        }
    }
}
