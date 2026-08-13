package ah.runtime.loader;

import java.nio.ByteBuffer;

final class NativePayloadBridge {
    static {
        System.loadLibrary("ah_runtime");
    }

    private NativePayloadBridge() {}

    static native byte[] nativeInspectBinding(String installedApkPath);

    static native long nativeOpenVerifiedPayload(
            String installedApkPath,
            String installedPackageName,
            byte[] installedSignerSha256);

    static native byte[] nativeAuthenticatedMetadata(long handle);

    static native ByteBuffer[] nativeDexBuffers(long handle);

    static native long[] nativeApplyMemoryProfile(long handle, int profile);

    static native void nativeClosePayload(long handle);
}
