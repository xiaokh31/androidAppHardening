import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.testing.Test
import java.io.File
import java.nio.file.Files

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

val hostReleaseRuntime by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    attributes {
        attribute(
            Usage.USAGE_ATTRIBUTE,
            objects.named(Usage.JAVA_RUNTIME),
        )
    }
}

val v02Apksig by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}

dependencies {
    implementation(libs.jna.platform)
    hostReleaseRuntime(project(":host:cli"))
    add(v02Apksig.name, libs.android.apksig)
}

val v02RuntimeBundle = layout.buildDirectory.dir("generated/v0.2/runtime-bundle")
val v02StagedApksig = layout.buildDirectory.file("intermediates/v0.2/apksig/apksig-9.3.0.jar")
val v02AndroidHome = providers.environmentVariable("ANDROID_HOME")
    .orElse(providers.environmentVariable("ANDROID_SDK_ROOT"))
val v02D8Jar = layout.file(v02AndroidHome.map { File(it, "build-tools/36.1.0/lib/d8.jar") })
val v02AndroidJar = layout.file(v02AndroidHome.map { File(it, "platforms/android-36/android.jar") })
val v02BootstrapAar = project(":runtime:bootstrap").layout.buildDirectory.file("outputs/aar/bootstrap-release.aar")
val v02PolicyAar = project(":runtime:policy").layout.buildDirectory.file("outputs/aar/policy-release.aar")
val v02NativeAar = project(":runtime:native").layout.buildDirectory.file("outputs/aar/native-release.aar")
val v02RuntimeTemplates = project(":runtime:native").layout.buildDirectory.dir(
    "intermediates/stripped_native_libs/release/out/lib",
)

val stageV02Apksig by tasks.registering(JavaExec::class) {
    dependsOn(tasks.named("classes"))
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("ah.distribution.V02ComponentBaselineValidator")
    inputs.files(v02Apksig, v02D8Jar)
    outputs.file(v02StagedApksig)
    args(
        "verifier-library", "--repo", rootProject.layout.projectDirectory.asFile.absolutePath,
        "--apksig", v02Apksig.singleFile.absolutePath, "--d8", v02D8Jar.get().asFile.absolutePath,
        "--output", v02StagedApksig.get().asFile.absolutePath,
    )
}

val generateV02RuntimeBundle by tasks.registering(JavaExec::class) {
    dependsOn(
        tasks.named("classes"),
        stageV02Apksig,
        ":runtime:bootstrap:assembleRelease",
        ":runtime:policy:assembleRelease",
        ":runtime:native:assembleRelease",
        ":runtime:native:stageM204RuntimeTemplates",
    )
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("ah.distribution.V02ComponentBaselineValidator")
    args(
        "runtime-bundle",
        "--repo", rootProject.layout.projectDirectory.asFile.absolutePath,
        "--output", v02RuntimeBundle.get().asFile.absolutePath,
        "--apksig", v02StagedApksig.get().asFile.absolutePath,
        "--d8", v02D8Jar.get().asFile.absolutePath,
        "--android-jar", v02AndroidJar.get().asFile.absolutePath,
        "--bootstrap-aar", v02BootstrapAar.get().asFile.absolutePath,
        "--policy-aar", v02PolicyAar.get().asFile.absolutePath,
        "--native-aar", v02NativeAar.get().asFile.absolutePath,
        "--runtime-templates", v02RuntimeTemplates.get().asFile.absolutePath,
    )
    inputs.files(v02StagedApksig, v02D8Jar, v02AndroidJar, v02BootstrapAar, v02PolicyAar, v02NativeAar)
    inputs.dir(v02RuntimeTemplates)
    outputs.dir(v02RuntimeBundle)
}

val v02HostReleaseJar by tasks.registering(Jar::class) {
    group = "distribution"
    description = "Builds the reproducible standalone v0.2 Host release JAR."
    dependsOn(hostReleaseRuntime, generateV02RuntimeBundle)
    archiveFileName.set("android-app-hardening.jar")
    destinationDirectory.set(layout.buildDirectory.dir("v0.2/host"))
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
    manifest.attributes["Main-Class"] = "ah.host.cli.CliMain"
    exclude("META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA", "META-INF/INDEX.LIST")
    from(hostReleaseRuntime.map { archive ->
        // The original dual-purpose apksig is build input only. Both Host and DEX use the same verifier-only bytes.
        zipTree(if (archive.name == "apksig-9.3.0.jar") v02StagedApksig.get().asFile else archive)
    })
    from(v02RuntimeBundle)
}

