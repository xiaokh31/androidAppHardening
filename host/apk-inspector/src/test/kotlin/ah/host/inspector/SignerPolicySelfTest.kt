package ah.host.inspector

import com.android.apksig.SigningCertificateLineage
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Signature
import java.time.Instant
import java.util.Locale
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText

object SignerPolicySelfTest {
    @JvmStatic
    fun main(args: Array<String>) {
        val reportDir = Path.of(requireNotNull(System.getProperty("ah.signer.reportDir")))
        val fixtureDir = reportDir.resolve("fixtures")
        reportDir.createDirectories()
        fixtureDir.createDirectories()
        val tools = AndroidSigningTools.locate()
        val oldIdentity = TestIdentity.create(fixtureDir, "old", "M1-02 old fixture signer", 0x4d31303201L)
        val currentIdentity = TestIdentity.create(fixtureDir, "current", "M1-02 current fixture signer", 0x4d31303202L)
        val otherIdentity = TestIdentity.create(fixtureDir, "other", "M1-02 other fixture signer", 0x4d31303203L)
        val unsigned = fixtureDir.resolve("unsigned.apk")
        unsigned.writeBytes(
            SyntheticApkFixtures.apk(
                SyntheticApkFixtures.baselineEntries(
                    manifest = SyntheticApkFixtures.manifest(targetSdk = 29),
                ),
            ),
        )

        val v1 = signSingle(tools, unsigned, fixtureDir.resolve("v1.apk"), currentIdentity, v1 = true)
        val v2 = signSingle(tools, unsigned, fixtureDir.resolve("v2.apk"), currentIdentity, v2 = true)
        val v3 = signSingle(tools, unsigned, fixtureDir.resolve("v3.apk"), currentIdentity, v3 = true)
        val combined = signSingle(
            tools,
            unsigned,
            fixtureDir.resolve("combined.apk"),
            currentIdentity,
            v1 = true,
            v2 = true,
            v3 = true,
        )
        val v4 = signSingle(
            tools,
            unsigned,
            fixtureDir.resolve("v4.apk"),
            currentIdentity,
            v2 = true,
            v3 = true,
            v4 = true,
        )
        val lineageFile = fixtureDir.resolve("lineage.bin")
        createLineage(tools, lineageFile, oldIdentity, currentIdentity)
        val rotated = signRotated(tools, unsigned, fixtureDir.resolve("rotated.apk"), lineageFile, oldIdentity, currentIdentity)
        val multiple = signMultiple(tools, unsigned, fixtureDir.resolve("multiple.apk"), currentIdentity, otherIdentity)

        val policies = linkedMapOf(
            "v1" to verify(v1),
            "v2" to verify(v2),
            "v3" to verify(v3),
            "combined" to verify(combined),
            "v4-apk" to verify(v4),
            "rotated" to verify(rotated),
        )
        check(policies.getValue("v1").verifiedSchemes == setOf(VerifiedScheme.V1))
        check(policies.getValue("v2").verifiedSchemes == setOf(VerifiedScheme.V2))
        check(policies.getValue("v3").verifiedSchemes == setOf(VerifiedScheme.V3))
        check(policies.getValue("combined").verifiedSchemes == setOf(VerifiedScheme.V3))
        check(VerifiedScheme.V4 !in policies.getValue("v4-apk").verifiedSchemes)
        check(Files.exists(v4.resolveSibling("${v4.name}.idsig")))
        val rotatedPolicy = policies.getValue("rotated")
        check(rotatedPolicy.lineageCertificateSha256Hex.size == 2)
        check(rotatedPolicy.lineageCertificateSha256Hex.last() == rotatedPolicy.currentCertificateSha256Hex)
        check(rotatedPolicy.currentCertificateSha256Hex == sha256(currentIdentity.certificate.readBytes()).toLowerHex())

        val officialDigest = tools.printCurrentCertificateSha256(combined)
        check(officialDigest == policies.getValue("combined").currentCertificateSha256Hex)
        check(officialDigest == sha256(currentIdentity.certificate.readBytes()).toLowerHex())
        exerciseDefensiveCopies(rotatedPolicy)
        exerciseSpv1ModelValidation()

        val malformedBlock = fixtureDir.resolve("malformed-signing-block.apk")
        malformedBlock.writeBytes(corruptSigningBlock(v2.readBytes(), corruptTrailer = true))
        val tamperedBlock = fixtureDir.resolve("tampered-signing-block.apk")
        tamperedBlock.writeBytes(corruptSignedContent(v2.readBytes()))
        val invalidLineage = fixtureDir.resolve("invalid-lineage.apk")
        invalidLineage.writeBytes(corruptLineage(rotated.readBytes(), lineageFile))

        val errorMatrix = linkedMapOf(
            "unsigned" to expectError(unsigned, SignerErrorCode.SIGNER_UNSIGNED),
            "tampered" to expectError(tamperedBlock, SignerErrorCode.SIGNER_INVALID),
            "malformed_block" to expectError(malformedBlock, SignerErrorCode.SIGNER_INVALID),
            "multiple_current" to expectError(multiple, SignerErrorCode.SIGNER_MULTIPLE_CURRENT),
            "invalid_lineage" to expectError(invalidLineage, SignerErrorCode.SIGNER_LINEAGE_INVALID),
            "inspection_mismatch" to expectInspectionMismatch(v2, policies.getValue("combined")),
            "input_changed" to expectConcurrentInputChange(v2, fixtureDir.resolve("changed-during-verification.apk")),
        )
        check(errorMatrix.values.toSet() == setOf(
            SignerErrorCode.SIGNER_UNSIGNED,
            SignerErrorCode.SIGNER_INVALID,
            SignerErrorCode.SIGNER_MULTIPLE_CURRENT,
            SignerErrorCode.SIGNER_LINEAGE_INVALID,
            SignerErrorCode.SIGNER_INPUT_CHANGED,
        ))
        scanProductionCapabilities(reportDir)

        val policyJson = canonicalPolicyJson(rotatedPolicy)
        val errorJson = canonicalErrorJson(errorMatrix)
        reportDir.resolve("canonical-policy.json").writeText(policyJson, StandardCharsets.UTF_8)
        reportDir.resolve("error-matrix.json").writeText(errorJson, StandardCharsets.UTF_8)
        reportDir.resolve("artifact-manifest.json").writeText(
            artifactManifest(
                listOf(
                    unsigned,
                    v1,
                    v2,
                    v3,
                    combined,
                    v4,
                    rotated,
                    multiple,
                    oldIdentity.certificate,
                    currentIdentity.certificate,
                    otherIdentity.certificate,
                    lineageFile,
                    reportDir.resolve("canonical-policy.json"),
                    reportDir.resolve("error-matrix.json"),
                ),
            ),
            StandardCharsets.UTF_8,
        )
        println("M1-02 signer policy matrix PASS")
        println("canonical_policy_sha256=${sha256(policyJson.toByteArray(StandardCharsets.UTF_8)).toLowerHex()}")
        println("error_matrix_sha256=${sha256(errorJson.toByteArray(StandardCharsets.UTF_8)).toLowerHex()}")
        println("current_certificate_sha256=$officialDigest")
    }

