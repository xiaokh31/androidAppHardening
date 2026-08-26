package ah.distribution

import com.sun.jna.Platform
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinBase
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinNT
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.text.Normalizer
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale
import java.util.zip.CRC32
import java.util.zip.Deflater

internal enum class V02Platform(val wireName: String) {
    WINDOWS("windows"),
    UBUNTU("ubuntu");

    companion object {
        fun parse(value: String): V02Platform = entries.singleOrNull { it.wireName == value }
            ?: throw DistributionException("unsupported platform")
    }
}

internal data class V02Component(
    val logicalPath: String,
    val archivePath: String?,
    val sourcePath: String,
    val mode: String,
    val sizeBytes: Long,
    val sha256: String,
    val role: String,
    val variant: String,
    val abi: String?,
    val platform: String,
)

internal data class V02ComponentManifest(
    val sourceCommit: String,
    val freezeEpochSeconds: Long,
    val implementationManifestSha256: String,
    val toolchainManifestSha256: String,
    val productContractManifestSha256: String,
    val entries: List<V02Component>,
)

internal class DistributionException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

object V02ReleasePackager {
    private const val MAX_MANIFEST_BYTES = 16 * 1024 * 1024
    private const val MAX_COMPONENT_BYTES = 512L * 1024 * 1024
    private const val MAX_TOTAL_BYTES = 1024L * 1024 * 1024
    private val SHA256 = Regex("[0-9a-f]{64}")
    private val GIT_SHA1 = Regex("[0-9a-f]{40}")
    private val ABI = setOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
    private val ENTRY_KEYS = listOf(
        "logicalPath", "archivePath", "sourcePath", "mode", "sizeBytes", "sha256",
        "role", "variant", "abi", "platform",
    )

    @JvmStatic
    fun main(args: Array<String>) {
        val parsed = parseArguments(args)
        packageArchive(parsed.first, parsed.second, parsed.third)
    }

    internal fun packageArchive(manifestPath: Path, platform: V02Platform, output: Path) {
        val manifestAbsolute = manifestPath.toAbsolutePath().normalize()
        val outputAbsolute = output.toAbsolutePath().normalize()
        if (manifestAbsolute == outputAbsolute) throw DistributionException("output aliases manifest")
        if (!Files.isRegularFile(manifestAbsolute, LinkOption.NOFOLLOW_LINKS)) {
            throw DistributionException("component manifest must be a regular file")
        }
        if (Files.isSymbolicLink(manifestAbsolute)) throw DistributionException("component manifest cannot be a symlink")
        if (Files.size(manifestAbsolute) > MAX_MANIFEST_BYTES) throw DistributionException("component manifest is too large")
        if (Files.exists(outputAbsolute, LinkOption.NOFOLLOW_LINKS)) throw DistributionException("output already exists")

        val repositoryRoot = locateRepositoryRoot(manifestAbsolute.parent)
        if (manifestAbsolute == repositoryRoot.resolve("build/v0.2/candidate-component-manifest.json")) {
            throw DistributionException("diagnostic baseline copy cannot be a packager input")
        }
        rejectHardlinks(manifestAbsolute)
        val manifestBytes = Files.readAllBytes(manifestAbsolute)
        rejectHardlinks(manifestAbsolute)
        val manifest = parseManifest(manifestBytes)
        packageArchive(repositoryRoot, manifest, platform, outputAbsolute)
    }

    internal fun packageArchive(repository: Path, manifest: V02ComponentManifest, platform: V02Platform, output: Path) {
        val repositoryRoot = repository.toAbsolutePath().normalize()
        val outputAbsolute = output.toAbsolutePath().normalize()
        if (Files.exists(outputAbsolute, LinkOption.NOFOLLOW_LINKS)) throw DistributionException("output already exists")
        val files = loadArchiveFiles(repositoryRoot, manifest, platform, outputAbsolute)
        val archiveBytes = when (platform) {
            V02Platform.WINDOWS -> buildZip(files, manifest.freezeEpochSeconds)
            V02Platform.UBUNTU -> buildTarGzip(files, manifest.freezeEpochSeconds)
        }
        publishAtomically(outputAbsolute, archiveBytes)
    }

