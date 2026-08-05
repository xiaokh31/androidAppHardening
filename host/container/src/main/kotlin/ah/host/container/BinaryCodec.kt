package ah.host.container

import ah.host.inspector.InspectionLimits
import ah.host.inspector.SignerPolicyV1
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

internal object AhConstants {
    const val HEADER_BYTES = 160
    const val RECORD_BYTES = 128
    const val CHUNK_BYTES = 32
    const val CONFIG_BYTES = 768
    const val SHA256_BYTES = 32
    const val ID_BYTES = 16
    const val KEY_BYTES = 32
    const val NONCE_PREFIX_BYTES = 8
    const val GCM_NONCE_BYTES = 12
    const val GCM_TAG_BYTES = 16
    const val CHUNK_PLAINTEXT_MAX = 65_536
    const val MAX_CHUNKS = 65_536
    const val MAX_DEX = 64
    const val MAX_LINEAGE = 16
    const val SPV1_FIXED_BYTES = 44
    const val MAJOR = 2
    const val MINOR = 0
    const val SHELL_FACTORY = "ah.runtime.bootstrap.ShellAppComponentFactory"
    const val MAX_FACTORY_UTF8_BYTES = 512
    val AHDC_MAGIC: ByteArray = "AHDC".toByteArray(Charsets.US_ASCII)
    val SPV1_MAGIC: ByteArray = "SPV1".toByteArray(Charsets.US_ASCII)
    val CONFIG_MAGIC: ByteArray = "AHKC".toByteArray(Charsets.US_ASCII)
    val CHUNK_AAD_DOMAIN: ByteArray = "AHDC-GCM-V2".toByteArray(Charsets.US_ASCII)
    val MANIFEST_INFO: ByteArray = "AHDC manifest v2".toByteArray(Charsets.US_ASCII)
    val RECORD_INFO: ByteArray = "AHDC record v2".toByteArray(Charsets.US_ASCII)
    val OFFLINE_KEK_INFO: ByteArray = "AHDC offline KEK v1".toByteArray(Charsets.US_ASCII)
}

internal data class HeaderV2(
    val dexCount: Int,
    val signerPolicySize: Int,
    val recordTableSize: Int,
    val chunkCount: Int,
    val chunkTableSize: Int,
    val payloadSize: Long,
    val buildId: ByteArray,
    val keySlotId: ByteArray,
    val configSha256: ByteArray,
    val manifestMac: ByteArray,
)

internal data class RecordV2(
    val ordinal: Int,
    val name: String,
    val originalLength: Long,
    val compressedLength: Long,
    val chunkCount: Int,
    val firstChunkIndex: Int,
    val payloadOffset: Long,
    val noncePrefix: ByteArray,
    val originalSha256: ByteArray,
)

internal data class ChunkV2(
    val recordOrdinal: Int,
    val chunkOrdinal: Int,
    val compressedOffset: Long,
    val payloadOffset: Long,
    val plaintextLength: Int,
)

internal object AhdcV2Codec {
    fun header(model: HeaderV2, zeroMac: Boolean = false): ByteArray = ByteArray(AhConstants.HEADER_BYTES).also { bytes ->
        putBytes(bytes, 0, AhConstants.AHDC_MAGIC)
        putU2(bytes, 4, AhConstants.MAJOR)
        putU2(bytes, 6, AhConstants.MINOR)
        putU2(bytes, 8, AhConstants.HEADER_BYTES)
        putU2(bytes, 10, 0)
        putU4(bytes, 12, model.dexCount.toLong())
        putU4(bytes, 16, model.signerPolicySize.toLong())
        putU4(bytes, 20, model.recordTableSize.toLong())
        putU4(bytes, 24, model.chunkCount.toLong())
        putU4(bytes, 28, model.chunkTableSize.toLong())
        putU8(bytes, 32, model.payloadSize)
        putSized(bytes, 40, model.buildId, AhConstants.ID_BYTES, "buildId")
        putSized(bytes, 56, model.keySlotId, AhConstants.ID_BYTES, "keySlotId")
        putSized(bytes, 72, model.configSha256, AhConstants.SHA256_BYTES, "configSha256")
        if (!zeroMac) putSized(bytes, 104, model.manifestMac, AhConstants.SHA256_BYTES, "manifestMac")
        putU4(bytes, 136, AhConstants.CHUNK_PLAINTEXT_MAX.toLong())
    }

