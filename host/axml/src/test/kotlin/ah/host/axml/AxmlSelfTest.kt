package ah.host.axml

import ah.host.inspector.ManifestSummary
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.LinkedHashMap
import java.util.Random
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

object AxmlSelfTest {
    @JvmStatic
    fun main(args: Array<String>) {
        val reportDir = Path.of(System.getProperty("ah.axml.reportDir"))
        Files.createDirectories(reportDir)
        val fixtures = linkedMapOf(
            "utf8-default" to SyntheticManifest.build(utf8 = true, applicationClass = null, factoryClass = null),
            "utf8-custom-app" to SyntheticManifest.build(utf8 = true, applicationClass = ".FixtureApplication", factoryClass = null),
            "utf8-no-resource-map" to SyntheticManifest.build(
                utf8 = true,
                applicationClass = ".FixtureApplication",
                factoryClass = null,
                includeResourceMap = false,
            ),
            "utf8-existing-factory" to SyntheticManifest.build(
                utf8 = true,
                applicationClass = ".FixtureApplication",
                factoryClass = ".FixtureFactory",
                metadata = true,
                unknownChunk = true,
            ),
            "utf16-resource-reference" to SyntheticManifest.build(
                utf8 = false,
                applicationClass = "ah.fixtures.axml.FixtureApplication",
                factoryClass = "FixtureFactory",
                metadata = true,
                resourceReference = true,
            ),
        )
        val transformRows = ArrayList<String>()
        for ((name, fixture) in fixtures) {
            val request = requestFor(fixture)
            val first = BinaryManifestTransformer.transform(fixture.bytes, request)
            val second = BinaryManifestTransformer.transform(fixture.bytes.copyOf(), requestFor(fixture))
            check(first.bytes.contentEquals(second.bytes)) { "$name is not byte deterministic" }
            check(first.beforeSha256.contentEquals(sha256(fixture.bytes)))
            check(first.afterSha256.contentEquals(sha256(first.bytes)))
            val defensiveBytes = first.bytes
            defensiveBytes[0] = (defensiveBytes[0].toInt() xor 1).toByte()
            check(first.bytes.contentEquals(second.bytes)) { "$name result bytes are not defensive" }
            check(first.semanticDiff.changes.size == 1)
            val change = first.semanticDiff.changes.single()
            check(change.elementPath == "/manifest/application")
            check(change.namespaceUri == ANDROID_NS)
            check(change.attributeName == "appComponentFactory")
            check(change.beforeValue == fixture.factoryClass)
            check(change.afterValue == ManifestTransformRequest.SHELL_FACTORY)
            check(fixture.bytes.contentEquals(fixture.originalCopy)) { "$name input mutated" }
            transformRows += jsonObject(
                "fixture" to name,
                "before_sha256" to hex(first.beforeSha256),
                "after_sha256" to hex(first.afterSha256),
                "diff_sha256" to hex(sha256(diffJson(change).toByteArray(StandardCharsets.UTF_8))),
                "encoding" to if (fixture.utf8) "UTF-8" else "UTF-16",
            )
        }

        val negativeRows = runNegativeMatrix(fixtures.getValue("utf8-existing-factory"))
        val fuzzSamples = System.getProperty("ah.axml.fuzzSamples").toInt()
        val fuzzSummary = runFuzz(fixtures.getValue("utf8-existing-factory"), fuzzSamples)
        Files.writeString(reportDir.resolve("transform-matrix.json"), "[${transformRows.joinToString(",")}]\n")
        Files.writeString(reportDir.resolve("error-matrix.json"), "[${negativeRows.joinToString(",")}]\n")
        Files.writeString(reportDir.resolve("fuzz-summary.json"), fuzzSummary + "\n")
        runAapt2CrossCheck(reportDir, fixtures)
        println("M1-03 AXML self-test PASS fixtures=${fixtures.size} negatives=${negativeRows.size} fuzz=$fuzzSamples")
    }

