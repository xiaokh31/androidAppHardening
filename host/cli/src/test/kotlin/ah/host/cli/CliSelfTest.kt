package ah.host.cli

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.time.Instant

object CliSelfTest {
    @JvmStatic
    fun main(args: Array<String>) {
        val reportDir = Path.of(requireNotNull(System.getProperty("ah.cli.reportDir"))).toAbsolutePath().normalize()
        Files.createDirectories(reportDir)
        parserAndGlobalCommands()
        reportWriterAndSchema(reportDir)
        pathMatrix(reportDir)
        capabilityScan(reportDir)
        println("M1-06 CLI unit matrix PASS")
    }

    private fun parserAndGlobalCommands() {
        fun run(vararg args: String): Triple<Int, String, String> {
            val stdout = ByteArrayOutputStream()
            val stderr = ByteArrayOutputStream()
            val exit = CliMain.run(
                arrayOf(*args),
                PrintStream(stdout, true, StandardCharsets.UTF_8),
                PrintStream(stderr, true, StandardCharsets.UTF_8),
                RuntimeBundleProvider { TestSupport.runtimeBundle() },
            )
            return Triple(exit, stdout.toString(StandardCharsets.UTF_8), stderr.toString(StandardCharsets.UTF_8))
        }
        val help = run("--help")
        check(help.first == 0 && help.second.contains("protect --input") && help.third.isEmpty())
        val version = run("--version")
        check(
            version.first == 0 && version.third.isEmpty() &&
                version.second in setOf("$TOOL_NAME $TOOL_VERSION\r\n", "$TOOL_NAME $TOOL_VERSION\n"),
        )
        val invalid = listOf(
            emptyArray(),
            arrayOf("protect"),
            arrayOf("protect", "--input", "a.apk", "--output", "b.apk"),
            arrayOf("protect", "--input", "a.apk", "--input", "b.apk", "--output", "c.apk", "--report", "r.json"),
            arrayOf("protect", "--input", "a.apk", "--output", "b.apk", "--report", "r.json", "--force", "true"),
            arrayOf("protect", "--input", "a.apk", "--output", "b.apk", "--report", "r.json", "--keystore", "x"),
        )
        invalid.forEach { values ->
            val result = run(*values)
            check(result.first == 2 && result.second.isEmpty() && result.third == "rejected/USAGE_INVALID/-${System.lineSeparator()}")
        }
    }

