package ah.host.container

import ah.host.inspector.ApkInspection
import ah.host.inspector.DexSummary
import ah.host.inspector.LimitsApplied
import ah.host.inspector.ManifestSummary
import ah.host.inspector.NativeAbiSummary
import ah.host.inspector.SignerPolicyV1
import ah.host.inspector.VerifiedScheme
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object M202DeviceVectorMain {
    @JvmStatic
    fun main(arguments: Array<String>) {
        require(arguments.size == 6) {
            "usage: <classes.dex> <classes2.dex> <output-root> <package-name> <signer-sha256> <original-factory-or-dash>"
        }
        val primary = Path.of(arguments[0]).toAbsolutePath().normalize()
        val secondary = Path.of(arguments[1]).toAbsolutePath().normalize()
        val outputRoot = Path.of(arguments[2]).toAbsolutePath().normalize()
        val packageName = arguments[3]
        val signer = hex(arguments[4])
        val originalFactory = arguments[5].takeUnless { it == "-" }
        require(signer.size == 32 && packageName.matches(Regex("[a-zA-Z][a-zA-Z0-9_.]{2,254}")))
        require(originalFactory == null || originalFactory.matches(Regex("[a-zA-Z_$][a-zA-Z0-9_$.]{2,511}")))
        val root = findRoot()
        val allowed = listOf(root.resolve("build"), root.resolve("artifacts"))
        require(allowed.any { outputRoot == it || outputRoot.startsWith(it) }) {
            "output root must be under ignored build/ or artifacts/"
        }
        Files.createDirectories(outputRoot)
        val input = outputRoot.resolve("input-dex.zip")
        val dexFiles = listOf(primary, secondary)
        ZipOutputStream(Files.newOutputStream(input)).use { zip ->
            dexFiles.forEachIndexed { index, path ->
                require(Files.isRegularFile(path) && Files.size(path) in 112..536_870_912)
                zip.putNextEntry(ZipEntry(if (index == 0) "classes.dex" else "classes2.dex"))
                Files.copy(path, zip)
                zip.closeEntry()
            }
        }
        val inputHash = sha256(input)
        val packageHash = sha256(packageName.toByteArray(Charsets.UTF_8))
        val inspection = ApkInspection(
            inputHash,
            ManifestSummary(packageName, packageHash, 29, 36, null, originalFactory, null),
            emptyList(),
            dexFiles.mapIndexed { index, path ->
                DexSummary(
                    if (index == 0) "classes.dex" else "classes2.dex",
                    index,
                    Files.size(path),
                    1,
                    sha256(path),
                )
            },
            NativeAbiSummary(listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")),
            emptyList(),
            "m2-02-device-vector",
            LimitsApplied(emptyMap()),
        )
        val signerPolicy = SignerPolicyV1(signer, listOf(signer), setOf(VerifiedScheme.V2))
        val container = outputRoot.resolve("payload.ahdc")
        val result = DexContainerBuilder(input).build(inspection, signerPolicy, container)
        var expectedBuildIdHex: String? = null
        var expectedKeySlotIdHex: String? = null
        result.keyPackagingPlan.consume { material ->
            Files.write(outputRoot.resolve("config.bin"), material.configV2().copyBytes())
            val rNative = material.rNative().copyBytes()
            val buildId = material.buildId().copyBytes()
            val keySlotId = material.keySlotId().copyBytes()
            try {
                expectedBuildIdHex = buildId.hex()
                expectedKeySlotIdHex = keySlotId.hex()
                material.targetAbis.forEach { abi ->
                    Files.write(
                        outputRoot.resolve("slot-${abi.directoryName}.bin"),
                        slot(abi, keySlotId, buildId, rNative),
                    )
                }
            } finally {
                rNative.fill(0)
                buildId.fill(0)
                keySlotId.fill(0)
            }
        }
        val report = buildString {
            append("{\n")
            append("  \"task_id\": \"M2-02\",\n")
            append("  \"package_name\": \"").append(packageName).append("\",\n")
            append("  \"original_factory\": ")
            if (originalFactory == null) append("null,\n")
            else append("\"").append(originalFactory).append("\",\n")
            append("  \"source_dex_sha256\": [\"")
            append(sha256(primary).hex()).append("\", \"")
            append(sha256(secondary).hex()).append("\"],\n")
            append("  \"container_sha256\": \"").append(sha256(container).hex()).append("\",\n")
            append("  \"config_sha256\": \"")
            append(sha256(outputRoot.resolve("config.bin")).hex()).append("\",\n")
            append("  \"build_id_hex\": \"").append(requireNotNull(expectedBuildIdHex))
                .append("\",\n")
            append("  \"key_slot_id_hex\": \"").append(requireNotNull(expectedKeySlotIdHex))
                .append("\",\n")
            append("  \"result\": \"PASS\"\n")
            append("}\n")
        }
        Files.writeString(outputRoot.resolve("vector-report.json"), report)
        signer.fill(0)
    }

    private fun slot(
        abi: RuntimeAbi,
        keySlotId: ByteArray,
        buildId: ByteArray,
        rNative: ByteArray,
    ): ByteArray {
        val bytes = ByteBuffer.allocate(104).order(ByteOrder.LITTLE_ENDIAN)
        bytes.put("AHS1".toByteArray(Charsets.US_ASCII))
        bytes.putShort(1.toShort())
        bytes.putShort(abi.abiId.toShort())
        bytes.put(keySlotId)
        bytes.put(buildId)
        bytes.put(rNative)
        val digest = sha256(bytes.array().copyOfRange(0, 72))
        bytes.put(digest)
        digest.fill(0)
        return bytes.array()
    }

    private fun ByteBuffer.copyBytes(): ByteArray = asReadOnlyBuffer().let { copy ->
        ByteArray(copy.remaining()).also { copy.get(it) }
    }

    private fun sha256(path: Path): ByteArray = Files.newInputStream(path).use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
        buffer.fill(0)
        digest.digest()
    }

    private fun sha256(bytes: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(bytes)

    private fun hex(value: String): ByteArray {
        require(value.matches(Regex("[0-9a-fA-F]{64}")))
        return ByteArray(32) { index -> value.substring(index * 2, index * 2 + 2).toInt(16).toByte() }
    }

    private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun findRoot(): Path {
        var current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()
        while (!Files.exists(current.resolve("settings.gradle.kts"))) {
            current = requireNotNull(current.parent)
        }
        return current
    }
}
