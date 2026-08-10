package ah.runtime.guard;

import ah.runtime.loader.AuthenticatedPayloadMetadata;

/** Immutable non-secret startup configuration built only from authenticated same-handle data. */
public final class VerifiedStartupConfiguration {
    private final String originalFactoryClassNameOrNull;
    private final int containerMajor;
    private final int containerMinor;
    private final int signerPolicyVersion;
    private final int riskPolicyVersion;
    private final byte[] buildId;
    private final byte[] keySlotId;

    VerifiedStartupConfiguration(AuthenticatedPayloadMetadata metadata) {
        if (metadata == null) {
            throw RuntimeIntegrityFailure.create("METADATA");
        }
        originalFactoryClassNameOrNull = metadata.originalFactoryClassNameOrNull();
        containerMajor = metadata.containerMajor();
        containerMinor = metadata.containerMinor();
        signerPolicyVersion = metadata.signerPolicyVersion();
        riskPolicyVersion = metadata.riskPolicyVersion();
        buildId = metadata.buildId();
        keySlotId = metadata.keySlotId();
    }

    public String originalFactoryClassNameOrNull() {
        return originalFactoryClassNameOrNull;
    }

    public int containerMajor() {
        return containerMajor;
    }

    public int containerMinor() {
        return containerMinor;
    }

    public int signerPolicyVersion() {
        return signerPolicyVersion;
    }

    public int riskPolicyVersion() {
        return riskPolicyVersion;
    }

    public byte[] buildId() {
        return buildId.clone();
    }

    public byte[] keySlotId() {
        return keySlotId.clone();
    }
}
