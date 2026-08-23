package ah.host.container

import ah.host.inspector.ApkInspection
import ah.host.inspector.DexSummary
import java.io.ByteArrayOutputStream
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.Comparator
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.Inflater
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** Read-only verification helpers for the immutable retained M3-12 profile package. */
internal object M310RetainedProfileSupport {
    private const val SLOT_BYTES = 104

    internal fun decryptPayload(
        container: Path,
        config: ByteArray,
        nativeShare: ByteArray,
        packageDigest: ByteArray,
        signerDigest: ByteArray,
    ): List<ByteArray> = FileChannel.open(container, StandardOpenOption.READ).use { channel ->
        val headerBytes = channel.readExact(0, AhConstants.HEADER_BYTES)
        val header = AhdcV2Codec.parseHeader(headerBytes)
        val signerOffset = AhConstants.HEADER_BYTES.toLong()
        val recordOffset = signerOffset + header.signerPolicySize
        val chunkTableOffset = recordOffset + header.recordTableSize
        val payloadBase = chunkTableOffset + header.chunkTableSize
        val records = (0 until header.dexCount).map { index ->
            val bytes = channel.readExact(recordOffset + index.toLong() * AhConstants.RECORD_BYTES, AhConstants.RECORD_BYTES)
            bytes to AhdcV2Codec.parseRecord(bytes)
        }
        val cek = ConfigV2Codec.recoverCek(
            config,
            nativeShare,
            header.buildId,
            header.keySlotId,
            signerDigest,
            packageDigest,
        )
        try {
            records.map { (recordBytes, record) ->
                val compressed = ByteArrayOutputStream(record.compressedLength.toInt())
                val key = ContainerCrypto.recordKey(cek, header.buildId, record.ordinal)
                try {
                    repeat(record.chunkCount) { chunkOrdinal ->
                        val chunkIndex = record.firstChunkIndex + chunkOrdinal
                        val chunkBytes = channel.readExact(
                            chunkTableOffset + chunkIndex.toLong() * AhConstants.CHUNK_BYTES,
                            AhConstants.CHUNK_BYTES,
                        )
                        val chunk = AhdcV2Codec.parseChunk(chunkBytes)
                        require(chunk == expectedChunk(record, chunkOrdinal)) { "canonical chunk topology differs" }
                        val ciphertext = channel.readExact(
                            payloadBase + chunk.payloadOffset,
                            chunk.plaintextLength + AhConstants.GCM_TAG_BYTES,
                        )
                        val nonce = ContainerCrypto.chunkNonce(record.noncePrefix, chunkOrdinal)
                        val aad = ContainerCrypto.chunkAad(
                            headerBytes.headerVersionBytes(),
                            header.buildId,
                            header.keySlotId,
                            signerDigest,
                            packageDigest,
                            recordBytes,
                            chunkBytes,
                        )
                        val plaintext = ContainerCrypto.aesGcmDecrypt(key, nonce, aad, ciphertext)
                        try {
                            compressed.write(plaintext)
                        } finally {
                            listOf(ciphertext, nonce, aad, plaintext, chunkBytes).forEach { it.fill(0) }
                        }
                    }
                    val compressedBytes = compressed.toByteArray()
                    try {
                        require(compressedBytes.size.toLong() == record.compressedLength) { "compressed length differs" }
                        inflateExact(compressedBytes, record.originalLength.toInt()).also { dex ->
                            val digest = ContainerCrypto.sha256(dex)
                            try {
                                require(digest.contentEquals(record.originalSha256)) { "payload DEX digest differs" }
                            } finally {
                                digest.fill(0)
                            }
                        }
                    } finally {
                        compressedBytes.fill(0)
                    }
                } finally {
                    key.fill(0)
                }
            }
        } finally {
            cek.fill(0)
            headerBytes.fill(0)
        }
    }

    private fun inflateExact(compressed: ByteArray, expectedSize: Int): ByteArray {
        val inflater = Inflater()
        val output = ByteArray(expectedSize)
        return try {
            inflater.setInput(compressed)
            var offset = 0
            while (!inflater.finished() && offset < output.size) {
                val count = inflater.inflate(output, offset, output.size - offset)
                require(count > 0 || inflater.finished()) { "zlib payload made no progress" }
                offset += count
            }
            require(inflater.finished() && inflater.remaining == 0 && offset == expectedSize) {
                "zlib payload boundary differs"
            }
            output
        } catch (failure: Throwable) {
            output.fill(0)
            throw failure
        } finally {
            inflater.end()
        }
    }

    internal data class RuntimeSlots(val rNative: ByteArray)

