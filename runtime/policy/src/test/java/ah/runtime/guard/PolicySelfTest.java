package ah.runtime.guard;

import ah.runtime.loader.AuthenticatedPayloadMetadata;
import ah.runtime.loader.UntrustedPayloadBinding;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.List;

/** Dependency-free JVM matrix executed by :runtime:policy:test. */
public final class PolicySelfTest {
    private int cases;

    public static void main(String[] arguments) throws Exception {
        require(arguments.length == 0, "arguments");
        PolicySelfTest self = new PolicySelfTest();
        self.run();
        System.out.println("M2-03 policy unit matrix PASS cases=" + self.cases);
    }

    private void run() throws Exception {
        byte[] current = digest(0x40);
        byte[] old = digest(0x10);
        VerifiedSignerIdentity identity =
                new VerifiedSignerIdentity(current, new byte[][] {old, current});
        check(identity.currentSignerSha256Hex().matches("[0-9a-f]{64}"), "lower-hex");
        check(identity.currentSignerAuditPrefix().length() == 12, "audit-prefix");
        byte[] signerCopy = identity.currentSignerSha256();
        signerCopy[0] ^= 1;
        check(!Arrays.equals(signerCopy, identity.currentSignerSha256()), "signer-copy");
        byte[][] lineageCopy = identity.signerLineageSha256();
        lineageCopy[0][0] ^= 1;
        check(!Arrays.equals(lineageCopy[0], identity.signerLineageSha256()[0]), "lineage-copy");

        expect("SIGNER_FORMAT", () -> new VerifiedSignerIdentity(new byte[31], new byte[][] {new byte[31]}));
        expect("LINEAGE_INVALID", () -> new VerifiedSignerIdentity(current, new byte[0][]));
        expect("LINEAGE_INVALID", () -> new VerifiedSignerIdentity(current, new byte[][] {current, current}));
        expect("LINEAGE_INVALID", () -> new VerifiedSignerIdentity(current, new byte[][] {current, old}));
        expect("LINEAGE_MISMATCH", () -> IntegrityChecks.requireLineageEqual(
                new byte[][] {old, current}, new byte[][] {current}, "LINEAGE_MISMATCH"));
        expect("LINEAGE_MISMATCH", () -> IntegrityChecks.requireLineageEqual(
                new byte[][] {old, current}, new byte[][] {current, old}, "LINEAGE_MISMATCH"));

        IntegrityResult verified = IntegrityResult.verified();
        check(verified.status() == IntegrityResult.Status.VERIFIED, "verified-status");
        check(verified.code().equals("AAH-RUNTIME-INTEGRITY-VERIFIED"), "verified-code");
        IntegrityResult rejected = IntegrityResult.rejected("PACKAGE_MISMATCH");
        check(rejected.status() == IntegrityResult.Status.REJECTED, "rejected-status");
        check(rejected.code().equals("AAH-RUNTIME-INTEGRITY-PACKAGE_MISMATCH"), "rejected-code");
        check(IntegrityResult.rejected("bad-value").code().endsWith("INTERNAL"), "code-normalize");
        check("UNSIGNED".equals(RuntimeSignerVerifier.classifyRejectedCategory(
                0, List.of("JAR_SIG_NO_SIGNATURES"))), "unsigned-map");
        check("MULTIPLE_CURRENT".equals(RuntimeSignerVerifier.classifyRejectedCategory(
                2, List.of())), "multiple-map");
        check("LINEAGE_INVALID".equals(RuntimeSignerVerifier.classifyRejectedCategory(
                1, List.of("V3_SIG_POR_DID_NOT_VERIFY"))), "lineage-map");
        check("SIGNATURE_INVALID".equals(RuntimeSignerVerifier.classifyRejectedCategory(
                0, List.of("V2_SIG_MALFORMED_SIGNERS"))), "invalid-map");

        byte[] buildId = Arrays.copyOf(old, 16);
        byte[] keySlotId = Arrays.copyOf(current, 16);
        byte[] packageDigest = digest(0x70);
        UntrustedPayloadBinding binding = binding(buildId, keySlotId, current);
        RuntimeSignerVerifier.Measurement measurement =
                new RuntimeSignerVerifier.Measurement(current, new byte[][] {old, current}, 7L, 11L);
        AuthenticatedPayloadMetadata metadata = metadata(
                "example.OriginalFactory",
                2,
                0,
                1,
                1,
                buildId,
                keySlotId,
                packageDigest,
                current,
                new byte[][] {old, current});
        IntegrityChecks.verifyPreReadSigner(binding, current);
        check(true, "pre-read-positive");
        IntegrityChecks.verifyAuthenticatedMetadata(metadata, binding, packageDigest, measurement);
        check(true, "metadata-positive");

        expect("SIGNER_MISMATCH", () -> IntegrityChecks.verifyPreReadSigner(
                binding(buildId, keySlotId, digest(0x41)), current));
        expect("PACKAGE_MISMATCH", () -> IntegrityChecks.verifyAuthenticatedMetadata(
                metadata, binding, digest(0x71), measurement));
        expect("SNAPSHOT_CHANGED", () -> IntegrityChecks.verifyAuthenticatedMetadata(
                metadata,
                binding(different16(buildId), keySlotId, current),
                packageDigest,
                measurement));
        expect("SNAPSHOT_CHANGED", () -> IntegrityChecks.verifyAuthenticatedMetadata(
                metadata,
                binding(buildId, different16(keySlotId), current),
                packageDigest,
                measurement));
        AuthenticatedPayloadMetadata badVersion = metadata(
                null, 2, 0, 1, 2, buildId, keySlotId, packageDigest, current,
                new byte[][] {old, current});
        expect("VERSION", () -> IntegrityChecks.verifyAuthenticatedMetadata(
                badVersion, binding, packageDigest, measurement));
        AuthenticatedPayloadMetadata wrongLineage = metadata(
                null, 2, 0, 1, 1, buildId, keySlotId, packageDigest, current,
                new byte[][] {current});
        expect("LINEAGE_MISMATCH", () -> IntegrityChecks.verifyAuthenticatedMetadata(
                wrongLineage, binding, packageDigest, measurement));
        VerifiedStartupConfiguration configuration = new VerifiedStartupConfiguration(metadata);
        check("example.OriginalFactory".equals(configuration.originalFactoryClassNameOrNull()), "factory-source");
        byte[] buildCopy = configuration.buildId();
        buildCopy[0] ^= 1;
        check(!Arrays.equals(buildCopy, configuration.buildId()), "config-copy");
    }

