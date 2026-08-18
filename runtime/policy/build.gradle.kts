import com.android.build.api.instrumentation.AsmClassVisitorFactory
import com.android.build.api.instrumentation.ClassContext
import com.android.build.api.instrumentation.ClassData
import com.android.build.api.instrumentation.FramesComputationMode
import com.android.build.api.instrumentation.InstrumentationParameters
import com.android.build.api.instrumentation.InstrumentationScope
import org.gradle.api.tasks.JavaExec
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.tasks.testing.Test
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

abstract class M210GuardTimingFactory :
    AsmClassVisitorFactory<InstrumentationParameters.None> {
    override fun isInstrumentable(classData: ClassData): Boolean =
        classData.className == "ah.runtime.guard.RuntimeStartupGuard"

    override fun createClassVisitor(
        classContext: ClassContext,
        nextClassVisitor: ClassVisitor,
    ): ClassVisitor = object : ClassVisitor(Opcodes.ASM9, nextClassVisitor) {
        override fun visitMethod(
            access: Int,
            name: String,
            descriptor: String,
            signature: String?,
            exceptions: Array<out String>?,
        ): MethodVisitor {
            val delegate = super.visitMethod(access, name, descriptor, signature, exceptions)
            if (name != "openVerifiedPayloadInternal") {
                return delegate
            }
            return object : MethodVisitor(Opcodes.ASM9, delegate) {
                private fun mark(index: Int) {
                    super.visitLdcInsn(index)
                    super.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        "ah/runtime/profile/M210StartupTimingObserver",
                        "mark",
                        "(I)V",
                        false,
                    )
                }

                override fun visitMethodInsn(
                    opcode: Int,
                    owner: String,
                    methodName: String,
                    methodDescriptor: String,
                    isInterface: Boolean,
                ) {
                    if (owner == "ah/runtime/guard/RuntimeSignerVerifier" &&
                        methodName == "verify"
                    ) {
                        mark(0)
                    }
                    super.visitMethodInsn(
                        opcode,
                        owner,
                        methodName,
                        methodDescriptor,
                        isInterface,
                    )
                    when {
                        owner == "ah/runtime/guard/RuntimeStartupGuard" &&
                            methodName == "sha256" -> mark(1)
                        owner == "ah/runtime/guard/IntegrityChecks" &&
                            methodName == "verifyPreReadSigner" -> mark(2)
                        owner == "ah/runtime/loader/PayloadRuntime" &&
                            methodName == "openVerified" -> mark(3)
                        owner == "ah/runtime/MemoryControls" &&
                            methodName == "apply" -> mark(4)
                    }
                }

                override fun visitInsn(opcode: Int) {
                    if (opcode == Opcodes.ARETURN) {
                        mark(5)
                    }
                    super.visitInsn(opcode)
                }
            }
        }
    }
}

plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "ah.runtime.policy"
    compileSdk = libs.versions.android.compile.sdk.get().toInt()
    buildToolsVersion = libs.versions.android.build.tools.get()

    defaultConfig {
        minSdk = libs.versions.android.min.sdk.get().toInt()
        testInstrumentationRunner = "ah.runtime.guard.PolicyConnectedRunner"
    }

    buildTypes {
        create("m210Profile") {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")
        }
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
}

androidComponents {
    onVariants(selector().withBuildType("m210Profile")) { variant ->
        variant.instrumentation.transformClassesWith(
            M210GuardTimingFactory::class.java,
            InstrumentationScope.PROJECT,
        ) {}
        variant.instrumentation.setAsmFramesComputationMode(
            FramesComputationMode.COMPUTE_FRAMES_FOR_INSTRUMENTED_METHODS,
        )
    }
}

dependencies {
    implementation(project(":runtime:native"))
    implementation(libs.android.apksig)
}

val policySelfTest by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs the dependency-free M2-03 policy unit matrix."
    dependsOn("compileDebugUnitTestJavaWithJavac", ":runtime:native:compileDebugJavaWithJavac")
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
            "runtime/native/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes",
        ),
        externalRuntime,
    )
    mainClass.set("ah.runtime.guard.PolicySelfTest")
}

val abiCompatibilitySelfTest by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs the dependency-free M2-04 ABI compatibility matrix."
    dependsOn("compileDebugUnitTestJavaWithJavac", ":runtime:native:compileDebugJavaWithJavac")
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
        externalRuntime,
    )
    mainClass.set("ah.runtime.AbiCompatibilitySelfTest")
    args(layout.buildDirectory.dir("reports/m2-04").get().asFile.absolutePath)
}

val environmentRiskSelfTest by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs the dependency-free M2-05 environment risk policy matrix."
    dependsOn("compileDebugUnitTestJavaWithJavac", ":runtime:native:compileDebugJavaWithJavac")
    classpath(
        layout.buildDirectory.dir(
            "intermediates/javac/debugUnitTest/compileDebugUnitTestJavaWithJavac/classes",
        ),
        layout.buildDirectory.dir(
            "intermediates/javac/debug/compileDebugJavaWithJavac/classes",
        ),
        rootProject.layout.projectDirectory.dir(
            "runtime/native/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes",
        ),
    )
    mainClass.set("ah.runtime.risk.EnvironmentRiskEngineSelfTest")
    args(layout.buildDirectory.dir("reports/m2-05").get().asFile.absolutePath)
}

val memoryControlsSelfTest by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs the dependency-free M2-06 memory profile policy matrix."
    dependsOn("compileDebugUnitTestJavaWithJavac", ":runtime:native:compileDebugJavaWithJavac")
    classpath(
        layout.buildDirectory.dir(
            "intermediates/javac/debugUnitTest/compileDebugUnitTestJavaWithJavac/classes",
        ),
        layout.buildDirectory.dir(
            "intermediates/javac/debug/compileDebugJavaWithJavac/classes",
        ),
        rootProject.layout.projectDirectory.dir(
            "runtime/native/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes",
        ),
    )
    mainClass.set("ah.runtime.MemoryControlsSelfTest")
}

afterEvaluate {
    tasks.named("test") {
        dependsOn(
            policySelfTest,
            abiCompatibilitySelfTest,
            environmentRiskSelfTest,
            memoryControlsSelfTest,
        )
    }
}

tasks.withType<Test>().configureEach {
    failOnNoDiscoveredTests = false
}