    internal fun readAllRuntimeSlots(entries: Map<String, EntryData>, config: ByteArray): RuntimeSlots {
        val expectedKeySlot = config.copyOfRange(40, 56)
        val expectedBuild = config.copyOfRange(24, 40)
        var expectedShare: ByteArray? = null
        try {
            for (abi in RuntimeAbi.entries) {
                val bytes = entries.getValue("lib/${abi.directoryName}/libah_runtime.so").bytes
                val offset = locateSlot(bytes, abi)
                require(bytes.copyOfRange(offset + 8, offset + 24).contentEquals(expectedKeySlot) &&
                    bytes.copyOfRange(offset + 24, offset + 40).contentEquals(expectedBuild)
                ) { "runtime/config binding differs for ${abi.directoryName}" }
                val share = bytes.copyOfRange(offset + 40, offset + 72)
                if (expectedShare == null) expectedShare = share else {
                    require(expectedShare.contentEquals(share)) { "runtime shares differ by ABI" }
                    share.fill(0)
                }
            }
            return RuntimeSlots(requireNotNull(expectedShare))
        } finally {
            expectedKeySlot.fill(0)
            expectedBuild.fill(0)
        }
    }

    internal fun locateSlot(bytes: ByteArray, abi: RuntimeAbi): Int {
        val magic = "AHS1".toByteArray(Charsets.US_ASCII)
        val matches = (0..bytes.size - SLOT_BYTES).filter { offset ->
            magic.indices.all { bytes[offset + it] == magic[it] } &&
                u2(bytes, offset + 4) == 1 && u2(bytes, offset + 6) == abi.abiId
        }
        require(matches.size == 1) { "runtime share slot count differs for ${abi.directoryName}" }
        val offset = matches.single()
        val digest = MessageDigest.getInstance("SHA-256").apply { update(bytes, offset, 72) }.digest()
        try {
            require(digest.contentEquals(bytes.copyOfRange(offset + 72, offset + SLOT_BYTES))) {
                "runtime share digest differs for ${abi.directoryName}"
            }
        } finally {
            digest.fill(0)
        }
        return offset
    }

    internal data class EntryData(val bytes: ByteArray, val method: Int)

    internal fun readEntries(apk: Path): Map<String, EntryData> = ZipFile(apk.toFile()).use { zip ->
        val result = linkedMapOf<String, EntryData>()
        val entries = zip.entries()
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            require(!entry.isDirectory && result.put(entry.name, EntryData(zip.getInputStream(entry).readBytes(), entry.method)) == null) {
                "APK entry topology differs"
            }
        }
        result
    }

    /** Writes only scratch mutation APKs used by the verifier's fail-closed tests. */
    internal fun writeApk(source: Map<String, EntryData>, replacements: Map<String, ByteArray>, output: Path) {
        require(replacements.keys.all(source::containsKey)) { "replacement entry is absent" }
        ZipOutputStream(Files.newOutputStream(output)).use { zip ->
            zip.setLevel(Deflater.BEST_COMPRESSION)
            for (name in source.keys.filterNot(::isSignatureEntry).sorted()) {
                val value = replacements[name] ?: source.getValue(name).bytes
                val method = source.getValue(name).method
                val entry = ZipEntry(name).apply {
                    time = 0L
                    this.method = method
                    if (method == ZipEntry.STORED) {
                        size = value.size.toLong()
                        compressedSize = value.size.toLong()
                        crc = CRC32().apply { update(value) }.value
                    }
                }
                zip.putNextEntry(entry)
                zip.write(value)
                zip.closeEntry()
            }
        }
    }

    internal fun isSignatureEntry(name: String): Boolean {
        val upper = name.uppercase()
        if (!upper.startsWith("META-INF/")) return false
        val leaf = upper.removePrefix("META-INF/")
        return leaf == "MANIFEST.MF" || leaf.endsWith(".SF") || leaf.endsWith(".RSA") ||
            leaf.endsWith(".DSA") || leaf.endsWith(".EC")
    }

    internal fun requireExactOriginal(path: Path, size: Long, digest: String, label: String) {
        require(Files.size(path) == size && sha256(path) == digest) { "$label original bytes differ" }
    }

    internal fun signerOnlyInspection(path: Path, canonical: ApkInspection): ApkInspection = ApkInspection(
        ContainerCrypto.sha256(path),
        canonical.manifest,
        canonical.zipEntries,
        canonical.dexEntries,
        canonical.nativeAbis,
        canonical.findings,
        canonical.compatibilityRulesVersion,
        canonical.limitsApplied,
    )

    internal fun toContainerInspection(value: ApkInspection): ApkInspection = ApkInspection(
        value.inputSha256,
        value.manifest,
        value.zipEntries,
        value.dexEntries.map { summary ->
            DexSummary(
                summary.entryName,
                summary.ordinal - 1,
                summary.fileSize,
                summary.classCount,
                summary.sha256,
            )
        },
        value.nativeAbis,
        value.findings,
        value.compatibilityRulesVersion,
        value.limitsApplied,
    )

    internal fun sha256(path: Path): String = sha256(Files.readAllBytes(path))

    internal fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 0xff) }

    internal fun deleteTree(root: Path) {
        if (!Files.exists(root)) return
        Files.walk(root).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }
}
