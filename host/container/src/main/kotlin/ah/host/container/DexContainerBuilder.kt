package ah.host.container

import ah.host.inspector.ApkInspection
import ah.host.inspector.DexSummary
import ah.host.inspector.InspectionLimits
import ah.host.inspector.SignerPolicyV1
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.zip.ZipFile

class DexContainerBuilder internal constructor(
    private val inputApk: Path,
    private val random: ContainerRandom,
    observer: ContainerObserver,
    private val betweenPasses: (() -> Unit)?,
    private val atomicMove: ((Path, Path) -> Unit)?,
) {
    private val observer = cleanupTrackingObserver(observer)

    internal constructor(
        inputApk: Path,
        random: ContainerRandom,
        observer: ContainerObserver,
        betweenPasses: (() -> Unit)?,
    ) : this(inputApk, random, observer, betweenPasses, null)

    constructor(inputApk: Path) : this(inputApk, SecureContainerRandom.create(), NO_CONTAINER_OBSERVER, null, null)

    fun build(inspection: ApkInspection, signer: SignerPolicyV1, encryptedTemp: Path): ContainerBuildResult {
        val dex = validateInspection(inspection, signer)
        val targetAbis = targetAbis(inspection)
        val destination = encryptedTemp.toAbsolutePath().normalize()
        if (Files.exists(destination)) format("outputExists")
        val parent = destination.parent ?: format("outputParent")
        val initialInputHash = hashInput()
        var observations: List<CompressionObservation> = emptyList()
        var secrets: BuildSecrets? = null
        var config: ConfigV2Material? = null
        var outputPart: Path? = null
        var unpublishedPlan: KeyPackagingPlanV2? = null
        var sensitiveCleared = false
        var primaryFailure: Throwable? = null

        fun clearSensitive(primary: Throwable?) {
            if (sensitiveCleared) return
            config?.let { material ->
                wipe("config", material.bytes, observer)
                wipe("rNative", material.rNative, observer)
            }
            secrets?.clear(observer)
            observations.forEachIndexed { index, value -> wipe("pass1.digest.$index", value.originalSha256, observer) }
            sensitiveCleared = true
            observer.finish(primary)
        }

        try {
            if (!initialInputHash.constantTimeEquals(inspection.inputSha256)) inputChanged("inputSha256")
            observations = observeDex(dex)
            val buildSecrets = BuildSecrets.create(random, dex.size, observer)
            secrets = buildSecrets
            config = ConfigV2Codec.build(
                inspection.appComponentFactoryClass,
                buildSecrets.buildId,
                buildSecrets.keySlotId,
                signer.currentCertificateSha256,
                inspection.packageNameSha256,
                buildSecrets.cek,
                buildSecrets.root,
                buildSecrets.rJava,
                buildSecrets.wrapNonce,
            )
            val records = buildRecords(observations, buildSecrets.noncePrefixes)
            val signerBytes = AhdcV2Codec.spv1(signer)
            val recordBytes = records.map(AhdcV2Codec::record)
            val headerWithoutMac = buildHeader(records, signerBytes, config.bytes, buildSecrets, ByteArray(32))
            val manifestKey = ContainerCrypto.manifestKey(buildSecrets.cek, buildSecrets.buildId)
            val manifestMac = try {
                manifestMac(manifestKey, headerWithoutMac, signerBytes, recordBytes, records)
            } finally {
                wipe("manifest.key", manifestKey, observer)
            }
            val header = buildHeader(records, signerBytes, config.bytes, buildSecrets, manifestMac)
            wipe("manifest.mac", manifestMac, observer)
            val expectedSize = expectedFileSize(header, signerBytes.size, records)
            Files.createDirectories(parent)
            outputPart = Files.createTempFile(parent, ".ahdc-v2-", ".part")
            Files.newOutputStream(outputPart, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING).use { output ->
                output.write(header)
                output.write(signerBytes)
                recordBytes.forEach(output::write)
                records.forEach { record ->
                    repeat(record.chunkCount) { chunkOrdinal -> output.write(AhdcV2Codec.chunk(expectedChunk(record, chunkOrdinal))) }
                }
                betweenPasses?.invoke()
                writePayload(output, header, inspection, signer, dex, observations, records, recordBytes, buildSecrets)
            }
            if (!hashInput().constantTimeEquals(initialInputHash)) inputChanged("inputFinalHash")
            if (Files.size(outputPart) != expectedSize) format("outputSize")
            val containerHash = ContainerCrypto.sha256(outputPart)
            val descriptor = descriptor(inspection, signer, records, containerHash)
            val plan = KeyPackagingPlanV2(
                config.bytes,
                config.rNative,
                buildSecrets.buildId,
                buildSecrets.keySlotId,
                targetAbis,
                observer,
            )
            unpublishedPlan = plan
            val result = ContainerBuildResult(descriptor, plan)
            clearSensitive(null)
            moveAtomically(outputPart, destination)
            outputPart = null
            unpublishedPlan = null
            return result
        } catch (exception: ContainerException) {
            primaryFailure = exception
            throw exception
        } catch (exception: IOException) {
            val mapped = try {
                if (!hashInput().constantTimeEquals(initialInputHash)) {
                    ContainerException(ContainerErrorCode.CONTAINER_INPUT_CHANGED, "inputIo", exception)
                } else {
                    ContainerException(ContainerErrorCode.CONTAINER_FORMAT, "io", exception)
                }
            } catch (_: ContainerException) {
                ContainerException(ContainerErrorCode.CONTAINER_INPUT_CHANGED, "inputIo", exception)
            }
            primaryFailure = mapped
            throw mapped
        } catch (failure: Throwable) {
            primaryFailure = failure
            throw failure
        } finally {
            val failureWasInFlight = primaryFailure != null
            unpublishedPlan?.let { plan ->
                try {
                    plan.close()
                } catch (cleanup: Throwable) {
                    primaryFailure?.let { failure -> suppressCleanup(failure, cleanup) } ?: run { primaryFailure = cleanup }
                }
            }
            outputPart?.let { part ->
                try {
                    Files.deleteIfExists(part)
                } catch (cleanup: IOException) {
                    val mapped = ContainerException(ContainerErrorCode.CONTAINER_FORMAT, "outputCleanup", cleanup)
                    primaryFailure?.let { failure -> suppressCleanup(failure, mapped) } ?: run { primaryFailure = mapped }
                }
            }
            if (!sensitiveCleared) {
                try {
                    clearSensitive(primaryFailure)
                } catch (cleanup: Throwable) {
                    primaryFailure?.let { failure -> suppressCleanup(failure, cleanup) } ?: run { primaryFailure = cleanup }
                }
            }
            initialInputHash.fill(0)
            if (!failureWasInFlight) primaryFailure?.let { failure -> throw failure }
        }
    }

    private fun validateInspection(inspection: ApkInspection, signer: SignerPolicyV1): List<DexSummary> {
        if (signer.policyVersion != 1) version("signerPolicy")
        if (inspection.dexEntries.size !in 1..AhConstants.MAX_DEX) limit("dexCount")
        inspection.dexEntries.forEachIndexed { index, summary ->
            if (summary.ordinal != index || summary.entryName != canonicalDexName(index)) format("dexOrder")
            if (summary.fileSize !in 1..InspectionLimits.MAX_DEX_BYTES) limit("dexLength")
        }
        if (!ContainerCrypto.sha256(inspection.packageName.toByteArray(Charsets.UTF_8))
                .constantTimeEquals(inspection.packageNameSha256)
        ) format("packageDigest")
        return inspection.dexEntries
    }

    private fun observeDex(dex: List<DexSummary>): List<CompressionObservation> = ZipFile(inputApk.toFile()).use { zip ->
        dex.map { summary ->
            val entry = zip.getEntry(summary.entryName) ?: inputChanged("dexMissing")
            val observation = zip.getInputStream(entry).use { input -> observeCompression(input, observer) }
            if (observation.originalLength != summary.fileSize ||
                !observation.originalSha256.constantTimeEquals(summary.sha256)
            ) inputChanged("dexPass1")
            observation
        }
    }

    private fun buildRecords(values: List<CompressionObservation>, noncePrefixes: List<ByteArray>): List<RecordV2> {
        var firstChunk = 0
        var payloadOffset = 0L
        return values.mapIndexed { index, value ->
            val chunks = chunksForLength(value.compressedLength)
            val record = RecordV2(
                index,
                canonicalDexName(index),
                value.originalLength,
                value.compressedLength,
                chunks,
                firstChunk,
                payloadOffset,
                noncePrefixes[index],
                value.originalSha256,
            )
            firstChunk = checkedAddInt(firstChunk, chunks, "chunkCount")
            if (firstChunk > AhConstants.MAX_CHUNKS) limit("chunkCount")
            payloadOffset = checkedAdd(
                payloadOffset,
                checkedAdd(value.compressedLength, chunks.toLong() * AhConstants.GCM_TAG_BYTES, "payloadSize"),
                "payloadSize",
            )
            record
        }
    }

    private fun buildHeader(
        records: List<RecordV2>,
        signerBytes: ByteArray,
        config: ByteArray,
        secrets: BuildSecrets,
        mac: ByteArray,
    ): ByteArray {
        val chunks = records.sumOf(RecordV2::chunkCount)
        val payload = records.last().let { record ->
            checkedAdd(record.payloadOffset, record.compressedLength + record.chunkCount.toLong() * AhConstants.GCM_TAG_BYTES, "payloadSize")
        }
        val header = HeaderV2(
            records.size,
            signerBytes.size,
            checkedIntProduct(records.size, AhConstants.RECORD_BYTES, "recordTableSize"),
            chunks,
            checkedIntProduct(chunks, AhConstants.CHUNK_BYTES, "chunkTableSize"),
            payload,
            secrets.buildId,
            secrets.keySlotId,
            ContainerCrypto.sha256(config),
            mac,
        )
        return AhdcV2Codec.header(header, zeroMac = mac.all { it == 0.toByte() })
    }

    private fun manifestMac(
        key: ByteArray,
        headerWithoutMac: ByteArray,
        signerBytes: ByteArray,
        recordBytes: List<ByteArray>,
        records: List<RecordV2>,
    ): ByteArray = ContainerCrypto.newHmacSha256(key).run {
        update(headerWithoutMac)
        update(signerBytes)
        recordBytes.forEach(::update)
        records.forEach { record ->
            repeat(record.chunkCount) { chunkOrdinal -> update(AhdcV2Codec.chunk(expectedChunk(record, chunkOrdinal))) }
        }
        doFinal()
    }

    private fun writePayload(
        output: OutputStream,
        header: ByteArray,
        inspection: ApkInspection,
        signer: SignerPolicyV1,
        dex: List<DexSummary>,
        pass1: List<CompressionObservation>,
        records: List<RecordV2>,
        recordBytes: List<ByteArray>,
        secrets: BuildSecrets,
    ) {
        ZipFile(inputApk.toFile()).use { zip ->
            records.forEachIndexed { index, record ->
                val entry = zip.getEntry(dex[index].entryName) ?: inputChanged("dexMissingPass2")
                val key = ContainerCrypto.recordKey(secrets.cek, secrets.buildId, record.ordinal)
                val encrypted = ChunkEncryptingOutputStream(
                    output,
                    header,
                    inspection.packageNameSha256,
                    signer.currentCertificateSha256,
                    record,
                    recordBytes[index],
                    key,
                    observer,
                )
                try {
                    val observation = zip.getInputStream(entry).use { input ->
                        compressInto(
                            input,
                            encrypted,
                            observer,
                            pass1[index].originalLength,
                            pass1[index].compressedLength,
                        )
                    }
                    if (observation.originalLength != pass1[index].originalLength ||
                        observation.compressedLength != pass1[index].compressedLength ||
                        !observation.originalSha256.constantTimeEquals(pass1[index].originalSha256) ||
                        encrypted.chunkCount != record.chunkCount
                    ) inputChanged("dexPass2")
                    wipe("pass2.digest.$index", observation.originalSha256, observer)
                } finally {
                    encrypted.clear()
                    wipe("record.key.$index", key, observer)
                }
            }
        }
    }

    private fun expectedFileSize(header: ByteArray, signerSize: Int, records: List<RecordV2>): Long {
        val metadata = header.size.toLong() + signerSize + records.size.toLong() * AhConstants.RECORD_BYTES +
            records.sumOf { it.chunkCount }.toLong() * AhConstants.CHUNK_BYTES
        val payload = records.sumOf { it.compressedLength + it.chunkCount.toLong() * AhConstants.GCM_TAG_BYTES }
        val total = checkedAdd(metadata, payload, "containerSize")
        if (total > InspectionLimits.MAX_APK_BYTES) limit("containerSize")
        return total
    }

    private fun descriptor(
        inspection: ApkInspection,
        signer: SignerPolicyV1,
        records: List<RecordV2>,
        hash: ByteArray,
    ): DexContainerDescriptor = DexContainerDescriptor(
        AhConstants.MAJOR,
        AhConstants.MINOR,
        inspection.packageName,
        signer.currentCertificateSha256,
        signer.lineageCertificateSha256,
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

    private fun targetAbis(inspection: ApkInspection): Set<RuntimeAbi> {
        val present = inspection.nativeAbis.abis.toSet()
        val supportedNames = RuntimeAbi.entries.mapTo(HashSet(), RuntimeAbi::directoryName)
        if (present.any { it !in supportedNames }) format("targetAbis")
        val supported = RuntimeAbi.entries.filterTo(LinkedHashSet()) { abi -> abi.directoryName in present }
        return if (present.isEmpty()) RuntimeAbi.entries.toCollection(LinkedHashSet()) else supported.also {
            if (it.isEmpty()) format("targetAbis")
        }
    }

    private fun hashInput(): ByteArray = try {
        ContainerCrypto.sha256(inputApk)
    } catch (exception: IOException) {
        throw ContainerException(ContainerErrorCode.CONTAINER_INPUT_CHANGED, "inputIo", exception)
    }

    private fun moveAtomically(source: Path, destination: Path) {
        atomicMove?.invoke(source, destination)
            ?: Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE)
    }
}