    fun parseHeader(bytes: ByteArray): HeaderV2 {
        requireSize(bytes, AhConstants.HEADER_BYTES, "header")
        if (!slice(bytes, 0, 4).contentEquals(AhConstants.AHDC_MAGIC)) format("magic")
        val major = u2(bytes, 4)
        val minor = u2(bytes, 6)
        if (major != AhConstants.MAJOR || minor != AhConstants.MINOR) version("version")
        if (u2(bytes, 8) != AhConstants.HEADER_BYTES) format("headerSize")
        if (u2(bytes, 10) != 0) version("headerFlags")
        val dexCount = u4Int(bytes, 12, "dexCount")
        val signerSize = u4Int(bytes, 16, "signerPolicySize")
        val recordSize = u4Int(bytes, 20, "recordTableSize")
        val chunkCount = u4Int(bytes, 24, "chunkCount")
        val chunkSize = u4Int(bytes, 28, "chunkTableSize")
        if (dexCount !in 1..AhConstants.MAX_DEX) limit("dexCount")
        if (signerSize < AhConstants.SPV1_FIXED_BYTES + AhConstants.SHA256_BYTES ||
            signerSize > AhConstants.SPV1_FIXED_BYTES + AhConstants.MAX_LINEAGE * AhConstants.SHA256_BYTES
        ) format("signerPolicySize")
        if (recordSize != checkedIntProduct(dexCount, AhConstants.RECORD_BYTES, "recordTableSize")) format("recordTableSize")
        if (chunkCount !in 1..AhConstants.MAX_CHUNKS) limit("chunkCount")
        if (chunkSize != checkedIntProduct(chunkCount, AhConstants.CHUNK_BYTES, "chunkTableSize")) format("chunkTableSize")
        if (u4(bytes, 136) != AhConstants.CHUNK_PLAINTEXT_MAX.toLong()) version("chunkPlaintextMax")
        requireZero(bytes, 140, 20, "headerReserved")
        return HeaderV2(
            dexCount,
            signerSize,
            recordSize,
            chunkCount,
            chunkSize,
            u8(bytes, 32, "payloadSize"),
            slice(bytes, 40, AhConstants.ID_BYTES),
            slice(bytes, 56, AhConstants.ID_BYTES),
            slice(bytes, 72, AhConstants.SHA256_BYTES),
            slice(bytes, 104, AhConstants.SHA256_BYTES),
        )
    }

    fun spv1(signer: SignerPolicyV1): ByteArray {
        val lineage = signer.lineageCertificateSha256
        if (signer.policyVersion != 1 || lineage.size !in 1..AhConstants.MAX_LINEAGE ||
            !lineage.last().constantTimeEquals(signer.currentCertificateSha256)
        ) format("signerPolicy")
        val bytes = ByteArray(checkedAddInt(AhConstants.SPV1_FIXED_BYTES, lineage.size * AhConstants.SHA256_BYTES, "spv1"))
        putBytes(bytes, 0, AhConstants.SPV1_MAGIC)
        putU2(bytes, 4, 1)
        putU2(bytes, 6, 0)
        putU2(bytes, 8, lineage.size)
        putU2(bytes, 10, 0)
        putSized(bytes, 12, signer.currentCertificateSha256, AhConstants.SHA256_BYTES, "currentSigner")
        lineage.forEachIndexed { index, digest ->
            putSized(bytes, AhConstants.SPV1_FIXED_BYTES + index * AhConstants.SHA256_BYTES, digest, AhConstants.SHA256_BYTES, "lineage")
        }
        return bytes
    }

    fun parseSpv1(bytes: ByteArray): Pair<ByteArray, List<ByteArray>> {
        if (bytes.size < AhConstants.SPV1_FIXED_BYTES + AhConstants.SHA256_BYTES ||
            !slice(bytes, 0, 4).contentEquals(AhConstants.SPV1_MAGIC) || u2(bytes, 4) != 1 ||
            u2(bytes, 6) != 0 || u2(bytes, 10) != 0
        ) format("spv1")
        val count = u2(bytes, 8)
        if (count !in 1..AhConstants.MAX_LINEAGE ||
            bytes.size != AhConstants.SPV1_FIXED_BYTES + count * AhConstants.SHA256_BYTES
        ) format("lineageCount")
        val current = slice(bytes, 12, AhConstants.SHA256_BYTES)
        val lineage = (0 until count).map { index ->
            slice(bytes, AhConstants.SPV1_FIXED_BYTES + index * AhConstants.SHA256_BYTES, AhConstants.SHA256_BYTES)
        }
        if (!lineage.last().constantTimeEquals(current) || lineage.map(ByteArray::toHex).distinct().size != lineage.size) {
            format("lineage")
        }
        return current to lineage
    }

