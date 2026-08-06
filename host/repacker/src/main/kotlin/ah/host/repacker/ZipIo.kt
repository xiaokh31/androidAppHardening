package ah.host.repacker

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.text.Normalizer
import java.util.LinkedHashSet
import java.util.zip.CRC32
import java.util.zip.DataFormatException
import java.util.zip.Deflater
import java.util.zip.Inflater

internal data class RawZipEntry(
    val index: Int,
    val name: String,
    val nameBytes: ByteArray,
    val versionMadeBy: Int,
    val versionNeeded: Int,
    val flags: Int,
    val method: Int,
    val modTime: Int,
    val modDate: Int,
    val crc32: Long,
    val compressedSize: Long,
    val uncompressedSize: Long,
    val internalAttributes: Int,
    val externalAttributes: Long,
    val localHeaderOffset: Long,
    val dataOffset: Long,
)

internal class RawZipArchive private constructor(
    val path: Path,
    private val channel: FileChannel,
    val entries: List<RawZipEntry>,
    val centralOffset: Long,
    val fileSize: Long,
) : AutoCloseable {
    fun fileSha256(): ByteArray {
        if (channel.size() != fileSize) packageFailure(PackageErrorCode.OUTPUT_INPUT_CHANGED, "inputSize")
        return digestRange(0, fileSize)
    }

    fun compressedSha256(entry: RawZipEntry): ByteArray = digestRange(entry.dataOffset, entry.compressedSize)

    fun uncompressedSha256(entry: RawZipEntry): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        val crc = CRC32()
        var written = 0L
        fun consume(bytes: ByteArray, count: Int) {
            digest.update(bytes, 0, count)
            crc.update(bytes, 0, count)
            written = checkedAdd(written, count.toLong())
            if (written > entry.uncompressedSize) zipFailure("entrySize")
        }
        when (entry.method) {
            METHOD_STORED -> {
                if (entry.compressedSize != entry.uncompressedSize) zipFailure("storedSize")
                transferRange(entry.dataOffset, entry.compressedSize) { bytes, count -> consume(bytes, count) }
            }
            METHOD_DEFLATED -> inflate(entry, ::consume)
            else -> zipFailure("method")
        }
        if (written != entry.uncompressedSize || crc.value != entry.crc32) zipFailure("entryCrc")
        return digest.digest()
    }

    fun readUncompressed(
        entry: RawZipEntry,
        limit: Int,
        onAllocated: () -> Unit = {},
        onFailureCleared: (Boolean) -> Unit = {},
    ): ByteArray {
        if (entry.uncompressedSize > limit.toLong()) zipFailure("materializeLimit")
        var owner: ByteArray? = ByteArray(entry.uncompressedSize.toInt())
        try {
            val result = requireNotNull(owner)
            onAllocated()
            var offset = 0
            fun consume(bytes: ByteArray, count: Int) {
                if (count < 0 || offset > result.size - count) zipFailure("materializeSize")
                bytes.copyInto(result, offset, 0, count)
                offset += count
            }
            when (entry.method) {
                METHOD_STORED -> {
                    if (entry.compressedSize != entry.uncompressedSize) zipFailure("storedSize")
                    transferRange(entry.dataOffset, entry.compressedSize, ::consume)
                }
                METHOD_DEFLATED -> inflate(entry, ::consume)
                else -> zipFailure("method")
            }
            if (offset.toLong() != entry.uncompressedSize) zipFailure("materializeSize")
            owner = null
            return result
        } finally {
            owner?.let { bytes ->
                bytes.fill(0)
                try { onFailureCleared(bytes.all { it == 0.toByte() }) } catch (_: Throwable) { /* cleanup wins */ }
            }
        }
    }

    fun uncompressedPrefix(entry: RawZipEntry, count: Int): ByteArray {
        if (count < 0) zipFailure("prefixCount")
        val wanted = minOf(entry.uncompressedSize, count.toLong()).toInt()
        if (wanted == 0) return ByteArray(0)
        return when (entry.method) {
            METHOD_STORED -> readBytes(channel, entry.dataOffset, wanted)
            METHOD_DEFLATED -> {
                val inflater = Inflater(true)
                val input = ByteArray(IO_BUFFER_BYTES)
                val output = ByteArray(wanted)
                var fed = 0L
                var written = 0
                try {
                    while (written < wanted) {
                        if (inflater.needsDictionary() || inflater.finished()) zipFailure("deflatePrefix")
                        if (inflater.needsInput()) {
                            if (fed >= entry.compressedSize) zipFailure("deflatePrefix")
                            val read = minOf(input.size.toLong(), entry.compressedSize - fed).toInt()
                            readInto(channel, entry.dataOffset + fed, input, read)
                            inflater.setInput(input, 0, read)
                            fed += read
                        }
                        val produced = try {
                            inflater.inflate(output, written, wanted - written)
                        } catch (_: DataFormatException) {
                            zipFailure("deflatePrefix")
                        }
                        if (produced > 0) written += produced else if (!inflater.needsInput()) zipFailure("deflatePrefix")
                    }
                    output
                } finally {
                    inflater.end()
                    input.fill(0)
                }
            }
            else -> zipFailure("method")
        }
    }

    fun copyCompressed(entry: RawZipEntry, sink: (ByteArray, Int) -> Unit) {
        transferRange(entry.dataOffset, entry.compressedSize, sink)
    }

    private fun digestRange(offset: Long, size: Long): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        transferRange(offset, size) { bytes, count -> digest.update(bytes, 0, count) }
        return digest.digest()
    }

    private fun transferRange(offset: Long, size: Long, sink: (ByteArray, Int) -> Unit) {
        val buffer = ByteArray(IO_BUFFER_BYTES)
        try {
            var position = offset
            var remaining = size
            while (remaining > 0L) {
                val count = minOf(buffer.size.toLong(), remaining).toInt()
                readInto(channel, position, buffer, count)
                sink(buffer, count)
                position += count
                remaining -= count
            }
        } finally {
            buffer.fill(0)
        }
    }

    private fun inflate(entry: RawZipEntry, sink: (ByteArray, Int) -> Unit) {
        val inflater = Inflater(true)
        val input = ByteArray(IO_BUFFER_BYTES)
        val output = ByteArray(IO_BUFFER_BYTES)
        var fed = 0L
        try {
            while (!inflater.finished()) {
                if (inflater.needsDictionary()) zipFailure("deflateDictionary")
                if (inflater.needsInput()) {
                    if (fed >= entry.compressedSize) zipFailure("deflateTruncated")
                    val count = minOf(input.size.toLong(), entry.compressedSize - fed).toInt()
                    readInto(channel, entry.dataOffset + fed, input, count)
                    inflater.setInput(input, 0, count)
                    fed += count
                }
                val count = try {
                    inflater.inflate(output)
                } catch (_: DataFormatException) {
                    zipFailure("deflateFormat")
                }
                if (count > 0) sink(output, count) else if (!inflater.needsInput() && !inflater.finished()) zipFailure("deflateStall")
            }
            if (fed - inflater.remaining != entry.compressedSize) zipFailure("deflateTrailing")
        } finally {
            inflater.end()
            input.fill(0)
            output.fill(0)
        }
    }

    override fun close() = channel.close()

    companion object {
        fun open(path: Path, requirePackedLayout: Boolean = false): RawZipArchive {
            val normalized = path.toAbsolutePath().normalize()
            val channel = try {
                FileChannel.open(normalized, StandardOpenOption.READ)
            } catch (_: IOException) {
                throw PackageException(PackageErrorCode.OUTPUT_VERIFICATION_FAILED, "zipOpen")
            }
            try {
                val size = channel.size()
                if (size < EOCD_FIXED_BYTES || size > UINT32_MAX) zipFailure("zipSize")
                val eocd = findEocd(channel, size)
                val entries = parseCentral(channel, eocd)
                validateLocalRanges(entries, eocd.centralOffset, requirePackedLayout)
                return RawZipArchive(normalized, channel, entries, eocd.centralOffset, size)
            } catch (failure: Throwable) {
                try {
                    channel.close()
                } catch (_: Throwable) {
                    // Close is best-effort while preserving the primary structural failure.
                }
                throw failure
            }
        }

        private fun findEocd(channel: FileChannel, size: Long): Eocd {
            val tailSize = minOf(size, EOCD_FIXED_BYTES + MAX_ZIP_COMMENT).toInt()
            val tailOffset = size - tailSize
            val tail = readBytes(channel, tailOffset, tailSize)
            var found: Eocd? = null
            for (offset in tail.size - EOCD_FIXED_BYTES downTo 0) {
                if (leU4(tail, offset) != EOCD_SIGNATURE) continue
                val commentLength = leU2(tail, offset + 20)
                if (offset + EOCD_FIXED_BYTES + commentLength != tail.size) continue
                if (found != null) zipFailure("multipleEocd")
                val disk = leU2(tail, offset + 4)
                val centralDisk = leU2(tail, offset + 6)
                val diskEntries = leU2(tail, offset + 8)
                val entryCount = leU2(tail, offset + 10)
                val centralSize = leU4(tail, offset + 12)
                val centralOffset = leU4(tail, offset + 16)
                if (disk != 0 || centralDisk != 0 || diskEntries != entryCount) zipFailure("multiDisk")
                if (centralSize == UINT32_MAX || centralOffset == UINT32_MAX) zipFailure("zip64")
                if (checkedAdd(centralOffset, centralSize) != tailOffset + offset) zipFailure("centralRange")
                found = Eocd(entryCount, centralOffset, centralSize)
            }
            return found ?: zipFailure("eocd")
        }

        private fun parseCentral(channel: FileChannel, eocd: Eocd): List<RawZipEntry> {
            val result = ArrayList<RawZipEntry>(eocd.entryCount)
            val normalizedNames = LinkedHashSet<String>()
            var cursor = eocd.centralOffset
            val end = checkedAdd(eocd.centralOffset, eocd.centralSize)
            repeat(eocd.entryCount) { index ->
                if (cursor > end - CENTRAL_FIXED_BYTES) zipFailure("centralHeader")
                val header = readBytes(channel, cursor, CENTRAL_FIXED_BYTES)
                if (leU4(header, 0) != CENTRAL_SIGNATURE) zipFailure("centralMagic")
                val madeBy = leU2(header, 4)
                val needed = leU2(header, 6)
                val flags = leU2(header, 8)
                validateFlags(flags)
                val method = leU2(header, 10)
                if (method != METHOD_STORED && method != METHOD_DEFLATED) zipFailure("method")
                val time = leU2(header, 12)
                val date = leU2(header, 14)
                val crc = leU4(header, 16)
                val compressed = leU4(header, 20)
                val uncompressed = leU4(header, 24)
                val nameLength = leU2(header, 28)
                val extraLength = leU2(header, 30)
                val commentLength = leU2(header, 32)
                val disk = leU2(header, 34)
                val internal = leU2(header, 36)
                val external = leU4(header, 38)
                val localOffset = leU4(header, 42)
                if (disk != 0 || nameLength == 0 || compressed == UINT32_MAX || uncompressed == UINT32_MAX || localOffset == UINT32_MAX) {
                    zipFailure("centralField")
                }
                val variable = checkedAdd(nameLength.toLong(), checkedAdd(extraLength.toLong(), commentLength.toLong()))
                val next = checkedAdd(cursor, checkedAdd(CENTRAL_FIXED_BYTES.toLong(), variable))
                if (next > end) zipFailure("centralVariable")
                val nameBytes = readBytes(channel, cursor + CENTRAL_FIXED_BYTES, nameLength)
                val name = decodeName(nameBytes, flags)
                val normalized = validateName(name)
                if (!normalizedNames.add(normalized)) zipFailure("duplicateName")
                val local = parseLocal(channel, localOffset, nameBytes, flags, method, crc, compressed, uncompressed, eocd.centralOffset)
                result += RawZipEntry(
                    index,
                    name,
                    nameBytes,
                    madeBy,
                    needed,
                    flags,
                    method,
                    time,
                    date,
                    crc,
                    compressed,
                    uncompressed,
                    internal,
                    external,
                    localOffset,
                    local.dataOffset,
                )
                cursor = next
            }
            if (cursor != end) zipFailure("centralConsumption")
            return result
        }

        private fun parseLocal(
            channel: FileChannel,
            offset: Long,
            expectedName: ByteArray,
            expectedFlags: Int,
            expectedMethod: Int,
            expectedCrc: Long,
            expectedCompressed: Long,
            expectedUncompressed: Long,
            centralOffset: Long,
        ): LocalInfo {
            if (offset > centralOffset - LOCAL_FIXED_BYTES) zipFailure("localRange")
            val header = readBytes(channel, offset, LOCAL_FIXED_BYTES)
            if (leU4(header, 0) != LOCAL_SIGNATURE) zipFailure("localMagic")
            val flags = leU2(header, 6)
            val method = leU2(header, 8)
            val crc = leU4(header, 14)
            val compressed = leU4(header, 18)
            val uncompressed = leU4(header, 22)
            val nameLength = leU2(header, 26)
            val extraLength = leU2(header, 28)
            if (flags != expectedFlags || method != expectedMethod || nameLength != expectedName.size) zipFailure("localMismatch")
            val dataOffset = checkedAdd(offset, LOCAL_FIXED_BYTES.toLong() + nameLength + extraLength)
            if (dataOffset > centralOffset || !readBytes(channel, offset + LOCAL_FIXED_BYTES, nameLength).contentEquals(expectedName)) {
                zipFailure("localName")
            }
            if (flags and DATA_DESCRIPTOR_FLAG == 0 &&
                (crc != expectedCrc || compressed != expectedCompressed || uncompressed != expectedUncompressed)
            ) {
                zipFailure("localSizes")
            }
            if (checkedAdd(dataOffset, expectedCompressed) > centralOffset) zipFailure("entryRange")
            return LocalInfo(dataOffset)
        }

        private fun validateLocalRanges(entries: List<RawZipEntry>, centralOffset: Long, packed: Boolean) {
            val sorted = entries.sortedBy(RawZipEntry::localHeaderOffset)
            var previousEnd = 0L
            for (entry in sorted) {
                val end = checkedAdd(entry.dataOffset, entry.compressedSize)
                if (entry.localHeaderOffset < previousEnd || end > centralOffset) zipFailure("localOverlap")
                if (packed && entry.localHeaderOffset != previousEnd) zipFailure("localGap")
                if (packed && entry.flags and DATA_DESCRIPTOR_FLAG != 0) zipFailure("descriptor")
                previousEnd = end
            }
            if (packed && previousEnd != centralOffset) zipFailure("signingBlockOrGap")
        }

        private fun validateFlags(flags: Int) {
            if (flags and ENCRYPTED_FLAG != 0 || flags and ALLOWED_FLAGS.inv() != 0) zipFailure("flags")
        }

        private fun decodeName(bytes: ByteArray, flags: Int): String {
            val charset = if (flags and UTF8_FLAG != 0) StandardCharsets.UTF_8 else StandardCharsets.US_ASCII
            return try {
                charset.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString()
            } catch (_: RuntimeException) {
                zipFailure("nameEncoding")
            } catch (_: java.nio.charset.CharacterCodingException) {
                zipFailure("nameEncoding")
            }
        }

        private fun validateName(name: String): String {
            if (name.isEmpty() || name.startsWith('/') || name.startsWith('\\') || name.contains('\\') || name.indexOf('\u0000') >= 0) {
                zipFailure("name")
            }
            val segments = name.split('/')
            segments.forEachIndexed { index, segment ->
                if (segment == "." || segment == ".." || (segment.isEmpty() && index != segments.lastIndex)) {
                    zipFailure("name")
                }
            }
            return Normalizer.normalize(name, Normalizer.Form.NFC)
        }
    }
}

