package ah.host.axml

import ah.host.inspector.ManifestSummary
import java.util.Collections

enum class AxmlErrorCode {
    AXML_MALFORMED,
    AXML_LIMIT_EXCEEDED,
    AXML_APPLICATION_MISSING,
    AXML_RESERVED_COLLISION,
    AXML_UNSUPPORTED_ENCODING,
    AXML_DIFF_VIOLATION,
}

class AxmlTransformException(
    val code: AxmlErrorCode,
    val chunkOffset: Int? = null,
    val chunkType: Int? = null,
) : Exception(buildMessage(code, chunkOffset, chunkType)) {
    companion object {
        private fun buildMessage(code: AxmlErrorCode, chunkOffset: Int?, chunkType: Int?): String = buildString {
            append(code.name)
            chunkOffset?.let { append(" offset=").append(it) }
            chunkType?.let { append(" chunk=0x").append(it.toString(16).padStart(4, '0')) }
        }
    }
}

class ManifestTransformRequest(val manifestSummary: ManifestSummary) {
    val shellFactory: String get() = SHELL_FACTORY

    companion object {
        const val SHELL_FACTORY: String = "ah.runtime.bootstrap.ShellAppComponentFactory"
    }
}

data class ManifestAttributeChange(
    val elementPath: String,
    val namespaceUri: String,
    val attributeName: String,
    val beforeValue: String?,
    val afterValue: String,
)

class ManifestSemanticDiff(changes: List<ManifestAttributeChange>) {
    val changes: List<ManifestAttributeChange> = Collections.unmodifiableList(ArrayList(changes))
}

class ManifestTransformResult(
    bytes: ByteArray,
    beforeSha256: ByteArray,
    afterSha256: ByteArray,
    val semanticDiff: ManifestSemanticDiff,
) {
    private val bytesValue = bytes.copyOf()
    private val beforeSha256Value = beforeSha256.copyOf()
    private val afterSha256Value = afterSha256.copyOf()

    val bytes: ByteArray get() = bytesValue.copyOf()
    val beforeSha256: ByteArray get() = beforeSha256Value.copyOf()
    val afterSha256: ByteArray get() = afterSha256Value.copyOf()
}
