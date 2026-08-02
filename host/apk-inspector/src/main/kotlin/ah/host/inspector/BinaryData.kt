package ah.host.inspector

import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.zip.Adler32

internal class FileSource(private val channel: FileChannel) {
    val size: Long = channel.size()
    private var snapshotHashes: List<ByteArray>? = null
    private val cachedBlocks = object : LinkedHashMap<Int, ByteArray>(SNAPSHOT_CACHE_BLOCKS, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, ByteArray>?): Boolean =
            size > SNAPSHOT_CACHE_BLOCKS
    }

    fun captureSnapshot(): ByteArray {
        if (snapshotHashes != null) throw StructureFailure()
        val fullDigest = MessageDigest.getInstance("SHA-256")
        val hashes = ArrayList<ByteArray>(blockCount())
        repeat(blockCount()) { index ->
            val block = readRawBlock(index)
            fullDigest.update(block)
            hashes += MessageDigest.getInstance("SHA-256").digest(block)
        }
        snapshotHashes = immutableList(hashes)
        return fullDigest.digest()
    }

    fun currentDigest(): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        repeat(blockCount()) { index -> digest.update(readRawBlock(index)) }
        return digest.digest()
    }

    fun readFully(position: Long, length: Int): ByteArray {
        if (position < 0L || length < 0 || position > size - length.toLong()) {
            throw StructureFailure()
        }
        val result = ByteArray(length)
        readInto(position, result, 0, length)
        return result
    }

    fun readInto(position: Long, target: ByteArray, offset: Int, length: Int) {
        if (position < 0L || offset < 0 || length < 0 || offset > target.size - length || position > size - length) {
            throw StructureFailure()
        }
        if (snapshotHashes == null) throw StructureFailure()
        var sourcePosition = position
        var targetOffset = offset
        var remaining = length
        while (remaining > 0) {
            interrupted()
            val blockIndex = (sourcePosition / SNAPSHOT_BLOCK_SIZE).toInt()
            val inBlock = (sourcePosition % SNAPSHOT_BLOCK_SIZE).toInt()
            val block = verifiedBlock(blockIndex)
            val count = minOf(remaining, block.size - inBlock)
            block.copyInto(target, targetOffset, inBlock, inBlock + count)
            sourcePosition += count
            targetOffset += count
            remaining -= count
        }
    }

    private fun verifiedBlock(index: Int): ByteArray = cachedBlocks[index] ?: run {
        val block = readRawBlock(index)
        val expected = snapshotHashes?.getOrNull(index) ?: throw StructureFailure()
        val actual = MessageDigest.getInstance("SHA-256").digest(block)
        if (!MessageDigest.isEqual(expected, actual)) throw InputChangedFailure()
        cachedBlocks[index] = block
        block
    }

    private fun readRawBlock(index: Int): ByteArray {
        val position = index.toLong() * SNAPSHOT_BLOCK_SIZE
        val length = minOf(SNAPSHOT_BLOCK_SIZE.toLong(), size - position).toInt()
        if (index < 0 || length <= 0) throw StructureFailure()
        val result = ByteArray(length)
        val buffer = ByteBuffer.wrap(result)
        var offset = position
        while (buffer.hasRemaining()) {
            val read = channel.read(buffer, offset)
            if (read <= 0) throw InputChangedFailure()
            offset = checkedAdd(offset, read.toLong())
        }
        return result
    }

    private fun blockCount(): Int = ((size + SNAPSHOT_BLOCK_SIZE - 1L) / SNAPSHOT_BLOCK_SIZE).toInt()

    companion object {
        private const val SNAPSHOT_BLOCK_SIZE = 64 * 1024
        private const val SNAPSHOT_CACHE_BLOCKS = 4
    }
}

