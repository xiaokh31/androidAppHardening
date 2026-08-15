package ah.integration.fixtures

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

object FixtureDriver {
    private const val TOOL_VERSION = "36.1.0"
    private val packageSuffix = mapOf(
        "java-single-dex" to "java_single",
        "kotlin-single-dex" to "kotlin_single",
        "kotlin-multidex" to "kotlin_multidex",
        "custom-application" to "custom_application",
        "custom-factory" to "custom_factory",
        "startup-provider" to "startup_provider",
        "multi-process" to "multi_process",
        "jni-four-abi" to "jni_four",
        "jni-arm-only" to "jni_arm",
    )

    @JvmStatic
    fun main(args: Array<String>) {
        val root = Path.of(requireNotNull(System.getProperty("m301.root"))).toAbsolutePath().normalize()
        val report = Path.of(requireNotNull(System.getProperty("m301.report"))).toAbsolutePath().normalize()
        val signing = Path.of(requireNotNull(System.getProperty("m301.signing"))).toAbsolutePath().normalize()
        val work = Path.of(requireNotNull(System.getProperty("m301.work"))).toAbsolutePath().normalize()
        val ownedBuild = root.resolve("integration-tests/build").toAbsolutePath().normalize()
        val hostOnly = System.getProperty("m301.hostOnly") == "true"
        val expectedAbi = System.getProperty("m304.expectedAbi")?.takeIf(String::isNotBlank)
        val signedInputCorpus = System.getProperty("m303.signedInputCorpus")?.let { Path.of(it) }?.toAbsolutePath()?.normalize()
        val skipNegatives = System.getProperty("m303.skipNegatives") == "true"
        require(signing.startsWith(ownedBuild) && work.startsWith(ownedBuild))
        signing.toFile().deleteRecursively()
        work.toFile().deleteRecursively()
        Files.createDirectories(signing)
        Files.createDirectories(work)

        val rows = ArrayList<Map<String, Any?>>()
        var runtimeSignerNegative: Map<String, Any?>? = null
        var runtimeTagNegative: Map<String, Any?>? = null
        var device: Map<String, Any?> = emptyMap()
        var failure: Throwable? = null
        try {
            val tools = Tools.find()
            val adb = if (hostOnly) null else Adb(tools.adb, expectedAbi)
            device = if (adb == null) linkedMapOf("mode" to "host-only") else emptyMap()
            val credentials = if (signedInputCorpus == null || !skipNegatives) {
                Credentials.create(tools, signing.resolve("primary.jks"), "m301-primary")
            } else null
            val alternate = if (!skipNegatives) Credentials.create(tools, signing.resolve("alternate.jks"), "m301-alternate") else null
            val requestedCase = System.getProperty("m301.case").orEmpty()
            val fixtures = FixtureCatalog.load(root).let { catalog ->
                if (requestedCase.isEmpty()) catalog else catalog.filter { it.id == requestedCase }.also {
                    check(it.size == 1) { "unknown M3-01 fixture case" }
                }
            }
            fixtures.forEach { fixture ->
                println("M3-01 fixture case start: ${fixture.id}")
                rows += runCase(fixture, work.resolve("cases/${fixture.id}"), tools, adb, credentials, signedInputCorpus)
            }
            if (adb != null) device = adb.deviceInfo()
            if (!skipNegatives) runSignerNegatives(
                fixtures.first(), work.resolve("negative"), tools, checkNotNull(credentials), checkNotNull(alternate),
            )
            if (!skipNegatives && adb != null) {
                runtimeSignerNegative = runDifferentSignerRuntimeNegative(
                    fixtures.first(), work.resolve("different-signer"), tools, adb, checkNotNull(credentials), checkNotNull(alternate),
                )
                runtimeTagNegative = runTagTamperRuntimeNegative(
                    root, fixtures.first(), work.resolve("tag-tamper"), tools, adb, checkNotNull(credentials),
                )
            }
        } catch (caught: Throwable) {
            failure = caught
        } finally {
            signing.toFile().deleteRecursively()
        }

        Files.createDirectories(report.parent)
        val result = linkedMapOf<String, Any?>(
            "schema_version" to 1,
            "status" to if (failure == null) "pass" else "fail",
            "fixture_count" to rows.size,
            "device" to device,
            "fixtures" to rows,
            "negative_matrix" to if (skipNegatives) {
                emptyList<String>()
            } else if (hostOnly) {
                listOf("unsigned_input", "multiple_current_signer")
            } else {
                listOf("unsigned_input", "multiple_current_signer", "different_output_signer")
            },
            "runtime_signer_negative" to runtimeSignerNegative,
            "runtime_tag_negative" to runtimeTagNegative,
            "test_signing_cleanup" to !Files.exists(signing),
            "failure" to failure?.let { it::class.java.simpleName },
        )
        Files.writeString(report, Json.write(result) + "\n", StandardCharsets.UTF_8)
        if (failure != null) throw IllegalStateException("M3-01 fixture matrix failed", failure)
        println("M3-01 fixture matrix PASS: ${rows.size} fixtures")
    }