    private fun verify(apk: Path): SignerPolicyV1 {
        val inspection = ApkInspector().inspect(apk)
        return SignerPolicyVerifier().verify(apk, inspection)
    }

    private fun expectError(apk: Path, expected: SignerErrorCode): SignerErrorCode {
        val inspection = ApkInspector().inspect(apk)
        val exception = runCatching { SignerPolicyVerifier().verify(apk, inspection) }.exceptionOrNull()
        check(exception is SignerPolicyException) { "expected $expected for ${apk.name}, got $exception" }
        check(exception.code == expected) { "expected $expected for ${apk.name}, got ${exception.code}" }
        check(!exception.message.orEmpty().contains(apk.toAbsolutePath().parent.toString()))
        return exception.code
    }

    private fun expectInspectionMismatch(apk: Path, unrelatedPolicy: SignerPolicyV1): SignerErrorCode {
        val inspection = ApkInspector().inspect(apk)
        val wrongDigest = unrelatedPolicy.currentCertificateSha256
        wrongDigest[0] = (wrongDigest[0].toInt() xor 0x01).toByte()
        val mismatched = ApkInspection(
            inputSha256 = wrongDigest,
            manifest = inspection.manifest,
            zipEntries = inspection.zipEntries,
            dexEntries = inspection.dexEntries,
            nativeAbis = inspection.nativeAbis,
            findings = inspection.findings,
            compatibilityRulesVersion = inspection.compatibilityRulesVersion,
            limitsApplied = inspection.limitsApplied,
        )
        val exception = runCatching { SignerPolicyVerifier().verify(apk, mismatched) }.exceptionOrNull()
        check(exception is SignerPolicyException && exception.code == SignerErrorCode.SIGNER_INPUT_CHANGED)
        return exception.code
    }

