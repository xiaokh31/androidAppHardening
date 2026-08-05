package ah.host.container

import ah.host.inspector.InspectionLimits
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream

internal data class CompressionObservation(
    val originalLength: Long,
    val compressedLength: Long,
    val originalSha256: ByteArray,
)

internal fun observeCompression(input: InputStream, observer: ContainerObserver): CompressionObservation {
    val originalDigest = MessageDigest.getInstance("SHA-256")
    val copyBuffer = ByteArray(AhConstants.CHUNK_PLAINTEXT_MAX)
    observer.allocated("compress.copy", copyBuffer.size)
    val counter = CountingOutputStream(InspectionLimits.MAX_APK_BYTES)
    val deflater = Deflater(Deflater.BEST_COMPRESSION, false)
    var originalLength = 0L
    try {
        DeflaterOutputStream(counter, deflater, AhConstants.CHUNK_PLAINTEXT_MAX, true).use { compressed ->
            while (true) {
                checkCancellation()
                val count = input.read(copyBuffer)
                if (count < 0) break
                if (count == 0) continue
                originalLength = checkedAdd(originalLength, count.toLong(), "dexLength")
                if (originalLength > InspectionLimits.MAX_DEX_BYTES) limit("dexLength")
                originalDigest.update(copyBuffer, 0, count)
                compressed.write(copyBuffer, 0, count)
            }
            compressed.finish()
        }
        return CompressionObservation(originalLength, counter.count, originalDigest.digest())
    } finally {
        deflater.end()
        wipe("compress.copy", copyBuffer, observer)
    }
}

internal fun compressInto(
    input: InputStream,
    output: OutputStream,
    observer: ContainerObserver,
    expectedOriginalLength: Long,
    expectedCompressedLength: Long,
): CompressionObservation {
    val originalDigest = MessageDigest.getInstance("SHA-256")
    val copyBuffer = ByteArray(AhConstants.CHUNK_PLAINTEXT_MAX)
    observer.allocated("compress.copy", copyBuffer.size)
    val counting = CountingForwardingOutputStream(output, expectedCompressedLength)
    val deflater = Deflater(Deflater.BEST_COMPRESSION, false)
    var originalLength = 0L
    try {
        DeflaterOutputStream(counting, deflater, AhConstants.CHUNK_PLAINTEXT_MAX, true).use { compressed ->
            while (true) {
                checkCancellation()
                val count = input.read(copyBuffer)
                if (count < 0) break
                if (count == 0) continue
                originalLength = checkedAdd(originalLength, count.toLong(), "dexLength")
                if (originalLength > expectedOriginalLength) changed("dexLengthPass2")
                originalDigest.update(copyBuffer, 0, count)
                compressed.write(copyBuffer, 0, count)
            }
            compressed.finish()
        }
        return CompressionObservation(originalLength, counting.count, originalDigest.digest())
    } finally {
        deflater.end()
        wipe("compress.copy", copyBuffer, observer)
    }
}

internal fun checkCancellation() {
    if (Thread.currentThread().isInterrupted) {
        throw ContainerException(ContainerErrorCode.CONTAINER_INPUT_CHANGED, "cancelled")
    }
}

private class CountingOutputStream(private val maxCount: Long) : OutputStream() {
    var count: Long = 0
        private set

    override fun write(value: Int) {
        val next = checkedAdd(count, 1, "compressedLength")
        if (next > maxCount) limit("compressedLength")
        count = next
    }

    override fun write(bytes: ByteArray, offset: Int, length: Int) {
        if (offset < 0 || length < 0 || offset > bytes.size - length) format("compressedWrite")
        val next = checkedAdd(count, length.toLong(), "compressedLength")
        if (next > maxCount) limit("compressedLength")
        count = next
    }
}

private class CountingForwardingOutputStream(
    private val delegate: OutputStream,
    private val expectedLength: Long,
) : OutputStream() {
    var count: Long = 0
        private set

    override fun write(value: Int) {
        val next = checkedAdd(count, 1, "compressedLength")
        if (next > expectedLength) changed("compressedLengthPass2")
        delegate.write(value)
        count = next
    }

    override fun write(bytes: ByteArray, offset: Int, length: Int) {
        if (offset < 0 || length < 0 || offset > bytes.size - length) format("compressedWrite")
        val next = checkedAdd(count, length.toLong(), "compressedLength")
        if (next > expectedLength) changed("compressedLengthPass2")
        delegate.write(bytes, offset, length)
        count = next
    }

    override fun close() = delegate.close()
}

private fun changed(field: String): Nothing =
    throw ContainerException(ContainerErrorCode.CONTAINER_INPUT_CHANGED, field)
