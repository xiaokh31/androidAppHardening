import java.io.File
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

val m301Apksig by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}

dependencies {
    implementation(project(":host:cli"))
    add(m301Apksig.name, libs.android.apksig)
}

val generatedRuntimeBundle = layout.buildDirectory.dir("generated/m3-01/runtime-bundle")
val androidHome = providers.environmentVariable("ANDROID_HOME")
    .orElse(providers.environmentVariable("ANDROID_SDK_ROOT"))
val bootstrapAar = project(":runtime:bootstrap").layout.buildDirectory.file("outputs/aar/bootstrap-release.aar")
val policyAar = project(":runtime:policy").layout.buildDirectory.file("outputs/aar/policy-release.aar")
val nativeAar = project(":runtime:native").layout.buildDirectory.file("outputs/aar/native-release.aar")
val stagedApksigDirectory = layout.buildDirectory.dir("intermediates/m3-01/apksig")
val stagedApksig = stagedApksigDirectory.map { it.file("apksig-9.3.0.jar") }
val stageM301Apksig by tasks.registering(Sync::class) {
    from(m301Apksig)
    into(stagedApksigDirectory)
    rename { "apksig-9.3.0.jar" }
}
val runtimeTemplates = project(":runtime:native").layout.buildDirectory.dir("intermediates/stripped_native_libs/release/out/lib")
val d8Jar = layout.file(androidHome.map { File(it, "build-tools/36.1.0/lib/d8.jar") })
val androidJar = layout.file(androidHome.map { File(it, "platforms/android-36/android.jar") })

val generateM301RuntimeBundle by tasks.registering(JavaExec::class) {
    dependsOn(
        tasks.named("classes"),
        stageM301Apksig,
        ":runtime:bootstrap:assembleRelease",
        ":runtime:policy:assembleRelease",
        ":runtime:native:assembleRelease",
        ":runtime:native:stageM204RuntimeTemplates",
    )
    inputs.files(bootstrapAar, policyAar, nativeAar, stagedApksig, d8Jar, androidJar)
    inputs.dir(runtimeTemplates)
    outputs.dir(generatedRuntimeBundle)
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("ah.integration.fixtures.RuntimeBundleGenerator")
    systemProperty("m301.bootstrapAar", bootstrapAar.get().asFile.absolutePath)
    systemProperty("m301.policyAar", policyAar.get().asFile.absolutePath)
    systemProperty("m301.nativeAar", nativeAar.get().asFile.absolutePath)
    systemProperty("m301.d8Jar", d8Jar.get().asFile.absolutePath)
    systemProperty("m301.androidJar", androidJar.get().asFile.absolutePath)
    systemProperty("m301.runtimeTemplates", runtimeTemplates.get().asFile.absolutePath)
    systemProperty("m301.runtimeBundle", generatedRuntimeBundle.get().asFile.absolutePath)
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
    providers.gradleProperty("m304ExpectedAbi").orNull?.let { systemProperty("m304.expectedAbi", it) }
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

tasks.register<JavaExec>("prepareM305AndroidBenchmark") {
    group = "verification"
    description = "Protects, externally signs, and installs the three fixed M3-05 benchmark targets."
    dependsOn(tasks.named("classes"), tasks.named("test"), generateM301RuntimeBundle)
    classpath = sourceSets["main"].runtimeClasspath + files(generatedRuntimeBundle)
    mainClass.set("ah.integration.fixtures.FixtureDriver")
    systemProperty("m301.root", rootProject.layout.projectDirectory.asFile.absolutePath)
    systemProperty("m301.report", rootProject.layout.buildDirectory.file("m3-05/android-preparation.json").get().asFile.absolutePath)
    systemProperty("m301.signing", layout.buildDirectory.dir("m3-05-test-signing").get().asFile.absolutePath)
    systemProperty("m301.work", layout.buildDirectory.dir("m3-05-device-targets").get().asFile.absolutePath)
    systemProperty("m301.case", "java-single-dex,kotlin-multidex,jni-four-abi")
    systemProperty("m301.hostOnly", "true")
    systemProperty("m303.skipNegatives", "true")
}

tasks.register("prepareAndroidPerformanceBenchmark") {
    group = "verification"
    description = "Stable dependency alias for the Android performance harness."
    dependsOn("prepareM305AndroidBenchmark")
}

val m303Seed = rootProject.layout.buildDirectory.dir("equivalence/input-corpus")
val m303Inputs = providers.gradleProperty("m303InputCorpus")
    .map { rootProject.layout.projectDirectory.dir(it) }
    .orElse(m303Seed)
val m303Runtime = providers.gradleProperty("m303RuntimeBundle")
    .map { rootProject.layout.projectDirectory.dir(it) }
    .orElse(generatedRuntimeBundle)
val m303Platform = providers.gradleProperty("m303Platform").orElse(
    providers.systemProperty("os.name").map { if (it.lowercase().contains("windows")) "windows" else "ubuntu" },
)
val m303Output = m303Platform.map { rootProject.layout.buildDirectory.dir("equivalence/$it").get() }

tasks.register<JavaExec>("prepareCrossPlatformInputs") {
    group = "verification"
    description = "Builds the fixed synthetic signed-input corpus for M3-03."
    dependsOn(tasks.named("classes"), ":fixtures:android:assembleFixtures")
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("ah.integration.equivalence.CrossPlatformCorpus")
    systemProperty("m303.mode", "seed")
    systemProperty("m303.root", rootProject.layout.projectDirectory.asFile.absolutePath)
    systemProperty("m303.output", m303Seed.get().asFile.absolutePath)
    outputs.dir(m303Seed)
}

val generateCrossPlatformCorpus by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs two randomized full-flow M3-03 corpus passes on the current host."
    dependsOn(tasks.named("classes"))
    if (!providers.gradleProperty("m303InputCorpus").isPresent) dependsOn(tasks.named("prepareCrossPlatformInputs"))
    if (!providers.gradleProperty("m303RuntimeBundle").isPresent) dependsOn(generateM301RuntimeBundle)
    classpath = sourceSets["main"].runtimeClasspath + files(m303Runtime)
    mainClass.set("ah.integration.equivalence.CrossPlatformCorpus")
    systemProperty("m303.mode", "run")
    systemProperty("m303.root", rootProject.layout.projectDirectory.asFile.absolutePath)
    systemProperty("user.timezone", "UTC")
    systemProperty("user.language", "en")
    systemProperty("user.country", "US")
    systemProperty("file.encoding", "UTF-8")
    systemProperty("m303.gradleVersion", gradle.gradleVersion)
    systemProperty("m303.buildToolsVersion", "36.1.0")
    systemProperty("m303.inputs", m303Inputs.get().asFile.absolutePath)
    systemProperty("m303.output", m303Output.get().asFile.absolutePath)
    inputs.dir(m303Inputs)
    inputs.dir(m303Runtime)
    outputs.dir(m303Output)
}

