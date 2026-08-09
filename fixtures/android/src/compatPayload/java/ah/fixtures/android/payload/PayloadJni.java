package ah.fixtures.android.payload;

import java.nio.ByteBuffer;

final class PayloadJni {
    private PayloadJni() {}

    static String loadAndReadMarker() {
        try {
            System.loadLibrary("fixture_jni");
            return nativeMarker();
        } catch (LinkageError failure) {
            throw new IllegalStateException("AAH-P004: fixture JNI load failed", failure);
        }
    }

    static void throwWithCleanupForTesting() {
        nativeThrowWithCleanupForTesting();
    }

    static void unmapDirectBufferForTesting(ByteBuffer buffer) {
        nativeUnmapDirectBufferForTesting(buffer);
    }

    private static native String nativeMarker();

    private static native void nativeThrowWithCleanupForTesting();

    private static native void nativeUnmapDirectBufferForTesting(ByteBuffer buffer);
}