    fun record(model: RecordV2): ByteArray = ByteArray(AhConstants.RECORD_BYTES).also { bytes ->
        val name = model.name.toByteArray(Charsets.US_ASCII)
        if (name.size > 24 || model.name != canonicalDexName(model.ordinal)) format("recordName")
        putU4(bytes, 0, model.ordinal.toLong())
        putU2(bytes, 4, name.size)
        putU2(bytes, 6, 0)
        putU8(bytes, 8, model.originalLength)
        putU8(bytes, 16, model.compressedLength)
        putU4(bytes, 24, model.chunkCount.toLong())
        putU4(bytes, 28, model.firstChunkIndex.toLong())
        putU8(bytes, 32, model.payloadOffset)
        putSized(bytes, 40, model.noncePrefix, AhConstants.NONCE_PREFIX_BYTES, "noncePrefix")
        putBytes(bytes, 48, name)
        putSized(bytes, 72, model.originalSha256, AhConstants.SHA256_BYTES, "originalSha256")
    }

    fun parseRecord(bytes: ByteArray): RecordV2 {
        requireSize(bytes, AhConstants.RECORD_BYTES, "record")
        val ordinal = u4Int(bytes, 0, "recordOrdinal")
        val nameLength = u2(bytes, 4)
        if (u2(bytes, 6) != 0 || nameLength !in 1..24) format("recordFlags")
        requireZero(bytes, 48 + nameLength, 24 - nameLength, "recordNamePadding")
        requireZero(bytes, 104, 24, "recordReserved")
        val nameBytes = slice(bytes, 48, nameLength)
        if (nameBytes.any { (it.toInt() and 0xff) !in 0x21..0x7e }) format("recordName")
        val name = nameBytes.toString(Charsets.US_ASCII)
        if (name != canonicalDexName(ordinal)) format("recordName")
        val originalLength = u8(bytes, 8, "originalLength")
        val compressedLength = u8(bytes, 16, "compressedLength")
        if (originalLength !in 1..InspectionLimits.MAX_DEX_BYTES || compressedLength <= 0) limit("recordLength")
        val noncePrefix = slice(bytes, 40, AhConstants.NONCE_PREFIX_BYTES)
        if (noncePrefix.all { it == 0.toByte() }) format("noncePrefix")
        return RecordV2(
            ordinal,
            name,
            originalLength,
            compressedLength,
            u4Int(bytes, 24, "recordChunkCount"),
            u4Int(bytes, 28, "firstChunkIndex"),
            u8(bytes, 32, "recordPayloadOffset"),
            noncePrefix,
            slice(bytes, 72, AhConstants.SHA256_BYTES),
        )
    }

    fun chunk(model: ChunkV2): ByteArray = ByteArray(AhConstants.CHUNK_BYTES).also { bytes ->
        putU4(bytes, 0, model.recordOrdinal.toLong())
        putU4(bytes, 4, model.chunkOrdinal.toLong())
        putU8(bytes, 8, model.compressedOffset)
        putU8(bytes, 16, model.payloadOffset)
        putU4(bytes, 24, model.plaintextLength.toLong())
    }

    fun parseChunk(bytes: ByteArray): ChunkV2 {
        requireSize(bytes, AhConstants.CHUNK_BYTES, "chunk")
        if (u4(bytes, 28) != 0L) format("chunkReserved")
        val length = u4Int(bytes, 24, "chunkLength")
        if (length !in 1..AhConstants.CHUNK_PLAINTEXT_MAX) format("chunkLength")
        return ChunkV2(
            u4Int(bytes, 0, "chunkRecordOrdinal"),
            u4Int(bytes, 4, "chunkOrdinal"),
            u8(bytes, 8, "compressedOffset"),
            u8(bytes, 16, "chunkPayloadOffset"),
            length,
        )
    }
}

internal fun canonicalDexName(ordinal: Int): String = if (ordinal == 0) "classes.dex" else "classes${ordinal + 1}.dex"

internal fun chunksForLength(compressedLength: Long): Int {
    if (compressedLength <= 0) format("compressedLength")
    val count = checkedAdd(compressedLength, AhConstants.CHUNK_PLAINTEXT_MAX - 1L, "chunkCount") /
        AhConstants.CHUNK_PLAINTEXT_MAX
    if (count !in 1..AhConstants.MAX_CHUNKS.toLong()) limit("chunkCount")
    return count.toInt()
}

internal fun expectedChunk(record: RecordV2, chunkOrdinal: Int): ChunkV2 {
    if (chunkOrdinal !in 0 until record.chunkCount) format("chunkOrdinal")
    val compressedOffset = chunkOrdinal.toLong() * AhConstants.CHUNK_PLAINTEXT_MAX
    val remaining = record.compressedLength - compressedOffset
    val length = minOf(remaining, AhConstants.CHUNK_PLAINTEXT_MAX.toLong()).toInt()
    val payloadOffset = checkedAdd(
        record.payloadOffset,
        checkedAdd(compressedOffset, chunkOrdinal.toLong() * AhConstants.GCM_TAG_BYTES, "chunkPayloadOffset"),
        "chunkPayloadOffset",
    )
    return ChunkV2(record.ordinal, chunkOrdinal, compressedOffset, payloadOffset, length)
}

