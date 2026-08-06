package ah.host.cli

import java.io.PrintStream
import kotlin.system.exitProcess

object CliMain {
    @JvmStatic
    fun main(args: Array<String>) {
        exitProcess(run(args, System.out, System.err, ClasspathRuntimeBundleProvider))
    }

    internal fun run(
        args: Array<String>,
        stdout: PrintStream,
        stderr: PrintStream,
        runtimeBundleProvider: RuntimeBundleProvider,
        pipelineFactory: (RuntimeBundleProvider) -> ProtectionPipeline = ::ProtectionPipeline,
    ): Int {
        val command = try {
            CliParser.parse(args)
        } catch (failure: CliFailure) {
            stderr.println("${failure.status.wireName}/${failure.errorCode}/-")
            return failure.exitCode
        }
        return when (command) {
            ParsedCommand.Help -> {
                stdout.print(HELP)
                0
            }
            ParsedCommand.Version -> {
                stdout.println("$TOOL_NAME $TOOL_VERSION")
                0
            }
            is ParsedCommand.Protect -> {
                val execution = pipelineFactory(runtimeBundleProvider).execute(command.arguments)
                stderr.println("${execution.status.wireName}/${execution.errorCode ?: "NONE"}/${execution.reportBasename}")
                execution.exitCode
            }
        }
    }

    private val HELP = """
        Usage:
          android-app-hardening protect --input <apk> --output <unsigned-apk> --report <json>
          android-app-hardening --help
          android-app-hardening --version

        The input is read-only. The new output remains unsigned and must be signed outside this tool.
    """.trimIndent() + "\n"
}