    private fun runNegativeMatrix(base: Fixture): List<String> {
        val cases = linkedMapOf<String, Pair<ByteArray, AxmlErrorCode>>(
            "truncated" to (base.bytes.copyOf(base.bytes.size - 1) to AxmlErrorCode.AXML_MALFORMED),
            "oversized-root" to (base.bytes.copyOf().also { putU4(it, 4, Int.MAX_VALUE) } to AxmlErrorCode.AXML_MALFORMED),
            "unsupported-string-flags" to (base.bytes.copyOf().also { putU4(it, 8 + 16, 0x200) } to AxmlErrorCode.AXML_UNSUPPORTED_ENCODING),
            "string-count-limit" to (base.bytes.copyOf().also { putU4(it, 8 + 8, 1_000_001) } to AxmlErrorCode.AXML_LIMIT_EXCEEDED),
            "string-length-truncated" to (
                base.bytes.copyOf().also {
                    val stringsStart = 8 + readU4(it, 8 + 20)
                    it[stringsStart] = 0xff.toByte()
                    it[stringsStart + 1] = 0xff.toByte()
                } to AxmlErrorCode.AXML_MALFORMED
                ),
            "resource-map-truncated" to (
                base.bytes.copyOf().also {
                    val resourceMapStart = 8 + readU4(it, 8 + 4)
                    putU4(it, resourceMapStart + 4, Int.MAX_VALUE)
                } to AxmlErrorCode.AXML_MALFORMED
                ),
            "duplicate-application" to (SyntheticManifest.build(duplicateApplication = true).bytes to AxmlErrorCode.AXML_MALFORMED),
            "nesting-limit" to (SyntheticManifest.build(nestedDepth = 1_025).bytes to AxmlErrorCode.AXML_LIMIT_EXCEEDED),
            "missing-application" to (SyntheticManifest.build(includeApplication = false).bytes to AxmlErrorCode.AXML_APPLICATION_MISSING),
            "shell-collision" to (
                SyntheticManifest.build(factoryClass = ManifestTransformRequest.SHELL_FACTORY).bytes to
                    AxmlErrorCode.AXML_RESERVED_COLLISION
                ),
            "resource-id-collision" to (
                SyntheticManifest.build(factoryResourceId = 0x0101_0003, reserveFactoryKey = true).bytes to AxmlErrorCode.AXML_RESERVED_COLLISION
                ),
        )
        val rows = ArrayList<String>()
        for ((name, pair) in cases) {
            val fixture = if (name == "shell-collision") {
                SyntheticManifest.build(factoryClass = ManifestTransformRequest.SHELL_FACTORY)
            } else if (name == "resource-id-collision") {
                SyntheticManifest.build(factoryResourceId = 0x0101_0003, reserveFactoryKey = true)
            } else {
                base.copy(bytes = pair.first, originalCopy = pair.first.copyOf())
            }
            expectCode(name, pair.second) { BinaryManifestTransformer.transform(pair.first, requestFor(fixture)) }
            rows += jsonObject("case" to name, "code" to pair.second.name)
        }
        val mismatchSummary = ManifestSummary(
            "ah.fixtures.wrong",
            sha256("ah.fixtures.wrong".toByteArray(StandardCharsets.UTF_8)),
            29,
            36,
            base.applicationClass,
            base.normalizedFactoryClass,
            null,
        )
        expectCode("summary-mismatch", AxmlErrorCode.AXML_DIFF_VIOLATION) {
            BinaryManifestTransformer.transform(base.bytes, ManifestTransformRequest(mismatchSummary))
        }
        rows += jsonObject("case" to "summary-mismatch", "code" to AxmlErrorCode.AXML_DIFF_VIOLATION.name)
        val oversized = ByteArray(16 * 1024 * 1024 + 1)
        expectCode("manifest-limit", AxmlErrorCode.AXML_LIMIT_EXCEEDED) {
            BinaryManifestTransformer.transform(oversized, requestFor(base))
        }
        rows += jsonObject("case" to "manifest-limit", "code" to AxmlErrorCode.AXML_LIMIT_EXCEEDED.name)
        return rows
    }

    private fun runFuzz(base: Fixture, samples: Int): String {
        require(samples >= 5_000)
        val random = Random(FUZZ_SEED)
        var accepted = 0
        val rejected = linkedMapOf<AxmlErrorCode, Int>()
        repeat(samples) { index ->
            val candidate = when (index % 4) {
                0 -> base.bytes.copyOf(random.nextInt(base.bytes.size + 1))
                1 -> base.bytes.copyOf().also { bytes ->
                    repeat(1 + random.nextInt(8)) {
                        val offset = random.nextInt(bytes.size)
                        bytes[offset] = (bytes[offset].toInt() xor (1 shl random.nextInt(8))).toByte()
                    }
                }
                2 -> base.bytes.copyOf(base.bytes.size + random.nextInt(32))
                else -> base.bytes.copyOf().also { bytes ->
                    val offset = random.nextInt(bytes.size - 3)
                    putU4(bytes, offset, random.nextInt())
                }
            }
            try {
                BinaryManifestTransformer.transform(candidate, requestFor(base))
                accepted++
            } catch (expected: AxmlTransformException) {
                rejected[expected.code] = (rejected[expected.code] ?: 0) + 1
            }
        }
        val counts = rejected.entries.sortedBy { it.key.name }.joinToString(",") {
            "\"${it.key.name}\":${it.value}"
        }
        return "{\"seed\":$FUZZ_SEED,\"samples\":$samples,\"accepted\":$accepted,\"rejected\":{$counts}}"
    }

