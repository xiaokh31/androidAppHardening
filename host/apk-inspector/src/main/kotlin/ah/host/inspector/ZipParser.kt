package ah.host.inspector

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.Normalizer
import java.util.LinkedHashSet
import java.util.zip.CRC32
import java.util.zip.DataFormatException
import java.util.zip.Inflater

internal data class ParsedZipEntry(
    val record: ZipEntryRecord,
    val normalizedName: String,
    val flags: Int,
    val dataOffset: Long,
    val endOffset: Long,
)

internal class ParsedZip(
    entries: List<ParsedZipEntry>,
) {
    val entries: List<ParsedZipEntry> = immutableList(entries)
}

internal class ZipParser(private val source: FileSource) {
    fun parse(): ParsedZip {
        if (source.size > InspectionLimits.MAX_APK_BYTES) throw LimitFailure("apkBytes")
        if (source.size < EOCD_MIN_SIZE.toLong()) throw StructureFailure()
        val eocd = findEocd()
        val entries = parseCentralDirectory(eocd)
        validateLocalRanges(entries, eocd.centralOffset)
        for (entry in entries) {
            interrupted()
            readAndVerify(entry, false)
        }
        return ParsedZip(entries)
    }

    fun materialize(entry: ParsedZipEntry): SegmentedBytes =
        readAndVerify(entry, true) ?: throw StructureFailure()

    private fun findEocd(): Eocd {
        val tailLength = minOf(source.size, (EOCD_MIN_SIZE + MAX_COMMENT).toLong()).toInt()
        val tailOffset = source.size - tailLength
        val tail = source.readFully(tailOffset, tailLength)
        var candidate: Eocd? = null
        var index = tail.size - EOCD_MIN_SIZE
        while (index >= 0) {
            if (leU4(tail, index) == EOCD_SIGNATURE) {
                val commentLength = leU2(tail, index + 20)
                if (index + EOCD_MIN_SIZE + commentLength == tail.size) {
                    if (candidate != null) throw StructureFailure()
                    val disk = leU2(tail, index + 4)
                    val centralDisk = leU2(tail, index + 6)
                    val diskEntries = leU2(tail, index + 8)
                    val totalEntries = leU2(tail, index + 10)
                    val centralSize = leU4(tail, index + 12)
                    val centralOffset = leU4(tail, index + 16)
                    if (disk != 0 || centralDisk != 0 || diskEntries != totalEntries) throw StructureFailure()
                    if (totalEntries > InspectionLimits.MAX_ENTRIES) throw LimitFailure("entries")
                    if (centralSize == UINT32_MAX || centralOffset == UINT32_MAX) {
                        throw StructureFailure()
                    }
                    val absoluteOffset = tailOffset + index
                    if (absoluteOffset >= ZIP64_LOCATOR_SIZE &&
                        leU4(source.readFully(absoluteOffset - ZIP64_LOCATOR_SIZE, 4), 0) == ZIP64_LOCATOR_SIGNATURE
                    ) {
                        throw StructureFailure()
                    }
                    if (checkedAdd(centralOffset, centralSize) != absoluteOffset) throw StructureFailure()
                    candidate = Eocd(totalEntries, centralOffset, centralSize)
                }
            }
            index--
        }
        return candidate ?: throw StructureFailure()
    }

