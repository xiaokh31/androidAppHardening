package ah.host.container

import ah.host.inspector.ApkInspector
import ah.host.inspector.ApkInspection
import ah.host.inspector.DexSummary
import ah.host.inspector.SignerPolicyVerifier
import ah.host.inspector.VerifiedScheme
import java.nio.file.Files
import java.nio.file.Path
import org.jf.dexlib2.Opcodes
import org.jf.dexlib2.Opcode
import org.jf.dexlib2.dexbacked.DexBackedDexFile
import org.jf.dexlib2.iface.Method
import org.jf.dexlib2.iface.debug.EndLocal
import org.jf.dexlib2.iface.debug.EpilogueBegin
import org.jf.dexlib2.iface.debug.LineNumber
import org.jf.dexlib2.iface.debug.PrologueEnd
import org.jf.dexlib2.iface.debug.RestartLocal
import org.jf.dexlib2.iface.debug.SetSourceFile
import org.jf.dexlib2.iface.debug.StartLocal
import org.jf.dexlib2.iface.instruction.ReferenceInstruction
import org.jf.dexlib2.iface.instruction.OneRegisterInstruction
import org.jf.dexlib2.iface.instruction.TwoRegisterInstruction
import org.jf.dexlib2.iface.reference.MethodReference
import org.jf.dexlib2.immutable.ImmutableExceptionHandler
import org.jf.dexlib2.immutable.ImmutableMethod
import org.jf.dexlib2.immutable.ImmutableMethodImplementation
import org.jf.dexlib2.immutable.ImmutableMethodParameter
import org.jf.dexlib2.immutable.ImmutableTryBlock
import org.jf.dexlib2.immutable.debug.ImmutableLineNumber
import org.jf.dexlib2.immutable.debug.ImmutableStartLocal
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction10x
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

/** Independent actual-byte verifier for the canonical pair and its two signed M3-10 profiles. */
object M310CanonicalProfileVerifier {
    private const val BASELINE_SHA256 = "4607d3289e1fc3bd95282ab47791ec810a5d2d3ac0a69fc0f91388901e412dcf"
    private const val PROTECTED_SHA256 = "1eb159d7f0149a943fb2e1c4d8467f283d1cfbbfad670628402cfb0cd23390d9"
    private const val CONFIG_ENTRY = "assets/ah/runtime/config.bin"
    private const val CONTAINER_ENTRY = "assets/ah/runtime/payload.ahdc"
    private const val DEX_ENTRY = "classes.dex"
    private const val SLOT_BYTES = 104

    @JvmStatic
    fun main(arguments: Array<String>) {
        if (arguments.contentEquals(arrayOf("--metadata-self-test"))) {
            metadataSelfTest()
            return
        }
        require(arguments.size == 8) {
            "usage: original-baseline.apk original-protected.apk profile-baseline.apk profile-protected.apk observer.dex derivation-manifest.json canonical-profile-lock.json report.json"
        }
        val originalBaseline = regular(arguments[0], "original baseline")
        val originalProtected = regular(arguments[1], "original protected")
        val profileBaseline = regular(arguments[2], "profile baseline")
        val profileProtected = regular(arguments[3], "profile protected")
        val observer = regular(arguments[4], "observer DEX")
        val derivationManifest = regular(arguments[5], "derivation manifest")
        val profileLock = regular(arguments[6], "canonical profile lock")
        val report = Path.of(arguments[7]).toAbsolutePath().normalize()
        require(!Files.exists(report)) { "verification report already exists" }
        Files.createDirectories(report.parent)
        val scratch = Files.createTempDirectory(report.parent, ".m310-verify-")
        try {
            verify(
                originalBaseline,
                originalProtected,
                profileBaseline,
                profileProtected,
                observer,
                derivationManifest,
                profileLock,
                report,
                scratch,
            )
        } finally {
            M310CanonicalProfileDeriver.deleteTree(scratch)
        }
    }

    private fun metadataSelfTest() {
        fun method(
            tryStart: Int = 0,
            tryCount: Int = 1,
            handlerAddress: Int = 0,
            lineAddress: Int = 0,
            lineNumber: Int = 7,
            localName: String = "value",
            localType: String = "I",
            localSignature: String? = null,
            parameterName: String = "input",
        ): Method = ImmutableMethod(
            "Ltest/M310;", "sample", listOf(ImmutableMethodParameter("I", emptySet(), parameterName)), "V", 0x8,
            emptySet(), emptySet(),
            ImmutableMethodImplementation(
                2,
                listOf(ImmutableInstruction10x(Opcode.NOP), ImmutableInstruction10x(Opcode.RETURN_VOID)),
                listOf(ImmutableTryBlock(tryStart, tryCount, listOf(ImmutableExceptionHandler("Ljava/lang/Exception;", handlerAddress)))),
                listOf(ImmutableLineNumber(lineAddress, lineNumber),
                    ImmutableStartLocal(0, 0, localName, localType, localSignature)),
            ),
        )
        val baseline = method()
        val mutations = linkedMapOf(
            "try-start" to method(tryStart = 1),
            "try-end" to method(tryCount = 2),
            "try-handler-target" to method(handlerAddress = 1),
            "debug-address" to method(lineAddress = 1),
            "debug-line" to method(lineNumber = 8),
            "debug-local-name" to method(localName = "changed"),
            "debug-local-type" to method(localType = "J"),
            "debug-local-signature" to method(localSignature = "Ljava/lang/Integer;"),
            "parameter-name" to method(parameterName = "changed"),
        )
        for ((name, candidate) in mutations) {
            val unchanged = trySignature(baseline, false) == trySignature(candidate, false) &&
                debugSignature(baseline, false) == debugSignature(candidate, false) &&
                baseline.parameters.map { Triple(it.type, it.name, annotationSignature(it.annotations)) } ==
                    candidate.parameters.map { Triple(it.type, it.name, annotationSignature(it.annotations)) }
            require(!unchanged) { "metadata mutation was accepted: $name" }
        }
        probeAdjacencySelfTest()
        println("M3-10 metadata self-test PASS mutations=${mutations.keys.joinToString(",")},all-probe-adjacency")
    }