val verifyCrossPlatformCorpus by tasks.registering(Exec::class) {
    group = "verification"
    dependsOn(generateCrossPlatformCorpus)
    commandLine(
        "node",
        rootProject.layout.projectDirectory.file("tools/compare-platform-results/index.mjs").asFile.absolutePath,
        "platform",
        m303Output.get().asFile.absolutePath,
    )
}

tasks.register("crossPlatformCorpus") {
    group = "verification"
    description = "Produces and independently validates the current-host M3-03 platform result."
    dependsOn(verifyCrossPlatformCorpus)
}

val m304EvidenceDirectory = providers.gradleProperty("m304EvidenceDir")
    .map { rootProject.layout.projectDirectory.dir(it) }
    .orElse(rootProject.layout.projectDirectory.dir("docs/evidence/M3-04/cells"))
val m304Json = rootProject.layout.projectDirectory.file("docs/evidence/M3-04/compatibility-matrix.json")
val m304Markdown = rootProject.layout.projectDirectory.file("docs/generated/COMPATIBILITY_RESULTS.md")
val m304Probe = rootProject.layout.projectDirectory.file("tools/device-capability-probe/index.mjs")

val testApiAbiMatrix by tasks.registering(Exec::class) {
    group = "verification"
    description = "Runs deterministic M3-04 inventory and status mutation tests."
    commandLine("node", m304Probe.asFile.absolutePath, "self-test")
    inputs.files(m304Probe, rootProject.layout.projectDirectory.file("integration-tests/schemas/compatibility-matrix.schema.json"))
}

val generateApiAbiMatrix by tasks.registering(Exec::class) {
    group = "verification"
    description = "Generates the exact 32-cell M3-04 JSON and Markdown from verified cell evidence."
    dependsOn(testApiAbiMatrix)
    commandLine(
        "node", m304Probe.asFile.absolutePath, "generate",
        "--evidence-dir", m304EvidenceDirectory.get().asFile.absolutePath,
        "--json-output", m304Json.asFile.absolutePath,
        "--markdown-output", m304Markdown.asFile.absolutePath,
    )
    inputs.dir(m304EvidenceDirectory)
    inputs.file(m304Probe)
    outputs.files(m304Json, m304Markdown)
}

tasks.register<Exec>("runApiAbiMatrix") {
    group = "verification"
    description = "Generates and independently validates the complete M3-04 API/ABI matrix."
    dependsOn(generateApiAbiMatrix)
    commandLine(
        "node", m304Probe.asFile.absolutePath, "validate",
        "--json", m304Json.asFile.absolutePath,
        "--markdown", m304Markdown.asFile.absolutePath,
    )
    inputs.files(m304Json, m304Markdown, m304Probe)
}

tasks.named("check") {
    dependsOn(tasks.named("test"), testApiAbiMatrix)
}
