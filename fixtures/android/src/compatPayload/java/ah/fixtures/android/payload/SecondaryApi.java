package ah.fixtures.android.payload;

/** Compiled exclusively into the second in-memory DEX. */
public final class SecondaryApi {
    private SecondaryApi() {}

    public static String marker(String caller) {
        return "M0-05-CLASSES2:" + caller;
    }
}