    private fun probeAdjacencySelfTest() {
        fun rejected(name: String, tokens: List<String>, expected: List<String>) {
            val failure = runCatching { requireProbeAdjacencyTokens(tokens, expected, name) }.exceptionOrNull()
            require(failure != null) { "probe adjacency mutation was accepted: $name" }
        }
        requireProbeAdjacencyTokens(listOf("observer:p1", "opcode:NOP", "observer:p2", "opcode:RETURN_VOID"),
            listOf("p1", "p2"), "p-entry-exit")
        rejected("p-entry", listOf("opcode:NOP", "observer:p1", "observer:p2", "opcode:RETURN_VOID"), listOf("p1", "p2"))
        rejected("p-exit", listOf("observer:p1", "observer:p2", "opcode:NOP", "opcode:RETURN_VOID"), listOf("p1", "p2"))
        requireProbeAdjacencyTokens(listOf("observer:p15", "opcode:RETURN_VOID"), listOf("p15"), "p15")
        rejected("p15-entry", listOf("opcode:NOP", "observer:p15", "opcode:RETURN_VOID"), listOf("p15"))
        requireProbeAdjacencyTokens(listOf("observer:h0", "observer:h8", "opcode:RETURN_OBJECT"), listOf("h0", "h8"), "h0-h8")
        rejected("h0-entry", listOf("opcode:NOP", "observer:h0", "observer:h8", "opcode:RETURN_OBJECT"), listOf("h0", "h8"))
        rejected("h8-exit", listOf("observer:h0", "observer:h8", "opcode:NOP", "opcode:RETURN_OBJECT"), listOf("h0", "h8"))
        val guard = listOf("observer:h1",
            "method:Lah/runtime/guard/RuntimeSignerVerifier;->verify(Landroid/content/pm/ApplicationInfo;)Lah/runtime/guard/RuntimeSignerVerifier\$Measurement;",
            "opcode:MOVE_RESULT_OBJECT",
            "method:Lah/runtime/guard/RuntimeStartupGuard;->sha256(Ljava/lang/String;)[B", "opcode:MOVE_RESULT_OBJECT", "observer:h2",
            "method:Lah/runtime/guard/IntegrityChecks;->verifyPreReadSigner(Lah/runtime/loader/UntrustedPayloadBinding;[B)V", "observer:h3",
            "method:Lah/runtime/loader/PayloadRuntime;->openVerified(Ljava/lang/ClassLoader;Landroid/content/pm/ApplicationInfo;[B)Lah/runtime/loader/LoadedPayload;",
            "opcode:MOVE_RESULT_OBJECT", "observer:h4",
            "method:Lah/runtime/MemoryControls;->apply(Lah/runtime/loader/LoadedPayload;Lah/runtime/risk/RiskReportV1;)Lah/runtime/MemoryProtectionReport;",
            "observer:h5", "observer:h6", "opcode:RETURN_OBJECT")
        requireProbeAdjacencyTokens(guard, listOf("h1", "h2", "h3", "h4", "h5", "h6"), "guard")
        val guardPoints = listOf("h1", "h2", "h3", "h4", "h5", "h6")
        for ((point, position) in mapOf("h1" to 1, "h2" to 5, "h3" to 7, "h4" to 10, "h5" to 12, "h6" to 14)) {
            rejected("$point-gap", guard.toMutableList().apply { add(position, "opcode:NOP") }, guardPoints)
        }
        rejected("h1-overload", guard.toMutableList().apply {
            this[1] = "method:Lah/runtime/guard/RuntimeSignerVerifier;->verify(Ljava/lang/String;)V"
        }, guardPoints)
        rejected("h2-overload", guard.toMutableList().apply {
            this[3] = "method:Lah/runtime/guard/RuntimeStartupGuard;->sha256([B)[B"
        }, guardPoints)
        val ready = "field:SGET_OBJECT:r9:Lah/runtime/bootstrap/HardeningBootstrap\$State;->READY:Lah/runtime/bootstrap/HardeningBootstrap\$State;"
        val state = "field:IPUT_OBJECT:r9,r11:Lah/runtime/bootstrap/HardeningBootstrap\$Coordinator;->state:Lah/runtime/bootstrap/HardeningBootstrap\$State;"
        requireProbeAdjacencyTokens(listOf(ready, state, "observer:h7"), listOf("h7"), "h7")
        rejected("h7-gap", listOf(ready, state, "opcode:NOP", "observer:h7"), listOf("h7"))
        rejected("h7-wrong-owner", listOf(ready, state.replace("HardeningBootstrap\$Coordinator", "Other"), "observer:h7"), listOf("h7"))
        rejected("h7-wrong-type", listOf(ready, state.replace("HardeningBootstrap\$State;", "Ljava/lang/Object;"), "observer:h7"), listOf("h7"))
        rejected("h7-wrong-value", listOf(ready.replace("->READY", "->FAILED"), state, "observer:h7"), listOf("h7"))
        rejected("h7-wrong-register", listOf(ready.replace("r9:", "r8:"), state, "observer:h7"), listOf("h7"))
    }