    private fun reportWriterAndSchema(reportDir: Path) {
        val root = Files.createTempDirectory(reportDir, "schema-")
        try {
            val input = root.resolve("输入 sample.apk").also { Files.write(it, byteArrayOf(1)) }
            val paths = PathPolicy.validate(ProtectArguments(input, root.resolve("output.apk"), root.resolve("report.json")))
            val bytes = ReportV1Writer.write(
                ReportSnapshot(
                    ResultStatus.FAILED,
                    "INTERNAL_UNEXPECTED",
                    Instant.EPOCH,
                    Instant.EPOCH,
                    paths,
                    TestSupport.sha256(input),
                    null,
                    null,
                    null,
                    null,
                    listOf(StageRecord(PipelineStage.INSPECT, "failed", 0)),
                    listOf(ReportError("INTERNAL_UNEXPECTED", PipelineStage.INSPECT, "internal.unexpected")),
                    1,
                    null,
                ),
            )
            check(bytes.take(3) != listOf(0xef.toByte(), 0xbb.toByte(), 0xbf.toByte()))
            val text = bytes.toString(StandardCharsets.UTF_8)
            check('\r' !in text && text.endsWith('\n'))
            val parsed = TestSupport.parseJson(text)
            TestSupport.validateReport(parsed)
            val schema = Files.readString(findRoot().resolve("docs/specs/report-v1.schema.json"), StandardCharsets.UTF_8)
            val schemaObject = TestSupport.parseJson(schema)
            check(schemaObject["${'$'}schema"] == "https://json-schema.org/draft/2020-12/schema")
            check((schemaObject["required"] as List<*>).size == 13)
            val invalid = LinkedHashMap(parsed).apply { put("unexpected", true) }
            check(runCatching { ReportSchemaValidator(schemaObject).validate(invalid) }.isFailure)
            Files.write(reportDir.resolve("schema-fixture.json"), bytes)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    private fun pathMatrix(reportDir: Path) {
        val root = Files.createTempDirectory(reportDir, "paths-")
        try {
            val input = root.resolve("input.apk").also { Files.write(it, byteArrayOf(1)) }
            val cases = linkedMapOf<String, String>()
            fun expect(name: String, expected: String, arguments: ProtectArguments) {
                val failure = runCatching { PathPolicy.validate(arguments) }.exceptionOrNull() as? CliFailure
                check(failure?.errorCode == expected) { "$name: $failure" }
                cases[name] = expected
            }
            expect("output-alias", "OUTPUT_PATH_ALIAS", ProtectArguments(input, input, root.resolve("report.json")))
            expect("report-alias", "OUTPUT_PATH_ALIAS", ProtectArguments(input, root.resolve("output.apk"), input))
            expect("output-report-alias", "OUTPUT_PATH_ALIAS", ProtectArguments(input, root.resolve("same.bin"), root.resolve("same.bin")))
            val outputHardLink = root.resolve("input-output-hardlink.apk").also { Files.createLink(it, input) }
            expect("output-hardlink-alias", "OUTPUT_PATH_ALIAS", ProtectArguments(input, outputHardLink, root.resolve("report.json")))
            val reportHardLink = root.resolve("input-report-hardlink.json").also { Files.createLink(it, input) }
            expect("report-hardlink-alias", "OUTPUT_PATH_ALIAS", ProtectArguments(input, root.resolve("output.apk"), reportHardLink))
            val existingOutput = root.resolve("exists.apk").also { Files.write(it, byteArrayOf(2)) }
            expect("output-exists", "OUTPUT_ALREADY_EXISTS", ProtectArguments(input, existingOutput, root.resolve("report.json")))
            val existingReport = root.resolve("exists.json").also { Files.write(it, byteArrayOf(3)) }
            expect("report-exists", "REPORT_ALREADY_EXISTS", ProtectArguments(input, root.resolve("output.apk"), existingReport))
            expect(
                "output-parent-missing",
                "OUTPUT_PARENT_INVALID",
                ProtectArguments(input, root.resolve("missing/output.apk"), root.resolve("report.json")),
            )
            val parentFile = root.resolve("not-a-directory").also { Files.write(it, byteArrayOf(4)) }
            expect(
                "output-parent-file",
                "OUTPUT_PARENT_INVALID",
                ProtectArguments(input, parentFile.resolve("output.apk"), root.resolve("report.json")),
            )
            val readOnlyParent = root.resolve("read-only-parent").also(Files::createDirectory)
            if (Files.getFileStore(readOnlyParent).supportsFileAttributeView("posix")) {
                val original = Files.getPosixFilePermissions(readOnlyParent)
                try {
                    Files.setPosixFilePermissions(readOnlyParent, PosixFilePermissions.fromString("r-x------"))
                    expect(
                        "output-parent-read-only",
                        "OUTPUT_PARENT_INVALID",
                        ProtectArguments(input, readOnlyParent.resolve("output.apk"), root.resolve("report.json")),
                    )
                    cases.remove("output-parent-read-only")
                } finally {
                    Files.setPosixFilePermissions(readOnlyParent, original)
                }
            }
            Files.writeString(
                reportDir.resolve("path-error-matrix.json"),
                JsonEncoder.encode(cases) + "\n",
                StandardCharsets.UTF_8,
            )
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    private fun capabilityScan(reportDir: Path) {
        val sourceRoot = findRoot().resolve("host/cli/src/main")
        val source = Files.walk(sourceRoot).use { stream ->
            stream.filter(Files::isRegularFile).sorted().map(Files::readString).toList().joinToString("\n")
        }
        val forbidden = listOf("--keystore", "--private-key", "--alias", "--password", "ProcessBuilder(", "Runtime.getRuntime().exec")
        val findings = forbidden.filter(source::contains)
        check(findings.isEmpty()) { "forbidden capability found: $findings" }
        Files.writeString(reportDir.resolve("capability-scan.json"), JsonEncoder.encode(mapOf("findings" to findings)) + "\n")
    }

    private fun findRoot(): Path {
        var current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()
        while (!Files.exists(current.resolve("settings.gradle.kts"))) current = requireNotNull(current.parent)
        return current
    }
}
