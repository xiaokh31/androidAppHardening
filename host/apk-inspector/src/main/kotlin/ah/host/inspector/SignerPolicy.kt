package ah.host.inspector

import com.android.apksig.ApkVerifier
import com.android.apksig.SigningCertificateLineage
import com.android.apksig.apk.ApkFormatException
import com.android.apksig.util.DataSink
import com.android.apksig.util.DataSource
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest
import java.security.cert.CertificateEncodingException
import java.security.cert.X509Certificate
import java.util.Collections

enum class SignerErrorCode {
    SIGNER_UNSIGNED,
    SIGNER_INVALID,
    SIGNER_MULTIPLE_CURRENT,
    SIGNER_LINEAGE_INVALID,
    SIGNER_INPUT_CHANGED,
    SIGNER_INTERNAL,
}

class SignerPolicyException(
    val code: SignerErrorCode,
    val safeFileName: String,
) : Exception("${code.name} file=$safeFileName")

enum class VerifiedScheme {
    V1,
    V2,
    V3,
    V31,
    V4,
}

class SignerPolicyV1(
    currentCertificateSha256: ByteArray,
    lineageCertificateSha256: List<ByteArray>,
    verifiedSchemes: Set<VerifiedScheme>,
) {
    private val currentDigest = requireDigest(currentCertificateSha256)
    private val lineageDigests = validateLineage(currentDigest, lineageCertificateSha256)

    val policyVersion: Int = POLICY_VERSION
    val currentCertificateSha256: ByteArray get() = currentDigest.copyOf()
    val currentCertificateSha256Hex: String = currentDigest.toLowerHex()
    val lineageCertificateSha256: List<ByteArray>
        get() = Collections.unmodifiableList(lineageDigests.map(ByteArray::copyOf))
    val lineageCertificateSha256Hex: List<String> =
        Collections.unmodifiableList(lineageDigests.map(ByteArray::toLowerHex))
    val verifiedSchemes: Set<VerifiedScheme> =
        Collections.unmodifiableSet(LinkedHashSet(VerifiedScheme.entries.filter(verifiedSchemes::contains)))
    val requiredAfterProtection: Boolean = true
    val performedByProduct: Boolean = false

    companion object {
        const val POLICY_VERSION = 1
        const val DIGEST_BYTES = 32
        const val MAX_LINEAGE_CERTIFICATES = 16

        private fun requireDigest(value: ByteArray): ByteArray {
            require(value.size == DIGEST_BYTES) { "certificate digest must contain 32 bytes" }
            return value.copyOf()
        }

        private fun validateLineage(current: ByteArray, values: List<ByteArray>): List<ByteArray> {
            require(values.isNotEmpty()) { "lineage must contain the current signer" }
            require(values.size <= MAX_LINEAGE_CERTIFICATES) { "lineage exceeds SPV1 limit" }
            val copies = values.map(::requireDigest)
            require(copies.distinctBy(ByteArray::toList).size == copies.size) { "lineage contains duplicate digests" }
            require(MessageDigest.isEqual(copies.last(), current)) { "lineage must end with current signer" }
            return copies
        }
    }
}

data class SignerPolicyReport(
    val inputVerified: Boolean,
    val currentCertificateSha256: String,
    val required: Boolean,
    val performed: Boolean,
)

fun SignerPolicyV1.toReport(): SignerPolicyReport = SignerPolicyReport(
    inputVerified = true,
    currentCertificateSha256 = currentCertificateSha256Hex,
    required = requiredAfterProtection,
    performed = performedByProduct,
)

