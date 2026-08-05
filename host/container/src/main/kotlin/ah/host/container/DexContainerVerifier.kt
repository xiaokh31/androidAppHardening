package ah.host.container

import ah.host.inspector.DexSummary
import ah.host.inspector.InspectionLimits
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.zip.DataFormatException
import java.util.zip.Inflater

class DexContainerVerifier internal constructor(observer: ContainerObserver) {
    private val observer = cleanupTrackingObserver(observer)

    constructor() : this(NO_CONTAINER_OBSERVER)

    fun verify(container: Path, expected: ExpectedBinding): DexContainerDescriptor {
        val initialHash = hashContainer(container)
        var cek: ByteArray? = null
        var primaryFailure: Throwable? = null
        try {
            return expected.withVerificationMaterial(observer) { config, nativeShare ->
                FileChannel.open(container, StandardOpenOption.READ).use { channel ->
                val size = channel.size()
                if (size <= AhConstants.HEADER_BYTES || size > InspectionLimits.MAX_APK_BYTES) limit("containerSize")
                val headerBytes = channel.readExact(0, AhConstants.HEADER_BYTES)
                val header = AhdcV2Codec.parseHeader(headerBytes)
                val metadataSize = metadataSize(header)
                if (checkedAdd(metadataSize, header.payloadSize, "containerSize") != size) format("containerSize")

                val signerOffset = AhConstants.HEADER_BYTES.toLong()
                val signerBytes = channel.readExact(signerOffset, header.signerPolicySize)
                val signer = AhdcV2Codec.parseSpv1(signerBytes)
                validateExpectedPublicBinding(expected, signer.first, signer.second)

                val recordOffset = checkedAdd(signerOffset, header.signerPolicySize.toLong(), "recordOffset")
                val recordBytes = ArrayList<ByteArray>(header.dexCount)
                val records = ArrayList<RecordV2>(header.dexCount)
                repeat(header.dexCount) { index ->
                    val bytes = channel.readExact(recordOffset + index.toLong() * AhConstants.RECORD_BYTES, AhConstants.RECORD_BYTES)
                    recordBytes += bytes
                    records += AhdcV2Codec.parseRecord(bytes)
                }
                validateRecordTopology(records, header, expected.expectedDex())

                val chunkTableOffset = checkedAdd(recordOffset, header.recordTableSize.toLong(), "chunkTableOffset")
                validateChunkTable(channel, chunkTableOffset, records, header)

                val actualConfigHash = ContainerCrypto.sha256(config)
                try {
                    if (!actualConfigHash.constantTimeEquals(header.configSha256)) {
                        throw ContainerException(ContainerErrorCode.CONTAINER_AUTH_FAILED, "configSha256")
                    }
                } finally {
                    actualConfigHash.fill(0)
                }
                val packageDigest = expected.packageDigest()
                val signerDigest = expected.signerDigest()
                try {
                    cek = ConfigV2Codec.recoverCek(
                        config,
                        nativeShare,
                        header.buildId,
                        header.keySlotId,
                        signerDigest,
                        packageDigest,
                    )
                    verifyManifest(channel, chunkTableOffset, header, headerBytes, signerBytes, recordBytes, cek)
                    verifyPayload(
                        channel,
                        metadataSize,
                        headerBytes,
                        records,
                        recordBytes,
                        packageDigest,
                        signerDigest,
                        cek,
                    )
                } finally {
                    packageDigest.fill(0)
                    signerDigest.fill(0)
                }

                val finalHash = hashContainer(container)
                if (!finalHash.constantTimeEquals(initialHash)) {
                    finalHash.fill(0)
                    throw ContainerException(ContainerErrorCode.CONTAINER_INPUT_CHANGED, "containerChanged")
                }
                    descriptor(expected, signer.first, signer.second, records, finalHash)
                }
            }
        } catch (exception: ContainerException) {
            primaryFailure = exception
            throw exception
        } catch (exception: IOException) {
            val mapped = ContainerException(ContainerErrorCode.CONTAINER_FORMAT, "io", exception)
            primaryFailure = mapped
            throw mapped
        } catch (failure: Throwable) {
            primaryFailure = failure
            throw failure
        } finally {
            cek?.let { wipe("verify.cek", it, observer) }
            initialHash.fill(0)
            observer.finish(primaryFailure)
        }
    }

    private fun validateExpectedPublicBinding(
        expected: ExpectedBinding,
        actualSigner: ByteArray,
        actualLineage: List<ByteArray>,
    ) {
        val packageDigest = ContainerCrypto.sha256(expected.packageName.toByteArray(Charsets.UTF_8))
        val expectedPackageDigest = expected.packageDigest()
        val expectedSigner = expected.signerDigest()
        val expectedLineage = expected.lineageDigests()
        try {
            if (!packageDigest.constantTimeEquals(expectedPackageDigest)) format("expectedPackage")
            if (!actualSigner.constantTimeEquals(expectedSigner) || actualLineage.size != expectedLineage.size ||
                actualLineage.indices.any { !actualLineage[it].constantTimeEquals(expectedLineage[it]) }
            ) throw ContainerException(ContainerErrorCode.CONTAINER_AUTH_FAILED, "signerPolicy")
        } finally {
            packageDigest.fill(0)
            expectedPackageDigest.fill(0)
            expectedSigner.fill(0)
            expectedLineage.forEach(ByteArray::fillZero)
        }
    }

