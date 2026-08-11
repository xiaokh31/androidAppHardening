package ah.host.cli

import ah.host.container.DexContainerDescriptor
import ah.host.inspector.ApkInspection
import ah.host.inspector.SignerPolicyV1
import ah.host.container.RuntimeAbi
import ah.host.repacker.OutputVerification
import java.nio.charset.StandardCharsets
import java.time.Instant

internal data class ReportSnapshot(
    val status: ResultStatus,
    val errorCode: String?,
    val startedAt: Instant,
    val finishedAt: Instant,
    val paths: InvocationPaths,
    val inputSha256: String?,
    val inspection: ApkInspection?,
    val signer: SignerPolicyV1?,
    val descriptor: DexContainerDescriptor?,
    val verification: OutputVerification?,
    val stages: List<StageRecord>,
    val errors: List<ReportError>,
    val inputBytes: Long?,
    val outputBytes: Long?,
)

internal object ReportV1Writer {
    fun write(snapshot: ReportSnapshot): ByteArray {
        val inspection = snapshot.inspection
        val signer = snapshot.signer
        val descriptor = snapshot.descriptor
        val verification = snapshot.verification
        val root = linkedMapOf<String, Any?>(
            "schema_version" to REPORT_SCHEMA_VERSION,
            "tool" to linkedMapOf(
                "name" to TOOL_NAME,
                "version" to TOOL_VERSION,
            ),
            "result" to linkedMapOf(
                "status" to snapshot.status.wireName,
                "error_code" to snapshot.errorCode,
                "started_at" to snapshot.startedAt.toString(),
                "finished_at" to snapshot.finishedAt.toString(),
            ),
            "input" to linkedMapOf(
                "basename" to snapshot.paths.inputBasename,
                "path_token" to snapshot.paths.inputPathToken,
                "sha256" to snapshot.inputSha256,
            ),
            "output" to linkedMapOf(
                "basename" to snapshot.paths.outputBasename,
                "path_token" to snapshot.paths.outputPathToken,
                "report_basename" to snapshot.paths.reportBasename,
                "report_path_token" to snapshot.paths.reportPathToken,
                "sha256" to verification?.outputSha256Hex,
                "manifest_sha256" to verification?.manifestSha256Hex,
                "container_sha256" to verification?.containerSha256Hex,
                "config_sha256" to verification?.configSha256Hex,
            ),
            "application" to linkedMapOf(
                "package_name" to inspection?.packageName,
                "min_sdk" to inspection?.minSdk,
                "target_sdk" to inspection?.targetSdk,
                "application_class" to inspection?.applicationClass,
                "original_factory" to inspection?.appComponentFactoryClass,
                "shell_factory" to inspection?.let { ah.host.axml.ManifestTransformRequest.SHELL_FACTORY },
            ),
            "signing" to linkedMapOf(
                "input_verified" to (signer != null),
                "current_certificate_sha256" to signer?.currentCertificateSha256Hex,
                "lineage_certificate_sha256" to (signer?.lineageCertificateSha256Hex ?: emptyList<String>()),
                "verified_schemes" to (signer?.verifiedSchemes?.map { it.name } ?: emptyList<String>()),
                "required" to true,
                "performed" to false,
            ),
            "dex" to linkedMapOf(
                "count" to (inspection?.dexEntries?.size ?: 0),
                "entries" to (inspection?.dexEntries?.map { entry ->
                    linkedMapOf(
                        "name" to entry.entryName,
                        "ordinal" to entry.ordinal,
                        "bytes" to entry.fileSize,
                        "sha256" to entry.sha256.toHex(),
                    )
                } ?: emptyList<Map<String, Any?>>()),
                "container_major" to descriptor?.major,
                "container_minor" to descriptor?.minor,
            ),
            "abi" to linkedMapOf(
                "runtime_available_abis" to RuntimeAbi.entries.map { it.directoryName },
                "input_native_abis" to (inspection?.nativeAbis?.abis ?: emptyList<String>()),
                "output_effective_abis" to
                    (verification?.outputEffectiveAbis?.map { it.directoryName } ?: emptyList<String>()),
                "limitations" to abiLimitations(inspection),
            ),
            "compatibility" to linkedMapOf(
                "supported" to (inspection != null),
                "rules_version" to inspection?.compatibilityRulesVersion,
                "findings" to (inspection?.findings?.map { finding ->
                    linkedMapOf("marker_id" to finding.markerId, "category" to finding.category)
                } ?: emptyList<Map<String, String>>()),
            ),
            "stages" to snapshot.stages.map { stage ->
                linkedMapOf(
                    "id" to stage.id.wireName,
                    "status" to stage.status,
                    "duration_ms" to stage.durationMillis,
                )
            },
            "size" to linkedMapOf(
                "input_bytes" to snapshot.inputBytes,
                "output_bytes" to snapshot.outputBytes,
                "delta_bytes" to if (snapshot.inputBytes != null && snapshot.outputBytes != null) {
                    snapshot.outputBytes - snapshot.inputBytes
                } else null,
            ),
            "errors" to snapshot.errors.map { error ->
                linkedMapOf(
                    "code" to error.code,
                    "stage" to error.stage.wireName,
                    "message_id" to error.messageId,
                )
            },
        )
        return (JsonEncoder.encode(root) + "\n").toByteArray(StandardCharsets.UTF_8)
    }

    private fun abiLimitations(inspection: ApkInspection?): List<String> {
        val input = inspection?.nativeAbis?.abis ?: return emptyList()
        val available = RuntimeAbi.entries.map { it.directoryName }.toSet()
        return if (input.isNotEmpty() && input.toSet() != available) {
            listOf("OUTPUT_LIMITED_TO_INPUT_NATIVE_ABIS")
        } else {
            emptyList()
        }
    }

    private fun ByteArray.toHex(): String = joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
}

internal object JsonEncoder {
    fun encode(value: Any?): String = buildString { appendValue(value, 0) }

    private fun StringBuilder.appendValue(value: Any?, depth: Int) {
        when (value) {
            null -> append("null")
            is String -> appendString(value)
            is Boolean, is Byte, is Short, is Int, is Long -> append(value)
            is Map<*, *> -> appendObject(value, depth)
            is Iterable<*> -> appendArray(value, depth)
            else -> error("unsupported JSON type")
        }
    }

    private fun StringBuilder.appendObject(value: Map<*, *>, depth: Int) {
        append('{')
        if (value.isNotEmpty()) append('\n')
        value.entries.forEachIndexed { index, entry ->
            indent(depth + 1)
            appendString(entry.key as String)
            append(": ")
            appendValue(entry.value, depth + 1)
            if (index != value.size - 1) append(',')
            append('\n')
        }
        if (value.isNotEmpty()) indent(depth)
        append('}')
    }

    private fun StringBuilder.appendArray(value: Iterable<*>, depth: Int) {
        val values = value.toList()
        append('[')
        if (values.isNotEmpty()) append('\n')
        values.forEachIndexed { index, item ->
            indent(depth + 1)
            appendValue(item, depth + 1)
            if (index != values.size - 1) append(',')
            append('\n')
        }
        if (values.isNotEmpty()) indent(depth)
        append(']')
    }

    private fun StringBuilder.appendString(value: String) {
        append('"')
        value.forEach { character ->
            when (character) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000c' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (character.code < 0x20) append("\\u%04x".format(character.code)) else append(character)
            }
        }
        append('"')
    }

    private fun StringBuilder.indent(depth: Int) = repeat(depth) { append("  ") }
}
