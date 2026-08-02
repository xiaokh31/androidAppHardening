package ah.runtime.bootstrap;

import android.content.pm.ApplicationInfo;
import com.android.apksig.ApkVerifier;
import java.io.File;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.List;

/** Public-API-only signer gate for the no-Context instantiateClassLoader callback. */
public final class EarlySignerProbe {
    private EarlySignerProbe() {}

    public static EarlySignerResult verify(ApplicationInfo applicationInfo) {
        if (applicationInfo == null
                || applicationInfo.sourceDir == null
                || applicationInfo.sourceDir.isEmpty()) {
            throw PocFailure.create(
                    PocFailure.SIGNER_UNREADABLE_CODE,
                    "Framework sourceDir is unavailable");
        }

        File sourceApk = new File(applicationInfo.sourceDir);
        if (!sourceApk.isFile() || !sourceApk.canRead()) {
            throw PocFailure.create(
                    PocFailure.SIGNER_UNREADABLE_CODE,
                    "installed APK is not a readable regular file");
        }

        final ApkVerifier.Result result;
        try {
            result =
                    new ApkVerifier.Builder(sourceApk)
                            .setMinCheckedPlatformVersion(29)
                            .build()
                            .verify();
        } catch (Exception exception) {
            throw PocFailure.create(
                    PocFailure.SIGNER_INVALID_CODE,
                    "installed APK signature verification failed");
        }

        if (!result.isVerified()) {
            throw PocFailure.create(
                    PocFailure.SIGNER_INVALID_CODE,
                    "installed APK signature is invalid");
        }

        List<X509Certificate> currentSigners = result.getSignerCertificates();
        if (currentSigners == null || currentSigners.size() != 1) {
            throw PocFailure.create(
                    PocFailure.SIGNER_NON_UNIQUE_CODE,
                    "installed APK does not have exactly one current signer");
        }

        try {
            byte[] measured =
                    MessageDigest.getInstance("SHA-256")
                            .digest(currentSigners.get(0).getEncoded());
            byte[] expected = decodeExpectedSigner();
            if (!MessageDigest.isEqual(measured, expected)) {
                throw PocFailure.create(
                        PocFailure.SIGNER_MISMATCH_CODE,
                        "installed signer differs from the M0-05 fixture policy");
            }
            return new EarlySignerResult(measured);
        } catch (java.security.cert.CertificateEncodingException
                | NoSuchAlgorithmException exception) {
            throw PocFailure.create(
                    PocFailure.SIGNER_INVALID_CODE,
                    "verified signer certificate cannot be digested");
        }
    }

    private static byte[] decodeExpectedSigner() {
        String hex = BuildConfig.M005_EXPECTED_SIGNER_SHA256_HEX;
        if (hex == null || hex.length() != 64) {
            throw PocFailure.create(
                    PocFailure.SIGNER_MISMATCH_CODE,
                    "M0-05 fixture signer policy is unavailable");
        }
        byte[] result = new byte[32];
        for (int index = 0; index < result.length; index++) {
            int high = Character.digit(hex.charAt(index * 2), 16);
            int low = Character.digit(hex.charAt(index * 2 + 1), 16);
            if (high < 0 || low < 0) {
                throw PocFailure.create(
                        PocFailure.SIGNER_MISMATCH_CODE,
                        "M0-05 fixture signer policy is malformed");
            }
            result[index] = (byte) ((high << 4) | low);
        }
        return result;
    }
}
