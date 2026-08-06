package ah.host.repacker

import ah.host.container.RuntimeAbi
import java.nio.file.Path
import java.security.MessageDigest

object OutputVerifier {
    fun verify(candidate: Path, expected: ExpectedOutput): OutputVerification = verify(candidate, expected, NO_PACKAGE_FAULTS)

    internal fun verify(candidate: Path, expected: ExpectedOutput, faults: PackageFaults): OutputVerification {
        val verified = ArrayList<VerifiedEntry>(expected.entries.size)
        RawZipArchive.open(candidate, requirePackedLayout = true).use { archive ->
            val actual = archive.entries
            if (actual.size != expected.entries.size) verificationFailure("entryCount")
            if (actual.map(RawZipEntry::name) != expected.entries.map(ExpectedEntry::name)) {
                verificationFailure("entryOrder")
            }
            val names = actual.mapTo(LinkedHashSet(), RawZipEntry::name)
            if (expected.deletedNames.filterNot { it == BOOTSTRAP_PATH }.any(names::contains) ||
                names.count { it == BOOTSTRAP_PATH } != 1
            ) {
                verificationFailure("deletedEntry")
            }
            if (names.any(::isJarSignatureEntry)) verificationFailure("signatureEntry")

            actual.zip(expected.entries).forEach { (entry, contract) ->
                if (entry.method != contract.method || entry.crc32 != contract.crc32 ||
                    entry.compressedSize != contract.compressedSize || entry.uncompressedSize != contract.uncompressedSize
                ) verificationFailure("entryMetadata")
                if (entry.dataOffset % contract.alignment != 0L) {
                    throw PackageException(PackageErrorCode.PACKAGE_ALIGNMENT, safeEntryField(entry.name))
                }
                val compressed = archive.compressedSha256(entry)
                val uncompressed = archive.uncompressedSha256(entry)
                if (!MessageDigest.isEqual(compressed, contract.compressedSha256) ||
                    !MessageDigest.isEqual(uncompressed, contract.uncompressedSha256)
                ) verificationFailure("entryDigest")
                if (entry.name != BOOTSTRAP_PATH && isDexMagic(archive.uncompressedPrefix(entry, DEX_MAGIC_BYTES))) {
                    verificationFailure("plaintextDex")
                }
                if (contract.kind == ExpectedContentKind.RUNTIME) {
                    val runtimeBytes = archive.readUncompressed(
                        entry,
                        MAX_RUNTIME_BYTES,
                        onAllocated = { faults.afterSensitiveCopy("verifier.materialize") },
                        onFailureCleared = { cleared -> reportSensitiveCleared("verifier.materialize", cleared, faults) },
                    )
                    try {
                        faults.afterVerifierRuntimeRead()
                        verifyRuntime(runtimeBytes, contract, expected)
                    } finally {
                        clearSensitive("verifier.runtime", runtimeBytes, faults)
                    }
                }
                verified += VerifiedEntry(
                    entry.name,
                    entry.method,
                    entry.crc32,
                    entry.uncompressedSize,
                    entry.dataOffset,
                    uncompressed,
                    disposition(contract.kind),
                )
            }

            val manifest = requireEntry(actual, MANIFEST_PATH)
            val payload = requireEntry(actual, PAYLOAD_PATH)
            val config = requireEntry(actual, CONFIG_PATH)
            if (!MessageDigest.isEqual(archive.uncompressedSha256(manifest), expected.manifestSha256) ||
                !MessageDigest.isEqual(archive.uncompressedSha256(payload), expected.containerSha256) ||
                !MessageDigest.isEqual(archive.uncompressedSha256(config), expected.configSha256)
            ) verificationFailure("fixedEntryDigest")

            val actualAbis = actual.mapNotNullTo(LinkedHashSet()) { entry -> runtimeAbi(entry.name) }
            if (actualAbis != expected.outputEffectiveAbis) verificationFailure("runtimeAbiSet")
        }
        return OutputVerification(
            expected.inputSha256,
            sha256(candidate),
            expected.manifestSha256,
            expected.containerSha256,
            expected.configSha256,
            verified,
            expected.inputNativeAbis,
            expected.outputEffectiveAbis,
            signingPerformed = false,
        )
    }

