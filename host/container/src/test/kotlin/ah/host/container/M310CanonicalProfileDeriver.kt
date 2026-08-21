package ah.host.container

import ah.host.inspector.ApkInspector
import ah.host.inspector.ApkInspection
import ah.host.inspector.DexSummary
import ah.host.inspector.SignerPolicyV1
import ah.host.inspector.SignerPolicyVerifier
import ah.host.inspector.VerifiedScheme
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
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
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Builds unsigned, independently signed-ready profile APKs from the exact M3-11 originals. */
object M310CanonicalProfileDeriver {
    private const val BASELINE_SHA256 = "4607d3289e1fc3bd95282ab47791ec810a5d2d3ac0a69fc0f91388901e412dcf"
    private const val PROTECTED_SHA256 = "1eb159d7f0149a943fb2e1c4d8467f283d1cfbbfad670628402cfb0cd23390d9"
    private const val BASELINE_SIZE = 29_962L
    private const val PROTECTED_SIZE = 1_287_876L
    private const val CONFIG_ENTRY = "assets/ah/runtime/config.bin"
    private const val CONTAINER_ENTRY = "assets/ah/runtime/payload.ahdc"
    private const val DEX_ENTRY = "classes.dex"
    private const val SLOT_BYTES = 104

    @JvmStatic
    fun main(arguments: Array<String>) {
        require(arguments.size == 6) {
            "usage: baseline.apk protected.apk observer.dex secret-seed.bin signer-sha256 output-directory"
        }
        val baseline = regular(arguments[0], "canonical baseline")
        val protected = regular(arguments[1], "canonical protected")
        val observer = regular(arguments[2], "observer DEX")
        val seedFile = regular(arguments[3], "secret seed")
        val signerDigest = hex(arguments[4])
        require(signerDigest.size == 32) { "profile signer digest must contain 32 bytes" }
        val output = Path.of(arguments[5]).toAbsolutePath().normalize()
        require(!Files.exists(output)) { "profile output already exists" }

        val seed = Files.readAllBytes(seedFile)
        require(seed.size == 32 && seed.any { it != 0.toByte() }) { "secret seed must contain 32 non-zero bytes" }
        val scratchParent = output.parent ?: error("profile output parent is absent")
        Files.createDirectories(scratchParent)
        val scratch = Files.createTempDirectory(scratchParent, ".m310-derive-")
        var completed = false
        try {
            derive(baseline, protected, observer, seed, signerDigest, output, scratch)
            completed = true
        } finally {
            seed.fill(0)
            signerDigest.fill(0)
            deleteTree(scratch)
            if (!completed) deleteTree(output)
        }
    }

