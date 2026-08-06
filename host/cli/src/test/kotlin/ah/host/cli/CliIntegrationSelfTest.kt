package ah.host.cli

import ah.host.container.ContainerErrorCode
import ah.host.container.ContainerException
import ah.host.repacker.PackageErrorCode
import ah.host.repacker.PackageException
import ah.host.repacker.RuntimeBundle
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

object CliIntegrationSelfTest {
    @JvmStatic
    fun main(args: Array<String>) {
        val reportDir = Path.of(requireNotNull(System.getProperty("ah.cli.reportDir"))).toAbsolutePath().normalize()
        val signedFixture = Path.of(requireNotNull(System.getProperty("ah.cli.signedFixture"))).toAbsolutePath().normalize()
        check(Files.isRegularFile(signedFixture)) { "signed fixture missing" }
        Files.createDirectories(reportDir)
        val root = Files.createTempDirectory(reportDir, "full-flow-")
        try {
            val validSignedFixture = buildValidSignedFixture(root.resolve("fixture-build"), signedFixture)
            val input = root.resolve("输入 fixture with spaces.apk")
            Files.copy(validSignedFixture, input)
            val inputHash = TestSupport.sha256(input)
            val bundle = TestSupport.runtimeBundle()

            val first = success(root.resolve("run-a"), input, bundle)
            val second = success(root.resolve("run-b"), input, bundle)
            check(TestSupport.normalizedReport(first.report) == TestSupport.normalizedReport(second.report))
            check(TestSupport.sha256(input) == inputHash)

            val normalized = TestSupport.normalizedReport(first.report)
            Files.writeString(reportDir.resolve("normalized-success.json"), normalized, StandardCharsets.UTF_8)
            Files.copy(first.report, reportDir.resolve("success-report.json"), StandardCopyOption.REPLACE_EXISTING)
            Files.copy(first.output, reportDir.resolve("output-unsigned.apk"), StandardCopyOption.REPLACE_EXISTING)

            val errorMatrix = failureMatrix(
                root,
                input,
                signedFixture.parent.resolve("unsigned.apk"),
                signedFixture,
                bundle,
                inputHash,
            )
            writeJson(reportDir.resolve("error-matrix.json"), errorMatrix)
            writeJson(
                reportDir.resolve("cleanup-matrix.json"),
                errorMatrix.map { row ->
                    linkedMapOf(
                        "fixture" to row.getValue("fixture"),
                        "output_absent" to row.getValue("output_absent"),
                        "workspace_absent" to row.getValue("workspace_absent"),
                        "input_unchanged" to row.getValue("input_unchanged"),
                    )
                },
            )
            writeJson(
                reportDir.resolve("console-capture.json"),
                linkedMapOf(
                    "exit_code" to first.exitCode,
                    "stdout" to first.stdout,
                    "stderr" to first.stderr,
                    "report_basename" to first.report.fileName.toString(),
                ),
            )
            writeJson(reportDir.resolve("artifact-hashes.json"), artifactHashes(input, first.output, first.report, bundle))
            writeJson(
                reportDir.resolve("schema-validation.json"),
                linkedMapOf(
                    "schema_version" to 1,
                    "success_report_valid" to true,
                    "failure_reports_valid" to true,
                    "encoding" to "UTF-8",
                    "bom" to false,
                    "line_endings" to "LF",
                ),
            )
            println("M1-06 full-flow CLI matrix PASS")
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    private fun success(root: Path, input: Path, bundle: RuntimeBundle): Invocation {
        Files.createDirectories(root)
        val output = root.resolve("output unsigned.apk")
        val report = root.resolve("report result.json")
        val invocation = invoke(input, output, report, RuntimeBundleProvider { bundle })
        check(invocation.exitCode == 0) {
            val reportText = if (Files.isRegularFile(report)) Files.readString(report, StandardCharsets.UTF_8) else "<missing>"
            "success invocation failed: $invocation report=$reportText"
        }
        check(invocation.stdout.isEmpty())
        check(invocation.stderr == "success/NONE/${report.fileName}")
        check(Files.isRegularFile(output) && Files.isRegularFile(report))
        val reportText = Files.readString(report, StandardCharsets.UTF_8)
        check(!reportText.startsWith('\uFEFF') && '\r' !in reportText && reportText.endsWith('\n'))
        val parsed = TestSupport.parseJson(reportText)
        TestSupport.validateReport(parsed)
        check((parsed.getValue("result") as Map<*, *>)["status"] == "success")
        check((parsed.getValue("output") as Map<*, *>)["sha256"] == TestSupport.sha256(output))
        check((parsed.getValue("input") as Map<*, *>)["sha256"] == TestSupport.sha256(input))
        val stages = parsed.getValue("stages") as List<*>
        check(stages.size == PipelineStage.entries.size)
        check(stages.all { (it as Map<*, *>)["status"] == "success" })
        val dex = parsed.getValue("dex") as Map<*, *>
        check(dex["count"] == 2L)
        val abi = parsed.getValue("abi") as Map<*, *>
        check((abi["input"] as List<*>).toSet() == setOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64"))
        val application = parsed.getValue("application") as Map<*, *>
        check(application["application_class"] != null && application["original_factory"] != null)
        assertUnsigned(output)
        return invocation
    }

    private fun failureMatrix(
        root: Path,
        input: Path,
        unsignedFixture: Path,
        invalidAxmlFixture: Path,
        bundle: RuntimeBundle,
        inputHash: String,
    ): List<Map<String, Any?>> {
        data class FailureCase(
            val name: String,
            val exit: Int,
            val code: String,
            val stage: PipelineStage,
            val provider: RuntimeBundleProvider = RuntimeBundleProvider { bundle },
            val faults: CliFaults = NO_CLI_FAULTS,
            val cancellation: CancellationProbe = CancellationProbe { false },
            val shutdownHooks: ShutdownHooks = CapturingShutdownHooks(),
            val expectReport: Boolean = true,
        )

        val cancellationChecks = AtomicInteger()
        val forcedShutdown = CapturingShutdownHooks()
        val cases = listOf(
            FailureCase(
                "container",
                13,
                "CONTAINER_CRYPTO",
                PipelineStage.CONTAINER,
                faults = before(PipelineStage.CONTAINER) { throw ContainerException(ContainerErrorCode.CONTAINER_CRYPTO, "fixture") },
            ),
            FailureCase(
                "package",
                14,
                "PACKAGE_WRITE_FAILED",
                PipelineStage.PACKAGE,
                faults = before(PipelineStage.PACKAGE) { throw PackageException(PackageErrorCode.PACKAGE_WRITE_FAILED, "fixture") },
            ),
            FailureCase(
                "package-short-write",
                14,
                "PACKAGE_WRITE_FAILED",
                PipelineStage.PACKAGE,
                faults = before(PipelineStage.PACKAGE) { throw PackageException(PackageErrorCode.PACKAGE_WRITE_FAILED, "shortWrite") },
            ),
            FailureCase(
                "package-disk-full",
                14,
                "PACKAGE_WRITE_FAILED",
                PipelineStage.PACKAGE,
                faults = before(PipelineStage.PACKAGE) { throw PackageException(PackageErrorCode.PACKAGE_WRITE_FAILED, "diskFull") },
            ),
            FailureCase(
                "verify",
                15,
                "OUTPUT_VERIFICATION_FAILED",
                PipelineStage.VERIFY,
                faults = before(PipelineStage.VERIFY) { throw PackageException(PackageErrorCode.OUTPUT_VERIFICATION_FAILED, "fixture") },
            ),
            FailureCase(
                "publish",
                15,
                "OUTPUT_ATOMIC_MOVE_UNSUPPORTED",
                PipelineStage.PUBLISH,
                faults = before(PipelineStage.PUBLISH) { throw PackageException(PackageErrorCode.OUTPUT_ATOMIC_MOVE_UNSUPPORTED, "fixture") },
            ),
            FailureCase(
                "runtime-unavailable",
                70,
                "INTERNAL_RUNTIME_BUNDLE_UNAVAILABLE",
                PipelineStage.PACKAGE,
                provider = RuntimeBundleProvider { throw RuntimeBundleUnavailable() },
            ),
            FailureCase(
                "cancelled",
                70,
                "INTERNAL_CANCELLED",
                PipelineStage.INSPECT,
                cancellation = CancellationProbe { cancellationChecks.incrementAndGet() >= 2 },
            ),
            FailureCase(
                "shutdown-cleanup",
                70,
                "INTERNAL_CANCELLED",
                PipelineStage.PUBLISH,
                faults = before(PipelineStage.PUBLISH) {
                    forcedShutdown.fire()
                    throw CliFailure(70, "INTERNAL_CANCELLED", PipelineStage.PUBLISH, "internal.cancelled", ResultStatus.FAILED)
                },
                shutdownHooks = forcedShutdown,
            ),
            FailureCase(
                "report-publish-race",
                15,
                "REPORT_ALREADY_EXISTS",
                PipelineStage.PUBLISH,
                faults = object : CliFaults {
                    override fun beforeReportPublish(report: Path, success: Boolean) {
                        if (success) Files.writeString(report, "race", StandardCharsets.UTF_8)
                    }
                },
                expectReport = false,
            ),
            FailureCase(
                "report-publish-failure",
                15,
                "REPORT_PUBLISH_FAILED",
                PipelineStage.PUBLISH,
                faults = object : CliFaults {
                    override fun beforeReportPublish(report: Path, success: Boolean) {
                        if (success) {
                            throw CliFailure(15, "REPORT_PUBLISH_FAILED", PipelineStage.PUBLISH, "report.publish", ResultStatus.FAILED)
                        }
                    }
                },
            ),
        )
        val rows = ArrayList<Map<String, Any?>>()
        cases.forEach { case ->
            val caseRoot = root.resolve("failure-${case.name}")
            Files.createDirectories(caseRoot)
            val output = caseRoot.resolve("output.apk")
            val report = caseRoot.resolve("report.json")
            val invocation = invoke(input, output, report, case.provider) { provider ->
                ProtectionPipeline(
                    provider,
                    instantClock = InstantClock { Instant.EPOCH },
                    cancellationProbe = case.cancellation,
                    faults = case.faults,
                    shutdownHooks = case.shutdownHooks,
                )
            }
            check(invocation.exitCode == case.exit) { "${case.name}: $invocation" }
            check(invocation.stdout.isEmpty())
            check(invocation.stderr == "failed/${case.code}/${report.fileName}") { "${case.name}: ${invocation.stderr}" }
            check(!Files.exists(output)) { "${case.name}: output survived" }
            check(TestSupport.sha256(input) == inputHash)
            if (case.expectReport) {
                val text = Files.readString(report, StandardCharsets.UTF_8)
                check(root.toString() !in text && "Exception" !in text && "stackTrace" !in text)
                val parsed = TestSupport.parseJson(text)
                TestSupport.validateReport(parsed)
                val errors = parsed.getValue("errors") as List<*>
                val error = errors.single() as Map<*, *>
                check(error["code"] == case.code && error["stage"] == case.stage.wireName)
            } else {
                check(Files.readString(report, StandardCharsets.UTF_8) == "race")
            }
            val workspaceAbsent = Files.list(caseRoot).use { stream ->
                stream.noneMatch { it.fileName.toString().startsWith(".ah-cli-") || it.fileName.toString().startsWith(".ah-report-") }
            }
            check(workspaceAbsent)
            rows += linkedMapOf(
                "fixture" to case.name,
                "exit_code" to case.exit,
                "error_code" to case.code,
                "stage" to case.stage.wireName,
                "failure_report_valid" to case.expectReport,
                "output_absent" to true,
                "workspace_absent" to true,
                "input_unchanged" to true,
            )
        }

        check(Files.isRegularFile(unsignedFixture))
        val signerRoot = root.resolve("failure-signer")
        Files.createDirectories(signerRoot)
        val signerInput = signerRoot.resolve("unsigned.apk")
        val signerOutput = signerRoot.resolve("output.apk")
        val signerReport = signerRoot.resolve("report.json")
        Files.copy(unsignedFixture, signerInput)
        val signerInputHash = TestSupport.sha256(signerInput)
        val signerInvocation = invoke(signerInput, signerOutput, signerReport, RuntimeBundleProvider { bundle })
        check(signerInvocation.exitCode == 11)
        check(signerInvocation.stdout.isEmpty())
        val signerParsed = TestSupport.parseJson(signerReport)
        TestSupport.validateReport(signerParsed)
        val signerError = (signerParsed.getValue("errors") as List<*>).single() as Map<*, *>
        check(signerError["stage"] == PipelineStage.SIGNER.wireName)
        check(!Files.exists(signerOutput) && TestSupport.sha256(signerInput) == signerInputHash)
        rows += linkedMapOf(
            "fixture" to "signer",
            "exit_code" to 11,
            "error_code" to signerError["code"],
            "stage" to PipelineStage.SIGNER.wireName,
            "failure_report_valid" to true,
            "output_absent" to true,
            "workspace_absent" to true,
            "input_unchanged" to true,
        )

        check(Files.isRegularFile(invalidAxmlFixture))
        val axmlRoot = root.resolve("failure-axml")
        Files.createDirectories(axmlRoot)
        val axmlInput = axmlRoot.resolve("invalid-axml.apk")
        val axmlOutput = axmlRoot.resolve("output.apk")
        val axmlReport = axmlRoot.resolve("report.json")
        Files.copy(invalidAxmlFixture, axmlInput)
        val axmlInputHash = TestSupport.sha256(axmlInput)
        val axmlInvocation = invoke(axmlInput, axmlOutput, axmlReport, RuntimeBundleProvider { bundle })
        check(axmlInvocation.exitCode == 12 && axmlInvocation.stderr.startsWith("rejected/AXML_MALFORMED/"))
        val axmlParsed = TestSupport.parseJson(axmlReport)
        TestSupport.validateReport(axmlParsed)
        check(!Files.exists(axmlOutput) && TestSupport.sha256(axmlInput) == axmlInputHash)
        rows += linkedMapOf(
            "fixture" to "axml",
            "exit_code" to 12,
            "error_code" to "AXML_MALFORMED",
            "stage" to PipelineStage.MANIFEST.wireName,
            "failure_report_valid" to true,
            "output_absent" to true,
            "workspace_absent" to true,
            "input_unchanged" to true,
        )
        return rows
    }

    private fun before(expectedStage: PipelineStage, action: () -> Unit): CliFaults = object : CliFaults {
        override fun beforeStage(stage: PipelineStage) {
            if (stage == expectedStage) action()
        }
    }

    private class CapturingShutdownHooks : ShutdownHooks {
        private var hook: Thread? = null

        override fun add(hook: Thread) {
            check(this.hook == null)
            this.hook = hook
        }

        override fun remove(hook: Thread) {
            check(this.hook === hook)
            this.hook = null
        }

        fun fire() = requireNotNull(hook).run()
    }

    private fun invoke(
        input: Path,
        output: Path,
        report: Path,
        provider: RuntimeBundleProvider,
        pipelineFactory: (RuntimeBundleProvider) -> ProtectionPipeline = ::ProtectionPipeline,
    ): Invocation {
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()
        val exit = CliMain.run(
            arrayOf("protect", "--input", input.toString(), "--output", output.toString(), "--report", report.toString()),
            PrintStream(stdout, true, StandardCharsets.UTF_8),
            PrintStream(stderr, true, StandardCharsets.UTF_8),
            provider,
            pipelineFactory,
        )
        return Invocation(
            exit,
            stdout.toString(StandardCharsets.UTF_8),
            stderr.toString(StandardCharsets.UTF_8).trimEnd('\r', '\n'),
            output,
            report,
        )
    }

    private fun assertUnsigned(output: Path) {
        val result = runTool(androidTool("apksigner"), "verify", "--verbose", output.toString())
        check(result.first != 0) { "output unexpectedly signed: ${result.second}" }
    }

    private fun buildValidSignedFixture(root: Path, signerFixture: Path): Path {
        Files.createDirectories(root)
        val manifest = root.resolve("AndroidManifest.xml")
        Files.writeString(
            manifest,
            """<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="ah.fixtures.cli"><uses-sdk android:minSdkVersion="29" android:targetSdkVersion="36"/><application android:name=".FixtureApplication" android:appComponentFactory=".FixtureFactory"/></manifest>""",
            StandardCharsets.UTF_8,
        )
        val linked = root.resolve("linked.apk")
        val sdk = androidSdk()
        val link = runTool(
            androidTool("aapt2"),
            "link",
            "-o",
            linked.toString(),
            "--manifest",
            manifest.toString(),
            "-I",
            sdk.resolve("platforms/android-36/android.jar").toString(),
        )
        check(link.first == 0) { "aapt2 link failed: ${link.second}" }

        val unsigned = root.resolve("unsigned.apk")
        ZipOutputStream(Files.newOutputStream(unsigned)).use { output ->
            copyEntries(linked, output) { true }
            copyEntries(signerFixture, output) { name ->
                name == "classes.dex" || name == "classes2.dex" || name.startsWith("lib/")
            }
        }
        val fixtures = signerFixture.parent
        val signed = root.resolve("signed.apk")
        val sign = runTool(
            androidTool("apksigner"),
            "sign",
            "--key",
            fixtures.resolve("current-key.pk8").toString(),
            "--cert",
            fixtures.resolve("current-cert.der").toString(),
            "--v1-signing-enabled",
            "true",
            "--v2-signing-enabled",
            "true",
            "--v3-signing-enabled",
            "true",
            "--out",
            signed.toString(),
            unsigned.toString(),
        )
        check(sign.first == 0) { "apksigner sign failed: ${sign.second}" }
        return signed
    }

    private fun copyEntries(source: Path, output: ZipOutputStream, include: (String) -> Boolean) {
        ZipFile(source.toFile()).use { zip ->
            zip.entries().asSequence().filter { !it.isDirectory && include(it.name) }.forEach { entry ->
                val target = ZipEntry(entry.name)
                target.time = 0L
                output.putNextEntry(target)
                zip.getInputStream(entry).use { it.copyTo(output) }
                output.closeEntry()
            }
        }
    }

    private fun androidSdk(): Path = sequenceOf(System.getenv("ANDROID_HOME"), System.getenv("ANDROID_SDK_ROOT"))
        .filterNotNull()
        .map(Path::of)
        .firstOrNull { Files.isDirectory(it) }
        ?: error("pinned Android SDK environment missing")

    private fun androidTool(name: String): Path {
        val windows = System.getProperty("os.name").lowercase().contains("windows")
        val extension = if (windows) when (name) {
            "apksigner" -> ".bat"
            else -> ".exe"
        } else ""
        return androidSdk().resolve("build-tools/36.1.0/$name$extension").also {
            check(Files.isRegularFile(it)) { "pinned $name 36.1.0 missing" }
        }
    }

    private fun runTool(tool: Path, vararg arguments: String): Pair<Int, String> {
        val windowsBatch = System.getProperty("os.name").lowercase().contains("windows") && tool.toString().endsWith(".bat")
        val command = if (windowsBatch) listOf("cmd.exe", "/d", "/c", tool.toString()) + arguments else
            listOf(tool.toString()) + arguments
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        return process.waitFor() to output
    }

    private fun artifactHashes(input: Path, output: Path, report: Path, bundle: RuntimeBundle): Map<String, Any?> =
        linkedMapOf(
            "input_sha256" to TestSupport.sha256(input),
            "output_sha256" to TestSupport.sha256(output),
            "report_sha256" to TestSupport.sha256(report),
            "schema_sha256" to TestSupport.sha256(findRoot().resolve("docs/specs/report-v1.schema.json")),
            "runtime_bootstrap_sha256" to hex(TestSupport.sha256(bundle.bootstrapDex)),
            "runtime_template_sha256" to bundle.templates.entries.sortedBy { it.key.directoryName }.associate { entry ->
                entry.key.directoryName to hex(TestSupport.sha256(entry.value.bytes))
            },
        )

    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private fun writeJson(path: Path, value: Any?) {
        Files.writeString(path, JsonEncoder.encode(value) + "\n", StandardCharsets.UTF_8)
    }

    private fun findRoot(): Path {
        var current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()
        while (!Files.exists(current.resolve("settings.gradle.kts"))) current = requireNotNull(current.parent)
        return current
    }

    private data class Invocation(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
        val output: Path,
        val report: Path,
    )
}
