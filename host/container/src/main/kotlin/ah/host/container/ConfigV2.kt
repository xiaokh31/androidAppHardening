package ah.host.container

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

internal data class ConfigV2Material(
    val bytes: ByteArray,
    val rNative: ByteArray,
)

internal object ConfigV2Codec {
    private const val PREFIX_BYTES = 132
    private const val FACTORY_OFFSET = 180
    private const val FACTORY_SLOT_BYTES = 512
    private const val RESERVED_OFFSET = 692

    fun build(
        originalFactory: String?,
        buildId: ByteArray,
        keySlotId: ByteArray,
        signerSha256: ByteArray,
        packageNameSha256: ByteArray,
        cek: ByteArray,
        rootMaterial: ByteArray,
        rJava: ByteArray,
        wrapNonce: ByteArray,
    ): ConfigV2Material {
        validateFixedInputs(buildId, keySlotId, signerSha256, packageNameSha256, cek, rootMaterial, rJava, wrapNonce)
        val factoryBytes = originalFactory?.toByteArray(Charsets.UTF_8) ?: ByteArray(0)
        validateFactory(originalFactory, factoryBytes)
        val config = ByteArray(AhConstants.CONFIG_BYTES)
        val rNative = ByteArray(AhConstants.KEY_BYTES) { index ->
            (rootMaterial[index].toInt() xor rJava[index].toInt()).toByte()
        }
        var kek: ByteArray? = null
        var envelope: ByteArray? = null
        var prefix: ByteArray? = null
        try {
            putBytes(config, 0, AhConstants.CONFIG_MAGIC)
            putU2(config, 4, AhConstants.MAJOR)
            putU2(config, 6, AhConstants.MINOR)
            putU2(config, 8, if (factoryBytes.isEmpty()) 0 else 1)
            putU4(config, 12, AhConstants.CONFIG_BYTES.toLong())
            putU2(config, 16, AhConstants.MAJOR)
            putU2(config, 18, 1)
            putU2(config, 20, 1)
            putU2(config, 22, factoryBytes.size)
            putBytes(config, 24, buildId)
            putBytes(config, 40, keySlotId)
            putBytes(config, 56, signerSha256)
            putBytes(config, 88, rJava)
            putBytes(config, 120, wrapNonce)
            if (factoryBytes.isNotEmpty()) putBytes(config, FACTORY_OFFSET, factoryBytes)
            prefix = slice(config, 0, PREFIX_BYTES)
            kek = ContainerCrypto.offlineKek(rootMaterial, buildId, signerSha256, packageNameSha256)
            envelope = ContainerCrypto.aesGcmEncrypt(kek, wrapNonce, prefix, cek)
            if (envelope.size != AhConstants.KEY_BYTES + AhConstants.GCM_TAG_BYTES) format("wrappedCek")
            envelope.copyInto(config, 132, 0, AhConstants.KEY_BYTES)
            envelope.copyInto(config, 164, AhConstants.KEY_BYTES, envelope.size)
            return ConfigV2Material(config, rNative)
        } catch (failure: Throwable) {
            config.fill(0)
            rNative.fill(0)
            throw failure
        } finally {
            factoryBytes.fill(0)
            kek?.fill(0)
            envelope?.fill(0)
            prefix?.fill(0)
        }
    }

    fun recoverCek(
        config: ByteArray,
        rNative: ByteArray,
        expectedBuildId: ByteArray,
        expectedKeySlotId: ByteArray,
        expectedSignerSha256: ByteArray,
        expectedPackageNameSha256: ByteArray,
    ): ByteArray {
        parseAndValidate(config, expectedBuildId, expectedKeySlotId, expectedSignerSha256)
        if (rNative.size != AhConstants.KEY_BYTES) format("rNative")
        val rJava = slice(config, 88, AhConstants.KEY_BYTES)
        val root = ByteArray(AhConstants.KEY_BYTES) { index ->
            (rNative[index].toInt() xor rJava[index].toInt()).toByte()
        }
        val nonce = slice(config, 120, AhConstants.GCM_NONCE_BYTES)
        val prefix = slice(config, 0, PREFIX_BYTES)
        val envelope = slice(config, 132, AhConstants.KEY_BYTES + AhConstants.GCM_TAG_BYTES)
        var kek: ByteArray? = null
        try {
            kek = ContainerCrypto.offlineKek(root, expectedBuildId, expectedSignerSha256, expectedPackageNameSha256)
            val cek = ContainerCrypto.aesGcmDecrypt(kek, nonce, prefix, envelope)
            if (cek.size != AhConstants.KEY_BYTES) format("cek")
            return cek
        } finally {
            rJava.fill(0)
            root.fill(0)
            nonce.fill(0)
            prefix.fill(0)
            envelope.fill(0)
            kek?.fill(0)
        }
    }

