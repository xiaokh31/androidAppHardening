package ah.runtime.loader;

import android.content.pm.ApplicationInfo;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.Arrays;

/** Low-level authenticated loader facade consumed only by the later startup Guard. */
public final class PayloadRuntime {
    private static final int SIGNER_SHA256_BYTES = 32;

    private PayloadRuntime() {}

    enum OpenStage {
        NATIVE_HANDLE,
        METADATA_BYTES,
        METADATA_PARSE,
        METADATA_OBJECT,
        BUFFER_ARRAY,
        BUFFER_ELEMENT,
        SEARCH_PATH,
        CLASS_LOADER,
        LOADED_PAYLOAD,
        BEFORE_RETURN,
    }

    interface OpenFailureProbe {
        void hit(OpenStage stage, long nativeHandle);
    }

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
        return openVerifiedInternal(
                shellLoader, applicationInfo, installedSignerSha256, null);
    }

    /**
     * Applies a monotonic best-effort profile to this payload's retained anonymous mappings.
     * The mappings remain owned until LoadedPayload.close() because ART may continue
     * reading them; these controls raise capture cost but do not prevent privileged extraction.
     */
    public static MemoryProtectionCapabilities applyMemoryProfile(
            LoadedPayload payload, MemoryProfile profile) {
        if (payload == null || profile == null) {
            throw new IllegalArgumentException("AAH-RUNTIME-MEMORY-ARGUMENT");
        }
        return payload.applyMemoryProfile(profile);
    }

    static LoadedPayload openVerifiedForTesting(
            ClassLoader shellLoader,
            ApplicationInfo applicationInfo,
            byte[] installedSignerSha256,
            OpenFailureProbe failureProbe) {
        if (failureProbe == null) {
            throw PayloadLoadException.create("ARGUMENT");
        }
        return openVerifiedInternal(
                shellLoader, applicationInfo, installedSignerSha256, failureProbe);
    }

    private static LoadedPayload openVerifiedInternal(
            ClassLoader shellLoader,
            ApplicationInfo applicationInfo,
            byte[] installedSignerSha256,
            OpenFailureProbe failureProbe) {
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
            hit(failureProbe, OpenStage.NATIVE_HANDLE, nativeHandle);
            metadataBytes = NativePayloadBridge.nativeAuthenticatedMetadata(nativeHandle);
            hit(failureProbe, OpenStage.METADATA_BYTES, nativeHandle);
            hit(failureProbe, OpenStage.METADATA_PARSE, nativeHandle);
            metadata = AuthenticatedPayloadMetadata.parse(metadataBytes);
            hit(failureProbe, OpenStage.METADATA_OBJECT, nativeHandle);
            nativeBuffers = NativePayloadBridge.nativeDexBuffers(nativeHandle);
            hit(failureProbe, OpenStage.BUFFER_ARRAY, nativeHandle);
            readOnlyBuffers =
                    PayloadClassLoaders.requireReadOnlyDirect(
                            nativeBuffers, failureProbe, nativeHandle);
            nativeSearchPath =
                    PayloadClassLoaders.resolveNativeLibrarySearchPath(applicationInfo);
            hit(failureProbe, OpenStage.SEARCH_PATH, nativeHandle);
            provisionalLoader =
                    PayloadClassLoaders.create(readOnlyBuffers, nativeSearchPath, shellLoader);
            hit(failureProbe, OpenStage.CLASS_LOADER, nativeHandle);
            result = new LoadedPayload(
                    nativeHandle,
                    readOnlyBuffers,
                    provisionalLoader,
                    metadata);
            hit(failureProbe, OpenStage.LOADED_PAYLOAD, nativeHandle);
            hit(failureProbe, OpenStage.BEFORE_RETURN, nativeHandle);
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

    private static void hit(
            OpenFailureProbe failureProbe,
            OpenStage stage,
            long nativeHandle) {
        if (failureProbe != null) {
            failureProbe.hit(stage, nativeHandle);
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