    private fun derive(
        baseline: Path,
        protected: Path,
        observer: Path,
        seed: ByteArray,
        profileSigner: ByteArray,
        output: Path,
        scratch: Path,
    ) {
        requireExactOriginal(baseline, BASELINE_SIZE, BASELINE_SHA256, "baseline")
        requireExactOriginal(protected, PROTECTED_SIZE, PROTECTED_SHA256, "protected")
        val inspector = ApkInspector()
        val baselineInspection = inspector.inspect(baseline)
        val baselineContainerInspection = toContainerInspection(baselineInspection)
        val protectedInspection = signerOnlyInspection(protected, baselineInspection)
        val signerVerifier = SignerPolicyVerifier()
        val baselineSigner = signerVerifier.verify(baseline, baselineInspection)
        val protectedSigner = signerVerifier.verify(protected, protectedInspection)
        require(baselineSigner.currentCertificateSha256.contentEquals(protectedSigner.currentCertificateSha256) &&
            VerifiedScheme.V3 in baselineSigner.verifiedSchemes && VerifiedScheme.V3 in protectedSigner.verifiedSchemes
        ) { "canonical signer/v3 semantics differ" }

        val baselineEntries = readEntries(baseline)
        val protectedEntries = readEntries(protected)
        val originalPayloadDex = baselineEntries.getValue(DEX_ENTRY).bytes
        val originalConfig = protectedEntries.getValue(CONFIG_ENTRY).bytes
        val originalContainer = protectedEntries.getValue(CONTAINER_ENTRY).bytes
        val originalSlots = readAllRuntimeSlots(protectedEntries, originalConfig)

        val containerFile = scratch.resolve("canonical.ahdc")
        Files.write(containerFile, originalContainer)
        ExpectedBinding.from(
            baselineContainerInspection,
            baselineSigner,
            originalConfig,
            originalSlots.rNative,
            NO_CONTAINER_OBSERVER,
        ).use { binding -> DexContainerVerifier().verify(containerFile, binding) }
        val decrypted = decryptPayload(
            containerFile,
            originalConfig,
            originalSlots.rNative,
            baselineContainerInspection.packageNameSha256,
            baselineSigner.currentCertificateSha256,
        )
        require(decrypted.size == 1 && decrypted.single().contentEquals(originalPayloadDex)) {
            "authenticated canonical payload is not the baseline DEX"
        }

        val originalPayloadFile = scratch.resolve("payload-original.dex")
        val baselineProfileDex = scratch.resolve("payload-baseline-profile.dex")
        val protectedProfileDex = scratch.resolve("payload-protected-profile.dex")
        val shellOriginalDex = scratch.resolve("shell-original.dex")
        val shellProfileDex = scratch.resolve("shell-profile.dex")
        Files.write(originalPayloadFile, decrypted.single())
        Files.write(shellOriginalDex, protectedEntries.getValue(DEX_ENTRY).bytes)
        M310DexProfileTool.derive("payload-baseline", originalPayloadFile, observer, baselineProfileDex)
        M310DexProfileTool.derive("payload-protected", originalPayloadFile, observer, protectedProfileDex)
        M310DexProfileTool.derive("shell", shellOriginalDex, observer, shellProfileDex)

        val containerInput = scratch.resolve("container-input.apk")
        writeApk(baselineEntries, mapOf(DEX_ENTRY to Files.readAllBytes(protectedProfileDex)), containerInput)
        val containerInspection = toContainerInspection(inspector.inspect(containerInput))
        val profilePolicy = SignerPolicyV1(profileSigner, listOf(profileSigner), setOf(VerifiedScheme.V3))
        val newContainer = scratch.resolve("profile.ahdc")
        val deterministicRandom = SeededContainerRandom(seed)
        val build = try {
            DexContainerBuilder(
                containerInput,
                deterministicRandom,
                NO_CONTAINER_OBSERVER,
                null,
            ).build(containerInspection, profilePolicy, newContainer)
        } finally {
            deterministicRandom.close()
        }
        var newConfig: ByteArray? = null
        var newShare: ByteArray? = null
        var newBuildId: ByteArray? = null
        var newKeySlotId: ByteArray? = null
        try {
            build.keyPackagingPlan.consume { material ->
                newConfig = material.configV2().copyBytes()
                newShare = material.rNative().copyBytes()
                newBuildId = material.buildId().copyBytes()
                newKeySlotId = material.keySlotId().copyBytes()
            }
            val config = requireNotNull(newConfig)
            val share = requireNotNull(newShare)
            val buildId = requireNotNull(newBuildId)
            val keySlotId = requireNotNull(newKeySlotId)
            val protectedReplacements = linkedMapOf(
                DEX_ENTRY to Files.readAllBytes(shellProfileDex),
                CONFIG_ENTRY to config,
                CONTAINER_ENTRY to Files.readAllBytes(newContainer),
            )
            for (abi in RuntimeAbi.entries) {
                val name = "lib/${abi.directoryName}/libah_runtime.so"
                protectedReplacements[name] = patchRuntimeSlot(
                    protectedEntries.getValue(name).bytes,
                    abi,
                    keySlotId,
                    buildId,
                    share,
                )
            }
            Files.createDirectory(output)
            val baselineOutput = output.resolve("profile-baseline-unsigned.apk")
            val protectedOutput = output.resolve("profile-protected-unsigned.apk")
            writeApk(baselineEntries, mapOf(DEX_ENTRY to Files.readAllBytes(baselineProfileDex)), baselineOutput)
            writeApk(protectedEntries, protectedReplacements, protectedOutput)
            val manifest = """
                {
                  "schemaVersion": 1,
                  "canonicalBaselineSha256": "$BASELINE_SHA256",
                  "canonicalProtectedSha256": "$PROTECTED_SHA256",
                  "profileSignerSha256Prefix": "${profilePolicy.currentCertificateSha256Hex.take(12)}",
                  "unsignedBaselineSha256": "${sha256(baselineOutput)}",
                  "unsignedProtectedSha256": "${sha256(protectedOutput)}",
                  "payloadOriginalSha256": "${sha256(originalPayloadDex)}",
                  "profileBaselineDexSha256": "${sha256(baselineProfileDex)}",
                  "profileProtectedPayloadDexSha256": "${sha256(protectedProfileDex)}",
                  "profileProtectedShellDexSha256": "${sha256(shellProfileDex)}",
                  "changedBaselineEntries": ["classes.dex", "APK signing block", "META-INF v1 signature files"],
                  "changedProtectedEntries": ["classes.dex", "assets/ah/runtime/config.bin", "assets/ah/runtime/payload.ahdc", "four libah_runtime.so share slots", "APK signing block", "META-INF v1 signature files"]
                }
            """.trimIndent() + "\n"
            Files.writeString(output.resolve("derivation-manifest.json"), manifest)
        } finally {
            newConfig?.fill(0)
            newShare?.fill(0)
            newBuildId?.fill(0)
            newKeySlotId?.fill(0)
            originalSlots.rNative.fill(0)
            decrypted.forEach { it.fill(0) }
        }
        println("M3-10 canonical derivation PASS signer=${profilePolicy.currentCertificateSha256Hex.take(12)}")
    }

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