    private fun runAapt2CrossCheck(reportDir: Path, fixtures: Map<String, Fixture>) {
        val executable = System.getProperty("ah.axml.aapt2") ?: return
        val rows = ArrayList<String>()
        for ((name, fixture) in fixtures) {
            val result = BinaryManifestTransformer.transform(fixture.bytes, requestFor(fixture))
            val apk = reportDir.resolve("$name.apk")
            ZipOutputStream(Files.newOutputStream(apk)).use { zip ->
                val entry = ZipEntry("AndroidManifest.xml")
                entry.time = 0L
                zip.putNextEntry(entry)
                zip.write(result.bytes)
                zip.closeEntry()
            }
            val process = ProcessBuilder(executable, "dump", "xmltree", apk.toString(), "--file", "AndroidManifest.xml")
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader(StandardCharsets.UTF_8).readText()
            val exitCode = process.waitFor()
            check(exitCode == 0) { "aapt2 rejected $name: $output" }
            check(output.contains(ManifestTransformRequest.SHELL_FACTORY)) { "aapt2 did not expose shell factory for $name" }
            rows += jsonObject("fixture" to name, "manifest_sha256" to hex(sha256(result.bytes)), "exit_code" to "0")
        }
        val androidJar = System.getProperty("ah.axml.androidJar")
        if (androidJar != null) rows += runCompiledAapt2Fixture(reportDir, executable, androidJar)
        Files.writeString(reportDir.resolve("aapt2-cross-check.json"), "[${rows.joinToString(",")}]\n")
    }

    private fun runCompiledAapt2Fixture(reportDir: Path, executable: String, androidJar: String): String {
        val source = reportDir.resolve("aapt2-source-manifest.xml")
        val linkedApk = reportDir.resolve("aapt2-linked-input.apk")
        Files.writeString(
            source,
            """<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="ah.fixtures.aapt2"><uses-sdk android:minSdkVersion="29" android:targetSdkVersion="36"/><application android:name=".AaptApplication" android:appComponentFactory=".OriginalFactory"><meta-data android:name="fixture.metadata" android:value="kept"/></application></manifest>""",
        )
        val link = ProcessBuilder(
            executable,
            "link",
            "-o",
            linkedApk.toString(),
            "--manifest",
            source.toString(),
            "-I",
            androidJar,
        ).redirectErrorStream(true).start()
        val linkOutput = link.inputStream.bufferedReader(StandardCharsets.UTF_8).readText()
        check(link.waitFor() == 0) { "aapt2 link failed: $linkOutput" }
        val manifestBytes = ZipFile(linkedApk.toFile()).use { zip ->
            zip.getInputStream(zip.getEntry("AndroidManifest.xml")).readBytes()
        }
        val summary = ManifestSummary(
            "ah.fixtures.aapt2",
            sha256("ah.fixtures.aapt2".toByteArray(StandardCharsets.UTF_8)),
            29,
            36,
            "ah.fixtures.aapt2.AaptApplication",
            "ah.fixtures.aapt2.OriginalFactory",
            null,
        )
        val transformed = BinaryManifestTransformer.transform(manifestBytes, ManifestTransformRequest(summary))
        val outputApk = reportDir.resolve("aapt2-linked-transformed.apk")
        ZipOutputStream(Files.newOutputStream(outputApk)).use { zip ->
            val entry = ZipEntry("AndroidManifest.xml")
            entry.time = 0L
            zip.putNextEntry(entry)
            zip.write(transformed.bytes)
            zip.closeEntry()
        }
        val dump = ProcessBuilder(executable, "dump", "xmltree", outputApk.toString(), "--file", "AndroidManifest.xml")
            .redirectErrorStream(true)
            .start()
        val dumpOutput = dump.inputStream.bufferedReader(StandardCharsets.UTF_8).readText()
        check(dump.waitFor() == 0) { "aapt2 rejected compiled fixture: $dumpOutput" }
        check(dumpOutput.contains(ManifestTransformRequest.SHELL_FACTORY))
        check(dumpOutput.contains(".AaptApplication"))
        check(dumpOutput.contains("fixture.metadata"))
        check(dumpOutput.contains("kept"))
        return jsonObject(
            "fixture" to "aapt2-compiled",
            "before_sha256" to hex(sha256(manifestBytes)),
            "after_sha256" to hex(sha256(transformed.bytes)),
            "exit_code" to "0",
        )
    }