    private fun validateRecordTopology(records: List<RecordV2>, header: HeaderV2, expectedDex: List<DexSummary>) {
        if (expectedDex.size != records.size) throw ContainerException(ContainerErrorCode.CONTAINER_AUTH_FAILED, "dexCount")
        var firstChunk = 0
        var payloadOffset = 0L
        records.forEachIndexed { index, record ->
            val expected = expectedDex[index]
            if (record.ordinal != index || record.name != canonicalDexName(index) ||
                record.firstChunkIndex != firstChunk || record.payloadOffset != payloadOffset ||
                record.chunkCount != chunksForLength(record.compressedLength)
            ) format("recordTopology")
            if (expected.ordinal != index || expected.entryName != record.name || expected.fileSize != record.originalLength ||
                !expected.sha256.constantTimeEquals(record.originalSha256)
            ) throw ContainerException(ContainerErrorCode.CONTAINER_AUTH_FAILED, "dexBinding")
            firstChunk = checkedAddInt(firstChunk, record.chunkCount, "chunkCount")
            payloadOffset = checkedAdd(
                payloadOffset,
                record.compressedLength + record.chunkCount.toLong() * AhConstants.GCM_TAG_BYTES,
                "payloadSize",
            )
        }
        if (firstChunk != header.chunkCount || payloadOffset != header.payloadSize) format("recordTotals")
    }

    private fun validateChunkTable(
        channel: FileChannel,
        offset: Long,
        records: List<RecordV2>,
        header: HeaderV2,
    ) {
        var globalIndex = 0
        records.forEach { record ->
            repeat(record.chunkCount) { chunkOrdinal ->
                val bytes = channel.readExact(offset + globalIndex.toLong() * AhConstants.CHUNK_BYTES, AhConstants.CHUNK_BYTES)
                val actual = AhdcV2Codec.parseChunk(bytes)
                if (actual != expectedChunk(record, chunkOrdinal)) format("chunkTopology")
                globalIndex++
            }
        }
        if (globalIndex != header.chunkCount) format("chunkCount")
    }

    private fun verifyManifest(
        channel: FileChannel,
        chunkTableOffset: Long,
        header: HeaderV2,
        headerBytes: ByteArray,
        signerBytes: ByteArray,
        recordBytes: List<ByteArray>,
        cek: ByteArray,
    ) {
        val key = ContainerCrypto.manifestKey(cek, header.buildId)
        val zeroHeader = headerBytes.copyOf().also { bytes -> bytes.fill(0, 104, 136) }
        try {
            val mac = ContainerCrypto.newHmacSha256(key)
            mac.update(zeroHeader)
            mac.update(signerBytes)
            recordBytes.forEach(mac::update)
            repeat(header.chunkCount) { index ->
                mac.update(channel.readExact(chunkTableOffset + index.toLong() * AhConstants.CHUNK_BYTES, AhConstants.CHUNK_BYTES))
            }
            val actual = mac.doFinal()
            try {
                if (!actual.constantTimeEquals(header.manifestMac)) {
                    throw ContainerException(ContainerErrorCode.CONTAINER_AUTH_FAILED, "manifestMac")
                }
            } finally {
                actual.fill(0)
            }
        } finally {
            wipe("verify.manifestKey", key, observer)
            zeroHeader.fill(0)
        }
    }

    private fun verifyPayload(
        channel: FileChannel,
        payloadBase: Long,
        headerBytes: ByteArray,
        records: List<RecordV2>,
        recordBytes: List<ByteArray>,
        packageDigest: ByteArray,
        signerDigest: ByteArray,
        cek: ByteArray,
    ) {
        val headerVersion = headerBytes.headerVersionBytes()
        val buildId = slice(headerBytes, 40, 16)
        val keySlotId = slice(headerBytes, 56, 16)
        try {
            records.forEachIndexed { index, record ->
                val key = ContainerCrypto.recordKey(cek, buildId, record.ordinal)
                try {
                    verifyRecordPayload(
                        channel,
                        payloadBase,
                        headerVersion,
                        buildId,
                        keySlotId,
                        packageDigest,
                        signerDigest,
                        record,
                        recordBytes[index],
                        key,
                    )
                } finally {
                    wipe("verify.recordKey", key, observer)
                }
            }
        } finally {
            headerVersion.fill(0)
            buildId.fill(0)
            keySlotId.fill(0)
        }
    }

