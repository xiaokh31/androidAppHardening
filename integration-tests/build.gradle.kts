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

dependencies {
    implementation(project(":host:cli"))
}

val generatedRuntimeBundle = layout.buildDirectory.dir("generated/m3-01/runtime-bundle")
val androidHome = providers.environmentVariable("ANDROID_HOME")
    .orElse(providers.environmentVariable("ANDROID_SDK_ROOT"))
val executableSuffix = if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) ".bat" else ""
val bootstrapAar = project(":runtime:bootstrap").layout.buildDirectory.file("outputs/aar/bootstrap-release.aar")
val policyAar = project(":runtime:policy").layout.buildDirectory.file("outputs/aar/policy-release.aar")
val nativeAar = project(":runtime:native").layout.buildDirectory.file("outputs/aar/native-release.aar")
val runtimeTemplates = project(":runtime:native").layout.buildDirectory.dir("intermediates/stripped_native_libs/release/out/lib")
val d8Executable = layout.file(androidHome.map { File(it, "build-tools/36.1.0/d8$executableSuffix") })
val androidJar = layout.file(androidHome.map { File(it, "platforms/android-36/android.jar") })

val generateM301RuntimeBundle by tasks.registering(JavaExec::class) {
    dependsOn(
        tasks.named("classes"),
        ":runtime:bootstrap:assembleRelease",
        ":runtime:policy:assembleRelease",
        ":runtime:native:assembleRelease",
        ":runtime:native:stageM204RuntimeTemplates",
    )
    inputs.files(bootstrapAar, policyAar, nativeAar, d8Executable, androidJar)
    inputs.dir(runtimeTemplates)
    outputs.dir(generatedRuntimeBundle)
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("ah.integration.fixtures.RuntimeBundleGenerator")
    systemProperty("m301.bootstrapAar", bootstrapAar.get().asFile.absolutePath)
    systemProperty("m301.policyAar", policyAar.get().asFile.absolutePath)
    systemProperty("m301.nativeAar", nativeAar.get().asFile.absolutePath)
    systemProperty("m301.d8", d8Executable.get().asFile.absolutePath)
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
