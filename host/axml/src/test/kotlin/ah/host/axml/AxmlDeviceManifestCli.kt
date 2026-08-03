package ah.host.axml

import ah.host.inspector.ManifestSummary
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

object AxmlDeviceManifestCli {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 3) { "expected input Manifest, output Manifest and package name" }
        val repository = Path.of("").toAbsolutePath().normalize()
        val input = Path.of(args[0]).toAbsolutePath().normalize()
        val output = Path.of(args[1]).toAbsolutePath().normalize()
        require(input != output) { "input and output must differ" }
        require(isIgnoredOutput(repository, output)) { "output must be under build/ or artifacts/" }
        val packageName = args[2]
        require(PACKAGE_NAME.matches(packageName)) { "invalid fixture package name" }
        val inputBytes = Files.readAllBytes(input)
        val inputDigest = sha256(inputBytes)
        val request = ManifestTransformRequest(
            ManifestSummary(
                packageName,
                sha256(packageName.toByteArray(StandardCharsets.UTF_8)),
                29,
                36,
                APPLICATION_CLASS,
                ORIGINAL_FACTORY,
                null,
            ),
        )
        val result = BinaryManifestTransformer.transform(inputBytes, request)
        check(MessageDigest.isEqual(inputDigest, sha256(Files.readAllBytes(input)))) { "input Manifest changed" }
        Files.createDirectories(output.parent)
        Files.write(output, result.bytes)
        println(
            "M1-03 device Manifest PASS before=${hex(result.beforeSha256)} " +
                "after=${hex(result.afterSha256)} diff=${result.semanticDiff.changes.size}",
        )
    }

    private fun isIgnoredOutput(repository: Path, output: Path): Boolean = listOf("build", "artifacts").any { name ->
        val root = repository.resolve(name).normalize()
        output == root || output.startsWith(root)
    }

    private fun sha256(value: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(value)
    private fun hex(value: ByteArray): String = value.joinToString("") { "%02x".format(it) }

    private const val APPLICATION_CLASS = "ah.fixtures.android.payload.PayloadApplication"
    private const val ORIGINAL_FACTORY = "ah.fixtures.android.payload.OriginalAppComponentFactory"
    private val PACKAGE_NAME = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")
}