    private fun requestFor(fixture: Fixture): ManifestTransformRequest = ManifestTransformRequest(
        ManifestSummary(
            fixture.packageName,
            sha256(fixture.packageName.toByteArray(StandardCharsets.UTF_8)),
            fixture.minSdk,
            fixture.targetSdk,
            fixture.normalizedApplicationClass,
            fixture.normalizedFactoryClass,
            null,
        ),
    )

    private fun expectCode(label: String, code: AxmlErrorCode, block: () -> Unit) {
        try {
            block()
            error("expected $code case=$label")
        } catch (expected: AxmlTransformException) {
            check(expected.code == code) { "expected $code but got ${expected.code} case=$label" }
            check(expected.cause == null)
            check(!expected.message.orEmpty().contains("ah.fixtures"))
        }
    }

    private fun diffJson(change: ManifestAttributeChange): String = jsonObject(
        "path" to change.elementPath,
        "namespace" to change.namespaceUri,
        "name" to change.attributeName,
        "before" to (change.beforeValue ?: "null"),
        "after" to change.afterValue,
    )

    private fun jsonObject(vararg pairs: Pair<String, String>): String = pairs.joinToString(",", "{", "}") {
        "\"${escape(it.first)}\":\"${escape(it.second)}\""
    }

    private fun escape(value: String): String = buildString {
        for (character in value) when (character) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(character)
        }
    }

    private fun sha256(value: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(value)
    private fun hex(value: ByteArray): String = value.joinToString("") { "%02x".format(it) }
    private fun putU4(bytes: ByteArray, offset: Int, value: Int) {
        repeat(4) { shift -> bytes[offset + shift] = (value ushr (shift * 8)).toByte() }
    }

    private fun readU4(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or
            ((bytes[offset + 1].toInt() and 0xff) shl 8) or
            ((bytes[offset + 2].toInt() and 0xff) shl 16) or
            ((bytes[offset + 3].toInt() and 0xff) shl 24)

    private const val FUZZ_SEED = 0x4d31_3033L
    private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
}

private data class Fixture(
    val bytes: ByteArray,
    val originalCopy: ByteArray,
    val utf8: Boolean,
    val packageName: String,
    val minSdk: Int,
    val targetSdk: Int,
    val applicationClass: String?,
    val factoryClass: String?,
) {
    val normalizedApplicationClass: String? get() = normalize(applicationClass)
    val normalizedFactoryClass: String? get() = normalize(factoryClass)

    private fun normalize(value: String?): String? = when {
        value == null -> null
        value.startsWith('.') -> packageName + value
        '.' !in value -> "$packageName.$value"
        else -> value
    }
}

private object SyntheticManifest {
    fun build(
        utf8: Boolean = true,
        packageName: String = "ah.fixtures.axml",
        minSdk: Int = 29,
        targetSdk: Int = 36,
        applicationClass: String? = ".FixtureApplication",
        factoryClass: String? = null,
        metadata: Boolean = false,
        resourceReference: Boolean = false,
        unknownChunk: Boolean = false,
        duplicateApplication: Boolean = false,
        includeApplication: Boolean = true,
        factoryResourceId: Int = ANDROID_ATTR_APP_COMPONENT_FACTORY,
        nestedDepth: Int = 0,
        includeResourceMap: Boolean = true,
        reserveFactoryKey: Boolean = false,
    ): Fixture {
        val strings = StringTable(utf8)
        val manifestName = strings.add("manifest")
        val packageNameAttribute = strings.add("package")
        val usesSdkName = strings.add("uses-sdk")
        val minSdkName = strings.add("minSdkVersion")
        val targetSdkName = strings.add("targetSdkVersion")
        val applicationName = strings.add("application")
        val androidName = strings.add("name")
        val factoryName = if (factoryClass != null || reserveFactoryKey) strings.add("appComponentFactory") else null
        val metadataName = strings.add("meta-data")
        val nestedName = strings.add("nested")
        val valueName = strings.add("value")
        val androidUri = strings.add(ANDROID_NS)
        val androidPrefix = strings.add("android")
        val packageValue = strings.add(packageName)
        val applicationValue = applicationClass?.let(strings::add)
        val factoryValue = factoryClass?.let(strings::add)
        val metadataKey = if (metadata) strings.add("fixture.metadata") else null
        val metadataValue = if (metadata && !resourceReference) strings.add("kept") else null

        val body = Writer()
        body.bytes(strings.encode())
        if (includeResourceMap) {
            val mappings = linkedMapOf(
                minSdkName to ANDROID_ATTR_MIN_SDK,
                targetSdkName to ANDROID_ATTR_TARGET_SDK,
                androidName to ANDROID_ATTR_NAME,
                valueName to ANDROID_ATTR_VALUE,
            )
            if (factoryName != null) mappings[factoryName] = factoryResourceId
            body.bytes(resourceMap(strings.size, mappings))
        }
        body.bytes(namespace(TYPE_START_NAMESPACE, androidPrefix, androidUri))
        body.bytes(
            startElement(
                manifestName,
                listOf(Attribute(NO_INDEX, packageNameAttribute, packageValue, TYPE_STRING, packageValue)),
            ),
        )
        body.bytes(
            startElement(
                usesSdkName,
                listOf(
                    Attribute(androidUri, minSdkName, NO_INDEX, TYPE_INT_DEC, minSdk),
                    Attribute(androidUri, targetSdkName, NO_INDEX, TYPE_INT_DEC, targetSdk),
                ),
            ),
        )
        body.bytes(endElement(usesSdkName))
        if (unknownChunk) body.bytes(unknownChunk())
        if (includeApplication) {
            val count = if (duplicateApplication) 2 else 1
            repeat(count) {
                val attributes = ArrayList<Attribute>()
                if (applicationValue != null) attributes += Attribute(androidUri, androidName, applicationValue, TYPE_STRING, applicationValue)
                if (factoryValue != null) attributes += Attribute(
                    androidUri,
                    checkNotNull(factoryName),
                    factoryValue,
                    TYPE_STRING,
                    factoryValue,
                )
                body.bytes(startElement(applicationName, attributes))
                if (metadata && metadataKey != null) {
                    val valueAttribute = if (resourceReference) {
                        Attribute(androidUri, valueName, NO_INDEX, TYPE_REFERENCE, 0x7f01_0001)
                    } else {
                        Attribute(androidUri, valueName, metadataValue!!, TYPE_STRING, metadataValue)
                    }
                    body.bytes(
                        startElement(
                            metadataName,
                            listOf(
                                Attribute(androidUri, androidName, metadataKey, TYPE_STRING, metadataKey),
                                valueAttribute,
                            ),
                        ),
                    )
                    body.bytes(endElement(metadataName))
                }
                repeat(nestedDepth) { body.bytes(startElement(nestedName, emptyList())) }
                repeat(nestedDepth) { body.bytes(endElement(nestedName)) }
                body.bytes(endElement(applicationName))
            }
        }
        body.bytes(endElement(manifestName))
        body.bytes(namespace(TYPE_END_NAMESPACE, androidPrefix, androidUri))
        val bodyBytes = body.toByteArray()
        val bytes = Writer().apply {
            u2(TYPE_XML)
            u2(8)
            u4(8 + bodyBytes.size)
            bytes(bodyBytes)
        }.toByteArray()
        return Fixture(
            bytes = bytes,
            originalCopy = bytes.copyOf(),
            utf8 = utf8,
            packageName = packageName,
            minSdk = minSdk,
            targetSdk = targetSdk,
            applicationClass = applicationClass,
            factoryClass = factoryClass,
        )
    }

    private fun resourceMap(count: Int, mappings: Map<Int, Int>): ByteArray = Writer().apply {
        u2(TYPE_RESOURCE_MAP)
        u2(8)
        u4(8 + count * 4)
        repeat(count) { u4(mappings[it] ?: 0) }
    }.toByteArray()

    private fun namespace(type: Int, prefix: Int, uri: Int): ByteArray = Writer().apply {
        u2(type)
        u2(16)
        u4(24)
        u4(1)
        u4(NO_INDEX)
        u4(prefix)
        u4(uri)
    }.toByteArray()

    private fun startElement(name: Int, attributes: List<Attribute>): ByteArray = Writer().apply {
        u2(TYPE_START_ELEMENT)
        u2(16)
        u4(36 + attributes.size * 20)
        u4(1)
        u4(NO_INDEX)
        u4(NO_INDEX)
        u4(name)
        u2(20)
        u2(20)
        u2(attributes.size)
        u2(0)
        u2(0)
        u2(0)
        for (attribute in attributes) {
            u4(attribute.namespace)
            u4(attribute.name)
            u4(attribute.rawValue)
            u2(8)
            u1(0)
            u1(attribute.type)
            u4(attribute.data)
        }
    }.toByteArray()

    private fun endElement(name: Int): ByteArray = Writer().apply {
        u2(TYPE_END_ELEMENT)
        u2(16)
        u4(24)
        u4(1)
        u4(NO_INDEX)
        u4(NO_INDEX)
        u4(name)
    }.toByteArray()

    private fun unknownChunk(): ByteArray = Writer().apply {
        u2(0x017f)
        u2(16)
        u4(20)
        u4(1)
        u4(NO_INDEX)
        u4(0x1357_2468)
    }.toByteArray()

    private data class Attribute(val namespace: Int, val name: Int, val rawValue: Int, val type: Int, val data: Int)

    private class StringTable(private val utf8: Boolean) {
        private val indices = LinkedHashMap<String, Int>()
        fun add(value: String): Int = indices.getOrPut(value) { indices.size }
        val size: Int get() = indices.size

        fun encode(): ByteArray {
            val offsets = ArrayList<Int>()
            val data = Writer()
            for (value in indices.keys) {
                offsets += data.size()
                if (utf8) {
                    val encoded = value.toByteArray(StandardCharsets.UTF_8)
                    data.length8(value.length)
                    data.length8(encoded.size)
                    data.bytes(encoded)
                    data.u1(0)
                } else {
                    val encoded = value.toByteArray(StandardCharsets.UTF_16LE)
                    data.length16(value.length)
                    data.bytes(encoded)
                    data.u2(0)
                }
            }
            while (data.size() % 4 != 0) data.u1(0)
            val dataBytes = data.toByteArray()
            val stringsStart = 28 + offsets.size * 4
            return Writer().apply {
                u2(TYPE_STRING_POOL)
                u2(28)
                u4(stringsStart + dataBytes.size)
                u4(offsets.size)
                u4(0)
                u4(if (utf8) 0x100 else 0)
                u4(stringsStart)
                u4(0)
                offsets.forEach(::u4)
                bytes(dataBytes)
            }.toByteArray()
        }
    }

    private class Writer {
        private val output = ByteArrayOutputStream()
        fun size(): Int = output.size()
        fun u1(value: Int) = output.write(value and 0xff)
        fun u2(value: Int) {
            u1(value)
            u1(value ushr 8)
        }
        fun u4(value: Int) {
            repeat(4) { u1(value ushr (it * 8)) }
        }
        fun bytes(value: ByteArray) = output.write(value)
        fun length8(value: Int) {
            if (value <= 0x7f) u1(value) else {
                u1((value ushr 8) or 0x80)
                u1(value)
            }
        }
        fun length16(value: Int) {
            if (value <= 0x7fff) u2(value) else {
                u2((value ushr 16) or 0x8000)
                u2(value)
            }
        }
        fun toByteArray(): ByteArray = output.toByteArray()
    }

    private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    private const val NO_INDEX = -1
    private const val TYPE_XML = 0x0003
    private const val TYPE_STRING_POOL = 0x0001
    private const val TYPE_RESOURCE_MAP = 0x0180
    private const val TYPE_START_NAMESPACE = 0x0100
    private const val TYPE_END_NAMESPACE = 0x0101
    private const val TYPE_START_ELEMENT = 0x0102
    private const val TYPE_END_ELEMENT = 0x0103
    private const val TYPE_STRING = 0x03
    private const val TYPE_REFERENCE = 0x01
    private const val TYPE_INT_DEC = 0x10
    private const val ANDROID_ATTR_NAME = 0x0101_0003
    private const val ANDROID_ATTR_VALUE = 0x0101_0024
    private const val ANDROID_ATTR_MIN_SDK = 0x0101_020c
    private const val ANDROID_ATTR_TARGET_SDK = 0x0101_0270
    private const val ANDROID_ATTR_APP_COMPONENT_FACTORY = 0x0101_057a
}
