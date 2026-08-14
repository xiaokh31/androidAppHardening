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
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
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
    private val tamperEvidence = ArrayList<TamperEvidence>()

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
        runCase("chunk_boundaries") { verifyChunkBoundaries(work) }
        runCase("near_limit_streaming") { verifyNearLimitStreaming(work) }
        runCase("config_v2_round_trip") { verifyConfigRoundTrip() }
        runCase("deterministic_multi_dex_round_trip") { verifyDeterministicRoundTrip(work) }
        runCase("production_randomness") { verifyProductionRandomness(work) }
        runCase("tamper_matrix") { verifyTamperMatrix(work) }
        runCase("input_changed_between_passes") { verifyInputChanged(work) }
        runCase("one_shot_key_plan") { verifyOneShotPlan(work) }
        runCase("random_failure_cleanup") { verifyRandomFailureCleanup(work) }
        runCase("io_atomic_cleanup_failures") { verifyIoAtomicAndCleanupFailures(work) }
        runCase("oom_cleanup_ownership") { verifyOomCleanupOwnership(work) }
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
            append("  ],\n  \"tamper_results\": [\n")
            tamperEvidence.forEachIndexed { index, evidence ->
                append("    {\"name\": \"").append(evidence.name)
                    .append("\", \"stage\": \"").append(evidence.stage)
                    .append("\", \"code\": \"").append(evidence.code)
                    .append("\", \"input_sha256\": \"").append(evidence.inputSha256)
                    .append("\", \"result\": \"PASS\"}")
                if (index != tamperEvidence.lastIndex) append(',')
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
        val zlibObservation = compressInto(
            ByteArrayInputStream("hello".toByteArray()),
            zlibOutput,
            NO_CONTAINER_OBSERVER,
            5,
            13,
        )
        check(zlibOutput.toByteArray().contentEquals(hex("78dacb48cdc9c90700062c0215")))
        check(zlibObservation.compressedLength == 13L)
        zlibObservation.originalSha256.fill(0)
    }

    private fun verifyChunkBoundaries(work: Path) {
        val lengths = longArrayOf(1, 65_535, 65_536, 65_537, 65_536L * 9 + 7)
        val expected = intArrayOf(1, 1, 1, 2, 10)
        lengths.indices.forEach { index -> check(chunksForLength(lengths[index]) == expected[index]) }
        val record = RecordV2(0, "classes.dex", 1, 65_537, 2, 0, 0, ByteArray(8) { 1 }, ByteArray(32))
        check(expectedChunk(record, 0).plaintextLength == 65_536)
        check(expectedChunk(record, 1).plaintextLength == 1)
        check(expectedChunk(record, 1).payloadOffset == 65_552L)
        expectCode(ContainerErrorCode.CONTAINER_LIMIT_EXCEEDED) { checkedAdd(Long.MAX_VALUE, 1, "overflow") }
        expectCode(ContainerErrorCode.CONTAINER_LIMIT_EXCEEDED) { checkedAddInt(Int.MAX_VALUE, 1, "overflow") }

        val fixture = fixture(work.resolve("boundaries.apk"), intArrayOf(1, 65_535, 65_536, 65_537), seed = 5)
        val output = work.resolve("boundaries.ahdc")
        val result = DexContainerBuilder(fixture.path, FixedRandom(), RecordingObserver(), null)
            .build(fixture.inspection, fixture.signer, output)
        result.keyPackagingPlan.consume { material ->
            material.expectedBinding(fixture.inspection, fixture.signer).use { binding ->
                val verified = DexContainerVerifier().verify(output, binding)
                check(verified.records.map(DexRecordDescriptor::originalLength) ==
                    listOf(1L, 65_535L, 65_536L, 65_537L))
            }
        }
    }

    private fun verifyNearLimitStreaming(work: Path) {
        val observer = RecordingObserver()
        val fixture = zeroFixture(work.resolve("near-limit.apk"), InspectionLimits.MAX_DEX_BYTES)
        val output = work.resolve("near-limit.ahdc")
        val result = DexContainerBuilder(fixture.path, FixedRandom(), observer, null)
            .build(fixture.inspection, fixture.signer, output)
        result.keyPackagingPlan.consume { material ->
            material.expectedBinding(fixture.inspection, fixture.signer).use { binding ->
                val verified = DexContainerVerifier(observer).verify(output, binding)
                check(verified.records.single().originalLength == InspectionLimits.MAX_DEX_BYTES)
            }
        }
        check(observer.maxAllocation <= 65_552)
        check(TestMetrics.peakLiveAllocation < 1_048_576)
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
            writeCrossLanguageVector(work.parent, outputA, fixture, material)
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

    private fun writeCrossLanguageVector(
        reportDir: Path,
        container: Path,
        fixture: Fixture,
        material: KeyPackagingMaterialV2,
    ) {
        val config = material.configV2().copyRemaining()
        val nativeShare = material.rNative().copyRemaining()
        val buildId = material.buildId().copyRemaining()
        val keySlotId = material.keySlotId().copyRemaining()
        try {
            val bytes = Files.readAllBytes(container)
            val header = AhdcV2Codec.parseHeader(bytes.copyOfRange(0, AhConstants.HEADER_BYTES))
            val recordOffset = AhConstants.HEADER_BYTES + header.signerPolicySize
            val records = (0 until header.dexCount).map { index ->
                AhdcV2Codec.parseRecord(bytes.copyOfRange(
                    recordOffset + index * AhConstants.RECORD_BYTES,
                    recordOffset + (index + 1) * AhConstants.RECORD_BYTES,
                ))
            }
            val json = buildString {
                append("{\n")
                append("  \"schema\": \"ahdc-v2-cross-language-vector-v1\",\n")
                append("  \"container_sha256\": \"").append(ContainerCrypto.sha256(bytes).toHex()).append("\",\n")
                append("  \"config_v2_hex\": \"").append(config.toHex()).append("\",\n")
                append("  \"r_native_hex\": \"").append(nativeShare.toHex()).append("\",\n")
                append("  \"build_id_hex\": \"").append(buildId.toHex()).append("\",\n")
                append("  \"key_slot_id_hex\": \"").append(keySlotId.toHex()).append("\",\n")
                append("  \"package_name\": \"").append(fixture.inspection.packageName).append("\",\n")
                append("  \"package_name_sha256\": \"").append(fixture.inspection.packageNameSha256.toHex()).append("\",\n")
                append("  \"current_signer_sha256\": \"").append(fixture.signer.currentCertificateSha256.toHex()).append("\",\n")
                append("  \"expected_original_factory\": \"ah.fixtures.OriginalFactory\",\n")
                append("  \"records\": [\n")
                records.forEachIndexed { index, record ->
                    append("    {\"ordinal\": ").append(record.ordinal)
                        .append(", \"name\": \"").append(record.name)
                        .append("\", \"original_length\": ").append(record.originalLength)
                        .append(", \"compressed_length\": ").append(record.compressedLength)
                        .append(", \"chunk_count\": ").append(record.chunkCount)
                        .append(", \"nonce_prefix_hex\": \"").append(record.noncePrefix.toHex())
                        .append("\", \"original_sha256\": \"").append(record.originalSha256.toHex()).append("\"}")
                    if (index != records.lastIndex) append(',')
                    append('\n')
                }
                append("  ]\n}\n")
            }
            Files.writeString(reportDir.resolve("cross-language-vector.json"), json)
            bytes.fill(0)
        } finally {
            listOf(config, nativeShare, buildId, keySlotId).forEach(ByteArray::fillZero)
        }
    }

    private fun verifyProductionRandomness(work: Path) {
        val fixture = fixture(work.resolve("random.apk"), intArrayOf(8_192), seed = 19)
        val outputA = work.resolve("random-a.ahdc")
        val outputB = work.resolve("random-b.ahdc")
        val first = DexContainerBuilder(fixture.path).build(fixture.inspection, fixture.signer, outputA)
        val second = DexContainerBuilder(fixture.path).build(fixture.inspection, fixture.signer, outputB)
        fun consume(result: ContainerBuildResult, output: Path): Pair<DexContainerDescriptor, List<ByteArray>> =
            result.keyPackagingPlan.consume { material ->
                val config = material.configV2().copyRemaining()
                val nativeShare = material.rNative().copyRemaining()
                val buildId = material.buildId().copyRemaining()
                val keySlotId = material.keySlotId().copyRemaining()
                val signer = fixture.signer.currentCertificateSha256
                val packageDigest = fixture.inspection.packageNameSha256
                val cek = ConfigV2Codec.recoverCek(config, nativeShare, buildId, keySlotId, signer, packageDigest)
                val rJava = slice(config, 88, 32)
                val root = ByteArray(32) { index -> (rJava[index].toInt() xor nativeShare[index].toInt()).toByte() }
                val wrapNonce = slice(config, 120, 12)
                val header = AhdcV2Codec.parseHeader(Files.readAllBytes(output).copyOfRange(0, AhConstants.HEADER_BYTES))
                val recordOffset = AhConstants.HEADER_BYTES.toLong() + header.signerPolicySize
                val noncePrefix = FileChannel.open(output, StandardOpenOption.READ).use { channel ->
                    AhdcV2Codec.parseRecord(channel.readExact(recordOffset, AhConstants.RECORD_BYTES)).noncePrefix
                }
                val verified = material.expectedBinding(fixture.inspection, fixture.signer).use { binding ->
                    DexContainerVerifier().verify(output, binding)
                }
                Pair(verified, listOf(config, nativeShare, buildId, keySlotId, cek, root, rJava, wrapNonce, noncePrefix))
            }
        val (verifiedFirst, firstMaterial) = consume(first, outputA)
        val (verifiedSecond, secondMaterial) = consume(second, outputB)
        check(first.descriptor.containerSha256Hex != second.descriptor.containerSha256Hex)
        firstMaterial.indices.forEach { index -> check(!firstMaterial[index].contentEquals(secondMaterial[index])) }
        assertDescriptorSemanticsEqual(verifiedFirst, verifiedSecond, ignoreContainerHash = true)
        assertDescriptorSemanticsEqual(first.descriptor, verifiedFirst, ignoreContainerHash = false)
        assertDescriptorSemanticsEqual(second.descriptor, verifiedSecond, ignoreContainerHash = false)
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
                    verifyFileTamper(name, fixture, clean, config, nativeShare,
                        work.resolve("tamper-$name.ahdc"), offset, code)
                }
                val payloadBase = 160L + cleanHeader.signerPolicySize + cleanHeader.recordTableSize + cleanHeader.chunkTableSize
                verifyFileTamper("cipher", fixture, clean, config, nativeShare, work.resolve("tamper-cipher.ahdc"), payloadBase,
                    ContainerErrorCode.CONTAINER_AUTH_FAILED)
                verifyFileTamper("tag", fixture, clean, config, nativeShare, work.resolve("tamper-tag.ahdc"),
                    payloadBase + minOf(65_536L, firstRecord.compressedLength), ContainerErrorCode.CONTAINER_AUTH_FAILED)
                verifyAuthenticatedZlibTamper(
                    fixture,
                    clean,
                    config,
                    nativeShare,
                    work.resolve("tamper-authenticated-zlib.ahdc"),
                    "checksum",
                )
                verifyAuthenticatedZlibTamper(
                    fixture,
                    clean,
                    config,
                    nativeShare,
                    work.resolve("tamper-authenticated-dictionary.ahdc"),
                    "dictionary",
                )
                verifyAuthenticatedZlibTamper(
                    fixture,
                    clean,
                    config,
                    nativeShare,
                    work.resolve("tamper-authenticated-concatenated.ahdc"),
                    "concatenated",
                )

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
        val observer = RecordingObserver()
        val builder = DexContainerBuilder(path, FixedRandom(), observer) {
            writeApk(path, listOf(ByteArray(4_096) { 7 }))
        }
        expectCode(ContainerErrorCode.CONTAINER_INPUT_CHANGED) {
            builder.build(fixture.inspection, fixture.signer, output)
        }
        check(!Files.exists(output)) { "failed build left a successful-looking output" }

        val mismatchObserver = RecordingObserver()
        val expectedObservation = CompressionObservation(10, 20, ByteArray(32) { 1 })
        val actualObservation = CompressionObservation(10, 20, ByteArray(32) { 2 })
        try {
            expectCode(ContainerErrorCode.CONTAINER_INPUT_CHANGED) {
                try {
                    validatePass2Observation(actualObservation, expectedObservation, 1, 1)
                } finally {
                    actualObservation.clear("pass2.digest", mismatchObserver)
                }
            }
        } finally {
            expectedObservation.clear("pass1.digest", mismatchObserver)
        }
        check("pass2.digest" in mismatchObserver.clearedLabels)

        val pass1Fixture = fixture(work.resolve("changed-pass1.apk"), intArrayOf(2_048), seed = 32)
        val original = pass1Fixture.inspection
        val summaries = original.dexEntries.mapIndexed { index, summary ->
            if (index == 0) {
                DexSummary(summary.entryName, summary.ordinal, summary.fileSize, summary.classCount,
                    summary.sha256.also { it[0] = (it[0].toInt() xor 1).toByte() })
            } else {
                summary
            }
        }
        val mismatched = ApkInspection(
            original.inputSha256,
            original.manifest,
            original.zipEntries,
            summaries,
            original.nativeAbis,
            original.findings,
            original.compatibilityRulesVersion,
            original.limitsApplied,
        )
        val pass1Observer = RecordingObserver()
        expectCode(ContainerErrorCode.CONTAINER_INPUT_CHANGED) {
            DexContainerBuilder(pass1Fixture.path, FixedRandom(), pass1Observer, null)
                .build(mismatched, pass1Fixture.signer, work.resolve("changed-pass1.ahdc"))
        }
        check("pass1.pendingDigest" in pass1Observer.clearedLabels)
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
        val failing = ContainerRandom { label, destination ->
            if (calls.incrementAndGet() == 4) throw IllegalStateException("synthetic RNG failure")
            destination.indices.forEach { destination[it] = (label.length + it + 1).toByte() }
        }
        expectCode(ContainerErrorCode.CONTAINER_RANDOM_FAILED) {
            DexContainerBuilder(fixture.path, failing, observer, null)
                .build(fixture.inspection, fixture.signer, work.resolve("random-failure.ahdc"))
        }
        check(observer.clearFailures == 0)
        check(!Files.exists(work.resolve("random-failure.ahdc")))

        val zeros = ContainerRandom { _, destination -> destination.fill(0) }
        expectCode(ContainerErrorCode.CONTAINER_RANDOM_FAILED) {
            DexContainerBuilder(fixture.path, zeros, observer, null)
                .build(fixture.inspection, fixture.signer, work.resolve("random-zero.ahdc"))
        }

        val collidingShares = ContainerRandom { label, destination ->
            if (label == "root" || label == "rJava") {
                destination.fill(7)
            } else {
                val digest = MessageDigest.getInstance("SHA-256").digest("collision:$label".toByteArray())
                digest.copyInto(destination, endIndex = destination.size)
                digest.fill(0)
            }
        }
        expectCode(ContainerErrorCode.CONTAINER_RANDOM_FAILED) {
            DexContainerBuilder(fixture.path, collidingShares, observer, null)
                .build(fixture.inspection, fixture.signer, work.resolve("random-collision.ahdc"))
        }
        check(!Files.exists(work.resolve("random-collision.ahdc")))
    }

    private fun verifyIoAtomicAndCleanupFailures(work: Path) {
        val fixture = fixture(work.resolve("failure-injection.apk"), intArrayOf(70_000), seed = 47)

        val ioOutput = work.resolve("io-failure.ahdc")
        expectCode(ContainerErrorCode.CONTAINER_FORMAT) {
            DexContainerBuilder(fixture.path, FixedRandom(), RecordingObserver(), { throw IOException("injected") })
                .build(fixture.inspection, fixture.signer, ioOutput)
        }
        check(!Files.exists(ioOutput))

        val atomicOutput = work.resolve("atomic-failure.ahdc")
        expectCode(ContainerErrorCode.CONTAINER_FORMAT) {
            DexContainerBuilder(fixture.path, FixedRandom(), RecordingObserver(), null) { source, target ->
                throw AtomicMoveNotSupportedException(source.toString(), target.toString(), "injected")
            }.build(fixture.inspection, fixture.signer, atomicOutput)
        }
        check(!Files.exists(atomicOutput))

        val earlyObserver = ThrowingCleanupObserver(setOf("manifest.key"))
        val cleanupOutput = work.resolve("cleanup-failure.ahdc")
        expectCode(ContainerErrorCode.CONTAINER_KEY_MATERIAL) {
            DexContainerBuilder(fixture.path, FixedRandom(), earlyObserver, null)
                .build(fixture.inspection, fixture.signer, cleanupOutput)
        }
        check(!Files.exists(cleanupOutput))
        check(earlyObserver.labels.containsAll(listOf("manifest.key", "config", "rNative", "cek", "noncePrefix")))
        check(earlyObserver.allZero)

        val middleObserver = ThrowingCleanupObserver(setOf("record.key"))
        val middleOutput = work.resolve("cleanup-middle-failure.ahdc")
        expectCode(ContainerErrorCode.CONTAINER_KEY_MATERIAL) {
            DexContainerBuilder(fixture.path, FixedRandom(), middleObserver, null)
                .build(fixture.inspection, fixture.signer, middleOutput)
        }
        check(!Files.exists(middleOutput))
        check(middleObserver.labels.containsAll(listOf("record.key", "config", "rNative", "cek", "noncePrefix")))
        check(middleObserver.allZero)

        val planObserver = ThrowingCleanupObserver(setOf("plan.config", "plan.rNative", "plan.keySlotId"))
        val planOutput = work.resolve("cleanup-primary.ahdc")
        val result = DexContainerBuilder(fixture.path, FixedRandom(), planObserver, null)
            .build(fixture.inspection, fixture.signer, planOutput)
        val primary = try {
            result.keyPackagingPlan.consume<Unit> { throw IllegalStateException("primary-action") }
            error("expected primary action failure")
        } catch (failure: IllegalStateException) {
            failure
        }
        check(primary.message == "primary-action")
        check(primary.suppressed.isNotEmpty())
        check(planObserver.labels.containsAll(listOf("plan.config", "plan.rNative", "plan.buildId", "plan.keySlotId")))
        check(planObserver.allZero)
    }

    private fun verifyOomCleanupOwnership(work: Path) {
        val fixture = fixture(work.resolve("oom-injection.apk"), intArrayOf(4_096), seed = 53)

        val randomObserver = RecordingObserver()
        val calls = AtomicInteger()
        var partialRandom: ByteArray? = null
        val oomRandom = ContainerRandom { label, destination ->
            if (calls.incrementAndGet() == 4) {
                destination.fill(91)
                partialRandom = destination
                throw OutOfMemoryError("rng-injected")
            }
            destination.indices.forEach { destination[it] = (label.length + it + 1).toByte() }
        }
        expectFailure<OutOfMemoryError> {
            DexContainerBuilder(fixture.path, oomRandom, randomObserver, null)
                .build(fixture.inspection, fixture.signer, work.resolve("oom-random.ahdc"))
        }
        check(!Files.exists(work.resolve("oom-random.ahdc")))
        check(randomObserver.clearFailures == 0)
        check(randomObserver.clearCount >= 4)
        check(requireNotNull(partialRandom).all { it == 0.toByte() })

        val cleanupObserver = OomCleanupObserver("manifest.key")
        expectCode(ContainerErrorCode.CONTAINER_KEY_MATERIAL) {
            DexContainerBuilder(fixture.path, FixedRandom(), cleanupObserver, null)
                .build(fixture.inspection, fixture.signer, work.resolve("oom-cleanup.ahdc"))
        }
        check(!Files.exists(work.resolve("oom-cleanup.ahdc")))
        check(cleanupObserver.labels.containsAll(listOf("manifest.key", "config", "rNative", "cek", "noncePrefix")))
        check(cleanupObserver.allZero)

        val copyObserver = RecordingObserver()
        val copyCalls = AtomicInteger()
        val oomCopier = SensitiveArrayCopier { source ->
            if (copyCalls.incrementAndGet() == 3) throw OutOfMemoryError("copy-injected")
            source.copyOf()
        }
        expectFailure<OutOfMemoryError> {
            KeyPackagingPlanV2(
                ByteArray(768) { 1 },
                ByteArray(32) { 2 },
                ByteArray(16) { 3 },
                ByteArray(16) { 4 },
                setOf(RuntimeAbi.ARM64_V8A),
                copyObserver,
                oomCopier,
            )
        }
        check(copyObserver.clearFailures == 0)
        check(copyObserver.clearCount >= 2)

        val bindingObserver = RecordingObserver()
        val bindingCalls = AtomicInteger()
        val bindingCopier = SensitiveArrayCopier { source ->
            if (bindingCalls.incrementAndGet() == 4) throw OutOfMemoryError("binding-copy-injected")
            source.copyOf()
        }
        expectFailure<OutOfMemoryError> {
            ExpectedBinding(
                fixture.inspection.packageName,
                fixture.inspection.packageNameSha256,
                fixture.signer.currentCertificateSha256,
                fixture.signer.lineageCertificateSha256,
                fixture.inspection.dexEntries,
                ByteArray(768),
                ByteArray(32),
                bindingObserver,
                bindingCopier,
            )
        }
        check(bindingObserver.clearFailures == 0)
        check(bindingObserver.clearCount >= 3)

        var orphanDigest: ByteArray? = null
        val observationFactory = CompressionObservationFactory { _, _, digest ->
            orphanDigest = digest
            throw OutOfMemoryError("observation-construction-injected")
        }
        expectFailure<OutOfMemoryError> {
            observeCompression(ByteArrayInputStream(ByteArray(128) { 3 }), RecordingObserver(), observationFactory)
        }
        check(requireNotNull(orphanDigest).all { it == 0.toByte() })

        val configBuildArrays = ArrayList<ByteArray>()
        val configBuildAllocator = SensitiveArrayAllocator { size ->
            if (configBuildArrays.size == 2) throw OutOfMemoryError("config-prefix-injected")
            ByteArray(size).also(configBuildArrays::add)
        }
        expectFailure<OutOfMemoryError> {
            ConfigV2Codec.build(
                null,
                ByteArray(16) { 1 },
                ByteArray(16) { 2 },
                ByteArray(32) { 3 },
                ByteArray(32) { 4 },
                ByteArray(32) { 5 },
                ByteArray(32) { 6 },
                ByteArray(32) { 7 },
                ByteArray(12) { 8 },
                configBuildAllocator,
            )
        }
        check(configBuildArrays.size == 2)
        check(configBuildArrays.all { bytes -> bytes.all { it == 0.toByte() } })

        val keyObserver = RecordingObserver()
        val streamAllocator = SensitiveArrayAllocator { throw OutOfMemoryError("stream-construction-injected") }
        expectFailure<OutOfMemoryError> {
            DexContainerBuilder(
                fixture.path,
                FixedRandom(),
                keyObserver,
                null,
                streamAllocator,
                null,
            ).build(fixture.inspection, fixture.signer, work.resolve("oom-record-stream.ahdc"))
        }
        check(!Files.exists(work.resolve("oom-record-stream.ahdc")))
        check("record.key" in keyObserver.clearedLabels)

        val exactObserver = ThrowingAllocationObserver("chunk.exact")
        expectFailure<OutOfMemoryError> {
            DexContainerBuilder(fixture.path, FixedRandom(), exactObserver, null)
                .build(fixture.inspection, fixture.signer, work.resolve("oom-chunk-exact.ahdc"))
        }
        check(!Files.exists(work.resolve("oom-chunk-exact.ahdc")))
        check(exactObserver.clearedLabels.containsAll(listOf("chunk.exact", "record.key", "pass1.digest")))
        check(exactObserver.allZero)

        val ownershipOutput = work.resolve("oom-consumption.ahdc")
        val ownershipResult = DexContainerBuilder(fixture.path, FixedRandom(), RecordingObserver(), null)
            .build(fixture.inspection, fixture.signer, ownershipOutput)
        ownershipResult.keyPackagingPlan.consume { material ->
            val config = material.configV2().copyRemaining()
            val nativeShare = material.rNative().copyRemaining()
            try {
                val recoveryArrays = ArrayList<ByteArray>()
                val recoveryCalls = AtomicInteger()
                val recoveryAllocator = SensitiveArrayAllocator { size ->
                    if (recoveryCalls.incrementAndGet() == 4) throw OutOfMemoryError("recover-allocation-injected")
                    ByteArray(size).also(recoveryArrays::add)
                }
                expectFailure<OutOfMemoryError> {
                    ConfigV2Codec.recoverCek(
                        config,
                        nativeShare,
                        slice(config, 24, 16),
                        slice(config, 40, 16),
                        fixture.signer.currentCertificateSha256,
                        fixture.inspection.packageNameSha256,
                        recoveryAllocator,
                    )
                }
                check(recoveryArrays.size == 3)
                check(recoveryArrays.all { bytes -> bytes.all { it == 0.toByte() } })

                val hkdfArrays = ArrayList<ByteArray>()
                var prk: ByteArray? = null
                val hkdfAllocator = SensitiveArrayAllocator { size ->
                    if (hkdfArrays.isNotEmpty()) throw OutOfMemoryError("hkdf-output-injected")
                    ByteArray(size).also(hkdfArrays::add)
                }
                val extractor = HkdfExtractor { _, _ -> ByteArray(32) { 73 }.also { prk = it } }
                expectFailure<OutOfMemoryError> {
                    ContainerCrypto.hkdfSha256(
                        ByteArray(32) { 11 },
                        ByteArray(0),
                        byteArrayOf(1, 2, 3),
                        allocator = hkdfAllocator,
                        extractor = extractor,
                    )
                }
                check(hkdfArrays.size == 1 && hkdfArrays.single().all { it == 0.toByte() })
                check(requireNotNull(prk).all { it == 0.toByte() })

                var verificationPhase = false
                val verificationCalls = AtomicInteger()
                var verifierConfigCopy: ByteArray? = null
                val verifierCopier = SensitiveArrayCopier { source ->
                    if (!verificationPhase) {
                        source.copyOf()
                    } else {
                        when (verificationCalls.incrementAndGet()) {
                            1 -> source.copyOf().also { verifierConfigCopy = it }
                            else -> throw OutOfMemoryError("verifier-native-copy-injected")
                        }
                    }
                }
                ExpectedBinding(
                    fixture.inspection.packageName,
                    fixture.inspection.packageNameSha256,
                    fixture.signer.currentCertificateSha256,
                    fixture.signer.lineageCertificateSha256,
                    fixture.inspection.dexEntries,
                    config,
                    nativeShare,
                    RecordingObserver(),
                    verifierCopier,
                ).use { binding ->
                    verificationPhase = true
                    expectFailure<OutOfMemoryError> {
                        DexContainerVerifier(RecordingObserver()).verify(ownershipOutput, binding)
                    }
                }
                check(requireNotNull(verifierConfigCopy).all { it == 0.toByte() })

                val manifestObserver = RecordingObserver()
                ExpectedBinding.from(
                    fixture.inspection,
                    fixture.signer,
                    config,
                    nativeShare,
                    NO_CONTAINER_OBSERVER,
                ).use { binding ->
                    expectFailure<OutOfMemoryError> {
                        DexContainerVerifier(
                            manifestObserver,
                            DEFAULT_SENSITIVE_ARRAY_ALLOCATOR,
                            SensitiveArrayCopier { throw OutOfMemoryError("manifest-header-copy-injected") },
                        ).verify(ownershipOutput, binding)
                    }
                }
                check("verify.manifestKey" in manifestObserver.clearedLabels)

                val verifierObserver = RecordingObserver()
                val verifierArrays = ArrayList<ByteArray>()
                val verifierAllocator = SensitiveArrayAllocator { size ->
                    if (verifierArrays.isNotEmpty()) throw OutOfMemoryError("verifier-aad-injected")
                    ByteArray(size).also(verifierArrays::add)
                }
                ExpectedBinding.from(
                    fixture.inspection,
                    fixture.signer,
                    config,
                    nativeShare,
                    NO_CONTAINER_OBSERVER,
                ).use { binding ->
                    expectFailure<OutOfMemoryError> {
                        DexContainerVerifier(verifierObserver, verifierAllocator).verify(ownershipOutput, binding)
                    }
                }
                check("verify.ciphertext" in verifierObserver.clearedLabels)
                check(verifierArrays.size == 1 && verifierArrays.single().all { it == 0.toByte() })
            } finally {
                config.fill(0)
                nativeShare.fill(0)
            }
        }

        val lateObserver = OomCleanupObserver("plan.rNative")
        val lateOutput = work.resolve("oom-late.ahdc")
        val result = DexContainerBuilder(fixture.path, FixedRandom(), lateObserver, null)
            .build(fixture.inspection, fixture.signer, lateOutput)
        val primary = expectFailure<IllegalStateException> {
            result.keyPackagingPlan.consume<Unit> { throw IllegalStateException("oom-primary") }
        }
        check(primary.message == "oom-primary")
        check(lateObserver.labels.containsAll(listOf("plan.config", "plan.rNative", "plan.buildId", "plan.keySlotId")))
        check(lateObserver.allZero)
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
        name: String,
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
        val stage = when (name) {
            "version", "payload-length" -> "CONTAINER_HEADER"
            "record-length" -> "CONTAINER_RECORDS"
            "chunk-ordinal" -> "CONTAINER_CHUNKS"
            else -> "CONTAINER_STRUCTURE"
        }
        tamperEvidence += TamperEvidence(
            name,
            stage,
            expectedCode.name,
            ContainerCrypto.sha256(tampered).joinToString("") { "%02x".format(it.toInt() and 0xff) },
        )
    }

    private fun verifyAuthenticatedZlibTamper(
        fixture: Fixture,
        clean: Path,
        config: ByteArray,
        nativeShare: ByteArray,
        tampered: Path,
        mutation: String,
    ) {
        Files.copy(clean, tampered, StandardCopyOption.REPLACE_EXISTING)
        val headerBytes = FileChannel.open(clean, StandardOpenOption.READ).use { channel ->
            channel.readExact(0, AhConstants.HEADER_BYTES)
        }
        val header = AhdcV2Codec.parseHeader(headerBytes)
        val recordOffset = AhConstants.HEADER_BYTES.toLong() + header.signerPolicySize
        val recordBytes = FileChannel.open(clean, StandardOpenOption.READ).use { channel ->
            channel.readExact(recordOffset, AhConstants.RECORD_BYTES)
        }
        val record = AhdcV2Codec.parseRecord(recordBytes)
        val chunkOrdinal = if (mutation == "checksum") record.chunkCount - 1 else 0
        val chunk = expectedChunk(record, chunkOrdinal)
        val chunkBytes = AhdcV2Codec.chunk(chunk)
        val payloadBase = AhConstants.HEADER_BYTES.toLong() + header.signerPolicySize +
            header.recordTableSize + header.chunkTableSize
        val ciphertextOffset = payloadBase + chunk.payloadOffset
        val ciphertext = FileChannel.open(clean, StandardOpenOption.READ).use { channel ->
            channel.readExact(ciphertextOffset, chunk.plaintextLength + AhConstants.GCM_TAG_BYTES)
        }
        val cek = ConfigV2Codec.recoverCek(
            config,
            nativeShare,
            header.buildId,
            header.keySlotId,
            fixture.signer.currentCertificateSha256,
            fixture.inspection.packageNameSha256,
        )
        val key = ContainerCrypto.recordKey(cek, header.buildId, record.ordinal)
        val nonce = ContainerCrypto.chunkNonce(record.noncePrefix, chunkOrdinal)
        val aad = ContainerCrypto.chunkAad(
            headerBytes.headerVersionBytes(),
            header.buildId,
            header.keySlotId,
            fixture.signer.currentCertificateSha256,
            fixture.inspection.packageNameSha256,
            recordBytes,
            chunkBytes,
        )
        val compressed = ContainerCrypto.aesGcmDecrypt(key, nonce, aad, ciphertext)
        when (mutation) {
            "checksum" -> compressed[compressed.lastIndex] = (compressed.last().toInt() xor 1).toByte()
            "dictionary" -> {
                val cmf = compressed[0].toInt() and 0xff
                val high = (compressed[1].toInt() and 0xc0) or 0x20
                compressed[1] = (0..31).first { low -> ((cmf shl 8) or high or low) % 31 == 0 }.let { (high or it).toByte() }
            }
            "concatenated" -> hex("78da030000000001").copyInto(compressed)
            else -> error("unknown mutation: $mutation")
        }
        val replacement = ContainerCrypto.aesGcmEncrypt(key, nonce, aad, compressed)
        FileChannel.open(tampered, StandardOpenOption.WRITE).use { channel ->
            check(channel.write(ByteBuffer.wrap(replacement), ciphertextOffset) == replacement.size)
        }
        val observer = RecordingObserver()
        ExpectedBinding.from(fixture.inspection, fixture.signer, config, nativeShare, NO_CONTAINER_OBSERVER).use { binding ->
            expectCode(ContainerErrorCode.CONTAINER_FORMAT) { DexContainerVerifier(observer).verify(tampered, binding) }
        }
        check(observer.authenticated.get() == if (mutation == "checksum") record.chunkCount else 1)
        listOf(headerBytes, recordBytes, chunkBytes, ciphertext, cek, key, nonce, aad, compressed, replacement)
            .forEach(ByteArray::fillZero)
    }

    private fun verifyWithCopies(
        fixture: Fixture,
        container: Path,
        config: ByteArray,
        nativeShare: ByteArray,
        expectedCode: ContainerErrorCode,
    ) {
        val observer = RecordingObserver()
        ExpectedBinding.from(fixture.inspection, fixture.signer, config, nativeShare, NO_CONTAINER_OBSERVER).use { binding ->
            expectCode(expectedCode) { DexContainerVerifier(observer).verify(container, binding) }
        }
        check(observer.authenticated.get() == 0) { "tamper reached inflater: ${container.fileName}" }
    }

    private fun assertDescriptorSemanticsEqual(
        first: DexContainerDescriptor,
        second: DexContainerDescriptor,
        ignoreContainerHash: Boolean,
    ) {
        check(first.major == second.major && first.minor == second.minor && first.packageName == second.packageName)
        check(first.currentSignerSha256.contentEquals(second.currentSignerSha256))
        check(first.signerLineageSha256.size == second.signerLineageSha256.size)
        first.signerLineageSha256.indices.forEach { index ->
            check(first.signerLineageSha256[index].contentEquals(second.signerLineageSha256[index]))
        }
        check(first.records.size == second.records.size)
        first.records.indices.forEach { index ->
            val left = first.records[index]
            val right = second.records[index]
            check(left.ordinal == right.ordinal && left.name == right.name &&
                left.originalLength == right.originalLength && left.compressedLength == right.compressedLength &&
                left.chunkCount == right.chunkCount && left.firstChunkIndex == right.firstChunkIndex &&
                left.payloadOffset == right.payloadOffset && left.originalSha256.contentEquals(right.originalSha256))
        }
        if (!ignoreContainerHash) check(first.containerSha256.contentEquals(second.containerSha256))
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

    private fun zeroFixture(path: Path, size: Long): Fixture {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(AhConstants.CHUNK_PLAINTEXT_MAX)
        Files.newOutputStream(path, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING).use { file ->
            ZipOutputStream(file).use { zip ->
                zip.putNextEntry(ZipEntry("classes.dex").also { it.time = 0 })
                var remaining = size
                while (remaining > 0) {
                    val count = minOf(remaining, buffer.size.toLong()).toInt()
                    zip.write(buffer, 0, count)
                    digest.update(buffer, 0, count)
                    remaining -= count
                }
                zip.closeEntry()
            }
        }
        val packageName = "ah.fixtures.container.limit"
        val inputHash = ContainerCrypto.sha256(path)
        val signerDigest = ByteArray(32) { index -> (index * 11 + 3).toByte() }
        val inspection = ApkInspection(
            inputHash,
            ManifestSummary(packageName, ContainerCrypto.sha256(packageName.toByteArray()), 29, 36, null,
                "ah.fixtures.OriginalFactory", null),
            emptyList(),
            listOf(DexSummary("classes.dex", 0, size, 1, digest.digest())),
            NativeAbiSummary(emptyList()),
            emptyList(),
            "test",
            LimitsApplied(emptyMap()),
        )
        val signer = SignerPolicyV1(signerDigest, listOf(signerDigest), setOf(VerifiedScheme.V2))
        buffer.fill(0)
        inputHash.fill(0)
        signerDigest.fill(0)
        return Fixture(path, inspection, signer, listOf(ByteArray(16)))
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

    private inline fun <reified T : Throwable> expectFailure(action: () -> Unit): T {
        try {
            action()
            error("expected ${T::class.java.simpleName}")
        } catch (failure: Throwable) {
            check(failure is T) { "expected ${T::class.java.simpleName}, got ${failure::class.java.simpleName}" }
            return failure
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

    private data class TamperEvidence(
        val name: String,
        val stage: String,
        val code: String,
        val inputSha256: String,
    )
}

private class FixedRandom : ContainerRandom {
    private var counter = 0

    override fun fill(label: String, destination: ByteArray) {
        require(destination.size in 1..32)
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("M1-04:$label:${counter++}".toByteArray(Charsets.UTF_8))
        digest.copyInto(destination, endIndex = destination.size)
        digest.fill(0)
    }
}

private class RecordingObserver : ContainerObserver {
    private val live = HashMap<String, Int>()
    private var liveBytes = 0
    var maxAllocation = 0
        private set
    var clearFailures = 0
        private set
    var clearCount = 0
        private set
    val clearedLabels = ArrayList<String>()
    val authenticated = AtomicInteger()

    override fun cleared(label: String, allZero: Boolean) {
        check(label.isNotEmpty())
        clearedLabels += label
        clearCount++
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

private class ThrowingAllocationObserver(private val throwingLabel: String) : ContainerObserver {
    val clearedLabels = ArrayList<String>()
    var allZero = true
        private set

    override fun allocated(label: String, bytes: Int) {
        if (label == throwingLabel) throw OutOfMemoryError("allocation:$label")
    }

    override fun cleared(label: String, allZero: Boolean) {
        clearedLabels += label
        this.allZero = this.allZero && allZero
    }
}

private class ThrowingCleanupObserver(private val throwingLabels: Set<String>) : ContainerObserver {
    val labels = ArrayList<String>()
    var allZero = true
        private set

    override fun cleared(label: String, allZero: Boolean) {
        labels += label
        this.allZero = this.allZero && allZero
        if (label in throwingLabels) throw IllegalStateException("cleanup:$label")
    }
}

private class OomCleanupObserver(private val throwingLabel: String) : ContainerObserver {
    val labels = ArrayList<String>()
    var allZero = true
        private set
    private val failure = OutOfMemoryError("cleanup:$throwingLabel")

    override fun cleared(label: String, allZero: Boolean) {
        labels += label
        this.allZero = this.allZero && allZero
        if (label == throwingLabel) throw failure
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
