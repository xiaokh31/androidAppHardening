# M2-05 local validation

## Environment

- Timestamp: 2026-08-12T12:58:00+08:00
- Branch base: `main@029f7af5a183b18704e088bcde89ab1e80f6a278`
- Frozen implementation: `d791dd2312c271ef2b976664c5e44538e22a103f`
- Evidence candidate: `c605a2e680c83de1a2d2440cd01795c5d49d32b4`
- Host: Windows 10 x64, PowerShell, Node.js `24.12.0`
- JDK: Eclipse Temurin `17.0.19+10`
- Android toolchain: compileSdk 36, Build Tools 36.1.0, NDK 29.0.14206865, CMake 4.1.2
- Physical probe: Xiaomi sirius, API 29, `arm64-v8a,armeabi-v7a`, `user`, `ro.debuggable=0`; no local emulator

## Results

| Command / gate | Exit | Result |
|---|---:|---|
| `node tools/governance/validate-project-package.mjs` | 0 | 28 tasks, 11 core docs, 10 ADRs |
| `node tools/validation/verify-m2-05-environment-risk.mjs build/m2-05/local/risk-policy-report.json` | 0 | fixed weights/actions, no DENY, ABI zero, bounded/redacted sources |
| `gradlew clean check lint verifyGovernance :runtime:native:assembleRelease` with pinned offline tools | 0 | 463 tasks; all existing Host/Runtime tests plus M2-05 matrix passed |
| `gradlew :runtime:policy:test :runtime:policy:compileDebugAndroidTestJavaWithJavac :runtime:policy:lintRelease :runtime:policy:assembleRelease :runtime:native:assembleRelease` | 0 | M2-05 54-case JVM matrix, Android test compile, lint and four ABI Release passed |
| `node tools/validation/verify-m2-04-four-abi-runtime.mjs --self-test --report=build/m2-05/local/native-runtime.json` | 0 | four ABI ELF/share slot and approved six-symbol JNI surface passed |
| API 29 ARM64 direct Native `risk_signals_test` | 0 | 10 parser/current-process cases passed; pushed executable removed and absence confirmed |
| `gradlew :runtime:policy:connectedCheck` on the same physical device | 1 (pre-test) | installation stopped before tests with `INSTALL_FAILED_USER_RESTRICTED`; no result was counted. Full instrumentation is delegated once to API 29/36 KVM rather than repeating prompts. |

## Artifacts

| Artifact | Bytes | SHA-256 |
|---|---:|---|
| `runtime/policy/build/outputs/aar/policy-release.aar` | 33568 | `250df3267558d8e7a71defcf52c384265750bd8226fe7bb0e8c0a8e1b42dbb95` |
| `runtime/native/build/outputs/aar/native-release.aar` | 442458 | `9a4a9c382497b8c099e4387f20004daed67e06f462eb830518ca7fc66c35632b` |
| `runtime/policy/build/reports/m2-05/risk-report-v1.json` | 399 | `f24c51538702e7cbcfae17530d65c120080d5950d3cb09d30d8ec7f4c22477c1` |
| ignored `build/m2-05/local/risk-policy-report.json` | 374 | `fc3582f6a670b3acd92fc1cf9f5f54ca8d06db6bab11a5e08f33c24908282660` |
| ignored `build/m2-05/local/native-runtime.json` | 5241 | `2c9ec90d73a1bdf2ece34d8d25d49bf2c8500d74b0e88ccff31cc68233a7d130` |
| ignored API 29 ARM64 probe executable | 64664 | `f462fd00e404d4d47c0e3735edc1968edcde5e63f6b002d7b06e55b8ab7b27c5` |

The report surface contains only signal IDs, state/hit, score, total, level and action. Scans found no raw proc text, mapping path, process list, device identifier, credential, key or plaintext DEX. Environment signals remain bypassable cost inputs and cannot create a deny/integrity result.
