package ah.distribution

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

object V02ReleasePackagerTest {
    @JvmStatic
    fun main(args: Array<String>) {
        val fixture = V02TestFixture.create("packager")
        try {
            val manifest = Files.readAllBytes(fixture.manifest)
            check(V02ReleasePackager.parseManifest(manifest).entries.size == 13)
            mutation(manifest, "\"schemaVersion\": 1", "\"schemaVersion\": 2")
            mutation(manifest, "\"releaseVersion\": \"0.2.0\"", "\"releaseVersion\": \"0.1.0-dev\"")
            mutation(manifest, "\"mode\": \"100644\"", "\"mode\": \"100600\"")
            mutation(manifest, "\"abi\": \"arm64-v8a\"", "\"abi\": \"mips\"")
            mutation(manifest, "\"logicalPath\": \"LICENSE\"", "\"logicalPath\": \"../LICENSE\"")
            mutation(manifest, "\"logicalPath\": \"LICENSE\"", "\"logicalPath\": \"LICE\\\\NSE\"")
            mutation(manifest, "\"entries\": [", "\"unexpected\": true,\n  \"entries\": [")
            expectFailure { V02ReleasePackager.parseManifest("{\"a\":1,\"a\":2}".toByteArray()) }
            expectFailure { V02ReleasePackager.parseManifest(byteArrayOf(0xef.toByte(), 0xbb.toByte(), 0xbf.toByte())) }

            val windows = fixture.root.resolve("windows.zip")
            val ubuntu = fixture.root.resolve("ubuntu.tar.gz")
            V02ReleasePackager.packageArchive(fixture.manifest, V02Platform.WINDOWS, windows)
            V02ReleasePackager.packageArchive(fixture.manifest, V02Platform.UBUNTU, ubuntu)
            V02ArchiveVerifier.verifyZip(Files.readAllBytes(windows), fixture.freezeEpoch)
            V02ArchiveVerifier.verifyTarGzip(Files.readAllBytes(ubuntu), fixture.freezeEpoch)
            expectFailure { V02ReleasePackager.packageArchive(fixture.manifest, V02Platform.WINDOWS, windows) }

            val forbidden = manifest.toString(StandardCharsets.UTF_8).replace(
                "    }\n  ]",
                "    },\n    {\n      \"logicalPath\": \"secret.key\",\n      \"archivePath\": \"secret.key\",\n      \"sourcePath\": \"inputs/LICENSE\",\n      \"mode\": \"100644\",\n      \"sizeBytes\": 8,\n      \"sha256\": \"${"0".repeat(64)}\",\n      \"role\": \"secret\",\n      \"variant\": \"source\",\n      \"abi\": null,\n      \"platform\": \"all\"\n    }\n  ]",
            )
            expectFailure { V02ReleasePackager.parseManifest(forbidden.toByteArray()) }
        } finally {
            fixture.root.toFile().deleteRecursively()
        }
        println("V2-M0-02 release packager self-test PASS")
    }

    private fun mutation(bytes: ByteArray, before: String, after: String) {
        val text = bytes.toString(StandardCharsets.UTF_8)
        check(before in text)
        expectFailure { V02ReleasePackager.parseManifest(text.replaceFirst(before, after).toByteArray()) }
    }
}

internal data class V02Fixture(val root: Path, val manifest: Path, val freezeEpoch: Long)