internal class SegmentedBytes private constructor(
    private val chunks: List<ByteArray>,
    val size: Int,
) {
    fun u1(offset: Int): Int {
        requireRange(offset, 1)
        return chunks[offset / CHUNK_SIZE][offset % CHUNK_SIZE].toInt() and 0xff
    }

    fun u2(offset: Int): Int = u1(offset) or (u1(offset + 1) shl 8)

    fun u4(offset: Int): Long =
        u1(offset).toLong() or
            (u1(offset + 1).toLong() shl 8) or
            (u1(offset + 2).toLong() shl 16) or
            (u1(offset + 3).toLong() shl 24)

    fun copy(offset: Int, length: Int): ByteArray {
        requireRange(offset, length)
        val result = ByteArray(length)
        var sourceOffset = offset
        var targetOffset = 0
        while (targetOffset < length) {
            val chunkIndex = sourceOffset / CHUNK_SIZE
            val inChunk = sourceOffset % CHUNK_SIZE
            val count = minOf(length - targetOffset, chunks[chunkIndex].size - inChunk)
            chunks[chunkIndex].copyInto(result, targetOffset, inChunk, inChunk + count)
            sourceOffset += count
            targetOffset += count
        }
        return result
    }

    fun digest(algorithm: String, offset: Int = 0, length: Int = size): ByteArray {
        requireRange(offset, length)
        val digest = MessageDigest.getInstance(algorithm)
        visit(offset, length) { bytes, start, count -> digest.update(bytes, start, count) }
        return digest.digest()
    }

    fun adler32(offset: Int, length: Int): Long {
        requireRange(offset, length)
        val adler = Adler32()
        visit(offset, length) { bytes, start, count -> adler.update(bytes, start, count) }
        return adler.value
    }

    fun strictUtf8(offset: Int, length: Int): String = strictDecode(copy(offset, length), StandardCharsets.UTF_8)

    fun strictUtf16Le(offset: Int, byteLength: Int): String =
        strictDecode(copy(offset, byteLength), StandardCharsets.UTF_16LE)

    private fun visit(offset: Int, length: Int, visitor: (ByteArray, Int, Int) -> Unit) {
        var current = offset
        var remaining = length
        while (remaining > 0) {
            val chunk = chunks[current / CHUNK_SIZE]
            val inChunk = current % CHUNK_SIZE
            val count = minOf(remaining, chunk.size - inChunk)
            visitor(chunk, inChunk, count)
            current += count
            remaining -= count
        }
    }

    private fun requireRange(offset: Int, length: Int) {
        if (offset < 0 || length < 0 || offset > size - length) throw DataFailure()
    }

    companion object {
        private const val CHUNK_SIZE = 64 * 1024

        fun fromChunks(chunks: List<ByteArray>, size: Long): SegmentedBytes {
            if (size < 0L || size > Int.MAX_VALUE) throw LimitFailure("materializedBytes")
            return SegmentedBytes(immutableList(chunks), size.toInt())
        }
    }
}

internal fun strictUtf8(bytes: ByteArray): String = strictDecode(bytes, StandardCharsets.UTF_8)

private fun strictDecode(bytes: ByteArray, charset: java.nio.charset.Charset): String = try {
    charset.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes))
        .toString()
} catch (_: CharacterCodingException) {
    throw DataFailure()
}

internal fun leU2(bytes: ByteArray, offset: Int): Int {
    if (offset < 0 || offset > bytes.size - 2) throw StructureFailure()
    return bytes[offset].toInt() and 0xff or ((bytes[offset + 1].toInt() and 0xff) shl 8)
}

internal fun leU4(bytes: ByteArray, offset: Int): Long {
    if (offset < 0 || offset > bytes.size - 4) throw StructureFailure()
    return bytes[offset].toLong() and 0xff or
        ((bytes[offset + 1].toLong() and 0xff) shl 8) or
        ((bytes[offset + 2].toLong() and 0xff) shl 16) or
        ((bytes[offset + 3].toLong() and 0xff) shl 24)
}

internal fun checkedAdd(left: Long, right: Long): Long = try {
    Math.addExact(left, right)
} catch (_: ArithmeticException) {
    throw StructureFailure()
}

internal fun checkedMultiply(left: Long, right: Long): Long = try {
    Math.multiplyExact(left, right)
} catch (_: ArithmeticException) {
    throw StructureFailure()
}

internal fun interrupted() {
    if (Thread.currentThread().isInterrupted) throw InterruptedFailure()
}

internal open class ParserFailure : RuntimeException()
internal class StructureFailure : ParserFailure()
internal class DataFailure : ParserFailure()
internal class LimitFailure(val limit: String) : ParserFailure()
internal class DuplicateFailure : ParserFailure()
internal class PathFailure : ParserFailure()
internal class ManifestFailure : ParserFailure()
internal class DexFailure : ParserFailure()
internal class InterruptedFailure : ParserFailure()
internal class InputChangedFailure : ParserFailure()
