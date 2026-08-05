package ah.host.container

import ah.host.inspector.ApkInspection
import ah.host.inspector.DexSummary
import ah.host.inspector.SignerPolicyV1
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean

enum class ContainerErrorCode {
    CONTAINER_FORMAT,
    CONTAINER_VERSION,
    CONTAINER_LIMIT_EXCEEDED,
    CONTAINER_INPUT_CHANGED,
    CONTAINER_CRYPTO,
    CONTAINER_AUTH_FAILED,
    CONTAINER_KEY_MATERIAL,
    CONTAINER_RANDOM_FAILED,
}

class ContainerException(
    val code: ContainerErrorCode,
    val field: String? = null,
    cause: Throwable? = null,
) : Exception(if (field == null) code.name else "${code.name} field=$field", cause)

class DexRecordDescriptor(
    val ordinal: Int,
    val name: String,
    val originalLength: Long,
    val compressedLength: Long,
    val chunkCount: Int,
    val firstChunkIndex: Int,
    val payloadOffset: Long,
    originalSha256: ByteArray,
) {
    private val originalDigest = requireDigest(originalSha256)
    val originalSha256: ByteArray get() = originalDigest.copyOf()
    val originalSha256Hex: String = originalDigest.toHex()
}

class DexContainerDescriptor(
    val major: Int,
    val minor: Int,
    val packageName: String,
    currentSignerSha256: ByteArray,
    signerLineageSha256: List<ByteArray>,
    records: List<DexRecordDescriptor>,
    containerSha256: ByteArray,
) {
    private val currentSignerDigest = requireDigest(currentSignerSha256)
    private val lineageDigests = signerLineageSha256.map(::requireDigest)
    private val containerDigest = requireDigest(containerSha256)

    val currentSignerSha256: ByteArray get() = currentSignerDigest.copyOf()
    val currentSignerSha256Hex: String = currentSignerDigest.toHex()
    val signerLineageSha256: List<ByteArray>
        get() = Collections.unmodifiableList(lineageDigests.map(ByteArray::copyOf))
    val signerLineageSha256Hex: List<String> =
        Collections.unmodifiableList(lineageDigests.map(ByteArray::toHex))
    val records: List<DexRecordDescriptor> = Collections.unmodifiableList(ArrayList(records))
    val containerSha256: ByteArray get() = containerDigest.copyOf()
    val containerSha256Hex: String = containerDigest.toHex()
}

class ContainerBuildResult(
    val descriptor: DexContainerDescriptor,
    val keyPackagingPlan: KeyPackagingPlanV2,
)

enum class RuntimeAbi(val directoryName: String, val abiId: Int) {
    ARMEABI_V7A("armeabi-v7a", 1),
    ARM64_V8A("arm64-v8a", 2),
    X86("x86", 3),
    X86_64("x86_64", 4),
}

class KeyPackagingMaterialV2 internal constructor(
    private val config: ByteArray,
    private val nativeShare: ByteArray,
    private val build: ByteArray,
    private val slot: ByteArray,
    targetAbis: Set<RuntimeAbi>,
) {
    val targetAbis: Set<RuntimeAbi> = Collections.unmodifiableSet(LinkedHashSet(targetAbis))

    fun configV2(): ByteBuffer = ByteBuffer.wrap(config).asReadOnlyBuffer()
    fun rNative(): ByteBuffer = ByteBuffer.wrap(nativeShare).asReadOnlyBuffer()
    fun buildId(): ByteBuffer = ByteBuffer.wrap(build).asReadOnlyBuffer()
    fun keySlotId(): ByteBuffer = ByteBuffer.wrap(slot).asReadOnlyBuffer()

    fun expectedBinding(inspection: ApkInspection, signer: SignerPolicyV1): ExpectedBinding =
        ExpectedBinding.from(inspection, signer, config, nativeShare, NO_CONTAINER_OBSERVER)

    internal fun clear(observer: ContainerObserver) {
        wipe("plan.config", config, observer)
        wipe("plan.rNative", nativeShare, observer)
        wipe("plan.buildId", build, observer)
        wipe("plan.keySlotId", slot, observer)
    }
}

class KeyPackagingPlanV2 internal constructor(
    configV2: ByteArray,
    rNative: ByteArray,
    buildId: ByteArray,
    keySlotId: ByteArray,
    targetAbis: Set<RuntimeAbi>,
    observer: ContainerObserver,
) : AutoCloseable {
    private val consumed = AtomicBoolean(false)
    private val observer = cleanupTrackingObserver(observer)
    private val material = KeyPackagingMaterialV2(
        configV2.copyOf(),
        rNative.copyOf(),
        buildId.copyOf(),
        keySlotId.copyOf(),
        targetAbis,
    )

    fun <T> consume(action: (KeyPackagingMaterialV2) -> T): T {
        if (!consumed.compareAndSet(false, true)) {
            throw ContainerException(ContainerErrorCode.CONTAINER_KEY_MATERIAL, "planConsumed")
        }
        var primaryFailure: Throwable? = null
        return try {
            action(material)
        } catch (failure: Throwable) {
            primaryFailure = failure
            throw failure
        } finally {
            material.clear(observer)
            observer.finish(primaryFailure)
        }
    }

    override fun close() {
        if (consumed.compareAndSet(false, true)) {
            material.clear(observer)
            observer.finish(null)
        }
    }
}

