import java.security.MessageDigest
import java.util.Properties
import java.util.zip.ZipFile
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.testing.Test
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilerExecutionStrategy

plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        allWarningsAsErrors.set(true)
    }
}

tasks.withType<KotlinCompile>().configureEach {
    compilerExecutionStrategy.set(KotlinCompilerExecutionStrategy.IN_PROCESS)
}

dependencies {
    implementation(project(":host:cli"))
}

@CacheableTask
abstract class GenerateM301RuntimeBundle : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceApkDirectory: DirectoryProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val runtimeTemplateDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val apks = sourceApkDirectory.get().asFile.listFiles { file -> file.isFile && file.extension == "apk" }?.toList().orEmpty()
        if (apks.size != 1) throw GradleException("M3-01 expected one M2-03 runtime source APK, found ${apks.size}")
        val root = outputDirectory.get().asFile.resolve("ah/runtime")
        root.deleteRecursively()
        root.mkdirs()
        val properties = Properties()
        properties["version"] = "1"
        ZipFile(apks.single()).use { zip ->
            val dexEntries = zip.entries().asSequence().filter { !it.isDirectory && it.name.matches(Regex("classes(?:[2-9][0-9]*)?\\.dex")) }.toList()
            if (dexEntries.size != 1) throw GradleException("M3-01 runtime bootstrap must be one DEX, found ${dexEntries.size}")
            val bootstrap = zip.getInputStream(dexEntries.single()).use { it.readBytes() }
            root.resolve("bootstrap.dex").writeBytes(bootstrap)
            properties["bootstrap.sha256"] = sha256(bootstrap)
            listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64").forEach { abi ->
                val template = runtimeTemplateDirectory.get().asFile.resolve("$abi/libah_runtime.so")
                if (!template.isFile) throw GradleException("M3-01 runtime release lacks $abi template")
                val bytes = template.readBytes()
                val target = root.resolve("$abi/libah_runtime.so")
                target.parentFile.mkdirs()
                target.writeBytes(bytes)
                properties["$abi.sha256"] = sha256(bytes)
            }
        }
        root.resolve("runtime-bundle-v1.properties").outputStream().use { output ->
            val lines = properties.stringPropertyNames().sorted().joinToString("\n") { key -> "$key=${properties.getProperty(key)}" } + "\n"
            output.write(lines.toByteArray(Charsets.ISO_8859_1))
        }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

val generatedRuntimeBundle = layout.buildDirectory.dir("generated/m3-01/runtime-bundle")

val generateM301RuntimeBundle by tasks.registering(GenerateM301RuntimeBundle::class) {
    dependsOn(":fixtures:android:assembleM203DirectRelease", ":runtime:native:stageM204RuntimeTemplates")
    sourceApkDirectory.set(project(":fixtures:android").layout.buildDirectory.dir("outputs/apk/m203Direct/release"))
    runtimeTemplateDirectory.set(
        project(":runtime:native").layout.buildDirectory.dir("intermediates/stripped_native_libs/release/out/lib"),
    )
    outputDirectory.set(generatedRuntimeBundle)
}

val fixtureContractTest by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Validates the M3-01 catalog and all nine unsigned fixture artifacts."
    dependsOn(tasks.named("classes"), ":fixtures:android:assembleFixtures")
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("ah.integration.fixtures.FixtureCatalogSelfTest")
    systemProperty("m301.root", rootProject.layout.projectDirectory.asFile.absolutePath)
}

tasks.named<Test>("test") {
    dependsOn(fixtureContractTest)
    failOnNoDiscoveredTests = false
}

tasks.register<JavaExec>("runFixtureMatrix") {
    group = "verification"
    description = "Protects, externally signs, installs, verifies, and cleans the nine M3-01 fixtures."
    dependsOn(tasks.named("classes"), tasks.named("test"), generateM301RuntimeBundle)
    classpath = sourceSets["main"].runtimeClasspath + files(generatedRuntimeBundle)
    mainClass.set("ah.integration.fixtures.FixtureDriver")
    systemProperty("m301.root", rootProject.layout.projectDirectory.asFile.absolutePath)
    systemProperty("m301.report", layout.buildDirectory.file("reports/fixture-results.json").get().asFile.absolutePath)
    systemProperty("m301.signing", layout.buildDirectory.dir("test-signing").get().asFile.absolutePath)
    systemProperty("m301.work", layout.buildDirectory.dir("fixture-matrix").get().asFile.absolutePath)
    providers.gradleProperty("m301Case").orNull?.let { systemProperty("m301.case", it) }
}

tasks.register<JavaExec>("runFixtureHostMatrix") {
    group = "verification"
    description = "Runs the nine M3-01 signing and product-flow cases without a device."
    dependsOn(tasks.named("classes"), tasks.named("test"), generateM301RuntimeBundle)
    classpath = sourceSets["main"].runtimeClasspath + files(generatedRuntimeBundle)
    mainClass.set("ah.integration.fixtures.FixtureDriver")
    systemProperty("m301.root", rootProject.layout.projectDirectory.asFile.absolutePath)
    systemProperty("m301.report", layout.buildDirectory.file("reports/fixture-host-results.json").get().asFile.absolutePath)
    systemProperty("m301.signing", layout.buildDirectory.dir("test-signing").get().asFile.absolutePath)
    systemProperty("m301.work", layout.buildDirectory.dir("fixture-matrix").get().asFile.absolutePath)
    systemProperty("m301.hostOnly", "true")
    providers.gradleProperty("m301Case").orNull?.let { systemProperty("m301.case", it) }
}

tasks.named("check") {
    dependsOn(tasks.named("test"))
}
