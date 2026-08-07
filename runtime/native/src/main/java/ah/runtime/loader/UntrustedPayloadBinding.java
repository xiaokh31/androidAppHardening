package ah.runtime.loader;

import java.util.Arrays;

/** Unauthenticated container pre-read. Never use these fields as a security decision. */
public final class UntrustedPayloadBinding {
    private final byte[] buildId;
    private final byte[] keySlotId;
    private final byte[] currentSignerSha256;

    private UntrustedPayloadBinding(byte[] buildId, byte[] keySlotId, byte[] currentSignerSha256) {
        this.buildId = buildId.clone();
        this.keySlotId = keySlotId.clone();
        this.currentSignerSha256 = currentSignerSha256.clone();
    }

    static UntrustedPayloadBinding parse(byte[] encoded) {
        if (encoded == null
                || encoded.length != 72
                || encoded[0] != 'A'
                || encoded[1] != 'H'
                || encoded[2] != 'U'
                || encoded[3] != 'B'
                || u16(encoded, 4) != 1
                || u16(encoded, 6) != encoded.length) {
            throw PayloadLoadException.create("METADATA");
        }
        return new UntrustedPayloadBinding(
                Arrays.copyOfRange(encoded, 8, 24),
                Arrays.copyOfRange(encoded, 24, 40),
                Arrays.copyOfRange(encoded, 40, 72));
    }

    /** Unauthenticated build ID copied from the installed APK container. */
    public byte[] buildId() {
        return buildId.clone();
    }

    /** Unauthenticated key-slot ID copied from the installed APK container. */
    public byte[] keySlotId() {
        return keySlotId.clone();
    }

    /** Unauthenticated current-signer digest copied from the installed APK container. */
    public byte[] currentSignerSha256() {
        return currentSignerSha256.clone();
    }

    private static int u16(byte[] value, int offset) {
        return (value[offset] & 0xff) | ((value[offset + 1] & 0xff) << 8);
    }
}
