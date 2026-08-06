package ah.host.repacker

import ah.host.axml.ManifestTransformResult
import ah.host.container.DexContainerDescriptor
import ah.host.container.KeyPackagingPlanV2
import ah.host.container.RuntimeAbi
import ah.host.inspector.ApkInspection
import ah.host.inspector.SignerPolicyV1
import java.nio.file.Path
import java.util.Collections

enum class PackageErrorCode {
    PACKAGE_ENTRY_CONFLICT,
    PACKAGE_ABI_MISMATCH,
    PACKAGE_ALIGNMENT,
    PACKAGE_WRITE_FAILED,
    COMPAT_ABI_UNSUPPORTED,
    OUTPUT_PATH_ALIAS,
    OUTPUT_ALREADY_EXISTS,
    OUTPUT_VERIFICATION_FAILED,
    OUTPUT_ATOMIC_MOVE_UNSUPPORTED,
    OUTPUT_INPUT_CHANGED,
}

class PackageException(
    val code: PackageErrorCode,
    val field: String? = null,
) : Exception(if (field == null) code.name else "${code.name} field=$field")

class RuntimeTemplate(
    val abi: RuntimeAbi,
    bytes: ByteArray,
    sha256: ByteArray,
) {
    private val templateBytes = bytes.copyOf()
    private val templateDigest = requireSha256(sha256)

    val bytes: ByteArray get() = templateBytes.copyOf()
    val sha256: ByteArray get() = templateDigest.copyOf()
}

class RuntimeBundle(
    bootstrapDex: ByteArray,
    templates: Map<RuntimeAbi, RuntimeTemplate>,
) {
    private val bootstrap = bootstrapDex.copyOf()
    private val runtimeTemplates: Map<RuntimeAbi, RuntimeTemplate>

    init {
        require(bootstrap.isNotEmpty()) { "bootstrap DEX is empty" }
        require(bootstrap.size <= MAX_BOOTSTRAP_BYTES) { "bootstrap DEX exceeds limit" }
        require(templates.keys == RuntimeAbi.entries.toSet()) { "RuntimeBundle must contain exactly four ABIs" }
        require(templates.all { (abi, template) -> template.abi == abi }) { "RuntimeBundle ABI key mismatch" }
        runtimeTemplates = Collections.unmodifiableMap(LinkedHashMap(templates))
    }

    val bootstrapDex: ByteArray get() = bootstrap.copyOf()
    val templates: Map<RuntimeAbi, RuntimeTemplate> get() = runtimeTemplates

    companion object {
        const val MAX_BOOTSTRAP_BYTES: Int = 16 * 1024 * 1024
    }
}

class RepackRequest(
    val input: Path,
    val output: Path,
    val inspection: ApkInspection,
    val signerPolicy: SignerPolicyV1,
    val transformedManifest: ManifestTransformResult,
    val container: Path,
    val containerDescriptor: DexContainerDescriptor,
    val runtimeBundle: RuntimeBundle,
    val keyPackagingPlan: KeyPackagingPlanV2,
)

enum class EntryDisposition {
    PRESERVED,
    REPLACED,
    DELETED,
    ADDED,
}

class VerifiedEntry(
    val name: String,
    val method: Int,
    val crc32: Long,
    val uncompressedSize: Long,
    val dataOffset: Long,
    sha256: ByteArray,
    val disposition: EntryDisposition,
) {
    private val digest = requireSha256(sha256)
    val sha256: ByteArray get() = digest.copyOf()
    val sha256Hex: String = digest.toHex()
}