    private fun expectConcurrentInputChange(source: Path, target: Path): SignerErrorCode {
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
        val inspection = ApkInspector().inspect(target)
        val verifier = SignerPolicyVerifier(afterInitialSnapshot = { path ->
            val bytes = path.readBytes()
            bytes[0] = (bytes[0].toInt() xor 0x01).toByte()
            path.writeBytes(bytes)
        })
        val exception = runCatching { verifier.verify(target, inspection) }.exceptionOrNull()
        check(exception is SignerPolicyException && exception.code == SignerErrorCode.SIGNER_INPUT_CHANGED)
        return exception.code
    }

    private fun exerciseDefensiveCopies(policy: SignerPolicyV1) {
        val current = policy.currentCertificateSha256
        val original = current[0]
        current[0] = (current[0].toInt() xor 0xff).toByte()
        check(policy.currentCertificateSha256[0] == original)
        val lineage = policy.lineageCertificateSha256
        val lineageOriginal = lineage[0][0]
        lineage[0][0] = (lineage[0][0].toInt() xor 0xff).toByte()
        check(policy.lineageCertificateSha256[0][0] == lineageOriginal)
        check(policy.currentCertificateSha256Hex.matches(Regex("[0-9a-f]{64}")))
        check(policy.toReport() == SignerPolicyReport(true, policy.currentCertificateSha256Hex, true, false))
    }

    private fun exerciseSpv1ModelValidation() {
        val current = ByteArray(32) { 0x11 }
        val old = ByteArray(32) { 0x22 }
        SignerPolicyV1(current, listOf(current), setOf(VerifiedScheme.V3))
        SignerPolicyV1(current, listOf(old, current), setOf(VerifiedScheme.V3))
        expectModelFailure { SignerPolicyV1(current, emptyList(), emptySet()) }
        expectModelFailure { SignerPolicyV1(current, List(17) { index -> ByteArray(32) { index.toByte() } }, emptySet()) }
        expectModelFailure { SignerPolicyV1(current, listOf(old, old, current), emptySet()) }
        expectModelFailure { SignerPolicyV1(current, listOf(old), emptySet()) }
        expectModelFailure { SignerPolicyV1(ByteArray(31), listOf(ByteArray(31)), emptySet()) }
    }

    private fun expectModelFailure(block: () -> Unit) {
        check(runCatching(block).exceptionOrNull() is IllegalArgumentException)
    }

    private fun scanProductionCapabilities(reportDir: Path) {
        val repository = findRepositoryRoot()
        val sourceRoot = repository.resolve("host/apk-inspector/src/main")
        val classRoot = repository.resolve("host/apk-inspector/build/classes/kotlin/main")
        val forbidden = listOf(
            "com.android.apksig.apksigner",
            "java.security.privatekey",
            "java.security.keystore",
            "keypassword",
            "storepassword",
            "signingexecutor",
            "processbuilder",
        )
        val scanned = ArrayList<String>()
        Files.walk(sourceRoot).use { paths ->
            paths.filter { it.isRegularFile() }.sorted().forEach { path ->
                val text = Files.readString(path, StandardCharsets.UTF_8).lowercase(Locale.ROOT)
                forbidden.forEach { token -> check(token !in text) { "forbidden production source capability: $token" } }
                scanned += repository.relativize(path).toString().replace('\\', '/')
            }
        }
        if (classRoot.exists()) {
            Files.walk(classRoot).use { paths ->
                paths.filter { it.isRegularFile() }.sorted().forEach { path ->
                    val text = path.readBytes().toString(StandardCharsets.ISO_8859_1).lowercase(Locale.ROOT)
                    forbidden.forEach { token -> check(token !in text) { "forbidden production bytecode capability: $token" } }
                }
            }
        }
        reportDir.resolve("capability-scan.txt").writeText(
            "result=PASS\nproduction_source_files=${scanned.size}\nforbidden_tokens=${forbidden.joinToString(",")}\n",
            StandardCharsets.UTF_8,
        )
    }