val componentRoot = layout.buildDirectory.dir("v0.2/components")
val trackedBaseline = rootProject.layout.projectDirectory.file(
    "docs/v0.2/evidence/V2-M0-02/v02-component-baseline.json",
)
val candidateManifest = rootProject.layout.projectDirectory.file(
    "build/v0.2/candidate-component-manifest.json",
)

val stageV02Components by tasks.registering(JavaExec::class) {
    group = "distribution"
    description = "Stages the exact non-release v0.2 canary component set."
    dependsOn(
        v02HostReleaseJar,
        ":runtime:native:assembleRelease",
        ":runtime:native:stageM204RuntimeTemplates",
        tasks.named("classes"),
    )
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("ah.distribution.V02ComponentBaselineValidator")
    args(
        "stage",
        "--repo", rootProject.layout.projectDirectory.asFile.absolutePath,
        "--components", componentRoot.get().asFile.absolutePath,
    )
    inputs.file(v02HostReleaseJar.flatMap { it.archiveFile })
    inputs.files(
        listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64").map { abi ->
            rootProject.layout.projectDirectory.file(
                "runtime/native/build/intermediates/stripped_native_libs/release/out/lib/$abi/libah_runtime.so",
            )
        },
    )
    inputs.files(
        rootProject.layout.projectDirectory.file(
            "distribution/src/main/resources/v0.2/windows/android-app-hardening.cmd",
        ),
        rootProject.layout.projectDirectory.file(
            "distribution/src/main/resources/v0.2/ubuntu/android-app-hardening",
        ),
        rootProject.layout.projectDirectory.file("distribution/docs/QUICKSTART.md"),
        rootProject.layout.projectDirectory.file("LICENSE"),
        rootProject.layout.projectDirectory.file("THIRD_PARTY_NOTICES.md"),
    )
    outputs.dir(componentRoot)
}

val writeV02IdentityManifests by tasks.registering(JavaExec::class) {
    group = "distribution"
    description = "Writes the three canonical V2-M0-02 identity manifests from the exact HEAD Git tree."
    dependsOn(tasks.named("classes"))
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("ah.distribution.V02ComponentBaselineValidator")
    args(
        "manifests",
        "--repo", rootProject.layout.projectDirectory.asFile.absolutePath,
        "--output", rootProject.layout.projectDirectory.dir("docs/v0.2/evidence/V2-M0-02").asFile.absolutePath,
    )
}

val writeV02ComponentBaseline by tasks.registering(JavaExec::class) {
    group = "distribution"
    description = "Writes the canonical tracked v0.2 component baseline from exact component and Git bytes."
    dependsOn(stageV02Components, writeV02IdentityManifests, tasks.named("classes"))
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("ah.distribution.V02ComponentBaselineValidator")
    args(
        "baseline",
        "--repo", rootProject.layout.projectDirectory.asFile.absolutePath,
        "--components", componentRoot.get().asFile.absolutePath,
        "--baseline", trackedBaseline.asFile.absolutePath,
    )
    inputs.files(
        rootProject.layout.projectDirectory.file("docs/v0.2/evidence/V2-M0-02/implementation-manifest.json"),
        rootProject.layout.projectDirectory.file("docs/v0.2/evidence/V2-M0-02/toolchain-manifest.json"),
        rootProject.layout.projectDirectory.file("docs/v0.2/evidence/V2-M0-02/product-contract-manifest.json"),
    )
    inputs.dir(componentRoot)
}

val v02CandidateManifest by tasks.registering(JavaExec::class) {
    group = "distribution"
    description = "Rebuilds the ignored candidate component manifest from the tracked baseline."
    dependsOn(stageV02Components, tasks.named("classes"))
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("ah.distribution.V02ComponentBaselineValidator")
    args(
        "candidate",
        "--repo", rootProject.layout.projectDirectory.asFile.absolutePath,
        "--components", componentRoot.get().asFile.absolutePath,
        "--baseline", trackedBaseline.asFile.absolutePath,
        "--output", candidateManifest.asFile.absolutePath,
    )
    inputs.file(trackedBaseline)
    inputs.dir(componentRoot)
    outputs.file(candidateManifest)
}

