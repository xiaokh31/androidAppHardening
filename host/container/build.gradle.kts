import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.process.CommandLineArgumentProvider

plugins {
    alias(libs.plugins.kotlin.jvm)
}

abstract class M202DeviceVectorArguments : CommandLineArgumentProvider {
    @get:Input
    abstract val primaryDex: Property<String>

    @get:Input
    abstract val secondaryDex: Property<String>

    @get:Input
    abstract val outputRoot: Property<String>

    @get:Input
    abstract val packageName: Property<String>

    @get:Input
    abstract val signerSha256: Property<String>

    override fun asArguments(): Iterable<String> =
        listOf(
            primaryDex.get(),
            secondaryDex.get(),
            outputRoot.get(),
            packageName.get(),
            signerSha256.get(),
        )
}

dependencies {
    implementation(project(":host:apk-inspector"))
}

val containerTest by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs the M1-04 AHDC v2 vectors, round trips, tamper matrix, and cleanup checks."
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("ah.host.container.ContainerSelfTest")
    systemProperty("ah.container.reportDir", layout.buildDirectory.dir("reports/m1-04").get().asFile.absolutePath)
}

val prepareM202DeviceVector by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Builds an ignored M2-02 device vector from two synthetic DEX files."
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("ah.host.container.M202DeviceVectorMain")
    argumentProviders.add(
        objects.newInstance<M202DeviceVectorArguments>().apply {
            primaryDex.set(providers.gradleProperty("m202PrimaryDex"))
            secondaryDex.set(providers.gradleProperty("m202SecondaryDex"))
            outputRoot.set(providers.gradleProperty("m202VectorOutput"))
            packageName.set(providers.gradleProperty("m202PackageName"))
            signerSha256.set(providers.gradleProperty("m202SignerSha256"))
        },
    )
}

tasks.named<Test>("test") {
    dependsOn(containerTest)
    failOnNoDiscoveredTests = false
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        allWarningsAsErrors.set(true)
    }
}