class ExpectedBinding internal constructor(
    val packageName: String,
    packageNameSha256: ByteArray,
    currentSignerSha256: ByteArray,
    signerLineageSha256: List<ByteArray>,
    expectedDex: List<DexSummary>,
    configV2: ByteArray,
    rNative: ByteArray,
    observer: ContainerObserver,
) : AutoCloseable {
    private val observer = cleanupTrackingObserver(observer)
    private val packageDigest = requireDigest(packageNameSha256)
    private val signerDigest = requireDigest(currentSignerSha256)
    private val lineageDigests = signerLineageSha256.map(::requireDigest)
    private val dex = ArrayList(expectedDex)
    private val config = configV2.copyOf()
    private val nativeShare = rNative.copyOf()
    private val closed = AtomicBoolean(false)

    internal fun packageDigest(): ByteArray = packageDigest.copyOf()
    internal fun signerDigest(): ByteArray = signerDigest.copyOf()
    internal fun lineageDigests(): List<ByteArray> = lineageDigests.map(ByteArray::copyOf)
    internal fun expectedDex(): List<DexSummary> = ArrayList(dex)
    internal fun config(): ByteArray = config.copyOf()
    internal fun nativeShare(): ByteArray = nativeShare.copyOf()

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            wipe("binding.package", packageDigest, observer)
            wipe("binding.signer", signerDigest, observer)
            lineageDigests.forEachIndexed { index, bytes -> wipe("binding.lineage.$index", bytes, observer) }
            wipe("binding.config", config, observer)
            wipe("binding.rNative", nativeShare, observer)
            observer.finish(null)
        }
    }

    companion object {
        internal fun from(
            inspection: ApkInspection,
            signer: SignerPolicyV1,
            configV2: ByteArray,
            rNative: ByteArray,
            observer: ContainerObserver,
        ): ExpectedBinding = ExpectedBinding(
            inspection.packageName,
            inspection.packageNameSha256,
            signer.currentCertificateSha256,
            signer.lineageCertificateSha256,
            inspection.dexEntries,
            configV2,
            rNative,
            observer,
        )
    }
}

internal interface ContainerObserver {
    fun cleared(label: String, allZero: Boolean) = Unit
    fun allocated(label: String, bytes: Int) = Unit
    fun authenticatedBeforeInflate(record: Int, chunk: Int) = Unit
}

internal object NO_CONTAINER_OBSERVER : ContainerObserver

internal class CleanupTrackingObserver(private val delegate: ContainerObserver) : ContainerObserver {
    private val failures = ArrayList<Throwable>()

    override fun cleared(label: String, allZero: Boolean) {
        try {
            delegate.cleared(label, allZero)
        } catch (failure: Throwable) {
            failures += failure
        }
    }

    override fun allocated(label: String, bytes: Int) = delegate.allocated(label, bytes)

    override fun authenticatedBeforeInflate(record: Int, chunk: Int) =
        delegate.authenticatedBeforeInflate(record, chunk)

    fun finish(primaryFailure: Throwable?) {
        if (failures.isEmpty()) return
        val first = failures.removeAt(0)
        failures.forEach { failure -> suppressCleanup(first, failure) }
        failures.clear()
        if (primaryFailure != null) {
            suppressCleanup(primaryFailure, first)
        } else {
            throw ContainerException(ContainerErrorCode.CONTAINER_KEY_MATERIAL, "cleanupObserver", first)
        }
    }
}

internal fun cleanupTrackingObserver(observer: ContainerObserver): CleanupTrackingObserver =
    observer as? CleanupTrackingObserver ?: CleanupTrackingObserver(observer)

internal fun suppressCleanup(primary: Throwable, cleanup: Throwable) {
    try {
        primary.addSuppressed(cleanup)
    } catch (_: Throwable) {
        // Cleanup diagnostics are best-effort and never replace the primary failure.
    }
}

internal fun wipe(label: String, bytes: ByteArray, observer: ContainerObserver) {
    bytes.fill(0)
    observer.cleared(label, bytes.all { it == 0.toByte() })
}

internal fun requireDigest(bytes: ByteArray): ByteArray {
    if (bytes.size != AhConstants.SHA256_BYTES) {
        throw ContainerException(ContainerErrorCode.CONTAINER_FORMAT, "sha256")
    }
    return bytes.copyOf()
}

internal fun ByteArray.constantTimeEquals(other: ByteArray): Boolean = MessageDigest.isEqual(this, other)

internal fun ByteArray.toHex(): String = joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

internal fun ByteBuffer.copyRemaining(): ByteArray {
    val duplicate = asReadOnlyBuffer()
    val bytes = ByteArray(duplicate.remaining())
    duplicate.get(bytes)
    return bytes
}

internal fun interface ContainerRandom {
    fun bytes(label: String, size: Int): ByteArray
}

internal class SecureContainerRandom private constructor(private val random: SecureRandom) : ContainerRandom {

    override fun bytes(label: String, size: Int): ByteArray = try {
        if (label.isEmpty() || size <= 0) format("randomRequest")
        ByteArray(size).also(random::nextBytes)
    } catch (exception: RuntimeException) {
        throw ContainerException(ContainerErrorCode.CONTAINER_RANDOM_FAILED, "random", exception)
    }

    companion object {
        fun create(): SecureContainerRandom = try {
            SecureContainerRandom(SecureRandom())
        } catch (failure: RuntimeException) {
            throw ContainerException(ContainerErrorCode.CONTAINER_RANDOM_FAILED, "randomInit", failure)
        }
    }
}
