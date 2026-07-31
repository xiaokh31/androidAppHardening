package ah.runtime.bootstrap;

import java.security.MessageDigest;
import java.util.Objects;

/** Immutable result of verifying the installed APK's unique current signer. */
public final class EarlySignerResult {
    private final byte[] certificateSha256;

    EarlySignerResult(byte[] certificateSha256) {
        this.certificateSha256 = Objects.requireNonNull(certificateSha256).clone();
    }

    public byte[] certificateSha256() {
        return certificateSha256.clone();
    }

    /** Post-Context diagnostic only; payload access never depends on this method. */
    public void requireMatches(byte[] observedCertificateSha256) {
        if (observedCertificateSha256 == null
                || !MessageDigest.isEqual(certificateSha256, observedCertificateSha256)) {
            throw PocFailure.create(
                    PocFailure.SIGNER_MISMATCH_CODE,
                    "post-start signer digest differs from the early verified signer");
        }
    }
}
