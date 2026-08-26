import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.testing.Test

plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

abstract class GenerateProductVersion : DefaultTask() {
    @get:Input
    abstract val productVersion: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val output = outputDirectory.file("ah/host/cli/ProductVersion.kt").get().asFile
        output.parentFile.mkdirs()
        output.writeText(
            "package ah.host.cli\n\ninternal object ProductVersion { const val VALUE = \"${productVersion.get()}\" }\n",
            Charsets.UTF_8,
        )
    }
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    implementation(project(":host:apk-inspector"))
    implementation(project(":host:axml"))
    implementation(project(":host:container"))
    implementation(project(":host:repacker"))
}

application {
    mainClass.set("ah.host.cli.CliMain")
    applicationName = "android-app-hardening"
}

val generatedProductVersion = layout.buildDirectory.dir("generated/sources/productVersion/kotlin")
val productVersionValue = rootProject.version.toString()
val generateProductVersion by tasks.registering(GenerateProductVersion::class) {
    description = "Generates the CLI/REPORT product version from the root project version."
    productVersion.set(productVersionValue)
    outputDirectory.set(generatedProductVersion)
}

kotlin.sourceSets.named("main") {
    kotlin.srcDir(generatedProductVersion)
}

tasks.named("compileKotlin") {
    dependsOn(generateProductVersion)
}

tasks.named<Jar>("jar") {
    archiveFileName.set("android-app-hardening-core.jar")
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

val cliTest by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs M1-06 parser, report schema, error mapping, path, cleanup, and capability tests."
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("ah.host.cli.CliSelfTest")
    systemProperty("ah.cli.reportDir", layout.buildDirectory.dir("reports/m1-06/unit").get().asFile.absolutePath)
}

val integrationTest by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs the M1-06 full-flow CLI against the repository-generated signed APK fixture."
    dependsOn(tasks.named("testClasses"), ":host:apk-inspector:signerPolicyTest")
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("ah.host.cli.CliIntegrationSelfTest")
    systemProperty("ah.cli.reportDir", layout.buildDirectory.dir("reports/m1-06/integration").get().asFile.absolutePath)
    systemProperty(
        "ah.cli.signedFixture",
        project(":host:apk-inspector").layout.buildDirectory.file("reports/m1-02/fixtures/combined.apk").get().asFile.absolutePath,
    )
}

tasks.named<Test>("test") {
    dependsOn(cliTest)
    failOnNoDiscoveredTests = false
}

tasks.named("check") {
    dependsOn(integrationTest)
}