class SignerPolicyVerifier internal constructor(
    private val afterInitialSnapshot: ((Path) -> Unit)? = null,
    private val beforeFinalHash: ((Path) -> Unit)? = null,
) {
    constructor() : this(null, null)

    fun verify(input: Path, inspection: ApkInspection): SignerPolicyV1 {
        val safeFileName = safeFileName(input)
        try {
            val initialIdentity = pathIdentity(input)
            FileChannel.open(input, StandardOpenOption.READ).use { channel ->
                val source = FileSource(channel)
                if (source.size > InspectionLimits.MAX_APK_BYTES) {
                    throw SignerPolicyException(SignerErrorCode.SIGNER_INVALID, safeFileName)
                }
                val initialDigest = source.captureSnapshot()
                if (!MessageDigest.isEqual(initialDigest, inspection.inputSha256)) {
                    throw SignerPolicyException(SignerErrorCode.SIGNER_INPUT_CHANGED, safeFileName)
                }
                afterInitialSnapshot?.invoke(input)
                val officialResult = verifyWithOfficialLibrary(source, safeFileName)
                beforeFinalHash?.invoke(input)
                val finalDigest = try {
                    source.currentDigest()
                } catch (exception: InputChangedFailure) {
                    throw SignerPolicyException(SignerErrorCode.SIGNER_INPUT_CHANGED, safeFileName)
                }
                val finalIdentity = try {
                    pathIdentity(input)
                } catch (exception: IOException) {
                    throw SignerPolicyException(SignerErrorCode.SIGNER_INPUT_CHANGED, safeFileName)
                }
                if (!MessageDigest.isEqual(initialDigest, finalDigest) || initialIdentity != finalIdentity) {
                    throw SignerPolicyException(SignerErrorCode.SIGNER_INPUT_CHANGED, safeFileName)
                }
                return buildPolicy(officialResult, safeFileName)
            }
        } catch (exception: SignerPolicyException) {
            throw exception
        } catch (exception: InputChangedFailure) {
            throw SignerPolicyException(SignerErrorCode.SIGNER_INPUT_CHANGED, safeFileName)
        } catch (exception: IOException) {
            if (containsInputChanged(exception)) {
                throw SignerPolicyException(SignerErrorCode.SIGNER_INPUT_CHANGED, safeFileName)
            }
            throw SignerPolicyException(SignerErrorCode.SIGNER_INTERNAL, safeFileName)
        } catch (exception: SecurityException) {
            throw SignerPolicyException(SignerErrorCode.SIGNER_INTERNAL, safeFileName)
        } catch (exception: RuntimeException) {
            if (containsInputChanged(exception)) {
                throw SignerPolicyException(SignerErrorCode.SIGNER_INPUT_CHANGED, safeFileName)
            }
            throw SignerPolicyException(SignerErrorCode.SIGNER_INTERNAL, safeFileName)
        }
    }

    private fun verifyWithOfficialLibrary(source: FileSource, safeFileName: String): OfficialResult = try {
        val signingBlockState = inspectApkSigningBlock(source)
        if (signingBlockState == SigningBlockState.MALFORMED ||
            signingBlockState == SigningBlockState.OVERSIZED
        ) {
            throw SignerPolicyException(SignerErrorCode.SIGNER_INVALID, safeFileName)
        }
        val dataSource = VerifiedFileDataSource(source)
        OfficialResult(
            result = ApkVerifier.Builder(dataSource)
                .setMinCheckedPlatformVersion(MIN_CHECKED_PLATFORM)
                .build()
                .verify(),
            hasApkSigningBlockMarker = signingBlockState == SigningBlockState.VALID,
            lineageParseState = parseLineageState(dataSource),
        )
    } catch (exception: IOException) {
        if (containsInputChanged(exception)) {
            throw SignerPolicyException(SignerErrorCode.SIGNER_INPUT_CHANGED, safeFileName)
        }
        throw SignerPolicyException(SignerErrorCode.SIGNER_INVALID, safeFileName)
    }

    private fun parseLineageState(source: DataSource): LineageParseState = try {
        val lineage = SigningCertificateLineage.readFromApkDataSource(source)
        if (lineage.certificatesInLineage.isEmpty()) LineageParseState.INVALID else LineageParseState.VALID
    } catch (_: IllegalArgumentException) {
        // Official apksig uses this result when the APK has no proof-of-rotation attribute.
        LineageParseState.ABSENT
    } catch (exception: SecurityException) {
        if (containsInputChanged(exception)) throw exception
        // This call only parses and authenticates proof-of-rotation. A security failure is
        // therefore specific evidence of an invalid lineage, not a generic APK failure.
        LineageParseState.INVALID
    } catch (exception: IOException) {
        if (containsInputChanged(exception)) throw exception
        LineageParseState.INDETERMINATE
    } catch (exception: ApkFormatException) {
        if (containsInputChanged(exception)) throw exception
        LineageParseState.INDETERMINATE
    }

    private fun buildPolicy(official: OfficialResult, safeFileName: String): SignerPolicyV1 {
        val result = official.result
        val issueNames = result.allErrors.map { it.issue.name }.toSet()
        if (!result.isVerified) {
            val code = when {
                result.signerCertificates.size > 1 || issueNames.any(MULTIPLE_SIGNER_ISSUES::contains) ->
                    SignerErrorCode.SIGNER_MULTIPLE_CURRENT
                official.lineageParseState == LineageParseState.INVALID || issueNames.any(::isLineageIssue) ->
                    SignerErrorCode.SIGNER_LINEAGE_INVALID
                !official.hasApkSigningBlockMarker &&
                    issueNames.any(UNSIGNED_ISSUES::contains) &&
                    issueNames.none(::isDefinitivelyInvalidIssue) ->
                    SignerErrorCode.SIGNER_UNSIGNED
                else -> SignerErrorCode.SIGNER_INVALID
            }
            throw SignerPolicyException(code, safeFileName)
        }
        if (result.signerCertificates.size != 1) {
            throw SignerPolicyException(
                if (result.signerCertificates.size > 1) {
                    SignerErrorCode.SIGNER_MULTIPLE_CURRENT
                } else {
                    SignerErrorCode.SIGNER_INVALID
                },
                safeFileName,
            )
        }
        val current = certificateDigest(result.signerCertificates.single(), safeFileName)
        val lineageCertificates = result.signingCertificateLineage?.certificatesInLineage
            ?: listOf(result.signerCertificates.single())
        val lineage = lineageCertificates.map { certificateDigest(it, safeFileName) }
        val schemes = buildSet {
            if (result.isVerifiedUsingV1Scheme) add(VerifiedScheme.V1)
            if (result.isVerifiedUsingV2Scheme) add(VerifiedScheme.V2)
            if (result.isVerifiedUsingV3Scheme) add(VerifiedScheme.V3)
            if (result.isVerifiedUsingV31Scheme) add(VerifiedScheme.V31)
            if (result.isVerifiedUsingV4Scheme) add(VerifiedScheme.V4)
        }
        return try {
            SignerPolicyV1(current, lineage, schemes)
        } catch (exception: IllegalArgumentException) {
            throw SignerPolicyException(SignerErrorCode.SIGNER_LINEAGE_INVALID, safeFileName)
        }
    }

    private fun certificateDigest(certificate: X509Certificate, safeFileName: String): ByteArray = try {
        MessageDigest.getInstance("SHA-256").digest(certificate.encoded)
    } catch (exception: CertificateEncodingException) {
        throw SignerPolicyException(SignerErrorCode.SIGNER_INVALID, safeFileName)
    }

    private fun inspectApkSigningBlock(source: FileSource): SigningBlockState {
        if (source.size < EOCD_MIN_BYTES + APK_SIGNING_BLOCK_FOOTER_BYTES) return SigningBlockState.ABSENT
        val tailSize = minOf(source.size, EOCD_MAX_SEARCH_BYTES.toLong()).toInt()
        val tailOffset = source.size - tailSize
        val tail = source.readFully(tailOffset, tailSize)
        var eocd = tail.size - EOCD_MIN_BYTES
        while (eocd >= 0) {
            if (leU4(tail, eocd) == EOCD_SIGNATURE) {
                val commentLength = leU2(tail, eocd + 20)
                if (eocd + EOCD_MIN_BYTES + commentLength == tail.size) {
                    val centralOffset = leU4(tail, eocd + 16)
                    if (centralOffset < APK_SIGNING_BLOCK_FOOTER_BYTES || centralOffset > source.size) {
                        return SigningBlockState.ABSENT
                    }
                    val footer = source.readFully(
                        centralOffset - APK_SIGNING_BLOCK_FOOTER_BYTES,
                        APK_SIGNING_BLOCK_FOOTER_BYTES,
                    )
                    if (!footer.copyOfRange(Long.SIZE_BYTES, footer.size).contentEquals(APK_SIGNING_BLOCK_MAGIC)) {
                        return SigningBlockState.ABSENT
                    }
                    val declaredSize = leI8(footer, 0)
                    if (declaredSize < APK_SIGNING_BLOCK_MIN_SIZE_FIELD) return SigningBlockState.ABSENT
                    val totalSize = declaredSize + Long.SIZE_BYTES
                    if (totalSize <= 0 || totalSize > centralOffset) return SigningBlockState.MALFORMED
                    val leadingSize = leI8(source.readFully(centralOffset - totalSize, Long.SIZE_BYTES), 0)
                    if (leadingSize != declaredSize) return SigningBlockState.MALFORMED
                    return if (totalSize > MAX_APKSIG_SIGNING_BLOCK_BYTES) {
                        SigningBlockState.OVERSIZED
                    } else {
                        SigningBlockState.VALID
                    }
                }
            }
            eocd--
        }
        return SigningBlockState.ABSENT
    }

    private fun pathIdentity(input: Path): PathIdentity {
        val attributes = Files.readAttributes(input, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
        if (!attributes.isRegularFile) throw IOException("input is not a regular file")
        return PathIdentity(attributes.fileKey()?.toString(), attributes.size(), attributes.lastModifiedTime().toMillis())
    }

    private fun safeFileName(input: Path): String {
        val value = input.fileName?.toString().orEmpty().take(MAX_SAFE_FILE_NAME)
        return value.map { if (it.isISOControl()) '_' else it }.joinToString("").ifEmpty { "input.apk" }
    }

    private fun containsInputChanged(throwable: Throwable): Boolean {
        var current: Throwable? = throwable
        while (current != null) {
            if (current is InputChangedFailure) return true
            current = current.cause
        }
        return false
    }

    private fun isLineageIssue(name: String): Boolean = name in LINEAGE_ISSUES

    private fun isDefinitivelyInvalidIssue(name: String): Boolean =
        name !in UNSIGNED_ISSUES && name != "V2_SIG_MISSING" && name != "MIN_SIG_SCHEME_FOR_TARGET_SDK_NOT_MET"

    private data class PathIdentity(val fileKey: String?, val size: Long, val modifiedMillis: Long)
    private data class OfficialResult(
        val result: ApkVerifier.Result,
        val hasApkSigningBlockMarker: Boolean,
        val lineageParseState: LineageParseState,
    )

    private enum class LineageParseState { ABSENT, VALID, INVALID, INDETERMINATE }
    private enum class SigningBlockState { ABSENT, VALID, MALFORMED, OVERSIZED }

    companion object {
        private const val MIN_CHECKED_PLATFORM = 29
        private const val MAX_SAFE_FILE_NAME = 128
        private const val EOCD_MIN_BYTES = 22
        private const val EOCD_MAX_SEARCH_BYTES = EOCD_MIN_BYTES + 0xffff
        private const val EOCD_SIGNATURE = 0x06054b50L
        private const val APK_SIGNING_BLOCK_FOOTER_BYTES = Long.SIZE_BYTES + 16
        private const val APK_SIGNING_BLOCK_MIN_SIZE_FIELD = 24L
        internal const val MAX_APKSIG_SIGNING_BLOCK_BYTES = 32L * 1024 * 1024
        private val APK_SIGNING_BLOCK_MAGIC = "APK Sig Block 42".toByteArray(Charsets.US_ASCII)
        private val UNSIGNED_ISSUES = setOf(
            "JAR_SIG_NO_SIGNATURES",
            "JAR_SIG_NO_SIGNERS",
            "JAR_SIG_NO_MANIFEST",
            "JAR_SIG_MISSING",
            "V2_SIG_MISSING",
            "MIN_SIG_SCHEME_FOR_TARGET_SDK_NOT_MET",
        )
        private val MULTIPLE_SIGNER_ISSUES = setOf(
            "V3_SIG_MULTIPLE_SIGNERS",
            "V4_SIG_MULTIPLE_SIGNERS",
        )
        private val LINEAGE_ISSUES = setOf(
            "V3_SIG_MULTIPLE_PAST_SIGNERS",
            "V3_SIG_PAST_SIGNERS_MISMATCH",
            "V3_SIG_POR_DID_NOT_VERIFY",
            "V3_SIG_MALFORMED_LINEAGE",
            "V3_SIG_POR_CERT_MISMATCH",
            "V3_INCONSISTENT_LINEAGES",
            "V31_ROTATION_MIN_SDK_MISMATCH",
            "V31_ROTATION_MIN_SDK_ATTR_MISSING",
            "V31_ROTATION_TARGETS_DEV_RELEASE_ATTR_ON_V3_SIGNER",
        )
    }
}

private class VerifiedFileDataSource(
    private val source: FileSource,
    private val baseOffset: Long = 0,
    private val length: Long = source.size,
) : DataSource {
    init {
        requireRange(0, length)
    }

    override fun size(): Long = length

    override fun feed(offset: Long, size: Long, sink: DataSink) {
        requireRange(offset, size)
        var position = offset
        var remaining = size
        while (remaining > 0) {
            val count = minOf(remaining, FEED_BLOCK_BYTES.toLong()).toInt()
            sink.consume(source.readFully(baseOffset + position, count), 0, count)
            position += count
            remaining -= count
        }
    }

    override fun getByteBuffer(offset: Long, size: Int): ByteBuffer {
        requireRange(offset, size.toLong())
        requireMaterializationLimit(size)
        return ByteBuffer.wrap(source.readFully(baseOffset + offset, size)).asReadOnlyBuffer()
    }

    override fun copyTo(offset: Long, size: Int, destination: ByteBuffer) {
        requireRange(offset, size.toLong())
        requireMaterializationLimit(size)
        if (destination.remaining() < size) throw IOException("destination buffer too small")
        destination.put(source.readFully(baseOffset + offset, size))
    }

    override fun slice(offset: Long, size: Long): DataSource {
        requireRange(offset, size)
        return VerifiedFileDataSource(source, baseOffset + offset, size)
    }

    private fun requireRange(offset: Long, size: Long) {
        if (offset < 0 || size < 0 || offset > length - size) throw IndexOutOfBoundsException("data source range")
    }

    private fun requireMaterializationLimit(size: Int) {
        if (size > SignerPolicyVerifier.MAX_APKSIG_SIGNING_BLOCK_BYTES) {
            throw IOException("apksig contiguous read exceeds limit")
        }
    }

    companion object {
        private const val FEED_BLOCK_BYTES = 64 * 1024
    }
}

private fun leI8(bytes: ByteArray, offset: Int): Long {
    if (offset < 0 || offset > bytes.size - Long.SIZE_BYTES) throw IndexOutOfBoundsException("little-endian u8")
    var value = 0L
    for (index in Long.SIZE_BYTES - 1 downTo 0) {
        value = (value shl Byte.SIZE_BITS) or (bytes[offset + index].toLong() and 0xff)
    }
    return value
}

private fun ByteArray.toLowerHex(): String = joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
