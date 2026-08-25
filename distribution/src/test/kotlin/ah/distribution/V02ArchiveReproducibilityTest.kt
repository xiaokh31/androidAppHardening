package ah.distribution

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.Inflater

object V02ArchiveReproducibilityTest {
    @JvmStatic
    fun main(args: Array<String>) {
        val fixture = V02TestFixture.create("reproducibility")
        try {
            val win1 = fixture.root.resolve("win-1.zip")
            val win2 = fixture.root.resolve("win-2.zip")
            val ubu1 = fixture.root.resolve("ubu-1.tar.gz")
            val ubu2 = fixture.root.resolve("ubu-2.tar.gz")
            V02ReleasePackager.packageArchive(fixture.manifest, V02Platform.WINDOWS, win1)
            V02ReleasePackager.packageArchive(fixture.manifest, V02Platform.WINDOWS, win2)
            V02ReleasePackager.packageArchive(fixture.manifest, V02Platform.UBUNTU, ubu1)
            V02ReleasePackager.packageArchive(fixture.manifest, V02Platform.UBUNTU, ubu2)
            val zip = Files.readAllBytes(win1)
            val gzip = Files.readAllBytes(ubu1)
            check(zip.contentEquals(Files.readAllBytes(win2)))
            check(gzip.contentEquals(Files.readAllBytes(ubu2)))
            V02ArchiveVerifier.verifyZip(zip, fixture.freezeEpoch)
            V02ArchiveVerifier.verifyTarGzip(gzip, fixture.freezeEpoch)

            val zipMutations = listOf(4, 6, 8, 10, 12, 14, 18, 22, zip.size - 22, zip.size - 20, zip.size - 16, zip.size - 12, zip.size - 8, zip.size - 2)
            zipMutations.forEach { offset -> expectFailure { V02ArchiveVerifier.verifyZip(zip.flipped(offset), fixture.freezeEpoch) } }
            val gzipMutations = listOf(0, 1, 2, 3, 4, 8, 9, gzip.size / 2, gzip.size - 8, gzip.size - 4)
            gzipMutations.forEach { offset -> expectFailure { V02ArchiveVerifier.verifyTarGzip(gzip.flipped(offset), fixture.freezeEpoch) } }
            expectFailure { V02ArchiveVerifier.verifyZip(byteArrayOf(0) + zip, fixture.freezeEpoch) }
            expectFailure { V02ArchiveVerifier.verifyZip(zip + byteArrayOf(0), fixture.freezeEpoch) }
            expectFailure { V02ArchiveVerifier.verifyTarGzip(gzip + byteArrayOf(0), fixture.freezeEpoch) }
        } finally {
            fixture.root.toFile().deleteRecursively()
        }
        println("V2-M0-02 archive reproducibility self-test PASS")
    }

    private fun ByteArray.flipped(offset: Int): ByteArray = copyOf().also { it[offset] = (it[offset].toInt() xor 1).toByte() }
}

internal object V02ArchiveVerifier {
    private data class Local(
        val path: String,
        val offset: Int,
        val directory: Boolean,
        val crc: Long,
        val compressed: ByteArray,
        val content: ByteArray,
    )