    private fun verifyRecordPayload(
        channel: FileChannel,
        payloadBase: Long,
        headerVersion: ByteArray,
        buildId: ByteArray,
        keySlotId: ByteArray,
        packageDigest: ByteArray,
        signerDigest: ByteArray,
        record: RecordV2,
        recordBytes: ByteArray,
        key: ByteArray,
    ) {
        val inflater = Inflater(false)
        val output = ByteArray(AhConstants.CHUNK_PLAINTEXT_MAX)
        val digest = MessageDigest.getInstance("SHA-256")
        observer.allocated("inflate.output", output.size)
        var outputLength = 0L
        try {
            repeat(record.chunkCount) { chunkOrdinal ->
                val chunk = expectedChunk(record, chunkOrdinal)
                val chunkBytes = AhdcV2Codec.chunk(chunk)
                val ciphertext = channel.readExact(
                    checkedAdd(payloadBase, chunk.payloadOffset, "payloadOffset"),
                    checkedAddInt(chunk.plaintextLength, AhConstants.GCM_TAG_BYTES, "ciphertextLength"),
                )
                observer.allocated("verify.ciphertext", ciphertext.size)
                val nonce = ContainerCrypto.chunkNonce(record.noncePrefix, chunkOrdinal)
                val aad = ContainerCrypto.chunkAad(
                    headerVersion,
                    buildId,
                    keySlotId,
                    signerDigest,
                    packageDigest,
                    recordBytes,
                    chunkBytes,
                )
                observer.allocated("verify.aad", aad.size)
                var compressed: ByteArray? = null
                try {
                    compressed = ContainerCrypto.aesGcmDecrypt(key, nonce, aad, ciphertext)
                    observer.allocated("verify.compressed", compressed.size)
                    if (compressed.size != chunk.plaintextLength) format("chunkPlaintextLength")
                    observer.authenticatedBeforeInflate(record.ordinal, chunkOrdinal)
                    inflater.setInput(compressed)
                    outputLength = drainInflater(inflater, digest, output, outputLength, record.originalLength)
                    if (inflater.finished() &&
                        (chunkOrdinal != record.chunkCount - 1 || inflater.remaining != 0)
                    ) format("zlibTrailing")
                } finally {
                    wipe("verify.ciphertext", ciphertext, observer)
                    compressed?.let { wipe("verify.compressed", it, observer) }
                    nonce.fill(0)
                    wipe("verify.aad", aad, observer)
                    chunkBytes.fill(0)
                }
            }
            if (!inflater.finished() || inflater.needsDictionary() || inflater.remaining != 0) format("zlibEnd")
            val actualDigest = digest.digest()
            try {
                if (outputLength != record.originalLength || !actualDigest.constantTimeEquals(record.originalSha256)) {
                    throw ContainerException(ContainerErrorCode.CONTAINER_AUTH_FAILED, "dexDigest")
                }
            } finally {
                actualDigest.fill(0)
            }
        } finally {
            inflater.end()
            wipe("inflate.output", output, observer)
        }
    }

    private fun drainInflater(
        inflater: Inflater,
        digest: MessageDigest,
        output: ByteArray,
        startLength: Long,
        declaredLength: Long,
    ): Long {
        var length = startLength
        try {
            while (!inflater.needsInput() && !inflater.finished()) {
                val count = inflater.inflate(output)
                if (count > 0) {
                    length = checkedAdd(length, count.toLong(), "inflatedLength")
                    if (length > declaredLength) limit("inflatedLength")
                    digest.update(output, 0, count)
                    output.fill(0, 0, count)
                } else if (inflater.needsDictionary()) {
                    format("zlibDictionary")
                } else {
                    format("zlibStall")
                }
            }
            return length
        } catch (exception: DataFormatException) {
            throw ContainerException(ContainerErrorCode.CONTAINER_FORMAT, "zlib", exception)
        }
    }

    private fun metadataSize(header: HeaderV2): Long {
        var size = AhConstants.HEADER_BYTES.toLong()
        size = checkedAdd(size, header.signerPolicySize.toLong(), "metadataSize")
        size = checkedAdd(size, header.recordTableSize.toLong(), "metadataSize")
        return checkedAdd(size, header.chunkTableSize.toLong(), "metadataSize")
    }

    private fun descriptor(
        expected: ExpectedBinding,
        signer: ByteArray,
        lineage: List<ByteArray>,
        records: List<RecordV2>,
        hash: ByteArray,
    ): DexContainerDescriptor = DexContainerDescriptor(
        AhConstants.MAJOR,
        AhConstants.MINOR,
        expected.packageName,
        signer,
        lineage,
        records.map { record -> DexRecordDescriptor(
            record.ordinal,
            record.name,
            record.originalLength,
            record.compressedLength,
            record.chunkCount,
            record.firstChunkIndex,
            record.payloadOffset,
            record.originalSha256,
        ) },
        hash,
    )

    private fun hashContainer(path: Path): ByteArray = try {
        ContainerCrypto.sha256(path)
    } catch (exception: IOException) {
        throw ContainerException(ContainerErrorCode.CONTAINER_FORMAT, "containerIo", exception)
    }
}

private fun ByteArray.fillZero() = fill(0)
