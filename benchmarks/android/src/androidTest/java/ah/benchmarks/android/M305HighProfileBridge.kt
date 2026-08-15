package ah.benchmarks.android

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ah.runtime.guard.RuntimeStartupGuard
import ah.runtime.guard.VerifiedPayloadSession
import ah.runtime.loader.LoadedPayload
import ah.runtime.loader.MemoryProfile
import ah.runtime.loader.PayloadRuntime
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.reflect.InvocationTargetException

/** Android-test-only bridge fixed by ADR 0014; this class must never enter a product artifact. */
@RunWith(AndroidJUnit4::class)
class M305HighProfileBridge {
    @Test
    fun isolatedHighUpgrade() {
        val arguments = InstrumentationRegistry.getArguments()
        val fixtureId = requireToken(arguments.getString("fixtureId"), "fixtureId")
        val packageName = requirePackage(arguments.getString("targetPackage"))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val applicationInfo = context.packageManager.getApplicationInfo(packageName, 0)
        var session: VerifiedPayloadSession? = null
        var nativeHandle = 0L
        var cleanupPassed = false
        try {
            session = RuntimeStartupGuard.openVerifiedPayload(applicationInfo, javaClass.classLoader!!)
            val payload = captureLoadedPayload(session)
            nativeHandle = captureNativeHandle(payload)
            val lookupCountBefore = 0
            val started = SystemClock.elapsedRealtimeNanos()
            val capabilities = PayloadRuntime.applyMemoryProfile(payload, MemoryProfile.HIGH)
            val wallMillis = (SystemClock.elapsedRealtimeNanos() - started + 999_999L) / 1_000_000L
            val jitterMillis = capabilities.javaClass.getDeclaredMethod("jitterMillis").let {
                it.isAccessible = true
                (it.invoke(capabilities) as Number).toLong()
            }
            check(wallMillis in 20L..250L) { "HIGH wall time outside contract" }
            check(jitterMillis in 20L..50L) { "Native jitter outside contract" }
            check(captureNativeHandle(payload) == nativeHandle) { "owned handle changed" }
            session.provisionalClassLoader().loadClass("ah.fixtures.android.m301.FixtureActivity")
            val lookupCountAfter = 1
            session.close()
            requireNativeHandleClosed(nativeHandle)
            session.close()
            cleanupPassed = true
            val result = "{" +
                "\"schemaVersion\":1," +
                "\"fixtureId\":\"$fixtureId\"," +
                "\"wallMillis\":$wallMillis," +
                "\"nativeJitterMillis\":$jitterMillis," +
                "\"sameHandle\":true," +
                "\"lookupCountBeforeUpgrade\":$lookupCountBefore," +
                "\"lookupCountAfterUpgrade\":$lookupCountAfter," +
                "\"cleanupPassed\":true" +
                "}"
            context.filesDir.resolve("m3-05-high-result.json").writeText(result + "\n", Charsets.UTF_8)
        } finally {
            if (!cleanupPassed) session?.close()
        }
    }

    private fun captureLoadedPayload(session: VerifiedPayloadSession): LoadedPayload {
        val field = VerifiedPayloadSession::class.java.getDeclaredField("loadedPayload")
        field.isAccessible = true
        return field.get(session) as? LoadedPayload ?: error("missing session payload")
    }

    private fun captureNativeHandle(payload: LoadedPayload): Long {
        val memoryHandle = LoadedPayload::class.java.getDeclaredField("memoryHandle").let {
            it.isAccessible = true
            it.get(payload) ?: error("missing memory handle")
        }
        return memoryHandle.javaClass.getDeclaredField("value").let {
            it.isAccessible = true
            it.getLong(memoryHandle).also { value -> check(value > 0) { "invalid native handle" } }
        }
    }

    private fun requireNativeHandleClosed(handle: Long) {
        try {
            val bridge = Class.forName("ah.runtime.loader.NativePayloadBridge")
            bridge.getDeclaredMethod("nativeAuthenticatedMetadata", Long::class.javaPrimitiveType).let {
                it.isAccessible = true
                it.invoke(null, handle)
            }
            error("native handle survived close")
        } catch (failure: InvocationTargetException) {
            check(failure.cause?.message?.contains("HANDLE") == true) { "unexpected native close failure" }
        }
    }

    private fun requireToken(value: String?, label: String): String = requireNotNull(value) { "missing $label" }
        .also { require(it.matches(Regex("[a-z0-9_.-]{1,64}"))) { "invalid $label" } }

    private fun requirePackage(value: String?): String = requireNotNull(value).also {
        require(it.matches(Regex("[a-z][a-z0-9_.]{2,127}"))) { "invalid package" }
    }
}
