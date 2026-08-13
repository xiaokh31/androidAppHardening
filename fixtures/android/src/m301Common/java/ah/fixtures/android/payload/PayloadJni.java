package ah.fixtures.android.payload;

public final class PayloadJni {
    static { System.loadLibrary("fixture_jni"); }
    private PayloadJni() {}
    private static native String nativeMarker();
    public static String marker() { return nativeMarker(); }
}