    private void check(boolean condition, String label) {
        require(condition, label);
        cases++;
    }

    private void expect(String category, ThrowingRunnable action) {
        try {
            action.run();
            throw new AssertionError("missing failure " + category);
        } catch (RuntimeIntegrityFailure expected) {
            require(
                    (RuntimeIntegrityFailure.PREFIX + category).equals(expected.getMessage()),
                    "wrong code " + category);
            cases++;
        } catch (Exception unexpected) {
            throw new AssertionError("unexpected checked failure", unexpected);
        }
    }

    private static UntrustedPayloadBinding binding(
            byte[] buildId, byte[] keySlotId, byte[] current) {
        try {
            Constructor<UntrustedPayloadBinding> constructor =
                    UntrustedPayloadBinding.class.getDeclaredConstructor(
                            byte[].class, byte[].class, byte[].class);
            constructor.setAccessible(true);
            return constructor.newInstance(buildId, keySlotId, current);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private static AuthenticatedPayloadMetadata metadata(
            String factory,
            int major,
            int minor,
            int signerVersion,
            int riskVersion,
            byte[] buildId,
            byte[] keySlotId,
            byte[] packageDigest,
            byte[] current,
            byte[][] lineage) {
        try {
            Constructor<AuthenticatedPayloadMetadata> constructor =
                    AuthenticatedPayloadMetadata.class.getDeclaredConstructor(
                            String.class,
                            int.class,
                            int.class,
                            int.class,
                            int.class,
                            byte[].class,
                            byte[].class,
                            byte[].class,
                            byte[].class,
                            byte[][].class);
            constructor.setAccessible(true);
            return constructor.newInstance(
                    factory,
                    major,
                    minor,
                    signerVersion,
                    riskVersion,
                    buildId,
                    keySlotId,
                    packageDigest,
                    current,
                    lineage);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private static byte[] digest(int seed) {
        byte[] result = new byte[32];
        for (int index = 0; index < result.length; index++) {
            result[index] = (byte) (seed + index);
        }
        return result;
    }

    private static byte[] different16(byte[] source) {
        byte[] copy = source.clone();
        copy[0] ^= 1;
        return copy;
    }

    private static void require(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError(label);
        }
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
