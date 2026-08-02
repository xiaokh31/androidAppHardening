package ah.runtime.bootstrap;

import android.content.pm.ApplicationInfo;
import java.nio.ByteBuffer;

/** Opens only the fixed sourceDir ConfigV2 entry and releases fields after signer binding. */
public final class EarlyConfigProbe {
    private EarlyConfigProbe() {}

    public static EarlyConfigResult open(
            ApplicationInfo applicationInfo,
            EarlySignerResult signer) {
        if (applicationInfo == null) {
            throw PocFailure.create(PocFailure.CONFIG_CODE, "ApplicationInfo is unavailable");
        }
        ByteBuffer config = StoredDexReader.readConfig(applicationInfo.sourceDir);
        ConfigV2Parser.Parsed parsed = ConfigV2Parser.parse(config);
        ClassLoaderProbe.record(ClassLoaderProbe.EARLY_CONFIG_PARSED, null, null);
        EarlyConfigResult result = ConfigV2Parser.authenticate(parsed, signer);
        ClassLoaderProbe.record(ClassLoaderProbe.EARLY_CONFIG_APK_AUTHENTICATED, null, null);
        return result;
    }
}
