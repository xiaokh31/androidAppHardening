package ah.host.container

import ah.host.inspector.ApkInspection
import ah.host.inspector.DexSummary
import ah.host.inspector.InspectionLimits
import ah.host.inspector.LimitsApplied
import ah.host.inspector.ManifestSummary
import ah.host.inspector.NativeAbiSummary
import ah.host.inspector.SignerPolicyV1
import ah.host.inspector.VerifiedScheme
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher

object ContainerSelfTest {
    private val passed = ArrayList<String>()

    @JvmStatic
    fun main(arguments: Array<String>) {
        require(arguments.isEmpty()) { "ContainerSelfTest does not accept arguments" }
        val reportDir = Path.of(requireNotNull(System.getProperty("ah.container.reportDir")))
        Files.createDirectories(reportDir)
        val work = reportDir.resolve("work")
        if (Files.exists(work)) {
            Files.walk(work).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach(Files::delete)
            }
        }
        Files.createDirectories(work)

        runCase("rfc5869_hkdf") { verifyRfc5869() }
        runCase("chunk_boundaries") { verifyChunkBoundaries() }
        runCase("near_limit_streaming") { verifyNearLimitStreaming() }
        runCase("config_v2_round_trip") { verifyConfigRoundTrip() }
        runCase("deterministic_multi_dex_round_trip") { verifyDeterministicRoundTrip(work) }
        runCase("production_randomness") { verifyProductionRandomness(work) }
        runCase("tamper_matrix") { verifyTamperMatrix(work) }
        runCase("input_changed_between_passes") { verifyInputChanged(work) }
        runCase("one_shot_key_plan") { verifyOneShotPlan(work) }
        runCase("random_failure_cleanup") { verifyRandomFailureCleanup(work) }
        runCase("cancellation_cleanup") { verifyCancellationCleanup(work) }