internal sealed interface EntryPayload {
    fun writeTo(writer: AlignedZipWriter)
}

internal class RawEntryPayload(
    private val archive: RawZipArchive,
    private val entry: RawZipEntry,
) : EntryPayload {
    override fun writeTo(writer: AlignedZipWriter) = archive.copyCompressed(entry, writer::writePayload)
}

internal class BytesEntryPayload(private val bytes: ByteArray) : EntryPayload {
    override fun writeTo(writer: AlignedZipWriter) = writer.writePayload(bytes, bytes.size)
    fun clear() = bytes.fill(0)
    fun isCleared(): Boolean = bytes.all { it == 0.toByte() }
}

internal class FileEntryPayload(private val path: Path, private val size: Long) : EntryPayload {
    override fun writeTo(writer: AlignedZipWriter) {
        FileChannel.open(path, StandardOpenOption.READ).use { source ->
            val buffer = ByteArray(IO_BUFFER_BYTES)
            var position = 0L
            while (position < size) {
                val count = minOf(buffer.size.toLong(), size - position).toInt()
                readInto(source, position, buffer, count)
                writer.writePayload(buffer, count)
                position += count
            }
        }
    }
}

internal data class PlannedZipEntry(
    val expected: ExpectedEntry,
    val flags: Int,
    val modTime: Int,
    val modDate: Int,
    val versionMadeBy: Int,
    val versionNeeded: Int,
    val internalAttributes: Int,
    val externalAttributes: Long,
    val payload: EntryPayload,
)

