# M2-04 local validation

## 2026-08-11 implementation checkpoint

- Branch/base: `feat/m2-04-four-abi-runtime` from `main@9ea71927aea01cd28ba993df71d50b82213dd87d`; Issue #15 is open and no M2-04 PR exists.
- Environment: Windows 10, repository JDK `17.0.19+10`, Gradle `9.5.0`, pinned Android SDK, NDK `29.0.14206865`, Android Clang `21.0.0`. No tool was downloaded and no local emulator was started.
- `gradlew.bat --offline --no-daemon --console=plain :runtime:native:assembleRelease :runtime:native:test :runtime:policy:test :host:cli:test` exited `0` in 38 seconds. The ABI policy matrix passed 26 cases and the existing M2-03 policy regression passed 57 cases.
- `gradlew.bat --offline --no-daemon --console=plain :host:cli:integrationTest` exited `0` in 47 seconds. REPORT_V1 normalized output SHA-256 is `11471db5fc272ee2a3b8001a6773097a9af23bd668d11652d0cde196d2d144e3`; the error, cleanup and path matrices stayed byte-identical.
- The single targeted x86 Release/R8 plus instrumentation build exited `0` in 39 seconds. Both fixture variants contained only `lib/x86/libah_runtime.so` and the synthetic fixture JNI library; no x86_64 fallback was packaged.
- The final four-ABI Release/vector build exited `0` in 38 seconds. It produced all four 104-byte `AHS1` slot files from one source pair.
- `node tools/validation/verify-m2-04-four-abi-runtime.mjs --self-test --report=build/m2-04/native-runtime.json` exited `0`. Report SHA-256 is `8cc20f1af70b5b0e591aefe79c34020b322f34480af509dab76f0db8622abf5a`.

## Release artifact evidence

| Artifact | Bytes | SHA-256 |
|---|---:|---|
| `armeabi-v7a/libah_runtime.so` | 168636 | `59f3fdbc296ca910a476e12b51c77721524e62a918710a2f04b902e127e01eaf` |
| `arm64-v8a/libah_runtime.so` | 264552 | `e793545738d0307789d80c0cb2d0a82c60306e8e8bf1767284418c239ab4264e` |
| `x86/libah_runtime.so` | 261268 | `9e91ac146fbf3f3f19a6687c2a836a01543a719620b24d66da4aff06864ca7d9` |
| `x86_64/libah_runtime.so` | 266256 | `a885677c2cbf0696a544f180a4cbadd46d0c29b7b9e1d78ae60f52b8f4184bae` |
| `native-release.aar` | 435542 | `03ce61952a76eddec170e57d0415c44fe8cf269c32d20b308b4d7ba622153463` |
| Native debug-symbol ZIP | 3603789 | `71e28ce3660a9266a3e2d1ab02cca6d89a8e778e99f8668044d02871ba095ae8` |

Every stripped ELF passed exact machine/ABI ID, one alloc/read-only 104-byte `.ah_share_v1`, RELRO, BIND_NOW, non-executable stack and the exact five-symbol JNI whitelist. Directed negative self-tests rejected machine, slot, writable-section, executable-stack and export mutations. Unstripped hashes and the full symbol archive remain in ignored build evidence.

## ABI policy evidence

`runtime/policy/build/reports/m2-04/abi-compatibility.json` is 309 bytes with SHA-256 `4012e9d6364d72d6b4fe74859fe3b7026073052ab453fc6432c47d72f0ac9b61`. It records all four available Runtime ABIs, an ARM-only input/effective output pair, the explicit limitation `OUTPUT_LIMITED_TO_INPUT_NATIVE_ABIS`, and zero x86/x86_64 risk contribution.

## Physical-device checkpoint

- Read-only preflight confirmed Xiaomi `sirius`/MI 8 SE, API 29, `arm64-v8a,armeabi-v7a,armeabi`, `ro.secure=1`, `ro.debuggable=0`.
- The first script invocation stopped before build/install because the host process exposed JDK 8. The runner was corrected to require the repository-pinned JDK 17; no APK had been installed.
- The corrected bounded run built the shared DEX vector, four slot files, arm64 Release/R8 target/test APKs and both packaging variants successfully. The first ordinary `adb install --no-streaming` then failed once with `INSTALL_FAILED_USER_RESTRICTED: Install canceled by user`.
- The runner immediately stopped, performed package cleanup and did not retry, change MIUI settings, automate confirmation, use root or bypass the platform gate. `pm path` confirms both target and test packages are absent.

