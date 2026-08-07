package ah.runtime.loader;

import android.content.pm.ApplicationInfo;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.Arrays;

/** Low-level authenticated loader facade consumed only by the later startup Guard. */
public final class PayloadRuntime {
    private static final int SIGNER_SHA256_BYTES = 32;

    private PayloadRuntime() {}

    /** Reads explicitly untrusted binding hints without recovering or authenticating payload data. */
    public static UntrustedPayloadBinding inspectBinding(ApplicationInfo applicationInfo) {
        validateApplicationInfo(applicationInfo);
        return UntrustedPayloadBinding.parse(
                NativePayloadBridge.nativeInspectBinding(applicationInfo.sourceDir));
    }

    /**
     * Opens an authenticated payload after the caller has independently measured the installed
     * APK signer. The returned provisional loader must remain unused until the M2-03 Guard accepts
     * the authenticated metadata snapshot.
     */
    public static LoadedPayload openVerified(
            ClassLoader shellLoader,
            ApplicationInfo applicationInfo,
            byte[] installedSignerSha256) {
        validateApplicationInfo(applicationInfo);
        if (shellLoader == null
                || installedSignerSha256 == null
                || installedSignerSha256.length != SIGNER_SHA256_BYTES) {
            throw PayloadLoadException.create("ARGUMENT");
        }

        byte[] signerCopy = installedSignerSha256.clone();
        long nativeHandle = 0;
        boolean committed = false;
        Throwable primary = null;
        byte[] metadataBytes = null;
        AuthenticatedPayloadMetadata metadata = null;
        ByteBuffer[] nativeBuffers = null;
        ByteBuffer[] readOnlyBuffers = null;
        String nativeSearchPath = null;
        ClassLoader provisionalLoader = null;
        LoadedPayload result = null;
        try {
            nativeHandle = NativePayloadBridge.nativeOpenVerifiedPayload(
                    applicationInfo.sourceDir,
                    applicationInfo.packageName,
                    signerCopy);
            metadataBytes = NativePayloadBridge.nativeAuthenticatedMetadata(nativeHandle);
            metadata = AuthenticatedPayloadMetadata.parse(metadataBytes);
            nativeBuffers = NativePayloadBridge.nativeDexBuffers(nativeHandle);
            readOnlyBuffers = PayloadClassLoaders.requireReadOnlyDirect(nativeBuffers);
            nativeSearchPath =
                    PayloadClassLoaders.resolveNativeLibrarySearchPath(applicationInfo);
            provisionalLoader =
                    PayloadClassLoaders.create(readOnlyBuffers, nativeSearchPath, shellLoader);
            result = new LoadedPayload(
                    nativeHandle,
                    readOnlyBuffers,
                    provisionalLoader,
                    metadata);
            committed = true;
            return result;
        } catch (RuntimeException | Error failure) {
            primary = failure;
            throw failure;
        } finally {
            Arrays.fill(signerCopy, (byte) 0);
            if (!committed && nativeHandle != 0) {
                try {
                    NativePayloadBridge.nativeClosePayload(nativeHandle);
                } catch (RuntimeException | Error cleanupFailure) {
                    if (primary == null) {
                        throw cleanupFailure;
                    }
                    try {
                        primary.addSuppressed(cleanupFailure);
                    } catch (RuntimeException | Error ignored) {
                        // Preserve the primary failure even when suppression itself cannot allocate.
                    }
                }
            }
            metadataBytes = null;
            metadata = null;
            nativeBuffers = null;
            readOnlyBuffers = null;
            nativeSearchPath = null;
            provisionalLoader = null;
            if (!committed) {
                result = null;
            }
        }
    }

    private static void validateApplicationInfo(ApplicationInfo applicationInfo) {
        if (applicationInfo == null
                || applicationInfo.sourceDir == null
                || applicationInfo.sourceDir.isEmpty()
                || !new File(applicationInfo.sourceDir).isAbsolute()
                || applicationInfo.packageName == null
                || applicationInfo.packageName.isEmpty()) {
            throw PayloadLoadException.create("ARGUMENT");
        }
    }
}