internal fun checkedAdd(left: Long, right: Long, field: String): Long {
    if (left < 0 || right < 0 || left > Long.MAX_VALUE - right) limit(field)
    return left + right
}

internal fun checkedMultiply(left: Long, right: Long, field: String): Long {
    if (left < 0 || right < 0 || (left != 0L && right > Long.MAX_VALUE / left)) limit(field)
    return left * right
}

internal fun checkedAddInt(left: Int, right: Int, field: String): Int {
    val value = left.toLong() + right.toLong()
    if (value !in 0..Int.MAX_VALUE.toLong()) limit(field)
    return value.toInt()
}

internal fun checkedIntProduct(left: Int, right: Int, field: String): Int {
    val value = left.toLong() * right.toLong()
    if (value !in 0..Int.MAX_VALUE.toLong()) limit(field)
    return value.toInt()
}

internal fun ByteArray.headerVersionBytes(): ByteArray = slice(this, 4, 4)

internal fun FileChannel.writeAll(bytes: ByteArray) {
    val buffer = ByteBuffer.wrap(bytes)
    while (buffer.hasRemaining()) write(buffer)
}

internal fun FileChannel.readExact(offset: Long, size: Int): ByteArray {
    if (offset < 0 || size < 0) format("fileRange")
    val bytes = ByteArray(size)
    val buffer = ByteBuffer.wrap(bytes)
    var position = offset
    while (buffer.hasRemaining()) {
        val count = read(buffer, position)
        if (count < 0) format("truncated")
        if (count == 0) format("shortRead")
        position += count
    }
    return bytes
}

internal fun putU2(bytes: ByteArray, offset: Int, value: Int) {
    if (value !in 0..0xffff) limit("u16")
    ByteBuffer.wrap(bytes, offset, 2).order(ByteOrder.LITTLE_ENDIAN).putShort(value.toShort())
}

internal fun putU4(bytes: ByteArray, offset: Int, value: Long) {
    if (value !in 0..0xffff_ffffL) limit("u32")
    ByteBuffer.wrap(bytes, offset, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(value.toInt())
}

internal fun putU8(bytes: ByteArray, offset: Int, value: Long) {
    if (value < 0) limit("u64")
    ByteBuffer.wrap(bytes, offset, 8).order(ByteOrder.LITTLE_ENDIAN).putLong(value)
}

internal fun u2(bytes: ByteArray, offset: Int): Int =
    ByteBuffer.wrap(bytes, offset, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xffff

internal fun u4(bytes: ByteArray, offset: Int): Long =
    ByteBuffer.wrap(bytes, offset, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xffff_ffffL

internal fun u4Int(bytes: ByteArray, offset: Int, field: String): Int {
    val value = u4(bytes, offset)
    if (value > Int.MAX_VALUE.toLong()) limit(field)
    return value.toInt()
}

internal fun u8(bytes: ByteArray, offset: Int, field: String): Long {
    val value = ByteBuffer.wrap(bytes, offset, 8).order(ByteOrder.LITTLE_ENDIAN).long
    if (value < 0) limit(field)
    return value
}

internal fun putBytes(target: ByteArray, offset: Int, value: ByteArray) {
    if (offset < 0 || offset > target.size - value.size) format("byteRange")
    value.copyInto(target, offset)
}

internal fun putSized(target: ByteArray, offset: Int, value: ByteArray, size: Int, field: String) {
    if (value.size != size) format(field)
    putBytes(target, offset, value)
}

internal fun slice(bytes: ByteArray, offset: Int, size: Int): ByteArray {
    if (offset < 0 || size < 0 || offset > bytes.size - size) format("byteRange")
    return bytes.copyOfRange(offset, offset + size)
}

internal fun requireZero(bytes: ByteArray, offset: Int, size: Int, field: String) {
    if (size > 0 && slice(bytes, offset, size).any { it != 0.toByte() }) format(field)
}

internal fun requireSize(bytes: ByteArray, size: Int, field: String) {
    if (bytes.size != size) format(field)
}

internal fun format(field: String): Nothing = throw ContainerException(ContainerErrorCode.CONTAINER_FORMAT, field)
internal fun version(field: String): Nothing = throw ContainerException(ContainerErrorCode.CONTAINER_VERSION, field)
internal fun limit(field: String): Nothing = throw ContainerException(ContainerErrorCode.CONTAINER_LIMIT_EXCEEDED, field)
