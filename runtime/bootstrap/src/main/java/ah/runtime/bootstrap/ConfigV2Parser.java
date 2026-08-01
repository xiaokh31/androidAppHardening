package ah.runtime.bootstrap;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.regex.Pattern;

/** Strict, bounded parser for the frozen 768-byte ConfigV2 PoC contract. */
final class ConfigV2Parser {
    static final int SIZE = 768;
    static final int SIGNER_OFFSET = 56;
    static final int SIGNER_SIZE = 32;
    static final int FACTORY_OFFSET = 180;
    static final int FACTORY_SLOT_SIZE = 512;
    static final int RESERVED_TAIL_OFFSET = 692;

    private static final int HAS_ORIGINAL_FACTORY = 1;
    private static final Pattern CLASS_NAME =
            Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)+");

    private ConfigV2Parser() {}

    static Parsed parse(ByteBuffer input) {
        if (input == null || input.remaining() != SIZE) {
            throw invalid("ConfigV2 must be exactly 768 bytes");
        }
        ByteBuffer config = input.slice().order(ByteOrder.LITTLE_ENDIAN);
        requireByte(config, 0, 'A', "magic");
        requireByte(config, 1, 'H', "magic");
        requireByte(config, 2, 'K', "magic");
        requireByte(config, 3, 'C', "magic");
        requireUnsignedShort(config, 4, 2, "major");
        requireUnsignedShort(config, 6, 0, "minor");

        int flags = unsignedShort(config, 8);
        if ((flags & ~HAS_ORIGINAL_FACTORY) != 0) {
            throw invalid("ConfigV2 flags contain unsupported bits");
        }
        requireUnsignedShort(config, 10, 0, "reserved");
        if (config.getInt(12) != SIZE) {
            throw invalid("ConfigV2 total_size is invalid");
        }
        requireUnsignedShort(config, 16, 1, "container major");
        requireUnsignedShort(config, 18, 1, "signer policy version");
        requireUnsignedShort(config, 20, 1, "risk policy version");

        int factoryLength = unsignedShort(config, 22);
        boolean hasFactory = (flags & HAS_ORIGINAL_FACTORY) != 0;
        String factory = null;
        if (!hasFactory) {
            if (factoryLength != 0 || !isZero(config, FACTORY_OFFSET, FACTORY_SLOT_SIZE)) {
                throw invalid("Factory slot must be empty when HAS_ORIGINAL_FACTORY is clear");
            }
        } else {
            if (factoryLength < 1 || factoryLength > FACTORY_SLOT_SIZE) {
                throw invalid("Factory length is outside ConfigV2 bounds");
            }
            if (!isZero(
                    config,
                    FACTORY_OFFSET + factoryLength,
                    FACTORY_SLOT_SIZE - factoryLength)) {
                throw invalid("Factory slot has a non-zero unused tail");
            }
            byte[] encoded = new byte[factoryLength];
            ByteBuffer factoryBytes = config.duplicate();
            factoryBytes.position(FACTORY_OFFSET);
            factoryBytes.get(encoded);
            factory = decodeFactory(encoded);
            if ("ah.runtime.bootstrap.ShellAppComponentFactory".equals(factory)) {
                throw invalid("Original Factory recursively names the Shell Factory");
            }
        }
        if (!isZero(config, RESERVED_TAIL_OFFSET, SIZE - RESERVED_TAIL_OFFSET)) {
            throw invalid("ConfigV2 reserved tail is non-zero");
        }

        byte[] signer = new byte[SIGNER_SIZE];
        ByteBuffer signerBytes = config.duplicate();
        signerBytes.position(SIGNER_OFFSET);
        signerBytes.get(signer);
        return new Parsed(factory, signer);
    }

    static EarlyConfigResult authenticate(Parsed parsed, EarlySignerResult signer) {
        if (parsed == null || signer == null
                || !MessageDigest.isEqual(
                        parsed.signerSha256,
                        signer.certificateSha256())) {
            throw PocFailure.create(
                    PocFailure.CONFIG_AUTH_CODE,
                    "ConfigV2 signer binding differs from the verified installed signer");
        }
        return new EarlyConfigResult(parsed.originalFactory);
    }

    private static String decodeFactory(byte[] encoded) {
        final String value;
        try {
            value =
                    StandardCharsets.UTF_8
                            .newDecoder()
                            .onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT)
                            .decode(ByteBuffer.wrap(encoded))
                            .toString();
        } catch (CharacterCodingException exception) {
            throw invalid("Original Factory is not strict UTF-8");
        }
        if (value.indexOf('\0') >= 0 || !CLASS_NAME.matcher(value).matches()) {
            throw invalid("Original Factory is not a canonical Java class name");
        }
        return value;
    }

    private static void requireByte(ByteBuffer config, int offset, int expected, String field) {
        if (Byte.toUnsignedInt(config.get(offset)) != expected) {
            throw invalid("ConfigV2 " + field + " is invalid");
        }
    }

    private static void requireUnsignedShort(
            ByteBuffer config,
            int offset,
            int expected,
            String field) {
        if (unsignedShort(config, offset) != expected) {
            throw invalid("ConfigV2 " + field + " is invalid");
        }
    }

    private static int unsignedShort(ByteBuffer config, int offset) {
        return Short.toUnsignedInt(config.getShort(offset));
    }

    private static boolean isZero(ByteBuffer config, int offset, int length) {
        for (int index = 0; index < length; index++) {
            if (config.get(offset + index) != 0) {
                return false;
            }
        }
        return true;
    }

    private static IllegalStateException invalid(String detail) {
        return PocFailure.create(PocFailure.CONFIG_CODE, detail);
    }

    static final class Parsed {
        final String originalFactory;
        final byte[] signerSha256;

        Parsed(String originalFactory, byte[] signerSha256) {
            this.originalFactory = originalFactory;
            this.signerSha256 = signerSha256.clone();
        }
    }
}
