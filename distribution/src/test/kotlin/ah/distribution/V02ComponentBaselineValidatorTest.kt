package ah.distribution

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

object V02ComponentBaselineValidatorTest {
    @JvmStatic
    fun main(args: Array<String>) {
        val repository = Path.of(requireNotNull(System.getProperty("ah.distribution.repo"))).toAbsolutePath().normalize()
        val components = repository.resolve("distribution/build/v0.2/components")
        val baseline = repository.resolve("docs/v0.2/evidence/V2-M0-02/v02-component-baseline.json")
        val candidate = repository.resolve("build/v0.2/candidate-component-manifest.json")
        check(Files.isRegularFile(baseline))
        val originalCandidate = if (Files.exists(candidate)) Files.readAllBytes(candidate) else null
        try {
            Files.deleteIfExists(candidate)
            validate(repository, components, baseline, candidate)
            val placeholder = Files.readString(components.resolve("bom.cdx.json"))
            check("\"timestamp\": \"1980-01-01T00:00:00Z\"" in placeholder)
            listOf("windows", "ubuntu").forEach { platform ->
                val releaseManifest = Files.readString(components.resolve("$platform/release-manifest.json"))
                check("\"sourceCommit\": \"${"0".repeat(40)}\"" in releaseManifest)
            }
            val bytes = Files.readAllBytes(baseline)
            val text = bytes.toString(StandardCharsets.UTF_8)
            val mutations = listOf(
                "\"releaseVersion\": \"0.2.0\"" to "\"releaseVersion\": \"0.2.1\"",
                "\"implementationManifestSha256\": \"" to "\"implementationManifestSha256\": \"0",
                "\"mode\": \"100644\"" to "\"mode\": \"100600\"",
                "\"variant\": \"source\"" to "\"variant\": \"debug\"",
                "\"platform\": \"all\"" to "\"platform\": \"extra\"",
                "\"entries\": [" to "\"extra\": true,\n  \"entries\": [",
            )
            mutations.forEachIndexed { index, pair ->
                check(pair.first in text)
                val copy = repository.resolve("distribution/build/v0.2/baseline-mutation-$index.json")
                Files.writeString(copy, text.replaceFirst(pair.first, pair.second))
                expectFailure { validate(repository, components, copy, candidate) }
            }
            Files.createDirectories(candidate.parent)
            Files.writeString(candidate, "{}\n")
            expectFailure { validate(repository, components, baseline, candidate) }
        } finally {
            if (originalCandidate == null) Files.deleteIfExists(candidate) else Files.write(candidate, originalCandidate)
        }
        println("V2-M0-02 component baseline validator self-test PASS")
    }

    private fun validate(repository: Path, components: Path, baseline: Path, candidate: Path) {
        V02ComponentBaselineValidator.main(
            arrayOf(
                "validate",
                "--repo", repository.toString(),
                "--components", components.toString(),
                "--baseline", baseline.toString(),
                "--output", candidate.toString(),
            ),
        )
    }
}