    fun verifyZip(bytes: ByteArray, freeze: Long) {
        check(u32(bytes, 0) == 0x04034b50L)
        val locals = ArrayList<Local>()
        var cursor = 0
        while (u32(bytes, cursor) == 0x04034b50L) {
            val offset = cursor
            check(u16(bytes, cursor + 4) == 20 && u16(bytes, cursor + 6) == 0x0800)
            val method = u16(bytes, cursor + 8)
            check(u16(bytes, cursor + 10) == dos(freeze).first && u16(bytes, cursor + 12) == dos(freeze).second)
            val crc = u32(bytes, cursor + 14)
            val compressedSize = exactInt(u32(bytes, cursor + 18))
            val size = exactInt(u32(bytes, cursor + 22))
            val nameSize = u16(bytes, cursor + 26)
            check(u16(bytes, cursor + 28) == 0)
            val nameStart = cursor + 30
            val path = bytes.copyOfRange(nameStart, nameStart + nameSize).toString(StandardCharsets.UTF_8)
            cursor = nameStart + nameSize
            val compressed = bytes.copyOfRange(cursor, cursor + compressedSize)
            cursor += compressedSize
            val directory = path.endsWith('/')
            check(if (directory) method == 0 && size == 0 && compressedSize == 0 && crc == 0L else method == 8)
            val content = if (directory) ByteArray(0) else inflate(compressed, size)
            check(crc(content) == crc)
            if (!directory) check(rawDeflate(content).contentEquals(compressed))
            locals += Local(path, offset, directory, crc, compressed, content)
        }
        val paths = locals.map { it.path }
        check(paths == paths.sortedWith(V02ReleasePackager::compareUnsignedUtf8) && paths.toSet().size == paths.size)
        val centralOffset = cursor
        for ((index, local) in locals.withIndex()) {
            check(u32(bytes, cursor) == 0x02014b50L)
            check(u16(bytes, cursor + 4) == 0x0314 && u16(bytes, cursor + 6) == 20)
            check(u16(bytes, cursor + 8) == 0x0800 && u16(bytes, cursor + 10) == if (local.directory) 0 else 8)
            check(u16(bytes, cursor + 12) == dos(freeze).first && u16(bytes, cursor + 14) == dos(freeze).second)
            check(u32(bytes, cursor + 16) == local.crc)
            check(u32(bytes, cursor + 20) == local.compressed.size.toLong())
            check(u32(bytes, cursor + 24) == local.content.size.toLong())
            val nameSize = u16(bytes, cursor + 28)
            check(u16(bytes, cursor + 30) == 0 && u16(bytes, cursor + 32) == 0)
            check(u16(bytes, cursor + 34) == 0 && u16(bytes, cursor + 36) == 0 && u32(bytes, cursor + 42) == local.offset.toLong())
            val external = when {
                local.directory -> (0b100000111101101L shl 16) or 0x10L
                local.path.startsWith("bin/") -> 0b1000000111101101L shl 16
                else -> 0b1000000110100100L shl 16
            }
            check(u32(bytes, cursor + 38) == external)
            val path = bytes.copyOfRange(cursor + 46, cursor + 46 + nameSize).toString(StandardCharsets.UTF_8)
            check(path == locals[index].path)
            cursor += 46 + nameSize
        }
        val centralSize = cursor - centralOffset
        check(u32(bytes, cursor) == 0x06054b50L && cursor + 22 == bytes.size)
        check(u16(bytes, cursor + 4) == 0 && u16(bytes, cursor + 6) == 0)
        check(u16(bytes, cursor + 8) == locals.size && u16(bytes, cursor + 10) == locals.size)
        check(u32(bytes, cursor + 12) == centralSize.toLong() && u32(bytes, cursor + 16) == centralOffset.toLong())
        check(u16(bytes, cursor + 20) == 0)
    }