    private fun parseArguments(args: Array<String>): Triple<Path, V02Platform, Path> {
        if (args.size != 6) {
            throw DistributionException("usage: --manifest <path> --platform <windows|ubuntu> --output <path>")
        }
        val values = LinkedHashMap<String, String>()
        var index = 0
        while (index < args.size) {
            val key = args[index]
            if (key !in setOf("--manifest", "--platform", "--output") || key in values) {
                throw DistributionException("invalid or duplicate argument")
            }
            values[key] = args[index + 1]
            index += 2
        }
        return Triple(
            Path.of(values.getValue("--manifest")),
            V02Platform.parse(values.getValue("--platform")),
            Path.of(values.getValue("--output")),
        )
    }

    internal fun parseManifest(bytes: ByteArray): V02ComponentManifest {
        val value = StrictJson.parse(bytes)
        if (!bytes.contentEquals(CanonicalJson.prettyBytes(value))) {
            throw DistributionException("component manifest is not canonical two-space LF JSON")
        }
        val root = value.asObject("manifest")
        root.requireKeys(
            listOf(
                "schemaVersion", "releaseLine", "releaseVersion", "sourceCommit", "freezeEpochSeconds",
                "implementationManifestSha256", "toolchainManifestSha256", "productContractManifestSha256", "entries",
            ),
            "manifest",
        )
        if (root.long("schemaVersion") != 1L || root.string("releaseLine") != "v0.2" ||
            root.string("releaseVersion") != "0.2.0"
        ) {
            throw DistributionException("component manifest identity mismatch")
        }
        val sourceCommit = root.string("sourceCommit")
        if (!GIT_SHA1.matches(sourceCommit)) throw DistributionException("invalid source commit")
        val freezeEpoch = root.long("freezeEpochSeconds")
        val instant = try {
            Instant.ofEpochSecond(freezeEpoch)
        } catch (failure: RuntimeException) {
            throw DistributionException("invalid freeze epoch", failure)
        }
        val year = instant.atZone(ZoneOffset.UTC).year
        if (freezeEpoch !in 0..0xffff_ffffL || year !in 1980..2107) {
            throw DistributionException("freeze epoch is outside ZIP/GZIP range")
        }
        val implementationHash = root.hash("implementationManifestSha256")
        val toolchainHash = root.hash("toolchainManifestSha256")
        val contractHash = root.hash("productContractManifestSha256")
        val rawEntries = root.array("entries")
        if (rawEntries.isEmpty()) throw DistributionException("component entries cannot be empty")
        val entries = rawEntries.mapIndexed { entryIndex, raw ->
            val entry = raw.asObject("entries[$entryIndex]")
            entry.requireKeys(ENTRY_KEYS, "entries[$entryIndex]")
            val logicalPath = validatePath(entry.string("logicalPath"), "logicalPath")
            val archivePath = entry.nullableString("archivePath")?.let { validatePath(it, "archivePath") }
            val sourcePath = validatePath(entry.string("sourcePath"), "sourcePath")
            val mode = entry.string("mode")
            if (mode !in setOf("100644", "100755")) throw DistributionException("invalid component mode")
            val expectedMode = if (archivePath?.startsWith("bin/") == true) "100755" else "100644"
            if (mode != expectedMode) throw DistributionException("component mode differs from fixed archive role")
            val size = entry.long("sizeBytes")
            if (size !in 0..MAX_COMPONENT_BYTES) throw DistributionException("component size is out of range")
            val hash = entry.hash("sha256")
            val role = entry.string("role").also { if (it.isBlank()) throw DistributionException("blank role") }
            val variant = entry.string("variant").also { if (it.isBlank()) throw DistributionException("blank variant") }
            val abi = entry.nullableString("abi")
            if (abi != null && abi !in ABI) throw DistributionException("invalid ABI")
            val platform = entry.string("platform")
            if (platform !in setOf("all", "windows", "ubuntu", "contract")) {
                throw DistributionException("invalid component platform")
            }
            if ((platform == "contract") != (archivePath == null)) {
                throw DistributionException("contract/archive path mismatch")
            }
            V02Component(logicalPath, archivePath, sourcePath, mode, size, hash, role, variant, abi, platform)
        }
        requireStrictOrder(entries.map(V02Component::logicalPath), "logical paths")
        requireNoPathCollisions(entries.map(V02Component::logicalPath), "logical paths")
        requireNoPathCollisions(entries.map(V02Component::sourcePath), "source paths")
        return V02ComponentManifest(
            sourceCommit,
            freezeEpoch,
            implementationHash,
            toolchainHash,
            contractHash,
            entries,
        )
    }

