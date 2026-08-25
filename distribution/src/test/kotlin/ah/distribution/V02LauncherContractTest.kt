package ah.distribution

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

object V02LauncherContractTest {
    @JvmStatic
    fun main(args: Array<String>) {
        val repository = Path.of(requireNotNull(System.getProperty("ah.distribution.repo")))
        val windows = repository.resolve("distribution/src/main/resources/v0.2/windows/android-app-hardening.cmd")
        val ubuntu = repository.resolve("distribution/src/main/resources/v0.2/ubuntu/android-app-hardening")
        val quickstart = repository.resolve("distribution/docs/QUICKSTART.md")
        val winText = Files.readString(windows)
        val ubuntuText = Files.readString(ubuntu)
        val quickstartText = Files.readString(quickstart)
        val forbidden = listOf("curl ", "wget ", "Invoke-WebRequest", "jarsigner", "apksigner", "keytool", "keystore")
        forbidden.forEach { token ->
            check(!winText.contains(token, ignoreCase = true))
            check(!ubuntuText.contains(token, ignoreCase = true))
        }
        for (required in listOf("Eclipse Adoptium", "17.0.19", "17.0.19+10", "CliMain")) {
            check(required in winText && required in ubuntuText)
        }
        check("offline" in quickstartText && "unsigned" in quickstartText && "minSdk >= 29" in quickstartText)
        check("0.1.0-dev" !in winText && "0.1.0-dev" !in ubuntuText && "0.1.0-dev" !in quickstartText)

        val root = Files.createTempDirectory("v02-launcher-")
        try {
            val signerFixture = repository.resolve("host/apk-inspector/build/reports/m1-02/fixtures/combined.apk")
            val signedInput = buildValidSignedFixture(root.resolve("fixture-build"), signerFixture)
            if (System.getProperty("os.name").startsWith("Windows")) {
                testWindows(root, windows, signedInput)
            } else {
                testUbuntu(root, ubuntu, signedInput)
            }
        } finally {
            root.toFile().deleteRecursively()
        }
        println("V2-M0-02 launcher contract self-test PASS")
    }

    private fun testWindows(root: Path, launcherSource: Path, signedInput: Path) {
        val bin = root.resolve("bin").also(Files::createDirectories)
        val lib = root.resolve("lib").also(Files::createDirectories)
        Files.copy(launcherSource, bin.resolve("android-app-hardening.cmd"))
        val repository = Path.of(requireNotNull(System.getProperty("ah.distribution.repo")))
        Files.copy(repository.resolve("distribution/build/v0.2/host/android-app-hardening.jar"), lib.resolve("android-app-hardening.jar"))
        val javaBin = Path.of(System.getProperty("java.home")).resolve("bin")
        val launcher = bin.resolve("android-app-hardening.cmd")
        val command = { arguments: List<String> -> windowsLauncherCommand(launcher, arguments) }
        val result = run(command(listOf("--version")), javaBin)
        check(result.first == 0 && result.second == "android-app-hardening 0.2.0\r\n" && result.third.isEmpty()) { result.toString() }

        val fakeHome = root.resolve("fake-java-home")
        fakeHome.resolve("bin").also(Files::createDirectories)
        val systemRoot = requireNotNull(System.getenv("SystemRoot"))
        val systemBin = Path.of(systemRoot, "System32")
        val wrong = run(
            command(listOf("--version")),
            systemBin,
            javaHome = fakeHome,
            replacePath = true,
        )
        check(wrong.first == 78 && wrong.second.isEmpty() && "requires Eclipse Temurin 17.0.19+10" in wrong.third) {
            wrong.toString()
        }
        protectSmoke(
            root,
            command,
            javaBin,
            signedInput,
        )
    }

    private fun testUbuntu(root: Path, launcherSource: Path, signedInput: Path) {
        val bin = root.resolve("bin").also(Files::createDirectories)
        val lib = root.resolve("lib").also(Files::createDirectories)
        val launcher = bin.resolve("android-app-hardening").also { Files.copy(launcherSource, it) }
        launcher.toFile().setExecutable(true, false)
        val repository = Path.of(requireNotNull(System.getProperty("ah.distribution.repo")))
        Files.copy(repository.resolve("distribution/build/v0.2/host/android-app-hardening.jar"), lib.resolve("android-app-hardening.jar"))
        val javaBin = Path.of(System.getProperty("java.home")).resolve("bin")
        val command = { arguments: List<String> -> listOf(launcher.toString()) + arguments }
        val result = run(command(listOf("--version")), javaBin)
        check(result.first == 0 && result.second == "android-app-hardening 0.2.0\n" && result.third.isEmpty())

        val fakeHome = root.resolve("fake-java-home")
        val fake = fakeHome.resolve("bin").also(Files::createDirectories)
        val java = fake.resolve("java")
        Files.writeString(java, "#!/bin/sh\necho '    java.vendor = Wrong Vendor' >&2\nexit 0\n")
        java.toFile().setExecutable(true, false)
        val wrong = run(listOf(launcher.toString(), "--version"), fake, javaHome = fakeHome)
        check(wrong.first == 78)
        protectSmoke(root, command, javaBin, signedInput)
    }

    private fun windowsLauncherCommand(launcher: Path, arguments: List<String>): List<String> {
        val commandLine = (listOf(launcher.toString()) + arguments).joinToString(" ") { value ->
            require(value.none { it == '\u0000' || it == '\r' || it == '\n' || it == '"' })
            "\"$value\""
        }
        return listOf("cmd.exe", "/d", "/s", "/c", "\"$commandLine\"")
    }

