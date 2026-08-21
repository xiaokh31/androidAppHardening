package ah.host.container

import ah.host.inspector.ApkInspector
import ah.host.inspector.ApkInspection
import ah.host.inspector.DexSummary
import ah.host.inspector.SignerPolicyVerifier
import ah.host.inspector.VerifiedScheme
import java.nio.file.Files
import java.nio.file.Path
import org.jf.dexlib2.Opcodes
import org.jf.dexlib2.dexbacked.DexBackedDexFile
import org.jf.dexlib2.iface.Method
import org.jf.dexlib2.iface.instruction.ReferenceInstruction
import org.jf.dexlib2.iface.reference.MethodReference
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream

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
        require(arguments.size == 6) {
            "usage: original-baseline.apk original-protected.apk profile-baseline.apk profile-protected.apk observer.dex report.json"
        }
        val originalBaseline = regular(arguments[0], "original baseline")
        val originalProtected = regular(arguments[1], "original protected")
        val profileBaseline = regular(arguments[2], "profile baseline")
        val profileProtected = regular(arguments[3], "profile protected")
        val observer = regular(arguments[4], "observer DEX")
        val report = Path.of(arguments[5]).toAbsolutePath().normalize()
        require(!Files.exists(report)) { "verification report already exists" }
        Files.createDirectories(report.parent)
        val scratch = Files.createTempDirectory(report.parent, ".m310-verify-")
        try {
            verify(originalBaseline, originalProtected, profileBaseline, profileProtected, observer, report, scratch)
        } finally {
            M310CanonicalProfileDeriver.deleteTree(scratch)
        }
    }

    private fun verify(
        originalBaseline: Path,
        originalProtected: Path,
        profileBaseline: Path,
        profileProtected: Path,
        observer: Path,
        report: Path,
        scratch: Path,
    ) {
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

        val expectedBaselineDex = scratch.resolve("expected-baseline.dex")
        val expectedPayloadDex = scratch.resolve("expected-protected-payload.dex")
        val expectedShellDex = scratch.resolve("expected-shell.dex")
        val originalPayloadDex = scratch.resolve("original-payload.dex")
        val originalShellDex = scratch.resolve("original-shell.dex")
        Files.write(originalPayloadDex, originalBaselineEntries.getValue(DEX_ENTRY).bytes)
        Files.write(originalShellDex, originalProtectedEntries.getValue(DEX_ENTRY).bytes)
        M310DexProfileTool.derive("payload-baseline", originalPayloadDex, observer, expectedBaselineDex)
        M310DexProfileTool.derive("payload-protected", originalPayloadDex, observer, expectedPayloadDex)
        M310DexProfileTool.derive("shell", originalShellDex, observer, expectedShellDex)
        require(Files.readAllBytes(expectedBaselineDex).contentEquals(profileBaselineEntries.getValue(DEX_ENTRY).bytes)) {
            "baseline DEX is not the exact reviewed transform"
        }
        requireProbeCalls(profileBaselineEntries.getValue(DEX_ENTRY).bytes, OUTER_CALLS, observerPresent = true)
        require(Files.readAllBytes(expectedShellDex).contentEquals(profileProtectedEntries.getValue(DEX_ENTRY).bytes)) {
            "protected shell DEX is not the exact reviewed transform"
        }
        requireProbeCalls(profileProtectedEntries.getValue(DEX_ENTRY).bytes, INNER_CALLS, observerPresent = true)

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
            val expectedPayloadApk = scratch.resolve("expected-payload.apk")
            M310CanonicalProfileDeriver.writeApk(
                originalBaselineEntries,
                mapOf(DEX_ENTRY to Files.readAllBytes(expectedPayloadDex)),
                expectedPayloadApk,
            )
            val expectedPayloadBytes = Files.readAllBytes(expectedPayloadDex)
            val expectedPayloadInspection = M310CanonicalProfileDeriver.toContainerInspection(profileInspection(
                expectedPayloadApk,
                originalBaselineInspection,
                expectedPayloadBytes,
                originalBaselineInspection.dexEntries.single().classCount,
            ))
            val profileContainer = scratch.resolve("profile.ahdc")
            Files.write(profileContainer, profileProtectedEntries.getValue(CONTAINER_ENTRY).bytes)
            ExpectedBinding.from(
                expectedPayloadInspection,
                profileProtectedSigner,
                profileConfig,
                profileSlots.rNative,
                NO_CONTAINER_OBSERVER,
            ).use { binding -> DexContainerVerifier().verify(profileContainer, binding) }
            val decrypted = M310CanonicalProfileDeriver.decryptPayload(
                profileContainer,
                profileConfig,
                profileSlots.rNative,
                expectedPayloadInspection.packageNameSha256,
                profileProtectedSigner.currentCertificateSha256,
            )
            try {
                require(decrypted.size == 1 && decrypted.single().contentEquals(Files.readAllBytes(expectedPayloadDex))) {
                    "profile authenticated payload is not the exact reviewed transform"
                }
                requireProbeCalls(decrypted.single(), OUTER_CALLS, observerPresent = false)
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
                for (instruction in method.implementation?.instructions ?: emptyList()) {
                    val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference ?: continue
                    if (reference.definingClass == OBSERVER) {
                        actual.getOrPut("${classDef.type}->${method.key()}") { mutableListOf() } += reference.name
                    }
                }
            }
        }
        require(actual == expected.mapValues { it.value.toMutableList() }) {
            "observer callsite graph differs: $actual"
        }
    }

    private fun Method.key(): String = buildString {
        append(name)
        append('(')
        parameters.forEach { append(it.type) }
        append(')')
        append(returnType)
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
}