    private data class ArchiveFile(val path: String, val mode: String, val bytes: ByteArray, val role: String)
    private data class ArchiveEntry(val path: String, val directory: Boolean, val mode: String, val bytes: ByteArray)

    private fun loadArchiveFiles(
        repositoryRoot: Path,
        manifest: V02ComponentManifest,
        platform: V02Platform,
        output: Path,
    ): List<ArchiveFile> {
        val selected = manifest.entries.filter { it.platform == "all" || it.platform == platform.wireName }
        if (selected.isEmpty()) throw DistributionException("platform component set is empty")
        val archivePaths = selected.map { it.archivePath ?: throw DistributionException("selected contract entry") }
        requireStrictOrder(archivePaths, "archive paths")
        requireNoPathCollisions(archivePaths, "archive paths")
        validateExpectedLayout(archivePaths, platform)

        var total = 0L
        return selected.map { component ->
            val source = repositoryRoot.resolve(component.sourcePath).normalize()
            if (!source.startsWith(repositoryRoot) || source == output || source == output.toAbsolutePath().normalize()) {
                throw DistributionException("component source escapes repository or aliases output")
            }
            rejectSymlinkPath(repositoryRoot, source)
            if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) {
                throw DistributionException("component source is not a regular file: ${component.logicalPath}")
            }
            rejectHardlinks(source)
            val bytes = Files.readAllBytes(source)
            rejectHardlinks(source)
            total = Math.addExact(total, bytes.size.toLong())
            if (total > MAX_TOTAL_BYTES) throw DistributionException("component set is too large")
            if (bytes.size.toLong() != component.sizeBytes || sha256(bytes) != component.sha256) {
                throw DistributionException("component bytes drifted: ${component.logicalPath}")
            }
            ArchiveFile(component.archivePath!!, component.mode, bytes, component.role)
        }
    }

    private fun validateExpectedLayout(paths: List<String>, platform: V02Platform) {
        val launcher = when (platform) {
            V02Platform.WINDOWS -> "bin/android-app-hardening.cmd"
            V02Platform.UBUNTU -> "bin/android-app-hardening"
        }
        val required = setOf(
            launcher,
            "lib/android-app-hardening.jar",
            "runtime/armeabi-v7a/libah_runtime.so",
            "runtime/arm64-v8a/libah_runtime.so",
            "runtime/x86/libah_runtime.so",
            "runtime/x86_64/libah_runtime.so",
            "docs/QUICKSTART.md",
            "LICENSE",
            "THIRD_PARTY_NOTICES.md",
            "bom.cdx.json",
            "release-manifest.json",
        )
        if (paths.toSet() != required) throw DistributionException("archive component set differs from the fixed layout")
    }

    private fun archiveEntries(files: List<ArchiveFile>): List<ArchiveEntry> {
        val directories = LinkedHashSet<String>()
        for (file in files) {
            var slash = file.path.indexOf('/')
            while (slash >= 0) {
                directories += file.path.substring(0, slash + 1)
                slash = file.path.indexOf('/', slash + 1)
            }
        }
        val entries = ArrayList<ArchiveEntry>(directories.size + files.size)
        directories.forEach { entries += ArchiveEntry(it, true, "100755", ByteArray(0)) }
        files.forEach { entries += ArchiveEntry(it.path, false, it.mode, it.bytes) }
        return entries.sortedWith { left, right -> compareUnsignedUtf8(left.path, right.path) }
    }

    private fun buildZip(files: List<ArchiveFile>, freezeEpoch: Long): ByteArray {
        val entries = archiveEntries(files)
        if (entries.size > 0xffff) throw DistributionException("ZIP32 entry count exceeded")
        val dos = dosDateTime(freezeEpoch)
        val output = ByteArrayOutputStream()
        val central = ArrayList<ByteArray>(entries.size)
        for (entry in entries) {
            val name = entry.path.toByteArray(StandardCharsets.UTF_8)
            if (name.size > 0xffff) throw DistributionException("ZIP path is too long")
            val crc = CRC32().also { it.update(entry.bytes) }.value
            val compressed = if (entry.directory) ByteArray(0) else deflate(entry.bytes)
            requireU32(entry.bytes.size.toLong(), "ZIP uncompressed size")
            requireU32(compressed.size.toLong(), "ZIP compressed size")
            val localOffset = output.size().toLong()
            requireU32(localOffset, "ZIP local offset")
            val local = ByteWriter()
                .u4(0x04034b50)
                .u2(20)
                .u2(0x0800)
                .u2(if (entry.directory) 0 else 8)
                .u2(dos.first)
                .u2(dos.second)
                .u4(crc)
                .u4(compressed.size.toLong())
                .u4(entry.bytes.size.toLong())
                .u2(name.size)
                .u2(0)
                .bytes(name)
                .toByteArray()
            output.write(local)
            output.write(compressed)

            val external = when {
                entry.directory -> (octal("0040755") shl 16) or 0x10L
                entry.path.startsWith("bin/") -> octal("0100755") shl 16
                else -> octal("0100644") shl 16
            }
            central += ByteWriter()
                .u4(0x02014b50)
                .u2(0x0314)
                .u2(20)
                .u2(0x0800)
                .u2(if (entry.directory) 0 else 8)
                .u2(dos.first)
                .u2(dos.second)
                .u4(crc)
                .u4(compressed.size.toLong())
                .u4(entry.bytes.size.toLong())
                .u2(name.size)
                .u2(0)
                .u2(0)
                .u2(0)
                .u2(0)
                .u4(external)
                .u4(localOffset)
                .bytes(name)
                .toByteArray()
        }
        val centralOffset = output.size().toLong()
        requireU32(centralOffset, "ZIP central offset")
        central.forEach(output::write)
        val centralSize = output.size().toLong() - centralOffset
        requireU32(centralSize, "ZIP central size")
        output.write(
            ByteWriter()
                .u4(0x06054b50)
                .u2(0)
                .u2(0)
                .u2(entries.size)
                .u2(entries.size)
                .u4(centralSize)
                .u4(centralOffset)
                .u2(0)
                .toByteArray(),
        )
        return output.toByteArray()
    }

    private fun buildTarGzip(files: List<ArchiveFile>, freezeEpoch: Long): ByteArray {
        val tar = ByteArrayOutputStream()
        for (entry in archiveEntries(files)) {
            val header = tarHeader(entry, freezeEpoch)
            tar.write(header)
            if (!entry.directory) {
                tar.write(entry.bytes)
                val padding = ((512 - (entry.bytes.size % 512)) % 512)
                tar.write(ByteArray(padding))
            }
        }
        tar.write(ByteArray(1024))
        val tarBytes = tar.toByteArray()
        val compressed = deflate(tarBytes)
        val crc = CRC32().also { it.update(tarBytes) }.value
        return ByteWriter()
            .u1(0x1f)
            .u1(0x8b)
            .u1(8)
            .u1(0)
            .u4(freezeEpoch)
            .u1(2)
            .u1(3)
            .bytes(compressed)
            .u4(crc)
            .u4(tarBytes.size.toLong() and 0xffff_ffffL)
            .toByteArray()
    }

    private fun tarHeader(entry: ArchiveEntry, freezeEpoch: Long): ByteArray {
        val split = splitUstarPath(entry.path)
        val header = ByteArray(512)
        putBytes(header, 0, 100, split.second.toByteArray(StandardCharsets.UTF_8))
        putBytes(header, 100, 8, tarOctal(if (entry.directory || entry.path.startsWith("bin/")) 493 else 420, 7))
        putBytes(header, 108, 8, tarOctal(0, 7))
        putBytes(header, 116, 8, tarOctal(0, 7))
        putBytes(header, 124, 12, tarOctal(if (entry.directory) 0 else entry.bytes.size.toLong(), 11))
        putBytes(header, 136, 12, tarOctal(freezeEpoch, 11))
        for (index in 148 until 156) header[index] = 0x20
        header[156] = if (entry.directory) '5'.code.toByte() else '0'.code.toByte()
        putBytes(header, 257, 6, byteArrayOf('u'.code.toByte(), 's'.code.toByte(), 't'.code.toByte(), 'a'.code.toByte(), 'r'.code.toByte(), 0))
        putBytes(header, 263, 2, byteArrayOf('0'.code.toByte(), '0'.code.toByte()))
        putBytes(header, 329, 8, tarOctal(0, 7))
        putBytes(header, 337, 8, tarOctal(0, 7))
        putBytes(header, 345, 155, split.first.toByteArray(StandardCharsets.UTF_8))
        val checksum = header.sumOf { it.toInt() and 0xff }
        val encoded = checksum.toString(8).padStart(6, '0').toByteArray(StandardCharsets.US_ASCII)
        putBytes(header, 148, 6, encoded)
        header[154] = 0
        header[155] = 0x20
        return header
    }

    private fun splitUstarPath(path: String): Pair<String, String> {
        val bytes = path.toByteArray(StandardCharsets.UTF_8)
        if (bytes.size <= 100) return "" to path
        var slash = path.lastIndexOf('/')
        while (slash > 0) {
            val prefix = path.substring(0, slash)
            val name = path.substring(slash + 1)
            if (prefix.toByteArray(StandardCharsets.UTF_8).size <= 155 &&
                name.toByteArray(StandardCharsets.UTF_8).size <= 100 && name.isNotEmpty()
            ) {
                return prefix to name
            }
            slash = path.lastIndexOf('/', slash - 1)
        }
        throw DistributionException("path cannot be represented by POSIX ustar")
    }

    private fun tarOctal(value: Long, digits: Int): ByteArray {
        if (value < 0) throw DistributionException("negative TAR field")
        val octal = value.toString(8)
        if (octal.length > digits) throw DistributionException("TAR field requires base-256")
        return (octal.padStart(digits, '0') + "\u0000").toByteArray(StandardCharsets.US_ASCII)
    }

    private fun tarOctal(value: Int, digits: Int): ByteArray = tarOctal(value.toLong(), digits)

    private fun putBytes(target: ByteArray, offset: Int, width: Int, value: ByteArray) {
        if (value.size > width) throw DistributionException("fixed field overflow")
        value.copyInto(target, offset)
    }

    private fun deflate(input: ByteArray): ByteArray {
        val deflater = Deflater(9, true)
        return try {
            deflater.setStrategy(Deflater.DEFAULT_STRATEGY)
            deflater.setInput(input)
            deflater.finish()
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (!deflater.finished()) {
                val count = deflater.deflate(buffer)
                if (count <= 0) throw DistributionException("raw DEFLATE made no progress")
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        } finally {
            deflater.end()
        }
    }

    private fun dosDateTime(epoch: Long): Pair<Int, Int> {
        val value = Instant.ofEpochSecond(epoch).atZone(ZoneOffset.UTC)
        val time = (value.hour shl 11) or (value.minute shl 5) or (value.second / 2)
        val date = ((value.year - 1980) shl 9) or (value.monthValue shl 5) or value.dayOfMonth
        return time to date
    }

    private fun publishAtomically(output: Path, bytes: ByteArray) {
        val parent = output.parent ?: throw DistributionException("output must have a parent")
        Files.createDirectories(parent)
        rejectSymlinkPath(parent.toAbsolutePath().normalize(), parent.toAbsolutePath().normalize())
        if (Files.exists(output, LinkOption.NOFOLLOW_LINKS)) throw DistributionException("output already exists")
        val temp = Files.createTempFile(parent, ".${output.fileName}.", ".tmp")
        var moved = false
        try {
            Files.newByteChannel(temp, java.nio.file.StandardOpenOption.WRITE).use { channel ->
                var remaining = ByteBuffer.wrap(bytes)
                while (remaining.hasRemaining()) channel.write(remaining)
            }
            Files.newByteChannel(temp, java.nio.file.StandardOpenOption.WRITE).use { channel ->
                if (channel is java.nio.channels.FileChannel) channel.force(true)
            }
            try {
                Files.move(temp, output, StandardCopyOption.ATOMIC_MOVE)
            } catch (failure: AtomicMoveNotSupportedException) {
                throw DistributionException("atomic output move is unavailable", failure)
            }
            moved = true
        } finally {
            if (!moved) Files.deleteIfExists(temp)
        }
    }

    private fun locateRepositoryRoot(start: Path): Path {
        var current: Path? = start.toAbsolutePath().normalize()
        while (current != null) {
            if (Files.isRegularFile(current.resolve("settings.gradle.kts"), LinkOption.NOFOLLOW_LINKS) &&
                Files.isDirectory(current.resolve(".git"), LinkOption.NOFOLLOW_LINKS)
            ) {
                return current
            }
            if (Files.isRegularFile(current.resolve(".git"), LinkOption.NOFOLLOW_LINKS) &&
                Files.isRegularFile(current.resolve("settings.gradle.kts"), LinkOption.NOFOLLOW_LINKS)
            ) {
                return current
            }
            current = current.parent
        }
        throw DistributionException("repository root not found")
    }

    private fun rejectSymlinkPath(root: Path, target: Path) {
        val normalizedRoot = root.toAbsolutePath().normalize()
        val normalizedTarget = target.toAbsolutePath().normalize()
        if (!normalizedTarget.startsWith(normalizedRoot)) throw DistributionException("path escapes root")
        var current = normalizedRoot
        for (segment in normalizedRoot.relativize(normalizedTarget)) {
            current = current.resolve(segment)
            if (Files.isSymbolicLink(current)) throw DistributionException("symlink path is forbidden")
        }
    }

    private fun rejectHardlinks(path: Path) {
        val links = when {
            Platform.isLinux() -> (Files.getAttribute(path, "unix:nlink", LinkOption.NOFOLLOW_LINKS) as Number).toLong()
            Platform.isWindows() -> {
                val handle = Kernel32.INSTANCE.CreateFile(
                    path.toString(), WinNT.GENERIC_READ,
                    WinNT.FILE_SHARE_READ or WinNT.FILE_SHARE_WRITE or WinNT.FILE_SHARE_DELETE,
                    null, WinNT.OPEN_EXISTING, WinNT.FILE_FLAG_OPEN_REPARSE_POINT, null,
                )
                if (handle == WinBase.INVALID_HANDLE_VALUE) throw DistributionException("cannot inspect component link count")
                try {
                    val info = WinBase.FILE_STANDARD_INFO()
                    if (!Kernel32.INSTANCE.GetFileInformationByHandleEx(
                            handle, WinBase.FileStandardInfo, info.pointer, WinDef.DWORD(info.size().toLong()),
                        )
                    ) throw DistributionException("cannot inspect component link count")
                    info.read()
                    Integer.toUnsignedLong(info.NumberOfLinks)
                } finally {
                    if (!Kernel32.INSTANCE.CloseHandle(handle)) throw DistributionException("cannot close component inspection handle")
                }
            }
            else -> throw DistributionException("unsupported component filesystem platform")
        }
        if (links != 1L) throw DistributionException("hardlinked component input is forbidden")
    }

    private fun validatePath(value: String, field: String): String {
        if (value.isEmpty() || value.startsWith('/') || value.endsWith('/') || '\\' in value || '\u0000' in value ||
            Normalizer.normalize(value, Normalizer.Form.NFC) != value
        ) {
            throw DistributionException("invalid $field")
        }
        val segments = value.split('/')
        if (segments.any { it.isEmpty() || it == "." || it == ".." }) throw DistributionException("invalid $field")
        return value
    }

    private fun requireStrictOrder(values: List<String>, label: String) {
        for (index in 1 until values.size) {
            if (compareUnsignedUtf8(values[index - 1], values[index]) >= 0) {
                throw DistributionException("$label are not strictly UTF-8 sorted")
            }
        }
    }

    private fun requireNoPathCollisions(values: List<String>, label: String) {
        if (values.toSet().size != values.size) throw DistributionException("duplicate $label")
        val folded = HashSet<String>()
        for (value in values) {
            if (!folded.add(value.lowercase(Locale.ROOT))) throw DistributionException("case-fold collision in $label")
        }
    }

    internal fun compareUnsignedUtf8(left: String, right: String): Int {
        val leftBytes = left.toByteArray(StandardCharsets.UTF_8)
        val rightBytes = right.toByteArray(StandardCharsets.UTF_8)
        val count = minOf(leftBytes.size, rightBytes.size)
        for (index in 0 until count) {
            val comparison = (leftBytes[index].toInt() and 0xff) - (rightBytes[index].toInt() and 0xff)
            if (comparison != 0) return comparison
        }
        return leftBytes.size - rightBytes.size
    }

    internal fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun requireU32(value: Long, field: String) {
        if (value !in 0..0xffff_ffffL) throw DistributionException("$field requires ZIP64")
    }

    private fun octal(value: String): Long = value.toLong(8)

    private class ByteWriter {
        private val output = ByteArrayOutputStream()
        fun u1(value: Int) = apply { output.write(value and 0xff) }
        fun u2(value: Int) = apply { u1(value); u1(value ushr 8) }
        fun u4(value: Int) = u4(value.toLong() and 0xffff_ffffL)
        fun u4(value: Long) = apply { repeat(4) { u1((value ushr (it * 8)).toInt()) } }
        fun bytes(value: ByteArray) = apply { output.write(value) }
        fun toByteArray(): ByteArray = output.toByteArray()
    }

    private fun LinkedHashMap<String, Any?>.requireKeys(keys: List<String>, label: String) {
        if (this.keys.toList() != keys) throw DistributionException("$label fields are missing, extra, or reordered")
    }

    private fun LinkedHashMap<String, Any?>.string(key: String): String =
        this[key] as? String ?: throw DistributionException("$key must be a string")

    private fun LinkedHashMap<String, Any?>.nullableString(key: String): String? {
        val value = this[key]
        if (value != null && value !is String) throw DistributionException("$key must be string or null")
        return value
    }

    private fun LinkedHashMap<String, Any?>.long(key: String): Long =
        this[key] as? Long ?: throw DistributionException("$key must be an integer")

    private fun LinkedHashMap<String, Any?>.array(key: String): List<Any?> =
        this[key] as? List<Any?> ?: throw DistributionException("$key must be an array")

    private fun LinkedHashMap<String, Any?>.hash(key: String): String =
        string(key).also { if (!SHA256.matches(it)) throw DistributionException("$key must be SHA-256") }

    @Suppress("UNCHECKED_CAST")
    private fun Any?.asObject(label: String): LinkedHashMap<String, Any?> =
        this as? LinkedHashMap<String, Any?> ?: throw DistributionException("$label must be an object")
}

