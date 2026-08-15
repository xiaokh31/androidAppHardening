package ah.benchmarks.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dalvik.system.PathClassLoader
import org.junit.Test
import org.junit.runner.RunWith

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
        val nativeLibraryDirectory = requireNotNull(applicationInfo.nativeLibraryDir) {
            "target native library directory unavailable"
        }
        val isolatedLoader = PathClassLoader(
            context.applicationInfo.sourceDir,
            nativeLibraryDirectory,
            requireNotNull(context.classLoader.parent) { "bootstrap parent unavailable" },
        )
        val worker = isolatedLoader.loadClass("ah.benchmarks.android.M305HighProfileWorker")
        val result = worker.getMethod(
            "run",
            android.content.Context::class.java,
            String::class.java,
            String::class.java,
        ).invoke(null, context, fixtureId, packageName) as String
        context.filesDir.resolve("m3-05-high-result.json").writeText(result + "\n", Charsets.UTF_8)
    }

    private fun requireToken(value: String?, label: String): String = requireNotNull(value) { "missing $label" }
        .also { require(it.matches(Regex("[a-z0-9_.-]{1,64}"))) { "invalid $label" } }

    private fun requirePackage(value: String?): String = requireNotNull(value).also {
        require(it.matches(Regex("[a-z][a-z0-9_.]{2,127}"))) { "invalid package" }
    }
}