    fun originalFactory(config: ByteArray): String? {
        parseAndValidate(config, slice(config, 24, 16), slice(config, 40, 16), slice(config, 56, 32))
        val length = u2(config, 22)
        return if (length == 0) null else decodeFactory(slice(config, FACTORY_OFFSET, length))
    }

    private fun parseAndValidate(
        config: ByteArray,
        expectedBuildId: ByteArray,
        expectedKeySlotId: ByteArray,
        expectedSignerSha256: ByteArray,
    ) {
        requireSize(config, AhConstants.CONFIG_BYTES, "configSize")
        if (!slice(config, 0, 4).contentEquals(AhConstants.CONFIG_MAGIC)) format("configMagic")
        if (u2(config, 4) != AhConstants.MAJOR || u2(config, 6) != AhConstants.MINOR) version("configVersion")
        val flags = u2(config, 8)
        if (flags and 1.inv() != 0 || u2(config, 10) != 0) version("configFlags")
        if (u4(config, 12) != AhConstants.CONFIG_BYTES.toLong() || u2(config, 16) != AhConstants.MAJOR) {
            version("configContainer")
        }
        if (u2(config, 18) != 1 || u2(config, 20) != 1) version("configPolicy")
        val factoryLength = u2(config, 22)
        if ((flags == 0 && factoryLength != 0) || (flags == 1 && factoryLength !in 1..FACTORY_SLOT_BYTES)) {
            format("factoryLength")
        }
        if (!slice(config, 24, 16).constantTimeEquals(expectedBuildId)) format("configBuildId")
        if (!slice(config, 40, 16).constantTimeEquals(expectedKeySlotId)) format("configKeySlotId")
        if (!slice(config, 56, 32).constantTimeEquals(expectedSignerSha256)) {
            throw ContainerException(ContainerErrorCode.CONTAINER_AUTH_FAILED, "configSigner")
        }
        val factoryBytes = slice(config, FACTORY_OFFSET, factoryLength)
        val factory = if (factoryLength == 0) null else decodeFactory(factoryBytes)
        try {
            validateFactory(factory, factoryBytes)
        } finally {
            factoryBytes.fill(0)
        }
        requireZero(config, FACTORY_OFFSET + factoryLength, FACTORY_SLOT_BYTES - factoryLength, "factoryPadding")
        requireZero(config, RESERVED_OFFSET, AhConstants.CONFIG_BYTES - RESERVED_OFFSET, "configReserved")
    }

    private fun validateFixedInputs(vararg values: ByteArray) {
        val sizes = intArrayOf(16, 16, 32, 32, 32, 32, 32, 12)
        values.forEachIndexed { index, value -> if (value.size != sizes[index]) format("configInput$index") }
    }

    private fun validateFactory(factory: String?, bytes: ByteArray) {
        if (factory == null) {
            if (bytes.isNotEmpty()) format("factory")
            return
        }
        if (bytes.isEmpty() || bytes.size > AhConstants.MAX_FACTORY_UTF8_BYTES || factory.indexOf('\u0000') >= 0 ||
            factory == AhConstants.SHELL_FACTORY || !JAVA_CLASS_NAME.matches(factory)
        ) format("factory")
    }

    private fun decodeFactory(bytes: ByteArray): String = try {
        val value = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
        if (!value.toByteArray(Charsets.UTF_8).contentEquals(bytes)) format("factoryUtf8")
        value
    } catch (exception: CharacterCodingException) {
        throw ContainerException(ContainerErrorCode.CONTAINER_FORMAT, "factoryUtf8", exception)
    }

    private val JAVA_CLASS_NAME = Regex("(?:[A-Za-z_$][A-Za-z0-9_$]*\\.)*[A-Za-z_$][A-Za-z0-9_$]*")
}
