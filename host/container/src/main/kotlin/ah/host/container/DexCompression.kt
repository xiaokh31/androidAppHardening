package ah.host.container

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
    val counter = CountingOutputStream()
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

internal fun compressInto(input: InputStream, output: OutputStream, observer: ContainerObserver): CompressionObservation {
    val originalDigest = MessageDigest.getInstance("SHA-256")
    val copyBuffer = ByteArray(AhConstants.CHUNK_PLAINTEXT_MAX)
    observer.allocated("compress.copy", copyBuffer.size)
    val counting = CountingForwardingOutputStream(output)
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

private class CountingOutputStream : OutputStream() {
    var count: Long = 0
        private set

    override fun write(value: Int) {
        count = checkedAdd(count, 1, "compressedLength")
    }

    override fun write(bytes: ByteArray, offset: Int, length: Int) {
        if (offset < 0 || length < 0 || offset > bytes.size - length) format("compressedWrite")
        count = checkedAdd(count, length.toLong(), "compressedLength")
    }
}

private class CountingForwardingOutputStream(private val delegate: OutputStream) : OutputStream() {
    var count: Long = 0
        private set

    override fun write(value: Int) {
        delegate.write(value)
        count = checkedAdd(count, 1, "compressedLength")
    }

    override fun write(bytes: ByteArray, offset: Int, length: Int) {
        delegate.write(bytes, offset, length)
        count = checkedAdd(count, length.toLong(), "compressedLength")
    }

    override fun close() = delegate.close()
}
