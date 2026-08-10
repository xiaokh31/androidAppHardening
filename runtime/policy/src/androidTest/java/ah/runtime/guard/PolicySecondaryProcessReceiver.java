package ah.runtime.guard;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import android.os.Process;
import android.os.SystemClock;
import java.io.File;

/** Executes the real verifier twice in an independent process for cache consistency evidence. */
public final class PolicySecondaryProcessReceiver extends BroadcastReceiver {
    static final String ACTION = "ah.runtime.guard.VERIFY_SECONDARY_PROCESS";

    @Override
    public void onReceive(Context context, Intent intent) {
        Bundle result = getResultExtras(true);
        try {
            require(ACTION.equals(intent.getAction()), "action");
            RuntimeSignerVerifier.clearCacheForTesting();
            File secondaryReady = new File(context.getFilesDir(), "m2-03-secondary-ready");
            File primaryReady = new File(context.getFilesDir(), "m2-03-primary-ready");
            require(secondaryReady.createNewFile(), "secondary-ready");
            long deadline = SystemClock.elapsedRealtime() + 30_000L;
            while (!primaryReady.isFile() && SystemClock.elapsedRealtime() < deadline) {
                SystemClock.sleep(10L);
            }
            require(primaryReady.isFile(), "primary-ready-timeout");
            ApplicationInfo candidate = new ApplicationInfo();
            candidate.sourceDir = requiredExtra(intent, "verify_process_apk");
            candidate.packageName = requiredExtra(intent, "verify_process_package");
            RuntimeSignerVerifier.Measurement first = RuntimeSignerVerifier.verify(candidate);
            RuntimeSignerVerifier.Measurement second = RuntimeSignerVerifier.verify(candidate);
            require(!first.cacheHit() && second.cacheHit(), "secondary-cache");
            require(RuntimeSignerVerifier.cacheSizeForTesting() == 1, "secondary-cache-size");
            result.putString("signer_prefix", signerPrefix(second));
            result.putInt("pid", Process.myPid());
            result.putBoolean("cache_hit", true);
            setResultCode(Activity.RESULT_OK);
        } catch (Throwable failure) {
            result.putString("failure", failure.getClass().getSimpleName());
            setResultCode(Activity.RESULT_CANCELED);
        }
        setResultExtras(result);
    }

    private static String signerPrefix(RuntimeSignerVerifier.Measurement measurement) {
        return IntegrityChecks.lowerHex(measurement.currentSignerSha256()).substring(0, 12);
    }

    private static String requiredExtra(Intent intent, String name) {
        String value = intent.getStringExtra(name);
        require(value != null && !value.isEmpty(), "missing-" + name);
        return value;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
