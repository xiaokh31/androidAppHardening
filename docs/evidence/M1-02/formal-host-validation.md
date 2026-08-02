# M1-02 formal Host validation

## Scope and frozen implementation

- Task: M1-02, Issue [#7](https://github.com/xiaokh31/androidAppHardening/issues/7).
- Branch: `feat/m1-02-signer-policy`.
- Base: `aebbc441da34d2fba78648415c1d80ea844d774d`.
- Frozen implementation: `146aac3795a1f92adefbab376939129e55975c65`.
- Product boundary: read-only input verification and public certificate identity only. Product code has no output signing, private-key, keystore, alias, password, HSM or remote-signing entry point.

The implementation uses the pinned `com.android.tools.build:apksig:9.3.0` verifier with minimum checked platform 29. It requires exactly one current signer, hashes the current X.509 DER certificate with SHA-256, records an authenticated oldest-to-newest lineage, and binds verification to the M1-01 inspection digest and the same open file handle. It does not encode `SPV1`; it only enforces the ADR 0004 model constraints needed by M1-04.

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
| project-local Gradle `:host:apk-inspector:clean :host:apk-inspector:signerPolicyTest` with offline verified dependencies | 0 | clean generation and signer matrix PASS in 100 seconds |
| project-local Gradle `clean check verifyGovernance` with offline verified dependencies | 0 | 256 actionable tasks; M1-01 10,000-sample regression and M1-02 signer matrix PASS |
| `node tools/governance/validate-project-package.mjs` | 0 | 26 task cards, 11 core docs and 7 ADRs PASS |
| `node .agents/skills/coordinate-project-handoff/scripts/validate-handoff.mjs HandOff.md --strict` | 0 | strict HandOff PASS |
| `git diff --check` and strict UTF-8 replacement-character scan of all M1-02 files | 0 | PASS |

The formal root validation ran from `2026-08-02T08:05:47.7488843Z` through `2026-08-02T08:08:29.6641619Z` and completed in 2 minutes 41 seconds. No emulator or physical device was started.

The optional local `node tools/validation/test-dependency-verification.mjs` invocation exited 1 before reaching the tampered-checksum assertion because its deliberate `--refresh-dependencies` copy could not resolve AGP through the restricted local network. The committed checksum remains present, the offline verified root build passed, and the unchanged fail-closed script remains a required Ubuntu CI step after publication.

## Positive and negative matrix

Positive fixtures cover v1, v2, v3, combined v1/v2/v3, a v4-generated APK with its ignored `.idsig`, and a valid two-certificate rotation lineage. Standalone APK verification deliberately does not consume `.idsig`; v4 is therefore not claimed as an independently verified scheme.

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
| signed-content tamper | `SIGNER_INVALID` |
| malformed signing-block trailer | `SIGNER_INVALID` |
| multiple current signers | `SIGNER_MULTIPLE_CURRENT` |
| invalid proof-of-rotation signature | `SIGNER_LINEAGE_INVALID` |
| inspection digest mismatch | `SIGNER_INPUT_CHANGED` |
| bytes changed after the initial snapshot | `SIGNER_INPUT_CHANGED` |

The invalid-lineage fixture changes the proof record itself. Android's official `SigningCertificateLineage.readFromApkDataSource` rejects that record with a security failure; the implementation does not implement or substitute a signature algorithm.

Model tests cover the unrotated and rotated cases, empty lineage, more than 16 entries, duplicate digests, a final digest different from the current signer, wrong digest length, defensive byte-array copies and lowercase 64-character encoding.

## Canonical reports

| Artifact | SHA-256 |
|---|---|
| `canonical-policy.json` | `b945ede114fd87771631b862c5f7a22120bc5aac2db6bbc836cfb608a54f52a2` |
| `error-matrix.json` | `ecd2193e7ec38418715cc7ee57023d0aa9ba9923d4001fa8d6d1da71cbea3762` |
| `artifact-manifest.json` | `187c200809051300e028bfc5270f43fc264c1e62baa414890fa501893d0b4488` |
| `capability-scan.txt` | `97c89653b10a7e7b2fd97b53e7ae2ccc53994d623de2fc7c56852d982adbfcfa` |

The artifact manifest fixes hashes for the unsigned, v1, v2, v3, combined, v4, rotated and multi-signer APKs, three DER certificates, the lineage and both canonical reports. Its `generated_at` is deliberately fixed to the Unix epoch.

The production source and bytecode capability scan passed across nine source files. It rejects references to the apksig signer CLI, `PrivateKey`, `KeyStore`, key/store passwords, signing executors and process execution.

## Cross-platform and review gates

`.github/workflows/build.yml` now requires Ubuntu 24.04 and Windows 2025 to regenerate both canonical M1-02 reports and match the exact hashes above after a clean root check. Windows is proven locally. Ubuntu byte equivalence remains pending publication and cannot be claimed before the branch runs in GitHub Actions.

Independent reviewer `m1_02_security_review` must review the frozen implementation and this evidence. P0, P1 and P2 must all be closed before publication is requested. The branch is not published, no M1-02 PR exists, and M1-03/M1-04/M2-03 remain unstarted.
