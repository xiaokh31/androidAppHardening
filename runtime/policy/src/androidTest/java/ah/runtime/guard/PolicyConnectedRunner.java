package ah.runtime.guard;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.os.SystemClock;
import ah.runtime.AbiCompatibility;
import ah.runtime.AbiCompatibilityPolicy;
import ah.runtime.risk.RiskConnectedAssertions;
import java.io.File;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Non-empty on-device policy checks used by :runtime:policy:connectedCheck. */
public final class PolicyConnectedRunner extends Instrumentation {
    private Bundle arguments;

    @Override
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        this.arguments = arguments == null ? Bundle.EMPTY : new Bundle(arguments);
        start();
    }

    @Override
    public void onStart() {
        Bundle result = new Bundle();
        try {
            if (arguments.containsKey("verify_apk")) {
                runFixtureVerification(result);
                finish(Activity.RESULT_OK, result);
                return;
            }
            byte[] current = digest(0x31);
            byte[] old = digest(0x17);
            VerifiedSignerIdentity identity =
                    new VerifiedSignerIdentity(current, new byte[][] {old, current});
            require(identity.currentSignerSha256Hex().length() == 64, "hex");
            require(identity.currentSignerAuditPrefix().length() == 12, "audit-prefix");
            byte[][] copy = identity.signerLineageSha256();
            copy[0][0] ^= 1;
            require(!Arrays.equals(copy[0], identity.signerLineageSha256()[0]), "lineage-copy");
            expect("AAH-RUNTIME-INTEGRITY-LINEAGE_INVALID", () ->
                    new VerifiedSignerIdentity(current, new byte[][] {current, current}));
            expect("AAH-RUNTIME-INTEGRITY-SIGNER_FORMAT", () ->
                    new VerifiedSignerIdentity(new byte[31], new byte[][] {new byte[31]}));
            if (!arguments.containsKey("verify_process_apk")) {
                LinkedHashSet<String> armOnly = new LinkedHashSet<>();
                armOnly.add("armeabi-v7a");
                armOnly.add("arm64-v8a");
                AbiCompatibility compatibility = AbiCompatibilityPolicy.evaluate(armOnly);
                require(compatibility.runtimeAvailableAbis().size() == 4, "runtime-abis");
                require(compatibility.inputNativeAbis().equals(armOnly), "input-abis");
                require(compatibility.outputEffectiveAbis().equals(armOnly), "output-abis");
                require(compatibility.limitations().size() == 1, "abi-limitation");
                String riskSummary = RiskConnectedAssertions.run(getTargetContext().getApplicationInfo());
                result.putString("summary", "policy_connected_smoke=true cases=11 " + riskSummary);
                finish(Activity.RESULT_OK, result);
                return;
            }
            ApplicationInfo self = new ApplicationInfo();
            self.sourceDir = requiredArgument("verify_process_apk");
            self.packageName = requiredArgument("verify_process_package");
            ProcessVerification processes = verifyAcrossProcesses(self);
            RuntimeSignerVerifier.Measurement first = processes.first;
            RuntimeSignerVerifier.Measurement measured = processes.second;
            require(measured.currentSignerSha256().length == 32, "measured-signer");
            require(measured.signerLineageSha256().length == 1, "measured-lineage");
            require(!first.cacheHit() && measured.cacheHit(), "primary-cache-hit");
            require(RuntimeSignerVerifier.cacheSizeForTesting() == 1, "cache-size");
            Bundle secondary = processes.secondary;
            String signerPrefix = signerPrefix(measured);
            require(signerPrefix.equals(secondary.getString("signer_prefix")), "process-signer");
            require(Process.myPid() != secondary.getInt("pid"), "process-pid");
            require(secondary.getBoolean("cache_hit"), "process-cache");
            result.putString(
                    "summary",
                    "policy_connected=true cases=15 signer_prefix="
                            + signerPrefix
                            + " primary_pid="
                            + Process.myPid()
                            + " secondary_pid="
                            + secondary.getInt("pid")
                            + " primary_cache_hit=true secondary_cache_hit=true cache_size=1");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable failure) {
            result.putString("summary", failure.getClass().getName() + ":" + failure.getMessage());
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    private void runFixtureVerification(Bundle result) {
        String sourceDir = requiredArgument("verify_apk");
        String packageName = requiredArgument("verify_package");
        String expectedCategory = requiredArgument("expected_category");
        int expectedLineageCount = Integer.parseInt(requiredArgument("expected_lineage_count"));
        ApplicationInfo candidate = new ApplicationInfo();
        candidate.sourceDir = sourceDir;
        candidate.packageName = packageName;
        try {
            RuntimeSignerVerifier.Measurement measured = RuntimeSignerVerifier.verify(candidate);
            String expectedCurrent = arguments.getString("expected_current_sha256", "");
            if (!expectedCurrent.isEmpty()) {
                IntegrityChecks.requireEqual(
                        measured.currentSignerSha256(), decodeDigest(expectedCurrent), "SIGNER_MISMATCH");
            }
            require("VERIFIED".equals(expectedCategory), "unexpected verification success");
            require(measured.signerLineageSha256().length == expectedLineageCount,
                    "lineage-count");
            result.putString("summary", "policy_fixture=true actual=VERIFIED lineage_count="
                    + measured.signerLineageSha256().length + " signer_prefix="
                    + signerPrefix(measured));
        } catch (RuntimeIntegrityFailure failure) {
            String actual = failure.getMessage().substring(RuntimeIntegrityFailure.PREFIX.length());
            require(expectedCategory.equals(actual),
                    "expected " + expectedCategory + " actual " + actual);
            require(expectedLineageCount == 0, "failure-lineage-count");
            result.putString("summary", "policy_fixture=true actual=" + actual
                    + " lineage_count=0");
        }
    }

    private String requiredArgument(String name) {
        String value = arguments.getString(name);
        require(value != null && !value.isEmpty(), "missing-" + name);
        return value;
    }

    private ProcessVerification verifyAcrossProcesses(ApplicationInfo self) throws Exception {
        File secondaryReady = new File(getContext().getFilesDir(), "m2-03-secondary-ready");
        File primaryReady = new File(getContext().getFilesDir(), "m2-03-primary-ready");
        secondaryReady.delete();
        primaryReady.delete();
        RuntimeSignerVerifier.clearCacheForTesting();
        HandlerThread callbackThread = new HandlerThread("m2-03-policy-callback");
        callbackThread.start();
        CountDownLatch completed = new CountDownLatch(1);
        AtomicInteger code = new AtomicInteger(Activity.RESULT_CANCELED);
        AtomicReference<Bundle> extras = new AtomicReference<>();
        BroadcastReceiver callback = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                code.set(getResultCode());
                extras.set(getResultExtras(false));
                completed.countDown();
            }
        };
        try {
            Intent request = new Intent(PolicySecondaryProcessReceiver.ACTION);
            request.setComponent(new ComponentName(getContext(), PolicySecondaryProcessReceiver.class));
            request.putExtra("verify_process_apk", self.sourceDir);
            request.putExtra("verify_process_package", self.packageName);
            getContext().sendOrderedBroadcast(
                    request,
                    null,
                    callback,
                    new Handler(callbackThread.getLooper()),
                    Activity.RESULT_CANCELED,
                    null,
                    null);
            long deadline = SystemClock.elapsedRealtime() + 30_000L;
            while (!secondaryReady.isFile() && SystemClock.elapsedRealtime() < deadline) {
                SystemClock.sleep(10L);
            }
            require(secondaryReady.isFile(), "secondary-ready-timeout");
            require(primaryReady.createNewFile(), "primary-ready");
            RuntimeSignerVerifier.Measurement first = RuntimeSignerVerifier.verify(self);
            RuntimeSignerVerifier.Measurement second = RuntimeSignerVerifier.verify(self);
            require(completed.await(30, TimeUnit.SECONDS), "secondary-timeout");
            require(code.get() == Activity.RESULT_OK, "secondary-result");
            Bundle result = extras.get();
            require(result != null, "secondary-extras");
            return new ProcessVerification(first, second, result);
        } finally {
            secondaryReady.delete();
            primaryReady.delete();
            callbackThread.quitSafely();
        }
    }

    private static String signerPrefix(RuntimeSignerVerifier.Measurement measurement) {
        return IntegrityChecks.lowerHex(measurement.currentSignerSha256()).substring(0, 12);
    }

    private static final class ProcessVerification {
        private final RuntimeSignerVerifier.Measurement first;
        private final RuntimeSignerVerifier.Measurement second;
        private final Bundle secondary;

        private ProcessVerification(
                RuntimeSignerVerifier.Measurement first,
                RuntimeSignerVerifier.Measurement second,
                Bundle secondary) {
            this.first = first;
            this.second = second;
            this.secondary = secondary;
        }
    }

    private static byte[] decodeDigest(String value) {
        require(value.matches("[0-9a-f]{64}"), "digest-format");
        byte[] decoded = new byte[32];
        for (int index = 0; index < decoded.length; index++) {
            decoded[index] = (byte) Integer.parseInt(value.substring(index * 2, index * 2 + 2), 16);
        }
        return decoded;
    }

    private static byte[] digest(int seed) {
        byte[] result = new byte[32];
        for (int index = 0; index < result.length; index++) {
            result[index] = (byte) (seed + index);
        }
        return result;
    }

    private static void expect(String code, Runnable action) {
        try {
            action.run();
            throw new AssertionError("missing failure " + code);
        } catch (RuntimeException expected) {
            require(code.equals(expected.getMessage()), "wrong code");
        }
    }

    private static void require(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError(label);
        }
    }
}