    private fun verifyRuntime(bytes: ByteArray, contract: ExpectedEntry, expected: ExpectedOutput) {
        val abi = contract.runtimeAbi ?: verificationFailure("runtimeAbi")
        val template = contract.runtimeTemplate ?: verificationFailure("runtimeTemplate")
        val slot = contract.runtimeSlotOffset ?: verificationFailure("runtimeSlot")
        val source = template.bytes
        if (bytes.size != source.size || slot < 0 || slot > bytes.size - SHARE_SLOT_BYTES) {
            verificationFailure("runtimeSize")
        }
        if (!MessageDigest.isEqual(sha256(source), template.sha256)) verificationFailure("runtimeTemplateDigest")
        if (!source.copyOfRange(slot, slot + 4).contentEquals(AHP0) ||
            leU2(source, slot + 4) != 1 || leU2(source, slot + 6) != abi.abiId
        ) verificationFailure("runtimeTemplateSlot")
        for (index in bytes.indices) {
            if (index !in slot until slot + SHARE_SLOT_BYTES && bytes[index] != source[index]) {
                verificationFailure("runtimeOutsideSlot")
            }
        }
        if (!bytes.copyOfRange(slot, slot + 4).contentEquals(AHS1) ||
            leU2(bytes, slot + 4) != 1 || leU2(bytes, slot + 6) != abi.abiId ||
            !rangeEquals(bytes, slot + 8, expected.keySlotId) ||
            !rangeEquals(bytes, slot + 24, expected.buildId) ||
            !rangeEquals(bytes, slot + 40, expected.rNative)
        ) verificationFailure("runtimeBinding")
        val slotDigest = MessageDigest.getInstance("SHA-256").apply { update(bytes, slot, 72) }.digest()
        val validDigest = rangeEquals(bytes, slot + 72, slotDigest)
        slotDigest.fill(0)
        if (!validDigest) {
            verificationFailure("runtimeSlotDigest")
        }
    }

    private fun disposition(kind: ExpectedContentKind): EntryDisposition = when (kind) {
        ExpectedContentKind.PRESERVED -> EntryDisposition.PRESERVED
        ExpectedContentKind.MANIFEST -> EntryDisposition.REPLACED
        else -> EntryDisposition.ADDED
    }

    private fun requireEntry(entries: List<RawZipEntry>, name: String): RawZipEntry =
        entries.singleOrNull { it.name == name } ?: verificationFailure("fixedEntry")

    private fun runtimeAbi(name: String): RuntimeAbi? = RuntimeAbi.entries.singleOrNull { runtimePath(it) == name }

    private fun isDexMagic(prefix: ByteArray): Boolean = prefix.size >= DEX_MAGIC_BYTES &&
        prefix[0] == 'd'.code.toByte() && prefix[1] == 'e'.code.toByte() && prefix[2] == 'x'.code.toByte() &&
        prefix[3] == '\n'.code.toByte() && prefix[7] == 0.toByte() &&
        prefix.sliceArray(4..6).all { it.toInt().toChar().isDigit() }

    private fun safeEntryField(name: String): String = "entry-${sha256(name.toByteArray()).copyOfRange(0, 4).toHex()}"

    private fun verificationFailure(field: String): Nothing =
        throw PackageException(PackageErrorCode.OUTPUT_VERIFICATION_FAILED, field)

    private fun rangeEquals(container: ByteArray, offset: Int, expected: ByteArray): Boolean {
        if (offset < 0 || offset > container.size - expected.size) return false
        var difference = 0
        expected.indices.forEach { index ->
            difference = difference or (container[offset + index].toInt() xor expected[index].toInt())
        }
        return difference == 0
    }

    private val AHP0 = "AHP0".toByteArray(Charsets.US_ASCII)
    private val AHS1 = "AHS1".toByteArray(Charsets.US_ASCII)
    private const val DEX_MAGIC_BYTES = 8
    private const val MAX_RUNTIME_BYTES = 64 * 1024 * 1024
}