    private fun signSingle(
        tools: AndroidSigningTools,
        input: Path,
        output: Path,
        identity: TestIdentity,
        v1: Boolean = false,
        v2: Boolean = false,
        v3: Boolean = false,
        v4: Boolean = false,
    ): Path {
        if (output.exists() && (!v4 || output.resolveSibling("${output.name}.idsig").exists())) return output
        tools.run(
            "sign",
            "--key", identity.privateKey.toString(),
            "--cert", identity.certificate.toString(),
            "--v1-signing-enabled", v1.toString(),
            "--v2-signing-enabled", v2.toString(),
            "--v3-signing-enabled", v3.toString(),
            "--v4-signing-enabled", v4.toString(),
            "--out", output.toString(),
            input.toString(),
        )
        return output
    }

    private fun createLineage(tools: AndroidSigningTools, output: Path, old: TestIdentity, current: TestIdentity) {
        if (output.exists()) return
        tools.run(
            "rotate",
            "--out", output.toString(),
            "--old-signer",
            "--key", old.privateKey.toString(),
            "--cert", old.certificate.toString(),
            "--new-signer",
            "--key", current.privateKey.toString(),
            "--cert", current.certificate.toString(),
        )
    }

    private fun signRotated(
        tools: AndroidSigningTools,
        input: Path,
        output: Path,
        lineage: Path,
        old: TestIdentity,
        current: TestIdentity,
    ): Path {
        if (output.exists()) return output
        tools.run(
            "sign",
            "--key", old.privateKey.toString(),
            "--cert", old.certificate.toString(),
            "--next-signer",
            "--key", current.privateKey.toString(),
            "--cert", current.certificate.toString(),
            "--lineage", lineage.toString(),
            "--v1-signing-enabled", "true",
            "--v2-signing-enabled", "true",
            "--v3-signing-enabled", "true",
            "--v4-signing-enabled", "false",
            "--out", output.toString(),
            input.toString(),
        )
        return output
    }

    private fun signMultiple(
        tools: AndroidSigningTools,
        input: Path,
        output: Path,
        first: TestIdentity,
        second: TestIdentity,
    ): Path {
        if (output.exists()) return output
        tools.run(
            "sign",
            "--key", first.privateKey.toString(),
            "--cert", first.certificate.toString(),
            "--next-signer",
            "--key", second.privateKey.toString(),
            "--cert", second.certificate.toString(),
            "--v1-signing-enabled", "false",
            "--v2-signing-enabled", "true",
            "--v3-signing-enabled", "false",
            "--v4-signing-enabled", "false",
            "--out", output.toString(),
            input.toString(),
        )
        return output
    }

    private fun corruptSigningBlock(apk: ByteArray, corruptTrailer: Boolean): ByteArray {
        val result = apk.copyOf()
        val magic = "APK Sig Block 42".toByteArray(StandardCharsets.US_ASCII)
        val magicOffset = findBytes(result, magic)
        val offset = if (corruptTrailer) magicOffset - 8 else magicOffset - 32
        check(offset >= 0)
        result[offset] = (result[offset].toInt() xor 0x40).toByte()
        return result
    }

    private fun corruptSignedContent(apk: ByteArray): ByteArray {
        val result = apk.copyOf()
        val centralHeader = byteArrayOf(0x50, 0x4b, 0x01, 0x02)
        val centralOffset = findBytes(result, centralHeader)
        val externalAttributes = centralOffset + 38
        check(externalAttributes < result.size)
        result[externalAttributes] = (result[externalAttributes].toInt() xor 0x01).toByte()
        return result
    }

    private fun corruptLineage(apk: ByteArray, lineageFile: Path): ByteArray {
        val encoded = SigningCertificateLineage.readFromFile(lineageFile.toFile()).encodeSigningCertificateLineage()
        val result = apk.copyOf()
        val offset = findBytesOrNegative(result, encoded).takeIf { it >= 0 }
            ?: error("lineage bytes not found in rotated fixture")
        // The final record contains the proof signed by the previous signer. Mutating its
        // signature (rather than certificate metadata) makes the official lineage parser reject it.
        val mutateAt = offset + encoded.size - 16
        result[mutateAt] = (result[mutateAt].toInt() xor 0x01).toByte()
        return result
    }

