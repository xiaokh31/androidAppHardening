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
            "utf8-extended-factory" to SyntheticManifest.build(
                utf8 = true,
                applicationClass = ".FixtureApplication",
                factoryClass = ".FixtureFactory",
                applicationAttributeExtension = byteArrayOf(0x6a, 0x7b, 0x8c.toByte(), 0x9d.toByte()),
            ),
            "utf16-resource-reference" to SyntheticManifest.build(
                utf8 = false,
                applicationClass = "ah.fixtures.axml.FixtureApplication",
                factoryClass = "FixtureFactory",
                metadata = true,
                resourceReference = true,
                valueResourceId = HIGH_RESOURCE_MAP_ID,
                resourceReferenceId = HIGH_TYPED_REFERENCE,
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
            val preservation = preservationEvidence(fixture.bytes, first.bytes)
            fixture.factoryAttributeExtension?.let { extension ->
                check(countSequence(fixture.bytes, extension) == countSequence(first.bytes, extension)) {
                    "$name factory attribute extension changed"
                }
            }
            transformRows += jsonObject(
                "fixture" to name,
                "before_sha256" to hex(first.beforeSha256),
                "after_sha256" to hex(first.afterSha256),
                "diff_sha256" to hex(sha256(diffJson(change).toByteArray(StandardCharsets.UTF_8))),
                "encoding" to if (fixture.utf8) "UTF-8" else "UTF-16",
                "old_string_count" to preservation.oldStringCount.toString(),
                "old_string_index_sha256" to preservation.oldStringIndexSha256,
                "resource_map_prefix_count" to preservation.resourceMapPrefixCount.toString(),
                "resource_map_prefix_sha256" to preservation.resourceMapPrefixSha256,
                "unknown_chunk_count" to preservation.unknownChunkCount.toString(),
                "unknown_chunk_sequence_sha256" to preservation.unknownChunkSequenceSha256,
                "high_bit_typed_value_count" to preservation.highBitTypedValueCount.toString(),
                "high_bit_typed_value_sha256" to preservation.highBitTypedValueSha256,
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
            "string-count-limit" to (base.bytes.copyOf().also { putU4(it, 8 + 8, 262_145) } to AxmlErrorCode.AXML_LIMIT_EXCEEDED),
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
            "long-name-nesting-limit" to (
                SyntheticManifest.build(nestedDepth = 1_025, nestedElementName = "n".repeat(0x7fff)).bytes to
                    AxmlErrorCode.AXML_LIMIT_EXCEEDED
                ),
            "namespace-budget" to (SyntheticManifest.build(namespaceCount = 1_025).bytes to AxmlErrorCode.AXML_LIMIT_EXCEEDED),
            "chunk-budget" to (SyntheticManifest.build(unknownChunkCount = 16_385).bytes to AxmlErrorCode.AXML_LIMIT_EXCEEDED),
            "attribute-budget" to (withFirstStartElementAttributeCount(base.bytes, 16_385) to AxmlErrorCode.AXML_LIMIT_EXCEEDED),
            "style-work-budget" to (amplifiedStyleFixture(base.bytes) to AxmlErrorCode.AXML_LIMIT_EXCEEDED),
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

    private fun preservationEvidence(beforeBytes: ByteArray, afterBytes: ByteArray): PreservationEvidence {
        val before = readEvidence(beforeBytes)
        val after = readEvidence(afterBytes)
        check(after.strings.size >= before.strings.size)
        check(after.strings.take(before.strings.size) == before.strings) { "old string indexes changed" }
        check(after.resourceIds.size >= before.resourceIds.size)
        check(after.resourceIds.take(before.resourceIds.size) == before.resourceIds) { "resource-map prefix changed" }
        check(after.unknownChunks == before.unknownChunks) { "unknown chunk bytes or order changed" }
        check(after.highBitTypedValues == before.highBitTypedValues) { "high-bit typed value changed" }
        return PreservationEvidence(
            oldStringCount = before.strings.size,
            oldStringIndexSha256 = indexedStringsSha256(before.strings),
            resourceMapPrefixCount = before.resourceIds.size,
            resourceMapPrefixSha256 = resourceIdsSha256(before.resourceIds),
            unknownChunkCount = before.unknownChunks.size,
            unknownChunkSequenceSha256 = unknownChunksSha256(before.unknownChunks),
            highBitTypedValueCount = before.highBitTypedValues.size,
            highBitTypedValueSha256 = resourceIdsSha256(before.highBitTypedValues),
        )
    }

    private fun readEvidence(bytes: ByteArray): EvidenceDocument {
        val strings = ArrayList<String>()
        val resourceIds = ArrayList<Long>()
        val unknown = ArrayList<String>()
        val highTyped = ArrayList<Long>()
        var offset = 8
        while (offset < bytes.size) {
            val type = readU2(bytes, offset)
            val headerSize = readU2(bytes, offset + 2)
            val size = readU4(bytes, offset + 4)
            when (type) {
                TYPE_STRING_POOL -> {
                    val count = readU4(bytes, offset + 8)
                    val utf8 = readU4(bytes, offset + 16) and 0x100 != 0
                    val stringsStart = readU4(bytes, offset + 20)
                    repeat(count) { index ->
                        var cursor = offset + stringsStart + readU4(bytes, offset + headerSize + index * 4)
                        if (utf8) {
                            cursor = skipLength8(bytes, cursor)
                            val byteLength = readLength8Value(bytes, cursor)
                            cursor = byteLength.second
                            strings += bytes.copyOfRange(cursor, cursor + byteLength.first).toString(StandardCharsets.UTF_8)
                        } else {
                            val length = readLength16Value(bytes, cursor)
                            cursor = length.second
                            strings += bytes.copyOfRange(cursor, cursor + length.first * 2).toString(StandardCharsets.UTF_16LE)
                        }
                    }
                }
                TYPE_RESOURCE_MAP -> repeat((size - headerSize) / 4) { index ->
                    resourceIds += readU4Long(bytes, offset + headerSize + index * 4)
                }
                TYPE_START_ELEMENT -> {
                    val attributeStart = readU2(bytes, offset + 24)
                    val attributeSize = readU2(bytes, offset + 26)
                    val count = readU2(bytes, offset + 28)
                    val attributes = offset + 16 + attributeStart
                    repeat(count) { index ->
                        val data = readU4Long(bytes, attributes + index * attributeSize + 16)
                        if (data >= 0x8000_0000L) highTyped += data
                    }
                }
                TYPE_START_NAMESPACE, TYPE_END_NAMESPACE, TYPE_END_ELEMENT, TYPE_CDATA -> Unit
                else -> unknown += "$type:${hex(sha256(bytes.copyOfRange(offset, offset + size)))}"
            }
            offset += size
        }
        return EvidenceDocument(strings, resourceIds, unknown, highTyped)
    }

    private fun indexedStringsSha256(strings: List<String>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        strings.forEachIndexed { index, value ->
            val encoded = value.toByteArray(StandardCharsets.UTF_8)
            updateInt(digest, index)
            updateInt(digest, encoded.size)
            digest.update(encoded)
        }
        return hex(digest.digest())
    }

    private fun resourceIdsSha256(values: List<Long>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        values.forEach { value -> updateInt(digest, value.toInt()) }
        return hex(digest.digest())
    }

    private fun unknownChunksSha256(values: List<String>): String =
        hex(sha256(values.joinToString("\n").toByteArray(StandardCharsets.UTF_8)))

    private fun updateInt(digest: MessageDigest, value: Int) {
        repeat(4) { shift -> digest.update((value ushr (24 - shift * 8)).toByte()) }
    }

    private fun countSequence(bytes: ByteArray, sequence: ByteArray): Int {
        var count = 0
        for (offset in 0..bytes.size - sequence.size) {
            if (sequence.indices.all { bytes[offset + it] == sequence[it] }) count++
        }
        return count
    }

    private fun amplifiedStyleFixture(base: ByteArray): ByteArray {
        val chunkStart = 8
        check(readU2(base, chunkStart) == TYPE_STRING_POOL)
        val oldSize = readU4(base, chunkStart + 4)
        val headerSize = readU2(base, chunkStart + 2)
        val stringCount = readU4(base, chunkStart + 8)
        val oldStringsStart = readU4(base, chunkStart + 20)
        val stringData = base.copyOfRange(chunkStart + oldStringsStart, chunkStart + oldSize)
        val styleCount = 2_048
        val stringsStart = headerSize + (stringCount + styleCount) * 4
        val stylesStart = stringsStart + stringData.size
        val styles = ByteArray(styleCount * 12 + 12) { 0xff.toByte() }
        repeat(styleCount) { index ->
            putU4(styles, index * 12, 0)
            putU4(styles, index * 12 + 4, 0)
            putU4(styles, index * 12 + 8, 0)
        }
        val newChunk = ByteArray(stylesStart + styles.size)
        base.copyInto(newChunk, 0, chunkStart, chunkStart + headerSize)
        putU4(newChunk, 4, newChunk.size)
        putU4(newChunk, 12, styleCount)
        putU4(newChunk, 20, stringsStart)
        putU4(newChunk, 24, stylesStart)
        repeat(stringCount) { index -> putU4(newChunk, headerSize + index * 4, readU4(base, chunkStart + headerSize + index * 4)) }
        repeat(styleCount) { index -> putU4(newChunk, headerSize + (stringCount + index) * 4, index * 12) }
        stringData.copyInto(newChunk, stringsStart)
        styles.copyInto(newChunk, stylesStart)
        val result = ByteArray(base.size - oldSize + newChunk.size)
        base.copyInto(result, 0, 0, chunkStart)
        newChunk.copyInto(result, chunkStart)
        base.copyInto(result, chunkStart + newChunk.size, chunkStart + oldSize, base.size)
        putU4(result, 4, result.size)
        return result
    }

    private fun withFirstStartElementAttributeCount(base: ByteArray, count: Int): ByteArray {
        val result = base.copyOf()
        var offset = 8
        while (offset < result.size) {
            val type = readU2(result, offset)
            val size = readU4(result, offset + 4)
            if (type == TYPE_START_ELEMENT) {
                result[offset + 28] = count.toByte()
                result[offset + 29] = (count ushr 8).toByte()
                return result
            }
            offset += size
        }
        error("start element not found")
    }

    private fun skipLength8(bytes: ByteArray, offset: Int): Int = if (bytes[offset].toInt() and 0x80 == 0) offset + 1 else offset + 2
    private fun readLength8Value(bytes: ByteArray, offset: Int): Pair<Int, Int> {
        val first = bytes[offset].toInt() and 0xff
        return if (first and 0x80 == 0) first to offset + 1 else (((first and 0x7f) shl 8) or
            (bytes[offset + 1].toInt() and 0xff)) to offset + 2
    }

    private fun readLength16Value(bytes: ByteArray, offset: Int): Pair<Int, Int> {
        val first = readU2(bytes, offset)
        return if (first and 0x8000 == 0) first to offset + 2 else
            (((first and 0x7fff) shl 16) or readU2(bytes, offset + 2)) to offset + 4
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

    private fun readU2(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8)

    private fun readU4Long(bytes: ByteArray, offset: Int): Long = readU4(bytes, offset).toLong() and 0xffff_ffffL

    private const val FUZZ_SEED = 0x4d31_3033L
    private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    private const val TYPE_STRING_POOL = 0x0001
    private const val TYPE_RESOURCE_MAP = 0x0180
    private const val TYPE_START_NAMESPACE = 0x0100
    private const val TYPE_END_NAMESPACE = 0x0101
    private const val TYPE_START_ELEMENT = 0x0102
    private const val TYPE_END_ELEMENT = 0x0103
    private const val TYPE_CDATA = 0x0104
    private const val HIGH_RESOURCE_MAP_ID = -0x0edc_ba99
    private const val HIGH_TYPED_REFERENCE = -0x0123_4568
}

private data class EvidenceDocument(
    val strings: List<String>,
    val resourceIds: List<Long>,
    val unknownChunks: List<String>,
    val highBitTypedValues: List<Long>,
)

private data class PreservationEvidence(
    val oldStringCount: Int,
    val oldStringIndexSha256: String,
    val resourceMapPrefixCount: Int,
    val resourceMapPrefixSha256: String,
    val unknownChunkCount: Int,
    val unknownChunkSequenceSha256: String,
    val highBitTypedValueCount: Int,
    val highBitTypedValueSha256: String,
)

private data class Fixture(
    val bytes: ByteArray,
    val originalCopy: ByteArray,
    val utf8: Boolean,
    val packageName: String,
    val minSdk: Int,
    val targetSdk: Int,
    val applicationClass: String?,
    val factoryClass: String?,
    val factoryAttributeExtension: ByteArray?,
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
        valueResourceId: Int = ANDROID_ATTR_VALUE,
        resourceReferenceId: Int = 0x7f01_0001,
        unknownChunk: Boolean = false,
        unknownChunkCount: Int = if (unknownChunk) 1 else 0,
        duplicateApplication: Boolean = false,
        includeApplication: Boolean = true,
        factoryResourceId: Int = ANDROID_ATTR_APP_COMPONENT_FACTORY,
        nestedDepth: Int = 0,
        includeResourceMap: Boolean = true,
        reserveFactoryKey: Boolean = false,
        namespaceCount: Int = 1,
        applicationAttributeExtension: ByteArray = ByteArray(0),
        nestedElementName: String = "nested",
    ): Fixture {
        require(namespaceCount >= 1)
        require(applicationAttributeExtension.size % 4 == 0)
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
        val nestedName = strings.add(nestedElementName)
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
                valueName to valueResourceId,
            )
            if (factoryName != null) mappings[factoryName] = factoryResourceId
            body.bytes(resourceMap(strings.size, mappings))
        }
        repeat(namespaceCount) { body.bytes(namespace(TYPE_START_NAMESPACE, androidPrefix, androidUri)) }
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
        repeat(unknownChunkCount) { body.bytes(unknownChunk()) }
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
                body.bytes(startElement(applicationName, attributes, applicationAttributeExtension))
                if (metadata && metadataKey != null) {
                    val valueAttribute = if (resourceReference) {
                        Attribute(androidUri, valueName, NO_INDEX, TYPE_REFERENCE, resourceReferenceId)
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
        repeat(namespaceCount) { body.bytes(namespace(TYPE_END_NAMESPACE, androidPrefix, androidUri)) }
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
            factoryAttributeExtension = applicationAttributeExtension.takeIf { factoryClass != null && it.isNotEmpty() }?.copyOf(),
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

    private fun startElement(
        name: Int,
        attributes: List<Attribute>,
        attributeExtension: ByteArray = ByteArray(0),
    ): ByteArray = Writer().apply {
        val attributeSize = 20 + attributeExtension.size
        u2(TYPE_START_ELEMENT)
        u2(16)
        u4(36 + attributes.size * attributeSize)
        u4(1)
        u4(NO_INDEX)
        u4(NO_INDEX)
        u4(name)
        u2(20)
        u2(attributeSize)
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
            bytes(attributeExtension)
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
