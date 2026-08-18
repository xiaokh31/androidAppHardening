package ah.integration.fixtures

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Properties
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile

object RuntimeBundleGenerator {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.isEmpty())
        val output = propertyPath("m301.runtimeBundle")
        val apksig = output.parent.parent.parent.resolve("intermediates/m3-01/apksig/apksig-9.3.0.jar")
        val work = output.resolveSibling("runtime-bundle-work")
        output.toFile().deleteRecursively()
        work.toFile().deleteRecursively()
        try {
            val runtimeRoot = output.resolve("ah/runtime")
            Files.createDirectories(runtimeRoot)
            val jars = listOf("m301.bootstrapAar", "m301.policyAar", "m301.nativeAar").mapIndexed { index, property ->
                val target = work.resolve("runtime-$index.jar")
                Files.createDirectories(target.parent)
                ZipFile(propertyPath(property).toFile()).use { zip ->
                    val entry = checkNotNull(zip.getEntry("classes.jar")) { "M3-01 runtime AAR is missing classes.jar" }
                    zip.getInputStream(entry).use { input ->
                        Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING)
                    }
                }
                target
            }
            val dexDirectory = work.resolve("d8")
            Files.createDirectories(dexDirectory)
            check(Files.isRegularFile(apksig)) { "M3-01 staged apksig dependency is missing" }
            runD8(jars + listOf(apksig), dexDirectory, work.resolve("d8-output.txt"))
            val dexFiles = Files.list(dexDirectory).use { entries ->
                entries.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".dex") }.toList()
            }
            check(dexFiles.size == 1) { "M3-01 runtime bootstrap must be one DEX, found ${dexFiles.size}" }
            val bootstrap = Files.readAllBytes(dexFiles.single())
            val bootstrapText = bootstrap.toString(Charsets.ISO_8859_1)
            listOf(
                "Lah/runtime/bootstrap/ShellAppComponentFactory;",
                "Lah/runtime/guard/RuntimeStartupGuard;",
                "Lah/runtime/loader/PayloadRuntime;",
                "Lcom/android/apksig/ApkVerifier;",
            ).forEach { descriptor ->
                check(bootstrapText.contains(descriptor)) { "M3-01 runtime bootstrap is missing $descriptor" }
            }
            val profileObserver = "Lah/runtime/profile/M210StartupTimingObserver;"
            if (System.getProperty("m210.requireObserver") == "true") {
                check(bootstrapText.contains(profileObserver)) {
                    "M2-10 profile runtime bootstrap is missing its test-only observer"
                }
            } else {
                check(!bootstrapText.contains(profileObserver)) {
                    "Release runtime bootstrap contains the M2-10 test-only observer"
                }
            }
            check(!bootstrapText.contains("Lah/fixtures/android/")) { "M3-01 runtime bootstrap contains fixture classes" }
            Files.write(runtimeRoot.resolve("bootstrap.dex"), bootstrap)

            val properties = Properties()
            properties["version"] = "1"
            properties["bootstrap.sha256"] = sha256(bootstrap)
            val templates = propertyPath("m301.runtimeTemplates")
            listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64").forEach { abi ->
                val source = templates.resolve("$abi/libah_runtime.so")
                check(Files.isRegularFile(source)) { "M3-01 runtime release lacks $abi template" }
                val bytes = Files.readAllBytes(source)
                val target = runtimeRoot.resolve("$abi/libah_runtime.so")
                Files.createDirectories(target.parent)
                Files.write(target, bytes)
                properties["$abi.sha256"] = sha256(bytes)
            }
            val lines = properties.stringPropertyNames().sorted()
                .joinToString("\n") { key -> "$key=${properties.getProperty(key)}" } + "\n"
            Files.writeString(runtimeRoot.resolve("runtime-bundle-v1.properties"), lines, Charsets.ISO_8859_1)
        } finally {
            work.toFile().deleteRecursively()
        }
    }

    private fun runD8(jars: List<Path>, output: Path, log: Path) {
        val arguments = listOf(
            javaExecutable().toString(),
            "-cp", propertyPath("m301.d8Jar").toString(),
            "com.android.tools.r8.D8",
            "--min-api", "29",
            "--lib", propertyPath("m301.androidJar").toString(),
            "--output", output.toString(),
        ) + jars.map(Path::toString)
        val process = ProcessBuilder(arguments)
            .redirectErrorStream(true)
            .redirectOutput(log.toFile())
            .start()
        if (!process.waitFor(2, TimeUnit.MINUTES)) {
            process.destroyForcibly()
            error("M3-01 D8 runtime bootstrap timed out")
        }
        if (process.exitValue() != 0) {
            error("M3-01 D8 runtime bootstrap failed: ${Files.readString(log).takeLast(2_000)}")
        }
    }

    private fun propertyPath(name: String): Path =
        Path.of(requireNotNull(System.getProperty(name))).toAbsolutePath().normalize()

    private fun javaExecutable(): Path {
        val executable = if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) "java.exe" else "java"
        return Path.of(System.getProperty("java.home"), "bin", executable).toAbsolutePath().normalize()
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