class OutputVerification(
    inputSha256: ByteArray,
    outputSha256: ByteArray,
    manifestSha256: ByteArray,
    containerSha256: ByteArray,
    configSha256: ByteArray,
    entries: List<VerifiedEntry>,
    inputNativeAbis: Set<String>,
    outputEffectiveAbis: Set<RuntimeAbi>,
    val signingPerformed: Boolean,
) {
    private val inputDigest = requireSha256(inputSha256)
    private val outputDigest = requireSha256(outputSha256)
    private val manifestDigest = requireSha256(manifestSha256)
    private val containerDigest = requireSha256(containerSha256)
    private val configDigest = requireSha256(configSha256)

    val inputSha256: ByteArray get() = inputDigest.copyOf()
    val outputSha256: ByteArray get() = outputDigest.copyOf()
    val manifestSha256: ByteArray get() = manifestDigest.copyOf()
    val containerSha256: ByteArray get() = containerDigest.copyOf()
    val configSha256: ByteArray get() = configDigest.copyOf()
    val inputSha256Hex: String = inputDigest.toHex()
    val outputSha256Hex: String = outputDigest.toHex()
    val manifestSha256Hex: String = manifestDigest.toHex()
    val containerSha256Hex: String = containerDigest.toHex()
    val configSha256Hex: String = configDigest.toHex()
    val entries: List<VerifiedEntry> = Collections.unmodifiableList(ArrayList(entries))
    val inputNativeAbis: Set<String> = Collections.unmodifiableSet(LinkedHashSet(inputNativeAbis.sorted()))
    val outputEffectiveAbis: Set<RuntimeAbi> =
        Collections.unmodifiableSet(LinkedHashSet(RuntimeAbi.entries.filter(outputEffectiveAbis::contains)))
}

internal enum class ExpectedContentKind {
    PRESERVED,
    MANIFEST,
    BOOTSTRAP,
    CONTAINER,
    CONFIG,
    RUNTIME,
}

internal class ExpectedEntry(
    val name: String,
    val method: Int,
    val crc32: Long,
    val compressedSize: Long,
    val uncompressedSize: Long,
    val alignment: Int,
    val kind: ExpectedContentKind,
    uncompressedSha256: ByteArray,
    compressedSha256: ByteArray,
    val runtimeAbi: RuntimeAbi? = null,
    val runtimeTemplate: RuntimeTemplate? = null,
    val runtimeSlotOffset: Int? = null,
) {
    private val uncompressedDigest = requireSha256(uncompressedSha256)
    private val compressedDigest = requireSha256(compressedSha256)

    val uncompressedSha256: ByteArray get() = uncompressedDigest.copyOf()
    val compressedSha256: ByteArray get() = compressedDigest.copyOf()
}

class ExpectedOutput internal constructor(
    internal val entries: List<ExpectedEntry>,
    internal val deletedNames: Set<String>,
    internal val inputSha256: ByteArray,
    internal val manifestSha256: ByteArray,
    internal val containerSha256: ByteArray,
    internal val configSha256: ByteArray,
    internal val buildId: ByteArray,
    internal val keySlotId: ByteArray,
    internal val rNative: ByteArray,
    internal val inputNativeAbis: Set<String>,
    internal val outputEffectiveAbis: Set<RuntimeAbi>,
) : AutoCloseable {
    override fun close() {
        inputSha256.fill(0)
        manifestSha256.fill(0)
        containerSha256.fill(0)
        configSha256.fill(0)
        buildId.fill(0)
        keySlotId.fill(0)
        rNative.fill(0)
    }
}

internal fun requireSha256(bytes: ByteArray): ByteArray {
    require(bytes.size == SHA256_BYTES) { "SHA-256 must contain 32 bytes" }
    return bytes.copyOf()
}

internal fun ByteArray.toHex(): String = joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

internal const val SHA256_BYTES: Int = 32
internal const val METHOD_STORED: Int = 0
internal const val METHOD_DEFLATED: Int = 8
internal const val MANIFEST_PATH: String = "AndroidManifest.xml"
internal const val BOOTSTRAP_PATH: String = "classes.dex"
internal const val PAYLOAD_PATH: String = "assets/ah/runtime/payload.ahdc"
internal const val CONFIG_PATH: String = "assets/ah/runtime/config.bin"

internal fun runtimePath(abi: RuntimeAbi): String = "lib/${abi.directoryName}/libah_runtime.so"