    private fun runCase(
        fixture: FixtureDescriptor,
        caseRoot: Path,
        tools: Tools,
        adb: Adb?,
        credentials: Credentials?,
        signedInputCorpus: Path?,
    ): Map<String, Any?> {
        Files.createDirectories(caseRoot)
        val signedInput = caseRoot.resolve("signed-input.apk")
        val unsignedOutput = caseRoot.resolve("protected-unsigned.apk")
        val signedOutput = caseRoot.resolve("protected-signed.apk")
        val productReport = caseRoot.resolve("protect-report.json")
        if (signedInputCorpus == null) {
            checkNotNull(credentials).sign(tools, fixture.unsignedFixtureApk, signedInput)
        } else {
            val supplied = signedInputCorpus.resolve("${fixture.id}.apk").normalize()
            check(supplied.startsWith(signedInputCorpus) && Files.isRegularFile(supplied)) { "missing fixed M3-03 input ${fixture.id}" }
            Files.copy(supplied, signedInput)
        }
        val inputBefore = sha256(signedInput)
        val inputSigner = signerDigest(tools, signedInput)
        val protect = runProduct(signedInput, unsignedOutput, productReport)
        check(protect.exit == 0) {
            val detail = if (Files.isRegularFile(productReport)) Files.readString(productReport, StandardCharsets.UTF_8) else protect.output
            "${fixture.id} protect failed: ${detail.takeLast(2_000)}"
        }
        check(inputBefore == sha256(signedInput)) { "${fixture.id} input changed" }
        check(Files.isRegularFile(unsignedOutput) && Files.isRegularFile(productReport))
        check(run(listOf(tools.apksigner.toString(), "verify", unsignedOutput.toString()), Duration.ofSeconds(30), allowFailure = true).exit != 0) {
            "${fixture.id} product output is signed"
        }
        val outputSigner = if (signedInputCorpus == null) {
            checkNotNull(credentials).sign(tools, unsignedOutput, signedOutput)
            signerDigest(tools, signedOutput).also { digest ->
                check(inputSigner == digest) { "${fixture.id} signer changed" }
            }
        } else null

        val packageName = packageName(fixture.id)
        val abi = adb?.abi().orEmpty()
        val shouldInstall = adb != null &&
            (fixture.expectedOutcome == "compatible" || abi.startsWith("arm") || abi.startsWith("armeabi"))
        var events: List<String> = emptyList()
        var expectedEvents = fixture.expectedEvents
        var configurationRelaunch = false
        var processFacts: Map<String, Any?>? = null
        if (adb != null) try {
            if (shouldInstall) {
                adb.install(checkNotNull(if (signedInputCorpus == null) signedOutput else null))
                adb.clearLogcat()
                val launch = adb.start(packageName)
                val observation = adb.awaitEvents(fixture.id, packageName, fixture.expectedEvents, launch)
                events = observation.observed
                expectedEvents = observation.expected
                configurationRelaunch = observation.configurationRelaunch
                processFacts = adb.processFacts(packageName)
            } else {
                val text = Files.readString(productReport, StandardCharsets.UTF_8)
                val armOnlyLimitation = Regex(
                    "\\\"limitations\\\"\\s*:\\s*\\[\\s*\\\"OUTPUT_LIMITED_TO_INPUT_NATIVE_ABIS\\\"\\s*]",
                )
                check(armOnlyLimitation.containsMatchIn(text) && fixture.payloadAbis.none { it == abi }) {
                    "${fixture.id} x86 limitation not reported"
                }
            }
        } finally {
            adb.uninstall(packageName)
        }

        return linkedMapOf(
            "id" to fixture.id,
            "status" to "pass",
            "installed" to shouldInstall,
            "catalog_expected_events" to fixture.expectedEvents,
            "expected_events" to expectedEvents,
            "observed_events" to events,
            "configuration_relaunch" to configurationRelaunch,
            "process_facts" to processFacts,
            "payload_abis" to fixture.payloadAbis,
            "expected_outcome" to fixture.expectedOutcome,
            "input_sha256" to inputBefore,
            "unsigned_output_sha256" to sha256(unsignedOutput),
            "signed_output_sha256" to if (outputSigner == null) null else sha256(signedOutput),
            "product_report_sha256" to sha256(productReport),
            "same_current_signer" to (outputSigner == null || inputSigner == outputSigner),
            "product_output_unsigned" to true,
            "package_cleanup" to (adb == null || !adb.isInstalled(packageName)),
        )
    }

