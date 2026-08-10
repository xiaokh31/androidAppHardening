import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.testing.Test

plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "ah.runtime.bootstrap"
    compileSdk = libs.versions.android.compile.sdk.get().toInt()
    buildToolsVersion = libs.versions.android.build.tools.get()

    defaultConfig {
        minSdk = libs.versions.android.min.sdk.get().toInt()
        testInstrumentationRunner = "ah.runtime.bootstrap.BootstrapConnectedRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = true
        checkDependencies = true
        disable += "GradleDependency" // M0-03 intentionally pins compileSdk 36.
        warningsAsErrors = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    api(project(":runtime:policy"))
}

val bootstrapSelfTest by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs the dependency-free M2-01 bootstrap state-machine matrix."
    dependsOn(
        "compileDebugUnitTestJavaWithJavac",
        ":runtime:policy:compileDebugJavaWithJavac",
        ":runtime:native:compileDebugJavaWithJavac",
    )
    val externalRuntime =
        configurations.named("debugRuntimeClasspath").map { configuration ->
            configuration.incoming.artifactView {
                componentFilter { identifier -> identifier is ModuleComponentIdentifier }
            }.files
        }
    classpath(
        layout.buildDirectory.dir(
            "intermediates/javac/debugUnitTest/compileDebugUnitTestJavaWithJavac/classes",
        ),
        layout.buildDirectory.dir(
            "intermediates/javac/debug/compileDebugJavaWithJavac/classes",
        ),
        rootProject.layout.projectDirectory.dir(
            "runtime/policy/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes",
        ),
        rootProject.layout.projectDirectory.dir(
            "runtime/native/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes",
        ),
        androidComponents.sdkComponents.sdkDirectory.map { sdk ->
            sdk.file("platforms/android-${libs.versions.android.compile.sdk.get()}/android.jar")
        },
        externalRuntime,
    )
    mainClass.set("ah.runtime.bootstrap.BootstrapSelfTest")
}

afterEvaluate {
    tasks.named("test") {
        dependsOn(bootstrapSelfTest)
    }
}

tasks.withType<Test>().configureEach {
    failOnNoDiscoveredTests = false
}
