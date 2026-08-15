package ah.benchmarks.android

import android.content.Intent
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.FileInputStream
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.ceil

@RunWith(AndroidJUnit4::class)
class M305StartupBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun startupAndMemory() {
        val arguments = InstrumentationRegistry.getArguments()
        val fixtureId = requireToken(arguments.getString("fixtureId"), "fixtureId")
        val packageName = requirePackage(arguments.getString("targetPackage"))
        val mode = requireToken(arguments.getString("mode"), "mode")
        require(mode == "baseline" || mode == "protected")

        repeat(5) {
            shell("am force-stop $packageName")
            shell("am start -W -n $packageName/ah.fixtures.android.m301.FixtureActivity")
        }

        val samples = CopyOnWriteArrayList<Sample>()
        benchmarkRule.measureRepeated(
            packageName = packageName,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = CompilationMode.None(),
            startupMode = StartupMode.COLD,
            iterations = 30,
            setupBlock = { pressHome() },
        ) {
            val memory = MemoryPoller(packageName)
            memory.start()
            startActivityAndWait(Intent().setClassName(packageName, "ah.fixtures.android.m301.FixtureActivity"))
            val timing = awaitTiming(packageName)
            val stableAt = timing.interactiveMs + 5_000L
            while (SystemClock.elapsedRealtime() < stableAt) SystemClock.sleep(20)
            memory.stop()
            val stable = parseMemory(shell("dumpsys meminfo $packageName"))
            samples += Sample(
                timing.applicationMs - timing.processStartMs,
                timing.interactiveMs - timing.processStartMs,
                memory.peakPss,
                memory.peakNative,
                stable.first,
            )
        }
        require(samples.size == 30) { "wrong M3-05 Android sample count" }
        val observation = awaitTiming(packageName)
        if (mode == "protected") {
            require(observation.observedLevel == "LOW" && observation.observedAction == "ALLOW") {
                "fixed reference environment is not LOW/ALLOW"
            }
        }
        val json = buildJson(fixtureId, mode, packageName, samples, observation)
        val file = InstrumentationRegistry.getInstrumentation().targetContext.filesDir.resolve("m3-05-result.json")
        file.writeText(json + "\n", Charsets.UTF_8)
    }

    private fun awaitTiming(packageName: String): StartupObservation {
        val deadline = SystemClock.elapsedRealtime() + 10_000L
        do {
            val output = shell("content query --uri content://$packageName.events/events")
            val values = StartupObservation(
                field(output, "process_start_ms"),
                field(output, "application_on_create_ms"),
                field(output, "interactive_ms"),
                textField(output, "observed_level"),
                textField(output, "observed_action"),
            )
            if (values.processStartMs > 0 && values.applicationMs >= values.processStartMs
                && values.interactiveMs >= values.applicationMs && values.observedLevel.isNotEmpty()
                && values.observedAction.isNotEmpty()) return values
            SystemClock.sleep(20)
        } while (SystemClock.elapsedRealtime() < deadline)
        error("M3-05 target did not publish monotonic startup timing")
    }

    private inner class MemoryPoller(private val packageName: String) {
        private val running = AtomicBoolean()
        private var thread: Thread? = null
        @Volatile var peakPss = 0L
        @Volatile var peakNative = 0L

        fun start() {
            running.set(true)
            thread = Thread({
                while (running.get()) {
                    val memory = parseMemory(shell("dumpsys meminfo $packageName"))
                    peakPss = maxOf(peakPss, memory.first)
                    peakNative = maxOf(peakNative, memory.second)
                    SystemClock.sleep(20)
                }
            }, "m305-memory-poller").also(Thread::start)
        }

        fun stop() {
            running.set(false)
            thread?.join(5_000)
            require(thread?.isAlive != true && peakPss > 0) { "M3-05 memory polling failed" }
        }
    }

    private fun parseMemory(output: String): Pair<Long, Long> {
        val totalKb = Regex("(?m)^\\s*TOTAL(?: PSS)?:?\\s+([0-9]+)").find(output)?.groupValues?.get(1)?.toLongOrNull()
            ?: Regex("(?m)^\\s*TOTAL PSS:\\s*([0-9]+)").find(output)?.groupValues?.get(1)?.toLongOrNull()
            ?: 0L
        val nativeKb = Regex("(?m)^\\s*Native Heap\\s+([0-9]+)").find(output)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        return totalKb * 1024L to nativeKb * 1024L
    }

    private fun field(output: String, name: String): Long =
        Regex("(?:^|, )${Regex.escape(name)}=([0-9]+)").find(output)?.groupValues?.get(1)?.toLongOrNull() ?: 0L

    private fun textField(output: String, name: String): String =
        Regex("(?:^|, )${Regex.escape(name)}=([A-Z_]+)").find(output)?.groupValues?.get(1) ?: ""

    private fun shell(command: String): String {
        val descriptor: ParcelFileDescriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        return FileInputStream(descriptor.fileDescriptor).bufferedReader(Charsets.UTF_8).use { it.readText() }.also { descriptor.close() }
    }

    private fun buildJson(
        fixture: String,
        mode: String,
        packageName: String,
        values: List<Sample>,
        observation: StartupObservation,
    ): String {
        fun samples(selector: (Sample) -> Long) = values.joinToString(",", "[", "]") { selector(it).toString() }
        return "{" +
            "\"schemaVersion\":1," +
            "\"fixtureId\":\"$fixture\"," +
            "\"mode\":\"$mode\"," +
            "\"packageToken\":\"${packageName.substringAfterLast('.')}\"," +
            "\"observedRiskLevel\":\"${observation.observedLevel}\"," +
            "\"observedRiskAction\":\"${observation.observedAction}\"," +
            "\"sampleCount\":${values.size}," +
            "\"processToApplicationOnCreateMs\":${samples { it.applicationMs }}," +
            "\"processToInteractiveMs\":${samples { it.interactiveMs }}," +
            "\"peakPssBytes\":${samples { it.peakPss }}," +
            "\"nativeHeapPeakBytes\":${samples { it.peakNative }}," +
            "\"stablePssBytes\":${samples { it.stablePss }}" +
            "}"
    }

    private fun requireToken(value: String?, label: String): String = requireNotNull(value) { "missing $label" }
        .also { require(it.matches(Regex("[a-z0-9_.-]{1,64}"))) { "invalid $label" } }
    private fun requirePackage(value: String?): String = requireNotNull(value).also {
        require(it.matches(Regex("[a-z][a-z0-9_.]{2,127}"))) { "invalid package" }
    }

    private data class Sample(val applicationMs: Long, val interactiveMs: Long, val peakPss: Long, val peakNative: Long, val stablePss: Long)
    private data class StartupObservation(
        val processStartMs: Long,
        val applicationMs: Long,
        val interactiveMs: Long,
        val observedLevel: String,
        val observedAction: String,
    )
}