    private fun runSignerNegatives(
        fixture: FixtureDescriptor,
        root: Path,
        tools: Tools,
        primary: Credentials,
        alternate: Credentials,
    ) {
        Files.createDirectories(root)
        val unsignedOutput = root.resolve("unsigned-output.apk")
        val unsignedReport = root.resolve("unsigned-report.json")
        val unsigned = runProduct(fixture.unsignedFixtureApk, unsignedOutput, unsignedReport)
        check(unsigned.exit == 11 && !Files.exists(unsignedOutput)) { "unsigned input did not fail at signer stage" }

        val multi = root.resolve("multiple-current.apk")
        val command = mutableListOf(
            tools.apksigner.toString(), "sign",
            "--v1-signing-enabled", "false",
            "--v2-signing-enabled", "true",
            "--v3-signing-enabled", "false",
            "--v4-signing-enabled", "false",
            "--ks", primary.keystore.toString(), "--ks-key-alias", primary.alias,
            "--ks-pass", "pass:${primary.password}", "--key-pass", "pass:${primary.password}",
            "--next-signer",
            "--ks", alternate.keystore.toString(), "--ks-key-alias", alternate.alias,
            "--ks-pass", "pass:${alternate.password}", "--key-pass", "pass:${alternate.password}",
            "--out", multi.toString(), fixture.unsignedFixtureApk.toString(),
        )
        check(run(command, Duration.ofMinutes(1)).exit == 0)
        val multiOutput = root.resolve("multiple-output.apk")
        val multiReport = root.resolve("multiple-report.json")
        val multiple = runProduct(multi, multiOutput, multiReport)
        check(multiple.exit == 11 && !Files.exists(multiOutput)) { "multiple-current signer input was accepted" }
    }

