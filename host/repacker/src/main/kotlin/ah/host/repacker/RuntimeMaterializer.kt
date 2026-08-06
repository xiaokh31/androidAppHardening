package ah.host.repacker

import ah.host.container.KeyPackagingMaterialV2
import ah.host.container.RuntimeAbi
import java.nio.ByteBuffer
import java.security.MessageDigest

internal data class MaterializedRuntime(
    val abi: RuntimeAbi,
    val bytes: ByteArray,
    val template: RuntimeTemplate,
    val slotOffset: Int,
)

internal object RuntimeMaterializer {
    fun materialize(bundle: RuntimeBundle, material: KeyPackagingMaterialV2): List<MaterializedRuntime> {
        val selected = material.targetAbis
        if (selected.isEmpty()) packageFailure(PackageErrorCode.PACKAGE_ABI_MISMATCH, "targetAbis")
        val config = material.configV2().copyBytes()
        val nativeShare = material.rNative().copyBytes()
        val buildId = material.buildId().copyBytes()
        val keySlotId = material.keySlotId().copyBytes()
        try {
            validateMaterial(config, nativeShare, buildId, keySlotId)
            return RuntimeAbi.entries.filter(selected::contains).map { abi ->
                val template = bundle.templates[abi]
                    ?: packageFailure(PackageErrorCode.PACKAGE_ABI_MISMATCH, "runtimeTemplate")
                val source = template.bytes
                if (!MessageDigest.isEqual(sha256(source), template.sha256)) {
                    packageFailure(PackageErrorCode.PACKAGE_ABI_MISMATCH, "templateSha256")
                }
                val slot = locateSlot(source, abi)
                validatePlaceholder(source, slot, abi)
                val output = source.copyOf()
                writeSlot(output, slot, abi, keySlotId, buildId, nativeShare)
                MaterializedRuntime(abi, output, template, slot)
            }
        } finally {
            config.fill(0)
            nativeShare.fill(0)
            buildId.fill(0)
            keySlotId.fill(0)
        }
    }

    private fun validateMaterial(config: ByteArray, nativeShare: ByteArray, buildId: ByteArray, keySlotId: ByteArray) {
        if (config.size != CONFIG_V2_BYTES || nativeShare.size != 32 || buildId.size != 16 || keySlotId.size != 16) {
            packageFailure(PackageErrorCode.PACKAGE_ABI_MISMATCH, "keyMaterialSize")
        }
        if (!config.copyOfRange(0, 4).contentEquals("AHKC".toByteArray(Charsets.US_ASCII)) ||
            leU2(config, 4) != 2 ||
            leU2(config, 6) != 0 ||
            leU4(config, 12) != CONFIG_V2_BYTES.toLong() ||
            leU2(config, 16) != 2 ||
            !config.copyOfRange(24, 40).contentEquals(buildId) ||
            !config.copyOfRange(40, 56).contentEquals(keySlotId)
        ) {
            packageFailure(PackageErrorCode.PACKAGE_ABI_MISMATCH, "configBinding")
        }
    }

    private fun locateSlot(bytes: ByteArray, abi: RuntimeAbi): Int {
        val elf = ElfLayout.parse(bytes)
        val expectedClass = when (abi) {
            RuntimeAbi.ARMEABI_V7A, RuntimeAbi.X86 -> 1
            RuntimeAbi.ARM64_V8A, RuntimeAbi.X86_64 -> 2
        }
        if (elf.elfClass != expectedClass) packageFailure(PackageErrorCode.PACKAGE_ABI_MISMATCH, "elfClass")
        if (elf.machine != expectedMachine(abi)) packageFailure(PackageErrorCode.PACKAGE_ABI_MISMATCH, "elfMachine")
        val matches = elf.sections.filter { it.name == SHARE_SECTION }
        if (matches.size != 1) packageFailure(PackageErrorCode.PACKAGE_ABI_MISMATCH, "shareSectionCount")
        val section = matches.single()
        if (section.size != SHARE_SLOT_BYTES.toLong() || section.offset > Int.MAX_VALUE ||
            section.offset < 0L || section.offset > bytes.size.toLong() - SHARE_SLOT_BYTES
        ) {
            packageFailure(PackageErrorCode.PACKAGE_ABI_MISMATCH, "shareSectionSize")
        }
        if (section.flags and ELF_SHF_ALLOC == 0L || section.flags and ELF_SHF_WRITE != 0L) {
            packageFailure(PackageErrorCode.PACKAGE_ABI_MISMATCH, "shareSectionFlags")
        }
        return section.offset.toInt()
    }

