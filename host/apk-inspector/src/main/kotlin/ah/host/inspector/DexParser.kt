package ah.host.inspector

import java.security.MessageDigest
import java.util.LinkedHashSet

internal data class ParsedDex(
    val summary: DexSummary,
    val descriptorMarkerIds: List<String>,
)

internal class DexParser(
    private val entryName: String,
    private val ordinal: Int,
    private val data: SegmentedBytes,
) {
    fun parse(): ParsedDex = try {
        parseChecked()
    } catch (_: DataFailure) {
        throw DexFailure()
    } catch (_: StructureFailure) {
        throw DexFailure()
    }

    private fun parseChecked(): ParsedDex {
        if (data.size < HEADER_SIZE || !validMagic()) throw DexFailure()
        val fileSize = toInt(data.u4(FILE_SIZE_OFFSET))
        if (fileSize != data.size || data.u4(HEADER_SIZE_OFFSET) != HEADER_SIZE.toLong()) throw DexFailure()
        if (data.u4(ENDIAN_TAG_OFFSET) != ENDIAN_CONSTANT) throw DexFailure()
        val expectedChecksum = data.u4(CHECKSUM_OFFSET)
        if (data.adler32(SIGNATURE_OFFSET, data.size - SIGNATURE_OFFSET) != expectedChecksum) throw DexFailure()
        val expectedSignature = data.copy(SIGNATURE_OFFSET, SIGNATURE_SIZE)
        val actualSignature = data.digest("SHA-1", SHA1_DATA_OFFSET, data.size - SHA1_DATA_OFFSET)
        if (!MessageDigest.isEqual(expectedSignature, actualSignature)) throw DexFailure()

        val stringCount = tableCount(STRING_IDS_SIZE_OFFSET)
        val stringOffset = tableOffset(STRING_IDS_OFFSET_OFFSET, stringCount, 4)
        val typeCount = tableCount(TYPE_IDS_SIZE_OFFSET)
        val typeOffset = tableOffset(TYPE_IDS_OFFSET_OFFSET, typeCount, 4)
        val classCount = tableCount(CLASS_DEFS_SIZE_OFFSET)
        val classOffset = tableOffset(CLASS_DEFS_OFFSET_OFFSET, classCount, CLASS_DEF_SIZE)
        val descriptorMarkers = LinkedHashSet<String>()
        val seen = LinkedHashSet<String>()
        repeat(classCount) { index ->
            val classIndex = toInt(data.u4(classOffset + index * CLASS_DEF_SIZE))
            if (classIndex >= typeCount) throw DexFailure()
            val descriptorIndex = toInt(data.u4(typeOffset + classIndex * 4))
            if (descriptorIndex >= stringCount) throw DexFailure()
            val stringDataOffset = toInt(data.u4(stringOffset + descriptorIndex * 4))
            val descriptor = readMutf8(stringDataOffset)
            if (!validClassDescriptor(descriptor) || !seen.add(descriptor)) throw DexFailure()
            descriptorMarkers += CompatibilityRules.descriptorMarkerIds(descriptor)
        }
        return ParsedDex(
            summary = DexSummary(
                entryName = entryName,
                ordinal = ordinal,
                fileSize = data.size.toLong(),
                classCount = classCount,
                sha256 = data.digest("SHA-256"),
            ),
            descriptorMarkerIds = immutableList(descriptorMarkers),
        )
    }

    private fun validMagic(): Boolean {
        if (data.u1(0) != 'd'.code || data.u1(1) != 'e'.code || data.u1(2) != 'x'.code || data.u1(3) != '\n'.code) {
            return false
        }
        val version = data.copy(4, 3).toString(Charsets.US_ASCII).toIntOrNull() ?: return false
        return version in MIN_VERSION..MAX_VERSION && data.u1(7) == 0
    }

    private fun tableCount(offset: Int): Int {
        val count = toInt(data.u4(offset))
        if (count > MAX_TABLE_ITEMS) throw DexFailure()
        return count
    }

    private fun tableOffset(offsetField: Int, count: Int, itemSize: Int): Int {
        val offset = toInt(data.u4(offsetField))
        if (count == 0) {
            if (offset != 0) throw DexFailure()
            return 0
        }
        val byteCount = try {
            Math.multiplyExact(count, itemSize)
        } catch (_: ArithmeticException) {
            throw DexFailure()
        }
        if (offset < HEADER_SIZE || offset > data.size - byteCount) throw DexFailure()
        return offset
    }

    private fun readMutf8(offset: Int): String {
        if (offset < HEADER_SIZE || offset >= data.size) throw DexFailure()
        val length = readUleb128(offset)
        var cursor = length.next
        val result = StringBuilder(length.value)
        while (true) {
            if (cursor >= data.size) throw DexFailure()
            val first = data.u1(cursor++)
            if (first == 0) break
            when {
                first and 0x80 == 0 -> result.append(first.toChar())
                first and 0xe0 == 0xc0 -> {
                    if (cursor >= data.size) throw DexFailure()
                    val second = data.u1(cursor++)
                    if (second and 0xc0 != 0x80) throw DexFailure()
                    val value = ((first and 0x1f) shl 6) or (second and 0x3f)
                    if (value != 0 && value < 0x80) throw DexFailure()
                    result.append(value.toChar())
                }
                first and 0xf0 == 0xe0 -> {
                    if (cursor > data.size - 2) throw DexFailure()
                    val second = data.u1(cursor++)
                    val third = data.u1(cursor++)
                    if (second and 0xc0 != 0x80 || third and 0xc0 != 0x80) throw DexFailure()
                    val value = ((first and 0x0f) shl 12) or ((second and 0x3f) shl 6) or (third and 0x3f)
                    if (value < 0x800) throw DexFailure()
                    result.append(value.toChar())
                }
                else -> throw DexFailure()
            }
            if (result.length > length.value) throw DexFailure()
        }
        if (result.length != length.value) throw DexFailure()
        return result.toString()
    }

    private fun readUleb128(offset: Int): Uleb {
        var result = 0L
        var cursor = offset
        var shift = 0
        repeat(5) { index ->
            if (cursor >= data.size) throw DexFailure()
            val value = data.u1(cursor++)
            result = result or ((value and 0x7f).toLong() shl shift)
            if (value and 0x80 == 0) {
                if (result > Int.MAX_VALUE || index == 4 && value and 0xf0 != 0) throw DexFailure()
                return Uleb(result.toInt(), cursor)
            }
            shift += 7
        }
        throw DexFailure()
    }

    private fun validClassDescriptor(value: String): Boolean {
        if (value.length < 3 || value.first() != 'L' || value.last() != ';') return false
        val body = value.substring(1, value.length - 1)
        if (body.startsWith('/') || body.endsWith('/') || "//" in body) return false
        return body.none { it == '.' || it == '[' || it == ';' || it == '\u0000' }
    }

    private fun toInt(value: Long): Int {
        if (value < 0L || value > Int.MAX_VALUE) throw DexFailure()
        return value.toInt()
    }

    private data class Uleb(val value: Int, val next: Int)

    companion object {
        private const val HEADER_SIZE = 112
        private const val CHECKSUM_OFFSET = 8
        private const val SIGNATURE_OFFSET = 12
        private const val SIGNATURE_SIZE = 20
        private const val SHA1_DATA_OFFSET = 32
        private const val FILE_SIZE_OFFSET = 32
        private const val HEADER_SIZE_OFFSET = 36
        private const val ENDIAN_TAG_OFFSET = 40
        private const val STRING_IDS_SIZE_OFFSET = 56
        private const val STRING_IDS_OFFSET_OFFSET = 60
        private const val TYPE_IDS_SIZE_OFFSET = 64
        private const val TYPE_IDS_OFFSET_OFFSET = 68
        private const val CLASS_DEFS_SIZE_OFFSET = 96
        private const val CLASS_DEFS_OFFSET_OFFSET = 100
        private const val CLASS_DEF_SIZE = 32
        private const val ENDIAN_CONSTANT = 0x1234_5678L
        private const val MIN_VERSION = 35
        private const val MAX_VERSION = 41
        private const val MAX_TABLE_ITEMS = 16_777_216
    }
}