    private fun runDifferentSignerRuntimeNegative(
        fixture: FixtureDescriptor,
        root: Path,
        tools: Tools,
        adb: Adb,
        primary: Credentials,
        alternate: Credentials,
    ): Map<String, Any?> {
        Files.createDirectories(root)
        val input = root.resolve("input.apk")
        val output = root.resolve("output.apk")
        val report = root.resolve("report.json")
        val mismatched = root.resolve("mismatched.apk")
        primary.sign(tools, fixture.unsignedFixtureApk, input)
        check(runProduct(input, output, report).exit == 0)
        alternate.sign(tools, output, mismatched)
        val packageName = packageName(fixture.id)
        var launchExit = -1
        var events: List<String> = emptyList()
        try {
            adb.install(mismatched)
            val launch = adb.launch(packageName)
            launchExit = launch.exit
            events = adb.observeEvents(packageName, fixture.expectedEvents, Duration.ofSeconds(3))
            check(launch.exit != 0 || events.none(fixture.expectedEvents::contains)) {
                "different signer reached business events: $events"
            }
        } finally {
            adb.uninstall(packageName)
        }
        return linkedMapOf(
            "status" to "pass",
            "launch_exit" to launchExit,
            "observed_events" to events,
            "business_events_reached" to false,
            "package_cleanup" to !adb.isInstalled(packageName),
        )
    }

    private fun runTagTamperRuntimeNegative(
        repository: Path,
        fixture: FixtureDescriptor,
        root: Path,
        tools: Tools,
        adb: Adb,
        credentials: Credentials,
    ): Map<String, Any?> {
        Files.createDirectories(root)
        val input = root.resolve("input.apk")
        val output = root.resolve("protected-unsigned.apk")
        val report = root.resolve("protect-report.json")
        val mutations = repository.resolve("build/m3-04/fixture-tag-tamper")
        mutations.toFile().deleteRecursively()
        Files.createDirectories(mutations)
        val tampered = mutations.resolve("m0-05-payload-tag-first-unsigned.apk")
        val signedTampered = root.resolve("payload-tag-first.apk")
        credentials.sign(tools, fixture.unsignedFixtureApk, input)
        check(runProduct(input, output, report).exit == 0) { "tag-tamper baseline protection failed" }
        check(run(
            listOf(
                "node", repository.resolve("tools/validation/create-m0-05-test-apks.mjs").toString(),
                output.toString(), mutations.toString(),
            ),
            Duration.ofMinutes(1),
            workingDirectory = repository,
        ).exit == 0 && Files.isRegularFile(tampered)) { "authenticated tag mutation failed" }
        credentials.sign(tools, tampered, signedTampered)
        val packageName = packageName(fixture.id)
        var launchExit = -1
        var events: List<String> = emptyList()
        try {
            adb.install(signedTampered)
            adb.clearLogcat()
            val launch = adb.launch(packageName)
            launchExit = launch.exit
            events = adb.observeEvents(packageName, fixture.expectedEvents, Duration.ofSeconds(3))
            check(events.none(fixture.expectedEvents::contains)) {
                "authenticated tag tamper reached business events: $events"
            }
        } finally {
            adb.uninstall(packageName)
        }
        return linkedMapOf(
            "status" to "pass",
            "launch_exit" to launchExit,
            "observed_events" to events,
            "business_events_reached" to false,
            "package_cleanup" to !adb.isInstalled(packageName),
            "tampered_apk_sha256" to sha256(signedTampered),
        )
    }

    private fun runProduct(
        input: Path,
        output: Path,
        report: Path,
        timeout: Duration = Duration.ofMinutes(2),
    ): Result {
        val java = Path.of(System.getProperty("java.home")).resolve("bin").resolve(if (isWindows()) "java.exe" else "java")
        return run(
            listOf(
                java.toString(), "-cp", System.getProperty("java.class.path"), "ah.host.cli.CliMain",
                "protect", "--input", input.toString(), "--output", output.toString(), "--report", report.toString(),
            ),
            timeout,
            allowFailure = true,
        )
    }

    private fun signerDigest(tools: Tools, apk: Path): String {
        val result = run(listOf(tools.apksigner.toString(), "verify", "--print-certs", apk.toString()), Duration.ofSeconds(30))
        return Regex("(?i)Signer #1 certificate SHA-256 digest: ([0-9a-f]{64})")
            .find(result.output)?.groupValues?.get(1)?.lowercase()
            ?: error("apksigner did not print one SHA-256 signer digest")
    }

    private fun packageName(id: String): String = "ah.fixtures.android.m301.${packageSuffix.getValue(id)}"

