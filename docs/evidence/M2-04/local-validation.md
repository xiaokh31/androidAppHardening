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
