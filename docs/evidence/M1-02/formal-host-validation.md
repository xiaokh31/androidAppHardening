# M1-02 formal Host validation

## Scope and frozen implementation

- Task: M1-02, Issue [#7](https://github.com/xiaokh31/androidAppHardening/issues/7).
- Branch: `feat/m1-02-signer-policy`.
- Base: `aebbc441da34d2fba78648415c1d80ea844d774d`.
- Frozen remediation implementation: `61908507c741865c50aac07763d42c890bf25d4b`.
- Product boundary: read-only input verification and public certificate identity only. Product code has no output signing, private-key, keystore, alias, password, HSM or remote-signing entry point.

The implementation uses the pinned `com.android.tools.build:apksig:9.3.0` verifier with minimum checked platform 29. It requires exactly one current signer, hashes the current X.509 DER certificate with SHA-256, records an authenticated oldest-to-newest lineage, and binds verification to the M1-01 inspection digest and the same open file handle. A bounded envelope check and a 32 MiB contiguous-read limit prevent apksig from materializing an attacker-sized Signing Block. Public failures retain only stable codes and safe file names, never raw causes. It does not encode `SPV1`; it only enforces the ADR 0004 model constraints needed by M1-04.

## Environment

| Item | Frozen value |
|---|---|
| OS | Windows 10 `10.0.19045` amd64 |
| JDK | Eclipse Temurin `17.0.19+10` |
| Gradle | `9.5.0` |
| Kotlin Gradle plugin | `2.4.10` |
| Android apksig | `9.3.0`; JAR SHA-256 `562cd0a88890960d2ece48e116c61f12872222f1dcc306890799382bc019b201` |
| Android Build Tools | package `36.1.0`; `apksigner version` reports `0.9` |
| Node.js | `24.12.0` |

All APKs, DER certificates, PKCS#8 keys and lineage files are deterministic synthetic test artifacts generated under ignored `host/apk-inspector/build/`. The private keys have no production value and are neither committed nor distributed.

## Commands and results

| Command | Exit | Result |
|---|---:|---|
| project-local Gradle `:host:apk-inspector:clean :host:apk-inspector:signerPolicyTest` with offline verified dependencies | 0 | remediation clean generation and signer matrix PASS in 102 seconds |
| project-local Gradle `clean check verifyGovernance` with offline verified dependencies | 0 | 256 actionable tasks; M1-01 10,000-sample regression and M1-02 signer matrix PASS |
| `node tools/governance/validate-project-package.mjs` | 0 | 26 task cards, 11 core docs and 7 ADRs PASS |
| `node .agents/skills/coordinate-project-handoff/scripts/validate-handoff.mjs HandOff.md --strict` | 0 | strict HandOff PASS |
| `git diff --check` and strict UTF-8 replacement-character scan of all M1-02 files | 0 | PASS |

The final remediation root validation ran from `2026-08-02T09:01:28.5534714Z` through `2026-08-02T09:04:11.7625329Z` and completed in 2 minutes 43 seconds. No emulator or physical device was started.

The optional local `node tools/validation/test-dependency-verification.mjs` invocation exited 1 before reaching the tampered-checksum assertion because its deliberate `--refresh-dependencies` copy could not resolve AGP through the restricted local network. The committed checksum remains present, the offline verified root build passed, and the unchanged fail-closed script remains a required Ubuntu CI step after publication.

## Positive and negative matrix

Positive fixtures cover v1, v2, v3, combined v1/v2/v3, a v4-generated APK with its ignored `.idsig`, and a valid two-certificate rotation lineage. Every fixture is independently run through pinned `apksigner --print-certs`; the rotation output is checked as current then historical signer records. Standalone product verification deliberately does not consume `.idsig`; v4 is therefore not claimed as an independently verified scheme.

The official `apksigner verify --min-sdk-version 29 --print-certs` digest and the verifier's DER digest are both:

```text
d183c6e5aa4fc22150451b37879c6bb8aa2fdc392b1dcf2fd45414fad9908a16
```

The authenticated lineage is stable oldest-to-newest:

```text
ba2af0c4efd0cf314c1db1ed5fbb28f283ef194627f53931c3fcfc293c1f4645
d183c6e5aa4fc22150451b37879c6bb8aa2fdc392b1dcf2fd45414fad9908a16
```

| Fixture | Stable result |
|---|---|
| unsigned | `SIGNER_UNSIGNED` |
| unsigned with magic-only ZIP padding | `SIGNER_UNSIGNED` |
| signed-content tamper | `SIGNER_INVALID` |
| malformed signing-block trailer | `SIGNER_INVALID` |
| structurally complete Signing Block above 32 MiB | `SIGNER_INVALID` before apksig materialization |
| declared oversized block with a truncated body | `SIGNER_INVALID` |
| size `0x8000000000000000` | `SIGNER_INVALID` |
| size `0xffffffffffffffff` | `SIGNER_INVALID` |
| multiple current signers | `SIGNER_MULTIPLE_CURRENT` |
| invalid proof-of-rotation signature | `SIGNER_LINEAGE_INVALID` |
| inspection digest mismatch | `SIGNER_INPUT_CHANGED` |
| bytes changed after the initial snapshot | `SIGNER_INPUT_CHANGED` |
| missing input with a sensitive parent path | `SIGNER_INTERNAL`; no cause or rendered path leakage |

The invalid-lineage fixture changes the proof record itself. Android's official `SigningCertificateLineage.readFromApkDataSource` rejects that record with a security failure; the implementation does not implement or substitute a signature algorithm.

Model tests cover the unrotated and rotated cases, empty lineage, more than 16 entries, duplicate digests, a final digest different from the current signer, wrong digest length, defensive byte-array copies and lowercase 64-character encoding.

## Canonical reports

| Artifact | SHA-256 |
|---|---|
| `canonical-policy.json` | `b945ede114fd87771631b862c5f7a22120bc5aac2db6bbc836cfb608a54f52a2` |
| `error-matrix.json` | `c33d342077c371878399c80e76ae025cd0efc56bfcca6d5bf80ffde4d75677c6` |
| `official-cross-check.json` | `c63d706f08763819e30c1e682fff87448a999a3ce53a27c7253e35ef9f82e2ba` |
| `artifact-manifest.json` | `d74287aec49cfd3cb18af55c6119b3ea90689d2f03bc15df8e5e8d04f43eb201` |
| `capability-scan.txt` | `97c89653b10a7e7b2fd97b53e7ae2ccc53994d623de2fc7c56852d982adbfcfa` |

The artifact manifest fixes hashes for every positive and negative APK, the v4 `.idsig`, three DER certificates, the lineage, capability scan, official cross-check and both canonical reports. Each error-matrix row records the product error and whether official verification accepts the underlying APK. Its `generated_at` is deliberately fixed to the Unix epoch.

The production source and bytecode capability scan passed across nine source files. It rejects references to the apksig signer CLI, `PrivateKey`, `KeyStore`, key/store passwords, signing executors and process execution.

## Cross-platform and review gates

`.github/workflows/build.yml` now requires Ubuntu 24.04 and Windows 2025 to regenerate both canonical M1-02 reports and match the exact hashes above after a clean root check. Windows is proven locally. Ubuntu byte equivalence remains pending publication and cannot be claimed before the branch runs in GitHub Actions.

The first independent review of evidence HEAD `21bfd6db333767c9182c1310e6cd838a8fae49a1` returned FAIL with P0 `0`, P1 `1`, P2 `3`; it is archived in `security-review-1.md`. The second review of remediation evidence HEAD `8718975255cfbdab4fc2ce29eae67c18f21b62ed` confirmed those four findings closed but returned FAIL with one P2 for high-bit size misclassification; it is archived in `security-review-2.md`. Commit `61908507c741865c50aac07763d42c890bf25d4b` classifies negative decoded sizes as malformed, adds both high-bit regressions and binds `input_changed` official status to the changed artifact. Both earlier targets are invalid.

The third independent read-only review of frozen evidence HEAD `902c20977d787ea9646078bbbe4c3c46bf0041cc` returned **PASS** with P0 `0`, P1 `0`, P2 `0`; it is archived in `security-review-3.md`. The reviewer independently reran the clean signer matrix and 256-task root validation, tested 64-bit and 32 MiB Signing Block boundaries under `-Xmx256m`, checked all 13 error rows, all 26 artifact hashes, the six official signer cross-checks, exception-chain redaction and the no-signing capability boundary. The local implementation and independent-review gate is closed, and M1-03/M1-04/M2-03 remain unstarted.

The branch was subsequently published as draft PR [#34](https://github.com/xiaokh31/androidAppHardening/pull/34). Initial Build run `30752847752` failed on both platforms before tests because `host:cli/gradle.lockfile` did not yet contain the transitive runtime dependency `com.android.tools.build:apksig:9.3.0`; both Governance jobs in run `30752847768` passed. The approved remediation regenerated only the downstream lock state, adding apksig 9.3.0 to `runtimeClasspath,testRuntimeClasspath` without changing a version, product code, public API, fixtures or frozen report bytes. The Windows offline root `clean check verifyGovernance` then passed in 1 minute 47 seconds with 256 actionable tasks and the same canonical report hashes.

Replacement Build run `30753702741` and Governance run `30753702728` passed on Ubuntu 24.04 and Windows 2025 at remediation HEAD `b72ef88003c2dea993afbd7d96d502535833e450`. Both explicit M1-02 byte-equivalence steps reproduced canonical policy SHA-256 `b945ede114fd87771631b862c5f7a22120bc5aac2db6bbc836cfb608a54f52a2` and error matrix SHA-256 `c33d342077c371878399c80e76ae025cd0efc56bfcca6d5bf80ffde4d75677c6`. Ubuntu also passed the dependency-verification tamper test and four-ABI gate. The lock remediation is therefore closed on both platforms.