internal object StrictJson {
    fun parse(bytes: ByteArray): Any? {
        if (bytes.isEmpty() || bytes.size >= 3 && bytes[0] == 0xef.toByte() && bytes[1] == 0xbb.toByte() && bytes[2] == 0xbf.toByte()) {
            throw DistributionException("JSON must be non-empty UTF-8 without BOM")
        }
        val text = try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (failure: Exception) {
            throw DistributionException("invalid UTF-8 JSON", failure)
        }
        return Parser(text).parse()
    }

    private class Parser(private val text: String) {
        private var index = 0

        fun parse(): Any? {
            skipWhitespace()
            val value = value()
            skipWhitespace()
            if (index != text.length) fail("trailing JSON data")
            return value
        }

        private fun value(): Any? {
            if (index >= text.length) fail("unexpected end")
            return when (text[index]) {
                '{' -> objectValue()
                '[' -> arrayValue()
                '"' -> stringValue()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                '-', in '0'..'9' -> integerValue()
                else -> fail("invalid JSON token")
            }
        }

        private fun objectValue(): LinkedHashMap<String, Any?> {
            expect('{')
            skipWhitespace()
            val result = LinkedHashMap<String, Any?>()
            if (take('}')) return result
            while (true) {
                if (index >= text.length || text[index] != '"') fail("object key must be a string")
                val key = stringValue()
                if (result.containsKey(key)) fail("duplicate JSON key")
                skipWhitespace()
                expect(':')
                skipWhitespace()
                result[key] = value()
                skipWhitespace()
                if (take('}')) return result
                expect(',')
                skipWhitespace()
            }
        }

