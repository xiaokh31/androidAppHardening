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

abstract class M310DexArguments : CommandLineArgumentProvider {
    @get:Input
    abstract val inputDex: Property<String>

    @get:Input
    abstract val outputDex: Property<String>

    override fun asArguments(): Iterable<String> = listOf(inputDex.get(), outputDex.get())
}

abstract class M310ProfileArguments : CommandLineArgumentProvider {
    @get:Input
    abstract val mode: Property<String>

    @get:Input
    abstract val inputDex: Property<String>

    @get:Input
    abstract val observerDex: Property<String>

    @get:Input
    abstract val outputDex: Property<String>

    override fun asArguments(): Iterable<String> =
        listOf(mode.get(), inputDex.get(), observerDex.get(), outputDex.get())
}

abstract class M310CanonicalArguments : CommandLineArgumentProvider {
    @get:Input abstract val baselineApk: Property<String>
    @get:Input abstract val protectedApk: Property<String>
    @get:Input abstract val observerDex: Property<String>
    @get:Input abstract val secretSeed: Property<String>
    @get:Input abstract val signerSha256: Property<String>
    @get:Input abstract val outputDirectory: Property<String>

    override fun asArguments(): Iterable<String> = listOf(
        baselineApk.get(), protectedApk.get(), observerDex.get(), secretSeed.get(),
        signerSha256.get(), outputDirectory.get(),
    )
}

abstract class M310VerifyArguments : CommandLineArgumentProvider {
    @get:Input abstract val originalBaseline: Property<String>
    @get:Input abstract val originalProtected: Property<String>
    @get:Input abstract val profileBaseline: Property<String>
    @get:Input abstract val profileProtected: Property<String>
    @get:Input abstract val observerDex: Property<String>
    @get:Input abstract val report: Property<String>

    override fun asArguments(): Iterable<String> = listOf(
        originalBaseline.get(), originalProtected.get(), profileBaseline.get(), profileProtected.get(),
        observerDex.get(), report.get(),
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

val m310DexSmoke by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Proves the pinned test-only DEX rewriter can parse and deterministically rewrite canonical DEX files."
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("ah.host.container.M310DexSmoke")
    argumentProviders.add(
        objects.newInstance(M310DexArguments::class).apply {
            inputDex.set(providers.gradleProperty("m310DexInput"))
            outputDex.set(providers.gradleProperty("m310DexOutput"))
        },
    )
}

val m310DexProfile by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Derives one M3-10 profile DEX from an exact canonical DEX and the fixed observer DEX."
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("ah.host.container.M310DexProfileTool")
    argumentProviders.add(
        objects.newInstance(M310ProfileArguments::class).apply {
            mode.set(providers.gradleProperty("m310Mode"))
            inputDex.set(providers.gradleProperty("m310DexInput"))
            observerDex.set(providers.gradleProperty("m310ObserverDex"))
            outputDex.set(providers.gradleProperty("m310DexOutput"))
        },
    )
}

val m310CanonicalProfiles by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Derives the two unsigned M3-10 profiles from the exact M3-11 canonical APK pair."
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("ah.host.container.M310CanonicalProfileDeriver")
    argumentProviders.add(
        objects.newInstance(M310CanonicalArguments::class).apply {
            baselineApk.set(providers.gradleProperty("m310BaselineApk"))
            protectedApk.set(providers.gradleProperty("m310ProtectedApk"))
            observerDex.set(providers.gradleProperty("m310ObserverDex"))
            secretSeed.set(providers.gradleProperty("m310SecretSeed"))
            signerSha256.set(providers.gradleProperty("m310SignerSha256"))
            outputDirectory.set(providers.gradleProperty("m310OutputDirectory"))
        },
    )
}

val m310VerifyProfiles by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Verifies four actual M3-10 APKs, exact DEX probes, v3 signer, container and share-slot semantics."
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("ah.host.container.M310CanonicalProfileVerifier")
    argumentProviders.add(
        objects.newInstance(M310VerifyArguments::class).apply {
            originalBaseline.set(providers.gradleProperty("m310OriginalBaseline"))
            originalProtected.set(providers.gradleProperty("m310OriginalProtected"))
            profileBaseline.set(providers.gradleProperty("m310ProfileBaseline"))
            profileProtected.set(providers.gradleProperty("m310ProfileProtected"))
            observerDex.set(providers.gradleProperty("m310ObserverDex"))
            report.set(providers.gradleProperty("m310VerificationReport"))
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
