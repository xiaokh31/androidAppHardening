import java.io.File
import javax.inject.Inject
import javax.tools.ToolProvider
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations

plugins {
    alias(libs.plugins.android.application)
}

@CacheableTask
abstract class GenerateClassloaderPocDex
@Inject
constructor(private val execOperations: ExecOperations) : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val androidJar: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val d8Executable: RegularFileProperty

    @get:Input
    abstract val jdkRuntimeVersion: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        if (jdkRuntimeVersion.get() != "17.0.19+10") {
            throw GradleException(
                "M0-04 requires JDK 17.0.19+10, got ${jdkRuntimeVersion.get()}",
            )
        }
        val compiler =
            ToolProvider.getSystemJavaCompiler()
                ?: throw GradleException("M0-04 requires the pinned JDK, not a JRE")
        val classDirectory = temporaryDir.resolve("classes")
        val assetRoot = outputDirectory.get().asFile
        val dexDirectory = assetRoot.resolve("ah/poc")
        classDirectory.deleteRecursively()
        assetRoot.deleteRecursively()
        classDirectory.mkdirs()
        dexDirectory.mkdirs()

        val sourceFiles = sources.files.sortedBy(File::getPath)
        if (sourceFiles.isEmpty()) {
            throw GradleException("M0-04 payload sources are missing")
        }

        compiler.getStandardFileManager(null, null, Charsets.UTF_8).use { fileManager ->
            val units = fileManager.getJavaFileObjectsFromFiles(sourceFiles)
            val options =
                listOf(
                    "-encoding",
                    "UTF-8",
                    "-source",
                    "17",
                    "-target",
                    "17",
                    "-classpath",
                    androidJar.get().asFile.absolutePath,
                    "-d",
                    classDirectory.absolutePath,
                )
            if (compiler.getTask(null, fileManager, null, options, null, units).call() != true) {
                throw GradleException("M0-04 payload javac failed")
            }
        }

        val classFiles =
            classDirectory
                .walkTopDown()
                .filter { it.isFile && it.extension == "class" }
                .sortedBy(File::getPath)
                .toList()
        if (classFiles.size != 3) {
            throw GradleException(
                "M0-04 expected exactly three payload classes, found ${classFiles.size}",
            )
        }

        val d8Arguments =
            listOf(
                d8Executable.get().asFile.absolutePath,
                "--min-api",
                "29",
                "--lib",
                androidJar.get().asFile.absolutePath,
                "--output",
                dexDirectory.absolutePath,
            ) + classFiles.map(File::getAbsolutePath)
        execOperations
            .exec {
                if (System.getProperty("os.name").startsWith("Windows")) {
                    commandLine(listOf("cmd.exe", "/d", "/c") + d8Arguments)
                } else {
                    commandLine(d8Arguments)
                }
            }.assertNormalExitValue()

        val payload = dexDirectory.resolve("classes.dex")
        if (!payload.isFile || payload.length() < 112) {
            throw GradleException("M0-04 D8 output is missing or truncated")
        }
    }
}

android {
    namespace = "ah.fixtures.android"
    compileSdk = libs.versions.android.compile.sdk.get().toInt()
    buildToolsVersion = libs.versions.android.build.tools.get()

    defaultConfig {
        applicationId = "ah.fixtures.android"
        minSdk = libs.versions.android.min.sdk.get().toInt()
        targetSdk = libs.versions.android.target.sdk.get().toInt()
        versionCode = 1
        versionName = "0.1"
        testInstrumentationRunner = "ah.fixtures.android.ClassLoaderPocRunner"
    }

    flavorDimensions += "poc"
    productFlavors {
        create("classloaderPoc") {
            dimension = "poc"
            applicationIdSuffix = ".classloaderpoc"
        }
    }

    androidResources {
        noCompress += "dex"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = true
        checkDependencies = true
        disable += setOf(
            "GradleDependency", // M0-03 intentionally pins compileSdk 36.
            "MissingApplicationIcon", // The empty M0-03 fixture has no UI assets.
            "OldTargetApi", // M0-03 intentionally pins fixture targetSdk 36.
        )
        warningsAsErrors = true
    }
}

val androidHome =
    providers
        .environmentVariable("ANDROID_HOME")
        .orElse(providers.environmentVariable("ANDROID_SDK_ROOT"))
        .orNull
        ?: throw GradleException("ANDROID_HOME or ANDROID_SDK_ROOT must identify the pinned SDK")
val compileSdkVersion = libs.versions.android.compile.sdk.get()
val buildToolsVersion = libs.versions.android.build.tools.get()
val androidJarFile = File(androidHome, "platforms/android-$compileSdkVersion/android.jar")
val d8Command = if (System.getProperty("os.name").startsWith("Windows")) "d8.bat" else "d8"
val d8ExecutableFile = File(androidHome, "build-tools/$buildToolsVersion/$d8Command")

androidComponents {
    onVariants(selector().withFlavor("poc" to "classloaderPoc")) { variant ->
        val taskName =
            "generate${variant.name.replaceFirstChar { character -> character.uppercase() }}PayloadDex"
        val generatePayload =
            tasks.register<GenerateClassloaderPocDex>(taskName) {
                sources.from(
                    layout.projectDirectory
                        .dir("src/classloaderPocPayload/java")
                        .asFileTree,
                )
                androidJar.set(androidJarFile)
                d8Executable.set(d8ExecutableFile)
                jdkRuntimeVersion.set(providers.systemProperty("java.runtime.version"))
                outputDirectory.set(
                    layout.buildDirectory.dir("generated/m0-04/${variant.name}/assets"),
                )
            }
        variant.sources.assets?.addGeneratedSourceDirectory(
            generatePayload,
            GenerateClassloaderPocDex::outputDirectory,
        )
    }
}

dependencies {
    add("classloaderPocImplementation", project(":runtime:bootstrap"))
}