    private fun patchRuntimeSlot(
        source: ByteArray,
        abi: RuntimeAbi,
        keySlotId: ByteArray,
        buildId: ByteArray,
        rNative: ByteArray,
    ): ByteArray {
        val offset = locateSlot(source, abi)
        val output = source.copyOf()
        output.fill(0, offset, offset + SLOT_BYTES)
        "AHS1".toByteArray(Charsets.US_ASCII).copyInto(output, offset)
        putU2(output, offset + 4, 1)
        putU2(output, offset + 6, abi.abiId)
        keySlotId.copyInto(output, offset + 8)
        buildId.copyInto(output, offset + 24)
        rNative.copyInto(output, offset + 40)
        val digest = MessageDigest.getInstance("SHA-256").apply { update(output, offset, 72) }.digest()
        try {
            digest.copyInto(output, offset + 72)
        } finally {
            digest.fill(0)
        }
        require(source.indices.all { it in offset until offset + SLOT_BYTES || source[it] == output[it] }) {
            "runtime patch escaped the share slot"
        }
        return output
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

    private class SeededContainerRandom(seed: ByteArray) : ContainerRandom, AutoCloseable {
        private val key = seed.copyOf()
        private var counter = 0L
        private var closed = false

        override fun fill(label: String, destination: ByteArray) {
            require(!closed && label.isNotEmpty() && destination.isNotEmpty()) {
                "deterministic random request differs"
            }
            var offset = 0
            while (offset < destination.size) {
                val mac = Mac.getInstance("HmacSHA256")
                mac.init(SecretKeySpec(key, "HmacSHA256"))
                mac.update("M3-10-CONTAINER-V1\u0000".toByteArray(Charsets.UTF_8))
                mac.update(label.toByteArray(Charsets.UTF_8))
                mac.update(ByteBuffer.allocate(8).putLong(counter++).array())
                val block = mac.doFinal()
                val count = minOf(block.size, destination.size - offset)
                block.copyInto(destination, offset, 0, count)
                block.fill(0)
                offset += count
            }
        }

        override fun close() {
            if (!closed) {
                key.fill(0)
                closed = true
            }
        }
    }

    private fun java.nio.ByteBuffer.copyBytes(): ByteArray = asReadOnlyBuffer().let { buffer ->
        ByteArray(buffer.remaining()).also(buffer::get)
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

    private fun hex(value: String): ByteArray {
        require(value.matches(Regex("[0-9a-f]{64}"))) { "digest must be lowercase SHA-256" }
        return ByteArray(32) { index -> value.substring(index * 2, index * 2 + 2).toInt(16).toByte() }
    }

    private fun regular(value: String, label: String): Path = Path.of(value).toAbsolutePath().normalize().also {
        require(Files.isRegularFile(it)) { "$label is missing" }
    }

    internal fun deleteTree(root: Path) {
        if (!Files.exists(root)) return
        Files.walk(root).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }
}
