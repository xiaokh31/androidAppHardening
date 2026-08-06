package ah.host.cli

import java.nio.file.Path
import java.time.Instant

internal const val TOOL_NAME = "android-app-hardening"
internal const val TOOL_VERSION = "0.1.0-dev"
internal const val REPORT_SCHEMA_VERSION = 1

internal enum class PipelineStage(val wireName: String) {
    INSPECT("inspect"),
    SIGNER("signer"),
    MANIFEST("manifest"),
    CONTAINER("container"),
    PACKAGE("package"),
    VERIFY("verify"),
    PUBLISH("publish"),
}

internal enum class ResultStatus(val wireName: String) {
    SUCCESS("success"),
    REJECTED("rejected"),
    FAILED("failed"),
}

internal data class StageRecord(
    val id: PipelineStage,
    val status: String,
    val durationMillis: Long,
)

internal data class ReportError(
    val code: String,
    val stage: PipelineStage,
    val messageId: String,
)

internal data class CliFailure(
    val exitCode: Int,
    val errorCode: String,
    val stage: PipelineStage,
    val messageId: String,
    val status: ResultStatus,
) : RuntimeException(errorCode)

internal data class ProtectArguments(
    val input: Path,
    val output: Path,
    val report: Path,
)

internal sealed interface ParsedCommand {
    data object Help : ParsedCommand
    data object Version : ParsedCommand
    data class Protect(val arguments: ProtectArguments) : ParsedCommand
}

internal data class InvocationPaths(
    val input: Path,
    val output: Path,
    val report: Path,
    val inputReal: Path,
    val outputResolved: Path,
    val reportResolved: Path,
    val inputBasename: String,
    val outputBasename: String,
    val reportBasename: String,
    val inputPathToken: String,
    val outputPathToken: String,
    val reportPathToken: String,
)

internal data class CliExecution(
    val exitCode: Int,
    val status: ResultStatus,
    val errorCode: String?,
    val reportBasename: String,
)

internal fun interface MillisClock {
    fun now(): Long
}

internal fun interface InstantClock {
    fun now(): Instant
}

internal fun interface CancellationProbe {
    fun cancelled(): Boolean
}

internal object SystemCancellationProbe : CancellationProbe {
    override fun cancelled(): Boolean = Thread.currentThread().isInterrupted
}

internal interface ShutdownHooks {
    fun add(hook: Thread)
    fun remove(hook: Thread)
}

internal object SystemShutdownHooks : ShutdownHooks {
    override fun add(hook: Thread) = Runtime.getRuntime().addShutdownHook(hook)
    override fun remove(hook: Thread) {
        Runtime.getRuntime().removeShutdownHook(hook)
    }
}
