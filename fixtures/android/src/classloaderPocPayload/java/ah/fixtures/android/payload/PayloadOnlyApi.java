package ah.fixtures.android.payload;

/** A marker that proves code unavailable to the parent APK loader can execute. */
public final class PayloadOnlyApi {
    private PayloadOnlyApi() {}

    public static String marker() {
        return "M0-04-IN-MEMORY";
    }
}