    private fun verify(
        originalBaseline: Path,
        originalProtected: Path,
        profileBaseline: Path,
        profileProtected: Path,
        observer: Path,
        derivationManifest: Path,
        profileLock: Path,
        report: Path,
        scratch: Path,
    ) {
        val lock = Files.readString(profileLock, StandardCharsets.UTF_8)
        requireLockedFile(lock, "observer", observer, "dexSizeBytes", "dexSha256")
        requireLockedFile(lock, "derivation", derivationManifest, "manifestSizeBytes", "manifestSha256")
        requireLockedFile(lock, "signedBaseline", profileBaseline)
        requireLockedFile(lock, "signedProtected", profileProtected)
        M310CanonicalProfileDeriver.requireExactOriginal(originalBaseline, 29_962L, BASELINE_SHA256, "baseline")
        M310CanonicalProfileDeriver.requireExactOriginal(originalProtected, 1_287_876L, PROTECTED_SHA256, "protected")
        require(M310CanonicalProfileDeriver.sha256(profileBaseline) != BASELINE_SHA256 &&
            M310CanonicalProfileDeriver.sha256(profileProtected) != PROTECTED_SHA256
        ) { "a profile was mislabeled as an original" }

        val inspector = ApkInspector()
        val originalBaselineInspection = inspector.inspect(originalBaseline)
        val originalBaselineEntries = M310CanonicalProfileDeriver.readEntries(originalBaseline)
        val originalProtectedEntries = M310CanonicalProfileDeriver.readEntries(originalProtected)
        val profileBaselineEntries = M310CanonicalProfileDeriver.readEntries(profileBaseline)
        val profileProtectedEntries = M310CanonicalProfileDeriver.readEntries(profileProtected)
        val profileBaselineInspection = profileInspection(
            profileBaseline,
            originalBaselineInspection,
            profileBaselineEntries.getValue(DEX_ENTRY).bytes,
            originalBaselineInspection.dexEntries.single().classCount + 1,
        )
        require(originalBaselineInspection.packageName == profileBaselineInspection.packageName &&
            originalBaselineInspection.minSdk == profileBaselineInspection.minSdk &&
            originalBaselineInspection.targetSdk == profileBaselineInspection.targetSdk
        ) { "baseline profile package/SDK semantics differ" }
        val signerVerifier = SignerPolicyVerifier()
        val originalBaselineSigner = signerVerifier.verify(originalBaseline, originalBaselineInspection)
        val originalProtectedSigner = signerVerifier.verify(
            originalProtected,
            M310CanonicalProfileDeriver.signerOnlyInspection(originalProtected, originalBaselineInspection),
        )
        val profileBaselineSigner = signerVerifier.verify(profileBaseline, profileBaselineInspection)
        val profileProtectedSigner = signerVerifier.verify(
            profileProtected,
            M310CanonicalProfileDeriver.signerOnlyInspection(profileProtected, profileBaselineInspection),
        )
        require(originalBaselineSigner.currentCertificateSha256.contentEquals(originalProtectedSigner.currentCertificateSha256)) {
            "original signer identity differs"
        }
        require(profileBaselineSigner.currentCertificateSha256.contentEquals(profileProtectedSigner.currentCertificateSha256) &&
            !profileBaselineSigner.currentCertificateSha256.contentEquals(originalBaselineSigner.currentCertificateSha256) &&
            VerifiedScheme.V3 in profileBaselineSigner.verifiedSchemes &&
            VerifiedScheme.V3 in profileProtectedSigner.verifiedSchemes &&
            profileBaselineSigner.lineageCertificateSha256.size == 1 &&
            profileProtectedSigner.lineageCertificateSha256.size == 1
        ) { "profile signer/v3 semantics differ" }
        requireLockedSigner(lock, profileBaselineSigner.currentCertificateSha256)

        compareEntrySets(originalBaselineEntries, profileBaselineEntries, setOf(DEX_ENTRY), "baseline")
        val protectedAllowed = mutableSetOf(DEX_ENTRY, CONFIG_ENTRY, CONTAINER_ENTRY)
        RuntimeAbi.entries.forEach { abi -> protectedAllowed += "lib/${abi.directoryName}/libah_runtime.so" }
        compareEntrySets(originalProtectedEntries, profileProtectedEntries, protectedAllowed, "protected")
        require(originalBaselineEntries.getValue("AndroidManifest.xml").bytes.contentEquals(
            profileBaselineEntries.getValue("AndroidManifest.xml").bytes,
        ) && originalProtectedEntries.getValue("AndroidManifest.xml").bytes.contentEquals(
            profileProtectedEntries.getValue("AndroidManifest.xml").bytes,
        )) { "profile manifest bytes differ" }
        require(profileBaselineInspection.appComponentFactoryClass == null) { "baseline gained a synthetic Factory" }

        requireLockedDigest(lock, "profileBaselineDexSha256", profileBaselineEntries.getValue(DEX_ENTRY).bytes)
        requireProbeCalls(profileBaselineEntries.getValue(DEX_ENTRY).bytes, OUTER_CALLS, observerPresent = true)
        requireMetadataEquivalent(
            originalBaselineEntries.getValue(DEX_ENTRY).bytes,
            profileBaselineEntries.getValue(DEX_ENTRY).bytes,
            SYNTHETIC_OUTER_METHODS,
            "baseline payload",
        )
        requireSyntheticLifecycleOverrides(profileBaselineEntries.getValue(DEX_ENTRY).bytes)
        requireLockedDigest(lock, "profileProtectedShellDexSha256", profileProtectedEntries.getValue(DEX_ENTRY).bytes)
        requireProbeCalls(profileProtectedEntries.getValue(DEX_ENTRY).bytes, INNER_CALLS, observerPresent = true)
        requireMetadataEquivalent(
            originalProtectedEntries.getValue(DEX_ENTRY).bytes,
            profileProtectedEntries.getValue(DEX_ENTRY).bytes,
            emptySet(),
            "protected shell",
        )

        val profileConfig = profileProtectedEntries.getValue(CONFIG_ENTRY).bytes
        require(ConfigV2Codec.originalFactory(profileConfig) == null) { "profile config original Factory differs" }
        val profileSlots = M310CanonicalProfileDeriver.readAllRuntimeSlots(profileProtectedEntries, profileConfig)
        try {
            for (abi in RuntimeAbi.entries) {
                val name = "lib/${abi.directoryName}/libah_runtime.so"
                requireOnlyShareSlotChanged(
                    originalProtectedEntries.getValue(name).bytes,
                    profileProtectedEntries.getValue(name).bytes,
                    abi,
                )
            }
            val profileContainer = scratch.resolve("profile.ahdc")
            Files.write(profileContainer, profileProtectedEntries.getValue(CONTAINER_ENTRY).bytes)
            val canonicalPackageDigest = M310CanonicalProfileDeriver.toContainerInspection(originalBaselineInspection)
                .packageNameSha256
            val decrypted = M310CanonicalProfileDeriver.decryptPayload(
                profileContainer,
                profileConfig,
                profileSlots.rNative,
                canonicalPackageDigest,
                profileProtectedSigner.currentCertificateSha256,
            )
            try {
                require(decrypted.size == 1) { "profile authenticated payload count differs" }
                requireLockedDigest(lock, "profileProtectedPayloadDexSha256", decrypted.single())
                requireProbeCalls(decrypted.single(), OUTER_CALLS, observerPresent = false)
                requireMetadataEquivalent(
                    originalBaselineEntries.getValue(DEX_ENTRY).bytes,
                    decrypted.single(),
                    SYNTHETIC_OUTER_METHODS,
                    "protected payload",
                )
                requireSyntheticLifecycleOverrides(decrypted.single())
                val actualPayloadApk = scratch.resolve("actual-payload.apk")
                M310CanonicalProfileDeriver.writeApk(
                    originalBaselineEntries,
                    mapOf(DEX_ENTRY to decrypted.single()),
                    actualPayloadApk,
                )
                val actualPayloadInspection = M310CanonicalProfileDeriver.toContainerInspection(profileInspection(
                    actualPayloadApk,
                    originalBaselineInspection,
                    decrypted.single(),
                    originalBaselineInspection.dexEntries.single().classCount,
                ))
                ExpectedBinding.from(
                    actualPayloadInspection,
                    profileProtectedSigner,
                    profileConfig,
                    profileSlots.rNative,
                    NO_CONTAINER_OBSERVER,
                ).use { binding -> DexContainerVerifier().verify(profileContainer, binding) }
            } finally {
                decrypted.forEach { it.fill(0) }
            }
        } finally {
            profileSlots.rNative.fill(0)
        }

        val result = """
            {
              "schemaVersion": 1,
              "status": "PASS",
              "canonicalBaselineSha256": "$BASELINE_SHA256",
              "canonicalProtectedSha256": "$PROTECTED_SHA256",
              "profileBaselineSha256": "${M310CanonicalProfileDeriver.sha256(profileBaseline)}",
              "profileProtectedSha256": "${M310CanonicalProfileDeriver.sha256(profileProtected)}",
              "profileSignerSha256Prefix": "${profileBaselineSigner.currentCertificateSha256Hex.take(12)}",
              "profileLockSha256": "${M310CanonicalProfileDeriver.sha256(profileLock)}",
              "profileV3Verified": true,
              "sameProfileSigner": true,
              "manifestBytesEqual": true,
              "baselineFactoryAbsent": true,
              "authenticatedContainerVerified": true,
              "runtimeShareSlotsOnly": true,
              "exactProbeDexTransforms": true
            }
        """.trimIndent() + "\n"
        Files.writeString(report, result)
        println("M3-10 canonical profile verification PASS signer=${profileBaselineSigner.currentCertificateSha256Hex.take(12)}")
    }

