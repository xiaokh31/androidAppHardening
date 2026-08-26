package ah.distribution

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.text.Normalizer
import java.util.Comparator
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile

private data class GitEntry(val path: String, val mode: String, val blob: String)

private data class ComponentSpec(
    val logicalPath: String,
    val archivePath: String?,
    val sourcePath: String,
    val sourceGitPath: String,
    val mode: String,
    val role: String,
    val variant: String,
    val abi: String?,
    val platform: String,
)

object V02ComponentBaselineValidator {
    private const val IDENTITY_POLICY_SHA256 = "874d788d45aaa051ca5695aeac8d5f693bebde0cba4a4e026312d1ee5cc0e58b"
    private const val CANARY_SOURCE_COMMIT = "0000000000000000000000000000000000000000"
    private const val CANARY_TIMESTAMP = "1980-01-01T00:00:00Z"
    private val SHA256 = Regex("[0-9a-f]{64}")
    private val GIT_SHA1 = Regex("[0-9a-f]{40}")
    private val MANIFEST_KINDS = listOf("implementation", "toolchain", "product-contract")
    private val MANIFEST_NAMES = mapOf(
        "implementation" to "implementation-manifest.json",
        "toolchain" to "toolchain-manifest.json",
        "product-contract" to "product-contract-manifest.json",
    )
    private val BASELINE_KEYS = listOf(
        "schemaVersion", "releaseLine", "releaseVersion", "taskId", "implementationManifestSha256",
        "toolchainManifestSha256", "productContractManifestSha256", "entries",
    )
    private val BASELINE_ENTRY_KEYS = listOf(
        "logicalPath", "archivePath", "sourcePath", "sourceGitPath", "sourceGitBlobSha1", "mode",
        "sizeBytes", "sha256", "role", "variant", "abi", "platform",
    )

    @JvmStatic
    fun main(args: Array<String>) {
        if (args.isEmpty()) throw DistributionException("validator command is required")
        val command = args.first()
        val options = parseOptions(args.drop(1))
        val repository = requiredPath(options, "--repo").toAbsolutePath().normalize()
        requireRepository(repository)
        val commit = head(repository)
        when (command) {
            "stage" -> stage(repository, requiredPath(options, "--components"), commit)
            "runtime-bundle" -> writeRuntimeBundle(repository, options)
            "verifier-library" -> writeVerifierLibrary(repository, options)
            "manifests" -> writeManifests(repository, requiredPath(options, "--output"), commit)
            "baseline" -> writeBaseline(
                repository,
                requiredPath(options, "--components"),
                requiredPath(options, "--baseline"),
                commit,
            )
            "candidate" -> writeCandidate(
                repository,
                requiredPath(options, "--components"),
                requiredPath(options, "--baseline"),
                requiredPath(options, "--output"),
                commit,
            )
            "validate" -> validate(
                repository,
                requiredPath(options, "--components"),
                requiredPath(options, "--baseline"),
                options["--output"]?.let(Path::of),
                commit,
            )
            else -> throw DistributionException("unsupported validator command")
        }
    }

    private fun parseOptions(values: List<String>): Map<String, String> {
        if (values.size % 2 != 0) throw DistributionException("validator options must be key/value pairs")
        val allowed = setOf(
            "--repo", "--components", "--output", "--baseline", "--apksig", "--d8", "--android-jar",
            "--bootstrap-aar", "--policy-aar", "--native-aar", "--runtime-templates",
        )
        val result = LinkedHashMap<String, String>()
        var index = 0
        while (index < values.size) {
            val key = values[index]
            if (key !in allowed || result.put(key, values[index + 1]) != null) {
                throw DistributionException("invalid or duplicate validator option")
            }
            index += 2
        }
        return result
    }

    private fun requiredPath(options: Map<String, String>, key: String): Path =
        Path.of(options[key] ?: throw DistributionException("missing $key"))

    private fun requireRepository(repository: Path) {
        if (!Files.isRegularFile(repository.resolve("settings.gradle.kts"), LinkOption.NOFOLLOW_LINKS) ||
            !(Files.isDirectory(repository.resolve(".git"), LinkOption.NOFOLLOW_LINKS) ||
                Files.isRegularFile(repository.resolve(".git"), LinkOption.NOFOLLOW_LINKS))
        ) {
            throw DistributionException("invalid repository root")
        }
    }