    private fun parseCentralDirectory(eocd: Eocd): List<ParsedZipEntry> {
        val entries = ArrayList<ParsedZipEntry>(eocd.entryCount)
        val normalizedNames = LinkedHashSet<String>()
        var totalUncompressed = 0L
        var cursor = eocd.centralOffset
        val centralEnd = checkedAdd(eocd.centralOffset, eocd.centralSize)
        repeat(eocd.entryCount) { index ->
            if (cursor > centralEnd - CENTRAL_FIXED_SIZE) throw StructureFailure()
            val header = source.readFully(cursor, CENTRAL_FIXED_SIZE)
            if (leU4(header, 0) != CENTRAL_SIGNATURE) throw StructureFailure()
            val flags = leU2(header, 8)
            validateFlags(flags)
            val method = leU2(header, 10)
            validateMethod(method)
            val crc32 = leU4(header, 16)
            val compressedSize = leU4(header, 20)
            val uncompressedSize = leU4(header, 24)
            val nameLength = leU2(header, 28)
            val extraLength = leU2(header, 30)
            val commentLength = leU2(header, 32)
            val diskStart = leU2(header, 34)
            val localOffset = leU4(header, 42)
            if (compressedSize == UINT32_MAX || uncompressedSize == UINT32_MAX || localOffset == UINT32_MAX) {
                throw StructureFailure()
            }
            if (diskStart != 0 || nameLength == 0) throw StructureFailure()
            val variableLength = checkedAdd(nameLength.toLong(), checkedAdd(extraLength.toLong(), commentLength.toLong()))
            val next = checkedAdd(cursor, checkedAdd(CENTRAL_FIXED_SIZE.toLong(), variableLength))
            if (next > centralEnd) throw StructureFailure()
            val rawName = source.readFully(cursor + CENTRAL_FIXED_SIZE, nameLength)
            val extra = source.readFully(cursor + CENTRAL_FIXED_SIZE + nameLength, extraLength)
            rejectZip64Extra(extra)
            val name = validateName(rawName)
            val normalized = Normalizer.normalize(name, Normalizer.Form.NFC)
            if (!normalizedNames.add(normalized)) throw DuplicateFailure()
            validateDeclaredLimits(compressedSize, uncompressedSize)
            totalUncompressed = checkedAdd(totalUncompressed, uncompressedSize)
            if (totalUncompressed > InspectionLimits.MAX_TOTAL_UNCOMPRESSED_BYTES) {
                throw LimitFailure("totalUncompressedBytes")
            }
            val record = ZipEntryRecord(
                index = index,
                name = name,
                originalNameSha256 = MessageDigest.getInstance("SHA-256").digest(rawName),
                method = method,
                crc32 = crc32,
                compressedSize = compressedSize,
                uncompressedSize = uncompressedSize,
                localHeaderOffset = localOffset,
            )
            entries += ParsedZipEntry(record, normalized, flags, -1L, -1L)
            cursor = next
        }
        if (cursor != centralEnd) throw StructureFailure()
        return entries.map { validateLocalHeader(it, eocd.centralOffset) }
    }

    private fun validateLocalHeader(entry: ParsedZipEntry, centralOffset: Long): ParsedZipEntry {
        val offset = entry.record.localHeaderOffset
        if (offset > centralOffset - LOCAL_FIXED_SIZE) throw StructureFailure()
        val header = source.readFully(offset, LOCAL_FIXED_SIZE)
        if (leU4(header, 0) != LOCAL_SIGNATURE) throw StructureFailure()
        val flags = leU2(header, 6)
        val method = leU2(header, 8)
        val crc32 = leU4(header, 14)
        val compressedSize = leU4(header, 18)
        val uncompressedSize = leU4(header, 22)
        val nameLength = leU2(header, 26)
        val extraLength = leU2(header, 28)
        if (flags != entry.flags || method != entry.record.method) throw StructureFailure()
        val variableEnd = checkedAdd(offset, LOCAL_FIXED_SIZE.toLong() + nameLength + extraLength)
        if (variableEnd > centralOffset) throw StructureFailure()
        val rawName = source.readFully(offset + LOCAL_FIXED_SIZE, nameLength)
        if (!rawName.contentEquals(entry.record.name.toByteArray(StandardCharsets.UTF_8))) throw StructureFailure()
        val extra = source.readFully(offset + LOCAL_FIXED_SIZE + nameLength, extraLength)
        rejectZip64Extra(extra)
        val descriptor = flags and DATA_DESCRIPTOR_FLAG != 0
        if (!descriptor) {
            if (crc32 != entry.record.crc32 ||
                compressedSize != entry.record.compressedSize ||
                uncompressedSize != entry.record.uncompressedSize
            ) {
                throw StructureFailure()
            }
        } else {
            if (crc32 != 0L && crc32 != entry.record.crc32) throw StructureFailure()
            if (compressedSize != 0L && compressedSize != entry.record.compressedSize) throw StructureFailure()
            if (uncompressedSize != 0L && uncompressedSize != entry.record.uncompressedSize) throw StructureFailure()
        }
        val dataEnd = checkedAdd(variableEnd, entry.record.compressedSize)
        if (dataEnd > centralOffset) throw StructureFailure()
        val entryEnd = if (descriptor) validateDataDescriptor(dataEnd, entry.record, centralOffset) else dataEnd
        return entry.copy(dataOffset = variableEnd, endOffset = entryEnd)
    }