    private fun compareEntrySets(
        original: Map<String, M310CanonicalProfileDeriver.EntryData>,
        profile: Map<String, M310CanonicalProfileDeriver.EntryData>,
        allowedChanges: Set<String>,
        label: String,
    ) {
        val originalNames = original.keys.filterNot(M310CanonicalProfileDeriver::isSignatureEntry).toSet()
        val profileNames = profile.keys.filterNot(M310CanonicalProfileDeriver::isSignatureEntry).toSet()
        require(originalNames == profileNames && profile.keys.none(M310CanonicalProfileDeriver::isSignatureEntry)) {
            "$label profile entry set/signature metadata differs"
        }
        for (name in originalNames - allowedChanges) {
            require(original.getValue(name).bytes.contentEquals(profile.getValue(name).bytes) &&
                original.getValue(name).method == profile.getValue(name).method
            ) { "$label changed a non-profile entry: $name" }
        }
        require(allowedChanges.all { name -> !original.getValue(name).bytes.contentEquals(profile.getValue(name).bytes) }) {
            "$label permitted-change set was not fully exercised"
        }
    }

    private fun requireOnlyShareSlotChanged(original: ByteArray, profile: ByteArray, abi: RuntimeAbi) {
        require(original.size == profile.size) { "runtime size differs for ${abi.directoryName}" }
        val originalOffset = M310CanonicalProfileDeriver.locateSlot(original, abi)
        val profileOffset = M310CanonicalProfileDeriver.locateSlot(profile, abi)
        require(originalOffset == profileOffset && original.indices.all { index ->
            index in originalOffset until originalOffset + SLOT_BYTES || original[index] == profile[index]
        }) { "runtime changed outside share slot for ${abi.directoryName}" }
    }

