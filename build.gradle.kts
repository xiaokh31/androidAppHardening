plugins {
    base
    id("org.cyclonedx.bom") version "3.4.1"
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
}

group = "io.github.xiaokh31.androidapphardening"
version = "0.2.0"

allprojects {
    group = rootProject.group
    version = rootProject.version

    dependencyLocking {
        lockAllConfigurations()
    }
}

val v02ProductSbomProjects = setOf(
    ":host:apk-inspector",
    ":host:axml",
    ":host:cli",
    ":host:container",
    ":host:repacker",
    ":runtime:bootstrap",
    ":runtime:native",
    ":runtime:policy",
)

allprojects {
    tasks.withType<org.cyclonedx.gradle.CyclonedxDirectTask>().configureEach {
        enabled = project.path in v02ProductSbomProjects
        includeConfigs.set(
            if (project.path in v02ProductSbomProjects) {
                listOf("runtimeClasspath", "releaseRuntimeClasspath")
            } else {
                listOf("__v02_no_product_configuration__")
            },
        )
        includeMetadataResolution = false
        includeBuildEnvironment = false
    }
}

tasks.cyclonedxBom {
    schemaVersion = org.cyclonedx.Version.VERSION_16
    projectType = org.cyclonedx.model.Component.Type.APPLICATION
    includeBomSerialNumber = false
    includeBuildSystem = false
    componentName = "android-app-hardening"
    componentVersion = rootProject.version.toString()
    jsonOutput.set(layout.buildDirectory.file("v0.2/security/bom-v0.2.0.raw.cdx.json"))
    xmlOutput.unsetConvention()
}

val securityReviewV02 by tasks.registering(Exec::class) {
    group = "verification"
    description = "Generates and canonicalizes the pinned CycloneDX 1.6 v0.2 SBOM."
    dependsOn(tasks.named("cyclonedxBom"))

    val implementationFreeze = providers.gradleProperty("implementationFreezeSha")
    val cycloneDxCli = providers.environmentVariable("V02_CYCLONEDX_CLI")
    val raw = layout.buildDirectory.file("v0.2/security/bom-v0.2.0.raw.cdx.json")
    val canonical = layout.buildDirectory.file("v0.2/security/bom-v0.2.0.cdx.json")
    val report = layout.buildDirectory.file("v0.2/security/v02-sbom-canonicalization.json")
    inputs.file(raw)
    inputs.property("implementationFreezeSha", implementationFreeze)
    inputs.file(cycloneDxCli)
    outputs.files(canonical, report)

    doFirst {
        val freeze = implementationFreeze.orNull
        require(freeze != null && Regex("[0-9a-f]{40}").matches(freeze)) {
            "securityReviewV02 requires -PimplementationFreezeSha=<40-lowercase-hex>"
        }
        java.nio.file.Files.deleteIfExists(canonical.get().asFile.toPath())
        java.nio.file.Files.deleteIfExists(report.get().asFile.toPath())
        commandLine(
            "node",
            "tools/supply-chain-v02/canonicalize-cyclonedx-v02.mjs",
            "--raw",
            raw.get().asFile.absolutePath,
            "--implementation-freeze",
            freeze,
            "--output",
            canonical.get().asFile.absolutePath,
            "--report",
            report.get().asFile.absolutePath,
        )
    }
}

allprojects {
    tasks.register("writeV02ResolvedMavenGraphPart") {
        notCompatibleWithConfigurationCache("One-shot project-owned pre-freeze resolution audit")
        doLast {
            val candidate = project
            val records = sortedMapOf<String, List<String>>()
            val configurationsToResolve = candidate.configurations.filter {
                it.isCanBeResolved && (it.name.endsWith("CompileClasspath") ||
                    it.name.endsWith("RuntimeClasspath") || it.name in setOf("compileClasspath", "runtimeClasspath", "hostReleaseRuntime", "v02Apksig"))
            } + candidate.buildscript.configurations.filter { it.isCanBeResolved && it.name == "classpath" }
            configurationsToResolve.forEach { configuration ->
                val resolution = configuration.incoming.resolutionResult
                val unresolved = resolution.allDependencies.filterIsInstance<org.gradle.api.artifacts.result.UnresolvedDependencyResult>()
                require(unresolved.isEmpty()) { "Unresolved Maven graph in ${candidate.path}:${configuration.name}: $unresolved" }
                records["${candidate.path}:${configuration.name}"] = resolution.allComponents.mapNotNull { component ->
                    (component.id as? org.gradle.api.artifacts.component.ModuleComponentIdentifier)?.let {
                        "${it.group}:${it.module}:${it.version}"
                    }
                }.distinct().sorted()
            }
            val destination = candidate.layout.buildDirectory.file("v0.2/resolved-maven-graph-part.json").get().asFile
            destination.parentFile.mkdirs()
            destination.writeText(groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(records)) + "\n", Charsets.UTF_8)
        }
    }
}