    private fun validateDataDescriptor(offset: Long, record: ZipEntryRecord, centralOffset: Long): Long {
        if (offset > centralOffset - DATA_DESCRIPTOR_MIN_SIZE) throw StructureFailure()
        val first = leU4(source.readFully(offset, 4), 0)
        val signed = first == DATA_DESCRIPTOR_SIGNATURE
        val size = if (signed) DATA_DESCRIPTOR_SIGNED_SIZE else DATA_DESCRIPTOR_MIN_SIZE
        if (offset > centralOffset - size) throw StructureFailure()
        val descriptor = source.readFully(offset, size.toInt())
        val valueOffset = if (signed) 4 else 0
        if (leU4(descriptor, valueOffset) != record.crc32 ||
            leU4(descriptor, valueOffset + 4) != record.compressedSize ||
            leU4(descriptor, valueOffset + 8) != record.uncompressedSize
        ) {
            throw StructureFailure()
        }
        return offset + size
    }

    private fun validateLocalRanges(entries: List<ParsedZipEntry>, centralOffset: Long) {
        val ranges = entries.map {
            if (it.record.localHeaderOffset >= centralOffset || it.endOffset > centralOffset) throw StructureFailure()
            it.record.localHeaderOffset to it.endOffset
        }.sortedBy { it.first }
        var previousEnd = 0L
        for ((start, end) in ranges) {
            if (start < previousEnd || end < start) throw StructureFailure()
            previousEnd = end
        }
    }

    private fun readAndVerify(entry: ParsedZipEntry, materialize: Boolean): SegmentedBytes? {
        val crc = CRC32()
        val chunks = if (materialize) ArrayList<ByteArray>() else null
        var outputCount = 0L
        fun consume(buffer: ByteArray, count: Int) {
            interrupted()
            outputCount = checkedAdd(outputCount, count.toLong())
            if (outputCount > entry.record.uncompressedSize ||
                outputCount > InspectionLimits.MAX_ENTRY_UNCOMPRESSED_BYTES
            ) {
                throw LimitFailure("entryUncompressedBytes")
            }
            crc.update(buffer, 0, count)
            if (chunks != null && count > 0) chunks += buffer.copyOf(count)
        }

        when (entry.record.method) {
            METHOD_STORED -> {
                if (entry.record.compressedSize != entry.record.uncompressedSize) throw StructureFailure()
                var position = entry.dataOffset
                var remaining = entry.record.compressedSize
                while (remaining > 0L) {
                    val count = minOf(IO_CHUNK.toLong(), remaining).toInt()
                    val buffer = source.readFully(position, count)
                    consume(buffer, count)
                    position += count
                    remaining -= count
                }
            }
            METHOD_DEFLATED -> inflate(entry, ::consume)
            else -> throw StructureFailure()
        }
        if (outputCount != entry.record.uncompressedSize || crc.value != entry.record.crc32) {
            throw StructureFailure()
        }
        return chunks?.let { SegmentedBytes.fromChunks(it, outputCount) }
    }

    private fun inflate(entry: ParsedZipEntry, consume: (ByteArray, Int) -> Unit) {
        val inflater = Inflater(true)
        val input = ByteArray(IO_CHUNK)
        val output = ByteArray(IO_CHUNK)
        var fed = 0L
        try {
            while (!inflater.finished()) {
                interrupted()
                if (inflater.needsDictionary()) throw StructureFailure()
                if (inflater.needsInput()) {
                    if (fed >= entry.record.compressedSize) throw StructureFailure()
                    val count = minOf(input.size.toLong(), entry.record.compressedSize - fed).toInt()
                    source.readInto(entry.dataOffset + fed, input, 0, count)
                    inflater.setInput(input, 0, count)
                    fed += count
                }
                val count = try {
                    inflater.inflate(output)
                } catch (_: DataFormatException) {
                    throw StructureFailure()
                }
                if (count > 0) {
                    consume(output, count)
                } else if (!inflater.finished() && !inflater.needsInput()) {
                    throw StructureFailure()
                }
            }
            val consumed = fed - inflater.remaining
            if (consumed != entry.record.compressedSize) throw StructureFailure()
        } finally {
            inflater.end()
        }
    }

