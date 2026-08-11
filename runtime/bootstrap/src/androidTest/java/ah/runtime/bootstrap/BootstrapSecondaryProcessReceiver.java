package ah.runtime.bootstrap;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

/** Runs the same process-local bootstrap contract in a declared isolated app process. */
public final class BootstrapSecondaryProcessReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        try {
            BootstrapConnectedRunner.ProbeSummary summary =
                    BootstrapConnectedRunner.runProcessProbe(context.getApplicationInfo());
            Bundle result = new Bundle();
            result.putInt("install_count", summary.installCount);
            result.putInt("hook_count", summary.classLoaderHookCount);
            result.putInt("component_count", summary.componentCount);
            result.putInt("close_count", summary.closeCount);
            setResultExtras(result);
            setResultCode(Activity.RESULT_OK);
        } catch (Throwable failure) {
            Bundle result = new Bundle();
            result.putString("error", "AAH-RUNTIME-BOOT-PROCESS");
            setResultExtras(result);
            setResultCode(Activity.RESULT_CANCELED);
        }
    }
}
