package ah.integration.equivalence

import ah.integration.fixtures.FixtureCatalog
import ah.integration.fixtures.FixtureDriver
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

object CrossPlatformCorpus {
    private const val BUILD_TOOLS = "36.1.0"

    @JvmStatic
    fun main(args: Array<String>) {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        Locale.setDefault(Locale.ROOT)
        val mode = requireNotNull(System.getProperty("m303.mode"))
        val root = propertyPath("m303.root")
        val output = propertyPath("m303.output")
        val ownedBuild = root.resolve("build/equivalence").normalize()
        require(output.startsWith(ownedBuild)) { "M3-03 output must stay under build/equivalence" }
        output.toFile().deleteRecursively()
        Files.createDirectories(output)
        when (mode) {
            "seed" -> seed(root, output)
            "run" -> runPlatform(root, output, propertyPath("m303.inputs"))
            else -> error("unsupported M3-03 mode")
        }
    }

    private fun seed(root: Path, output: Path) {
        val fixtures = FixtureCatalog.load(root)
        check(fixtures.size == 9)
        val tools = tools()
        val signing = root.resolve("integration-tests/build/test-signing/m3-03-seed").normalize()
        check(signing.startsWith(root.resolve("integration-tests/build").normalize()))
        signing.toFile().deleteRecursively()
        Files.createDirectories(signing)
        val passwordBytes = ByteArray(18).also(SecureRandom()::nextBytes)
        val password = passwordBytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }
        passwordBytes.fill(0)
        val key = signing.resolve("seed.jks")
        try {
            command(
                listOf(
                    tools.keytool.toString(), "-genkeypair", "-noprompt", "-keystore", key.toString(),
                    "-storepass", password, "-keypass", password, "-alias", "m303-seed", "-keyalg", "RSA",
                    "-keysize", "3072", "-validity", "2",
                    "-dname", "CN=M3-03 Synthetic Equivalence,O=androidAppHardening,C=XX",
                ),
                Duration.ofMinutes(1),
            )
            val inputs = output.resolve("inputs")
            Files.createDirectories(inputs)
            fixtures.forEach { fixture ->
                val destination = inputs.resolve("${fixture.id}.apk")
                command(
                    listOf(
                        tools.apksigner.toString(), "sign", "--v4-signing-enabled", "false", "--ks", key.toString(), "--ks-key-alias", "m303-seed",
                        "--ks-pass", "pass:$password", "--key-pass", "pass:$password", "--out", destination.toString(),
                        fixture.unsignedFixtureApk.toString(),
                    ),
                    Duration.ofMinutes(1),
                )
            }
            val negative = output.resolve("negative")
            Files.createDirectories(negative)
            Files.copy(fixtures.first().unsignedFixtureApk, negative.resolve("unsigned.apk"))
            Files.write(negative.resolve("invalid.apk"), "M3-03 invalid synthetic APK\n".toByteArray(StandardCharsets.UTF_8))
            writeHashes(output, output.resolve("inputs.sha256"))
        } finally {
            password.toCharArray().fill('\u0000')
            signing.toFile().deleteRecursively()
        }
        check(!Files.exists(signing))
    }

    private fun runPlatform(root: Path, output: Path, suppliedInputs: Path) {
        check(Files.isDirectory(suppliedInputs))
        val expected = parseHashes(suppliedInputs.resolve("inputs.sha256"))
        check(expected.size == 11) { "M3-03 fixed corpus must contain nine fixtures and two negatives" }
        val workRoot = root.resolve("integration-tests/build/equivalence-work/路径-utf8").normalize()
        val perturbation = workRoot.resolve("deep-" + "m303".repeat(24)).resolve("inputs").normalize()
        val negativePerturbation = workRoot.resolve("negative").normalize()
        workRoot.toFile().deleteRecursively()
        Files.createDirectories(perturbation)
        expected.keys.forEach { relative ->
            val source = suppliedInputs.resolve(relative).normalize()
            val target = perturbation.resolve(relative).normalize()
            check(source.startsWith(suppliedInputs) && target.startsWith(perturbation) && Files.isRegularFile(source))
            Files.createDirectories(target.parent)
            Files.copy(source, target)
            check(sha256(target) == expected.getValue(relative))
        }
        Files.createDirectories(negativePerturbation)
        listOf("unsigned.apk", "invalid.apk").forEach { name ->
            Files.copy(suppliedInputs.resolve("negative/$name"), negativePerturbation.resolve(name))
            check(sha256(negativePerturbation.resolve(name)) == expected.getValue("negative/$name"))
        }

        try {
            repeat(2) { index ->
                val run = "run${index + 1}"
                val work = root.resolve("integration-tests/build/m3-03/$run")
                val report = root.resolve("integration-tests/build/reports/m3-03-$run.json")
                val signing = root.resolve("integration-tests/build/test-signing/m3-03-$run")
                set("m301.root", root)
                set("m301.report", report)
                set("m301.signing", signing)
                set("m301.work", work)
                System.setProperty("m301.hostOnly", "true")
                System.setProperty("m303.skipNegatives", "true")
                set("m303.signedInputCorpus", perturbation.resolve("inputs"))
                FixtureDriver.main(emptyArray())
                val runOutput = output.resolve("runs/$run")
                Files.createDirectories(runOutput)
                FixtureCatalog.load(root).forEach { fixture ->
                    val source = work.resolve("cases/${fixture.id}")
                    val target = runOutput.resolve(fixture.id)
                    Files.createDirectories(target)
                    Files.copy(source.resolve("signed-input.apk"), target.resolve("input.apk"))
                    Files.copy(source.resolve("protected-unsigned.apk"), target.resolve("output.apk"))
                    Files.copy(source.resolve("protect-report.json"), target.resolve("report.json"))
                }
                Files.copy(report, output.resolve("$run-fixture-report.json"))
            }
            runNegatives(output, negativePerturbation)
            expected.forEach { (relative, digest) ->
                check(sha256(suppliedInputs.resolve(relative)) == digest) { "fixed input source changed: $relative" }
                check(sha256(perturbation.resolve(relative)) == digest) { "deep-path input copy changed: $relative" }
            }
            listOf("unsigned.apk", "invalid.apk").forEach { name ->
                check(sha256(negativePerturbation.resolve(name)) == expected.getValue("negative/$name")) {
                    "UTF-8 negative input copy changed: $name"
                }
            }
            Files.writeString(
                output.resolve("environment.json"),
                "{\"schema_version\":1,\"os_family\":\"${if (isWindows()) "windows" else "ubuntu"}\"," +
                    "\"java_version\":\"${System.getProperty("java.version")}\",\"timezone\":\"${TimeZone.getDefault().id}\"," +
                    "\"gradle_version\":\"${requireNotNull(System.getProperty("m303.gradleVersion"))}\"," +
                    "\"build_tools_version\":\"${requireNotNull(System.getProperty("m303.buildToolsVersion"))}\"," +
                    "\"locale\":\"${Locale.getDefault().toLanguageTag()}\",\"encoding\":\"${System.getProperty("file.encoding")}\"," +
                    "\"fixture_count\":9,\"runs_per_fixture\":2,\"input_immutable\":true,\"test_signing_cleanup\":true}\n",
                StandardCharsets.UTF_8,
            )
        } finally {
            workRoot.toFile().deleteRecursively()
        }
    }

    private fun runNegatives(output: Path, negativeInputs: Path) {
        val destination = output.resolve("negative")
        Files.createDirectories(destination)
        listOf("unsigned", "invalid").forEach { name ->
            val input = negativeInputs.resolve("$name.apk")
            val result = command(
                listOf(
                    javaTool().toString(), "ah.host.cli.CliMain", "protect",
                    "--input", input.toString(), "--output", destination.resolve("$name-output.apk").toString(),
                    "--report", destination.resolve("$name-report.json").toString(),
                ),
                Duration.ofMinutes(2),
                allowFailure = true,
                environment = mapOf("CLASSPATH" to System.getProperty("java.class.path")),
            )
            val lines = result.output.lineSequence().map(String::trim).filter(String::isNotEmpty).toList()
            check(lines.size == 1) { "$name did not emit the canonical one-line CLI result" }
            val match = Regex("^(rejected|failed)/([A-Z0-9_]+)/([^/]+)$").matchEntire(lines.single())
                ?: error("$name emitted a non-canonical CLI result")
            val report = destination.resolve("$name-report.json")
            check(result.exit != 0 && !Files.exists(destination.resolve("$name-output.apk")))
            Files.writeString(destination.resolve("$name-exit.txt"), "${result.exit}\n", StandardCharsets.US_ASCII)
            Files.writeString(
                destination.resolve("$name-result.json"),
                "{\"schema_version\":1,\"status\":\"${match.groupValues[1]}\"," +
                    "\"error_code\":\"${match.groupValues[2]}\",\"report_basename\":\"${match.groupValues[3]}\"," +
                    "\"report_present\":${Files.isRegularFile(report)},\"partial_output\":false}\n",
                StandardCharsets.UTF_8,
            )
        }
    }

    private fun writeHashes(root: Path, target: Path) {
        val rows = Files.walk(root).use { paths ->
            paths.filter(Files::isRegularFile).filter { it != target }.map { path ->
                val relative = root.relativize(path).joinToString("/") { it.toString() }
                "$relative ${sha256(path)}"
            }.sorted().toList()
        }
        Files.writeString(target, rows.joinToString("\n", postfix = "\n"), StandardCharsets.US_ASCII)
    }

    private fun parseHashes(path: Path): Map<String, String> = Files.readAllLines(path, StandardCharsets.US_ASCII).associate { line ->
        val split = line.lastIndexOf(' ')
        check(split > 0)
        line.substring(0, split) to line.substring(split + 1)
    }

    private fun propertyPath(name: String): Path =
        Path.of(requireNotNull(System.getProperty(name)) { "missing $name" }).toAbsolutePath().normalize()

    private fun set(name: String, path: Path) = System.setProperty(name, path.toString())

    private data class Tools(val apksigner: Path, val keytool: Path)
    private data class Result(val exit: Int, val output: String)

    private fun tools(): Tools {
        val sdk = sequenceOf(System.getenv("ANDROID_HOME"), System.getenv("ANDROID_SDK_ROOT"))
            .filterNotNull().map(Path::of).firstOrNull(Files::isDirectory) ?: error("pinned Android SDK unavailable")
        val suffix = if (isWindows()) ".bat" else ""
        val apksigner = sdk.resolve("build-tools/$BUILD_TOOLS/apksigner$suffix")
        val keytool = javaTool("keytool")
        listOf(apksigner, keytool).forEach { check(Files.isRegularFile(it)) { "missing pinned tool ${it.fileName}" } }
        return Tools(apksigner, keytool)
    }

    private fun javaTool(name: String = "java"): Path = Path.of(System.getProperty("java.home"))
        .resolve("bin").resolve(name + if (isWindows()) ".exe" else "")

    private fun command(
        command: List<String>,
        timeout: Duration,
        allowFailure: Boolean = false,
        environment: Map<String, String> = emptyMap(),
    ): Result {
        val actual = if (isWindows() && command.first().endsWith(".bat", ignoreCase = true)) {
            listOf("cmd.exe", "/d", "/c") + command
        } else command
        val processBuilder = ProcessBuilder(actual).redirectErrorStream(true)
        processBuilder.environment().putAll(environment)
        val process = processBuilder.start()
        val captured = ByteArrayOutputStream()
        val reader = Thread { process.inputStream.use { it.copyTo(captured) } }.also { it.isDaemon = true; it.start() }
        if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            process.destroyForcibly(); process.waitFor(); reader.join(5_000); error("bounded command timed out")
        }
        reader.join(5_000)
        check(!reader.isAlive)
        val result = Result(process.exitValue(), captured.toString(StandardCharsets.UTF_8))
        if (!allowFailure) check(result.exit == 0) { "command failed (${result.exit}): ${result.output.take(500)}" }
        return result
    }

    private fun sha256(path: Path): String = Files.newInputStream(path).use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
        digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private fun isWindows(): Boolean = System.getProperty("os.name").lowercase().contains("windows")
}