    private fun canonicalPolicyJson(policy: SignerPolicyV1): String = buildString {
        append("{\n")
        append("  \"policy_version\": 1,\n")
        append("  \"current_certificate_sha256\": \"").append(policy.currentCertificateSha256Hex).append("\",\n")
        append("  \"lineage_certificate_sha256\": [")
        append(policy.lineageCertificateSha256Hex.joinToString(",") { "\"$it\"" })
        append("],\n")
        append("  \"verified_schemes\": [")
        append(policy.verifiedSchemes.joinToString(",") { "\"${it.name}\"" })
        append("],\n")
        append("  \"required_after_protection\": true,\n")
        append("  \"performed_by_product\": false\n")
        append("}\n")
    }

    private fun canonicalErrorJson(values: Map<String, SignerErrorCode>): String = buildString {
        append("{\n")
        values.entries.forEachIndexed { index, entry ->
            append("  \"").append(entry.key).append("\": \"").append(entry.value.name).append('"')
            if (index != values.size - 1) append(',')
            append('\n')
        }
        append("}\n")
    }

    private fun artifactManifest(paths: List<Path>): String = buildString {
        append("{\n  \"fixture_identity\": \"deterministic synthetic test-only, no production value\",\n")
        append("  \"generated_at\": \"").append(Instant.EPOCH).append("\",\n")
        append("  \"artifacts\": {\n")
        paths.sortedBy { it.name }.forEachIndexed { index, path ->
            append("    \"").append(path.name).append("\": \"").append(sha256(path.readBytes()).toLowerHex()).append('"')
            if (index != paths.size - 1) append(',')
            append('\n')
        }
        append("  }\n}\n")
    }

    private fun findRepositoryRoot(): Path {
        var current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()
        while (!Files.exists(current.resolve("settings.gradle.kts"))) {
            current = current.parent ?: error("repository root not found")
        }
        return current
    }

    private fun findBytes(haystack: ByteArray, needle: ByteArray): Int =
        findBytesOrNegative(haystack, needle).also { check(it >= 0) { "byte sequence not found" } }

    private fun findBytesOrNegative(haystack: ByteArray, needle: ByteArray): Int {
        if (needle.isEmpty() || needle.size > haystack.size) return -1
        for (offset in 0..haystack.size - needle.size) {
            if (needle.indices.all { index -> haystack[offset + index] == needle[index] }) return offset
        }
        return -1
    }

    private fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

    private fun ByteArray.toLowerHex(): String = joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private data class TestIdentity(val privateKey: Path, val certificate: Path) {
        companion object {
            fun create(directory: Path, id: String, commonName: String, seed: Long): TestIdentity {
                val key = directory.resolve("$id-key.pk8")
                val certificate = directory.resolve("$id-cert.der")
                if (key.exists() && certificate.exists()) return TestIdentity(key, certificate)
                val random = SecureRandom.getInstance("SHA1PRNG").apply {
                    setSeed(ByteBuffer.allocate(Long.SIZE_BYTES).putLong(seed).array())
                }
                val generator = KeyPairGenerator.getInstance("RSA")
                generator.initialize(2048, random)
                val keyPair = generator.generateKeyPair()
                key.writeBytes(keyPair.private.encoded)
                certificate.writeBytes(SelfSignedCertificate.create(keyPair, commonName, seed))
                return TestIdentity(key, certificate)
            }
        }
    }

    private object SelfSignedCertificate {
        private val sha256WithRsa = sequence(oid(1, 2, 840, 113549, 1, 1, 11), derNull())

        fun create(keyPair: KeyPair, commonName: String, serial: Long): ByteArray {
            val name = sequence(set(sequence(oid(2, 5, 4, 3), utf8(commonName))))
            val tbs = sequence(
                explicit(0, integer(byteArrayOf(2))),
                integer(ByteBuffer.allocate(Long.SIZE_BYTES).putLong(serial).array().dropWhile { it == 0.toByte() }.toByteArray()),
                sha256WithRsa,
                name,
                sequence(generalizedTime("20260101000000Z"), generalizedTime("20360101000000Z")),
                name,
                keyPair.public.encoded,
            )
            val signature = Signature.getInstance("SHA256withRSA").run {
                initSign(keyPair.private)
                update(tbs)
                sign()
            }
            return sequence(tbs, sha256WithRsa, bitString(signature))
        }

