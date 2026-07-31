# M0-03 本地 Windows 验证证据

## Snapshot

- task_id: `M0-03`
- git_commit: `305a60898e04d5ab631534705d7b37c2f533021d`
- issue: `https://github.com/xiaokh31/androidAppHardening/issues/3`
- validation_mode: `pre-cli`
- fixture_validation: `not_applicable; M0-03 建立工具链和空模块图，不提供也不消费 APK fixture`
- timestamp: `2026-07-31T10:16:15+08:00`
- environment: `Microsoft Windows NT 10.0.19045.0 x64; Git 2.52.0.windows.1; Temurin JDK 17.0.19+10; Gradle 9.5.0; Node.js 24.12.0; Android Platform 36; Build Tools 36.1.0; NDK 29.0.14206865; CMake 4.1.2; Android clang 21.0.0`
- immutable_commit_tree_sha256: `ab4dadcc7b9a5e2ca120f1a97d11df43b59ba7de3555163ccc5ea3bbdc70b8fa`

## Commands and Results

| Command | Exit code | Result |
| --- | ---: | --- |
| `.\gradlew.bat --no-daemon projects` | `0` | `PASS; all fourteen required leaf modules are present and no business module is present` |
| `.\gradlew.bat --no-daemon clean check lint verifyGovernance :runtime:native:assemble` | `0` | `PASS; 237 tasks completed and all four native ABIs were assembled` |
| `.\gradlew.bat --no-daemon --offline clean check lint verifyGovernance :runtime:native:assemble` | `0` | `PASS; 237 tasks completed from the verified local cache` |
| `node tools/validation/test-dependency-verification.mjs` | `0` | `PASS; tampered checksum resolution exited 1 and restored metadata exited 0` |
| `node tools/validation/verify-m0-toolchain.mjs` | `0` | `PASS; pinned versions, repositories, fourteen-module graph, locks, checksums and empty-source boundary verified` |
| `node tools/validation/test-m0-toolchain-policy.mjs` | `0` | `PASS; positive case and policy tamper cases verified` |
| `node tools/governance/validate-project-package.mjs` | `0` | `PASS; 25 task cards, 11 core documents and 6 ADRs verified with generated directories excluded` |
| `node .agents/skills/coordinate-project-handoff/scripts/validate-handoff.mjs HandOff.md --strict` | `0` | `PASS; active M0-03 handoff snapshot verified before the implementation commit` |
| `git diff --cached --check` | `0` | `PASS; staged implementation had no whitespace errors` |
| `llvm-readobj --file-headers <four stripped debug libraries>` | `0` | `PASS; ELF32 ARM, ELF64 AArch64, ELF32 i386 and ELF64 x86-64 headers match their ABI directories` |

## Reproducibility Hashes

Hashes below are SHA-256. Text-file hashes are computed from the committed Git blobs, so Windows working-tree line-ending conversion cannot change the evidence.

| Artifact | SHA-256 |
| --- | --- |
| Gradle `9.5.0` binary distribution | `553c78f50dafcd54d65b9a444649057857469edf836431389695608536d6b746` |
| Temurin `17.0.19+10` Windows x64 ZIP | `b5b235c48adf6a081874b812c630b9f4b5f637b7a5ed18b9174d08a41ec4c235` |
| `gradle/wrapper/gradle-wrapper.jar` | `497c8c2a7e5031f6aa847f88104aa80a93532ec32ee17bdb8d1d2f67a194a9c7` |
| `gradle/wrapper/gradle-wrapper.properties` | `2cb0f9ddf992a26fc4947c91019136bff5e1ccfbf93bd905402b1abf69d15bc5` |
| `gradle/libs.versions.toml` | `d4ef2c15198a1b7884fb2385db7d6934488f0fb442da85d478363b5264629031` |
| `gradle/verification-metadata.xml` | `7fdf53d8d7a290dfb372ac73a2b1e4928214050fb37730805feabe515dcae235` |
| Sorted fifteen-lockfile manifest | `171b471b6ec61295dd7417c1c2d427181a4db82419aa2b98685f99056db67262` |
| `armeabi-v7a/libah_runtime.so` | `4858636f25fd7304526538743104d1b0c7e01711a17469f2b06648b26dee579f` |
| `arm64-v8a/libah_runtime.so` | `82fe70b09d79b8716487a4f2d35721a2565f4a4d43e3c9942bbc1a5f03d7ff7f` |
| `x86/libah_runtime.so` | `63cf56bb6390dcfe5bed7f6fa875a20c3d7581029c0c7604b91ca3af6ccedf50` |
| `x86_64/libah_runtime.so` | `59d089311ed4c53bfd507e54add9be03c6c804d9c4be8f3fb4c7cbfc38420ccb` |

The lockfile manifest hash is computed over the sorted UTF-8 lines `<file-sha256>  <repository-relative-path>\n` for all fifteen committed `gradle.lockfile` files.

## Supply Chain and Security Boundary

- Dependency sources are limited to Google Maven and Maven Central. Gradle comes from `services.gradle.org`; Temurin comes from the Adoptium release; Android SDK, Build Tools, NDK and CMake come from Android SDK Manager.
- Maven artifact checksums are committed in `gradle/verification-metadata.xml`; resolved versions are committed in fifteen lockfiles; GitHub Actions use full commit SHAs.
- Input immutability: `not_applicable; no APK input is handled by M0-03`.
- Signing boundary: `PASS; product modules accept no keystore, private key, alias or signing password and invoke no signing tool`.
- API/ABI impact: `minSdk 29`, Java/Kotlin target `17`, and four empty native ABI libraries only.
- Independent security review: `not_required; task is not security_sensitive and contains no protection implementation`.
- CI evidence: pending creation and completion of the unique M0-03 pull request.