        private fun arrayValue(): List<Any?> {
            expect('[')
            skipWhitespace()
            val result = ArrayList<Any?>()
            if (take(']')) return result
            while (true) {
                result += value()
                skipWhitespace()
                if (take(']')) return result
                expect(',')
                skipWhitespace()
            }
        }

        private fun stringValue(): String {
            expect('"')
            val result = StringBuilder()
            while (index < text.length) {
                val character = text[index++]
                when {
                    character == '"' -> return result.toString()
                    character == '\\' -> {
                        if (index >= text.length) fail("unterminated escape")
                        when (val escaped = text[index++]) {
                            '"', '\\', '/' -> result.append(escaped)
                            'b' -> result.append('\b')
                            'f' -> result.append('\u000c')
                            'n' -> result.append('\n')
                            'r' -> result.append('\r')
                            't' -> result.append('\t')
                            'u' -> appendUnicodeEscape(result)
                            else -> fail("invalid string escape")
                        }
                    }
                    character.code < 0x20 -> fail("unescaped control character")
                    Character.isHighSurrogate(character) -> {
                        if (index >= text.length || !Character.isLowSurrogate(text[index])) fail("unpaired surrogate")
                        result.append(character).append(text[index++])
                    }
                    Character.isLowSurrogate(character) -> fail("unpaired surrogate")
                    else -> result.append(character)
                }
            }
            fail("unterminated string")
        }

