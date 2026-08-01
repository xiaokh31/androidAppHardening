package ah.runtime.bootstrap;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Dependency-free golden and tamper test for the exact Runtime ConfigV2 parser. */
public final class ConfigV2ParserSelfTest {
    private static final String FACTORY =
            "ah.fixtures.android.payload.OriginalAppComponentFactory";
    private static final byte[] SIGNER = signer();

    private ConfigV2ParserSelfTest() {}

    public static void main(String[] arguments) {
        byte[] valid = validConfig(FACTORY, SIGNER);
        assertFactory(valid, FACTORY);
        assertNoFactory();

        reject(resize(valid, 767), "AAH-P009");
        reject(resize(valid, 769), "AAH-P009");
        reject(setByte(valid, 0, 0), "AAH-P009");
        reject(setShort(valid, 4, 3), "AAH-P009");
        reject(setShort(valid, 6, 1), "AAH-P009");
        reject(setShort(valid, 8, 3), "AAH-P009");
        reject(setShort(valid, 10, 1), "AAH-P009");
        reject(setInt(valid, 12, 767), "AAH-P009");
        reject(setShort(valid, 16, 2), "AAH-P009");
        reject(setShort(valid, 18, 2), "AAH-P009");
        reject(setShort(valid, 20, 2), "AAH-P009");
        reject(setShort(valid, 22, 0), "AAH-P009");
        reject(setShort(valid, 22, 513), "AAH-P009");
        reject(setByte(valid, ConfigV2Parser.FACTORY_OFFSET, 0), "AAH-P009");
        reject(
                setByte(
                        valid,
                        ConfigV2Parser.FACTORY_OFFSET + FACTORY.length(),
                        1),
                "AAH-P009");
        reject(setByte(valid, ConfigV2Parser.RESERVED_TAIL_OFFSET, 1), "AAH-P009");

        byte[] invalidUtf8 = valid.clone();
        invalidUtf8[ConfigV2Parser.FACTORY_OFFSET] = (byte) 0xc3;
        invalidUtf8[ConfigV2Parser.FACTORY_OFFSET + 1] = (byte) 0x28;
        reject(invalidUtf8, "AAH-P009");

        byte[] recursive =
                validConfig("ah.runtime.bootstrap.ShellAppComponentFactory", SIGNER);
        reject(recursive, "AAH-P009");
        reject(validConfig("notCanonical", SIGNER), "AAH-P009");

        byte[] wrongSigner = SIGNER.clone();
        wrongSigner[0] ^= 1;
        try {
            ConfigV2Parser.authenticate(
                    ConfigV2Parser.parse(ByteBuffer.wrap(valid)),
                    new EarlySignerResult(wrongSigner));
            fail("AAH-P010 signer mismatch was accepted");
        } catch (IllegalStateException expected) {
            requireCode(expected, "AAH-P010");
        }

        System.out.println("PASS: M0-05 ConfigV2 golden + 20 tamper/no-factory cases");
    }

    private static void assertFactory(byte[] config, String expectedFactory) {
        EarlyConfigResult result =
                ConfigV2Parser.authenticate(
                        ConfigV2Parser.parse(ByteBuffer.wrap(config)),
                        new EarlySignerResult(SIGNER));
        if (!result.hasOriginalFactory()
                || !expectedFactory.equals(result.originalFactory())) {
            fail("authenticated Factory differs from the golden vector");
        }
    }

    private static void assertNoFactory() {
        byte[] config = validConfig(FACTORY, SIGNER);
        ByteBuffer bytes = ByteBuffer.wrap(config).order(ByteOrder.LITTLE_ENDIAN);
        bytes.putShort(8, (short) 0);
        bytes.putShort(22, (short) 0);
        Arrays.fill(
                config,
                ConfigV2Parser.FACTORY_OFFSET,
                ConfigV2Parser.FACTORY_OFFSET + ConfigV2Parser.FACTORY_SLOT_SIZE,
                (byte) 0);
        EarlyConfigResult result =
                ConfigV2Parser.authenticate(
                        ConfigV2Parser.parse(ByteBuffer.wrap(config)),
                        new EarlySignerResult(SIGNER));
        if (result.hasOriginalFactory() || result.originalFactory() != null) {
            fail("valid no-Factory ConfigV2 exposed a Factory");
        }
    }

    private static byte[] validConfig(String factory, byte[] signer) {
        byte[] factoryBytes = factory.getBytes(StandardCharsets.UTF_8);
        ByteBuffer config = ByteBuffer.allocate(ConfigV2Parser.SIZE).order(ByteOrder.LITTLE_ENDIAN);
        config.put("AHKC".getBytes(StandardCharsets.US_ASCII));
        config.putShort((short) 2);
        config.putShort((short) 0);
        config.putShort((short) 1);
        config.putShort((short) 0);
        config.putInt(ConfigV2Parser.SIZE);
        config.putShort((short) 1);
        config.putShort((short) 1);
        config.putShort((short) 1);
        config.putShort((short) factoryBytes.length);
        config.position(ConfigV2Parser.SIGNER_OFFSET);
        config.put(signer);
        config.position(ConfigV2Parser.FACTORY_OFFSET);
        config.put(factoryBytes);
        return config.array();
    }

    private static void reject(byte[] config, String code) {
        try {
            ConfigV2Parser.parse(ByteBuffer.wrap(config));
            fail(code + " malformed config was accepted");
        } catch (IllegalStateException expected) {
            requireCode(expected, code);
        }
    }

    private static byte[] resize(byte[] source, int size) {
        return Arrays.copyOf(source, size);
    }

    private static byte[] setByte(byte[] source, int offset, int value) {
        byte[] result = source.clone();
        result[offset] = (byte) value;
        return result;
    }

    private static byte[] setShort(byte[] source, int offset, int value) {
        byte[] result = source.clone();
        ByteBuffer.wrap(result).order(ByteOrder.LITTLE_ENDIAN).putShort(offset, (short) value);
        return result;
    }

    private static byte[] setInt(byte[] source, int offset, int value) {
        byte[] result = source.clone();
        ByteBuffer.wrap(result).order(ByteOrder.LITTLE_ENDIAN).putInt(offset, value);
        return result;
    }

    private static byte[] signer() {
        byte[] result = new byte[32];
        for (int index = 0; index < result.length; index++) {
            result[index] = (byte) index;
        }
        return result;
    }

    private static void requireCode(Throwable failure, String code) {
        if (failure.getMessage() == null || !failure.getMessage().startsWith(code + ":")) {
            fail("expected " + code + ", got " + failure);
        }
    }

    private static void fail(String message) {
        throw new AssertionError(message);
    }
}
