package ah.integration.fixtures

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

object FixtureCatalog {
    private val fixtureIds = listOf(
        "java-single-dex",
        "kotlin-single-dex",
        "kotlin-multidex",
        "custom-application",
        "custom-factory",
        "startup-provider",
        "multi-process",
        "jni-four-abi",
        "jni-arm-only",
    )
    private val startupById = mapOf(
        "custom-application" to "application",
        "custom-factory" to "app_component_factory",
        "startup-provider" to "provider",
        "multi-process" to "multi_process_service",
    )

    fun load(root: Path): List<FixtureDescriptor> {
        val catalog = root.resolve("fixtures/catalog.yaml")
        val rows = ArrayList<MutableMap<String, String>>()
        var current: MutableMap<String, String>? = null
        Files.readAllLines(catalog, StandardCharsets.UTF_8).forEachIndexed { index, raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith('#') || line == "fixtures:" || line == "schema_version: 1") return@forEachIndexed
            if (line.startsWith("- id: ")) {
                current = linkedMapOf("id" to line.removePrefix("- id: ").trim())
                rows += requireNotNull(current)
                return@forEachIndexed
            }
            val target = current ?: error("catalog property before fixture at line ${index + 1}")
            val separator = line.indexOf(':')
            check(separator > 0) { "invalid catalog line ${index + 1}" }
            val key = line.substring(0, separator).trim()
            val value = line.substring(separator + 1).trim()
            check(target.put(key, value) == null) { "duplicate catalog property $key" }
        }
        check(rows.map { it["id"] } == fixtureIds) { "M3-01 fixture order or membership changed" }
        return rows.map { row ->
            val allowed = setOf(
                "id", "unsigned_fixture_apk", "language", "dex_mode", "startup_customization",
                "expected_events", "payload_abis", "expected_outcome",
            )
            check(row.keys == allowed) { "catalog keys changed for ${row["id"]}: ${row.keys}" }
            val id = row.getValue("id")
            val relativeApk = row.getValue("unsigned_fixture_apk")
            check(relativeApk == "android/build/fixtures/$id.apk")
            check(row.getValue("language") == if (id.startsWith("kotlin-")) "kotlin" else "java")
            check(row.getValue("dex_mode") == if (id == "kotlin-multidex") "multi" else "single")
            check(row.getValue("startup_customization") == startupById.getOrDefault(id, "none"))
            FixtureDescriptor(
                id = id,
                unsignedFixtureApk = root.resolve("fixtures").resolve(relativeApk).normalize(),
                expectedEvents = list(row.getValue("expected_events")),
                payloadAbis = list(row.getValue("payload_abis")),
                expectedOutcome = row.getValue("expected_outcome"),
            )
        }
    }

    private fun list(value: String): List<String> {
        check(value.startsWith('[') && value.endsWith(']')) { "catalog list is not inline" }
        val body = value.substring(1, value.length - 1).trim()
        return if (body.isEmpty()) emptyList() else body.split(',').map(String::trim).also { values ->
            check(values.all { it.matches(Regex("[a-z0-9_.-]+")) }) { "catalog list token is invalid" }
            check(values.distinct().size == values.size) { "catalog list has duplicates" }
        }
    }
}