        private fun appendUnicodeEscape(result: StringBuilder) {
            val first = hex4()
            val firstChar = first.toChar()
            if (Character.isHighSurrogate(firstChar)) {
                if (index + 2 > text.length || text[index] != '\\' || text[index + 1] != 'u') fail("unpaired escaped surrogate")
                index += 2
                val secondChar = hex4().toChar()
                if (!Character.isLowSurrogate(secondChar)) fail("unpaired escaped surrogate")
                result.append(firstChar).append(secondChar)
            } else {
                if (Character.isLowSurrogate(firstChar)) fail("unpaired escaped surrogate")
                result.append(firstChar)
            }
        }

        private fun hex4(): Int {
            if (index + 4 > text.length) fail("short unicode escape")
            var value = 0
            repeat(4) {
                val digit = text[index++].digitToIntOrNull(16) ?: fail("invalid unicode escape")
                value = value * 16 + digit
            }
            return value
        }

        private fun integerValue(): Long {
            val start = index
            if (take('-') && index >= text.length) fail("short number")
            if (take('0')) {
                if (index < text.length && text[index].isDigit()) fail("leading zero")
            } else {
                if (index >= text.length || text[index] !in '1'..'9') fail("invalid number")
                while (index < text.length && text[index].isDigit()) index++
            }
            if (index < text.length && text[index] in setOf('.', 'e', 'E', '+')) fail("only integer JSON numbers are accepted")
            return text.substring(start, index).toLongOrNull() ?: fail("integer out of range")
        }