internal interface PackageFaults {
    fun allowedWrite(position: Long, requested: Int): Int = requested
    fun afterCandidateClosed(candidate: Path) = Unit
    fun beforeAtomicMove(candidate: Path, output: Path) = Unit
    fun beforePublication(candidate: Path, output: Path) = Unit
    fun beforeWriterClose() = Unit
    fun afterSensitiveCopy(label: String) = Unit
    fun afterVerifierRuntimeRead() = Unit
    fun sensitiveCleared(label: String, cleared: Boolean) = Unit
}

internal object NO_PACKAGE_FAULTS : PackageFaults

internal class AlignedZipWriter(
    output: Path,
    private val faults: PackageFaults,
) : AutoCloseable {
    private val channel = FileChannel.open(output, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)
    private val central = ArrayList<CentralRecord>()
    private var closed = false

    fun writeEntry(entry: PlannedZipEntry) {
        val name = entry.expected.name.toByteArray(StandardCharsets.UTF_8)
        if (name.isEmpty() || name.size > UINT16_MAX) packageFailure(PackageErrorCode.PACKAGE_ENTRY_CONFLICT, "entryName")
        val localOffset = channel.position()
        requireU32(localOffset, "localOffset")
        val extra = alignmentExtra(localOffset, name.size, entry.expected.alignment)
        val local = ByteArray(LOCAL_FIXED_BYTES)
        putU4(local, 0, LOCAL_SIGNATURE)
        putU2(local, 4, maxOf(20, entry.versionNeeded))
        putU2(local, 6, entry.flags and DATA_DESCRIPTOR_FLAG.inv())
        putU2(local, 8, entry.expected.method)
        putU2(local, 10, entry.modTime)
        putU2(local, 12, entry.modDate)
        putU4(local, 14, entry.expected.crc32)
        putU4(local, 18, entry.expected.compressedSize)
        putU4(local, 22, entry.expected.uncompressedSize)
        putU2(local, 26, name.size)
        putU2(local, 28, extra.size)
        writeBytes(local, local.size)
        writeBytes(name, name.size)
        writeBytes(extra, extra.size)
        val dataOffset = channel.position()
        if (dataOffset % entry.expected.alignment != 0L) packageFailure(PackageErrorCode.PACKAGE_ALIGNMENT, "entryAlignment")
        val before = channel.position()
        entry.payload.writeTo(this)
        if (channel.position() - before != entry.expected.compressedSize) {
            packageFailure(PackageErrorCode.PACKAGE_WRITE_FAILED, "payloadSize")
        }
        central += CentralRecord(entry, name, localOffset)
    }

    fun finish() {
        val centralOffset = channel.position()
        central.forEach(::writeCentral)
        val centralSize = channel.position() - centralOffset
        requireU32(centralOffset, "centralOffset")
        requireU32(centralSize, "centralSize")
        if (central.size > UINT16_MAX) packageFailure(PackageErrorCode.PACKAGE_WRITE_FAILED, "entryCount")
        val eocd = ByteArray(EOCD_FIXED_BYTES)
        putU4(eocd, 0, EOCD_SIGNATURE)
        putU2(eocd, 4, 0)
        putU2(eocd, 6, 0)
        putU2(eocd, 8, central.size)
        putU2(eocd, 10, central.size)
        putU4(eocd, 12, centralSize)
        putU4(eocd, 16, centralOffset)
        putU2(eocd, 20, 0)
        writeBytes(eocd, eocd.size)
        channel.force(true)
    }

    internal fun writePayload(bytes: ByteArray, count: Int) = writeBytes(bytes, count)

    private fun writeCentral(record: CentralRecord) {
        val entry = record.entry
        val header = ByteArray(CENTRAL_FIXED_BYTES)
        putU4(header, 0, CENTRAL_SIGNATURE)
        putU2(header, 4, entry.versionMadeBy)
        putU2(header, 6, maxOf(20, entry.versionNeeded))
        putU2(header, 8, entry.flags and DATA_DESCRIPTOR_FLAG.inv())
        putU2(header, 10, entry.expected.method)
        putU2(header, 12, entry.modTime)
        putU2(header, 14, entry.modDate)
        putU4(header, 16, entry.expected.crc32)
        putU4(header, 20, entry.expected.compressedSize)
        putU4(header, 24, entry.expected.uncompressedSize)
        putU2(header, 28, record.name.size)
        putU2(header, 30, 0)
        putU2(header, 32, 0)
        putU2(header, 34, 0)
        putU2(header, 36, entry.internalAttributes)
        putU4(header, 38, entry.externalAttributes)
        putU4(header, 42, record.localOffset)
        writeBytes(header, header.size)
        writeBytes(record.name, record.name.size)
    }

    private fun alignmentExtra(localOffset: Long, nameLength: Int, alignment: Int): ByteArray {
        require(alignment > 0 && alignment and (alignment - 1) == 0) { "alignment must be a power of two" }
        if (alignment == 1) return ByteArray(0)
        val base = checkedAdd(localOffset, LOCAL_FIXED_BYTES.toLong() + nameLength)
        var total = ((alignment - (base % alignment).toInt()) % alignment)
        while (total in 1 until ALIGNMENT_FIELD_MIN) total += alignment
        if (total == 0) return ByteArray(0)
        if (total > UINT16_MAX) packageFailure(PackageErrorCode.PACKAGE_ALIGNMENT, "extraLength")
        val extra = ByteArray(total)
        putU2(extra, 0, ALIGNMENT_EXTRA_ID)
        putU2(extra, 2, total - 4)
        putU2(extra, 4, alignment)
        return extra
    }

    private fun writeBytes(bytes: ByteArray, count: Int) {
        if (count == 0) return
        var offset = 0
        while (offset < count) {
            val requested = count - offset
            val allowed = faults.allowedWrite(channel.position(), requested)
            if (allowed <= 0 || allowed > requested) packageFailure(PackageErrorCode.PACKAGE_WRITE_FAILED, "shortWrite")
            val buffer = ByteBuffer.wrap(bytes, offset, allowed)
            while (buffer.hasRemaining()) {
                val wrote = channel.write(buffer)
                if (wrote <= 0) packageFailure(PackageErrorCode.PACKAGE_WRITE_FAILED, "shortWrite")
            }
            offset += allowed
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        var primary: Throwable? = null
        try {
            faults.beforeWriterClose()
        } catch (failure: Throwable) {
            primary = failure
        }
        try {
            channel.close()
        } catch (failure: Throwable) {
            if (primary == null) primary = failure else try {
                primary.addSuppressed(failure)
            } catch (_: Throwable) {
                // Preserve the first close failure even if suppression itself fails.
            }
        }
        if (primary != null) throw primary
    }

    private data class CentralRecord(val entry: PlannedZipEntry, val name: ByteArray, val localOffset: Long)
}

internal fun deflateRaw(bytes: ByteArray): ByteArray {
    val deflater = Deflater(9, true)
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(IO_BUFFER_BYTES)
    return try {
        deflater.setInput(bytes)
        deflater.finish()
        while (!deflater.finished()) {
            val count = deflater.deflate(buffer)
            if (count <= 0) zipFailure("deflateStall")
            output.write(buffer, 0, count)
        }
        output.toByteArray()
    } finally {
        deflater.end()
        buffer.fill(0)
    }
}

internal fun crc32(bytes: ByteArray): Long = CRC32().apply { update(bytes) }.value

internal fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

internal fun sha256(path: Path): ByteArray {
    val digest = MessageDigest.getInstance("SHA-256")
    FileChannel.open(path, StandardOpenOption.READ).use { channel ->
        val buffer = ByteArray(IO_BUFFER_BYTES)
        var position = 0L
        val size = channel.size()
        while (position < size) {
            val count = minOf(buffer.size.toLong(), size - position).toInt()
            readInto(channel, position, buffer, count)
            digest.update(buffer, 0, count)
            position += count
        }
    }
    return digest.digest()
}

internal fun crc32(path: Path): Long {
    val crc = CRC32()
    FileChannel.open(path, StandardOpenOption.READ).use { channel ->
        val buffer = ByteArray(IO_BUFFER_BYTES)
        var position = 0L
        val size = channel.size()
        while (position < size) {
            val count = minOf(buffer.size.toLong(), size - position).toInt()
            readInto(channel, position, buffer, count)
            crc.update(buffer, 0, count)
            position += count
        }
    }
    return crc.value
}

internal fun readBytes(channel: FileChannel, offset: Long, size: Int): ByteArray {
    val result = ByteArray(size)
    readInto(channel, offset, result, size)
    return result
}

internal fun readInto(channel: FileChannel, offset: Long, destination: ByteArray, count: Int) {
    var position = offset
    var written = 0
    while (written < count) {
        val buffer = ByteBuffer.wrap(destination, written, count - written)
        val read = channel.read(buffer, position)
        if (read <= 0) zipFailure("shortRead")
        written += read
        position += read
    }
}

internal fun checkedAdd(left: Long, right: Long): Long {
    if (left < 0L || right < 0L || left > Long.MAX_VALUE - right) zipFailure("overflow")
    return left + right
}

internal fun leU2(bytes: ByteArray, offset: Int): Int {
    if (offset < 0 || offset > bytes.size - 2) zipFailure("u2")
    return (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8)
}

internal fun leU4(bytes: ByteArray, offset: Int): Long {
    if (offset < 0 || offset > bytes.size - 4) zipFailure("u4")
    return (bytes[offset].toLong() and 0xff) or
        ((bytes[offset + 1].toLong() and 0xff) shl 8) or
        ((bytes[offset + 2].toLong() and 0xff) shl 16) or
        ((bytes[offset + 3].toLong() and 0xff) shl 24)
}

internal fun putU2(bytes: ByteArray, offset: Int, value: Int) {
    if (value !in 0..UINT16_MAX || offset < 0 || offset > bytes.size - 2) packageFailure(PackageErrorCode.PACKAGE_WRITE_FAILED, "u2")
    bytes[offset] = value.toByte()
    bytes[offset + 1] = (value ushr 8).toByte()
}

internal fun putU4(bytes: ByteArray, offset: Int, value: Long) {
    requireU32(value, "u4")
    if (offset < 0 || offset > bytes.size - 4) packageFailure(PackageErrorCode.PACKAGE_WRITE_FAILED, "u4")
    for (index in 0 until 4) bytes[offset + index] = (value ushr (index * 8)).toByte()
}

internal fun requireU32(value: Long, field: String) {
    if (value !in 0 until UINT32_MAX) packageFailure(PackageErrorCode.PACKAGE_WRITE_FAILED, field)
}

internal fun zipFailure(field: String): Nothing =
    throw PackageException(PackageErrorCode.OUTPUT_VERIFICATION_FAILED, field)

internal fun packageFailure(code: PackageErrorCode, field: String? = null): Nothing = throw PackageException(code, field)

private data class Eocd(val entryCount: Int, val centralOffset: Long, val centralSize: Long)
private data class LocalInfo(val dataOffset: Long)

internal const val LOCAL_SIGNATURE: Long = 0x04034b50L
internal const val CENTRAL_SIGNATURE: Long = 0x02014b50L
internal const val EOCD_SIGNATURE: Long = 0x06054b50L
internal const val LOCAL_FIXED_BYTES: Int = 30
internal const val CENTRAL_FIXED_BYTES: Int = 46
internal const val EOCD_FIXED_BYTES: Int = 22
internal const val MAX_ZIP_COMMENT: Long = 65_535L
internal const val UINT16_MAX: Int = 65_535
internal const val UINT32_MAX: Long = 0xffff_ffffL
internal const val UTF8_FLAG: Int = 0x0800
internal const val DATA_DESCRIPTOR_FLAG: Int = 0x0008
internal const val ENCRYPTED_FLAG: Int = 0x0001
internal const val ALLOWED_FLAGS: Int = UTF8_FLAG or DATA_DESCRIPTOR_FLAG or 0x0006
internal const val ALIGNMENT_EXTRA_ID: Int = 0xd935
internal const val ALIGNMENT_FIELD_MIN: Int = 6
internal const val IO_BUFFER_BYTES: Int = 64 * 1024
internal const val FIXED_DOS_TIME: Int = 0
internal const val FIXED_DOS_DATE: Int = 0x0021
