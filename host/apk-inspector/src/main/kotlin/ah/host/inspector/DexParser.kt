package ah.host.inspector

import java.security.MessageDigest
import java.util.BitSet
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

        if (data.u4(LINK_SIZE_OFFSET) != 0L || data.u4(LINK_OFFSET_OFFSET) != 0L) throw DexFailure()

        val stringCount = tableCount(STRING_IDS_SIZE_OFFSET)
        val stringOffset = tableOffset(STRING_IDS_OFFSET_OFFSET, stringCount, 4)
        val typeCount = tableCount(TYPE_IDS_SIZE_OFFSET)
        val typeOffset = tableOffset(TYPE_IDS_OFFSET_OFFSET, typeCount, 4)
        val protoCount = tableCount(PROTO_IDS_SIZE_OFFSET)
        val protoOffset = tableOffset(PROTO_IDS_OFFSET_OFFSET, protoCount, PROTO_ID_SIZE)
        val fieldCount = tableCount(FIELD_IDS_SIZE_OFFSET)
        val fieldOffset = tableOffset(FIELD_IDS_OFFSET_OFFSET, fieldCount, FIELD_ID_SIZE)
        val methodCount = tableCount(METHOD_IDS_SIZE_OFFSET)
        val methodOffset = tableOffset(METHOD_IDS_OFFSET_OFFSET, methodCount, METHOD_ID_SIZE)
        val classCount = tableCount(CLASS_DEFS_SIZE_OFFSET)
        val classOffset = tableOffset(CLASS_DEFS_OFFSET_OFFSET, classCount, CLASS_DEF_SIZE)
        val dataSize = toInt(data.u4(DATA_SIZE_OFFSET))
        val dataOffset = toInt(data.u4(DATA_OFFSET_OFFSET))
        if (dataSize <= 0 || dataOffset < HEADER_SIZE || dataOffset and 3 != 0 || dataOffset > data.size - dataSize ||
            dataOffset + dataSize != data.size
        ) {
            throw DexFailure()
        }
        validateFixedRanges(
            dataOffset,
            listOf(
                FixedRange(0, HEADER_SIZE),
                FixedRange(stringOffset, checkedTableSize(stringCount, 4)),
                FixedRange(typeOffset, checkedTableSize(typeCount, 4)),
                FixedRange(protoOffset, checkedTableSize(protoCount, PROTO_ID_SIZE)),
                FixedRange(fieldOffset, checkedTableSize(fieldCount, FIELD_ID_SIZE)),
                FixedRange(methodOffset, checkedTableSize(methodCount, METHOD_ID_SIZE)),
                FixedRange(classOffset, checkedTableSize(classCount, CLASS_DEF_SIZE)),
            ).filter { it.size > 0 },
        )

        val stringStarts = BitSet(data.size)
        var firstStringDataOffset = Int.MAX_VALUE
        repeat(stringCount) { index ->
            val value = toInt(data.u4(stringOffset + index * 4))
            if (value < dataOffset || value >= data.size || stringStarts.get(value)) throw DexFailure()
            stringStarts.set(value)
            firstStringDataOffset = minOf(firstStringDataOffset, value)
        }
        validateMap(
            toInt(data.u4(MAP_OFFSET)),
            dataOffset,
            stringCount,
            firstStringDataOffset,
            listOf(
                ExpectedMap(TYPE_HEADER_ITEM, 1, 0),
                ExpectedMap(TYPE_STRING_ID_ITEM, stringCount, stringOffset),
                ExpectedMap(TYPE_TYPE_ID_ITEM, typeCount, typeOffset),
                ExpectedMap(TYPE_PROTO_ID_ITEM, protoCount, protoOffset),
                ExpectedMap(TYPE_FIELD_ID_ITEM, fieldCount, fieldOffset),
                ExpectedMap(TYPE_METHOD_ID_ITEM, methodCount, methodOffset),
                ExpectedMap(TYPE_CLASS_DEF_ITEM, classCount, classOffset),
            ),
        )
        var previousDescriptorIndex = -1
        repeat(typeCount) { index ->
            val descriptorIndex = toInt(data.u4(typeOffset + index * 4))
            if (descriptorIndex >= stringCount || descriptorIndex <= previousDescriptorIndex) throw DexFailure()
            previousDescriptorIndex = descriptorIndex
        }
        val descriptorMarkers = LinkedHashSet<String>()
        var previousClassIndex = -1
        repeat(classCount) { index ->
            val itemOffset = classOffset + index * CLASS_DEF_SIZE
            val classIndex = toInt(data.u4(itemOffset))
            if (classIndex >= typeCount || classIndex <= previousClassIndex) throw DexFailure()
            previousClassIndex = classIndex
            validateOptionalIndex(data.u4(itemOffset + 8), typeCount)
            validateOptionalDataOffset(data.u4(itemOffset + 12), dataOffset)
            validateOptionalIndex(data.u4(itemOffset + 16), stringCount)
            validateOptionalDataOffset(data.u4(itemOffset + 20), dataOffset)
            validateOptionalDataOffset(data.u4(itemOffset + 24), dataOffset)
            validateOptionalDataOffset(data.u4(itemOffset + 28), dataOffset)
            val descriptorIndex = toInt(data.u4(typeOffset + classIndex * 4))
            if (descriptorIndex >= stringCount) throw DexFailure()
            val stringDataOffset = toInt(data.u4(stringOffset + descriptorIndex * 4))
            val descriptor = readDescriptorMarkerIds(stringDataOffset)
            val nextStringStart = stringStarts.nextSetBit(stringDataOffset + 1)
            if (nextStringStart >= 0 && nextStringStart < descriptor.endOffset) throw DexFailure()
            descriptorMarkers += descriptor.markerIds
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
        return version in SUPPORTED_VERSIONS && data.u1(7) == 0
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

    private fun checkedTableSize(count: Int, itemSize: Int): Int = try {
        Math.multiplyExact(count, itemSize)
    } catch (_: ArithmeticException) {
        throw DexFailure()
    }

    private fun validateFixedRanges(dataOffset: Int, ranges: List<FixedRange>) {
        var previousEnd = 0
        for (range in ranges.sortedBy { it.offset }) {
            if (range.offset < previousEnd || range.offset > dataOffset - range.size) throw DexFailure()
            previousEnd = range.offset + range.size
        }
    }

    private fun validateMap(
        mapOffset: Int,
        dataOffset: Int,
        stringCount: Int,
        firstStringDataOffset: Int,
        expected: List<ExpectedMap>,
    ) {
        if (mapOffset < dataOffset || mapOffset and 3 != 0 || mapOffset > data.size - 4) throw DexFailure()
        val count = toInt(data.u4(mapOffset))
        if (count <= 0 || count > MAX_MAP_ITEMS) throw DexFailure()
        val byteCount = try {
            Math.multiplyExact(count, MAP_ITEM_SIZE)
        } catch (_: ArithmeticException) {
            throw DexFailure()
        }
        if (mapOffset + 4 > data.size - byteCount) throw DexFailure()
        val seenTypes = BooleanArray(1 shl 16)
        val expectedSeen = BooleanArray(expected.size)
        var previousOffset = -1
        var firstDataItemOffset = Int.MAX_VALUE
        var stringDataSeen = stringCount == 0
        var mapListSeen = false
        repeat(count) { index ->
            val item = mapOffset + 4 + index * MAP_ITEM_SIZE
            val type = data.u2(item)
            if (data.u2(item + 2) != 0 || !seenTypes.indices.contains(type) || seenTypes[type]) throw DexFailure()
            seenTypes[type] = true
            if (type !in SUPPORTED_MAP_TYPES) throw DexFailure()
            val size = toInt(data.u4(item + 4))
            val offset = toInt(data.u4(item + 8))
            if (size <= 0 || offset <= previousOffset || offset >= data.size) throw DexFailure()
            previousOffset = offset
            if (type >= TYPE_MAP_LIST && offset < dataOffset) throw DexFailure()
            if (type >= TYPE_MAP_LIST) firstDataItemOffset = minOf(firstDataItemOffset, offset)

            val expectedIndex = expected.indexOfFirst { it.type == type }
            if (expectedIndex >= 0) {
                val value = expected[expectedIndex]
                if (value.count == 0 || value.count != size || value.offset != offset) throw DexFailure()
                expectedSeen[expectedIndex] = true
            }
            if (type == TYPE_STRING_DATA_ITEM) {
                if (stringCount == 0 || size != stringCount || offset != firstStringDataOffset) throw DexFailure()
                stringDataSeen = true
            }
            if (type == TYPE_MAP_LIST) {
                if (size != 1 || offset != mapOffset) throw DexFailure()
                mapListSeen = true
            }
        }
        expected.forEachIndexed { index, item ->
            if (item.count > 0 && !expectedSeen[index]) throw DexFailure()
            if (item.count == 0 && expectedSeen[index]) throw DexFailure()
        }
        if (!stringDataSeen || !mapListSeen || firstDataItemOffset != dataOffset) throw DexFailure()
    }

    private fun validateOptionalIndex(value: Long, count: Int) {
        if (value != NO_INDEX && (value < 0L || value >= count)) throw DexFailure()
    }

    private fun validateOptionalDataOffset(value: Long, dataOffset: Int) {
        if (value != 0L && (value < dataOffset || value >= data.size)) throw DexFailure()
    }

    private fun readDescriptorMarkerIds(offset: Int): DescriptorRead {
        if (offset < HEADER_SIZE || offset >= data.size) throw DexFailure()
        val length = readUleb128(offset)
        var cursor = length.next
        var decodedLength = 0
        var previous = '\u0000'
        var semicolonSeen = false
        val markerPrefix = StringBuilder(minOf(length.value, MARKER_PREFIX_CHARS))

        fun accept(value: Char) {
            decodedLength++
            if (decodedLength > length.value) throw DexFailure()
            if (markerPrefix.length < MARKER_PREFIX_CHARS) markerPrefix.append(value)
            when {
                decodedLength == 1 -> if (value != 'L') throw DexFailure()
                semicolonSeen -> throw DexFailure()
                value == ';' -> {
                    if (decodedLength < 3 || previous == '/') throw DexFailure()
                    semicolonSeen = true
                }
                value == '.' || value == '[' || value == '\u0000' -> throw DexFailure()
                decodedLength == 2 && value == '/' -> throw DexFailure()
                value == '/' && previous == '/' -> throw DexFailure()
            }
            previous = value
        }

        while (true) {
            if (cursor >= data.size) throw DexFailure()
            val first = data.u1(cursor++)
            if (first == 0) break
            when {
                first and 0x80 == 0 -> accept(first.toChar())
                first and 0xe0 == 0xc0 -> {
                    if (cursor >= data.size) throw DexFailure()
                    val second = data.u1(cursor++)
                    if (second and 0xc0 != 0x80) throw DexFailure()
                    val value = ((first and 0x1f) shl 6) or (second and 0x3f)
                    if (value != 0 && value < 0x80) throw DexFailure()
                    accept(value.toChar())
                }
                first and 0xf0 == 0xe0 -> {
                    if (cursor > data.size - 2) throw DexFailure()
                    val second = data.u1(cursor++)
                    val third = data.u1(cursor++)
                    if (second and 0xc0 != 0x80 || third and 0xc0 != 0x80) throw DexFailure()
                    val value = ((first and 0x0f) shl 12) or ((second and 0x3f) shl 6) or (third and 0x3f)
                    if (value < 0x800) throw DexFailure()
                    accept(value.toChar())
                }
                else -> throw DexFailure()
            }
        }
        if (decodedLength != length.value || !semicolonSeen || previous != ';') throw DexFailure()
        return DescriptorRead(CompatibilityRules.descriptorMarkerIds(markerPrefix.toString()), cursor)
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

    private fun toInt(value: Long): Int {
        if (value < 0L || value > Int.MAX_VALUE) throw DexFailure()
        return value.toInt()
    }

    private data class Uleb(val value: Int, val next: Int)
    private data class DescriptorRead(val markerIds: List<String>, val endOffset: Int)
    private data class ExpectedMap(val type: Int, val count: Int, val offset: Int)
    private data class FixedRange(val offset: Int, val size: Int)

    companion object {
        private const val HEADER_SIZE = 112
        private const val CHECKSUM_OFFSET = 8
        private const val SIGNATURE_OFFSET = 12
        private const val SIGNATURE_SIZE = 20
        private const val SHA1_DATA_OFFSET = 32
        private const val FILE_SIZE_OFFSET = 32
        private const val HEADER_SIZE_OFFSET = 36
        private const val ENDIAN_TAG_OFFSET = 40
        private const val LINK_SIZE_OFFSET = 44
        private const val LINK_OFFSET_OFFSET = 48
        private const val MAP_OFFSET = 52
        private const val STRING_IDS_SIZE_OFFSET = 56
        private const val STRING_IDS_OFFSET_OFFSET = 60
        private const val TYPE_IDS_SIZE_OFFSET = 64
        private const val TYPE_IDS_OFFSET_OFFSET = 68
        private const val PROTO_IDS_SIZE_OFFSET = 72
        private const val PROTO_IDS_OFFSET_OFFSET = 76
        private const val FIELD_IDS_SIZE_OFFSET = 80
        private const val FIELD_IDS_OFFSET_OFFSET = 84
        private const val METHOD_IDS_SIZE_OFFSET = 88
        private const val METHOD_IDS_OFFSET_OFFSET = 92
        private const val CLASS_DEFS_SIZE_OFFSET = 96
        private const val CLASS_DEFS_OFFSET_OFFSET = 100
        private const val DATA_SIZE_OFFSET = 104
        private const val DATA_OFFSET_OFFSET = 108
        private const val PROTO_ID_SIZE = 12
        private const val FIELD_ID_SIZE = 8
        private const val METHOD_ID_SIZE = 8
        private const val CLASS_DEF_SIZE = 32
        private const val ENDIAN_CONSTANT = 0x1234_5678L
        private val SUPPORTED_VERSIONS = setOf(35, 37, 38, 39, 40)
        private const val MAX_TABLE_ITEMS = 16_777_216
        private const val MAX_MAP_ITEMS = 65_536
        private const val MAP_ITEM_SIZE = 12
        private const val NO_INDEX = 0xffff_ffffL
        private const val TYPE_HEADER_ITEM = 0x0000
        private const val TYPE_STRING_ID_ITEM = 0x0001
        private const val TYPE_TYPE_ID_ITEM = 0x0002
        private const val TYPE_PROTO_ID_ITEM = 0x0003
        private const val TYPE_FIELD_ID_ITEM = 0x0004
        private const val TYPE_METHOD_ID_ITEM = 0x0005
        private const val TYPE_CLASS_DEF_ITEM = 0x0006
        private const val TYPE_MAP_LIST = 0x1000
        private const val TYPE_STRING_DATA_ITEM = 0x2002
        private val SUPPORTED_MAP_TYPES = setOf(
            TYPE_HEADER_ITEM,
            TYPE_STRING_ID_ITEM,
            TYPE_TYPE_ID_ITEM,
            TYPE_PROTO_ID_ITEM,
            TYPE_FIELD_ID_ITEM,
            TYPE_METHOD_ID_ITEM,
            TYPE_CLASS_DEF_ITEM,
            0x0007,
            0x0008,
            TYPE_MAP_LIST,
            0x1001,
            0x1002,
            0x1003,
            0x2000,
            0x2001,
            TYPE_STRING_DATA_ITEM,
            0x2003,
            0x2004,
            0x2005,
            0x2006,
            0x2007,
        )
        private const val MARKER_PREFIX_CHARS = 128
    }
}
