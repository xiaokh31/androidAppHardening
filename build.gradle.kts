plugins {
    base
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
}

group = "io.github.xiaokh31.androidapphardening"
version = "0.1.0-dev"

allprojects {
    group = rootProject.group
    version = rootProject.version

    dependencyLocking {
        lockAllConfigurations()
    }
}

val verifyToolchainPolicy by tasks.registering(Exec::class) {
    group = "verification"
    description = "Validates the pinned toolchain and fourteen-module graph."
    commandLine("node", "tools/validation/verify-m0-toolchain.mjs")
}

val testToolchainPolicy by tasks.registering(Exec::class) {
    group = "verification"
    description = "Runs positive and tamper tests for the M0-03 policy validator."
    commandLine("node", "tools/validation/test-m0-toolchain-policy.mjs")
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