    private fun writeRuntimeBundle(repository: Path, options: Map<String, String>) {
        val output = requiredPath(options, "--output").toAbsolutePath().normalize()
        val expected = repository.resolve("distribution/build/generated/v0.2/runtime-bundle").normalize()
        if (output != expected) throw DistributionException("runtime bundle output path is not fixed")
        val work = output.resolveSibling("runtime-bundle-work")
        clearOwnedDirectory(output)
        clearOwnedDirectory(work)
        try {
            val runtimeRoot = output.resolve("ah/runtime")
            Files.createDirectories(runtimeRoot)
            val jars = listOf("--bootstrap-aar", "--policy-aar", "--native-aar").mapIndexed { index, option ->
                val aar = requireRegular(requiredPath(options, option), option)
                val target = work.resolve("runtime-$index.jar")
                Files.createDirectories(target.parent)
                ZipFile(aar.toFile()).use { zip ->
                    val entry = zip.getEntry("classes.jar")
                        ?: throw DistributionException("$option is missing classes.jar")
                    if (entry.isDirectory || entry.size !in 1..(64L * 1024L * 1024L)) {
                        throw DistributionException("$option classes.jar is invalid")
                    }
                    zip.getInputStream(entry).use { input -> Files.copy(input, target) }
                }
                target
            }
            val apksig = requireRegular(requiredPath(options, "--apksig"), "--apksig")
            validateVerifierLibrary(apksig)
            val d8 = requireRegular(requiredPath(options, "--d8"), "--d8")
            val androidJar = requireRegular(requiredPath(options, "--android-jar"), "--android-jar")
            val dexDirectory = work.resolve("d8")
            Files.createDirectories(dexDirectory)
            val log = work.resolve("d8-output.txt")
            val javaName = if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) "java.exe" else "java"
            val arguments = listOf(
                Path.of(System.getProperty("java.home"), "bin", javaName).toString(),
                "-cp", d8.toString(), "com.android.tools.r8.D8", "--min-api", "29", "--lib", androidJar.toString(),
                "--output", dexDirectory.toString(),
            ) + (jars + listOf(apksig)).map(Path::toString)
            val process = ProcessBuilder(arguments).redirectErrorStream(true).redirectOutput(log.toFile()).start()
            if (!process.waitFor(2, TimeUnit.MINUTES)) {
                process.destroyForcibly()
                throw DistributionException("D8 runtime bundle generation timed out")
            }
            if (process.exitValue() != 0) {
                throw DistributionException("D8 runtime bundle generation failed: ${Files.readString(log).takeLast(2_000)}")
            }
            val dexFiles = Files.list(dexDirectory).use { stream ->
                stream.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".dex") }.toList()
            }
            if (dexFiles.size != 1) throw DistributionException("runtime bundle must contain exactly one bootstrap DEX")
            val bootstrap = readRegular(dexFiles.single())
            val bootstrapText = bootstrap.toString(StandardCharsets.ISO_8859_1)
            for (descriptor in listOf(
                "Lah/runtime/bootstrap/ShellAppComponentFactory;",
                "Lah/runtime/guard/RuntimeStartupGuard;",
                "Lah/runtime/loader/PayloadRuntime;",
                "Lcom/android/apksig/ApkVerifier;",
            )) {
                if (descriptor !in bootstrapText) throw DistributionException("runtime bootstrap is missing $descriptor")
            }
            if ("Lah/fixtures/android/" in bootstrapText) {
                throw DistributionException("runtime bootstrap contains fixture classes")
            }
            rejectSigningCapability(bootstrap)
            Files.write(runtimeRoot.resolve("bootstrap.dex"), bootstrap)

            val properties = linkedMapOf(
                "version" to "1",
                "bootstrap.sha256" to V02ReleasePackager.sha256(bootstrap),
            )
            val templates = requiredPath(options, "--runtime-templates").toAbsolutePath().normalize()
            if (!Files.isDirectory(templates, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(templates)) {
                throw DistributionException("runtime template directory is invalid")
            }
            for (abi in listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")) {
                val bytes = readRegular(templates.resolve("$abi/libah_runtime.so"))
                val target = runtimeRoot.resolve("$abi/libah_runtime.so")
                Files.createDirectories(target.parent)
                Files.write(target, bytes)
                properties["$abi.sha256"] = V02ReleasePackager.sha256(bytes)
            }
            val propertyBytes = properties.entries.sortedBy { it.key }
                .joinToString("\n", postfix = "\n") { (key, value) -> "$key=$value" }
                .toByteArray(StandardCharsets.ISO_8859_1)
            Files.write(runtimeRoot.resolve("runtime-bundle-v1.properties"), propertyBytes)
        } finally {
            clearOwnedDirectory(work)
        }
    }

    private fun requireRegular(path: Path, label: String): Path {
        val absolute = path.toAbsolutePath().normalize()
        if (!Files.isRegularFile(absolute, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(absolute)) {
            throw DistributionException("$label is not a regular file")
        }
        return absolute
    }

    private fun writeVerifierLibrary(repository: Path, options: Map<String, String>) {
        val output = requiredPath(options, "--output").toAbsolutePath().normalize()
        val expected = repository.resolve("distribution/build/intermediates/v0.2/apksig/apksig-9.3.0.jar")
        if (output != expected) throw DistributionException("verifier library output path is not fixed")
        val input = requireRegular(requiredPath(options, "--apksig"), "--apksig")
        val r8 = requireRegular(requiredPath(options, "--d8"), "--d8")
        Files.createDirectories(output.parent)
        val rules = output.resolveSibling("verifier-only.pro")
        val temporary = output.resolveSibling("verifier-only-pending.jar")
        val log = output.resolveSibling("r8-output.txt")
        Files.writeString(rules, """
            -dontobfuscate
            -keepattributes *
            -keep @interface com.android.apksig.internal.asn1.Asn1Class { *; }
            -keep @interface com.android.apksig.internal.asn1.Asn1Field { *; }
            -keep enum com.android.apksig.internal.asn1.* { *; }
            -keep @com.android.apksig.internal.asn1.Asn1Class class * { *; }
            -keep class com.android.apksig.ApkVerifier { public *; }
            -keep class com.android.apksig.ApkVerifier${'$'}* { public *; }
            -keep interface com.android.apksig.util.DataSource { *; }
            -keep interface com.android.apksig.util.DataSink { *; }
            -keep class com.android.apksig.util.DataSources { public *; }
            -keep class com.android.apksig.apk.ApkUtils { public *; }
            -keep class com.android.apksig.SigningCertificateLineage {
              public static com.android.apksig.SigningCertificateLineage readFromApkDataSource(com.android.apksig.util.DataSource);
              public java.util.List getCertificatesInLineage();
            }
        """.trimIndent() + "\n", StandardCharsets.UTF_8)
        Files.deleteIfExists(temporary)
        try {
            val javaName = if (System.getProperty("os.name").startsWith("Windows")) "java.exe" else "java"
            val process = ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", javaName).toString(),
                "-cp", r8.toString(), "com.android.tools.r8.R8", "--release", "--classfile",
                "--lib", System.getProperty("java.home"), "--pg-conf", rules.toString(),
                "--output", temporary.toString(), input.toString(),
            ).redirectErrorStream(true).redirectOutput(log.toFile()).start()
            if (!process.waitFor(2, TimeUnit.MINUTES)) {
                process.destroyForcibly()
                throw DistributionException("verifier-only R8 timed out")
            }
            if (process.exitValue() != 0) throw DistributionException("verifier-only R8 failed: ${Files.readString(log).takeLast(2000)}")
            validateVerifierLibrary(temporary)
            Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    internal fun rejectSigningCapability(bytes: ByteArray) {
        val content = bytes.toString(StandardCharsets.ISO_8859_1)
        for (token in listOf(
            "java/security/PrivateKey", "java/security/KeyStore", "initSign", "generateSignature",
            "com/android/apksig/ApkSigner", "ApkSignerEngine", "DefaultApkSignerEngine",
            "com/android/apksig/KeyConfig", "com/android/apksig/SignerEngine", "spawnDescendant",
        )) {
            if (token in content) throw DistributionException("signing capability in verifier bytes: $token")
        }
    }

    internal fun validateVerifierLibrary(path: Path) {
        ZipFile(requireRegular(path, "verifier library").toFile()).use { zip ->
            if (zip.getEntry("com/android/apksig/ApkVerifier.class") == null) {
                throw DistributionException("verifier library is missing ApkVerifier")
            }
            zip.entries().asSequence().filter { it.name.endsWith(".class") }.forEach { entry ->
                rejectSigningCapability(zip.getInputStream(entry).use { it.readAllBytes() })
            }
        }
    }

    private fun stage(repository: Path, requestedComponents: Path, commit: String) {
        val components = requestedComponents.toAbsolutePath().normalize()
        val expected = repository.resolve("distribution/build/v0.2/components").normalize()
        if (components != expected) throw DistributionException("component staging path is not fixed")
        val git = gitEntries(repository, commit)
        fun copyTracked(path: String, target: Path) {
            val entry = git[path] ?: throw DistributionException("component source path is absent from exact commit: $path")
            if (entry.mode !in setOf("100644", "100755")) {
                throw DistributionException("component source Git path is not regular: $path")
            }
            writeBytes(target, gitBlobBytes(repository, entry))
        }
        clearOwnedDirectory(components)
        Files.createDirectories(components)

        copy(
            repository.resolve("distribution/build/v0.2/host/android-app-hardening.jar"),
            components.resolve("lib/android-app-hardening.jar"),
        )
        for (abi in listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")) {
            copy(
                repository.resolve("runtime/native/build/intermediates/stripped_native_libs/release/out/lib/$abi/libah_runtime.so"),
                components.resolve("runtime/$abi/libah_runtime.so"),
            )
        }
        copyTracked(
            "distribution/src/main/resources/v0.2/windows/android-app-hardening.cmd",
            components.resolve("windows/bin/android-app-hardening.cmd"),
        )
        copyTracked(
            "distribution/src/main/resources/v0.2/ubuntu/android-app-hardening",
            components.resolve("ubuntu/bin/android-app-hardening"),
        )
        copyTracked("distribution/docs/QUICKSTART.md", components.resolve("docs/QUICKSTART.md"))
        copyTracked("LICENSE", components.resolve("LICENSE"))
        copyTracked("THIRD_PARTY_NOTICES.md", components.resolve("THIRD_PARTY_NOTICES.md"))

        val placeholder = linkedMapOf<String, Any?>(
            "bomFormat" to "CycloneDX",
            "specVersion" to "1.6",
            "version" to 1L,
            "metadata" to linkedMapOf(
                "timestamp" to CANARY_TIMESTAMP,
                "component" to linkedMapOf(
                    "type" to "application",
                    "bom-ref" to "pkg:generic/android-app-hardening@0.2.0",
                    "group" to "io.github.xiaokh31",
                    "name" to "android-app-hardening",
                    "version" to "0.2.0",
                ),
            ),
            "components" to emptyList<Any?>(),
            "dependencies" to emptyList<Any?>(),
        )
        writeCanonical(components.resolve("bom.cdx.json"), placeholder)
        writeReleaseManifest(repository, components, V02Platform.WINDOWS)
        writeReleaseManifest(repository, components, V02Platform.UBUNTU)
    }

    private fun writeReleaseManifest(repository: Path, components: Path, platform: V02Platform) {
        val selected = componentSpecs(repository, components)
            .filter { it.archivePath != null && (it.platform == "all" || it.platform == platform.wireName) }
            .filter { it.role != "release-manifest" }
            .sortedWith { left, right -> V02ReleasePackager.compareUnsignedUtf8(left.archivePath!!, right.archivePath!!) }
        val entries = selected.map { spec ->
            val source = repository.resolve(spec.sourcePath).normalize()
            val bytes = readRegular(source)
            linkedMapOf<String, Any?>(
                "path" to spec.archivePath,
                "mode" to spec.mode,
                "sizeBytes" to bytes.size.toLong(),
                "sha256" to V02ReleasePackager.sha256(bytes),
                "role" to spec.role,
            )
        }
        val manifest = linkedMapOf<String, Any?>(
            "schemaVersion" to 1L,
            "releaseLine" to "v0.2",
            "releaseVersion" to "0.2.0",
            "sourceCommit" to CANARY_SOURCE_COMMIT,
            "platform" to platform.wireName,
            "entries" to entries,
        )
        writeCanonical(components.resolve("${platform.wireName}/release-manifest.json"), manifest)
    }

    private fun componentSpecs(repository: Path, components: Path): List<ComponentSpec> {
        val componentRelative = repository.relativize(components).joinToString("/")
        fun staged(path: String) = "$componentRelative/$path"
        val values = listOf(
            ComponentSpec("LICENSE", "LICENSE", staged("LICENSE"), "LICENSE", "100644", "license", "source", null, "all"),
            ComponentSpec(
                "THIRD_PARTY_NOTICES.md", "THIRD_PARTY_NOTICES.md", staged("THIRD_PARTY_NOTICES.md"),
                "THIRD_PARTY_NOTICES.md", "100644", "third-party-notices", "source", null, "all",
            ),
            ComponentSpec(
                "bin/android-app-hardening", "bin/android-app-hardening", staged("ubuntu/bin/android-app-hardening"),
                "distribution/src/main/resources/v0.2/ubuntu/android-app-hardening", "100755", "launcher", "ubuntu", null,
                "ubuntu",
            ),
            ComponentSpec(
                "bin/android-app-hardening.cmd", "bin/android-app-hardening.cmd", staged("windows/bin/android-app-hardening.cmd"),
                "distribution/src/main/resources/v0.2/windows/android-app-hardening.cmd", "100755", "launcher", "windows", null,
                "windows",
            ),
            ComponentSpec(
                "bom.cdx.json", "bom.cdx.json", staged("bom.cdx.json"),
                "distribution/src/main/kotlin/ah/distribution/V02ComponentBaselineValidator.kt", "100644", "sbom-slot",
                "synthetic-canary", null, "all",
            ),
            ComponentSpec(
                "contract/archive-byte-contract-v1.json", null,
                "distribution/src/main/resources/v0.2/schemas/archive-byte-contract-v1.json",
                "distribution/src/main/resources/v0.2/schemas/archive-byte-contract-v1.json", "100644", "archive-contract",
                "contract", null, "contract",
            ),
            ComponentSpec(
                "contract/component-baseline-v1.schema.json", null,
                "distribution/src/main/resources/v0.2/schemas/component-baseline-v1.schema.json",
                "distribution/src/main/resources/v0.2/schemas/component-baseline-v1.schema.json", "100644",
                "component-baseline-schema", "contract", null, "contract",
            ),
            ComponentSpec(
                "contract/component-manifest-v1.schema.json", null,
                "distribution/src/main/resources/v0.2/schemas/component-manifest-v1.schema.json",
                "distribution/src/main/resources/v0.2/schemas/component-manifest-v1.schema.json", "100644",
                "component-manifest-schema", "contract", null, "contract",
            ),
            ComponentSpec(
                "contract/provenance-v1.schema.json", null,
                "distribution/src/main/resources/v0.2/schemas/provenance-v1.schema.json",
                "distribution/src/main/resources/v0.2/schemas/provenance-v1.schema.json", "100644", "provenance-schema",
                "contract", null, "contract",
            ),
            ComponentSpec(
                "contract/release-manifest-v1.schema.json", null,
                "distribution/src/main/resources/v0.2/schemas/release-manifest-v1.schema.json",
                "distribution/src/main/resources/v0.2/schemas/release-manifest-v1.schema.json", "100644", "release-manifest-schema",
                "contract", null, "contract",
            ),
            ComponentSpec(
                "contract/v02-component-baseline-validator.kt", null,
                "distribution/src/main/kotlin/ah/distribution/V02ComponentBaselineValidator.kt",
                "distribution/src/main/kotlin/ah/distribution/V02ComponentBaselineValidator.kt", "100644", "baseline-validator",
                "contract", null, "contract",
            ),
            ComponentSpec(
                "contract/v02-release-packager.kt", null,
                "distribution/src/main/kotlin/ah/distribution/V02ReleasePackager.kt",
                "distribution/src/main/kotlin/ah/distribution/V02ReleasePackager.kt", "100644", "release-packager",
                "contract", null, "contract",
            ),
            ComponentSpec(
                "docs/QUICKSTART.md", "docs/QUICKSTART.md", staged("docs/QUICKSTART.md"),
                "distribution/docs/QUICKSTART.md", "100644", "quickstart", "archive-internal", null, "all",
            ),
            ComponentSpec(
                "lib/android-app-hardening.jar", "lib/android-app-hardening.jar", staged("lib/android-app-hardening.jar"),
                "distribution/build.gradle.kts", "100644", "host-release", "release", null, "all",
            ),
            ComponentSpec(
                "release-manifest.json@ubuntu", "release-manifest.json", staged("ubuntu/release-manifest.json"),
                "distribution/src/main/kotlin/ah/distribution/V02ComponentBaselineValidator.kt", "100644", "release-manifest",
                "ubuntu", null, "ubuntu",
            ),
            ComponentSpec(
                "release-manifest.json@windows", "release-manifest.json", staged("windows/release-manifest.json"),
                "distribution/src/main/kotlin/ah/distribution/V02ComponentBaselineValidator.kt", "100644", "release-manifest",
                "windows", null, "windows",
            ),
            ComponentSpec(
                "runtime/arm64-v8a/libah_runtime.so", "runtime/arm64-v8a/libah_runtime.so",
                staged("runtime/arm64-v8a/libah_runtime.so"), "runtime/native/build.gradle.kts", "100644", "runtime-release",
                "Release", "arm64-v8a", "all",
            ),
            ComponentSpec(
                "runtime/armeabi-v7a/libah_runtime.so", "runtime/armeabi-v7a/libah_runtime.so",
                staged("runtime/armeabi-v7a/libah_runtime.so"), "runtime/native/build.gradle.kts", "100644", "runtime-release",
                "Release", "armeabi-v7a", "all",
            ),
            ComponentSpec(
                "runtime/x86/libah_runtime.so", "runtime/x86/libah_runtime.so", staged("runtime/x86/libah_runtime.so"),
                "runtime/native/build.gradle.kts", "100644", "runtime-release", "Release", "x86", "all",
            ),
            ComponentSpec(
                "runtime/x86_64/libah_runtime.so", "runtime/x86_64/libah_runtime.so",
                staged("runtime/x86_64/libah_runtime.so"), "runtime/native/build.gradle.kts", "100644", "runtime-release",
                "Release", "x86_64", "all",
            ),
        )
        return values.sortedWith { left, right -> V02ReleasePackager.compareUnsignedUtf8(left.logicalPath, right.logicalPath) }
    }

    private fun writeManifests(repository: Path, requestedOutput: Path, commit: String) {
        val output = requestedOutput.toAbsolutePath().normalize()
        val expected = repository.resolve("docs/v0.2/evidence/V2-M0-02").normalize()
        if (output != expected) throw DistributionException("manifest output directory is not fixed")
        Files.createDirectories(output)
        expectedManifestValues(repository, commit).forEach { (kind, value) ->
            writeCanonical(output.resolve(MANIFEST_NAMES.getValue(kind)), value)
        }
    }

    internal fun expectedManifestBytes(repository: Path): Map<String, ByteArray> =
        expectedManifestValues(repository, head(repository)).mapValues { CanonicalJson.prettyBytes(it.value) }

    private fun expectedManifestValues(repository: Path, commit: String): LinkedHashMap<String, LinkedHashMap<String, Any?>> {
        val allGit = gitEntries(repository, commit)
        val policyPath = "docs/v0.2/identity-path-policy-v1.json"
        val policyEntry = allGit[policyPath] ?: throw DistributionException("identity path policy is absent from exact commit")
        val policyBytes = gitBlobBytes(repository, policyEntry)
        if (V02ReleasePackager.sha256(policyBytes) != IDENTITY_POLICY_SHA256) {
            throw DistributionException("identity path policy differs from the accepted V2-M0-01 contract")
        }
        val policyValue = StrictJson.parse(policyBytes)
        if (!policyBytes.contentEquals(CanonicalJson.prettyBytes(policyValue))) {
            throw DistributionException("identity path policy is not canonical JSON")
        }
        val policy = policyValue.asObject("identity policy")
        val policies = policy.array("manifestPolicies").map { it.asObject("manifest policy") }
        val result = LinkedHashMap<String, LinkedHashMap<String, Any?>>()
        for (kind in MANIFEST_KINDS) {
            val manifestPolicy = policies.singleOrNull { it.string("manifestKind") == kind }
                ?: throw DistributionException("missing unique $kind policy")
            if (manifestPolicy.string("ownerTask") != "V2-M0-02") throw DistributionException("manifest owner mismatch")
            val exact = manifestPolicy.stringArray("exactPaths")
            val prefixes = manifestPolicy.stringArray("recursivePrefixes")
            val suffixSelectors = manifestPolicy.stringArray("suffixSelectors")
            val rules = manifestPolicy.stringArray("roleRules")
            val selected = LinkedHashSet<String>()
            for (path in exact) {
                if (path !in allGit) throw DistributionException("missing exact manifest path: $path")
                selected += path
            }
            for (prefix in prefixes) {
                val matches = allGit.keys.filter { it.startsWith(prefix) }
                if (matches.isEmpty()) throw DistributionException("empty recursive manifest prefix: $prefix")
                selected += matches
            }
            for (selector in suffixSelectors) selected += applySuffixSelector(allGit.keys, selector)
            val entries = selected.sortedWith(V02ReleasePackager::compareUnsignedUtf8).map { path ->
                val gitEntry = allGit.getValue(path)
                if (gitEntry.mode !in setOf("100644", "100755")) throw DistributionException("non-regular manifest entry")
                val bytes = gitBlobBytes(repository, gitEntry)
                linkedMapOf<String, Any?>(
                    "path" to path,
                    "mode" to gitEntry.mode,
                    "sizeBytes" to bytes.size.toLong(),
                    "sha256" to V02ReleasePackager.sha256(bytes),
                    "gitBlobSha1" to gitEntry.blob,
                    "role" to resolveRole(path, rules),
                )
            }
            result[kind] = linkedMapOf(
                "schemaVersion" to 1L,
                "manifestKind" to kind,
                "taskId" to "V2-M0-02",
                "releaseLine" to "v0.2",
                "entries" to entries,
            )
        }
        return result
    }

    private fun applySuffixSelector(paths: Set<String>, selector: String): List<String> {
        val fields = selector.split(';').associate { field ->
            val split = field.indexOf(':')
            if (split <= 0) throw DistributionException("invalid suffix selector")
            field.substring(0, split) to field.substring(split + 1)
        }
        if (fields.keys != setOf("prefixes", "suffix", "role")) throw DistributionException("invalid suffix selector fields")
        val prefixes = fields.getValue("prefixes").split(',').filter(String::isNotEmpty)
        val suffix = fields.getValue("suffix")
        if (prefixes.isEmpty() || suffix.isEmpty() || fields.getValue("role").isEmpty()) {
            throw DistributionException("empty suffix selector")
        }
        return paths.filter { path -> prefixes.any(path::startsWith) && path.endsWith(suffix) }
    }

    private fun resolveRole(path: String, rules: List<String>): String {
        for (rule in rules) {
            val split = rule.lastIndexOf('=')
            if (split <= 0) throw DistributionException("invalid role rule")
            val matcher = rule.substring(0, split)
            val role = rule.substring(split + 1)
            val colon = matcher.indexOf(':')
            if (colon <= 0 || role.isEmpty()) throw DistributionException("invalid role rule")
            val kind = matcher.substring(0, colon)
            val value = matcher.substring(colon + 1)
            val matches = when (kind) {
                "exact" -> path == value
                "prefix" -> path.startsWith(value)
                "suffix" -> path.endsWith(value)
                else -> throw DistributionException("invalid role rule kind")
            }
            if (matches) return role
        }
        throw DistributionException("manifest path has no role: $path")
    }

    private fun writeBaseline(repository: Path, components: Path, baseline: Path, commit: String) {
        val expectedPath = repository.resolve("docs/v0.2/evidence/V2-M0-02/v02-component-baseline.json").normalize()
        if (baseline.toAbsolutePath().normalize() != expectedPath) throw DistributionException("baseline path is not fixed")
        val value = expectedBaseline(repository, components.toAbsolutePath().normalize(), commit, requireTrackedPreimages = false)
        writeCanonical(expectedPath, value)
    }

    private fun expectedBaseline(
        repository: Path,
        components: Path,
        commit: String,
        requireTrackedPreimages: Boolean = true,
    ): LinkedHashMap<String, Any?> {
        val manifestValues = expectedManifestValues(repository, commit)
        val git = gitEntries(repository, commit)
        val evidence = repository.resolve("docs/v0.2/evidence/V2-M0-02")
        val manifestHashes = LinkedHashMap<String, String>()
        for ((kind, value) in manifestValues) {
            val path = evidence.resolve(MANIFEST_NAMES.getValue(kind))
            val relative = repository.relativize(path).joinToString("/")
            val bytes = if (requireTrackedPreimages) {
                val entry = git[relative] ?: throw DistributionException("manifest preimage is absent from exact commit")
                gitBlobBytes(repository, entry)
            } else {
                readRegular(path)
            }
            val expectedBytes = CanonicalJson.prettyBytes(value)
            validateManifestDocument(bytes, expectedBytes)
            manifestHashes[kind] = V02ReleasePackager.sha256(bytes)
        }
        val entries = componentSpecs(repository, components).map { spec ->
            val source = repository.resolve(spec.sourcePath).normalize()
            val sourceGit = git[spec.sourceGitPath]
                ?: throw DistributionException("component source path is absent from exact commit: ${spec.sourceGitPath}")
            if (sourceGit.mode !in setOf("100644", "100755")) throw DistributionException("component source Git path is not regular")
            val bytes = if (spec.sourcePath == spec.sourceGitPath) {
                gitBlobBytes(repository, sourceGit)
            } else {
                readRegular(source)
            }
            linkedMapOf<String, Any?>(
                "logicalPath" to spec.logicalPath,
                "archivePath" to spec.archivePath,
                "sourcePath" to spec.sourcePath,
                "sourceGitPath" to spec.sourceGitPath,
                "sourceGitBlobSha1" to sourceGit.blob,
                "mode" to spec.mode,
                "sizeBytes" to bytes.size.toLong(),
                "sha256" to V02ReleasePackager.sha256(bytes),
                "role" to spec.role,
                "variant" to spec.variant,
                "abi" to spec.abi,
                "platform" to spec.platform,
            )
        }
        return linkedMapOf(
            "schemaVersion" to 1L,
            "releaseLine" to "v0.2",
            "releaseVersion" to "0.2.0",
            "taskId" to "V2-M0-02",
            "implementationManifestSha256" to manifestHashes.getValue("implementation"),
            "toolchainManifestSha256" to manifestHashes.getValue("toolchain"),
            "productContractManifestSha256" to manifestHashes.getValue("product-contract"),
            "entries" to entries,
        )
    }

    private fun writeCandidate(repository: Path, components: Path, baseline: Path, output: Path, commit: String) {
        val value = expectedCandidate(repository, components.toAbsolutePath().normalize(), baseline, commit)
        val fixedOutput = repository.resolve("build/v0.2/candidate-component-manifest.json").normalize()
        if (output.toAbsolutePath().normalize() != fixedOutput) throw DistributionException("candidate output path is not fixed")
        writeCanonical(fixedOutput, value)
    }

    private fun validate(repository: Path, components: Path, baseline: Path, candidate: Path?, commit: String) {
        val expectedBaseline = expectedBaseline(repository, components.toAbsolutePath().normalize(), commit)
        val baselineBytes = readTrackedBaseline(repository, baseline, commit)
        validateBaselineDocument(baselineBytes, CanonicalJson.prettyBytes(expectedBaseline))
        if (candidate != null && Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)) {
            val expected = CanonicalJson.prettyBytes(expectedCandidate(repository, components.toAbsolutePath().normalize(), baseline, commit))
            if (!readRegular(candidate).contentEquals(expected)) throw DistributionException("ignored candidate manifest drifted")
        }
    }

    internal fun validateManifestDocument(bytes: ByteArray, expectedBytes: ByteArray) {
        val parsed = StrictJson.parse(bytes)
        if (!bytes.contentEquals(CanonicalJson.prettyBytes(parsed)) || !bytes.contentEquals(expectedBytes)) {
            throw DistributionException("tracked manifest differs from exact Git closure")
        }
    }

    internal fun validateBaselineDocument(baselineBytes: ByteArray, expectedBytes: ByteArray) {
        val expectedBaseline = StrictJson.parse(expectedBytes).asObject("expected baseline")
        val parsed = StrictJson.parse(baselineBytes)
        if (!baselineBytes.contentEquals(CanonicalJson.prettyBytes(parsed)) ||
            !baselineBytes.contentEquals(expectedBytes)
        ) {
            throw DistributionException(
                "tracked component baseline differs from exact expected bytes: " +
                    describeBaselineDifference(parsed, expectedBaseline),
            )
        }
        validateBaselineShape(parsed)
    }

    private fun readTrackedBaseline(repository: Path, requested: Path, commit: String): ByteArray {
        val relative = "docs/v0.2/evidence/V2-M0-02/v02-component-baseline.json"
        val expected = repository.resolve(relative).normalize()
        if (requested.toAbsolutePath().normalize() != expected) throw DistributionException("baseline preimage path is not fixed")
        val entry = gitEntries(repository, commit)[relative]
            ?: throw DistributionException("baseline preimage is absent from exact commit")
        val bytes = gitBlobBytes(repository, entry)
        if (!readRegular(expected).contentEquals(bytes)) throw DistributionException("working baseline differs from exact commit")
        return bytes
    }

    private fun describeBaselineDifference(actualValue: Any?, expectedValue: Map<String, Any?>): String {
        val actual = actualValue as? Map<*, *> ?: return "tracked baseline root is not an object"
        val differences = ArrayList<String>()
        for (key in BASELINE_KEYS.filter { it != "entries" }) {
            if (actual[key] != expectedValue[key]) {
                differences += "$key expected=${expectedValue[key]} actual=${actual[key]}"
            }
        }
        val actualEntries = actual["entries"] as? List<*>
        val expectedEntries = expectedValue["entries"] as? List<*>
            ?: return "validator expected entries are not an array"
        if (actualEntries == null) {
            differences += "entries actual value is not an array"
        } else if (actualEntries.size != expectedEntries.size) {
            differences += "entries count expected=${expectedEntries.size} actual=${actualEntries.size}"
        }
        val count = minOf(actualEntries?.size ?: 0, expectedEntries.size)
        for (index in 0 until count) {
            val actualEntry = actualEntries!![index] as? Map<*, *>
            val expectedEntry = expectedEntries[index] as? Map<*, *>
            if (actualEntry == null || expectedEntry == null) {
                differences += "entries[$index] is not an object"
                continue
            }
            val logicalPath = expectedEntry["logicalPath"] ?: "index-$index"
            for (key in BASELINE_ENTRY_KEYS) {
                if (actualEntry[key] != expectedEntry[key]) {
                    differences += "$logicalPath.$key expected=${expectedEntry[key]} actual=${actualEntry[key]}"
                    if (differences.size >= 16) break
                }
            }
            if (differences.size >= 16) break
        }
        return differences.take(16).joinToString("; ").ifEmpty {
            "canonical byte mismatch without a semantic field difference"
        }
    }

    private fun expectedCandidate(repository: Path, components: Path, baseline: Path, commit: String): LinkedHashMap<String, Any?> {
        val expectedBaseline = expectedBaseline(repository, components, commit)
        val baselineBytes = readTrackedBaseline(repository, baseline, commit)
        if (!baselineBytes.contentEquals(CanonicalJson.prettyBytes(expectedBaseline))) {
            throw DistributionException("tracked component baseline is stale")
        }
        val entries = expectedBaseline.array("entries").map { raw ->
            val value = raw.asObject("baseline entry")
            linkedMapOf<String, Any?>(
                "logicalPath" to value.string("logicalPath"),
                "archivePath" to value["archivePath"],
                "sourcePath" to value.string("sourcePath"),
                "mode" to value.string("mode"),
                "sizeBytes" to value.long("sizeBytes"),
                "sha256" to value.string("sha256"),
                "role" to value.string("role"),
                "variant" to value.string("variant"),
                "abi" to value["abi"],
                "platform" to value.string("platform"),
            )
        }
        return linkedMapOf(
            "schemaVersion" to 1L,
            "releaseLine" to "v0.2",
            "releaseVersion" to "0.2.0",
            "sourceCommit" to commit,
            "freezeEpochSeconds" to commitEpoch(repository, commit),
            "implementationManifestSha256" to expectedBaseline.string("implementationManifestSha256"),
            "toolchainManifestSha256" to expectedBaseline.string("toolchainManifestSha256"),
            "productContractManifestSha256" to expectedBaseline.string("productContractManifestSha256"),
            "entries" to entries,
        )
    }

    private fun validateBaselineShape(value: Any?) {
        val root = value.asObject("baseline")
        root.requireKeys(BASELINE_KEYS, "baseline")
        if (root.long("schemaVersion") != 1L || root.string("releaseLine") != "v0.2" ||
            root.string("releaseVersion") != "0.2.0" || root.string("taskId") != "V2-M0-02"
        ) {
            throw DistributionException("baseline identity mismatch")
        }
        for (field in listOf("implementationManifestSha256", "toolchainManifestSha256", "productContractManifestSha256")) {
            if (!SHA256.matches(root.string(field))) throw DistributionException("invalid baseline hash")
        }
        val logical = ArrayList<String>()
        root.array("entries").forEach { raw ->
            val entry = raw.asObject("baseline entry")
            entry.requireKeys(BASELINE_ENTRY_KEYS, "baseline entry")
            logical += entry.string("logicalPath")
            if (!GIT_SHA1.matches(entry.string("sourceGitBlobSha1"))) throw DistributionException("invalid source Git blob")
            if (!SHA256.matches(entry.string("sha256"))) throw DistributionException("invalid component hash")
        }
        for (index in 1 until logical.size) {
            if (V02ReleasePackager.compareUnsignedUtf8(logical[index - 1], logical[index]) >= 0) {
                throw DistributionException("baseline entries are not strictly sorted")
            }
        }
    }

    private fun gitEntries(repository: Path, commit: String): Map<String, GitEntry> {
        if (!GIT_SHA1.matches(commit)) throw DistributionException("exact commit is required")
        val bytes = gitBytes(repository, "ls-tree", "-r", "-z", commit, "--")
        val text = bytes.toString(StandardCharsets.UTF_8)
        if (!text.toByteArray(StandardCharsets.UTF_8).contentEquals(bytes)) throw DistributionException("Git paths are not UTF-8")
        val result = LinkedHashMap<String, GitEntry>()
        val foldedPaths = HashSet<String>()
        for (record in text.split('\u0000')) {
            if (record.isEmpty()) continue
            val tab = record.indexOf('\t')
            if (tab <= 0) throw DistributionException("invalid Git tree record")
            val metadata = record.substring(0, tab).split(' ')
            if (metadata.size != 3 || metadata[1] != "blob" || !GIT_SHA1.matches(metadata[2]) ||
                metadata[0] !in setOf("100644", "100755")) {
                throw DistributionException("non-regular or invalid Git tree record")
            }
            val path = record.substring(tab + 1)
            if (path.isEmpty() || path.startsWith('/') || '\\' in path ||
                path.split('/').any { it.isEmpty() || it == "." || it == ".." } ||
                path.any { it.code < 32 || it.code == 127 } || !Normalizer.isNormalized(path, Normalizer.Form.NFC) ||
                !foldedPaths.add(path.lowercase(java.util.Locale.ROOT))) {
                throw DistributionException("non-canonical or colliding Git tree path")
            }
            if (result.put(path, GitEntry(path, metadata[0], metadata[2])) != null) {
                throw DistributionException("duplicate Git tree path")
            }
        }
        return result
    }

    private fun gitBlobBytes(repository: Path, entry: GitEntry): ByteArray =
        gitBytes(repository, "cat-file", "blob", entry.blob)

    private fun head(repository: Path): String = git(repository, "rev-parse", "HEAD").trim().also {
        if (!GIT_SHA1.matches(it)) throw DistributionException("invalid HEAD")
    }

    private fun commitEpoch(repository: Path, commit: String): Long =
        git(repository, "show", "-s", "--format=%ct", commit).trim().toLongOrNull()
            ?: throw DistributionException("invalid commit epoch")

    private fun git(repository: Path, vararg args: String): String =
        gitBytes(repository, *args).toString(StandardCharsets.UTF_8)

    private fun gitBytes(repository: Path, vararg args: String): ByteArray {
        val command = mutableListOf("git", "-C", repository.toString())
        command += args
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val output = process.inputStream.readAllBytes()
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            throw DistributionException("Git command timed out")
        }
        if (process.exitValue() != 0) {
            throw DistributionException("Git command failed: ${output.toString(StandardCharsets.UTF_8).trim()}")
        }
        return output
    }

    private fun copy(source: Path, target: Path) {
        writeBytes(target, readRegular(source))
    }

    private fun writeBytes(target: Path, bytes: ByteArray) {
        Files.createDirectories(target.parent)
        Files.write(target, bytes)
    }

    private fun readRegular(path: Path): ByteArray {
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw DistributionException("required regular file is missing: $path")
        }
        return Files.readAllBytes(path)
    }

    private fun clearOwnedDirectory(path: Path) {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return
        if (Files.isSymbolicLink(path) || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            throw DistributionException("owned build directory is not a regular directory")
        }
        Files.walk(path).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach { current ->
                if (Files.isSymbolicLink(current)) throw DistributionException("symlink in owned build directory")
                Files.delete(current)
            }
        }
    }

    private fun writeCanonical(path: Path, value: Any?) {
        val bytes = CanonicalJson.prettyBytes(value)
        Files.createDirectories(path.parent)
        val temp = Files.createTempFile(path.parent, ".${path.fileName}.", ".tmp")
        var moved = false
        try {
            Files.write(temp, bytes)
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            moved = true
        } finally {
            if (!moved) Files.deleteIfExists(temp)
        }
    }

    private fun LinkedHashMap<String, Any?>.requireKeys(keys: List<String>, label: String) {
        if (this.keys.toList() != keys) throw DistributionException("$label fields are missing, extra, or reordered")
    }

    private fun LinkedHashMap<String, Any?>.string(key: String): String =
        this[key] as? String ?: throw DistributionException("$key must be a string")

    private fun LinkedHashMap<String, Any?>.long(key: String): Long =
        this[key] as? Long ?: throw DistributionException("$key must be an integer")

    private fun LinkedHashMap<String, Any?>.array(key: String): List<Any?> =
        this[key] as? List<Any?> ?: throw DistributionException("$key must be an array")

    private fun LinkedHashMap<String, Any?>.stringArray(key: String): List<String> =
        array(key).map { it as? String ?: throw DistributionException("$key must contain strings") }

    @Suppress("UNCHECKED_CAST")
    private fun Any?.asObject(label: String): LinkedHashMap<String, Any?> =
        this as? LinkedHashMap<String, Any?> ?: throw DistributionException("$label must be an object")
}
