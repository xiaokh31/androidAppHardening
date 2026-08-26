package ah.distribution

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

object V02ComponentBaselineValidatorTest {
    @JvmStatic
    fun main(args: Array<String>) {
        val repository = Path.of(requireNotNull(System.getProperty("ah.distribution.repo"))).toAbsolutePath().normalize()
        val components = repository.resolve("distribution/build/v0.2/components")
        val baseline = repository.resolve("docs/v0.2/evidence/V2-M0-02/v02-component-baseline.json")
        val candidate = repository.resolve("build/v0.2/candidate-component-manifest.json")
        check(Files.isRegularFile(baseline))
        documentMutations(Files.readAllBytes(baseline), baseline = true)
        for (name in listOf("implementation", "toolchain", "product-contract")) {
            documentMutations(Files.readAllBytes(baseline.resolveSibling("$name-manifest.json")), baseline = false)
        }
        exactGitTreeTest(repository)
        if (args.contentEquals(arrayOf("--documents-only"))) return
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
            val copy = repository.resolve("distribution/build/v0.2/wrong-baseline-path.json")
            Files.write(copy, bytes)
            expectFailure { validate(repository, components, copy, candidate) }
            Files.createDirectories(candidate.parent)
            Files.writeString(candidate, "{}\n")
            expectFailure { validate(repository, components, baseline, candidate) }
        } finally {
            if (originalCandidate == null) Files.deleteIfExists(candidate) else Files.write(candidate, originalCandidate)
        }
        println("V2-M0-02 component baseline validator self-test PASS")
    }

    private fun documentMutations(expected: ByteArray, baseline: Boolean) {
        fun validate(bytes: ByteArray) = if (baseline) V02ComponentBaselineValidator.validateBaselineDocument(bytes, expected)
            else V02ComponentBaselineValidator.validateManifestDocument(bytes, expected)
        validate(expected)
        fun root(): MutableMap<String, Any?> = StrictJson.parse(expected).asObject("test").toMutableMap()
        fun reject(label: String, change: (MutableMap<String, Any?>) -> Unit) {
            val value = root(); change(value)
            val mutated = CanonicalJson.prettyBytes(value)
            check(!mutated.contentEquals(expected)) { "no-op mutation $label" }
            check(runCatching { validate(mutated) }.isFailure) { "accepted semantic mutation $label" }
        }
        val original = root()
        for ((key, value) in original.filterKeys { it != "entries" }) {
            reject("root missing $key") { it.remove(key) }
            reject("root wrong $key") { it[key] = if (value is Long) value + 1 else "$value-mutated" }
        }
        reject("root extra") { it["extra"] = true }
        reject("root order") { value -> val first = value.keys.first(); val stored = value.remove(first); value[first] = stored }
        val entries = original.array("entries")
        reject("empty") { it["entries"] = emptyList<Any?>() }
        reject("missing") { it["entries"] = entries.drop(1) }
        reject("extra/duplicate") { it["entries"] = entries + entries.first() }
        reject("order") { it["entries"] = entries.reversed() }
        for ((index, raw) in entries.withIndex()) {
            val entry = raw.asObject("entry")
            for ((key, value) in entry) {
                reject("entry $index $key") {
                    val changed = entry.toMutableMap()
                    changed[key] = when (value) { null -> "unexpected"; is Long -> value + 1; else -> "$value-mutated" }
                    it["entries"] = entries.toMutableList().also { list -> list[index] = changed }
                }
            }
        }
        val pathKey = if (baseline) "logicalPath" else "path"
        for (path in listOf("", "../escape", "a\\b", "e\u0301", "docs/v0.2/evidence/V2-M0-02/self.json")) {
            reject("invalid path $path") { it["entries"] = entries.toMutableList().also { list ->
                list[0] = entries[0].asObject("entry").toMutableMap().also { entry -> entry[pathKey] = path }
            } }
        }
        for (mode in listOf("120000", "160000", "100600")) reject("mode $mode") {
            it["entries"] = entries.toMutableList().also { list ->
                list[0] = entries[0].asObject("entry").toMutableMap().also { entry -> entry["mode"] = mode }
            }
        }
        for (bytes in listOf(expected + byteArrayOf(0), expected.toString(StandardCharsets.UTF_8).replaceFirst("{", "{\"schemaVersion\":1,").toByteArray())) {
            check(runCatching { validate(bytes) }.isFailure)
        }
        println("document semantic matrix PASS baseline=$baseline entries=${entries.size}")
    }

    private fun exactGitTreeTest(repository: Path) {
        val parent = repository.resolve("distribution/build/v0.2").also(Files::createDirectories)
        val temporary = Files.createTempDirectory(parent, "git-tree-test-")
        val clone = temporary.resolve("repository")
        fun git(vararg arguments: String): String {
            val process = ProcessBuilder(listOf("git", "-c", "core.hooksPath=", "-C", clone.toString()) + arguments)
                .redirectErrorStream(true).start()
            val output = process.inputStream.readAllBytes().toString(StandardCharsets.UTF_8)
            check(process.waitFor(30, TimeUnit.SECONDS) && process.exitValue() == 0) { output }
            return output.trim()
        }
        fun commit() = git("-c", "user.name=Synthetic V02 Test", "-c", "user.email=v02-test@example.invalid", "commit", "--no-verify", "-m", "synthetic identity test")
        try {
            val process = ProcessBuilder("git", "clone", "--shared", "--no-checkout", repository.toString(), clone.toString())
                .redirectErrorStream(true).start()
            val output = process.inputStream.readAllBytes().toString(StandardCharsets.UTF_8)
            check(process.waitFor(30, TimeUnit.SECONDS) && process.exitValue() == 0) { output }
            git("read-tree", "HEAD")
            for (relative in listOf("docs/v0.2/identity-path-policy-v1.json", "docs/adr/0021-v0-2-maven-published-artifact-source-profiles.md")) {
                val path = clone.resolve(relative); Files.createDirectories(path.parent); Files.copy(repository.resolve(relative), path)
                git("add", "--", relative)
            }
            // This synthetic commit binds the revised policy before the real implementation is committed.
            if (git("diff", "--cached", "--name-only").isNotEmpty()) commit()
            val base = git("rev-parse", "HEAD")
            val expected = V02ComponentBaselineValidator.expectedManifestBytes(clone)
            val poison = clone.resolve("poison.txt").also { Files.writeString(it, "uncommitted adversarial bytes\n") }
            val blob = git("hash-object", "-w", poison.toString())
            val path = "host/cli/src/main/kotlin/ah/host/cli/CliMain.kt"
            git("update-index", "--add", "--cacheinfo", "100644,$blob,$path")
            val working = clone.resolve(path); Files.createDirectories(working.parent); Files.writeString(working, "different working bytes\n")
            val staged = V02ComponentBaselineValidator.expectedManifestBytes(clone)
            check(expected.keys == staged.keys && expected.all { (kind, bytes) -> bytes.contentEquals(staged.getValue(kind)) })
            commit()
            val changed = V02ComponentBaselineValidator.expectedManifestBytes(clone)
            expectFailure { V02ComponentBaselineValidator.validateManifestDocument(expected.getValue("implementation"), changed.getValue("implementation")) }
            for ((mode, name) in listOf("120000" to "host/symlink", "160000" to "host/gitlink", "100644" to "host/e\u0301", "100644" to "Host/cli/src/main/kotlin/ah/host/cli/CliMain.kt")) {
                git("read-tree", base)
                git("update-index", "--add", "--cacheinfo", "$mode,${if (mode == "160000") base else blob},$name")
                commit()
                expectFailure { V02ComponentBaselineValidator.expectedManifestBytes(clone) }
            }
        } finally { temporary.toFile().deleteRecursively() }
        println("exact Git tree: index/working poison ignored; committed product/mode/path mutations rejected")
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

    private fun Any?.asObject(label: String): MutableMap<String, Any?> {
        check(this is Map<*, *>) { label }
        return entries.associateTo(linkedMapOf()) { (key, value) -> check(key is String); key to value }
    }

    private fun Map<String, Any?>.array(key: String): List<Any?> {
        val value = get(key); check(value is List<*>); return value
    }
}
