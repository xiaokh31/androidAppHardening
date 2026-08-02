package ah.host.inspector

import java.util.Collections

enum class InspectionErrorCode {
    INPUT_IO,
    INPUT_ZIP_STRUCTURE,
    INPUT_LIMIT_EXCEEDED,
    INPUT_DUPLICATE_ENTRY,
    INPUT_PATH_UNSAFE,
    INPUT_MANIFEST_INVALID,
    INPUT_DEX_INVALID,
    INPUT_CHANGED,
    COMPAT_MIN_SDK,
    COMPAT_SPLIT,
    COMPAT_FRAMEWORK,
    COMPAT_EXISTING_SHELL,
    COMPAT_RESERVED_NAMESPACE,
}

class InspectionException(
    val code: InspectionErrorCode,
    val safeFileName: String,
    val entryIndex: Int? = null,
    val limitName: String? = null,
    markerIds: List<String> = emptyList(),
    cause: Throwable? = null,
) : Exception(buildMessage(code, safeFileName, entryIndex, limitName, markerIds), cause) {
    val markerIds: List<String> = immutableList(markerIds)

    companion object {
        private fun buildMessage(
            code: InspectionErrorCode,
            safeFileName: String,
            entryIndex: Int?,
            limitName: String?,
            markerIds: List<String>,
        ): String = buildString {
            append(code.name)
            append(" file=")
            append(safeFileName)
            entryIndex?.let { append(" entry=").append(it) }
            limitName?.let { append(" limit=").append(it) }
            if (markerIds.isNotEmpty()) {
                append(" markers=")
                append(markerIds.joinToString(","))
            }
        }
    }
}

class ZipEntryRecord(
    val index: Int,
    val name: String,
    originalNameSha256: ByteArray,
    val method: Int,
    val crc32: Long,
    val compressedSize: Long,
    val uncompressedSize: Long,
    val localHeaderOffset: Long,
) {
    private val originalNameSha256Value = originalNameSha256.copyOf()
    val originalNameSha256: ByteArray get() = originalNameSha256Value.copyOf()
}

class ManifestSummary(
    val packageName: String,
    packageNameSha256: ByteArray,
    val minSdk: Int,
    val targetSdk: Int?,
    val applicationClass: String?,
    val appComponentFactoryClass: String?,
    val splitName: String?,
) {
    private val packageNameSha256Value = packageNameSha256.copyOf()
    val packageNameSha256: ByteArray get() = packageNameSha256Value.copyOf()
    val hasAppComponentFactory: Boolean get() = appComponentFactoryClass != null
}

class DexSummary(
    val entryName: String,
    val ordinal: Int,
    val fileSize: Long,
    val classCount: Int,
    sha256: ByteArray,
) {
    private val sha256Value = sha256.copyOf()
    val sha256: ByteArray get() = sha256Value.copyOf()
}

class NativeAbiSummary(abis: List<String>) {
    val abis: List<String> = immutableList(abis)
}

data class CompatibilityFinding(
    val markerId: String,
    val category: String,
)

class LimitsApplied(values: Map<String, Long>) {
    val values: Map<String, Long> = Collections.unmodifiableMap(LinkedHashMap(values))
}

class ApkInspection(
    inputSha256: ByteArray,
    val manifest: ManifestSummary,
    zipEntries: List<ZipEntryRecord>,
    dexEntries: List<DexSummary>,
    val nativeAbis: NativeAbiSummary,
    findings: List<CompatibilityFinding>,
    val limitsApplied: LimitsApplied,
) {
    private val inputSha256Value = inputSha256.copyOf()
    val inputSha256: ByteArray get() = inputSha256Value.copyOf()
    val packageName: String get() = manifest.packageName
    val packageNameSha256: ByteArray get() = manifest.packageNameSha256
    val minSdk: Int get() = manifest.minSdk
    val targetSdk: Int? get() = manifest.targetSdk
    val applicationClass: String? get() = manifest.applicationClass
    val appComponentFactoryClass: String? get() = manifest.appComponentFactoryClass
    val hasAppComponentFactory: Boolean get() = manifest.hasAppComponentFactory
    val zipEntries: List<ZipEntryRecord> = immutableList(zipEntries)
    val dexEntries: List<DexSummary> = immutableList(dexEntries)
    val findings: List<CompatibilityFinding> = immutableList(findings)
}

internal fun <T> immutableList(values: Collection<T>): List<T> =
    Collections.unmodifiableList(ArrayList(values))
