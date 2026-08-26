package ah.distribution

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.text.Normalizer
import java.util.Locale
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
            V02SemanticArchiveMutations.run(zip, gzip, fixture.freezeEpoch)

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

private object V02SemanticArchiveMutations {
    private data class Entry(val local: ByteArray, var name: ByteArray, var payload: ByteArray, val central: ByteArray,
        var localExtra: ByteArray = byteArrayOf(), var centralExtra: ByteArray = byteArrayOf(),
        var comment: ByteArray = byteArrayOf(), var descriptor: ByteArray = byteArrayOf())

    fun run(zip: ByteArray, gzip: ByteArray, freeze: Long) {
        var count = 0
        fun zipCase(label: String, mutation: (MutableList<Entry>) -> Unit) {
            val entries = readZip(zip); mutation(entries)
            val bytes = zipBytes(entries)
            check(!bytes.contentEquals(zip)) { "no-op ZIP mutation $label" }
            check(runCatching { V02ArchiveVerifier.verifyZip(bytes, freeze) }.isFailure) { "accepted ZIP $label" }; count++
        }
        fun header(label: String, offset: Int, centralOffset: Int, value: Long, width: Int = 2, directory: Boolean = false) {
            zipCase(label) { entries -> val e = entries.first { it.name.toString(StandardCharsets.UTF_8).endsWith('/') == directory }
                put(e.local, offset, value, width); put(e.central, centralOffset, value, width) }
        }
        header("directory method", 8, 10, 8, directory = true)
        header("directory CRC", 14, 16, 1, 4, directory = true)
        header("directory sizes", 22, 24, 1, 4, directory = true)
        zipCase("directory nonempty") { entries -> val e = entries.first { it.name.last() == '/'.code.toByte() }
            e.payload = byteArrayOf(1); put(e.local, 18, 1, 4); put(e.central, 20, 1, 4)
            put(e.local, 22, 1, 4); put(e.central, 24, 1, 4); put(e.local, 14, crc(e.payload), 4); put(e.central, 16, crc(e.payload), 4) }
        header("file STORED", 8, 10, 0)
        for (flags in listOf(0L, 1L, 8L, 0x0808L)) header("flags $flags", 6, 8, flags)
        header("version needed", 4, 6, 45)
        header("DOS round up", 10, 12, (readZip(zip).first().let { get(it.local, 10, 2) } + 1))
        header("CRC", 14, 16, 1, 4)
        header("size", 22, 24, 1, 4)
        zipCase("local-central mismatch") { put(it.first().central, 16, 1, 4) }
        zipCase("made by") { put(it.first().central, 4, 20, 2) }
        zipCase("disk start") { put(it.first().central, 34, 1, 2) }
        zipCase("internal attributes") { put(it.first().central, 36, 1, 2) }
        for (kind in listOf("directory", "launcher", "regular")) zipCase("external $kind") { entries ->
            val e = entries.first { val name = it.name.toString(StandardCharsets.UTF_8)
                when (kind) { "directory" -> name.endsWith('/'); "launcher" -> name.startsWith("bin/") && !name.endsWith('/'); else -> !name.endsWith('/') && !name.startsWith("bin/") } }
            put(e.central, 38, 0, 4)
        }
        zipCase("local extra") { it.first().localExtra = byteArrayOf(1, 0, 0, 0) }
        zipCase("central extra ZIP64") { it.first().centralExtra = byteArrayOf(1, 0, 0, 0) }
        zipCase("entry comment") { it.first().comment = byteArrayOf(97) }
        zipCase("data descriptor") { val e = it.first(); put(e.local, 6, 0x0808, 2); put(e.central, 8, 0x0808, 2); e.descriptor = ByteArray(16) }
        zipCase("order") { it.reverse() }
        zipCase("duplicate") { it.add(0, readZip(zip).first()) }
        for (name in listOf("../escape", "a\\b", "e\u0301", "/root")) zipCase("path $name") { it.first().name = name.toByteArray() }
        zipCase("invalid UTF8") { it.first().name = byteArrayOf(0xc0.toByte(), 0xaf.toByte()) }
        zipCase("case collision") { val first = it.first(); val other = readZip(zip).first(); other.name = first.name.toString(StandardCharsets.UTF_8).lowercase().toByteArray(); it.add(1, other) }
        val modes = listOf("level" to Triple(0, Deflater.DEFAULT_STRATEGY, true), "strategy" to Triple(9, Deflater.HUFFMAN_ONLY, true), "nowrap/zlib" to Triple(9, Deflater.DEFAULT_STRATEGY, false))
        for ((label, settings) in modes) zipCase("compression $label") { entries ->
            val e = entries.first { it.name.toString(StandardCharsets.UTF_8) == "lib/android-app-hardening.jar" }
            replacePayload(e, compress(inflate(e.payload), settings.first, settings.second, settings.third))
        }
        zipCase("gzip wrapper") { entries -> val e = entries.first { it.payload.isNotEmpty() }; replacePayload(e, gzipBytes(inflate(e.payload), freeze)) }
        zipCase("input drain") { entries -> val e = entries.first { it.name.toString(StandardCharsets.UTF_8) == "lib/android-app-hardening.jar" }; replacePayload(e, compress(inflate(e.payload), flush = true)) }
        for ((label, offset, width) in listOf(Triple("EOCD disk",4,2), Triple("EOCD central disk",6,2), Triple("EOCD count",8,2), Triple("EOCD total",10,2), Triple("EOCD size",12,4), Triple("EOCD offset",16,4), Triple("archive comment",20,2))) {
            val mutated = zip.copyOf(); put(mutated, mutated.size - 22 + offset, 1, width)
            check(runCatching { V02ArchiveVerifier.verifyZip(mutated, freeze) }.isFailure) { label }; count++
        }
        val offsetMutation = zip.copyOf(); val central = get(zip, zip.size - 6, 4).toInt(); put(offsetMutation, central + 42, 1, 4)
        check(runCatching { V02ArchiveVerifier.verifyZip(offsetMutation, freeze) }.isFailure); count++

        val tar = inflate(gzip.copyOfRange(10, gzip.size - 8))
        val offsets = tarOffsets(tar)
        val directory = offsets.first { tar[it + 156] == '5'.code.toByte() }
        val regular = offsets.first { tar[it + 156] == '0'.code.toByte() }
        fun tarCase(label: String, header: Int = regular, checksum: Boolean = true, mutation: (ByteArray, Int) -> Unit) {
            val changed = tar.copyOf(); mutation(changed, header)
            if (checksum) checksum(changed, header)
            check(!changed.contentEquals(tar)) { "no-op TAR $label" }
            check(runCatching { V02ArchiveVerifier.verifyTarGzip(gzipBytes(changed, freeze), freeze) }.isFailure) { "accepted TAR $label" }; count++
        }
        for ((label, field) in listOf("magic" to 257, "version" to 263, "uid" to 114, "gid" to 122, "uname" to 265, "gname" to 297, "mode" to 106, "mtime" to 146, "linkname" to 157, "devmajor" to 335, "devminor" to 343, "reserved" to 500)) {
            tarCase(label) { bytes, start -> bytes[start + field] = (if (bytes[start + field] == '1'.code.toByte()) '2' else '1').code.toByte() }
        }
        tarCase("directory size", directory) { bytes, start -> bytes[start + 134] = '1'.code.toByte() }
        tarCase("octal width") { bytes, start -> bytes[start + 100] = 0x20 }
        tarCase("octal NUL") { bytes, start -> bytes[start + 107] = 0x20 }
        tarCase("base256") { bytes, start -> bytes[start + 124] = 0x80.toByte() }
        tarCase("checksum value", checksum = false) { bytes, start -> bytes[start + 148] = '1'.code.toByte() }
        tarCase("checksum encoding", checksum = false) { bytes, start -> bytes[start + 154] = 0x20 }
        tarCase("signed instead of unsigned checksum", checksum = false) { bytes, start ->
            bytes[start] = 0xc3.toByte(); bytes[start + 1] = 0xa9.toByte(); bytes.fill(0x20, start + 148, start + 156)
            val signed = bytes.copyOfRange(start, start + 512).sumOf { it.toInt() }
            (signed.toString(8).padStart(6, '0') + "\u0000 ").toByteArray().copyInto(bytes, start + 148)
        }
        tarCase("regular size") { bytes, start -> bytes[start + 134] = if (bytes[start + 134] == '1'.code.toByte()) '2'.code.toByte() else '1'.code.toByte() }
        for (name in listOf("../escape", "a\\b", "e\u0301", "/root")) tarCase("invalid path $name") { bytes, start ->
            bytes.fill(0, start, start + 100); name.toByteArray().copyInto(bytes, start)
        }
        for (type in listOf('1', '2', '3', '6', '7', 'x', 'g', 'L', 'K', 'S', '\u0000')) tarCase("type $type") { bytes, start -> bytes[start + 156] = type.code.toByte() }
        tarCase("directory regular type", directory) { bytes, start -> bytes[start + 156] = '0'.code.toByte() }
        tarCase("regular directory type") { bytes, start -> bytes[start + 156] = '5'.code.toByte() }
        val nested = offsets.first { tar[it + 156] == '0'.code.toByte() && String(tar, it, 100, StandardCharsets.UTF_8).contains('/') }
        tarCase("noncanonical name-prefix split", nested) { bytes, start ->
            val name = String(bytes, start, 100, StandardCharsets.UTF_8).substringBefore('\u0000'); val slash = name.indexOf('/')
            bytes.fill(0, start, start + 100); name.substring(slash + 1).toByteArray().copyInto(bytes, start)
            name.substring(0, slash).toByteArray().copyInto(bytes, start + 345)
        }
        tarCase("payload padding", checksum = false) { bytes, start -> val size = octal(bytes, start + 124, 12); bytes[start + 512 + size] = 1 }
        val chunks = offsets.mapIndexed { index, start -> tar.copyOfRange(start, offsets.getOrNull(index + 1) ?: tar.size - 1024) }
        val reordered = chunks.reversed().fold(byteArrayOf()) { total, chunk -> total + chunk } + ByteArray(1024)
        check(runCatching { V02ArchiveVerifier.verifyTarGzip(gzipBytes(reordered, freeze), freeze) }.isFailure); count++
        for (changed in listOf(tar.copyOf(tar.size - 512), tar + ByteArray(512), tar + byteArrayOf(1))) {
            check(runCatching { V02ArchiveVerifier.verifyTarGzip(gzipBytes(changed, freeze), freeze) }.isFailure); count++
        }
        for ((label, settings) in modes) {
            val changed = gzipBytes(tar, freeze, compress(tar, settings.first, settings.second, settings.third))
            check(!changed.contentEquals(gzip)); check(runCatching { V02ArchiveVerifier.verifyTarGzip(changed, freeze) }.isFailure) { label }; count++
        }
        for (flag in listOf(2, 4, 8, 16)) {
            val changed = gzip.copyOf().also { it[3] = flag.toByte() }
            check(runCatching { V02ArchiveVerifier.verifyTarGzip(changed, freeze) }.isFailure); count++
        }
        for (changed in listOf(gzip + gzip, gzip + ByteArray(8), gzip.copyOf().also { bytes -> bytes.copyOfRange(bytes.size - 8, bytes.size - 4).reversedArray().copyInto(bytes, bytes.size - 8) }, gzip.copyOf().also { bytes -> bytes.copyOfRange(bytes.size - 4, bytes.size).reversedArray().copyInto(bytes, bytes.size - 4) })) {
            check(runCatching { V02ArchiveVerifier.verifyTarGzip(changed, freeze) }.isFailure); count++
        }
        println("ZIP/TAR/GZIP semantic mutations rejected: $count")
    }