M2-04 is therefore an implementation checkpoint, not complete. The only local device blocker is renewed ordinary MIUI USB-install permission; API 29/36 KVM, frozen-SHA independent review, PR publication, README completion and final HandOff remain pending.

## 2026-08-11 review-1 remediation checkpoint

- Independent read-only review of frozen `3cc8dc2cc693c90cf757a39d794c3284ec73f6f6` returned `P0=0`, `P1=2`, `P2=2`; the commit is rejected and the findings are archived in `security-review-1.md`.
- Production ABI collections now use API 29-safe `Collections`/defensive copies. A connected policy smoke path invokes `evaluate()` and every getter on-device. Full four-ABI input now has no limitation; the Host report uses the same strict-subset rule.
- M202 device instrumentation reads extracted ELF headers from `nativeLibraryDir` and direct headers from the selected bounded APK ZIP entry. Search-path checks use the actual loaded ABI and no longer exclude `armeabi-v7a` or `x86`.
- The ELF verifier now requires `.ah_share_v1` to be fully covered by exactly one non-writable `PT_LOAD`; its self-test mutates that covering segment to writable and requires rejection.
- One bounded remediation Gradle invocation compiled both device instrumentation APKs, rebuilt the four-ABI Release, passed the ABI policy matrix (`27` cases) and existing policy matrix (`57` cases). Its only failure was the non-security `ChromeOsAbiSupport` lint false positive caused by the test-only single-ABI property; the module now suppresses that rule with the exact four-ABI Release still enforced by the archive verifier.
- The failed lint task alone was rerun and exited `0` in 36 seconds. The strengthened verifier exited `0`; `native-runtime-review2.json` is 4955 bytes with unchanged SHA-256 `8cc20f1af70b5b0e591aefe79c34020b322f34480af509dab76f0db8622abf5a`.

No device install was retried. The corrected implementation was frozen at `0cfafd4a956f0fefde3c7b5d8278a081f6e05c40`; independent read-only review 2 returned PASS with `P0=0`, `P1=0`, `P2=0` and is archived in `security-review-2.md`. The review closes the code gate only. ARM physical-device installation, API 29/36 KVM, Ubuntu/Windows CI, publication, README completion and post-merge strict HandOff remain pending.

## 2026-08-12 API 29 physical ARM acceptance

- Validation mode: `pre-cli`; project-generated fixture only. The authorized Xiaomi device was API 29, `user/release-keys`, `ro.secure=1`, `ro.debuggable=0`, non-root, and exposed both `arm64-v8a` and `armeabi-v7a`.
- The first invocation after renewed authorization stopped before installation because the previous rejected run's ignored vector directory already existed and the container builder correctly enforced no-replace publication. The runner now gives source DEX and Runtime/vector output a unique per-run ID; this test-only orchestration change does not affect production Runtime code.
- `tools/validation/run-m2-04-arm-device.ps1` was then executed once with a unique ignored evidence root. It exited `0` in 238 seconds. Both ABIs ran extracted/direct Release/R8 fixtures with instrumentation, ten failure-injection windows, cross-DEX, JNI, authenticated metadata, one bounded cold start, memory measurement, zero plaintext DEX files and successful package cleanup.
- Matrix: 1089 bytes, SHA-256 `701839820fac2d54690c923c77b8b4323773f9c880be0f55e27a46f34154ab06`.
- `arm64-v8a` report: 3298 bytes, SHA-256 `06107478f01933ba2262667ab1557d1ec129de5854f28d3d617cbef42def5635`; command transcript: 20290 bytes, SHA-256 `ca056a109880dabca89edc143d56a1475446da6ed7df3d8bda744352afbfbd40`.
- `armeabi-v7a` report: 3312 bytes, SHA-256 `ad8bbd5978740411b671c403db10f8925d3d5c1f59cb70bf9db3c60d0850b506`; command transcript: 20306 bytes, SHA-256 `6ffc9f70962500af8ab1f7512cf0411139332c5d73de0a9a2383a22009b3fadd`.
- Independent `pm path` checks after the runner returned no target packages. No emulator was started, no root or secure-setting change was used, and no successful device case was repeated.

The local implementation, independent code review and physical ARM gates are closed. M2-04 now awaits its single authorized draft-PR Ubuntu/Windows Build and API 29/36 Linux/KVM round.
