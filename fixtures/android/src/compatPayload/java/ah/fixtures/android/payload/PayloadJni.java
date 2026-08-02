package ah.fixtures.android.payload;

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

    private static native String nativeMarker();
}
