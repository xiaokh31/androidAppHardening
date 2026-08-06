package ah.host.cli

import java.nio.file.InvalidPathException
import java.nio.file.Path

internal object CliParser {
    private val forbiddenFragments = listOf("key", "keystore", "alias", "password", "sign", "token", "network", "plugin", "temp")

    fun parse(args: Array<String>): ParsedCommand {
        if (args.contentEquals(arrayOf("--help"))) return ParsedCommand.Help
        if (args.contentEquals(arrayOf("--version"))) return ParsedCommand.Version
        if (args.isEmpty() || args[0] != "protect") usage()
        val values = LinkedHashMap<String, String>()
        var index = 1
        while (index < args.size) {
            val option = args[index]
            if (option in values || option !in REQUIRED_OPTIONS || index + 1 >= args.size) usage()
            if (forbiddenFragments.any { fragment -> option.lowercase().contains(fragment) }) usage()
            values[option] = args[index + 1]
            index += 2
        }
        if (values.keys != REQUIRED_OPTIONS) usage()
        return try {
            ParsedCommand.Protect(
                ProtectArguments(
                    Path.of(values.getValue("--input")),
                    Path.of(values.getValue("--output")),
                    Path.of(values.getValue("--report")),
                ),
            )
        } catch (_: InvalidPathException) {
            usage()
        }
    }

    private fun usage(): Nothing = throw CliFailure(
        exitCode = 2,
        errorCode = "USAGE_INVALID",
        stage = PipelineStage.INSPECT,
        messageId = "usage.invalid",
        status = ResultStatus.REJECTED,
    )

    private val REQUIRED_OPTIONS = linkedSetOf("--input", "--output", "--report")
}
