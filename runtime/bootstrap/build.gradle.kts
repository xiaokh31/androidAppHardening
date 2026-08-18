import com.android.build.api.instrumentation.AsmClassVisitorFactory
import com.android.build.api.instrumentation.ClassContext
import com.android.build.api.instrumentation.ClassData
import com.android.build.api.instrumentation.FramesComputationMode
import com.android.build.api.instrumentation.InstrumentationParameters
import com.android.build.api.instrumentation.InstrumentationScope
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.testing.Test
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

abstract class M210BootstrapTimingFactory :
    AsmClassVisitorFactory<InstrumentationParameters.None> {
    override fun isInstrumentable(classData: ClassData): Boolean =
        classData.className == "ah.runtime.bootstrap.HardeningBootstrap\$Coordinator"

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
            if (name != "install") {
                return delegate
            }
            return object : MethodVisitor(Opcodes.ASM9, delegate) {
                private var expectReadyStateWrite = false

                private fun markCommittedReady() {
                    super.visitLdcInsn(6)
                    super.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        "ah/runtime/profile/M210StartupTimingObserver",
                        "mark",
                        "(I)V",
                        false,
                    )
                }

                override fun visitFieldInsn(
                    opcode: Int,
                    owner: String,
                    fieldName: String,
                    fieldDescriptor: String,
                ) {
                    val committedReady =
                        expectReadyStateWrite &&
                            opcode == Opcodes.PUTFIELD &&
                            owner == "ah/runtime/bootstrap/HardeningBootstrap\$Coordinator" &&
                            fieldName == "state"
                    expectReadyStateWrite = false
                    super.visitFieldInsn(opcode, owner, fieldName, fieldDescriptor)
                    if (committedReady) {
                        markCommittedReady()
                    } else if (
                        opcode == Opcodes.GETSTATIC &&
                        owner == "ah/runtime/bootstrap/HardeningBootstrap\$State" &&
                        fieldName == "READY"
                    ) {
                        expectReadyStateWrite = true
                    }
                }

                override fun visitInsn(opcode: Int) {
                    expectReadyStateWrite = false
                    super.visitInsn(opcode)
                }

                override fun visitVarInsn(opcode: Int, variable: Int) {
                    expectReadyStateWrite = false
                    super.visitVarInsn(opcode, variable)
                }

                override fun visitJumpInsn(opcode: Int, label: org.objectweb.asm.Label) {
                    expectReadyStateWrite = false
                    super.visitJumpInsn(opcode, label)
                }

                override fun visitMethodInsn(
                    opcode: Int,
                    owner: String,
                    methodName: String,
                    methodDescriptor: String,
                    isInterface: Boolean,
                ) {
                    expectReadyStateWrite = false
                    super.visitMethodInsn(
                        opcode,
                        owner,
                        methodName,
                        methodDescriptor,
                        isInterface,
                    )
                }
            }
        }
    }
}
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

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

androidComponents {
    onVariants(selector().withBuildType("m210Profile")) { variant ->
        variant.instrumentation.transformClassesWith(
            M210BootstrapTimingFactory::class.java,
            InstrumentationScope.PROJECT,
        ) {}
        variant.instrumentation.setAsmFramesComputationMode(
            FramesComputationMode.COMPUTE_FRAMES_FOR_INSTRUMENTED_METHODS,
        )
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
