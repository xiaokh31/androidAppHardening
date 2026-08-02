package ah.host.inspector

import java.util.LinkedHashSet

internal object CompatibilityRules {
    private val supportedAbis = linkedSetOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")

    fun nativeAbis(entryNames: List<String>): NativeAbiSummary {
        val abis = LinkedHashSet<String>()
        for (name in entryNames) {
            val parts = name.split('/')
            if (parts.size == 3 && parts[0] == "lib" && parts[2].endsWith(".so") && parts[2].length > 3) {
                abis += parts[1]
            }
        }
        return NativeAbiSummary(abis.toList())
    }

    fun evaluate(
        safeFileName: String,
        manifest: ParsedManifest,
        entryNames: List<String>,
        descriptorMarkerIds: List<String>,
        nativeAbis: NativeAbiSummary,
    ): List<CompatibilityFinding> {
        if (manifest.summary.minSdk < 29) {
            reject(safeFileName, InspectionErrorCode.COMPAT_MIN_SDK, listOf("MIN_SDK_BELOW_29"), "minSdk")
        }

        val splitMarkers = LinkedHashSet<String>()
        splitMarkers += manifest.splitMarkers
        for (name in entryNames) {
            when {
                name == "BundleConfig.pb" -> splitMarkers += "AAB_BUNDLE_CONFIG"
                name == "toc.pb" -> splitMarkers += "APKS_TABLE_OF_CONTENTS"
                name.startsWith("splits/") -> splitMarkers += "APKS_SPLITS_DIRECTORY"
                name.startsWith("base/manifest/") -> splitMarkers += "AAB_BASE_MANIFEST"
            }
        }
        if (splitMarkers.isNotEmpty()) {
            reject(safeFileName, InspectionErrorCode.COMPAT_SPLIT, splitMarkers.toList())
        }

        val reserved = LinkedHashSet<String>()
        if (entryNames.any { it == "assets/ah/runtime" || it.startsWith("assets/ah/runtime/") }) {
            reserved += "AH_RUNTIME_ASSET_NAMESPACE"
        }
        if (entryNames.any { RESERVED_NATIVE.matches(it) }) reserved += "AH_RUNTIME_NATIVE_LIBRARY"
        if ("AH_RUNTIME_CLASS_NAMESPACE" in descriptorMarkerIds) reserved += "AH_RUNTIME_CLASS_NAMESPACE"
        if (reserved.isNotEmpty()) {
            reject(safeFileName, InspectionErrorCode.COMPAT_RESERVED_NAMESPACE, reserved.toList())
        }

        val shellMarkers = markerMatches(entryNames, descriptorMarkerIds, SHELL_MARKERS)
        if (shellMarkers.isNotEmpty()) {
            reject(safeFileName, InspectionErrorCode.COMPAT_EXISTING_SHELL, shellMarkers)
        }

        val frameworkMarkers = markerMatches(entryNames, descriptorMarkerIds, FRAMEWORK_MARKERS).toMutableList()
        if (nativeAbis.abis.any { it !in supportedAbis }) frameworkMarkers += "NATIVE_ABI_UNSUPPORTED"
        if (frameworkMarkers.isNotEmpty()) {
            reject(safeFileName, InspectionErrorCode.COMPAT_FRAMEWORK, frameworkMarkers.distinct())
        }

        val findings = ArrayList<CompatibilityFinding>()
        if (manifest.summary.applicationClass != null) {
            findings += CompatibilityFinding("CUSTOM_APPLICATION", "SUPPORTED_CONFIGURATION")
        }
        if (manifest.summary.hasAppComponentFactory) {
            findings += CompatibilityFinding("CUSTOM_APP_COMPONENT_FACTORY", "SUPPORTED_CONFIGURATION")
        }
        for (abi in nativeAbis.abis) {
            findings += CompatibilityFinding("NATIVE_ABI_${abi.uppercase().replace('-', '_')}", "SUPPORTED_ABI")
        }
        return immutableList(findings)
    }

