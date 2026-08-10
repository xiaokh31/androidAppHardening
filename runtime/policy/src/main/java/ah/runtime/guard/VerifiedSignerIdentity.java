package ah.runtime.guard;

/** Immutable unique current signer and ordered old-to-new signer lineage. */
public final class VerifiedSignerIdentity {
    private final byte[] currentSignerSha256;
    private final byte[][] signerLineageSha256;

    VerifiedSignerIdentity(byte[] currentSignerSha256, byte[][] signerLineageSha256) {
        IntegrityChecks.requireDigest(currentSignerSha256, "SIGNER_FORMAT");
        IntegrityChecks.requireValidLineage(
                signerLineageSha256, currentSignerSha256, "LINEAGE_INVALID");
        this.currentSignerSha256 = currentSignerSha256.clone();
        this.signerLineageSha256 = IntegrityChecks.deepCopy(signerLineageSha256);
    }

    public byte[] currentSignerSha256() {
        return currentSignerSha256.clone();
    }

    public String currentSignerSha256Hex() {
        return IntegrityChecks.lowerHex(currentSignerSha256);
    }

    /** Audit-safe prefix; never returns the complete digest. */
    public String currentSignerAuditPrefix() {
        return currentSignerSha256Hex().substring(0, 12);
    }

    public byte[][] signerLineageSha256() {
        return IntegrityChecks.deepCopy(signerLineageSha256);
    }
}
