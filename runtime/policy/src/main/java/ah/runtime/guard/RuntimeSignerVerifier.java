package ah.runtime.guard;

import android.content.pm.ApplicationInfo;
import android.os.Process;
import android.os.SystemClock;
import com.android.apksig.ApkVerifier;
import com.android.apksig.SigningCertificateLineage;
import com.android.apksig.apk.ApkUtils;
import com.android.apksig.util.DataSources;
import java.io.File;
import java.io.RandomAccessFile;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class RuntimeSignerVerifier {
    private static final int MIN_CHECKED_PLATFORM = 29;
    private static final int MAX_CACHE_RECORDS = 8;
    private static volatile String processStartId;
    private static final Map<CacheKey, Boolean> VERIFIED_RECORDS =
            new LinkedHashMap<CacheKey, Boolean>(MAX_CACHE_RECORDS + 1, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<CacheKey, Boolean> eldest) {
                    return size() > MAX_CACHE_RECORDS;
                }
            };

    private RuntimeSignerVerifier() {}

    static Measurement verify(ApplicationInfo applicationInfo) {
        validateApplicationInfo(applicationInfo);
        File sourceApk = new File(applicationInfo.sourceDir);
        if (!sourceApk.isAbsolute() || !sourceApk.isFile() || !sourceApk.canRead()) {
            throw RuntimeIntegrityFailure.create("SOURCE");
        }
        FileIdentity before = FileIdentity.read(sourceApk);
        final ApkVerifier.Result result;
        try {
            result =
                    new ApkVerifier.Builder(sourceApk)
                            .setMinCheckedPlatformVersion(MIN_CHECKED_PLATFORM)
                            .build()
                            .verify();
        } catch (Exception failure) {
            throw RuntimeIntegrityFailure.create("SIGNATURE_INVALID", failure);
        }

        if (!result.isVerified()) {
            throw classifyRejected(result);
        }
        List<X509Certificate> signers = result.getSignerCertificates();
        if (signers == null || signers.size() != 1) {
            throw RuntimeIntegrityFailure.create(
                    signers != null && signers.size() > 1
                            ? "MULTIPLE_CURRENT"
                            : "UNSIGNED");
        }

        byte[] current = digestCertificate(signers.get(0));
        SigningCertificateLineage officialLineage = result.getSigningCertificateLineage();
        List<X509Certificate> certificates =
                officialLineage == null
                        ? signers
                        : officialLineage.getCertificatesInLineage();
        if (certificates == null || certificates.isEmpty() || certificates.size() > 16) {
            throw RuntimeIntegrityFailure.create("LINEAGE_INVALID");
        }
        byte[][] lineage = new byte[certificates.size()][];
        for (int index = 0; index < certificates.size(); index++) {
            lineage[index] = digestCertificate(certificates.get(index));
        }
        IntegrityChecks.requireValidLineage(lineage, current, "LINEAGE_INVALID");

        ApkMetadata apkMetadata = readApkMetadata(sourceApk);
        if (!applicationInfo.packageName.equals(apkMetadata.packageName)) {
            throw RuntimeIntegrityFailure.create("PACKAGE_MISMATCH");
        }
        FileIdentity after = FileIdentity.read(sourceApk);
        if (!before.equals(after)) {
            throw RuntimeIntegrityFailure.create("INPUT_CHANGED");
        }
        Measurement measurement =
                new Measurement(current, lineage, apkMetadata.versionCode, after.modifiedMillis);
        remember(
                new CacheKey(
                        applicationInfo.packageName,
                        apkMetadata.versionCode,
                        after.modifiedMillis,
                        after.size,
                        current,
                        lineage,
                        processStartId()));
        return measurement;
    }

    private static ApkMetadata readApkMetadata(File sourceApk) {
        try (RandomAccessFile source = new RandomAccessFile(sourceApk, "r")) {
            java.nio.ByteBuffer manifest =
                    ApkUtils.getAndroidManifest(DataSources.asDataSource(source));
            long versionCode = ApkUtils.getLongVersionCodeFromBinaryAndroidManifest(manifest.duplicate());
            String packageName = ApkUtils.getPackageNameFromBinaryAndroidManifest(manifest.duplicate());
            if (packageName == null || packageName.isEmpty()) {
                throw RuntimeIntegrityFailure.create("APK_METADATA");
            }
            return new ApkMetadata(packageName, versionCode);
        } catch (Exception failure) {
            throw RuntimeIntegrityFailure.create("APK_METADATA", failure);
        }
    }

    private static RuntimeIntegrityFailure classifyRejected(ApkVerifier.Result result) {
        List<String> issueNames = new ArrayList<>();
        for (ApkVerifier.IssueWithParams issue : result.getAllErrors()) {
            issueNames.add(issue.getIssue().name());
        }
        int signerCount =
                result.getSignerCertificates() == null ? 0 : result.getSignerCertificates().size();
        return RuntimeIntegrityFailure.create(classifyRejectedCategory(signerCount, issueNames));
    }

    static String classifyRejectedCategory(int signerCount, List<String> issueNames) {
        boolean unsigned = false;
        boolean lineage = false;
        boolean multiple = signerCount > 1;
        if (issueNames == null) {
            return "SIGNATURE_INVALID";
        }
        for (String name : issueNames) {
            if (name == null) {
                continue;
            }
            unsigned |= name.startsWith("JAR_SIG_NO_") || name.equals("JAR_SIG_MISSING");
            multiple |= name.equals("V3_SIG_MULTIPLE_SIGNERS")
                    || name.equals("V4_SIG_MULTIPLE_SIGNERS");
            lineage |= name.contains("LINEAGE") || name.contains("PAST_SIGNERS")
                    || name.contains("POR_") || name.startsWith("V31_ROTATION_");
        }
        if (multiple) {
            return "MULTIPLE_CURRENT";
        }
        if (lineage) {
            return "LINEAGE_INVALID";
        }
        if (unsigned) {
            return "UNSIGNED";
        }
        return "SIGNATURE_INVALID";
    }

    private static byte[] digestCertificate(X509Certificate certificate) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded());
        } catch (CertificateEncodingException | NoSuchAlgorithmException failure) {
            throw RuntimeIntegrityFailure.create("SIGNATURE_INVALID", failure);
        }
    }

    private static void validateApplicationInfo(ApplicationInfo applicationInfo) {
        if (applicationInfo == null
                || applicationInfo.sourceDir == null
                || applicationInfo.sourceDir.isEmpty()
                || applicationInfo.packageName == null
                || applicationInfo.packageName.isEmpty()) {
            throw RuntimeIntegrityFailure.create("ARGUMENT");
        }
    }

    private static synchronized void remember(CacheKey key) {
        VERIFIED_RECORDS.put(key, Boolean.TRUE);
    }

    private static String processStartId() {
        String current = processStartId;
        if (current != null) {
            return current;
        }
        synchronized (RuntimeSignerVerifier.class) {
            if (processStartId == null) {
                processStartId = Process.myPid() + ":" + SystemClock.elapsedRealtime();
            }
            return processStartId;
        }
    }

    static synchronized int cacheSizeForTesting() {
        return VERIFIED_RECORDS.size();
    }

    static final class Measurement {
        private final byte[] currentSignerSha256;
        private final byte[][] signerLineageSha256;
        private final long versionCode;
        private final long apkLastModified;

        Measurement(
                byte[] currentSignerSha256,
                byte[][] signerLineageSha256,
                long versionCode,
                long apkLastModified) {
            IntegrityChecks.requireValidLineage(
                    signerLineageSha256, currentSignerSha256, "LINEAGE_INVALID");
            this.currentSignerSha256 = currentSignerSha256.clone();
            this.signerLineageSha256 = IntegrityChecks.deepCopy(signerLineageSha256);
            this.versionCode = versionCode;
            this.apkLastModified = apkLastModified;
        }

        byte[] currentSignerSha256() {
            return currentSignerSha256.clone();
        }

        byte[][] signerLineageSha256() {
            return IntegrityChecks.deepCopy(signerLineageSha256);
        }

        long versionCode() {
            return versionCode;
        }

        long apkLastModified() {
            return apkLastModified;
        }
    }

    private static final class FileIdentity {
        private final long size;
        private final long modifiedMillis;

        private FileIdentity(long size, long modifiedMillis) {
            this.size = size;
            this.modifiedMillis = modifiedMillis;
        }

        static FileIdentity read(File source) {
            return new FileIdentity(source.length(), source.lastModified());
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof FileIdentity)) {
                return false;
            }
            FileIdentity that = (FileIdentity) other;
            return size == that.size && modifiedMillis == that.modifiedMillis;
        }

        @Override
        public int hashCode() {
            return Long.hashCode(size) * 31 + Long.hashCode(modifiedMillis);
        }
    }

    private static final class ApkMetadata {
        private final String packageName;
        private final long versionCode;

        private ApkMetadata(String packageName, long versionCode) {
            this.packageName = packageName;
            this.versionCode = versionCode;
        }
    }

    private static final class CacheKey {
        private final String packageName;
        private final long versionCode;
        private final long modifiedMillis;
        private final long size;
        private final String current;
        private final String lineage;
        private final String processStartId;

        CacheKey(
                String packageName,
                long versionCode,
                long modifiedMillis,
                long size,
                byte[] current,
                byte[][] lineage,
                String processStartId) {
            this.packageName = packageName;
            this.versionCode = versionCode;
            this.modifiedMillis = modifiedMillis;
            this.size = size;
            this.current = IntegrityChecks.lowerHex(current);
            StringBuilder encoded = new StringBuilder(lineage.length * 65);
            for (byte[] digest : lineage) {
                encoded.append(IntegrityChecks.lowerHex(digest)).append(':');
            }
            this.lineage = encoded.toString();
            this.processStartId = processStartId;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof CacheKey)) {
                return false;
            }
            CacheKey that = (CacheKey) other;
            return versionCode == that.versionCode
                    && modifiedMillis == that.modifiedMillis
                    && size == that.size
                    && packageName.equals(that.packageName)
                    && current.equals(that.current)
                    && lineage.equals(that.lineage)
                    && processStartId.equals(that.processStartId);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(
                    new Object[] {
                        packageName,
                        versionCode,
                        modifiedMillis,
                        size,
                        current,
                        lineage,
                        processStartId
                    });
        }
    }
}