    private fun profileInspection(
        apk: Path,
        canonical: ApkInspection,
        dex: ByteArray,
        classCount: Int,
    ): ApkInspection = ApkInspection(
        ContainerCrypto.sha256(apk),
        canonical.manifest,
        canonical.zipEntries,
        listOf(DexSummary(DEX_ENTRY, 1, dex.size.toLong(), classCount, ContainerCrypto.sha256(dex))),
        canonical.nativeAbis,
        canonical.findings,
        canonical.compatibilityRulesVersion,
        canonical.limitsApplied,
    )

    private fun requireProbeCalls(
        dexBytes: ByteArray,
        expected: Map<String, List<String>>,
        observerPresent: Boolean,
    ) {
        val dex = ByteArrayInputStream(dexBytes).use { stream ->
            DexBackedDexFile.fromInputStream(Opcodes.forApi(36), BufferedInputStream(stream))
        }
        require(dex.classes.count { it.type == OBSERVER } == if (observerPresent) 1 else 0) {
            "observer class presence differs"
        }
        val actual = linkedMapOf<String, MutableList<String>>()
        for (classDef in dex.classes) {
            if (classDef.type == OBSERVER) continue
            for (method in classDef.methods) {
                val methodKey = "${classDef.type}->${method.key()}"
                for (instruction in method.implementation?.instructions ?: emptyList()) {
                    val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference ?: continue
                    if (reference.definingClass == OBSERVER) {
                        actual.getOrPut(methodKey) { mutableListOf() } += reference.name
                    }
                }
                expected[methodKey]?.let { requireProbeAdjacency(method, it, methodKey) }
            }
        }
        require(actual == expected.mapValues { it.value.toMutableList() }) {
            "observer callsite graph differs: $actual"
        }
    }

    private fun requireProbeAdjacency(method: Method, expectedPoints: List<String>, label: String) {
        val instructions = requireNotNull(method.implementation).instructions.toList()
        val tokens = instructions.map { instruction ->
            val reference = (instruction as? ReferenceInstruction)?.reference
            val opcodeName = instruction.opcode.name.uppercase().replace('-', '_')
            val registers = when (instruction) {
                is TwoRegisterInstruction -> "r${instruction.registerA},r${instruction.registerB}"
                is OneRegisterInstruction -> "r${instruction.registerA}"
                else -> "-"
            }
            when {
                reference is MethodReference && reference.definingClass == OBSERVER -> "observer:${reference.name}"
                reference is MethodReference -> "method:${reference.definingClass}->${reference.name}(" +
                    reference.parameterTypes.joinToString("") + ")${reference.returnType}"
                reference is org.jf.dexlib2.iface.reference.FieldReference ->
                    "field:$opcodeName:$registers:${reference.definingClass}->${reference.name}:${reference.type}"
                else -> "opcode:$opcodeName"
            }
        }
        requireProbeAdjacencyTokens(tokens, expectedPoints, label)
    }

