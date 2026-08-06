package ah.host.cli

import ah.host.container.RuntimeAbi
import ah.host.repacker.RuntimeBundle
import ah.host.repacker.RuntimeTemplate
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

internal object TestSupport {
    fun runtimeBundle(): RuntimeBundle {
        val templates = RuntimeAbi.entries.associateWith { abi ->
            val bytes = elfTemplate(abi)
            RuntimeTemplate(abi, bytes, sha256(bytes))
        }
        return RuntimeBundle(dex(99), templates)
    }

    fun parseJson(path: Path): MutableMap<String, Any?> =
        parseJson(Files.readString(path, StandardCharsets.UTF_8))

    @Suppress("UNCHECKED_CAST")
    fun parseJson(value: String): MutableMap<String, Any?> = JsonParser(value).parse() as MutableMap<String, Any?>

    fun validateReport(report: Map<String, Any?>) {
        ReportSchemaValidator(reportSchema).validate(report)
        val required = listOf(
            "schema_version", "tool", "result", "input", "output", "application", "signing",
            "dex", "abi", "compatibility", "stages", "size", "errors",
        )
        check(report.keys.toList() == required)
        check(report["schema_version"] == 1L)
        val tool = report.objectValue("tool")
        check(tool["name"] == TOOL_NAME && tool["version"] == TOOL_VERSION)
        val result = report.objectValue("result")
        check(result["status"] in setOf("success", "rejected", "failed"))
        val signing = report.objectValue("signing")
        check(signing["required"] == true && signing["performed"] == false)
        val stages = report.arrayValue("stages").map { it as Map<*, *> }
        val ids = stages.map { it["id"] as String }
        val canonical = PipelineStage.entries.map(PipelineStage::wireName)
        check(ids == canonical.take(ids.size)) { "stage order is not canonical: $ids" }
        stages.forEach { stage ->
            check(stage["status"] in setOf("success", "failed"))
            check((stage["duration_ms"] as Long) >= 0)
        }
        val errors = report.arrayValue("errors")
        check(errors.size <= 1)
        report.objectValue("input").nullableHash("sha256")
        val output = report.objectValue("output")
        listOf("sha256", "manifest_sha256", "container_sha256", "config_sha256").forEach { output.nullableHash(it) }
        listOf("path_token").forEach { report.objectValue("input").hash(it) }
        listOf("path_token", "report_path_token").forEach { output.hash(it) }
        check(signing["current_certificate_sha256"] == null || HASH.matches(signing["current_certificate_sha256"] as String))
    }

    @Suppress("UNCHECKED_CAST")
    fun normalizedReport(path: Path): String {
        val report = parseJson(path)
        val result = report.objectValue("result")
        result["started_at"] = "<time>"
        result["finished_at"] = "<time>"
        report.objectValue("input")["sha256"] = "<fixture>"
        val output = report.objectValue("output")
        output["sha256"] = "<random>"
        output["container_sha256"] = "<random>"
        output["config_sha256"] = "<random>"
        report.arrayValue("stages").forEach { value -> (value as MutableMap<String, Any?>)["duration_ms"] = 0L }
        return JsonEncoder.encode(report) + "\n"
    }