    private fun markerMatches(
        entryNames: List<String>,
        descriptorMarkerIds: List<String>,
        rules: List<MarkerRule>,
    ): List<String> {
        val matches = LinkedHashSet<String>()
        for (rule in rules) {
            if (entryNames.any(rule.pathMatch) || rule.id in descriptorMarkerIds) matches += rule.id
        }
        return matches.toList()
    }

    private fun reject(
        safeFileName: String,
        code: InspectionErrorCode,
        markers: List<String>,
        limitName: String? = null,
    ): Nothing = throw InspectionException(
        code = code,
        safeFileName = safeFileName,
        limitName = limitName,
        markerIds = markers,
    )

    private data class MarkerRule(
        val id: String,
        val pathMatch: (String) -> Boolean = { false },
        val descriptorMatch: (String) -> Boolean = { false },
    )

    fun descriptorMarkerIds(descriptorPrefix: String): List<String> {
        val result = ArrayList<String>()
        if (descriptorPrefix.startsWith("Lah/runtime/")) result += "AH_RUNTIME_CLASS_NAMESPACE"
        for (rule in SHELL_MARKERS) {
            if (rule.descriptorMatch(descriptorPrefix)) result += rule.id
        }
        for (rule in FRAMEWORK_MARKERS) {
            if (rule.descriptorMatch(descriptorPrefix)) result += rule.id
        }
        return result
    }

    private val FRAMEWORK_MARKERS = listOf(
        MarkerRule(
            id = "FLUTTER_RUNTIME",
            pathMatch = { it.startsWith("assets/flutter_assets/") || it.matchesNative("libflutter.so") },
            descriptorMatch = { it.startsWith("Lio/flutter/") },
        ),
        MarkerRule(
            id = "UNITY_RUNTIME",
            pathMatch = { it.startsWith("assets/bin/Data/") || it.matchesNative("libunity.so") },
            descriptorMatch = { it.startsWith("Lcom/unity3d/player/") },
        ),
        MarkerRule(
            id = "REACT_NATIVE_RUNTIME",
            pathMatch = { it == "assets/index.android.bundle" || it.matchesNative("libreactnative.so") },
            descriptorMatch = { it.startsWith("Lcom/facebook/react/") },
        ),
        MarkerRule(id = "TINKER_HOTFIX", descriptorMatch = { it.startsWith("Lcom/tencent/tinker/") }),
        MarkerRule(
            id = "SOPHIX_HOTFIX",
            descriptorMatch = { it.startsWith("Lcom/taobao/sophix/") || it.startsWith("Lcom/ali/mobisecenhance/") },
        ),
        MarkerRule(id = "REPLUGIN_RUNTIME", descriptorMatch = { it.startsWith("Lcom/qihoo360/replugin/") }),
        MarkerRule(id = "VIRTUALAPK_RUNTIME", descriptorMatch = { it.startsWith("Lcom/didi/virtualapk/") }),
        MarkerRule(id = "DROIDPLUGIN_RUNTIME", descriptorMatch = { it.startsWith("Lcom/morgoo/droidplugin/") }),
    )

    private val SHELL_MARKERS = listOf(
        MarkerRule(
            id = "QIHO0_JIAGU_SHELL",
            pathMatch = { it.matchesNative("libjiagu.so") },
            descriptorMatch = { it == "Lcom/stub/StubApp;" },
        ),
        MarkerRule(
            id = "SECNEO_SHELL",
            pathMatch = { it.matchesNative("libDexHelper.so") },
            descriptorMatch = { it.startsWith("Lcom/secneo/apkwrapper/") },
        ),
        MarkerRule(id = "BANGCLE_SHELL", descriptorMatch = { it == "Ls/h/e/l/l/S;" }),
    )

    private val RESERVED_NATIVE = Regex("lib/[^/]+/libah_runtime\\.so")

    private fun String.matchesNative(fileName: String): Boolean {
        val parts = split('/')
        return parts.size == 3 && parts[0] == "lib" && parts[2] == fileName
    }
}