    private fun requireProbeAdjacencyTokens(tokens: List<String>, expectedPoints: List<String>, label: String) {
        fun observer(index: Int): String? = tokens.getOrNull(index)?.takeIf { it.startsWith("observer:") }?.substringAfter(':')
        val positions = tokens.indices.filter { observer(it) != null }
        require(positions.mapNotNull(::observer) == expectedPoints) { "$label probe order differs" }
        when {
            expectedPoints.singleOrNull() == "p15" -> require(positions.single() == 0) { "$label p15 is not the entry boundary" }
            expectedPoints == listOf("h7") -> {
                val position = positions.single()
                val ready = Regex("^field:SGET_OBJECT:r([0-9]+):Lah/runtime/bootstrap/HardeningBootstrap\\\$State;->READY:Lah/runtime/bootstrap/HardeningBootstrap\\\$State;$")
                    .matchEntire(tokens.getOrNull(position - 2) ?: "")
                val state = Regex("^field:IPUT_OBJECT:r([0-9]+),r([0-9]+):Lah/runtime/bootstrap/HardeningBootstrap\\\$Coordinator;->state:Lah/runtime/bootstrap/HardeningBootstrap\\\$State;$")
                    .matchEntire(tokens.getOrNull(position - 1) ?: "")
                require(ready != null && state != null && ready.groupValues[1] == state.groupValues[1]) {
                    "$label h7 is not the exact READY publication"
                }
            }
            expectedPoints.first() in setOf("h1", "h2", "h3", "h4", "h5", "h6") -> {
                val targets = mapOf(
                    "h1" to "method:Lah/runtime/guard/RuntimeSignerVerifier;->verify(Landroid/content/pm/ApplicationInfo;)Lah/runtime/guard/RuntimeSignerVerifier\$Measurement;",
                    "h2" to "method:Lah/runtime/guard/RuntimeStartupGuard;->sha256(Ljava/lang/String;)[B",
                    "h3" to "method:Lah/runtime/guard/IntegrityChecks;->verifyPreReadSigner(Lah/runtime/loader/UntrustedPayloadBinding;[B)V",
                    "h4" to "method:Lah/runtime/loader/PayloadRuntime;->openVerified(Ljava/lang/ClassLoader;Landroid/content/pm/ApplicationInfo;[B)Lah/runtime/loader/LoadedPayload;",
                    "h5" to "method:Lah/runtime/MemoryControls;->apply(Lah/runtime/loader/LoadedPayload;Lah/runtime/risk/RiskReportV1;)Lah/runtime/MemoryProtectionReport;",
                )
                for ((point, target) in targets) {
                    val position = positions.single { observer(it) == point }
                    val exact = when (point) {
                        "h1" -> tokens.getOrNull(position + 1) == target
                        "h2", "h4" -> tokens.getOrNull(position - 2) == target &&
                            tokens.getOrNull(position - 1) == "opcode:MOVE_RESULT_OBJECT"
                        else -> tokens.getOrNull(position - 1) == target
                    }
                    require(exact) {
                        "$label $point is not at its exact invoke/result boundary"
                    }
                }
                val h6 = positions.single { observer(it) == "h6" }
                require(tokens.getOrNull(h6 + 1) == "opcode:RETURN_OBJECT") { "$label h6 is not the success return boundary" }
            }
            else -> {
                require(positions.first() == 0) { "$label entry probe is not first" }
                val exitPoint = expectedPoints.last()
                val returns = tokens.indices.filter { tokens[it] in setOf("opcode:RETURN", "opcode:RETURN_OBJECT", "opcode:RETURN_VOID") }
                require(returns.isNotEmpty() && returns.all { index -> observer(index - 1) == exitPoint }) {
                    "$label exit probe is not adjacent to every return"
                }
            }
        }
    }

    /**
     * Independent metadata comparison. This does not call the profile transformer and therefore
     * cannot approve a transformer bug by regenerating the same bug. Instruction equivalence is
     * separately checked by the tracked dexdump comparator after observer calls are removed.
     */
    private fun requireMetadataEquivalent(
        originalBytes: ByteArray,
        profileBytes: ByteArray,
        syntheticMethods: Set<String>,
        label: String,
    ) {
        val original = parseDex(originalBytes).classes.associateBy { it.type }
        val profile = parseDex(profileBytes).classes.filterNot { it.type == OBSERVER }.associateBy { it.type }
        require(original.keys == profile.keys) { "$label class set differs" }
        for ((type, left) in original) {
            val right = profile.getValue(type)
            require(left.accessFlags == right.accessFlags && left.superclass == right.superclass &&
                left.interfaces.toList() == right.interfaces.toList() && left.sourceFile == right.sourceFile &&
                annotationSignature(left.annotations) == annotationSignature(right.annotations)
            ) { "$label class metadata differs: $type" }
            val leftFields = left.fields.associate { field -> field.key() to field.signature() }
            val rightFields = right.fields.associate { field -> field.key() to field.signature() }
            require(leftFields == rightFields) { "$label field metadata differs: $type" }
            val leftMethods = left.methods.associateBy { it.key() }
            val rightMethods = right.methods.associateBy { it.key() }
            val allowed = syntheticMethods.filter { it.startsWith("$type->") }.map { it.substringAfter("->") }.toSet()
            require(rightMethods.keys - leftMethods.keys == allowed && leftMethods.keys - rightMethods.keys == emptySet<String>()) {
                "$label method set differs: $type"
            }
            for ((key, leftMethod) in leftMethods) {
                val rightMethod = rightMethods.getValue(key)
                val methodLabel = "$label method metadata: $type->$key"
                require(leftMethod.accessFlags == rightMethod.accessFlags) { "$methodLabel access flags differ" }
                require(annotationSignature(leftMethod.annotations) == annotationSignature(rightMethod.annotations)) {
                    "$methodLabel annotations differ"
                }
                require(leftMethod.parameters.map { Triple(it.type, it.name, annotationSignature(it.annotations)) } ==
                    rightMethod.parameters.map { Triple(it.type, it.name, annotationSignature(it.annotations)) }) {
                    "$methodLabel parameter metadata differs"
                }
                require(leftMethod.hiddenApiRestrictions == rightMethod.hiddenApiRestrictions) {
                    "$methodLabel hidden API restrictions differ"
                }
                require(leftMethod.implementation?.registerCount == rightMethod.implementation?.registerCount) {
                    "$methodLabel register count differs"
                }
                require(trySignature(leftMethod, stripObserver = false) == trySignature(rightMethod, stripObserver = true)) {
                    "$methodLabel try topology differs"
                }
                val leftDebug = debugSignature(leftMethod, stripObserver = false)
                val rightDebug = debugSignature(rightMethod, stripObserver = true)
                require(leftDebug == rightDebug) {
                    "$methodLabel debug metadata differs: original=$leftDebug profile=$rightDebug"
                }
            }
        }
    }