tasks.register("writeV02ResolvedMavenGraph") {
    group = "verification"
    description = "Records resolved build/plugin and product/test Maven coordinates for the pre-freeze acquisition lock."
    notCompatibleWithConfigurationCache("One-shot aggregation of project-owned resolution audits")
    dependsOn(allprojects.map { it.tasks.named("writeV02ResolvedMavenGraphPart") })
    doLast {
        val records = sortedMapOf<String, List<String>>()
        allprojects.sortedBy { it.path }.forEach { candidate ->
            val file = candidate.layout.buildDirectory.file("v0.2/resolved-maven-graph-part.json").get().asFile
            val part = groovy.json.JsonSlurper().parse(file) as Map<*, *>
            part.forEach { (name, values) -> records[name as String] = (values as List<*>).map { it as String } }
        }
        val graph = linkedMapOf("schemaVersion" to 1, "configurations" to records,
            "coordinates" to records.values.flatten().distinct().sorted())
        val destination = layout.buildDirectory.file("v0.2/resolved-maven-graph.json").get().asFile
        destination.parentFile.mkdirs()
        destination.writeText(groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(graph)) + "\n", Charsets.UTF_8)
    }
}

val verifyToolchainPolicy by tasks.registering(Exec::class) {
    group = "verification"
    description = "Validates the pinned toolchain and fourteen-module graph."
    commandLine("node", "tools/validation/verify-m0-toolchain.mjs")
}

val testV02MavenAcquisitionLock by tasks.registering(Exec::class) {
    group = "verification"
    dependsOn(tasks.named("writeV02ResolvedMavenGraph"))
    commandLine("node", "tools/supply-chain-v02/locked-maven-published-artifact-v2.self-test.mjs")
}

val testV02SbomCanonicalizer by tasks.registering(Exec::class) {
    group = "verification"
    commandLine("node", "tools/supply-chain-v02/canonicalize-cyclonedx-v02.self-test.mjs")
}

val testToolchainPolicy by tasks.registering(Exec::class) {
    group = "verification"
    description = "Runs positive and tamper tests for the M0-03 policy validator."
    commandLine("node", "tools/validation/test-m0-toolchain-policy.mjs")
}

val verifyM203RuntimeIntegrity by tasks.registering(Exec::class) {
    group = "verification"
    description = "Validates the M2-03 runtime integrity architecture and capability boundary."
    commandLine("node", "tools/validation/verify-m2-03-runtime-integrity.mjs")
}

val verifyM201Bootstrap by tasks.registering(Exec::class) {
    group = "verification"
    description = "Validates the M2-01 public-API bootstrap and ownership boundary."
    commandLine("node", "tools/validation/verify-m2-01-bootstrap.mjs")
}

tasks.register<Exec>("testDependencyVerification") {
    group = "verification"
    description = "Proves tampered dependency checksums fail closed and restored metadata passes."
    commandLine("node", "tools/validation/test-dependency-verification.mjs")
}

tasks.register<Exec>("verifyGovernance") {
    group = "verification"
    description = "Validates governance plus the pinned project toolchain."
    commandLine("node", "tools/governance/validate-project-package.mjs")
    dependsOn(verifyToolchainPolicy)
}

tasks.named("check") {
    dependsOn(
        testV02MavenAcquisitionLock,
        testV02SbomCanonicalizer,
        ":host:cli:check",
        ":host:apk-inspector:check",
        ":host:axml:check",
        ":host:container:check",
        ":host:repacker:check",
        ":runtime:bootstrap:check",
        ":runtime:native:check",
        ":runtime:policy:check",
        ":fixtures:android:check",
        ":integration-tests:check",
        ":benchmarks:host:check",
        ":benchmarks:android:check",
        ":tools:validation:check",
        ":distribution:check",
        verifyToolchainPolicy,
        testToolchainPolicy,
        verifyM201Bootstrap,
        verifyM203RuntimeIntegrity,
    )
}

tasks.register("lint") {
    group = "verification"
    description = "Runs Android lint for every Android skeleton module."
    dependsOn(
        ":runtime:bootstrap:lint",
        ":runtime:native:lint",
        ":runtime:policy:lint",
        ":fixtures:android:lint",
        ":benchmarks:android:lint",
    )
}