    private fun readZip(bytes: ByteArray): MutableList<Entry> {
        val entries = mutableListOf<Entry>(); var cursor = 0
        while (get(bytes, cursor, 4) == 0x04034b50L) {
            val header = bytes.copyOfRange(cursor, cursor + 30); val nameSize = get(header, 26, 2).toInt(); val size = get(header, 18, 4).toInt()
            val name = bytes.copyOfRange(cursor + 30, cursor + 30 + nameSize); val payload = bytes.copyOfRange(cursor + 30 + nameSize, cursor + 30 + nameSize + size)
            entries += Entry(header, name, payload, ByteArray(46)); cursor += 30 + nameSize + size
        }
        for (entry in entries) { bytes.copyInto(entry.central, 0, cursor, cursor + 46); cursor += 46 + entry.name.size }
        return entries
    }
    private fun zipBytes(entries: List<Entry>): ByteArray {
        val out = ByteArrayOutputStream(); val offsets = mutableListOf<Int>()
        for (entry in entries) { offsets += out.size(); put(entry.local, 26, entry.name.size.toLong(), 2); put(entry.local, 28, entry.localExtra.size.toLong(), 2)
            out.write(entry.local); out.write(entry.name); out.write(entry.localExtra); out.write(entry.payload); out.write(entry.descriptor) }
        val start = out.size()
        entries.forEachIndexed { index, entry -> put(entry.central, 28, entry.name.size.toLong(), 2); put(entry.central, 30, entry.centralExtra.size.toLong(), 2)
            put(entry.central, 32, entry.comment.size.toLong(), 2); put(entry.central, 42, offsets[index].toLong(), 4)
            out.write(entry.central); out.write(entry.name); out.write(entry.centralExtra); out.write(entry.comment) }
        val end = ByteArray(22); put(end, 0, 0x06054b50, 4); put(end, 8, entries.size.toLong(), 2); put(end, 10, entries.size.toLong(), 2)
        put(end, 12, (out.size() - start).toLong(), 4); put(end, 16, start.toLong(), 4); out.write(end); return out.toByteArray()
    }
    private fun replacePayload(entry: Entry, payload: ByteArray) { check(!entry.payload.contentEquals(payload)); entry.payload = payload; put(entry.local, 18, payload.size.toLong(), 4); put(entry.central, 20, payload.size.toLong(), 4) }
    private fun tarOffsets(bytes: ByteArray): List<Int> { val result = mutableListOf<Int>(); var cursor = 0
        while (bytes[cursor] != 0.toByte()) { result += cursor; cursor += 512 + ((octal(bytes, cursor + 124, 12) + 511) / 512) * 512 }; return result }
    private fun checksum(bytes: ByteArray, offset: Int) { bytes.fill(0x20, offset + 148, offset + 156); val sum = bytes.copyOfRange(offset, offset + 512).sumOf { it.toInt() and 255 }
        (sum.toString(8).padStart(6, '0') + "\u0000 ").toByteArray().copyInto(bytes, offset + 148) }
    private fun octal(bytes: ByteArray, offset: Int, width: Int) = String(bytes, offset, width - 1, StandardCharsets.US_ASCII).toInt(8)
    private fun gzipBytes(bytes: ByteArray, epoch: Long, payload: ByteArray = compress(bytes)): ByteArray {
        val header = byteArrayOf(31, 139.toByte(), 8, 0, 0, 0, 0, 0, 2, 3); put(header, 4, epoch, 4)
        val trailer = ByteArray(8); put(trailer, 0, crc(bytes), 4); put(trailer, 4, bytes.size.toLong(), 4); return header + payload + trailer
    }
    private fun compress(bytes: ByteArray, level: Int = 9, strategy: Int = Deflater.DEFAULT_STRATEGY, nowrap: Boolean = true, flush: Boolean = false): ByteArray {
        val deflater = Deflater(level, nowrap); val output = ByteArrayOutputStream(); val buffer = ByteArray(8192)
        try { deflater.setStrategy(strategy)
            if (flush) { deflater.setInput(bytes, 0, bytes.size / 2)
                do { val count = deflater.deflate(buffer, 0, buffer.size, Deflater.SYNC_FLUSH); output.write(buffer, 0, count) } while (!deflater.needsInput())
                deflater.setInput(bytes, bytes.size / 2, bytes.size - bytes.size / 2)
            } else deflater.setInput(bytes)
            deflater.finish(); while (!deflater.finished()) { val count = deflater.deflate(buffer); output.write(buffer, 0, count) }; return output.toByteArray()
        } finally { deflater.end() }
    }
    private fun inflate(bytes: ByteArray): ByteArray { val inflater = Inflater(true); val output = ByteArrayOutputStream(); val buffer = ByteArray(8192)
        try { inflater.setInput(bytes); while (!inflater.finished()) { val count = inflater.inflate(buffer); check(count > 0); output.write(buffer, 0, count) }; return output.toByteArray() } finally { inflater.end() } }
    private fun crc(bytes: ByteArray) = CRC32().also { it.update(bytes) }.value
    private fun put(bytes: ByteArray, offset: Int, value: Long, width: Int) { repeat(width) { bytes[offset + it] = (value ushr (8 * it)).toByte() } }
    private fun get(bytes: ByteArray, offset: Int, width: Int): Long = (0 until width).sumOf { (bytes[offset + it].toInt() and 255).toLong() shl (8 * it) }
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
            val path = utf8(bytes.copyOfRange(nameStart, nameStart + nameSize))
            validPath(path)
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
        check(paths.map { it.lowercase(Locale.ROOT) }.toSet().size == paths.size)
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
            val path = utf8(bytes.copyOfRange(cursor + 46, cursor + 46 + nameSize))
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
            check(header.copyOfRange(157, 257).all { it == 0.toByte() })
            check(header.copyOfRange(500, 512).all { it == 0.toByte() })
            check(header.copyOfRange(329, 345).toString(StandardCharsets.US_ASCII) == "0000000\u00000000000\u0000")
            check(header[154] == 0.toByte() && header[155] == 0x20.toByte())
            val checksumText = header.copyOfRange(148, 154).toString(StandardCharsets.US_ASCII)
            check(checksumText.matches(Regex("[0-7]{6}")))
            val storedChecksum = checksumText.toLong(8)
            val checksumBytes = header.copyOf().also { for (index in 148 until 156) it[index] = 0x20 }
            check(storedChecksum == checksumBytes.sumOf { it.u() }.toLong())
            val name = nulString(header, 0, 100)
            val prefix = nulString(header, 345, 155)
            val path = if (prefix.isEmpty()) name else "$prefix/$name"
            validPath(path)
            if (path.toByteArray(StandardCharsets.UTF_8).size <= 100) check(prefix.isEmpty()) else {
                val split = path.indices.reversed().firstOrNull { index ->
                    path[index] == '/' && index > 0 && index < path.lastIndex &&
                        path.substring(0, index).toByteArray(StandardCharsets.UTF_8).size <= 155 &&
                        path.substring(index + 1).toByteArray(StandardCharsets.UTF_8).size <= 100
                }
                check(split != null && prefix == path.substring(0, split) && name == path.substring(split + 1))
            }
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
        check(paths.map { it.lowercase(Locale.ROOT) }.toSet().size == paths.size)
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
        return utf8(bytes.copyOfRange(offset, end))
    }

    private fun octal(bytes: ByteArray, offset: Int, width: Int): Long {
        val text = bytes.copyOfRange(offset, offset + width).toString(StandardCharsets.US_ASCII)
        check(text.last() == '\u0000' && text.dropLast(1).all { it in '0'..'7' })
        return text.dropLast(1).toLong(8)
    }

    private fun utf8(bytes: ByteArray): String = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()

    private fun validPath(path: String) {
        check(path.isNotEmpty() && !path.startsWith('/') && '\\' !in path && path.none { it.code < 32 || it.code == 127 })
        check(Normalizer.normalize(path, Normalizer.Form.NFC) == path)
        check(path.removeSuffix("/").split('/').none { it.isEmpty() || it == "." || it == ".." })
    }

    private fun exactInt(value: Long): Int = value.toInt().also { check(it.toLong() == value) }
    private fun u16(bytes: ByteArray, offset: Int): Int = bytes[offset].u() or (bytes[offset + 1].u() shl 8)
    private fun u32(bytes: ByteArray, offset: Int): Long = (0 until 4).sumOf { bytes[offset + it].u().toLong() shl (8 * it) }
    private fun Byte.u(): Int = toInt() and 0xff
    private fun crc(bytes: ByteArray): Long = CRC32().also { it.update(bytes) }.value
}