        private fun <T> literal(expected: String, value: T): T {
            if (!text.startsWith(expected, index)) fail("invalid literal")
            index += expected.length
            return value
        }

        private fun skipWhitespace() {
            while (index < text.length && text[index] in setOf(' ', '\t', '\r', '\n')) index++
        }

        private fun expect(expected: Char) {
            if (!take(expected)) fail("expected $expected")
        }

        private fun take(expected: Char): Boolean {
            if (index < text.length && text[index] == expected) {
                index++
                return true
            }
            return false
        }

        private fun fail(message: String): Nothing = throw DistributionException("$message at character $index")
    }
}

internal object CanonicalJson {
    fun prettyBytes(value: Any?): ByteArray = (pretty(value) + "\n").toByteArray(StandardCharsets.UTF_8)

    fun pretty(value: Any?): String = buildString { appendValue(value, 0) }

    private fun StringBuilder.appendValue(value: Any?, depth: Int) {
        when (value) {
            null -> append("null")
            is String -> appendString(value)
            is Boolean, is Long, is Int -> append(value)
            is Map<*, *> -> appendObject(value, depth)
            is List<*> -> appendArray(value, depth)
            else -> throw DistributionException("unsupported canonical JSON value")
        }
    }

    private fun StringBuilder.appendObject(value: Map<*, *>, depth: Int) {
        append('{')
        if (value.isNotEmpty()) append('\n')
        value.entries.forEachIndexed { index, entry ->
            indent(depth + 1)
            appendString(entry.key as? String ?: throw DistributionException("JSON key must be string"))
            append(": ")
            appendValue(entry.value, depth + 1)
            if (index != value.size - 1) append(',')
            append('\n')
        }
        if (value.isNotEmpty()) indent(depth)
        append('}')
    }

    private fun StringBuilder.appendArray(value: List<*>, depth: Int) {
        append('[')
        if (value.isNotEmpty()) append('\n')
        value.forEachIndexed { index, item ->
            indent(depth + 1)
            appendValue(item, depth + 1)
            if (index != value.size - 1) append(',')
            append('\n')
        }
        if (value.isNotEmpty()) indent(depth)
        append(']')
    }

    private fun StringBuilder.appendString(value: String) {
        append('"')
        value.forEach { character ->
            when (character) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000c' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (character.code < 0x20) append("\\u%04x".format(character.code)) else append(character)
            }
        }
        append('"')
    }

    private fun StringBuilder.indent(depth: Int) = repeat(depth) { append("  ") }
}