    private fun validatePlaceholder(bytes: ByteArray, offset: Int, abi: RuntimeAbi) {
        if (!bytes.copyOfRange(offset, offset + 4).contentEquals("AHP0".toByteArray(Charsets.US_ASCII)) ||
            leU2(bytes, offset + 4) != 1 || leU2(bytes, offset + 6) != abi.abiId ||
            bytes.copyOfRange(offset + 8, offset + SHARE_SLOT_BYTES).any { it != 0.toByte() }
        ) {
            packageFailure(PackageErrorCode.PACKAGE_ABI_MISMATCH, "sharePlaceholder")
        }
    }

    private fun writeSlot(
        output: ByteArray,
        offset: Int,
        abi: RuntimeAbi,
        keySlotId: ByteArray,
        buildId: ByteArray,
        nativeShare: ByteArray,
    ) {
        output.fill(0, offset, offset + SHARE_SLOT_BYTES)
        "AHS1".toByteArray(Charsets.US_ASCII).copyInto(output, offset)
        putU2(output, offset + 4, 1)
        putU2(output, offset + 6, abi.abiId)
        keySlotId.copyInto(output, offset + 8)
        buildId.copyInto(output, offset + 24)
        nativeShare.copyInto(output, offset + 40)
        sha256(output.copyOfRange(offset, offset + 72)).copyInto(output, offset + 72)
    }

    private fun expectedMachine(abi: RuntimeAbi): Int = when (abi) {
        RuntimeAbi.ARMEABI_V7A -> 40
        RuntimeAbi.ARM64_V8A -> 183
        RuntimeAbi.X86 -> 3
        RuntimeAbi.X86_64 -> 62
    }
}

private data class ElfSection(val name: String, val offset: Long, val size: Long, val flags: Long)

private class ElfLayout(val elfClass: Int, val machine: Int, val sections: List<ElfSection>) {
    companion object {
        fun parse(bytes: ByteArray): ElfLayout {
            if (bytes.size < 52 || bytes[0] != 0x7f.toByte() || bytes[1] != 'E'.code.toByte() ||
                bytes[2] != 'L'.code.toByte() || bytes[3] != 'F'.code.toByte() || bytes[5] != 1.toByte()
            ) {
                packageFailure(PackageErrorCode.PACKAGE_ABI_MISMATCH, "elfHeader")
            }
            val elfClass = bytes[4].toInt() and 0xff
            val machine = leU2(bytes, 18)
            val sectionOffset: Long
            val sectionEntrySize: Int
            val sectionCount: Int
            val stringIndex: Int
            val expectedEntrySize: Int
            if (elfClass == 1) {
                sectionOffset = leU4(bytes, 32)
                sectionEntrySize = leU2(bytes, 46)
                sectionCount = leU2(bytes, 48)
                stringIndex = leU2(bytes, 50)
                expectedEntrySize = 40
            } else if (elfClass == 2) {
                if (bytes.size < 64) packageFailure(PackageErrorCode.PACKAGE_ABI_MISMATCH, "elfHeader")
                sectionOffset = leU8(bytes, 40)
                sectionEntrySize = leU2(bytes, 58)
                sectionCount = leU2(bytes, 60)
                stringIndex = leU2(bytes, 62)
                expectedEntrySize = 64
            } else {
                packageFailure(PackageErrorCode.PACKAGE_ABI_MISMATCH, "elfClass")
            }
            if (sectionCount <= 0 || sectionCount > MAX_ELF_SECTIONS || stringIndex !in 0 until sectionCount ||
                sectionEntrySize != expectedEntrySize
            ) {
                packageFailure(PackageErrorCode.PACKAGE_ABI_MISMATCH, "elfSections")
            }
            val tableSize = checkedMultiply(sectionEntrySize.toLong(), sectionCount.toLong())
            if (sectionOffset < 0L || sectionOffset > bytes.size.toLong() - tableSize) {
                packageFailure(PackageErrorCode.PACKAGE_ABI_MISMATCH, "elfSectionTable")
            }
            fun sectionHeader(index: Int): SectionHeader {
                val base = checkedAdd(sectionOffset, index.toLong() * sectionEntrySize).toIntChecked("elfSectionOffset")
                return if (elfClass == 1) {
                    SectionHeader(
                        leU4(bytes, base).toIntChecked("elfName"),
                        leU4(bytes, base + 8),
                        leU4(bytes, base + 16),
                        leU4(bytes, base + 20),
                    )
                } else {
                    SectionHeader(
                        leU4(bytes, base).toIntChecked("elfName"),
                        leU8(bytes, base + 8),
                        leU8(bytes, base + 24),
                        leU8(bytes, base + 32),
                    )
                }
            }
            val stringHeader = sectionHeader(stringIndex)
            if (stringHeader.offset < 0L || stringHeader.size <= 0L ||
                stringHeader.offset > bytes.size.toLong() - stringHeader.size || stringHeader.size > MAX_STRING_TABLE_BYTES
            ) {
                packageFailure(PackageErrorCode.PACKAGE_ABI_MISMATCH, "elfStringTable")
            }
            val stringStart = stringHeader.offset.toIntChecked("elfStringTable")
            val stringSize = stringHeader.size.toIntChecked("elfStringTable")
            val sections = ArrayList<ElfSection>(sectionCount)
            repeat(sectionCount) { index ->
                val header = sectionHeader(index)
                if (header.offset < 0L || header.size < 0L || header.offset > bytes.size.toLong() - header.size) {
                    packageFailure(PackageErrorCode.PACKAGE_ABI_MISMATCH, "elfSectionRange")
                }
                val name = readCString(bytes, stringStart, stringSize, header.nameOffset)
                sections += ElfSection(name, header.offset, header.size, header.flags)
            }
            return ElfLayout(elfClass, machine, sections)
        }
    }
}

