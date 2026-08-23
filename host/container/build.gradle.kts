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

    @get:Input
    abstract val originalFactory: Property<String>

    override fun asArguments(): Iterable<String> =
        listOf(
            primaryDex.get(),
            secondaryDex.get(),
            outputRoot.get(),
            packageName.get(),
            signerSha256.get(),
            originalFactory.get(),
        )
}

abstract class M310VerifyArguments : CommandLineArgumentProvider {
    @get:Input abstract val originalBaseline: Property<String>
    @get:Input abstract val originalProtected: Property<String>
    @get:Input abstract val profileBaseline: Property<String>
    @get:Input abstract val profileProtected: Property<String>
    @get:Input abstract val observerDex: Property<String>
    @get:Input abstract val derivationManifest: Property<String>
    @get:Input abstract val profileLock: Property<String>
    @get:Input abstract val report: Property<String>

    override fun asArguments(): Iterable<String> = listOf(
        originalBaseline.get(), originalProtected.get(), profileBaseline.get(), profileProtected.get(),
        observerDex.get(), derivationManifest.get(), profileLock.get(), report.get(),
    )
}

dependencies {
    implementation(project(":host:apk-inspector"))
    testImplementation(libs.dexlib2)
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
            originalFactory.set(providers.gradleProperty("m202OriginalFactory").orElse("-"))
        },
    )
}

val m310VerifyProfiles by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Verifies retained M3-10 profile APKs for the M3-14 successor without regeneration."
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("ah.host.container.M310CanonicalProfileVerifier")
    argumentProviders.add(
        objects.newInstance(M310VerifyArguments::class).apply {
            originalBaseline.set(providers.environmentVariable("M310_ORIGINAL_BASELINE"))
            originalProtected.set(providers.environmentVariable("M310_ORIGINAL_PROTECTED"))
            profileBaseline.set(providers.environmentVariable("M310_PROFILE_BASELINE"))
            profileProtected.set(providers.environmentVariable("M310_PROFILE_PROTECTED"))
            observerDex.set(providers.environmentVariable("M310_OBSERVER_DEX"))
            derivationManifest.set(providers.environmentVariable("M310_DERIVATION_MANIFEST"))
            profileLock.set(providers.environmentVariable("M310_PROFILE_LOCK"))
            report.set(providers.environmentVariable("M310_VERIFICATION_REPORT"))
        },
    )
}

val m310MetadataSelfTest by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Rejects M3-10 try-handler and debug-position/value metadata mutations."
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("ah.host.container.M310CanonicalProfileVerifier")
    args("--metadata-self-test")
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