    private fun validateDeclaredLimits(compressedSize: Long, uncompressedSize: Long) {
        if (uncompressedSize > InspectionLimits.MAX_ENTRY_UNCOMPRESSED_BYTES) {
            throw LimitFailure("entryUncompressedBytes")
        }
        if (uncompressedSize > 0L && compressedSize == 0L) throw LimitFailure("compressionRatio")
        if (compressedSize > 0L && uncompressedSize > compressedSize * InspectionLimits.MAX_COMPRESSION_RATIO) {
            throw LimitFailure("compressionRatio")
        }
    }

    private fun validateName(rawName: ByteArray): String {
        if (rawName.size > InspectionLimits.MAX_PATH_UTF8_BYTES) throw LimitFailure("pathUtf8Bytes")
        val name = try {
            strictUtf8(rawName)
        } catch (_: DataFailure) {
            throw PathFailure()
        }
        if (name.isEmpty() || name.indexOf('\u0000') >= 0 || name.indexOf('\\') >= 0 || name.startsWith('/')) {
            throw PathFailure()
        }
        if (name.length >= 2 && name[0].isLetter() && name[1] == ':') throw PathFailure()
        val directory = name.endsWith('/')
        val components = name.split('/')
        for ((index, component) in components.withIndex()) {
            if (component.isEmpty() && directory && index == components.lastIndex) continue
            if (component.isEmpty() || component == "." || component == "..") throw PathFailure()
        }
        return name
    }

    private fun validateFlags(flags: Int) {
        if (flags and UNSUPPORTED_FLAGS != 0) throw StructureFailure()
    }

    private fun validateMethod(method: Int) {
        if (method != METHOD_STORED && method != METHOD_DEFLATED) throw StructureFailure()
    }

    private fun rejectZip64Extra(extra: ByteArray) {
        var offset = 0
        while (offset < extra.size) {
            if (offset > extra.size - 4) throw StructureFailure()
            val id = leU2(extra, offset)
            val size = leU2(extra, offset + 2)
            offset += 4
            if (offset > extra.size - size) throw StructureFailure()
            if (id == ZIP64_EXTRA_ID) throw StructureFailure()
            offset += size
        }
    }

    private data class Eocd(val entryCount: Int, val centralOffset: Long, val centralSize: Long)

    companion object {
        private const val EOCD_SIGNATURE = 0x06054b50L
        private const val ZIP64_LOCATOR_SIGNATURE = 0x07064b50L
        private const val CENTRAL_SIGNATURE = 0x02014b50L
        private const val LOCAL_SIGNATURE = 0x04034b50L
        private const val EOCD_MIN_SIZE = 22
        private const val ZIP64_LOCATOR_SIZE = 20L
        private const val CENTRAL_FIXED_SIZE = 46
        private const val LOCAL_FIXED_SIZE = 30
        private const val MAX_COMMENT = 65_535
        private const val UINT32_MAX = 0xffff_ffffL
        private const val ZIP64_EXTRA_ID = 0x0001
        private const val METHOD_STORED = 0
        private const val METHOD_DEFLATED = 8
        private const val DATA_DESCRIPTOR_FLAG = 0x0008
        private const val DATA_DESCRIPTOR_SIGNATURE = 0x08074b50L
        private const val DATA_DESCRIPTOR_MIN_SIZE = 12L
        private const val DATA_DESCRIPTOR_SIGNED_SIZE = 16L
        private const val UNSUPPORTED_FLAGS = 0x2061
        private const val IO_CHUNK = 64 * 1024
    }
}

internal fun canonicalDexOrdinal(name: String): Int? {
    if (name == "classes.dex") return 1
    if (!name.startsWith("classes") || !name.endsWith(".dex")) return null
    val number = name.substring(7, name.length - 4)
    if (number.isEmpty() || number[0] == '0' || number.any { !it.isDigit() }) return null
    val ordinal = number.toIntOrNull() ?: return null
    return if (ordinal in 2..InspectionLimits.MAX_DEX_ENTRIES) ordinal else null
}