private class ChunkEncryptingOutputStream(
    private val output: OutputStream,
    header: ByteArray,
    private val packageDigest: ByteArray,
    private val signerDigest: ByteArray,
    private val record: RecordV2,
    private val recordBytes: ByteArray,
    private val key: ByteArray,
    private val observer: ContainerObserver,
) : OutputStream() {
    private val headerVersion = header.headerVersionBytes()
    private val buildId = slice(header, 40, 16)
    private val keySlotId = slice(header, 56, 16)
    private val buffer = ByteArray(AhConstants.CHUNK_PLAINTEXT_MAX)
    private var used = 0
    var chunkCount: Int = 0
        private set

    init {
        observer.allocated("chunk.plaintext", buffer.size)
    }

    override fun write(value: Int) {
        buffer[used++] = value.toByte()
        if (used == buffer.size) emitChunk()
    }

    override fun write(bytes: ByteArray, offset: Int, length: Int) {
        if (offset < 0 || length < 0 || offset > bytes.size - length) format("chunkWrite")
        var source = offset
        var remaining = length
        while (remaining > 0) {
            val count = minOf(remaining, buffer.size - used)
            bytes.copyInto(buffer, used, source, source + count)
            used += count
            source += count
            remaining -= count
            if (used == buffer.size) emitChunk()
        }
    }

    override fun close() {
        if (used > 0) emitChunk()
        if (chunkCount != record.chunkCount) inputChanged("chunkCount")
    }

    fun clear() {
        wipe("chunk.plaintext", buffer, observer)
        headerVersion.fill(0)
        buildId.fill(0)
        keySlotId.fill(0)
    }

    private fun emitChunk() {
        checkCancellation()
        if (chunkCount >= record.chunkCount || used <= 0) inputChanged("chunkTopology")
        val chunkBytes = AhdcV2Codec.chunk(expectedChunk(record, chunkCount))
        if (u4Int(chunkBytes, 24, "chunkLength") != used) inputChanged("chunkLength")
        val nonce = ContainerCrypto.chunkNonce(record.noncePrefix, chunkCount)
        val aad = ContainerCrypto.chunkAad(
            headerVersion,
            buildId,
            keySlotId,
            signerDigest,
            packageDigest,
            recordBytes,
            chunkBytes,
        )
        observer.allocated("chunk.aad", aad.size)
        val plaintext = buffer.copyOf(used)
        observer.allocated("chunk.exact", plaintext.size)
        var ciphertext: ByteArray? = null
        try {
            ciphertext = ContainerCrypto.aesGcmEncrypt(key, nonce, aad, plaintext)
            observer.allocated("chunk.ciphertext", ciphertext.size)
            output.write(ciphertext)
        } finally {
            wipe("chunk.exact", plaintext, observer)
            ciphertext?.let { wipe("chunk.ciphertext", it, observer) }
            nonce.fill(0)
            wipe("chunk.aad", aad, observer)
            chunkBytes.fill(0)
            buffer.fill(0, 0, used)
        }
        used = 0
        chunkCount++
    }
}