        private fun sequence(vararg values: ByteArray): ByteArray = tagged(0x30, values.concat())
        private fun set(value: ByteArray): ByteArray = tagged(0x31, value)
        private fun explicit(tag: Int, value: ByteArray): ByteArray = tagged(0xa0 + tag, value)
        private fun integer(value: ByteArray): ByteArray {
            val normalized = if (value.isEmpty()) byteArrayOf(0) else value
            return tagged(0x02, if ((normalized[0].toInt() and 0x80) != 0) byteArrayOf(0) + normalized else normalized)
        }
        private fun utf8(value: String): ByteArray = tagged(0x0c, value.toByteArray(StandardCharsets.UTF_8))
        private fun generalizedTime(value: String): ByteArray = tagged(0x18, value.toByteArray(StandardCharsets.US_ASCII))
        private fun derNull(): ByteArray = byteArrayOf(0x05, 0x00)
        private fun bitString(value: ByteArray): ByteArray = tagged(0x03, byteArrayOf(0) + value)

        private fun oid(vararg components: Int): ByteArray {
            require(components.size >= 2)
            val output = ByteArrayOutputStream()
            output.write(components[0] * 40 + components[1])
            components.drop(2).forEach { component ->
                val encoded = ArrayList<Int>()
                var value = component
                encoded += value and 0x7f
                value = value ushr 7
                while (value > 0) {
                    encoded.add(0, (value and 0x7f) or 0x80)
                    value = value ushr 7
                }
                encoded.forEach(output::write)
            }
            return tagged(0x06, output.toByteArray())
        }

        private fun tagged(tag: Int, value: ByteArray): ByteArray =
            byteArrayOf(tag.toByte()) + derLength(value.size) + value

        private fun derLength(length: Int): ByteArray {
            if (length < 0x80) return byteArrayOf(length.toByte())
            val bytes = ByteBuffer.allocate(Int.SIZE_BYTES).putInt(length).array().dropWhile { it == 0.toByte() }.toByteArray()
            return byteArrayOf((0x80 or bytes.size).toByte()) + bytes
        }

        private fun Array<out ByteArray>.concat(): ByteArray = fold(ByteArray(0)) { result, bytes -> result + bytes }
    }

    private class AndroidSigningTools private constructor(private val executable: Path) {
        fun run(vararg arguments: String): String {
            val command = if (isWindows()) {
                listOf("cmd.exe", "/d", "/c", executable.toString()) + arguments
            } else {
                listOf(executable.toString()) + arguments
            }
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            val exitCode = process.waitFor()
            check(exitCode == 0) { "${executable.fileName} failed with exit code $exitCode" }
            return output
        }

        fun printCurrentCertificateSha256(apk: Path): String {
            val output = run("verify", "--min-sdk-version", "29", "--print-certs", apk.toString())
            val match = Regex("SHA-256 digest: ([0-9A-Fa-f]{64})", RegexOption.IGNORE_CASE).find(output)
                ?: error("official certificate digest missing")
            return match.groupValues[1].lowercase(Locale.ROOT)
        }

        companion object {
            fun locate(): AndroidSigningTools {
                val sdk = sequenceOf(System.getenv("ANDROID_HOME"), System.getenv("ANDROID_SDK_ROOT"))
                    .filterNotNull()
                    .map(Path::of)
                    .firstOrNull { Files.isDirectory(it) }
                    ?: error("pinned Android SDK environment missing")
                val name = if (isWindows()) "apksigner.bat" else "apksigner"
                val executable = sdk.resolve("build-tools/36.1.0").resolve(name)
                check(executable.isRegularFile()) { "pinned apksigner 36.1.0 missing" }
                return AndroidSigningTools(executable)
            }
        }
    }

    private fun isWindows(): Boolean = System.getProperty("os.name").lowercase(Locale.ROOT).contains("windows")
}