    private fun protectSmoke(
        root: Path,
        launcher: (List<String>) -> List<String>,
        javaBin: Path,
        signedInput: Path,
    ) {
        val smoke = root.resolve("offline protect smoke").also(Files::createDirectories)
        val input = smoke.resolve("输入 signed fixture.apk")
        Files.copy(signedInput, input)
        val inputHash = sha256(input)
        val output = smoke.resolve("output unsigned.apk")
        val report = smoke.resolve("report result.json")
        val result = run(
            launcher(listOf(
                "protect", "--input", input.toString(), "--output", output.toString(), "--report", report.toString(),
            )),
            javaBin,
        )
        check(
            result.first == 0 && result.second.isEmpty() &&
                (result.third == "success/NONE/${report.fileName}\n" ||
                    result.third == "success/NONE/${report.fileName}\r\n"),
        ) { result.toString() }
        check(Files.isRegularFile(output) && Files.isRegularFile(report) && sha256(input) == inputHash)
        val reportText = Files.readString(report)
        check("\"version\": \"0.2.0\"" in reportText && "\"status\": \"success\"" in reportText)
        check("\"performed\": false" in reportText)
        check(runTool(androidTool("apksigner"), "verify", "--verbose", output.toString()).first != 0)
        check(Files.list(smoke).use { entries ->
            entries.noneMatch { path -> path.fileName.toString().endsWith(".tmp") || path.fileName.toString().endsWith(".work") }
        })
    }

    private fun buildValidSignedFixture(root: Path, signerFixture: Path): Path {
        check(Files.isRegularFile(signerFixture))
        Files.createDirectories(root)
        val manifest = root.resolve("AndroidManifest.xml")
        Files.writeString(
            manifest,
            """<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="ah.fixtures.v02"><uses-sdk android:minSdkVersion="29" android:targetSdkVersion="36"/><application android:name=".FixtureApplication" android:appComponentFactory=".FixtureFactory"/></manifest>""",
            StandardCharsets.UTF_8,
        )
        val linked = root.resolve("linked.apk")
        val link = runTool(
            androidTool("aapt2"), "link", "-o", linked.toString(), "--manifest", manifest.toString(),
            "-I", androidSdk().resolve("platforms/android-36/android.jar").toString(),
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
            androidTool("apksigner"), "sign", "--key", fixtures.resolve("current-key.pk8").toString(),
            "--cert", fixtures.resolve("current-cert.der").toString(), "--v1-signing-enabled", "true",
            "--v2-signing-enabled", "true", "--v3-signing-enabled", "true", "--out", signed.toString(),
            unsigned.toString(),
        )
        check(sign.first == 0) { "apksigner sign failed: ${sign.second}" }
        return signed
    }

    private fun copyEntries(source: Path, output: ZipOutputStream, include: (String) -> Boolean) {
        ZipFile(source.toFile()).use { zip ->
            zip.entries().asSequence().filter { !it.isDirectory && include(it.name) }.forEach { entry ->
                output.putNextEntry(ZipEntry(entry.name).apply { time = 0L })
                zip.getInputStream(entry).use { input -> input.copyTo(output) }
                output.closeEntry()
            }
        }
    }

    private fun androidSdk(): Path = sequenceOf(System.getenv("ANDROID_HOME"), System.getenv("ANDROID_SDK_ROOT"))
        .filterNotNull().map(Path::of).firstOrNull { Files.isDirectory(it) }
        ?: error("pinned Android SDK environment missing")

    private fun androidTool(name: String): Path {
        val windows = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
        val extension = if (windows) if (name == "apksigner") ".bat" else ".exe" else ""
        return androidSdk().resolve("build-tools/36.1.0/$name$extension").also { check(Files.isRegularFile(it)) }
    }

    private fun runTool(tool: Path, vararg arguments: String): Pair<Int, String> {
        val windowsBatch = System.getProperty("os.name").startsWith("Windows", ignoreCase = true) &&
            tool.toString().endsWith(".bat")
        val command = if (windowsBatch) listOf("cmd.exe", "/d", "/c", tool.toString()) + arguments else
            listOf(tool.toString()) + arguments
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        return process.waitFor() to output
    }

    private fun sha256(path: Path): String = MessageDigest.getInstance("SHA-256")
        .digest(Files.readAllBytes(path)).joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private fun run(
        command: List<String>,
        fakePath: Path,
        javaHome: Path? = null,
        replacePath: Boolean = false,
    ): Triple<Int, String, String> {
        val process = ProcessBuilder(command).apply {
            val pathKey = environment().keys.singleOrNull { it.equals("PATH", ignoreCase = true) } ?: "PATH"
            val inheritedPath = environment()[pathKey].orEmpty()
            environment()[pathKey] = if (replacePath) {
                fakePath.toString()
            } else {
                fakePath.toString() + java.io.File.pathSeparator + inheritedPath
            }
            if (javaHome != null) {
                val javaHomeKey = environment().keys.singleOrNull { it.equals("JAVA_HOME", ignoreCase = true) } ?: "JAVA_HOME"
                environment()[javaHomeKey] = javaHome.toString()
            }
        }.start()
        val stdout = process.inputStream.readAllBytes().toString(StandardCharsets.UTF_8)
        val stderr = process.errorStream.readAllBytes().toString(StandardCharsets.UTF_8)
        check(process.waitFor(15, TimeUnit.SECONDS))
        return Triple(process.exitValue(), stdout, stderr)
    }
}
