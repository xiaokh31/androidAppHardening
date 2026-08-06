package ah.host.repacker

import ah.host.container.KeyPackagingMaterialV2
import ah.host.container.ContainerException
import ah.host.container.DexContainerDescriptor
import ah.host.container.DexContainerVerifier
import ah.host.container.RuntimeAbi
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest

class ApkRepacker internal constructor(
    private val faults: PackageFaults,
    private val atomicMove: (Path, Path) -> Unit,
) {
    constructor() : this(NO_PACKAGE_FAULTS, ::defaultAtomicMove)

    fun repack(request: RepackRequest): OutputVerification {
        var ready: ReadyCandidate? = null
        var published = false
        var completed = false
        try {
            val candidate = request.keyPackagingPlan.consume { material ->
                val input = request.input.toAbsolutePath().normalize()
                val output = request.output.toAbsolutePath().normalize()
                val paths = validatePaths(input, output)
                val containerIdentity = FileIdentity.capture(
                    request.container.toAbsolutePath().normalize(),
                    PackageErrorCode.PACKAGE_ABI_MISMATCH,
                    "containerIdentity",
                )
                validateBindings(request)
                verifyContainer(request, material)
                val archive = openInputArchive(input)
                var returned = false
                try {
                    val initialInputHash = archive.fileSha256()
                    if (!MessageDigest.isEqual(initialInputHash, request.inspection.inputSha256)) {
                        packageFailure(PackageErrorCode.OUTPUT_INPUT_CHANGED, "inputSha256")
                    }
                    validateInspection(archive, request)
                    val effectiveAbis = effectiveAbis(archive, request)
                    if (material.targetAbis != effectiveAbis) {
                        packageFailure(PackageErrorCode.PACKAGE_ABI_MISMATCH, "targetAbis")
                    }
                    prepare(request, archive, material, effectiveAbis, initialInputHash).use { prepared ->
                        val parent = output.parent ?: packageFailure(PackageErrorCode.PACKAGE_WRITE_FAILED, "outputParent")
                        val candidate = try {
                            Files.createTempFile(parent, ".ah-repack-", ".part")
                        } catch (_: IOException) {
                            packageFailure(PackageErrorCode.PACKAGE_WRITE_FAILED, "tempCreate")
                        }
                        try {
                            AlignedZipWriter(candidate, faults).use { writer ->
                                prepared.entries.forEach(writer::writeEntry)
                                writer.finish()
                            }
                            prepared.clearPayloads()
                            faults.afterCandidateClosed(candidate)
                            val verification = OutputVerifier.verify(candidate, prepared.expected, faults)
                            paths.verify(outputMustBeAbsent = true)
                            if (!containerIdentity.matches(request.container.toAbsolutePath().normalize(), requireSamePath = true)) {
                                packageFailure(PackageErrorCode.PACKAGE_ABI_MISMATCH, "containerIdentity")
                            }
                            if (!MessageDigest.isEqual(archive.fileSha256(), initialInputHash)) {
                                packageFailure(PackageErrorCode.OUTPUT_INPUT_CHANGED, "inputBeforeCleanup")
                            }
                            val value = ReadyCandidate(candidate, archive, paths, initialInputHash, verification)
                            returned = true
                            value
                        } catch (failure: PackageException) {
                            deleteQuietly(candidate)
                            throw failure
                        } catch (_: IOException) {
                            deleteQuietly(candidate)
                            packageFailure(PackageErrorCode.PACKAGE_WRITE_FAILED, "io")
                        } catch (_: SecurityException) {
                            deleteQuietly(candidate)
                            packageFailure(PackageErrorCode.PACKAGE_WRITE_FAILED, "permission")
                        } catch (failure: Throwable) {
                            deleteQuietly(candidate)
                            throw failure
                        }
                    }
                } finally {
                    if (!returned) archive.close()
                }
            }
            ready = candidate
            faults.beforeAtomicMove(candidate.path, candidate.paths.output)
            candidate.paths.verify(outputMustBeAbsent = true)
            if (!MessageDigest.isEqual(candidate.archive.fileSha256(), candidate.inputSha256)) {
                packageFailure(PackageErrorCode.OUTPUT_INPUT_CHANGED, "inputBeforeMove")
            }
            val candidateIdentity = FileIdentity.capture(candidate.path, PackageErrorCode.PACKAGE_WRITE_FAILED, "candidateIdentity")
            try {
                atomicMove(candidate.path, candidate.paths.output)
            } catch (_: AtomicMoveNotSupportedException) {
                packageFailure(PackageErrorCode.OUTPUT_ATOMIC_MOVE_UNSUPPORTED, "atomicMove")
            } catch (_: IOException) {
                packageFailure(PackageErrorCode.PACKAGE_WRITE_FAILED, "atomicMove")
            }
            published = true
            faults.afterPublished(candidate.paths.output)
            candidate.paths.verify(outputMustBeAbsent = false)
            if (!candidateIdentity.matches(candidate.paths.output)) {
                packageFailure(PackageErrorCode.OUTPUT_VERIFICATION_FAILED, "publishedIdentity")
            }
            if (!MessageDigest.isEqual(candidate.archive.fileSha256(), candidate.inputSha256)) {
                packageFailure(PackageErrorCode.OUTPUT_INPUT_CHANGED, "inputAfterMove")
            }
            if (!MessageDigest.isEqual(candidate.verification.outputSha256, sha256(candidate.paths.output))) {
                packageFailure(PackageErrorCode.OUTPUT_VERIFICATION_FAILED, "publishedDigest")
            }
            candidate.closeArchive()
            completed = true
            return candidate.verification
        } finally {
            ready?.let { candidate ->
                candidate.closeArchiveQuietly()
                if (!published) deleteQuietly(candidate.path)
                if (published && !completed && Files.exists(candidate.paths.output, LinkOption.NOFOLLOW_LINKS)) {
                    deleteQuietly(candidate.paths.output)
                }
            }
        }
    }

    private fun prepare(
        request: RepackRequest,
        archive: RawZipArchive,
        material: KeyPackagingMaterialV2,
        effectiveAbis: Set<RuntimeAbi>,
        inputHash: ByteArray,
    ): PreparedOutput {
        var config: ByteArray? = null
        var rNative: ByteArray? = null
        var buildId: ByteArray? = null
        var keySlotId: ByteArray? = null
        var runtimes: List<MaterializedRuntime> = emptyList()
        val entries = ArrayList<PlannedZipEntry>()
        val expected = ArrayList<ExpectedEntry>()
        val deleted = LinkedHashSet<String>()
        var expectedBuild: ByteArray? = null
        var expectedSlot: ByteArray? = null
        var expectedNative: ByteArray? = null
        var outputContract: ExpectedOutput? = null
        var completed = false
        try {
            val configValue = material.configV2().copyBytes()
            config = configValue
            faults.afterSensitiveCopy("prepare.config")
            val nativeValue = material.rNative().copyBytes()
            rNative = nativeValue
            faults.afterSensitiveCopy("prepare.rNative")
            val buildValue = material.buildId().copyBytes()
            buildId = buildValue
            faults.afterSensitiveCopy("prepare.buildId")
            val slotValue = material.keySlotId().copyBytes()
            keySlotId = slotValue
            faults.afterSensitiveCopy("prepare.keySlotId")
            runtimes = RuntimeMaterializer.materialize(request.runtimeBundle, material, faults)
            archive.entries.forEach { source ->
                when {
                    source.name == MANIFEST_PATH -> {
                        val plan = bytesPlan(
                            source.name,
                            request.transformedManifest.bytes,
                            source.method,
                            alignment(source.name, source.method),
                            ExpectedContentKind.MANIFEST,
                            original = source,
                        )
                        entries += plan.first
                        expected += plan.second
                    }
                    isBusinessDex(source.name) || isJarSignatureEntry(source.name) -> deleted += source.name
                    isReservedEntry(source.name) -> packageFailure(PackageErrorCode.PACKAGE_ENTRY_CONFLICT, "reservedEntry")
                    else -> {
                        val contract = ExpectedEntry(
                            source.name,
                            source.method,
                            source.crc32,
                            source.compressedSize,
                            source.uncompressedSize,
                            alignment(source.name, source.method),
                            ExpectedContentKind.PRESERVED,
                            archive.uncompressedSha256(source),
                            archive.compressedSha256(source),
                        )
                        entries += PlannedZipEntry(
                            contract,
                            source.flags or UTF8_FLAG,
                            source.modTime,
                            source.modDate,
                            source.versionMadeBy,
                            source.versionNeeded,
                            source.internalAttributes,
                            source.externalAttributes,
                            RawEntryPayload(archive, source),
                        )
                        expected += contract
                    }
                }
            }
            addBytes(entries, expected, BOOTSTRAP_PATH, request.runtimeBundle.bootstrapDex, METHOD_DEFLATED, 1, ExpectedContentKind.BOOTSTRAP)
            addFile(entries, expected, PAYLOAD_PATH, request.container, request.containerDescriptor.containerSha256, 4096, ExpectedContentKind.CONTAINER)
            addBytes(entries, expected, CONFIG_PATH, configValue, METHOD_STORED, 4096, ExpectedContentKind.CONFIG)
            runtimes.sortedBy { it.abi.directoryName }.forEach { runtime ->
                val path = runtimePath(runtime.abi)
                val pair = bytesPlan(path, runtime.bytes, METHOD_STORED, 16_384, ExpectedContentKind.RUNTIME)
                val runtimeContract = ExpectedEntry(
                    pair.second.name,
                    pair.second.method,
                    pair.second.crc32,
                    pair.second.compressedSize,
                    pair.second.uncompressedSize,
                    pair.second.alignment,
                    pair.second.kind,
                    pair.second.uncompressedSha256,
                    pair.second.compressedSha256,
                    runtime.abi,
                    runtime.template,
                    runtime.slotOffset,
                )
                entries += pair.first.copyWithExpected(runtimeContract)
                expected += runtimeContract
            }
            expectedBuild = buildValue.copyOf()
            expectedSlot = slotValue.copyOf()
            expectedNative = nativeValue.copyOf()
            val contract = ExpectedOutput(
                expected,
                deleted,
                inputHash.copyOf(),
                request.transformedManifest.afterSha256,
                request.containerDescriptor.containerSha256,
                sha256(configValue),
                requireNotNull(expectedBuild),
                requireNotNull(expectedSlot),
                requireNotNull(expectedNative),
                request.inspection.nativeAbis.abis.toSet(),
                effectiveAbis,
            )
            outputContract = contract
            expectedBuild = null
            expectedSlot = null
            expectedNative = null
            completed = true
            return PreparedOutput(entries, contract, faults)
        } finally {
            config?.let { clearSensitive("prepare.config", it, faults) }
            rNative?.let { clearSensitive("prepare.rNative", it, faults) }
            buildId?.let { clearSensitive("prepare.buildId", it, faults) }
            keySlotId?.let { clearSensitive("prepare.keySlotId", it, faults) }
            runtimes.forEach { clearSensitive("prepare.runtime.${it.abi.directoryName}", it.bytes, faults) }
            expectedBuild?.let { clearSensitive("prepare.expectedBuild", it, faults) }
            expectedSlot?.let { clearSensitive("prepare.expectedSlot", it, faults) }
            expectedNative?.let { clearSensitive("prepare.expectedNative", it, faults) }
            if (!completed) {
                entries.clearBytePayloads()
                outputContract?.close()
            }
        }
    }

    private fun addBytes(
        entries: MutableList<PlannedZipEntry>,
        expected: MutableList<ExpectedEntry>,
        name: String,
        bytes: ByteArray,
        method: Int,
        alignment: Int,
        kind: ExpectedContentKind,
    ) {
        val pair = bytesPlan(name, bytes, method, alignment, kind)
        entries += pair.first
        expected += pair.second
    }

    private fun addFile(
        entries: MutableList<PlannedZipEntry>,
        expected: MutableList<ExpectedEntry>,
        name: String,
        path: Path,
        expectedSha256: ByteArray,
        alignment: Int,
        kind: ExpectedContentKind,
    ) {
        val size = try { Files.size(path) } catch (_: IOException) {
            packageFailure(PackageErrorCode.PACKAGE_WRITE_FAILED, "containerRead")
        }
        val digest = sha256(path)
        if (!MessageDigest.isEqual(digest, expectedSha256)) {
            packageFailure(PackageErrorCode.PACKAGE_ABI_MISMATCH, "containerDigest")
        }
        val contract = ExpectedEntry(name, METHOD_STORED, crc32(path), size, size, alignment, kind, digest, digest)
        entries += canonicalPlan(contract, FileEntryPayload(path, size))
        expected += contract
    }

    private fun bytesPlan(
        name: String,
        bytes: ByteArray,
        method: Int,
        alignment: Int,
        kind: ExpectedContentKind,
        original: RawZipEntry? = null,
    ): Pair<PlannedZipEntry, ExpectedEntry> {
        val payload = if (method == METHOD_DEFLATED) deflateRaw(bytes) else bytes.copyOf()
        val digest = sha256(bytes)
        val contract = ExpectedEntry(
            name,
            method,
            crc32(bytes),
            payload.size.toLong(),
            bytes.size.toLong(),
            alignment,
            kind,
            digest,
            sha256(payload),
        )
        val plan = if (original == null) canonicalPlan(contract, BytesEntryPayload(payload)) else PlannedZipEntry(
            contract,
            original.flags or UTF8_FLAG,
            original.modTime,
            original.modDate,
            original.versionMadeBy,
            original.versionNeeded,
            original.internalAttributes,
            original.externalAttributes,
            BytesEntryPayload(payload),
        )
        return plan to contract
    }

    private fun canonicalPlan(expected: ExpectedEntry, payload: EntryPayload): PlannedZipEntry = PlannedZipEntry(
        expected,
        UTF8_FLAG,
        FIXED_DOS_TIME,
        FIXED_DOS_DATE,
        0x031e,
        20,
        0,
        0,
        payload,
    )

    private fun validateBindings(request: RepackRequest) {
        if (!request.containerDescriptor.containerSha256.contentEquals(sha256(request.container)) ||
            request.containerDescriptor.packageName != request.inspection.packageName ||
            !request.containerDescriptor.currentSignerSha256.contentEquals(request.signerPolicy.currentCertificateSha256) ||
            request.containerDescriptor.signerLineageSha256.size != request.signerPolicy.lineageCertificateSha256.size ||
            request.containerDescriptor.signerLineageSha256.zip(request.signerPolicy.lineageCertificateSha256)
                .any { (left, right) -> !MessageDigest.isEqual(left, right) }
        ) packageFailure(PackageErrorCode.PACKAGE_ABI_MISMATCH, "binding")
        if (!MessageDigest.isEqual(request.transformedManifest.afterSha256, sha256(request.transformedManifest.bytes))) {
            packageFailure(PackageErrorCode.PACKAGE_ENTRY_CONFLICT, "manifestDigest")
        }
    }

    private fun validateInspection(archive: RawZipArchive, request: RepackRequest) {
        if (archive.entries.size != request.inspection.zipEntries.size) {
            packageFailure(PackageErrorCode.OUTPUT_INPUT_CHANGED, "entryCount")
        }
        archive.entries.zip(request.inspection.zipEntries).forEach { (actual, recorded) ->
            if (actual.index != recorded.index || actual.name != recorded.name || actual.method != recorded.method ||
                actual.crc32 != recorded.crc32 || actual.compressedSize != recorded.compressedSize ||
                actual.uncompressedSize != recorded.uncompressedSize || actual.localHeaderOffset != recorded.localHeaderOffset ||
                !MessageDigest.isEqual(sha256(actual.nameBytes), recorded.originalNameSha256)
            ) packageFailure(PackageErrorCode.OUTPUT_INPUT_CHANGED, "inspectionEntry")
        }
        if (archive.entries.count { it.name == MANIFEST_PATH } != 1) {
            packageFailure(PackageErrorCode.PACKAGE_ENTRY_CONFLICT, "manifest")
        }
        val manifest = archive.entries.single { it.name == MANIFEST_PATH }
        if (!MessageDigest.isEqual(archive.uncompressedSha256(manifest), request.transformedManifest.beforeSha256)) {
            packageFailure(PackageErrorCode.OUTPUT_INPUT_CHANGED, "manifestBinding")
        }
    }

    private fun verifyContainer(request: RepackRequest, material: KeyPackagingMaterialV2) {
        try {
            material.expectedBinding(request.inspection, request.signerPolicy).use { binding ->
                val verified = DexContainerVerifier().verify(request.container, binding)
                if (!sameDescriptor(verified, request.containerDescriptor)) {
                    packageFailure(PackageErrorCode.PACKAGE_ABI_MISMATCH, "containerDescriptor")
                }
            }
        } catch (failure: PackageException) {
            throw failure
        } catch (_: ContainerException) {
            packageFailure(PackageErrorCode.PACKAGE_ABI_MISMATCH, "containerVerification")
        }
    }

    private fun sameDescriptor(left: DexContainerDescriptor, right: DexContainerDescriptor): Boolean =
        left.major == right.major && left.minor == right.minor && left.packageName == right.packageName &&
            MessageDigest.isEqual(left.currentSignerSha256, right.currentSignerSha256) &&
            left.signerLineageSha256.size == right.signerLineageSha256.size &&
            left.signerLineageSha256.zip(right.signerLineageSha256).all { (a, b) -> MessageDigest.isEqual(a, b) } &&
            left.records.size == right.records.size && left.records.zip(right.records).all { (a, b) ->
                a.ordinal == b.ordinal && a.name == b.name && a.originalLength == b.originalLength &&
                    a.compressedLength == b.compressedLength && a.chunkCount == b.chunkCount &&
                    a.firstChunkIndex == b.firstChunkIndex && a.payloadOffset == b.payloadOffset &&
                    MessageDigest.isEqual(a.originalSha256, b.originalSha256)
            } && MessageDigest.isEqual(left.containerSha256, right.containerSha256)

    private fun effectiveAbis(archive: RawZipArchive, request: RepackRequest): Set<RuntimeAbi> {
        val actualNames = archive.entries.mapNotNullTo(LinkedHashSet()) { entry ->
            NATIVE_LIBRARY.matchEntire(entry.name)?.groupValues?.get(1)
        }
        if (actualNames != request.inspection.nativeAbis.abis.toSet()) {
            packageFailure(PackageErrorCode.OUTPUT_INPUT_CHANGED, "nativeAbis")
        }
        val supported = RuntimeAbi.entries.associateBy(RuntimeAbi::directoryName)
        if (actualNames.any { it !in supported }) packageFailure(PackageErrorCode.COMPAT_ABI_UNSUPPORTED, "nativeAbi")
        return if (actualNames.isEmpty()) RuntimeAbi.entries.toCollection(LinkedHashSet()) else
            actualNames.mapTo(LinkedHashSet()) { supported.getValue(it) }
    }

    private fun validatePaths(input: Path, output: Path): PathContract {
        if (input == output) packageFailure(PackageErrorCode.OUTPUT_PATH_ALIAS, "normalizedPath")
        if (!Files.isRegularFile(input, LinkOption.NOFOLLOW_LINKS)) {
            packageFailure(PackageErrorCode.OUTPUT_INPUT_CHANGED, "inputType")
        }
        if (Files.exists(output, LinkOption.NOFOLLOW_LINKS)) {
            val aliases = try { Files.isSameFile(input, output) } catch (_: IOException) { false }
            packageFailure(if (aliases) PackageErrorCode.OUTPUT_PATH_ALIAS else PackageErrorCode.OUTPUT_ALREADY_EXISTS, "output")
        }
        val parent = output.parent ?: packageFailure(PackageErrorCode.PACKAGE_WRITE_FAILED, "outputParent")
        val inputIdentity = FileIdentity.capture(input, PackageErrorCode.OUTPUT_INPUT_CHANGED, "inputIdentity")
        val parentIdentity = FileIdentity.capture(parent, PackageErrorCode.PACKAGE_WRITE_FAILED, "outputParent")
        if (inputIdentity.realPath == parentIdentity.realPath.resolve(output.fileName).normalize()) {
            packageFailure(PackageErrorCode.OUTPUT_PATH_ALIAS, "resolvedPath")
        }
        return PathContract(input, output, parent, inputIdentity, parentIdentity)
    }

    private fun openInputArchive(input: Path): RawZipArchive = try {
        RawZipArchive.open(input)
    } catch (_: PackageException) {
        packageFailure(PackageErrorCode.OUTPUT_INPUT_CHANGED, "inputZip")
    }

    private fun alignment(name: String, method: Int): Int = when {
        method != METHOD_STORED -> 1
        name.endsWith(".so") -> 16_384
        else -> 4
    }

    private fun deleteQuietly(path: Path) {
        try { Files.deleteIfExists(path) } catch (_: IOException) { /* cleanup is best-effort */ }
    }

    private class PreparedOutput(
        val entries: List<PlannedZipEntry>,
        val expected: ExpectedOutput,
        private val faults: PackageFaults,
    ) : AutoCloseable {
        private var payloadsCleared = false

        fun clearPayloads() {
            if (payloadsCleared) return
            payloadsCleared = true
            entries.clearBytePayloads()
            reportSensitiveCleared("prepared.bytePayloads", entries.bytePayloadsCleared(), faults)
        }

        override fun close() {
            clearPayloads()
            expected.close()
            reportSensitiveCleared("prepared.expected", expected.isCleared(), faults)
        }
    }

    private class ReadyCandidate(
        val path: Path,
        val archive: RawZipArchive,
        val paths: PathContract,
        val inputSha256: ByteArray,
        val verification: OutputVerification,
    ) {
        private var archiveClosed = false

        fun closeArchive() {
            if (archiveClosed) return
            archiveClosed = true
            try {
                archive.close()
            } catch (_: IOException) {
                packageFailure(PackageErrorCode.PACKAGE_WRITE_FAILED, "inputClose")
            }
        }

        fun closeArchiveQuietly() {
            if (archiveClosed) return
            archiveClosed = true
            try { archive.close() } catch (_: IOException) { /* preserve the primary failure */ }
        }
    }

    private class PathContract(
        val input: Path,
        val output: Path,
        private val parent: Path,
        private val inputIdentity: FileIdentity,
        private val parentIdentity: FileIdentity,
    ) {
        fun verify(outputMustBeAbsent: Boolean) {
            if (!inputIdentity.matches(input, requireSamePath = true)) {
                packageFailure(PackageErrorCode.OUTPUT_INPUT_CHANGED, "inputIdentity")
            }
            if (!parentIdentity.matches(parent, requireSamePath = true)) {
                packageFailure(PackageErrorCode.PACKAGE_WRITE_FAILED, "outputParentIdentity")
            }
            val exists = Files.exists(output, LinkOption.NOFOLLOW_LINKS)
            if (outputMustBeAbsent && exists) packageFailure(PackageErrorCode.OUTPUT_ALREADY_EXISTS, "outputRace")
            if (!outputMustBeAbsent && !exists) packageFailure(PackageErrorCode.OUTPUT_VERIFICATION_FAILED, "publishedMissing")
        }
    }

    private data class FileIdentity(
        val realPath: Path,
        val fileKey: String?,
        val size: Long,
        val directory: Boolean,
    ) {
        fun matches(path: Path, requireSamePath: Boolean = false): Boolean = try {
            val actualReal = path.toRealPath()
            val attributes = Files.readAttributes(path, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
            (if (directory) attributes.isDirectory else attributes.isRegularFile) &&
                (!requireSamePath || actualReal == realPath) &&
                if (fileKey != null && attributes.fileKey() != null) fileKey == attributes.fileKey().toString() else size == attributes.size()
        } catch (_: IOException) {
            false
        } catch (_: SecurityException) {
            false
        }

        companion object {
            fun capture(path: Path, code: PackageErrorCode, field: String): FileIdentity = try {
                val real = path.toRealPath()
                val attributes = Files.readAttributes(path, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
                if (!attributes.isRegularFile && !attributes.isDirectory) packageFailure(code, field)
                FileIdentity(real, attributes.fileKey()?.toString(), attributes.size(), attributes.isDirectory)
            } catch (_: IOException) {
                packageFailure(code, field)
            } catch (_: SecurityException) {
                packageFailure(code, field)
            }
        }
    }

    companion object {
        private val NATIVE_LIBRARY = Regex("lib/([^/]+)/[^/]+\\.so")

        private fun defaultAtomicMove(source: Path, destination: Path) {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE)
        }
    }
}

private fun List<PlannedZipEntry>.clearBytePayloads() {
    forEach { entry -> (entry.payload as? BytesEntryPayload)?.clear() }
}

private fun List<PlannedZipEntry>.bytePayloadsCleared(): Boolean =
    all { entry -> (entry.payload as? BytesEntryPayload)?.isCleared() != false }

private fun PlannedZipEntry.copyWithExpected(expected: ExpectedEntry): PlannedZipEntry = PlannedZipEntry(
    expected,
    flags,
    modTime,
    modDate,
    versionMadeBy,
    versionNeeded,
    internalAttributes,
    externalAttributes,
    payload,
)

private fun java.nio.ByteBuffer.copyBytes(): ByteArray {
    val copy = asReadOnlyBuffer()
    val result = ByteArray(copy.remaining())
    copy.get(result)
    return result
}

internal fun isBusinessDex(name: String): Boolean = Regex("classes(?:[0-9]+)?\\.dex").matches(name)

internal fun isJarSignatureEntry(name: String): Boolean {
    val upper = name.uppercase()
    if (!upper.startsWith("META-INF/")) return false
    val leaf = upper.removePrefix("META-INF/")
    if (leaf.contains('/')) return false
    return leaf == "MANIFEST.MF" || leaf.startsWith("SIG-") ||
        leaf.endsWith(".SF") || leaf.endsWith(".RSA") || leaf.endsWith(".DSA") || leaf.endsWith(".EC")
}

private fun isReservedEntry(name: String): Boolean =
    name.startsWith("assets/ah/runtime/") || Regex("lib/[^/]+/libah_runtime\\.so", RegexOption.IGNORE_CASE).matches(name)
