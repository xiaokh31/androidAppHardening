package ah.host.container

import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.GeneralSecurityException
import java.security.MessageDigest
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

internal object ContainerCrypto {
    private const val HASH_BUFFER_BYTES = 65_536

    fun sha256(vararg values: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        values.forEach(digest::update)
        return digest.digest()
    }

    fun sha256(input: InputStream): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(HASH_BUFFER_BYTES)
        try {
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count == 0) continue
                digest.update(buffer, 0, count)
            }
            return digest.digest()
        } finally {
            buffer.fill(0)
        }
    }

    fun sha256(path: Path): ByteArray = Files.newInputStream(path).use(::sha256)

    fun hmacSha256(key: ByteArray, vararg values: ByteArray): ByteArray = try {
        newHmacSha256(key).run {
            values.forEach(::update)
            doFinal()
        }
    } catch (exception: GeneralSecurityException) {
        throw ContainerException(ContainerErrorCode.CONTAINER_CRYPTO, "hmac", exception)
    }

    fun newHmacSha256(key: ByteArray): Mac = try {
        Mac.getInstance("HmacSHA256").also { mac -> mac.init(SecretKeySpec(key, "HmacSHA256")) }
    } catch (exception: GeneralSecurityException) {
        throw ContainerException(ContainerErrorCode.CONTAINER_CRYPTO, "hmac", exception)
    }

    fun hkdfSha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int = AhConstants.KEY_BYTES): ByteArray {
        if (length !in 1..(255 * AhConstants.SHA256_BYTES)) limit("hkdfLength")
        val effectiveSalt = if (salt.isEmpty()) ByteArray(AhConstants.SHA256_BYTES) else salt
        val prk = hmacSha256(effectiveSalt, ikm)
        val output = ByteArray(length)
        var previous = ByteArray(0)
        var offset = 0
        var counter = 1
        try {
            while (offset < length) {
                val block = hmacSha256(prk, previous, info, byteArrayOf(counter.toByte()))
                previous.fill(0)
                previous = block
                val count = minOf(block.size, length - offset)
                block.copyInto(output, offset, 0, count)
                offset += count
                counter++
            }
            return output
        } finally {
            prk.fill(0)
            previous.fill(0)
            if (effectiveSalt !== salt) effectiveSalt.fill(0)
        }
    }

    fun aesGcmEncrypt(key: ByteArray, nonce: ByteArray, aad: ByteArray, plaintext: ByteArray): ByteArray =
        aesGcm(Cipher.ENCRYPT_MODE, key, nonce, aad, plaintext, "encrypt")

    fun aesGcmDecrypt(key: ByteArray, nonce: ByteArray, aad: ByteArray, ciphertextAndTag: ByteArray): ByteArray = try {
        aesGcm(Cipher.DECRYPT_MODE, key, nonce, aad, ciphertextAndTag, "decrypt")
    } catch (exception: ContainerException) {
        if (exception.cause is AEADBadTagException) {
            throw ContainerException(ContainerErrorCode.CONTAINER_AUTH_FAILED, "gcmTag", exception.cause)
        }
        throw exception
    }

    fun offlineKek(
        rootMaterial: ByteArray,
        buildId: ByteArray,
        signerSha256: ByteArray,
        packageNameSha256: ByteArray,
    ): ByteArray {
        val info = AhConstants.OFFLINE_KEK_INFO + signerSha256 + packageNameSha256
        return try {
            hkdfSha256(rootMaterial, buildId, info)
        } finally {
            info.fill(0)
        }
    }

    fun manifestKey(cek: ByteArray, buildId: ByteArray): ByteArray =
        hkdfSha256(cek, buildId, AhConstants.MANIFEST_INFO)

    fun recordKey(cek: ByteArray, buildId: ByteArray, recordOrdinal: Int): ByteArray {
        val ordinal = ByteArray(Int.SIZE_BYTES)
        putU4(ordinal, 0, recordOrdinal.toLong())
        return try {
            hkdfSha256(cek, buildId, AhConstants.RECORD_INFO + ordinal)
        } finally {
            ordinal.fill(0)
        }
    }

    fun chunkNonce(prefix: ByteArray, chunkOrdinal: Int): ByteArray {
        if (prefix.size != AhConstants.NONCE_PREFIX_BYTES || chunkOrdinal < 0) format("chunkNonce")
        return ByteArray(AhConstants.GCM_NONCE_BYTES).also { nonce ->
            prefix.copyInto(nonce)
            putU4(nonce, AhConstants.NONCE_PREFIX_BYTES, chunkOrdinal.toLong())
        }
    }

    fun chunkAad(
        headerVersion: ByteArray,
        buildId: ByteArray,
        keySlotId: ByteArray,
        signerSha256: ByteArray,
        packageNameSha256: ByteArray,
        recordBytes: ByteArray,
        chunkBytes: ByteArray,
    ): ByteArray = AhConstants.CHUNK_AAD_DOMAIN + headerVersion + buildId + keySlotId +
        signerSha256 + packageNameSha256 + recordBytes + chunkBytes

    private fun aesGcm(
        mode: Int,
        key: ByteArray,
        nonce: ByteArray,
        aad: ByteArray,
        input: ByteArray,
        field: String,
    ): ByteArray {
        if (key.size != AhConstants.KEY_BYTES || nonce.size != AhConstants.GCM_NONCE_BYTES) format(field)
        return try {
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(AhConstants.GCM_TAG_BYTES * 8, nonce))
                updateAAD(aad)
                doFinal(input)
            }
        } catch (exception: GeneralSecurityException) {
            throw ContainerException(ContainerErrorCode.CONTAINER_CRYPTO, field, exception)
        }
    }
}