private class BuildSecrets(
    val cek: ByteArray,
    val root: ByteArray,
    val rJava: ByteArray,
    val wrapNonce: ByteArray,
    val buildId: ByteArray,
    val keySlotId: ByteArray,
    val noncePrefixes: List<ByteArray>,
) {
    fun clear(observer: ContainerObserver) {
        wipe("cek", cek, observer)
        wipe("root", root, observer)
        wipe("rJava", rJava, observer)
        wipe("wrapNonce", wrapNonce, observer)
        wipe("buildId", buildId, observer)
        wipe("keySlotId", keySlotId, observer)
        noncePrefixes.forEachIndexed { index, bytes -> wipe("noncePrefix.$index", bytes, observer) }
    }

    companion object {
        fun create(random: ContainerRandom, dexCount: Int, observer: ContainerObserver): BuildSecrets {
            val allocated = ArrayList<Pair<String, ByteArray>>()
            fun take(label: String, size: Int): ByteArray {
                val bytes = try {
                    random.bytes(label, size)
                } catch (exception: ContainerException) {
                    throw exception
                } catch (failure: RuntimeException) {
                    throw ContainerException(ContainerErrorCode.CONTAINER_RANDOM_FAILED, label, failure)
                }
                if (bytes.size != size || bytes.all { it == 0.toByte() }) {
                    bytes.fill(0)
                    throw ContainerException(ContainerErrorCode.CONTAINER_RANDOM_FAILED, label)
                }
                allocated += label to bytes
                return bytes
            }
            try {
                val cek = take("cek", 32)
                val root = take("root", 32)
                val rJava = take("rJava", 32)
                val wrapNonce = take("wrapNonce", 12)
                val buildId = take("buildId", 16)
                val keySlotId = take("keySlotId", 16)
                val noncePrefixes = (0 until dexCount).map { index -> take("noncePrefix.$index", 8) }
                if (root.contentEquals(rJava) || buildId.contentEquals(keySlotId) ||
                    noncePrefixes.map(ByteArray::toHex).distinct().size != noncePrefixes.size
                ) throw ContainerException(ContainerErrorCode.CONTAINER_RANDOM_FAILED, "randomCollision")
                return BuildSecrets(cek, root, rJava, wrapNonce, buildId, keySlotId, noncePrefixes)
            } catch (failure: Throwable) {
                allocated.forEach { (label, bytes) -> wipe("random.$label", bytes, observer) }
                throw failure
            }
        }
    }
}

private fun inputChanged(field: String): Nothing =
    throw ContainerException(ContainerErrorCode.CONTAINER_INPUT_CHANGED, field)