internal object V02TestFixture {
    fun create(label: String): V02Fixture {
        val root = Files.createTempDirectory("v02-$label-")
        Files.createDirectory(root.resolve(".git"))
        Files.writeString(root.resolve("settings.gradle.kts"), "rootProject.name = \"fixture\"\n")
        val inputs = root.resolve("inputs")
        Files.createDirectories(inputs)
        val specs = listOf(
            Spec("LICENSE", "LICENSE", "LICENSE", "100644", "license", "source", null, "all"),
            Spec("THIRD_PARTY_NOTICES.md", "THIRD_PARTY_NOTICES.md", "THIRD_PARTY_NOTICES.md", "100644", "notices", "source", null, "all"),
            Spec("bin/android-app-hardening", "bin/android-app-hardening", "ubuntu-launcher", "100755", "launcher", "ubuntu", null, "ubuntu"),
            Spec("bin/android-app-hardening.cmd", "bin/android-app-hardening.cmd", "windows-launcher", "100755", "launcher", "windows", null, "windows"),
            Spec("bom.cdx.json", "bom.cdx.json", "bom.cdx.json", "100644", "sbom-slot", "canary", null, "all"),
            Spec("docs/QUICKSTART.md", "docs/QUICKSTART.md", "QUICKSTART.md", "100644", "quickstart", "archive", null, "all"),
            Spec("lib/android-app-hardening.jar", "lib/android-app-hardening.jar", "host.jar", "100644", "host-release", "release", null, "all"),
            Spec("release-manifest.json@ubuntu", "release-manifest.json", "ubuntu-release.json", "100644", "release-manifest", "ubuntu", null, "ubuntu"),
            Spec("release-manifest.json@windows", "release-manifest.json", "windows-release.json", "100644", "release-manifest", "windows", null, "windows"),
            Spec("runtime/arm64-v8a/libah_runtime.so", "runtime/arm64-v8a/libah_runtime.so", "arm64.so", "100644", "runtime-release", "Release", "arm64-v8a", "all"),
            Spec("runtime/armeabi-v7a/libah_runtime.so", "runtime/armeabi-v7a/libah_runtime.so", "arm.so", "100644", "runtime-release", "Release", "armeabi-v7a", "all"),
            Spec("runtime/x86/libah_runtime.so", "runtime/x86/libah_runtime.so", "x86.so", "100644", "runtime-release", "Release", "x86", "all"),
            Spec("runtime/x86_64/libah_runtime.so", "runtime/x86_64/libah_runtime.so", "x64.so", "100644", "runtime-release", "Release", "x86_64", "all"),
        ).sortedWith { left, right -> V02ReleasePackager.compareUnsignedUtf8(left.logical, right.logical) }
        val entries = specs.mapIndexed { index, spec ->
            val bytes = "v0.2 fixture ${spec.logical} $index\n".toByteArray(StandardCharsets.UTF_8)
            Files.write(inputs.resolve(spec.source), bytes)
            linkedMapOf<String, Any?>(
                "logicalPath" to spec.logical,
                "archivePath" to spec.archive,
                "sourcePath" to "inputs/${spec.source}",
                "mode" to spec.mode,
                "sizeBytes" to bytes.size.toLong(),
                "sha256" to V02ReleasePackager.sha256(bytes),
                "role" to spec.role,
                "variant" to spec.variant,
                "abi" to spec.abi,
                "platform" to spec.platform,
            )
        }
        val freeze = 1_700_000_001L
        val manifestValue = linkedMapOf<String, Any?>(
            "schemaVersion" to 1L,
            "releaseLine" to "v0.2",
            "releaseVersion" to "0.2.0",
            "sourceCommit" to "a".repeat(40),
            "freezeEpochSeconds" to freeze,
            "implementationManifestSha256" to "1".repeat(64),
            "toolchainManifestSha256" to "2".repeat(64),
            "productContractManifestSha256" to "3".repeat(64),
            "entries" to entries,
        )
        val manifest = root.resolve("build/v0.2/candidate-component-manifest.json")
        Files.createDirectories(manifest.parent)
        Files.write(manifest, CanonicalJson.prettyBytes(manifestValue))
        return V02Fixture(root, manifest, freeze)
    }

    private data class Spec(
        val logical: String,
        val archive: String,
        val source: String,
        val mode: String,
        val role: String,
        val variant: String,
        val abi: String?,
        val platform: String,
    )
}

internal fun expectFailure(action: () -> Unit) {
    check(runCatching(action).isFailure) { "expected failure" }
}