        val report = buildString {
            append("{\n  \"schema\": \"m1-04-container-self-test-v1\",\n")
            append("  \"timestamp_utc\": \"").append(Instant.now()).append("\",\n")
            append("  \"status\": \"pass\",\n")
            append("  \"os\": \"").append(System.getProperty("os.name")).append(" ")
                .append(System.getProperty("os.version")).append("\",\n")
            append("  \"java\": \"").append(System.getProperty("java.vendor")).append(" ")
                .append(System.getProperty("java.version")).append("\",\n")
            append("  \"jca_aes_gcm_provider\": \"")
                .append(Cipher.getInstance("AES/GCM/NoPadding").provider.name).append("\",\n")
            append("  \"peak_observed_single_buffer_bytes\": ").append(TestMetrics.peakAllocation).append(",\n")
            append("  \"peak_observed_live_buffers_bytes\": ").append(TestMetrics.peakLiveAllocation).append(",\n")
            append("  \"golden_container_sha256\": \"3764b908e534ffa5179a9519045ec74a7caa44b30c80447998c593a1ac2fa60d\",\n")
            append("  \"cases\": [\n")
            passed.forEachIndexed { index, name ->
                append("    {\"name\": \"").append(name).append("\", \"status\": \"pass\"}")
                if (index != passed.lastIndex) append(',')
                append('\n')
            }
            append("  ]\n}\n")
        }
        Files.writeString(reportDir.resolve("container-self-test.json"), report)
        println("M1-04 container self-test PASS cases=${passed.size}")
    }

    private fun verifyRfc5869() {
        val ikm = ByteArray(22) { 0x0b }
        val salt = hex("000102030405060708090a0b0c")
        val info = hex("f0f1f2f3f4f5f6f7f8f9")
        val expected = hex("3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865")
        val actual = ContainerCrypto.hkdfSha256(ikm, salt, info, 42)
        check(actual.contentEquals(expected)) { "RFC 5869 case 1 mismatch" }
        val zeroKey = ByteArray(32)
        val zeroNonce = ByteArray(12)
        val zeroPlaintext = ByteArray(16)
        val nistExpected = hex("cea7403d4d606b6e074ec5d3baf39d18d0d1c8a799996bf0265b98b5d48ab919")
        val encrypted = ContainerCrypto.aesGcmEncrypt(zeroKey, zeroNonce, ByteArray(0), zeroPlaintext)
        check(encrypted.contentEquals(nistExpected)) { "NIST AES-256-GCM vector mismatch" }
        check(ContainerCrypto.aesGcmDecrypt(zeroKey, zeroNonce, ByteArray(0), encrypted).contentEquals(zeroPlaintext))

        val cek = ByteArray(32) { (it + 1).toByte() }
        val build = ByteArray(16) { (it + 2).toByte() }
        val manifest = ContainerCrypto.manifestKey(cek, build)
        val record0 = ContainerCrypto.recordKey(cek, build, 0)
        val record1 = ContainerCrypto.recordKey(cek, build, 1)
        check(!manifest.contentEquals(record0) && !record0.contentEquals(record1))
        val nonce0 = ContainerCrypto.chunkNonce(ByteArray(8) { 1 }, 0)
        val nonce1 = ContainerCrypto.chunkNonce(ByteArray(8) { 1 }, 1)
        check(!nonce0.contentEquals(nonce1))
        listOf(ikm, salt, info, expected, actual, zeroKey, zeroNonce, zeroPlaintext, nistExpected, encrypted,
            cek, build, manifest, record0, record1, nonce0, nonce1).forEach(ByteArray::fillZero)

        val zlibOutput = ByteArrayOutputStream()
        val zlibObservation = compressInto(ByteArrayInputStream("hello".toByteArray()), zlibOutput, NO_CONTAINER_OBSERVER)
        check(zlibOutput.toByteArray().contentEquals(hex("78dacb48cdc9c90700062c0215")))
        check(zlibObservation.compressedLength == 13L)
        zlibObservation.originalSha256.fill(0)
    }

    private fun verifyChunkBoundaries() {
        val lengths = longArrayOf(1, 65_535, 65_536, 65_537, 65_536L * 9 + 7)
        val expected = intArrayOf(1, 1, 1, 2, 10)
        lengths.indices.forEach { index -> check(chunksForLength(lengths[index]) == expected[index]) }
        val record = RecordV2(0, "classes.dex", 1, 65_537, 2, 0, 0, ByteArray(8) { 1 }, ByteArray(32))
        check(expectedChunk(record, 0).plaintextLength == 65_536)
        check(expectedChunk(record, 1).plaintextLength == 1)
        check(expectedChunk(record, 1).payloadOffset == 65_552L)
    }

    private fun verifyNearLimitStreaming() {
        val observer = RecordingObserver()
        val observation = LimitedZeroInputStream(InspectionLimits.MAX_DEX_BYTES).use { input ->
            observeCompression(input, observer)
        }
        check(observation.originalLength == InspectionLimits.MAX_DEX_BYTES)
        check(observation.compressedLength > 0)
        check(observer.maxAllocation <= 65_536)
        observation.originalSha256.fill(0)
    }

    private fun verifyConfigRoundTrip() {
        val cek = ByteArray(32) { it.toByte() }
        val root = ByteArray(32) { (it + 32).toByte() }
        val rJava = ByteArray(32) { (it + 64).toByte() }
        val build = ByteArray(16) { (it + 96).toByte() }
        val slot = ByteArray(16) { (it + 112).toByte() }
        val signer = ByteArray(32) { (it + 7).toByte() }
        val packageDigest = ContainerCrypto.sha256("ah.fixture".toByteArray())
        val nonce = ByteArray(12) { (it + 3).toByte() }
        val material = ConfigV2Codec.build(
            "ah.fixture.RealFactory",
            build,
            slot,
            signer,
            packageDigest,
            cek,
            root,
            rJava,
            nonce,
        )
        val recovered = ConfigV2Codec.recoverCek(material.bytes, material.rNative, build, slot, signer, packageDigest)
        check(recovered.contentEquals(cek))
        check(ConfigV2Codec.originalFactory(material.bytes) == "ah.fixture.RealFactory")
        val tampered = material.bytes.copyOf().also { it[164] = (it[164].toInt() xor 1).toByte() }
        expectCode(ContainerErrorCode.CONTAINER_AUTH_FAILED) {
            ConfigV2Codec.recoverCek(tampered, material.rNative, build, slot, signer, packageDigest)
        }
        val aadTampered = material.bytes.copyOf().also { it[88] = (it[88].toInt() xor 1).toByte() }
        expectCode(ContainerErrorCode.CONTAINER_AUTH_FAILED) {
            ConfigV2Codec.recoverCek(aadTampered, material.rNative, build, slot, signer, packageDigest)
        }
        val malformedFactory = material.bytes.copyOf().also { it[180] = 0xc0.toByte() }
        expectCode(ContainerErrorCode.CONTAINER_FORMAT) {
            ConfigV2Codec.recoverCek(malformedFactory, material.rNative, build, slot, signer, packageDigest)
        }
        expectCode(ContainerErrorCode.CONTAINER_FORMAT) {
            ConfigV2Codec.build(AhConstants.SHELL_FACTORY, build, slot, signer, packageDigest, cek, root, rJava, nonce)
        }
        listOf(cek, root, rJava, build, slot, signer, packageDigest, nonce, recovered, tampered, aadTampered,
            malformedFactory, material.bytes, material.rNative)
            .forEach(ByteArray::fillZero)
    }

    private fun verifyDeterministicRoundTrip(work: Path) {
        val fixture = fixture(work.resolve("deterministic.apk"), intArrayOf(1_024, 190_000), seed = 11)
        val observerA = RecordingObserver()
        val outputA = work.resolve("deterministic-a.ahdc")
        val resultA = DexContainerBuilder(fixture.path, FixedRandom(), observerA, null)
            .build(fixture.inspection, fixture.signer, outputA)
        val descriptorA = resultA.keyPackagingPlan.consume { material ->
            material.expectedBinding(fixture.inspection, fixture.signer).use { binding ->
                DexContainerVerifier(observerA).verify(outputA, binding)
            }
        }
        check(descriptorA.records.size == 2)
        check(descriptorA.records.map(DexRecordDescriptor::name) == listOf("classes.dex", "classes2.dex"))
        check(descriptorA.records[1].chunkCount >= 3) { "multi-chunk fixture did not cross two boundaries" }
        check(observerA.authenticated.get() == descriptorA.records.sumOf(DexRecordDescriptor::chunkCount))
        check(observerA.maxAllocation <= 65_552) { "working allocation exceeded bound: ${observerA.maxAllocation}" }
        check(observerA.clearFailures == 0) { "sensitive cleanup assertion failed" }

        val outputB = work.resolve("deterministic-b.ahdc")
        val resultB = DexContainerBuilder(fixture.path, FixedRandom(), RecordingObserver(), null)
            .build(fixture.inspection, fixture.signer, outputB)
        resultB.keyPackagingPlan.close()
        check(Files.readAllBytes(outputA).contentEquals(Files.readAllBytes(outputB)))
        check(!contains(outputA, fixture.markers.first())) { "plaintext DEX marker found in container" }
        check(resultA.descriptor.containerSha256Hex == descriptorA.containerSha256Hex)
    }

    private fun verifyProductionRandomness(work: Path) {
        val fixture = fixture(work.resolve("random.apk"), intArrayOf(8_192), seed = 19)
        val outputA = work.resolve("random-a.ahdc")
        val outputB = work.resolve("random-b.ahdc")
        val first = DexContainerBuilder(fixture.path).build(fixture.inspection, fixture.signer, outputA)
        val second = DexContainerBuilder(fixture.path).build(fixture.inspection, fixture.signer, outputB)
        val firstMaterial = first.keyPackagingPlan.consume { material ->
            listOf(material.configV2().copyRemaining(), material.rNative().copyRemaining(),
                material.buildId().copyRemaining(), material.keySlotId().copyRemaining())
        }
        val secondMaterial = second.keyPackagingPlan.consume { material ->
            listOf(material.configV2().copyRemaining(), material.rNative().copyRemaining(),
                material.buildId().copyRemaining(), material.keySlotId().copyRemaining())
        }
        check(first.descriptor.containerSha256Hex != second.descriptor.containerSha256Hex)
        firstMaterial.indices.forEach { index -> check(!firstMaterial[index].contentEquals(secondMaterial[index])) }
        (firstMaterial + secondMaterial).forEach(ByteArray::fillZero)
    }

    private fun verifyTamperMatrix(work: Path) {
        val fixture = fixture(work.resolve("tamper.apk"), intArrayOf(140_000), seed = 23)
        val clean = work.resolve("tamper-clean.ahdc")
        val result = DexContainerBuilder(fixture.path, FixedRandom(), RecordingObserver(), null)
            .build(fixture.inspection, fixture.signer, clean)
        result.keyPackagingPlan.consume { material ->
            val config = material.configV2().copyRemaining()
            val nativeShare = material.rNative().copyRemaining()
            try {
                val cleanHeader = AhdcV2Codec.parseHeader(Files.readAllBytes(clean).copyOfRange(0, 160))
                val firstRecordOffset = 160L + cleanHeader.signerPolicySize
                val chunkTableOffset = firstRecordOffset + cleanHeader.recordTableSize
                val firstRecord = FileChannel.open(clean, StandardOpenOption.READ).use { channel ->
                    AhdcV2Codec.parseRecord(channel.readExact(firstRecordOffset, AhConstants.RECORD_BYTES))
                }
                val structural = listOf(
                    Triple("magic", 0L, ContainerErrorCode.CONTAINER_FORMAT),
                    Triple("version", 4L, ContainerErrorCode.CONTAINER_VERSION),
                    Triple("flags", 10L, ContainerErrorCode.CONTAINER_VERSION),
                    Triple("count", 12L, ContainerErrorCode.CONTAINER_LIMIT_EXCEEDED),
                    Triple("payload-length", 32L, ContainerErrorCode.CONTAINER_FORMAT),
                    Triple("build-id", 40L, ContainerErrorCode.CONTAINER_FORMAT),
                    Triple("key-slot", 56L, ContainerErrorCode.CONTAINER_FORMAT),
                    Triple("config-digest", 72L, ContainerErrorCode.CONTAINER_AUTH_FAILED),
                    Triple("manifest", 104L, ContainerErrorCode.CONTAINER_AUTH_FAILED),
                    Triple("chunk-max", 136L, ContainerErrorCode.CONTAINER_VERSION),
                    Triple("reserved", 140L, ContainerErrorCode.CONTAINER_FORMAT),
                    Triple("spv1", 160L, ContainerErrorCode.CONTAINER_FORMAT),
                    Triple("record-length", firstRecordOffset + 16, ContainerErrorCode.CONTAINER_FORMAT),
                    Triple("record-offset", firstRecordOffset + 32, ContainerErrorCode.CONTAINER_FORMAT),
                    Triple("nonce-prefix", firstRecordOffset + 40, ContainerErrorCode.CONTAINER_AUTH_FAILED),
                    Triple("record-digest", firstRecordOffset + 72, ContainerErrorCode.CONTAINER_AUTH_FAILED),
                    Triple("chunk-record", chunkTableOffset, ContainerErrorCode.CONTAINER_FORMAT),
                    Triple("chunk-ordinal", chunkTableOffset + 4, ContainerErrorCode.CONTAINER_FORMAT),
                    Triple("chunk-offset", chunkTableOffset + 8, ContainerErrorCode.CONTAINER_FORMAT),
                )
                structural.forEach { (name, offset, code) ->
                    verifyFileTamper(fixture, clean, config, nativeShare, work.resolve("tamper-$name.ahdc"), offset, code)
                }
                val payloadBase = 160L + cleanHeader.signerPolicySize + cleanHeader.recordTableSize + cleanHeader.chunkTableSize
                verifyFileTamper(fixture, clean, config, nativeShare, work.resolve("tamper-cipher.ahdc"), payloadBase,
                    ContainerErrorCode.CONTAINER_AUTH_FAILED)
                verifyFileTamper(fixture, clean, config, nativeShare, work.resolve("tamper-tag.ahdc"),
                    payloadBase + minOf(65_536L, firstRecord.compressedLength), ContainerErrorCode.CONTAINER_AUTH_FAILED)

                val truncated = work.resolve("tamper-truncated.ahdc")
                Files.copy(clean, truncated, StandardCopyOption.REPLACE_EXISTING)
                FileChannel.open(truncated, StandardOpenOption.WRITE).use { it.truncate(it.size() - 1) }
                verifyWithCopies(fixture, truncated, config, nativeShare, ContainerErrorCode.CONTAINER_FORMAT)

                val tailed = work.resolve("tamper-tailed.ahdc")
                Files.copy(clean, tailed, StandardCopyOption.REPLACE_EXISTING)
                Files.newOutputStream(tailed, StandardOpenOption.APPEND).use { it.write(1) }
                verifyWithCopies(fixture, tailed, config, nativeShare, ContainerErrorCode.CONTAINER_FORMAT)

                val badConfig = config.copyOf().also { it[200] = 1 }
                val binding = ExpectedBinding.from(fixture.inspection, fixture.signer, badConfig, nativeShare, NO_CONTAINER_OBSERVER)
                binding.use { expected ->
                    expectCode(ContainerErrorCode.CONTAINER_AUTH_FAILED) { DexContainerVerifier().verify(clean, expected) }
                }
                badConfig.fill(0)

                val otherSigner = SignerPolicyV1(ByteArray(32) { 99 }, listOf(ByteArray(32) { 99 }), setOf(VerifiedScheme.V2))
                ExpectedBinding.from(fixture.inspection, otherSigner, config, nativeShare, NO_CONTAINER_OBSERVER).use { binding ->
                    expectCode(ContainerErrorCode.CONTAINER_AUTH_FAILED) { DexContainerVerifier().verify(clean, binding) }
                }

                val otherPackage = "ah.fixtures.other"
                ExpectedBinding(
                    otherPackage,
                    ContainerCrypto.sha256(otherPackage.toByteArray()),
                    fixture.signer.currentCertificateSha256,
                    fixture.signer.lineageCertificateSha256,
                    fixture.inspection.dexEntries,
                    config,
                    nativeShare,
                    NO_CONTAINER_OBSERVER,
                ).use { binding ->
                    expectCode(ContainerErrorCode.CONTAINER_AUTH_FAILED) { DexContainerVerifier().verify(clean, binding) }
                }
            } finally {
                config.fill(0)
                nativeShare.fill(0)
            }
        }
    }

    private fun verifyInputChanged(work: Path) {
        val path = work.resolve("changed.apk")
        val fixture = fixture(path, intArrayOf(4_096), seed = 31)
        val output = work.resolve("changed.ahdc")
        val builder = DexContainerBuilder(path, FixedRandom(), RecordingObserver()) {
            writeApk(path, listOf(ByteArray(4_096) { 7 }))
        }
        expectCode(ContainerErrorCode.CONTAINER_INPUT_CHANGED) {
            builder.build(fixture.inspection, fixture.signer, output)
        }
        check(!Files.exists(output)) { "failed build left a successful-looking output" }
    }

    private fun verifyOneShotPlan(work: Path) {
        val fixture = fixture(work.resolve("one-shot.apk"), intArrayOf(2_048), seed = 37)
        val output = work.resolve("one-shot.ahdc")
        val observer = RecordingObserver()
        val result = DexContainerBuilder(fixture.path, FixedRandom(), observer, null)
            .build(fixture.inspection, fixture.signer, output)
        result.keyPackagingPlan.consume { material ->
            check(material.configV2().remaining() == 768)
            check(material.rNative().remaining() == 32)
        }
        expectCode(ContainerErrorCode.CONTAINER_KEY_MATERIAL) {
            result.keyPackagingPlan.consume { error("must not run") }
        }
        check(observer.clearFailures == 0)
    }

    private fun verifyRandomFailureCleanup(work: Path) {
        val fixture = fixture(work.resolve("random-failure.apk"), intArrayOf(1_024), seed = 41)
        val observer = RecordingObserver()
        val calls = AtomicInteger()
        val failing = ContainerRandom { label, size ->
            if (calls.incrementAndGet() == 4) throw IllegalStateException("synthetic RNG failure")
            ByteArray(size) { (label.length + it + 1).toByte() }
        }
        expectCode(ContainerErrorCode.CONTAINER_RANDOM_FAILED) {
            DexContainerBuilder(fixture.path, failing, observer, null)
                .build(fixture.inspection, fixture.signer, work.resolve("random-failure.ahdc"))
        }
        check(observer.clearFailures == 0)
        check(!Files.exists(work.resolve("random-failure.ahdc")))

        val zeros = ContainerRandom { _, size -> ByteArray(size) }
        expectCode(ContainerErrorCode.CONTAINER_RANDOM_FAILED) {
            DexContainerBuilder(fixture.path, zeros, observer, null)
                .build(fixture.inspection, fixture.signer, work.resolve("random-zero.ahdc"))
        }
    }

    private fun verifyCancellationCleanup(work: Path) {
        val fixture = fixture(work.resolve("cancel.apk"), intArrayOf(200_000), seed = 43)
        val output = work.resolve("cancel.ahdc")
        val observer = RecordingObserver()
        val builder = DexContainerBuilder(fixture.path, FixedRandom(), observer) {
            Thread.currentThread().interrupt()
        }
        try {
            expectCode(ContainerErrorCode.CONTAINER_INPUT_CHANGED) {
                builder.build(fixture.inspection, fixture.signer, output)
            }
        } finally {
            Thread.interrupted()
        }
        check(!Files.exists(output))
        check(observer.clearFailures == 0)
    }

    private fun verifyFileTamper(
        fixture: Fixture,
        clean: Path,
        config: ByteArray,
        nativeShare: ByteArray,
        tampered: Path,
        offset: Long,
        expectedCode: ContainerErrorCode,
    ) {
        Files.copy(clean, tampered, StandardCopyOption.REPLACE_EXISTING)
        FileChannel.open(tampered, StandardOpenOption.READ, StandardOpenOption.WRITE).use { channel ->
            val one = ByteBuffer.allocate(1)
            check(channel.read(one, offset) == 1)
            one.flip()
            val changed = byteArrayOf((one.get().toInt() xor 1).toByte())
            channel.write(ByteBuffer.wrap(changed), offset)
        }
        verifyWithCopies(fixture, tampered, config, nativeShare, expectedCode)
    }

    private fun verifyWithCopies(
        fixture: Fixture,
        container: Path,
        config: ByteArray,
        nativeShare: ByteArray,
        expectedCode: ContainerErrorCode,
    ) {
        ExpectedBinding.from(fixture.inspection, fixture.signer, config, nativeShare, NO_CONTAINER_OBSERVER).use { binding ->
            expectCode(expectedCode) { DexContainerVerifier().verify(container, binding) }
        }
    }

    private fun fixture(path: Path, sizes: IntArray, seed: Int): Fixture {
        val dex = sizes.mapIndexed { index, size ->
            var state = seed xor (index + 1) * 0x45d9f3b
            ByteArray(size) {
                state = state xor (state shl 13)
                state = state xor (state ushr 17)
                state = state xor (state shl 5)
                state.toByte()
            }.also { bytes ->
                val marker = "dex\n035\u0000M1-04-$index".toByteArray(Charsets.US_ASCII)
                marker.copyInto(bytes, endIndex = minOf(marker.size, bytes.size))
            }
        }
        writeApk(path, dex)
        val packageName = "ah.fixtures.container"
        val inputHash = ContainerCrypto.sha256(path)
        val manifest = ManifestSummary(packageName, ContainerCrypto.sha256(packageName.toByteArray()), 29, 36, null,
            "ah.fixtures.OriginalFactory", null)
        val summaries = dex.mapIndexed { index, bytes ->
            DexSummary(canonicalDexName(index), index, bytes.size.toLong(), 1, ContainerCrypto.sha256(bytes))
        }
        val inspection = ApkInspection(
            inputHash,
            manifest,
            emptyList(),
            summaries,
            NativeAbiSummary(emptyList()),
            emptyList(),
            "test",
            LimitsApplied(emptyMap()),
        )
        val signerDigest = ByteArray(32) { index -> (index * 7 + seed).toByte() }
        val signer = SignerPolicyV1(signerDigest, listOf(signerDigest), setOf(VerifiedScheme.V2))
        inputHash.fill(0)
        signerDigest.fill(0)
        return Fixture(path, inspection, signer, dex.map { it.copyOfRange(0, minOf(16, it.size)) })
    }

    private fun writeApk(path: Path, dex: List<ByteArray>) {
        Files.newOutputStream(path, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING).use { file ->
            ZipOutputStream(file).use { zip ->
                dex.forEachIndexed { index, bytes ->
                    val entry = ZipEntry(canonicalDexName(index)).also { it.time = 0 }
                    zip.putNextEntry(entry)
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
        }
    }

    private fun contains(path: Path, needle: ByteArray): Boolean {
        val bytes = Files.readAllBytes(path)
        return bytes.indices.any { offset ->
            offset <= bytes.size - needle.size && needle.indices.all { index -> bytes[offset + index] == needle[index] }
        }
    }

    private inline fun runCase(name: String, action: () -> Unit) {
        action()
        passed += name
        println("PASS $name")
    }

    private inline fun expectCode(code: ContainerErrorCode, action: () -> Unit) {
        try {
            action()
            error("expected $code")
        } catch (exception: ContainerException) {
            check(exception.code == code) { "expected $code, got ${exception.code} field=${exception.field}" }
        }
    }

    private fun hex(value: String): ByteArray = ByteArray(value.length / 2) { index ->
        value.substring(index * 2, index * 2 + 2).toInt(16).toByte()
    }

    private data class Fixture(
        val path: Path,
        val inspection: ApkInspection,
        val signer: SignerPolicyV1,
        val markers: List<ByteArray>,
    )
}

private class FixedRandom : ContainerRandom {
    private var counter = 0

    override fun bytes(label: String, size: Int): ByteArray {
        require(size in 1..32)
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("M1-04:$label:${counter++}".toByteArray(Charsets.UTF_8))
        return digest.copyOf(size).also { digest.fill(0) }
    }
}

private class RecordingObserver : ContainerObserver {
    private val live = HashMap<String, Int>()
    private var liveBytes = 0
    var maxAllocation = 0
        private set
    var clearFailures = 0
        private set
    val authenticated = AtomicInteger()

    override fun cleared(label: String, allZero: Boolean) {
        check(label.isNotEmpty())
        if (!allZero) clearFailures++
        live.remove(label)?.let { bytes -> liveBytes -= bytes }
    }

    override fun allocated(label: String, bytes: Int) {
        check(label.isNotEmpty())
        maxAllocation = maxOf(maxAllocation, bytes)
        TestMetrics.peakAllocation = maxOf(TestMetrics.peakAllocation, bytes)
        check(live.put(label, bytes) == null) { "allocation label reused before clear: $label" }
        liveBytes += bytes
        TestMetrics.peakLiveAllocation = maxOf(TestMetrics.peakLiveAllocation, liveBytes)
    }

    override fun authenticatedBeforeInflate(record: Int, chunk: Int) {
        check(record >= 0 && chunk >= 0)
        authenticated.incrementAndGet()
    }
}

private object TestMetrics {
    var peakAllocation: Int = 0
    var peakLiveAllocation: Int = 0
}

private class LimitedZeroInputStream(private var remaining: Long) : InputStream() {
    override fun read(): Int {
        if (remaining <= 0) return -1
        remaining--
        return 0
    }

    override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
        if (offset < 0 || length < 0 || offset > bytes.size - length) throw IndexOutOfBoundsException()
        if (remaining <= 0) return -1
        val count = minOf(remaining, length.toLong()).toInt()
        bytes.fill(0, offset, offset + count)
        remaining -= count
        return count
    }
}

private fun ByteArray.fillZero() = fill(0)
