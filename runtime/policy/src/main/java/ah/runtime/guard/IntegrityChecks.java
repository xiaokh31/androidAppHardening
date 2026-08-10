package ah.runtime.guard;

import ah.runtime.loader.AuthenticatedPayloadMetadata;
import ah.runtime.loader.UntrustedPayloadBinding;
import java.security.MessageDigest;

final class IntegrityChecks {
    private static final int DIGEST_BYTES = 32;
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private IntegrityChecks() {}

    static void verifyPreReadSigner(UntrustedPayloadBinding binding, byte[] measuredSigner) {
        if (binding == null) {
            throw RuntimeIntegrityFailure.create("BINDING");
        }
        requireEqual(binding.currentSignerSha256(), measuredSigner, "SIGNER_MISMATCH");
    }

    static void verifyAuthenticatedMetadata(
            AuthenticatedPayloadMetadata metadata,
            UntrustedPayloadBinding binding,
            byte[] packageNameSha256,
            RuntimeSignerVerifier.Measurement measurement) {
        if (metadata == null || binding == null || measurement == null) {
            throw RuntimeIntegrityFailure.create("METADATA");
        }
        requireEqual(metadata.packageNameSha256(), packageNameSha256, "PACKAGE_MISMATCH");
        requireEqual(
                metadata.currentSignerSha256(),
                measurement.currentSignerSha256(),
                "SIGNER_MISMATCH");
        requireLineageEqual(
                metadata.signerLineageSha256(),
                measurement.signerLineageSha256(),
                "LINEAGE_MISMATCH");
        requireBytesEqual(metadata.buildId(), binding.buildId(), 16, "SNAPSHOT_CHANGED");
        requireBytesEqual(metadata.keySlotId(), binding.keySlotId(), 16, "SNAPSHOT_CHANGED");
        if (metadata.containerMajor() != 2
                || metadata.containerMinor() != 0
                || metadata.signerPolicyVersion() != 1
                || metadata.riskPolicyVersion() != 1) {
            throw RuntimeIntegrityFailure.create("VERSION");
        }
    }

    static void requireValidLineage(byte[][] lineage, byte[] current, String category) {
        requireDigest(current, category);
        if (lineage == null || lineage.length < 1 || lineage.length > 16) {
            throw RuntimeIntegrityFailure.create(category);
        }
        for (int index = 0; index < lineage.length; index++) {
            requireDigest(lineage[index], category);
            for (int previous = 0; previous < index; previous++) {
                if (MessageDigest.isEqual(lineage[index], lineage[previous])) {
                    throw RuntimeIntegrityFailure.create(category);
                }
            }
        }
        requireEqual(lineage[lineage.length - 1], current, category);
    }

    static void requireLineageEqual(byte[][] actual, byte[][] expected, String category) {
        if (actual == null || expected == null || actual.length != expected.length) {
            throw RuntimeIntegrityFailure.create(category);
        }
        int aggregate = 0;
        for (int index = 0; index < actual.length; index++) {
            requireDigest(actual[index], category);
            requireDigest(expected[index], category);
            for (int offset = 0; offset < DIGEST_BYTES; offset++) {
                aggregate |= actual[index][offset] ^ expected[index][offset];
            }
        }
        if (aggregate != 0) {
            throw RuntimeIntegrityFailure.create(category);
        }
    }

    static void requireEqual(byte[] actual, byte[] expected, String category) {
        requireDigest(actual, category);
        requireDigest(expected, category);
        if (!MessageDigest.isEqual(actual, expected)) {
            throw RuntimeIntegrityFailure.create(category);
        }
    }

    private static void requireBytesEqual(
            byte[] actual, byte[] expected, int expectedLength, String category) {
        if (actual == null
                || expected == null
                || actual.length != expectedLength
                || expected.length != expectedLength
                || !MessageDigest.isEqual(actual, expected)) {
            throw RuntimeIntegrityFailure.create(category);
        }
    }

    static void requireDigest(byte[] value, String category) {
        if (value == null || value.length != DIGEST_BYTES) {
            throw RuntimeIntegrityFailure.create(category);
        }
    }

    static byte[][] deepCopy(byte[][] source) {
        if (source == null) {
            return null;
        }
        byte[][] copy = new byte[source.length][];
        for (int index = 0; index < source.length; index++) {
            copy[index] = source[index] == null ? null : source[index].clone();
        }
        return copy;
    }

    static String lowerHex(byte[] value) {
        requireDigest(value, "SIGNER_FORMAT");
        char[] encoded = new char[value.length * 2];
        for (int index = 0; index < value.length; index++) {
            int current = value[index] & 0xff;
            encoded[index * 2] = HEX[current >>> 4];
            encoded[index * 2 + 1] = HEX[current & 0x0f];
        }
        return new String(encoded);
    }
}
