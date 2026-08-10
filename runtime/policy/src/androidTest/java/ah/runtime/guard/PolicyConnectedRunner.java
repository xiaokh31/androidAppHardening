package ah.runtime.guard;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import android.os.Process;
import java.util.Arrays;

/** Non-empty on-device policy checks used by :runtime:policy:connectedCheck. */
public final class PolicyConnectedRunner extends Instrumentation {
    private Bundle arguments;

    @Override
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        this.arguments = arguments == null ? Bundle.EMPTY : new Bundle(arguments);
        start();
    }

    @Override
    public void onStart() {
        Bundle result = new Bundle();
        try {
            if (arguments.containsKey("verify_apk")) {
                runFixtureVerification(result);
                finish(Activity.RESULT_OK, result);
                return;
            }
            byte[] current = digest(0x31);
            byte[] old = digest(0x17);
            VerifiedSignerIdentity identity =
                    new VerifiedSignerIdentity(current, new byte[][] {old, current});
            require(identity.currentSignerSha256Hex().length() == 64, "hex");
            require(identity.currentSignerAuditPrefix().length() == 12, "audit-prefix");
            byte[][] copy = identity.signerLineageSha256();
            copy[0][0] ^= 1;
            require(!Arrays.equals(copy[0], identity.signerLineageSha256()[0]), "lineage-copy");
            expect("AAH-RUNTIME-INTEGRITY-LINEAGE_INVALID", () ->
                    new VerifiedSignerIdentity(current, new byte[][] {current, current}));
            expect("AAH-RUNTIME-INTEGRITY-SIGNER_FORMAT", () ->
                    new VerifiedSignerIdentity(new byte[31], new byte[][] {new byte[31]}));
            ApplicationInfo self = getContext().getApplicationInfo();
            RuntimeSignerVerifier.Measurement measured = RuntimeSignerVerifier.verify(self);
            require(measured.currentSignerSha256().length == 32, "measured-signer");
            require(measured.signerLineageSha256().length == 1, "measured-lineage");
            require(RuntimeSignerVerifier.cacheSizeForTesting() == 1, "cache-size");
            result.putString(
                    "summary",
                    "policy_connected=true cases=8 policy_signer="
                            + IntegrityChecks.lowerHex(measured.currentSignerSha256())
                            + " policy_pid="
                            + Process.myPid()
                            + " cache_size=1");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable failure) {
            result.putString("summary", failure.getClass().getName() + ":" + failure.getMessage());
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    private void runFixtureVerification(Bundle result) {
        String sourceDir = requiredArgument("verify_apk");
        String packageName = requiredArgument("verify_package");
        String expectedCategory = requiredArgument("expected_category");
        int expectedLineageCount = Integer.parseInt(requiredArgument("expected_lineage_count"));
        ApplicationInfo candidate = new ApplicationInfo();
        candidate.sourceDir = sourceDir;
        candidate.packageName = packageName;
        try {
            RuntimeSignerVerifier.Measurement measured = RuntimeSignerVerifier.verify(candidate);
            require("VERIFIED".equals(expectedCategory), "unexpected verification success");
            require(measured.signerLineageSha256().length == expectedLineageCount,
                    "lineage-count");
            String expectedCurrent = arguments.getString("expected_current_sha256", "");
            if (!expectedCurrent.isEmpty()) {
                IntegrityChecks.requireEqual(
                        measured.currentSignerSha256(), decodeDigest(expectedCurrent), "SIGNER_MISMATCH");
            }
            result.putString("summary", "policy_fixture=true actual=VERIFIED lineage_count="
                    + measured.signerLineageSha256().length + " signer="
                    + IntegrityChecks.lowerHex(measured.currentSignerSha256()));
        } catch (RuntimeIntegrityFailure failure) {
            String actual = failure.getMessage().substring(RuntimeIntegrityFailure.PREFIX.length());
            require(expectedCategory.equals(actual),
                    "expected " + expectedCategory + " actual " + actual);
            require(expectedLineageCount == 0, "failure-lineage-count");
            result.putString("summary", "policy_fixture=true actual=" + actual
                    + " lineage_count=0");
        }
    }

    private String requiredArgument(String name) {
        String value = arguments.getString(name);
        require(value != null && !value.isEmpty(), "missing-" + name);
        return value;
    }

    private static byte[] decodeDigest(String value) {
        require(value.matches("[0-9a-f]{64}"), "digest-format");
        byte[] decoded = new byte[32];
        for (int index = 0; index < decoded.length; index++) {
            decoded[index] = (byte) Integer.parseInt(value.substring(index * 2, index * 2 + 2), 16);
        }
        return decoded;
    }

    private static byte[] digest(int seed) {
        byte[] result = new byte[32];
        for (int index = 0; index < result.length; index++) {
            result[index] = (byte) (seed + index);
        }
        return result;
    }

    private static void expect(String code, Runnable action) {
        try {
            action.run();
            throw new AssertionError("missing failure " + code);
        } catch (RuntimeException expected) {
            require(code.equals(expected.getMessage()), "wrong code");
        }
    }

    private static void require(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError(label);
        }
    }
}
