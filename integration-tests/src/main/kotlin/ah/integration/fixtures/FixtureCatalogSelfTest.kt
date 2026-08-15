package ah.integration.fixtures

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.zip.ZipFile

object FixtureCatalogSelfTest {
    @JvmStatic
    fun main(args: Array<String>) {
        val root = Path.of(requireNotNull(System.getProperty("m301.root"))).toAbsolutePath().normalize()
        val fixtures = FixtureCatalog.load(root)
        check(fixtures.size == 9)
        val hashes = linkedMapOf<String, String>()
        fixtures.forEach { fixture ->
            check(Files.isRegularFile(fixture.unsignedFixtureApk)) { "missing ${fixture.id}" }
            ZipFile(fixture.unsignedFixtureApk.toFile()).use { zip ->
                val entries = zip.entries().asSequence().filterNot { it.isDirectory }.toList()
                val names = entries.map { it.name }
                check(names.distinct().size == names.size) { "${fixture.id} contains duplicate entries" }
                check(names.none(::signatureEntry)) { "${fixture.id} contains signing metadata" }
                val dex = entries.filter { it.name.matches(Regex("classes(?:[2-9][0-9]*)?\\.dex")) }
                if (fixture.id == "kotlin-multidex") {
                    check(dex.size >= 2) { "kotlin-multidex is not multidex" }
                    val descriptor = "Lah/fixtures/android/m301/secondary/SecondaryMarker;".toByteArray(StandardCharsets.US_ASCII)
                    check(dex.drop(1).any { entry -> zip.getInputStream(entry).use { it.readBytes().contains(descriptor) } }) {
                        "multidex probe is not in a secondary DEX"
                    }
                } else {
                    check(dex.size == 1) { "${fixture.id} must be single DEX" }
                }
                val abis = names.filter { it.startsWith("lib/") && it.endsWith("/libfixture_jni.so") }
                    .map { it.split('/')[1] }.sorted()
                check(abis == fixture.payloadAbis.sorted()) { "${fixture.id} ABI mismatch $abis" }
            }
            hashes[fixture.id] = sha256(fixture.unsignedFixtureApk)
        }
        val schema = Files.readString(root.resolve("fixtures/catalog.schema.json"), StandardCharsets.UTF_8)
        listOf("schema_version", "unsigned_fixture_apk", "expected_events", "payload_abis", "expected_outcome").forEach {
            check("\"$it\"" in schema) { "catalog schema lacks $it" }
        }
        check(hashes.values.distinct().size == 9) { "fixture artifacts unexpectedly alias" }
        verifyConfigurationRelaunchContract()
        println("M3-01 fixture catalog PASS: ${hashes.keys.joinToString()}")
    }

    private fun verifyConfigurationRelaunchContract() {
        val provider = listOf("provider.ready", "startup_provider.create", "activity.create")
        check(FixtureDriver.matchDeviceEvents(29, "startup-provider", provider, provider)?.configurationRelaunch == false)
        check(
            FixtureDriver.matchDeviceEvents(
                29, "startup-provider", provider, provider + "activity.create",
            )?.configurationRelaunch == true,
        )
        check(FixtureDriver.matchDeviceEvents(36, "startup-provider", provider, provider + "activity.create") == null)
        check(FixtureDriver.matchDeviceEvents(29, "startup-provider", provider, provider + listOf("activity.create", "activity.create")) == null)

        val kotlin = listOf("provider.ready", "activity.create", "kotlin.marker", "multidex.class")
        val kotlinRelaunch = kotlin + listOf("activity.create", "kotlin.marker", "multidex.class")
        check(FixtureDriver.matchDeviceEvents(29, "kotlin-multidex", kotlin, kotlinRelaunch)?.configurationRelaunch == true)
        check(FixtureDriver.matchDeviceEvents(29, "kotlin-multidex", kotlin, kotlinRelaunch + "activity.create") == null)

        val multiProcess = listOf("provider.ready", "activity.create", "worker.create")
        check(
            FixtureDriver.matchDeviceEvents(
                29, "multi-process", multiProcess, multiProcess + "activity.create",
            )?.configurationRelaunch == true,
        )
        check(FixtureDriver.matchDeviceEvents(29, "multi-process", multiProcess, multiProcess + listOf("activity.create", "worker.create")) == null)
    }

    private fun signatureEntry(name: String): Boolean {
        val upper = name.uppercase()
        return upper == "META-INF/MANIFEST.MF" ||
            (upper.startsWith("META-INF/") && listOf(".SF", ".RSA", ".DSA", ".EC").any(upper::endsWith))
    }

    private fun sha256(path: Path): String = Files.newInputStream(path).use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
        digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private fun ByteArray.contains(needle: ByteArray): Boolean {
        if (needle.isEmpty() || needle.size > size) return false
        for (offset in 0..size - needle.size) {
            var matches = true
            for (index in needle.indices) if (this[offset + index] != needle[index]) { matches = false; break }
            if (matches) return true
        }
        return false
    }
}