private data class SectionHeader(val nameOffset: Int, val flags: Long, val offset: Long, val size: Long)

private fun readCString(bytes: ByteArray, start: Int, size: Int, offset: Int): String {
    if (offset !in 0 until size) packageFailure(PackageErrorCode.PACKAGE_ABI_MISMATCH, "elfSectionName")
    var end = start + offset
    val limit = start + size
    while (end < limit && bytes[end] != 0.toByte()) end++
    if (end == limit || end - (start + offset) > MAX_SECTION_NAME_BYTES) {
        packageFailure(PackageErrorCode.PACKAGE_ABI_MISMATCH, "elfSectionName")
    }
    return bytes.copyOfRange(start + offset, end).toString(Charsets.US_ASCII)
}

private fun ByteBuffer.copyBytes(): ByteArray {
    val copy = asReadOnlyBuffer()
    val result = ByteArray(copy.remaining())
    copy.get(result)
    return result
}

private fun leU8(bytes: ByteArray, offset: Int): Long {
    if (offset < 0 || offset > bytes.size - 8) packageFailure(PackageErrorCode.PACKAGE_ABI_MISMATCH, "elfU8")
    var result = 0L
    for (index in 7 downTo 0) {
        if (index == 7 && bytes[offset + index].toInt() and 0x80 != 0) {
            packageFailure(PackageErrorCode.PACKAGE_ABI_MISMATCH, "elfU8")
        }
        result = (result shl 8) or (bytes[offset + index].toLong() and 0xff)
    }
    return result
}

private fun checkedMultiply(left: Long, right: Long): Long {
    if (left < 0L || right < 0L || (right != 0L && left > Long.MAX_VALUE / right)) {
        packageFailure(PackageErrorCode.PACKAGE_ABI_MISMATCH, "elfOverflow")
    }
    return left * right
}

private fun Long.toIntChecked(field: String): Int {
    if (this !in 0..Int.MAX_VALUE.toLong()) packageFailure(PackageErrorCode.PACKAGE_ABI_MISMATCH, field)
    return toInt()
}

internal const val SHARE_SECTION: String = ".ah_share_v1"
internal const val SHARE_SLOT_BYTES: Int = 104
internal const val CONFIG_V2_BYTES: Int = 768
private const val ELF_SHF_WRITE: Long = 0x1L
private const val ELF_SHF_ALLOC: Long = 0x2L
private const val MAX_ELF_SECTIONS: Int = 4_096
private const val MAX_STRING_TABLE_BYTES: Long = 1024L * 1024L
private const val MAX_SECTION_NAME_BYTES: Int = 255
