package ah.runtime.guard;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;
import java.util.Arrays;

/** Non-empty on-device policy checks used by :runtime:policy:connectedCheck. */
public final class PolicyConnectedRunner extends Instrumentation {
    @Override
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        start();
    }

    @Override
    public void onStart() {
        Bundle result = new Bundle();
        try {
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
            result.putString("summary", "policy_connected=true cases=5");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable failure) {
            result.putString("summary", failure.getClass().getName() + ":" + failure.getMessage());
            finish(Activity.RESULT_CANCELED, result);
        }
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
