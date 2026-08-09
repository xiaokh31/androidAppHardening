package ah.fixtures.android.m202;

import java.nio.ByteBuffer;

/** Synthetic M2-02 fixture hooks. This source set is never shipped as a product module. */
public final class M202NativeTestHooks {
    static {
        System.loadLibrary("fixture_jni");
    }

    private M202NativeTestHooks() {}

    public static void throwWithCleanup() {
        nativeThrowWithCleanupForTesting();
    }

    public static void unmapDirectBuffer(ByteBuffer buffer) {
        nativeUnmapDirectBufferForTesting(buffer);
    }

    private static native void nativeThrowWithCleanupForTesting();

    private static native void nativeUnmapDirectBufferForTesting(ByteBuffer buffer);
}