    private fun requireSyntheticLifecycleOverrides(dexBytes: ByteArray) {
        val classes = parseDex(dexBytes).classes.associateBy { it.type }
        requireExactOverride(
            classes.getValue("Lah/fixtures/android/m301/BenchmarkFixtureApplication;"),
            "attachBaseContext(Landroid/content/Context;)V",
            listOf("p3", "attachBaseContext", "p4"),
        )
        requireExactOverride(
            classes.getValue("Lah/fixtures/android/m301/FixtureActivity;"),
            "onResume()V",
            listOf("p13", "onResume", "p14"),
        )
    }

    private fun requireExactOverride(classDef: org.jf.dexlib2.iface.ClassDef, key: String, calls: List<String>) {
        val method = classDef.methods.singleOrNull { it.key() == key } ?: error("synthetic override is missing: ${classDef.type}->$key")
        require(method.accessFlags == 0x14 && method.annotations.isEmpty() && method.hiddenApiRestrictions.isEmpty()) {
            "synthetic override metadata differs: ${classDef.type}->$key"
        }
        val implementation = requireNotNull(method.implementation)
        require(implementation.tryBlocks.none() && implementation.debugItems.none()) {
            "synthetic override handler/debug surface differs: ${classDef.type}->$key"
        }
        val instructions = implementation.instructions.toList()
        require(instructions.map { it.opcode } == listOf(
            Opcode.INVOKE_STATIC, Opcode.INVOKE_SUPER, Opcode.INVOKE_STATIC, Opcode.RETURN_VOID,
        )) { "synthetic override opcode sequence differs: ${classDef.type}->$key" }
        val actualCalls = instructions.filterIsInstance<ReferenceInstruction>().mapNotNull {
            (it.reference as? MethodReference)?.name
        }
        require(actualCalls == calls) { "synthetic override call sequence differs: ${classDef.type}->$key" }
    }

    private fun parseDex(bytes: ByteArray): DexBackedDexFile = ByteArrayInputStream(bytes).use { stream ->
        DexBackedDexFile.fromInputStream(Opcodes.forApi(36), BufferedInputStream(stream))
    }

    private fun annotationSignature(values: Set<org.jf.dexlib2.iface.Annotation>): List<String> =
        values.map { annotation ->
            annotation.type + ":" + annotation.visibility + ":" + annotation.elements
                .sortedBy { it.name }.joinToString(",") { it.name + "=" + it.value.toString() }
        }.sorted()

    private fun org.jf.dexlib2.iface.Field.key(): String = "$name:$type"

    private fun org.jf.dexlib2.iface.Field.signature(): String = listOf(
        accessFlags.toString(), initialValue?.toString() ?: "null", annotationSignature(annotations).joinToString("|"),
        hiddenApiRestrictions.map { it.toString() }.sorted().joinToString("|"),
    ).joinToString(";")

    private fun trySignature(method: Method, stripObserver: Boolean): List<String> =
        method.implementation?.tryBlocks?.map { block ->
            val start = normalizedAddress(method, block.startCodeAddress, stripObserver)
            val end = normalizedAddress(method, block.startCodeAddress + block.codeUnitCount, stripObserver)
            "$start..$end:" + block.exceptionHandlers.joinToString(",") { handler ->
                "${handler.exceptionType ?: "*"}@${normalizedAddress(method, handler.handlerCodeAddress, stripObserver)}"
            }
        } ?: emptyList()

    private fun debugSignature(method: Method, stripObserver: Boolean): List<String> =
        method.implementation?.debugItems?.map { item ->
            val address = normalizedAddress(method, item.codeAddress, stripObserver)
            val payload = when (item) {
                is LineNumber -> "line=${item.lineNumber}"
                is StartLocal -> "register=${item.register},name=${item.name},type=${item.type},signature=${item.signature}"
                is EndLocal -> "register=${item.register},name=${item.name},type=${item.type},signature=${item.signature}"
                is RestartLocal -> "register=${item.register},name=${item.name},type=${item.type},signature=${item.signature}"
                is SetSourceFile -> "source=${item.sourceFile}"
                is PrologueEnd -> "prologue"
                is EpilogueBegin -> "epilogue"
                else -> "type=${item.debugItemType}"
            }
            "${item.javaClass.name.substringAfterLast('.')}@$address:$payload"
        } ?: emptyList()

    private fun normalizedAddress(method: Method, target: Int, stripObserver: Boolean): Int {
        val implementation = requireNotNull(method.implementation)
        var raw = 0
        var removed = 0
        for (instruction in implementation.instructions) {
            if (raw >= target) break
            if (stripObserver && instruction.isObserverCall()) {
                require(target >= raw + instruction.codeUnits) {
                    "metadata address falls inside an observer call: ${method.key()}@$target"
                }
                removed += instruction.codeUnits
            }
            raw += instruction.codeUnits
        }
        require(target >= 0) { "metadata address is negative: ${method.key()}@$target" }
        return target - removed
    }

