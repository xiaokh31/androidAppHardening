# M0-03 PR #28 跨平台 CI 证据

## Snapshot

- task_id: `M0-03`
- git_commit: `cd182b0e762eda914ca297a5f9fdeada13dad6a9`
- issue: `https://github.com/xiaokh31/androidAppHardening/issues/3`
- pull_request: `https://github.com/xiaokh31/androidAppHardening/pull/28`
- validation_mode: `pre-cli`
- fixture_validation: `not_applicable; M0-03 建立工具链和空模块图，不提供也不消费 APK fixture`
- immutable_commit_tree_file_count: `131; HandOff.md excluded`
- immutable_commit_tree_sha256: `18c63310289a6c03781250430188da0bcb90bab7b106adf6c38992708ad3c4d1`

## Successful Required Checks

| Check | Started | Completed | Exit code | Result |
| --- | --- | --- | ---: | --- |
| [Build (ubuntu-24.04)](https://github.com/xiaokh31/androidAppHardening/actions/runs/30601222919/job/91064122142) | `2026-07-31T03:17:58Z` | `2026-07-31T03:20:09Z` | `0` | `PASS` |
| [Build (windows-2025)](https://github.com/xiaokh31/androidAppHardening/actions/runs/30601222919/job/91064122076) | `2026-07-31T03:17:58Z` | `2026-07-31T03:20:58Z` | `0` | `PASS` |
| [Governance (ubuntu-24.04)](https://github.com/xiaokh31/androidAppHardening/actions/runs/30601222904/job/91064122122) | `2026-07-31T03:17:58Z` | `2026-07-31T03:18:10Z` | `0` | `PASS` |
| [Governance (windows-2025)](https://github.com/xiaokh31/androidAppHardening/actions/runs/30601222904/job/91064122127) | `2026-07-31T03:18:02Z` | `2026-07-31T03:18:28Z` | `0` | `PASS` |

## Platform and Toolchain Evidence

Both Build jobs installed or resolved the same pinned task-card versions. The runner label is the OS contract; the successful setup, version assertion, package installation and build steps are the observable evidence.

| Field | Ubuntu | Windows |
| --- | --- | --- |
| OS | `GitHub-hosted ubuntu-24.04 x64` | `GitHub-hosted windows-2025 x64` |
| JDK | `Temurin 17.0.19+10` | `Temurin 17.0.19+10` |
| Gradle | `9.5.0 Wrapper; distribution SHA-256 verified` | `9.5.0 Wrapper; distribution SHA-256 verified` |
| Node.js | `24.12.0` | `24.12.0` |
| Android Platform | `platforms;android-36` | `platforms;android-36` |
| Build Tools | `build-tools;36.1.0` | `build-tools;36.1.0` |
| NDK | `ndk;29.0.14206865` | `ndk;29.0.14206865` |
| CMake | `cmake;4.1.2` | `cmake;4.1.2` |

## Commands and Acceptance Results

- Ubuntu `./gradlew --no-daemon clean check lint verifyGovernance :runtime:native:assemble`: exit `0`.
- Ubuntu `node tools/validation/test-dependency-verification.mjs`: exit `0`; tampered checksum resolution failed closed and restored metadata passed.
- Ubuntu `llvm-readelf -h` over the stripped debug `armeabi-v7a`, `arm64-v8a`, `x86`, and `x86_64` libraries: all exit `0` with architecture-matched ELF headers.
- Windows `.\gradlew.bat --no-daemon clean check verifyGovernance`: exit `0`.
- Ubuntu and Windows permanent governance validation, strict pending-main HandOff validation, negative HandOff tests, sensitive scan and Git object verification: all exit `0`.
- Local module graph, offline build and four-library SHA-256 evidence remain in `docs/evidence/M0-03/local-windows.md`; the CI-only fixes changed no native source or library inputs.

## Final Supply-Chain Hashes

| Artifact | SHA-256 or Git mode |
| --- | --- |
| Final `gradle/verification-metadata.xml` committed blob | `16ed13c7a611f25c7dd0576d6262ce1bb60b080fafcffa2957f18569bd6da8b3` |
| Official Google Maven `aapt2-9.3.0-15703166-linux.jar` | `e772a3dae8354764f1b0793903218427f483982445207f2e4ffc8c2026755bd4` |
| `gradlew` content | `ab5c0cad16305af2e619c159c1f58dd68d07fab9c11e36701e109c0277407f7a` |
| `gradlew` Git mode | `100755` |
| Gradle `9.5.0` binary distribution | `553c78f50dafcd54d65b9a444649057857469edf836431389695608536d6b746` |
| Gradle Wrapper JAR | `497c8c2a7e5031f6aa847f88104aa80a93532ec32ee17bdb8d1d2f67a194a9c7` |

The Linux `aapt2` artifact was downloaded twice from the fixed official Google Maven URL; both copies were 2,369,543 bytes and had the same SHA-256. The archive contained `META-INF/MANIFEST.MF`, `aapt2`, and `NOTICE`; it was inspected but not executed locally.

## Security and Compatibility Conclusion

- Input immutability: `not_applicable; no APK input is handled by M0-03`.
- Signing boundary: `PASS; no product module accepts signing material or invokes signing tools`.
- Compatibility: `PASS; Windows and Ubuntu Host checks succeeded, API 29 remains the minimum, and four empty Runtime ABI libraries were built and inspected`.
- Supply chain: `PASS; repositories remain limited to Google Maven and Maven Central, all Actions use full commit SHAs, and dependency verification remains strict`.
- Independent security review: `not_required; M0-03 is not security_sensitive and contains no protection implementation`.
- Residual risk: `None for the M0-03 acceptance scope`.