    fun verifyTarGzip(bytes: ByteArray, freeze: Long) {
        check(bytes.size > 18 && bytes[0].u() == 0x1f && bytes[1].u() == 0x8b && bytes[2].u() == 8)
        check(bytes[3].u() == 0 && u32(bytes, 4) == freeze && bytes[8].u() == 2 && bytes[9].u() == 3)
        val compressed = bytes.copyOfRange(10, bytes.size - 8)
        val tar = inflateUnknown(compressed)
        check(rawDeflate(tar).contentEquals(compressed))
        check(u32(bytes, bytes.size - 8) == crc(tar))
        check(u32(bytes, bytes.size - 4) == (tar.size.toLong() and 0xffff_ffffL))
        val paths = ArrayList<String>()
        var cursor = 0
        while (!tar.copyOfRange(cursor, cursor + 512).all { it == 0.toByte() }) {
            val header = tar.copyOfRange(cursor, cursor + 512)
            check(header.copyOfRange(257, 263).contentEquals(byteArrayOf(117, 115, 116, 97, 114, 0)))
            check(header.copyOfRange(263, 265).toString(StandardCharsets.US_ASCII) == "00")
            check(header.copyOfRange(108, 116).toString(StandardCharsets.US_ASCII) == "0000000\u0000")
            check(header.copyOfRange(116, 124).toString(StandardCharsets.US_ASCII) == "0000000\u0000")
            check(header.copyOfRange(265, 329).all { it == 0.toByte() })
            check(header.copyOfRange(329, 345).toString(StandardCharsets.US_ASCII) == "0000000\u00000000000\u0000")
            val storedChecksum = octal(header, 148, 8)
            val checksumBytes = header.copyOf().also { for (index in 148 until 156) it[index] = 0x20 }
            check(storedChecksum == checksumBytes.sumOf { it.u() }.toLong())
            val name = nulString(header, 0, 100)
            val prefix = nulString(header, 345, 155)
            val path = if (prefix.isEmpty()) name else "$prefix/$name"
            paths += path
            val directory = header[156].toInt().toChar() == '5'
            check(header[156].toInt().toChar() == if (directory) '5' else '0')
            val size = exactInt(octal(header, 124, 12))
            check(octal(header, 136, 12) == freeze)
            check(octal(header, 100, 8) == if (directory || path.startsWith("bin/")) 493L else 420L)
            check(if (directory) size == 0 && path.endsWith('/') else !path.endsWith('/'))
            cursor += 512
            if (!directory) {
                cursor += size
                while (cursor % 512 != 0) {
                    check(tar[cursor] == 0.toByte())
                    cursor += 1
                }
            }
        }
        check(paths == paths.sortedWith(V02ReleasePackager::compareUnsignedUtf8))
        check(cursor + 1024 == tar.size && tar.copyOfRange(cursor, tar.size).all { it == 0.toByte() })
    }

    private fun rawDeflate(bytes: ByteArray): ByteArray {
        val deflater = Deflater(9, true)
        return try {
            deflater.setStrategy(Deflater.DEFAULT_STRATEGY)
            deflater.setInput(bytes)
            deflater.finish()
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (!deflater.finished()) output.write(buffer, 0, deflater.deflate(buffer))
            output.toByteArray()
        } finally {
            deflater.end()
        }
    }

    private fun inflate(bytes: ByteArray, size: Int): ByteArray = inflateUnknown(bytes).also { check(it.size == size) }

    private fun inflateUnknown(bytes: ByteArray): ByteArray {
        val inflater = Inflater(true)
        return try {
            inflater.setInput(bytes)
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (!inflater.finished()) {
                val count = inflater.inflate(buffer)
                check(count > 0)
                output.write(buffer, 0, count)
            }
            check(inflater.remaining == 0)
            output.toByteArray()
        } finally {
            inflater.end()
        }
    }

    private fun dos(epoch: Long): Pair<Int, Int> {
        val value = java.time.Instant.ofEpochSecond(epoch).atZone(java.time.ZoneOffset.UTC)
        return (((value.hour shl 11) or (value.minute shl 5) or (value.second / 2))) to
            (((value.year - 1980) shl 9) or (value.monthValue shl 5) or value.dayOfMonth)
    }

    private fun nulString(bytes: ByteArray, offset: Int, width: Int): String {
        val end = (offset until offset + width).firstOrNull { bytes[it] == 0.toByte() } ?: offset + width
        check(bytes.copyOfRange(end, offset + width).all { it == 0.toByte() })
        return bytes.copyOfRange(offset, end).toString(StandardCharsets.UTF_8)
    }

    private fun octal(bytes: ByteArray, offset: Int, width: Int): Long {
        val text = bytes.copyOfRange(offset, offset + width).toString(StandardCharsets.US_ASCII)
        check(text.last() == '\u0000' || text.last() == ' ')
        return text.trim { it == '\u0000' || it == ' ' }.toLong(8)
    }

    private fun exactInt(value: Long): Int = value.toInt().also { check(it.toLong() == value) }
    private fun u16(bytes: ByteArray, offset: Int): Int = bytes[offset].u() or (bytes[offset + 1].u() shl 8)
    private fun u32(bytes: ByteArray, offset: Int): Long = (0 until 4).sumOf { bytes[offset + it].u().toLong() shl (8 * it) }
    private fun Byte.u(): Int = toInt() and 0xff
    private fun crc(bytes: ByteArray): Long = CRC32().also { it.update(bytes) }.value
}
