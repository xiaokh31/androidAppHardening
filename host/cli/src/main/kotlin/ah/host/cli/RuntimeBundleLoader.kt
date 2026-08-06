package ah.host.cli

import ah.host.container.RuntimeAbi
import ah.host.repacker.RuntimeBundle
import ah.host.repacker.RuntimeTemplate
import java.io.InputStream
import java.security.MessageDigest
import java.util.Properties

internal fun interface RuntimeBundleProvider {
    fun load(): RuntimeBundle
}

internal object ClasspathRuntimeBundleProvider : RuntimeBundleProvider {
    private const val PROPERTIES = "/ah/runtime/runtime-bundle-v1.properties"
    private const val MAX_PROPERTIES_BYTES = 64 * 1024
    private const val MAX_TEMPLATE_BYTES = 64 * 1024 * 1024

    override fun load(): RuntimeBundle = try {
        loadVerified()
    } catch (failure: RuntimeBundleUnavailable) {
        throw failure
    } catch (_: Exception) {
        unavailable()
    }

    private fun loadVerified(): RuntimeBundle {
        val metadata = Properties().apply { load(readBounded(PROPERTIES, MAX_PROPERTIES_BYTES).inputStream()) }
        if (metadata.getProperty("version") != "1") unavailable()
        val bootstrap = readBounded("/ah/runtime/bootstrap.dex", RuntimeBundle.MAX_BOOTSTRAP_BYTES)
        requireHash(bootstrap, metadata.getProperty("bootstrap.sha256"))
        val templates = RuntimeAbi.entries.associateWith { abi ->
            val bytes = readBounded("/ah/runtime/${abi.directoryName}/libah_runtime.so", MAX_TEMPLATE_BYTES)
            val digest = requireHash(bytes, metadata.getProperty("${abi.directoryName}.sha256"))
            RuntimeTemplate(abi, bytes, digest)
        }
        return RuntimeBundle(bootstrap, templates)
    }

    private fun readBounded(name: String, maximumBytes: Int): ByteArray = resource(name).use { input ->
        val bytes = input.readNBytes(maximumBytes + 1)
        if (bytes.size > maximumBytes) unavailable()
        bytes
    }

    private fun resource(name: String): InputStream =
        ClasspathRuntimeBundleProvider::class.java.getResourceAsStream(name) ?: unavailable()

    private fun requireHash(bytes: ByteArray, expected: String?): ByteArray {
        if (expected == null || !expected.matches(Regex("[0-9a-f]{64}"))) unavailable()
        val actual = MessageDigest.getInstance("SHA-256").digest(bytes)
        if (actual.toHex() != expected) unavailable()
        return actual
    }

    private fun unavailable(): Nothing = throw RuntimeBundleUnavailable()

    private fun ByteArray.toHex(): String = joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
}

internal class RuntimeBundleUnavailable : RuntimeException("runtime bundle unavailable")