val packageWindowsV02 by tasks.registering(JavaExec::class) {
    group = "distribution"
    description = "Builds the deterministic non-release Windows v0.2 canary ZIP."
    dependsOn(stageV02Components, tasks.named("classes"))
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("ah.distribution.V02ComponentBaselineValidator")
    val archive = layout.buildDirectory.file("v0.2/archives/android-app-hardening-0.2.0-windows.zip")
    args(
        "canary", "--repo", rootProject.layout.projectDirectory.asFile.absolutePath,
        "--components", componentRoot.get().asFile.absolutePath,
        "--baseline", trackedBaseline.asFile.absolutePath,
        "--platform", "windows",
        "--output", archive.get().asFile.absolutePath,
    )
    inputs.file(trackedBaseline)
    inputs.dir(componentRoot)
    outputs.file(archive)
    // Recheck exact Git HEAD/time, optional diagnostic bytes and filesystem link metadata on every invocation.
    outputs.upToDateWhen { false }
    doFirst {
        Files.deleteIfExists(archive.get().asFile.toPath())
    }
}

val packageUbuntuV02 by tasks.registering(JavaExec::class) {
    group = "distribution"
    description = "Builds the deterministic non-release Ubuntu v0.2 canary TAR.GZ."
    dependsOn(stageV02Components, tasks.named("classes"))
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("ah.distribution.V02ComponentBaselineValidator")
    val archive = layout.buildDirectory.file("v0.2/archives/android-app-hardening-0.2.0-ubuntu.tar.gz")
    args(
        "canary", "--repo", rootProject.layout.projectDirectory.asFile.absolutePath,
        "--components", componentRoot.get().asFile.absolutePath,
        "--baseline", trackedBaseline.asFile.absolutePath,
        "--platform", "ubuntu",
        "--output", archive.get().asFile.absolutePath,
    )
    inputs.file(trackedBaseline)
    inputs.dir(componentRoot)
    outputs.file(archive)
    // Recheck exact Git HEAD/time, optional diagnostic bytes and filesystem link metadata on every invocation.
    outputs.upToDateWhen { false }
    doFirst {
        Files.deleteIfExists(archive.get().asFile.toPath())
    }
}

fun registerSelfTest(taskName: String, mainClassName: String) = tasks.register<JavaExec>(taskName) {
    group = "verification"
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set(mainClassName)
    systemProperty("ah.distribution.repo", rootProject.layout.projectDirectory.asFile.absolutePath)
}

val componentBaselineTest = registerSelfTest(
    "componentBaselineTest",
    "ah.distribution.V02ComponentBaselineValidatorTest",
).also { registration -> registration.configure { dependsOn(stageV02Components) } }
val releasePackagerTest = registerSelfTest(
    "releasePackagerTest",
    "ah.distribution.V02ReleasePackagerTest",
)
val launcherContractTest = registerSelfTest(
    "launcherContractTest",
    "ah.distribution.V02LauncherContractTest",
).also { registration ->
    registration.configure {
        dependsOn(v02HostReleaseJar, ":host:apk-inspector:signerPolicyTest")
        systemProperty("ah.distribution.originalApksig", v02Apksig.singleFile.absolutePath)
    }
}
val archiveReproducibilityTest = registerSelfTest(
    "archiveReproducibilityTest",
    "ah.distribution.V02ArchiveReproducibilityTest",
)

tasks.named<Test>("test") {
    dependsOn(componentBaselineTest, releasePackagerTest, launcherContractTest, archiveReproducibilityTest)
    failOnNoDiscoveredTests = false
}

val verifyReleaseV02 by tasks.registering {
    group = "verification"
    description = "Verifies the tracked baseline and deterministic Windows/Ubuntu canary archives."
    dependsOn(tasks.named("test"), packageWindowsV02, packageUbuntuV02)
}

tasks.named("check") {
    dependsOn(verifyReleaseV02)
}