    private data class Credentials(val keystore: Path, val alias: String, val password: String) {
        fun sign(tools: Tools, input: Path, output: Path) {
            Files.createDirectories(output.parent)
            val command = listOf(
                tools.apksigner.toString(), "sign", "--ks", keystore.toString(), "--ks-key-alias", alias,
                "--ks-pass", "pass:$password", "--key-pass", "pass:$password", "--out", output.toString(), input.toString(),
            )
            check(run(command, Duration.ofMinutes(1)).exit == 0) { "external fixture signing failed" }
        }

        companion object {
            fun create(tools: Tools, keystore: Path, alias: String): Credentials {
                Files.createDirectories(keystore.parent)
                val passwordBytes = ByteArray(18).also(SecureRandom()::nextBytes)
                val password = passwordBytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }
                passwordBytes.fill(0)
                val command = listOf(
                    tools.keytool.toString(), "-genkeypair", "-noprompt", "-keystore", keystore.toString(),
                    "-storepass", password, "-keypass", password, "-alias", alias, "-keyalg", "RSA", "-keysize", "3072",
                    "-validity", "2", "-dname", "CN=M3-01 Synthetic Fixture,O=androidAppHardening,C=XX",
                )
                check(run(command, Duration.ofMinutes(1)).exit == 0) { "ephemeral key generation failed" }
                return Credentials(keystore, alias, password)
            }
        }
    }

    private data class Tools(val adb: Path, val apksigner: Path, val keytool: Path) {
        companion object {
            fun find(): Tools {
                val sdk = sequenceOf(System.getenv("ANDROID_HOME"), System.getenv("ANDROID_SDK_ROOT"))
                    .filterNotNull().map(Path::of).firstOrNull(Files::isDirectory)
                    ?: error("pinned Android SDK is unavailable")
                val suffix = if (isWindows()) ".bat" else ""
                val executable = if (isWindows()) ".exe" else ""
                val apksigner = sdk.resolve("build-tools/$TOOL_VERSION/apksigner$suffix")
                val adb = sdk.resolve("platform-tools/adb$executable")
                val keytool = Path.of(System.getProperty("java.home")).resolve("bin/keytool$executable")
                listOf(apksigner, adb, keytool).forEach { check(Files.isRegularFile(it)) { "required pinned tool missing: ${it.fileName}" } }
                return Tools(adb, apksigner, keytool)
            }
        }
    }

    private class Adb(private val tool: Path, private val expectedAbi: String?) {
        private var observedProcessFacts: Map<String, Any?>? = null

        fun deviceInfo(): Map<String, Any?> {
            val state = command("get-state")
            check(state.output.trim() == "device") { "exactly one authorized adb device is required" }
            return linkedMapOf<String, Any?>(
                "sdk" to shell("getprop", "ro.build.version.sdk").output.trim(),
                "reported_primary_abi" to abi(),
                "supported_abis" to shell("getprop", "ro.product.cpu.abilist").output.trim().split(',').filter(String::isNotBlank),
                "process" to observedProcessFacts,
            )
        }

        fun abi(): String = shell("getprop", "ro.product.cpu.abi").output.trim()

        fun install(apk: Path) {
            val arguments = mutableListOf("install", "--no-incremental", "-r", "-t")
            if (expectedAbi != null) arguments += listOf("--abi", expectedAbi)
            arguments += apk.toString()
            val result = command(
                *arguments.toTypedArray(),
                allowFailure = true,
                timeout = Duration.ofMinutes(2),
            )
            check(result.exit == 0 && "Success" in result.output) { "adb install failed: ${result.output.take(300)}" }
        }

        fun processFacts(packageName: String): Map<String, Any?> {
            val result = shell("content", "query", "--uri", "content://$packageName.events/events")
            fun field(name: String): String = Regex("(?:^|, )$name=([^,\\r\\n]+)")
                .find(result.output)?.groupValues?.get(1)?.trim()
                ?: error("fixture did not report $name")
            val facts = linkedMapOf<String, Any?>(
                "sdk" to field("sdk").toInt(),
                "process_abi" to field("process_abi"),
                "supported_abis" to field("supported_abis").split('|'),
                "is_64_bit" to field("is_64_bit").toBooleanStrict(),
            )
            if (expectedAbi != null) check(facts["process_abi"] == expectedAbi) {
                "expected process ABI $expectedAbi but Android reported ${facts["process_abi"]}"
            }
            observedProcessFacts?.let { check(it == facts) { "fixture process facts changed within one cell" } }
            observedProcessFacts = facts
            return facts
        }

        fun clearLogcat() {
            command("logcat", "-c", allowFailure = true)
        }

        fun start(packageName: String): Result {
            val result = shell(
                "am", "start", "-W", "-n", "$packageName/ah.fixtures.android.m301.FixtureActivity",
                allowFailure = true,
            )
            check(result.exit == 0 && "Starting: Intent" in result.output) { "fixture start failed: ${result.output.take(300)}" }
            return result
        }

        fun launch(packageName: String): Result =
            shell("am", "start", "-n", "$packageName/ah.fixtures.android.m301.FixtureActivity", allowFailure = true)

        fun observeEvents(packageName: String, expected: List<String>, duration: Duration): List<String> {
            Thread.sleep(duration.toMillis())
            return events(packageName)
        }

        fun awaitEvents(
            fixtureId: String,
            packageName: String,
            expected: List<String>,
            launch: Result,
        ): EventObservation {
            // `content query` starts the exported provider when the application process is not yet
            // ready. Polling it concurrently with `am start` can therefore create a second startup
            // on slower API 29 devices. `am start -W` supplies the synchronization boundary; allow
            // the remote worker process a bounded settle window, then observe exactly once.
            Thread.sleep(if ("worker.create" in expected) 1_000 else 250)
            val observed = events(packageName)
            matchDeviceEvents(shell("getprop", "ro.build.version.sdk").output.trim().toInt(), fixtureId, expected, observed)
                ?.let { return it }
            val diagnostics = focusedDiagnostics(packageName)
            error(
                "$packageName events did not reach $expected; observed=$observed; " +
                    "launch=${sanitize(launch.output).take(300)}; diagnostics=$diagnostics",
            )
        }

        private fun focusedDiagnostics(packageName: String): String {
            val pid = shell("pidof", packageName, allowFailure = true).output.trim()
            val logcat = command("logcat", "-d", "-t", "400", allowFailure = true).output
            val relevant = logcat.lineSequence().filter { line ->
                packageName in line || "AAH-RUNTIME" in line || "AndroidRuntime" in line ||
                    "FATAL EXCEPTION" in line || "ClassNotFoundException" in line ||
                    "UnsatisfiedLinkError" in line || "VerifyError" in line
            }.toList().takeLast(40).joinToString(" | ")
            return "pid=${sanitize(pid)} log=${sanitize(relevant).takeLast(6_000)}"
        }

        private fun sanitize(value: String): String = value
            .replace(Regex("(?i)\\b[0-9a-f]{64}\\b")) { it.value.take(12) + "<redacted>" }
            .replace(Regex("/(?:data|sdcard|storage|proc|system|vendor|product|apex|mnt)/[^\\s:;,]+"), "<device-path>")
            .replace(Regex("[\\r\\n]+"), " ")

        fun events(packageName: String): List<String> {
            val result = shell("content", "query", "--uri", "content://$packageName.events/events", allowFailure = true)
            if (result.exit != 0) return emptyList()
            return Regex("event=([a-z0-9_.-]+)").findAll(result.output).map { it.groupValues[1] }.toList()
        }

        fun uninstall(packageName: String) {
            command("uninstall", packageName, allowFailure = true, timeout = Duration.ofSeconds(30))
        }

        fun isInstalled(packageName: String): Boolean = shell("pm", "path", packageName, allowFailure = true).exit == 0

        fun shell(vararg arguments: String, allowFailure: Boolean = false): Result =
            command("shell", *arguments, allowFailure = allowFailure)

        private fun command(
            vararg arguments: String,
            allowFailure: Boolean = false,
            timeout: Duration = Duration.ofSeconds(30),
        ): Result = run(listOf(tool.toString()) + arguments, timeout, allowFailure)
    }

    internal data class EventObservation(
        val expected: List<String>,
        val observed: List<String>,
        val configurationRelaunch: Boolean,
    )

    internal fun matchDeviceEvents(
        apiLevel: Int,
        fixtureId: String,
        catalogExpected: List<String>,
        observed: List<String>,
    ): EventObservation? {
        if (observed == catalogExpected) return EventObservation(catalogExpected, observed, false)
        if (apiLevel != 29) return null
        val relaunchedExpected = catalogExpected + activityRelaunchEvents(fixtureId)
        return if (observed == relaunchedExpected) EventObservation(relaunchedExpected, observed, true) else null
    }

    private fun activityRelaunchEvents(fixtureId: String): List<String> = when (fixtureId) {
        "kotlin-single-dex" -> listOf("activity.create", "kotlin.marker")
        "kotlin-multidex" -> listOf("activity.create", "kotlin.marker", "multidex.class")
        "jni-four-abi", "jni-arm-only" -> listOf("activity.create", "jni.marker")
        "java-single-dex", "custom-application", "custom-factory", "startup-provider", "multi-process" ->
            listOf("activity.create")
        else -> error("unknown fixture for configuration relaunch: $fixtureId")
    }

    private data class Result(val exit: Int, val output: String)

    private fun run(
        command: List<String>,
        timeout: Duration,
        allowFailure: Boolean = false,
        workingDirectory: Path? = null,
    ): Result {
        val actual = if (isWindows() && command.first().endsWith(".bat", ignoreCase = true)) {
            listOf("cmd.exe", "/d", "/c") + command
        } else command
        val builder = ProcessBuilder(actual).redirectErrorStream(true)
        workingDirectory?.let { builder.directory(it.toFile()) }
        val process = builder.start()
        val captured = ByteArrayOutputStream()
        val readFailure = AtomicReference<Throwable?>()
        val reader = Thread({
            try {
                process.inputStream.use { input ->
                    val buffer = ByteArray(16 * 1024)
                    var retained = 0
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        val copy = minOf(count, 1024 * 1024 - retained)
                        if (copy > 0) {
                            captured.write(buffer, 0, copy)
                            retained += copy
                        }
                    }
                }
            } catch (failure: Throwable) {
                readFailure.set(failure)
            }
        }, "m301-command-output")
        reader.isDaemon = true
        reader.start()
        val finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)
        if (!finished) {
            process.destroyForcibly()
            process.waitFor()
            reader.join(5_000)
            error("bounded command timed out: ${command.first().substringAfterLast('\\')}")
        }
        reader.join(5_000)
        check(!reader.isAlive) { "command output reader did not terminate" }
        readFailure.get()?.let { throw IllegalStateException("command output read failed", it) }
        val output = captured.toString(StandardCharsets.UTF_8)
        val result = Result(process.exitValue(), output)
        if (!allowFailure) check(result.exit == 0) { "command failed (${result.exit}): ${output.take(500)}" }
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

    private object Json {
        fun write(value: Any?): String = when (value) {
            null -> "null"
            is Boolean, is Number -> value.toString()
            is String -> "\"${escape(value)}\""
            is Map<*, *> -> value.entries.joinToString(prefix = "{", postfix = "}", separator = ",") { write(it.key.toString()) + ":" + write(it.value) }
            is Iterable<*> -> value.joinToString(prefix = "[", postfix = "]", separator = ",", transform = ::write)
            else -> error("unsupported JSON value ${value::class.java.name}")
        }

        private fun escape(value: String): String = buildString {
            value.forEach { character ->
                when (character) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> if (character.code < 0x20) append("\\u%04x".format(character.code)) else append(character)
                }
            }
        }
    }
}
