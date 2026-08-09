package ah.runtime.loader;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.regex.Pattern;

/** Immutable, non-secret snapshot authenticated by the same Native payload handle. */
public final class AuthenticatedPayloadMetadata {
    private static final int FIXED_BYTES = 120;
    private static final int DIGEST_BYTES = 32;
    private static final Pattern JAVA_CLASS_NAME =
            Pattern.compile("(?:[A-Za-z_$][A-Za-z0-9_$]*\\.)*[A-Za-z_$][A-Za-z0-9_$]*");
    private static final String SHELL_FACTORY =
            "ah.runtime.bootstrap.ShellAppComponentFactory";

    private final String originalFactoryClassNameOrNull;
    private final int containerMajor;
    private final int containerMinor;
    private final int signerPolicyVersion;
    private final int riskPolicyVersion;
    private final byte[] buildId;
    private final byte[] keySlotId;
    private final byte[] packageNameSha256;
    private final byte[] currentSignerSha256;
    private final byte[][] signerLineageSha256;

    private AuthenticatedPayloadMetadata(
            String originalFactoryClassNameOrNull,
            int containerMajor,
            int containerMinor,
            int signerPolicyVersion,
            int riskPolicyVersion,
            byte[] buildId,
            byte[] keySlotId,
            byte[] packageNameSha256,
            byte[] currentSignerSha256,
            byte[][] signerLineageSha256) {
        this.originalFactoryClassNameOrNull = originalFactoryClassNameOrNull;
        this.containerMajor = containerMajor;
        this.containerMinor = containerMinor;
        this.signerPolicyVersion = signerPolicyVersion;
        this.riskPolicyVersion = riskPolicyVersion;
        this.buildId = buildId.clone();
        this.keySlotId = keySlotId.clone();
        this.packageNameSha256 = packageNameSha256.clone();
        this.currentSignerSha256 = currentSignerSha256.clone();
        this.signerLineageSha256 = deepCopy(signerLineageSha256);
    }

    static AuthenticatedPayloadMetadata parse(byte[] encoded) {
        if (encoded == null
                || encoded.length < FIXED_BYTES + DIGEST_BYTES
                || encoded[0] != 'A'
                || encoded[1] != 'H'
                || encoded[2] != 'M'
                || encoded[3] != 'D'
                || u16(encoded, 4) != 1
                || u16(encoded, 6) != encoded.length
                || !allZero(encoded, 20, 4)) {
            throw PayloadLoadException.create("METADATA");
        }
        int factoryLength = u16(encoded, 16);
        int lineageCount = u16(encoded, 18);
        if (u16(encoded, 8) != 2
                || u16(encoded, 10) != 0
                || u16(encoded, 12) != 1
                || u16(encoded, 14) != 1
                || factoryLength > 512
                || lineageCount < 1
                || lineageCount > 16) {
            throw PayloadLoadException.create("METADATA");
        }
        long required = (long) FIXED_BYTES + factoryLength + (long) lineageCount * DIGEST_BYTES;
        if (required != encoded.length) {
            throw PayloadLoadException.create("METADATA");
        }
        String factory = null;
        if (factoryLength != 0) {
            byte[] factoryBytes = Arrays.copyOfRange(encoded, FIXED_BYTES, FIXED_BYTES + factoryLength);
            try {
                factory = StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(factoryBytes))
                        .toString();
            } catch (CharacterCodingException failure) {
                throw PayloadLoadException.create("METADATA", failure);
            } finally {
                Arrays.fill(factoryBytes, (byte) 0);
            }
            if (factory.indexOf('\0') >= 0
                    || factory.equals(SHELL_FACTORY)
                    || !JAVA_CLASS_NAME.matcher(factory).matches()) {
                throw PayloadLoadException.create("METADATA");
            }
        }
        byte[][] lineage = new byte[lineageCount][];
        int cursor = FIXED_BYTES + factoryLength;
        for (int index = 0; index < lineageCount; index++) {
            lineage[index] = Arrays.copyOfRange(encoded, cursor, cursor + DIGEST_BYTES);
            cursor += DIGEST_BYTES;
        }
        return new AuthenticatedPayloadMetadata(
                factory,
                u16(encoded, 8),
                u16(encoded, 10),
                u16(encoded, 12),
                u16(encoded, 14),
                Arrays.copyOfRange(encoded, 24, 40),
                Arrays.copyOfRange(encoded, 40, 56),
                Arrays.copyOfRange(encoded, 56, 88),
                Arrays.copyOfRange(encoded, 88, 120),
                lineage);
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

    public byte[] packageNameSha256() {
        return packageNameSha256.clone();
    }

    public byte[] currentSignerSha256() {
        return currentSignerSha256.clone();
    }

    public byte[][] signerLineageSha256() {
        return deepCopy(signerLineageSha256);
    }

    private static int u16(byte[] value, int offset) {
        return (value[offset] & 0xff) | ((value[offset + 1] & 0xff) << 8);
    }

    private static boolean allZero(byte[] value, int offset, int count) {
        int aggregate = 0;
        for (int index = 0; index < count; index++) {
            aggregate |= value[offset + index];
        }
        return aggregate == 0;
    }

    private static byte[][] deepCopy(byte[][] source) {
        byte[][] copy = new byte[source.length][];
        for (int index = 0; index < source.length; index++) {
            copy[index] = source[index].clone();
        }
        return copy;
    }
}