    private fun org.jf.dexlib2.iface.instruction.Instruction.isObserverCall(): Boolean =
        ((this as? ReferenceInstruction)?.reference as? MethodReference)?.definingClass == OBSERVER

    private fun Method.key(): String = buildString {
        append(name)
        append('(')
        parameters.forEach { append(it.type) }
        append(')')
        append(returnType)
    }

    private fun requireLockedFile(
        lock: String,
        objectName: String,
        file: Path,
        sizeName: String = "sizeBytes",
        hashName: String = "sha256",
    ) {
        val body = Regex("\\\"${Regex.escape(objectName)}\\\"\\s*:\\s*\\{([^{}]*)}", RegexOption.DOT_MATCHES_ALL)
            .find(lock)?.groupValues?.get(1) ?: error("profile lock object is missing: $objectName")
        val size = Regex("\\\"${Regex.escape(sizeName)}\\\"\\s*:\\s*([0-9]+)")
            .find(body)?.groupValues?.get(1)?.toLong() ?: error("profile lock size is missing: $objectName")
        val digest = Regex("\\\"${Regex.escape(hashName)}\\\"\\s*:\\s*\\\"([0-9a-f]{64})\\\"")
            .find(body)?.groupValues?.get(1) ?: error("profile lock digest is missing: $objectName")
        require(Files.size(file) == size && M310CanonicalProfileDeriver.sha256(file) == digest) {
            "profile lock file differs: $objectName"
        }
    }

    private fun requireLockedDigest(lock: String, name: String, bytes: ByteArray) {
        val digest = Regex("\\\"${Regex.escape(name)}\\\"\\s*:\\s*\\\"([0-9a-f]{64})\\\"")
            .find(lock)?.groupValues?.get(1) ?: error("profile lock digest is missing: $name")
        require(M310CanonicalProfileDeriver.sha256(bytes) == digest) { "profile lock DEX differs: $name" }
    }

    private fun requireLockedSigner(lock: String, certificateDigest: ByteArray) {
        val expected = Regex("\\\"commitment\\\"\\s*:\\s*\\\"([0-9a-f]{64})\\\"")
            .find(lock)?.groupValues?.get(1) ?: error("profile signer commitment is missing")
        val prefix = "M3-10-PROFILE-SIGNER-V1\u0000".toByteArray(StandardCharsets.UTF_8)
        val commitment = ContainerCrypto.sha256(prefix + certificateDigest).joinToString("") { "%02x".format(it) }
        require(commitment == expected) { "profile signer commitment differs" }
    }

    private fun regular(value: String, label: String): Path = Path.of(value).toAbsolutePath().normalize().also {
        require(Files.isRegularFile(it)) { "$label is missing" }
    }

    private const val OBSERVER = "Lah/runtime/profile/M310StartupTimingObserver;"
    private val OUTER_CALLS = linkedMapOf(
        "Lah/fixtures/android/m301/BenchmarkFixtureApplication;-><init>()V" to listOf("p1", "p2"),
        "Lah/fixtures/android/m301/BenchmarkFixtureApplication;->attachBaseContext(Landroid/content/Context;)V" to
            listOf("p3", "p4"),
        "Lah/fixtures/android/m301/BenchmarkFixtureApplication;->onCreate()V" to listOf("p7", "p8"),
        "Lah/fixtures/android/m301/FixtureActivity;-><init>()V" to listOf("p9", "p10"),
        "Lah/fixtures/android/m301/FixtureActivity;->onCreate(Landroid/os/Bundle;)V" to listOf("p11", "p12"),
        "Lah/fixtures/android/m301/FixtureActivity;->onResume()V" to listOf("p13", "p14"),
        "Lah/fixtures/android/m301/FixtureActivity;->onWindowFocusChanged(Z)V" to listOf("p15"),
        "Lah/fixtures/android/m301/FixtureEventProvider;->onCreate()Z" to listOf("p5", "p6", "p6"),
    )
    private val INNER_CALLS = linkedMapOf(
        "Lah/runtime/bootstrap/HardeningBootstrap\$Coordinator;->install(Ljava/lang/ClassLoader;Landroid/content/pm/ApplicationInfo;)Lah/runtime/bootstrap/BootstrapResult;" to listOf("h7"),
        "Lah/runtime/bootstrap/ShellAppComponentFactory;->instantiateClassLoader(Ljava/lang/ClassLoader;Landroid/content/pm/ApplicationInfo;)Ljava/lang/ClassLoader;" to listOf("h0", "h8"),
        "Lah/runtime/guard/RuntimeStartupGuard;->openVerifiedPayloadInternal(Landroid/content/pm/ApplicationInfo;Ljava/lang/ClassLoader;Lah/runtime/guard/RuntimeStartupGuard\$GuardFailureProbe;)Lah/runtime/guard/VerifiedPayloadSession;" to
            listOf("h1", "h2", "h3", "h4", "h5", "h6"),
    )
    private val SYNTHETIC_OUTER_METHODS = setOf(
        "Lah/fixtures/android/m301/BenchmarkFixtureApplication;->attachBaseContext(Landroid/content/Context;)V",
        "Lah/fixtures/android/m301/FixtureActivity;->onResume()V",
    )
}