    fun sha256(path: Path): String = Files.newInputStream(path).use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
        digest.digest().toHex()
    }

    fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

    fun ByteArray.toHex(): String = joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.objectValue(name: String): MutableMap<String, Any?> = getValue(name) as MutableMap<String, Any?>

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.arrayValue(name: String): MutableList<Any?> = getValue(name) as MutableList<Any?>

    private fun Map<String, Any?>.hash(name: String) = check(HASH.matches(getValue(name) as String))

    private fun Map<String, Any?>.nullableHash(name: String) {
        val value = getValue(name)
        check(value == null || HASH.matches(value as String))
    }

    private val reportSchema: Map<String, Any?> by lazy {
        parseJson(Files.readString(findRoot().resolve("docs/specs/report-v1.schema.json"), StandardCharsets.UTF_8))
    }

    private fun findRoot(): Path {
        var current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()
        while (!Files.exists(current.resolve("settings.gradle.kts"))) current = requireNotNull(current.parent)
        return current
    }

    private fun dex(seed: Int): ByteArray = ByteArray(160) { index -> (seed + index).toByte() }.also { bytes ->
        "dex\n039\u0000".toByteArray(StandardCharsets.US_ASCII).copyInto(bytes)
    }

    private fun elfTemplate(abi: RuntimeAbi): ByteArray {
        val is64 = abi == RuntimeAbi.ARM64_V8A || abi == RuntimeAbi.X86_64
        val header = if (is64) 64 else 52
        val sectionHeader = if (is64) 64 else 40
        val stringTable = byteArrayOf(0) + ".shstrtab\u0000.ah_share_v1\u0000".toByteArray(StandardCharsets.US_ASCII)
        val stringOffset = header
        val slotOffset = align(stringOffset + stringTable.size, 16)
        val sectionOffset = align(slotOffset + 104, 16)
        val bytes = ByteArray(sectionOffset + sectionHeader * 3)
        bytes[0] = 0x7f
        bytes[1] = 'E'.code.toByte(); bytes[2] = 'L'.code.toByte(); bytes[3] = 'F'.code.toByte()
        bytes[4] = if (is64) 2 else 1; bytes[5] = 1; bytes[6] = 1
        putU2(bytes, 16, 3)
        putU2(bytes, 18, when (abi) {
            RuntimeAbi.ARMEABI_V7A -> 40
            RuntimeAbi.ARM64_V8A -> 183
            RuntimeAbi.X86 -> 3
            RuntimeAbi.X86_64 -> 62
        })
        if (is64) {
            putU8(bytes, 40, sectionOffset.toLong())
            putU2(bytes, 58, sectionHeader); putU2(bytes, 60, 3); putU2(bytes, 62, 1)
        } else {
            putU4(bytes, 32, sectionOffset.toLong())
            putU2(bytes, 46, sectionHeader); putU2(bytes, 48, 3); putU2(bytes, 50, 1)
        }
        stringTable.copyInto(bytes, stringOffset)
        writeSection(bytes, sectionOffset + sectionHeader, is64, 1, 0, stringOffset, stringTable.size)
        writeSection(bytes, sectionOffset + sectionHeader * 2, is64, 11, 2, slotOffset, 104)
        "AHP0".toByteArray().copyInto(bytes, slotOffset)
        putU2(bytes, slotOffset + 4, 1)
        putU2(bytes, slotOffset + 6, abi.abiId)
        return bytes
    }

    private fun writeSection(bytes: ByteArray, offset: Int, is64: Boolean, name: Int, flags: Long, data: Int, size: Int) {
        putU4(bytes, offset, name.toLong())
        if (is64) {
            putU8(bytes, offset + 8, flags); putU8(bytes, offset + 24, data.toLong()); putU8(bytes, offset + 32, size.toLong())
        } else {
            putU4(bytes, offset + 8, flags); putU4(bytes, offset + 16, data.toLong()); putU4(bytes, offset + 20, size.toLong())
        }
    }

    private fun align(value: Int, alignment: Int): Int = (value + alignment - 1) / alignment * alignment
    private fun putU2(bytes: ByteArray, offset: Int, value: Int) = repeat(2) { index -> bytes[offset + index] = (value ushr (8 * index)).toByte() }
    private fun putU4(bytes: ByteArray, offset: Int, value: Long) = repeat(4) { index -> bytes[offset + index] = (value ushr (8 * index)).toByte() }
    private fun putU8(bytes: ByteArray, offset: Int, value: Long) = repeat(8) { index -> bytes[offset + index] = (value ushr (8 * index)).toByte() }

    private val HASH = Regex("[0-9a-f]{64}")
}

private class JsonParser(private val source: String) {
    private var index = 0

    fun parse(): Any? {
        val value = value()
        whitespace()
        check(index == source.length) { "trailing JSON" }
        return value
    }

    private fun value(): Any? {
        whitespace()
        check(index < source.length)
        return when (source[index]) {
            '{' -> objectValue()
            '[' -> arrayValue()
            '"' -> stringValue()
            't' -> literal("true", true)
            'f' -> literal("false", false)
            'n' -> literal("null", null)
            else -> numberValue()
        }
    }

    private fun objectValue(): MutableMap<String, Any?> {
        expect('{')
        val result = LinkedHashMap<String, Any?>()
        whitespace()
        if (peek('}')) return result.also { index++ }
        while (true) {
            val key = stringValue()
            whitespace(); expect(':')
            check(result.put(key, value()) == null) { "duplicate JSON key" }
            whitespace()
            if (peek('}')) { index++; return result }
            expect(',')
        }
    }

    private fun arrayValue(): MutableList<Any?> {
        expect('[')
        val result = ArrayList<Any?>()
        whitespace()
        if (peek(']')) return result.also { index++ }
        while (true) {
            result += value()
            whitespace()
            if (peek(']')) { index++; return result }
            expect(',')
        }
    }

    private fun stringValue(): String {
        expect('"')
        val result = StringBuilder()
        while (index < source.length) {
            val character = source[index++]
            when (character) {
                '"' -> return result.toString()
                '\\' -> {
                    check(index < source.length)
                    when (val escaped = source[index++]) {
                        '"', '\\', '/' -> result.append(escaped)
                        'b' -> result.append('\b')
                        'f' -> result.append('\u000c')
                        'n' -> result.append('\n')
                        'r' -> result.append('\r')
                        't' -> result.append('\t')
                        'u' -> {
                            check(index + 4 <= source.length)
                            result.append(source.substring(index, index + 4).toInt(16).toChar())
                            index += 4
                        }
                        else -> error("invalid escape")
                    }
                }
                else -> {
                    check(character.code >= 0x20)
                    result.append(character)
                }
            }
        }
        error("unterminated string")
    }

    private fun numberValue(): Long {
        val start = index
        if (peek('-')) index++
        while (index < source.length && source[index].isDigit()) index++
        check(index > start)
        return source.substring(start, index).toLong()
    }

    private fun <T> literal(text: String, value: T): T {
        check(source.startsWith(text, index))
        index += text.length
        return value
    }

    private fun whitespace() {
        while (index < source.length && source[index].isWhitespace()) index++
    }

    private fun expect(character: Char) {
        whitespace(); check(peek(character)) { "expected $character" }; index++
    }

    private fun peek(character: Char): Boolean = index < source.length && source[index] == character
}
