# M1-03 formal Host validation

## Scope and current implementation

- Task: M1-03, Issue [#8](https://github.com/xiaokh31/androidAppHardening/issues/8).
- Branch: `feat/m1-03-binary-axml-transformer`.
- Base: `077e4be14865c777dbbf3c1a5a3d9609b3620868`.
- Current status: Host implementation candidate; synthetic Release/R8 APK construction and static validation pass. API 29 physical installation is blocked by the device-side MIUI install confirmation, while API 36 KVM parsing and independent review remain pending.
- Product boundary: binary Manifest bytes only. No production ZIP/APK writer, signing operation, DEX change, ConfigV2 encoder or Runtime change is present.

The implementation copies the caller input, validates the supplied M1-01 summary, parses binary AXML iteratively under fixed limits, appends only missing strings/resource-map data, replaces or appends one application Factory attribute, reparses the result and enforces an ordered semantic whitelist. Public results and hashes use defensive byte-array copies; public failures do not include input paths, Manifest text or nested causes.

## Environment

| Item | Value |
|---|---|
| OS | Windows 10 `10.0.19045` amd64 |
| JDK | Eclipse Temurin `17.0.19+10` from ignored repository-local toolchain |
| Gradle | `9.5.0` |
| Kotlin Gradle plugin | `2.4.10` |
| Android Platform | `36` |
| Android Build Tools | package `36.1.0`; `aapt2` reports `2.20-14042983` |
| Fuzz | seed `1295069235` (`0x4d313033`), 5,000 samples |

No emulator was started. The only physical-device command was the bounded API 29 arm64 acceptance runner described below; it stopped at the first rejected install and completed its package cleanup checks.

## Commands and current results

| Command | Exit | Result |
|---|---:|---|
| project-local JDK 17 `./gradlew :host:axml:test --offline` with pinned `aapt2` and Android 36 `android.jar` properties | 0 | five positive fixtures, thirteen stable negatives, real `aapt2 link/dump` cross-check and 5,000 malformed samples PASS |
| project-local JDK 17 `./gradlew check verifyGovernance --offline` | 0 | 237 actionable tasks; M1-01 10,000-sample and M1-02 signer regressions PASS |
| `node tools/validation/verify-m0-05-apks.mjs` against both transformed Release/R8 APKs | 0 | signer/config, dual DEX, JNI, ABI, R8, native extraction modes and no-plaintext-payload static gates PASS |
| bounded API 29 arm64 runner, 20 cold starts requested per variant | 1 | stopped before execution at the first `adb install`: `INSTALL_FAILED_USER_RESTRICTED: Install canceled by user`; no target or test package remained installed |
| `git diff --check` | 0 | current implementation diff has no whitespace errors |

The local Kotlin daemon could not create its marker under the user-local C-drive directory in the restricted workspace. Gradle automatically used its supported in-process fallback and every command above exited `0`; no tool or dependency was downloaded to C by these validation commands.

## Positive and negative coverage

Positive fixtures cover UTF-8, UTF-16, default/custom Application, absent/existing Factory, absent/existing resource map, metadata string/reference values and an unknown XML node chunk. The transform runs twice for every fixture and must produce identical bytes. Input and returned arrays are mutated in the test harness to prove input immutability and defensive result copies.

The explicit negative matrix currently contains:

| Case | Stable result |
|---|---|
| truncated root bytes | `AXML_MALFORMED` |
| declared root beyond available bytes | `AXML_MALFORMED` |
| unsupported string-pool flags | `AXML_UNSUPPORTED_ENCODING` |
| string count above fixed budget | `AXML_LIMIT_EXCEEDED` |
| truncated/oversized encoded string length | `AXML_MALFORMED` |
| truncated resource map | `AXML_MALFORMED` |
| duplicate application | `AXML_MALFORMED` |
| nesting over 1,024 | `AXML_LIMIT_EXCEEDED` |
| missing application | `AXML_APPLICATION_MISSING` |
| existing Shell Factory | `AXML_RESERVED_COLLISION` |
| reserved name/resource-ID collision | `AXML_RESERVED_COLLISION` |
| supplied M1-01 summary mismatch | `AXML_DIFF_VIOLATION` |
| Manifest over 16 MiB | `AXML_LIMIT_EXCEEDED` |

The 5,000-sample deterministic corpus accepted 220 structurally valid mutations and classified the remainder only through stable `AXML_*` errors. It produced no unexpected exception or crash.

## Canonical Host reports

| Artifact | SHA-256 |
|---|---|
| `transform-matrix.json` | `01757e930cd3bb0a60a9b7b2445db4fa0000f050c65f9e88dba82e1d4b3c5d63` |
| `error-matrix.json` | `54a785f3a4ccd698452fccedaec8e98d509907efcbf67578dd16d6cedcf4956b` |
| `fuzz-summary.json` | `7910c602477e7ff1b560075468468dd4ccfd9985468655ddca3a2174fdbca199` |
| `aapt2-cross-check.json` | `e9e3b0307b6953e5b8cc98b651925f6c6cfc5a15b081115138e225efcf69f38e` |

The transform report records before/after/diff hashes for every fixture. The `aapt2` report includes a Manifest independently produced by `aapt2 link`, then transformed and read by `aapt2 dump xmltree`; it confirms the Shell Factory while retaining the original `.AaptApplication` and `fixture.metadata=kept`.

## Test-only Release/R8 device fixtures

The ignored device harness links the original binary Manifest with pinned `aapt2`, invokes the production transformer as a separate Gradle process, replaces only `AndroidManifest.xml` in a synthetic M0-05 compatibility APK, then zipaligns and externally signs the ignored install copy with an ephemeral test key. This is validation infrastructure only; no APK/ZIP writer or signing capability is added to a production module.

| Variant | Original Manifest SHA-256 | Transformed Manifest SHA-256 | Signed test APK SHA-256 |
|---|---|---|---|
| extracted | `4e5343457cd915067391aaf1026d656173687cd2569ffdb6cc11c48a3c7e5ace` | `8050475b79e73ac4026bba45d45b99b06d3ef3bcc0584dbe0027525f3390318e` | `9c31c54ad001613130b2150937f95d94a75f5bc2cf12bc2af87e8206ac381c18` |
| direct | `f02908f191c4267c983da6d15d904dd51d9ae3adcb1b49537ea545f673119080` | `8ef367881530d7520106ff9e26f95f0df14a62d2bca9351bdb6db998d5106f8b` | `ece3317e12b420e7afb66ee16f08c42518772818ee6f16b97a52a3f2bb524f64` |

Pinned `aapt2 dump xmltree` observes `ah.runtime.bootstrap.ShellAppComponentFactory`, the original `PayloadApplication`, and `ah.m103.fixture=preserved` in both APKs. The existing M0-05 static verifier independently reports `PASS` for both transformed APKs.

## API 29 physical-device attempt

At `2026-08-03T12:56:58+08:00`, the runner verified a unique authorized device with API 29, `arm64-v8a`, 64-bit userspace, `user/release-keys`, `ro.secure=1`, `ro.debuggable=0`, and shell UID 2000. The first extracted-variant install was rejected by MIUI after about 18 seconds with `INSTALL_FAILED_USER_RESTRICTED`; therefore no instrumentation, lifecycle, cross-DEX, JNI, signer, metadata, cold-start, memory or plaintext-disk assertion is claimed for this attempt. Final `pm path` checks confirmed that all four target/test package names were absent. The serial and ignored signing material are not recorded in versioned evidence.

## Remaining gates

- Run the same four canonical reports on Ubuntu and Windows CI and require exact hashes.
- After the user accepts MIUI's USB-install confirmation, rerun the bounded API 29 arm64 matrix once; do not treat the rejected installation as an acceptance pass.
- Publish only after explicit authorization, then run the existing timeout/cleanup-controlled API 29/36 x86_64 Linux/KVM workflow against the transformed APKs.
- Freeze the implementation/evidence commit and obtain an independent parser/security review with P0/P1/P2 all zero.
- Do not publish the branch or create a PR before explicit user authorization.
