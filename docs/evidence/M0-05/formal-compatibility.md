# M0-05 formal compatibility evidence

## Scope and status

- Task: `M0-05`
- Validation mode: `pre-cli`
- Branch: `spike/m0-05-application-factory-provider-jni-poc`
- Issue: `#5`
- Review-remediation implementation commit: `789d37e9fa321b54ee19bf4af1382e589f2942d4`
- KVM validation commit: `587e7f2c7ab9ba44296891fb3d2668e4bd54998c`
- Status at this snapshot: repaired API 29/36 x86_64 GitHub Linux/KVM and repaired API 29 arm64 physical-device acceptance PASS; evidence freeze and second independent read-only security review pending.
- Local emulator use: none. The x86_64 workflow owns its emulator lifecycle, has a 35-minute job limit, a 180-second boot limit, a 900-second acceptance-runner limit, and EXIT/INT/TERM cleanup.
- Security boundary: the signer/config binding is a synthetic-fixture PoC check. It is not the production ConfigV2 authentication planned for M1/M2.

## Local implementation gate

Executed on Windows 10 x64 with project-local Temurin `17.0.19+10`, Gradle `9.5.0`, Node.js `24.12.0`, and the already pinned Android toolchain. No tool or emulator was downloaded to the C drive for this run.

```text
gradle --offline --no-daemon --no-configuration-cache --console=plain :runtime:bootstrap:check :fixtures:android:check :tools:validation:check verifyGovernance
node tools/validation/verify-m0-05-apks.mjs <extracted> <direct> <extracted-test> <direct-test> <extracted-mapping> <extracted-usage> <direct-mapping> <direct-usage> <baseline> <fixture-signer-sha256>
apksigner verify --verbose <seven generated M0-05 APKs>
zipalign -c -P 16 4 <seven generated M0-05 APKs>
```

All commands exited `0`. The Gradle gate reported `BUILD SUCCESSFUL`, the ConfigV2 test reported the golden case plus 20 tamper/no-factory cases, governance reported 26 task cards, 11 core documents and 7 ADRs, the APK verifier reported `PASS`, and all seven signed APKs passed signature and alignment verification. The R8 scan reported that signing execution classes were removed; adding the verifier increased the root DEX by 1,440 bytes in each compatibility variant.

## API 29 arm64 physical device

- Timestamp: `2026-08-02T10:31:01+08:00`
- Result: `PASS`
- Environment: Android API 29; `arm64-v8a,armeabi-v7a,armeabi`; 64-bit process; Xiaomi user/release-keys build; `ro.secure=1`; `ro.debuggable=0`; adb shell uid 2000; non-root.
- Device identifier: omitted. The ignored report stores only a SHA-256 digest of the serial.
- Commands: repaired `run-m0-05-device-acceptance.mjs` with four extracted/direct signed/unsigned negative directories, followed by separate `run-m0-05-startup-negative.mjs` invocations for the extracted and direct packages.
- Validation command exit codes: `0`, `0`, `0`. A surrounding convenience wrapper later returned `1` only because MIUI denied a direct `settings put` while restoring the temporary stay-awake value; `svc power stayon false` then exited `0`, restored `STAY_ON=0`, and cleanup verification found no installed M0-05 package or remote negative directory.
- Raw ignored evidence: `build/m0-05/device-arm64-api29-repaired-20260802/`
- Raw report SHA-256: `e2b154a79f22b900956f4eccdd9c8a450a69a6be340244c031ccf6103aaa94dd`
- Redacted command log SHA-256: `15d700aae1be8f2f9b82839cf1469c0e93dc21f58d47818b613a6cac4d5aa830`
- Device JUnit XML SHA-256: `04a12c0e60857dac8a41468b79780b036d37df3ed2c2047ed06dc92239edd15d`

| Variant | Instrumentation | Lifecycle/factory | Cross-DEX | JNI | Signer/config/metadata | Startup negatives | Plaintext DEX | Cold starts | p50 | p95 | Peak PSS |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| extracted (`extractNativeLibs=true`) | PASS | PASS | PASS | PASS | PASS | 17/17 PASS | 0 | 20/20 | 265 ms | 286 ms | 50,039 KB |
| direct (`extractNativeLibs=false`) | PASS | PASS | PASS | PASS | PASS | 17/17 PASS | 0 | 20/20 | 267 ms | 292 ms | 50,310 KB |

The no-original-factory case also passed: no original-factory events occurred, all six original component counts were zero, and the provisional and final loader identities were equal. Cleanup completed and the runner did not own or start an emulator.

### Frozen arm64 artifacts

| Artifact | SHA-256 |
|---|---|
| extracted Release/R8 APK | `4f4e1d3166d44078b3a721d69b3a943f5a435515151cc5ba074fbdf94529800c` |
| direct Release/R8 APK | `c670a8b8e84c7a479ae558df8be0a06cadfe985c5fe52d8aec7f9f0f5a64e368` |
| extracted instrumentation APK | `47e22fd3bef439dee00091624b227ce73233b63f5f5c2f1169f1b142c98ffeb3` |
| direct instrumentation APK | `7087dae6fa3d8aa819186bb2cd23948908860b3cb525ba8f9a5abf7d51261d21` |
| no-original-factory APK | `9b5120781cb621095024bfee577787e4659e74a514dbaa0f388c6f164ee1fc83` |
| AHDC payload | `bbfd4c5ce0434793d47a4f2e6ff01ec7a40fdeb1e7738ea017133e7a7fadd879` |
| ConfigV2 | `3cb4874df2052d07fa9e5be6410ef040d4e6367177ce4b7b47d909336ea353d2` |
| R8 mapping | `154e41c163b364270cd5ad25a3f76536e291292823e2fff13b1c3719a757d1cb` |
| R8 usage | `b84a8d149f8e8d9dbc1bcd26bfb0bee783bb0ce55388c6d370c4584ef7987cc4` |
| `libfixture_jni.so` arm64-v8a | `a2334bdf16584dc7d5983bb17f1e65bb0d3ac98ea51eac8a25f9a67483155e25` |
| `libfixture_jni.so` x86_64 | `fac69e5f5b9776b97c14e40d83ee54bbd0eb600c098949a09754cfe94198e2d1` |
| static verifier report | `03d535c96ba38e8c2c006691ce08d1d8145f32efc9b270fb7c479b4401f2618a` |
| extracted startup-negative report / JUnit | `c172f84b3861909eff72efdb4b5bb6cb4e684e5d69c051e725736dfb30788b2a` / `b22fa9d2e1b90bc9a9ecacbb3a1f348ab7b32ee87dc748d66cb8fcd980f30f00` |
| direct startup-negative report / JUnit | `54d398ddbde87462c6b7fb6fd26a773713aa1c3271cfc17cb455cb356a2519de` / `f20d9e11fadab0658273b3b4adb54cc5b3a34203fea6c0aa9df08830d7fa75c0` |

Both instrumentation runs reported the exact lifecycle order, six original-factory component counts of `1`, `component_delegate_negative=16`, `native_negative=3`, signer/config/metadata checks, cross-DEX and JNI success. The ignored one-time fixture signer digest is recorded in the raw report/config verification output, but no keystore, certificate, password, private key, device path, or plaintext DEX is committed.

## GitHub Linux/KVM matrix

The committed workflow pins these official archives through `tools/validation/m0-05-linux-kvm-packages.json`:

| Environment | Fixed revision | Status |
|---|---:|---|
| API 29 x86_64 | system image revision 8; Emulator 37.1.11 | PASS |
| API 36 x86_64 | system image revision 2; Emulator 37.1.11 | PASS |

Both repaired jobs built extracted/direct Release/R8 fixtures, executed independent 17-case startup-negative matrices for both variants, ran 20 cold starts per variant, generated JUnit XML, collected memory and no-plaintext-DEX evidence, and forcibly cleaned their AVD/emulator state.

- GitHub Actions run: [#30708544925](https://github.com/xiaokh31/androidAppHardening/actions/runs/30708544925)
- Validated commit: `587e7f2c7ab9ba44296891fb3d2668e4bd54998c`
- API 29 job: `91391784532`, `success`
- API 36 job: `91391784498`, `success`
- Run conclusion: `success`
- Local emulator use: none

| Environment / variant | Instrumentation and functional matrix | Independent startup negatives | Plaintext DEX | Cold starts | p50 | p95 | Peak PSS |
|---|---:|---:|---:|---:|---:|---:|---:|
| API 29 x86_64 extracted | PASS | 17/17 PASS | 0 | 20/20 | 803 ms | 1,043 ms | 61,246 KB |
| API 29 x86_64 direct | PASS | 17/17 PASS | 0 | 20/20 | 812 ms | 981 ms | 104,191 KB |
| API 36 x86_64 extracted | PASS | 17/17 PASS | 0 | 20/20 | 1,381 ms | 1,763 ms | 14,964 KB |
| API 36 x86_64 direct | PASS | 17/17 PASS | 0 | 20/20 | 1,387 ms | 1,568 ms | 14,748 KB |

The API 29 environment reported Android 10/API 29, `x86_64,x86`, a 64-bit process, non-root adb shell, `ro.secure=1`, and a userdebug/test-keys system image. The API 36 environment reported Android 16/API 36, `x86_64`, a 64-bit process, non-root adb shell, `ro.secure=1`, and a userdebug/test-keys system image. Both no-original-factory cases passed, both device runners reported cleanup PASS, and the workflow's EXIT/INT/TERM trap completed.

### Linux/KVM evidence hashes

| Evidence | API 29 SHA-256 | API 36 SHA-256 |
|---|---|---|
| Device report | `a2333cc0539330331a1db287aa4c4279209ee0b01aa07c78cac6633e90428c50` | `da70a5f80d10e8d295b8e1795803adb64803ce0244fb8f967764f43578481983` |
| Redacted command log | `1e31cd18ce1336f8515577137e6891d965260c5eeb10e3e1f213e3332a590068` | `beeb4120c98652b3dddbb3a1033269a02f479bf6b1e00a2830409eac298e37eb` |
| Device JUnit XML | `57a59f6f1d52d1cd2137183280a5f9863ba24224c0a816a0e83a5093ecc393ff` | `68e4189677f58cb48a48f022feeb29bbd0841f7c9ef0c648165b3cf8dcbe59bf` |
| Extracted startup-negative report | `396c8ea3be4b3ed6f82574dbcc67279213260673996b6777ea75a9b060336077` | `67df4119f25d9af2a75a1a812ec985c9cf5f329bd56dc1775066ffc642b7b49d` |
| Direct startup-negative report | `1397ca7033184a593933de1e5026191a60f9d394d972a2e1fab9dd55c3ac0d59` | `761e5bcaa9f6544f1bcba59eb40070f6ef4d5242043f71c03766e103303f9dd0` |
| Static report | `62eaaaa1e312eafd3bb0b6adc116f3c84662a5ce249e1049a9d8b6eb4b0520d3` | `696cf57cfbd4c3ba6ea29d5a9d40b743f64b0f36757c8b4748251910e27e25e2` |

| Generated artifact | API 29 SHA-256 | API 36 SHA-256 |
|---|---|---|
| extracted Release/R8 APK | `04e221d7a6d3117e5cf124f0fdf464493a73b69012cbdce9bdb2122dab6787df` | `0aa8d7a4ed42a50760c06f80cf68655597172b36ee9a710f39ec5645fea5ecde` |
| direct Release/R8 APK | `e9827ae8e9c49356ca79e57d87dd5164b311a3d38fec0ca67597949858bbbb6a` | `f9a77961f3a9bf0126d8ef2ecd22b3846a7264a224b721110a318b75c46a2df8` |
| extracted instrumentation APK | `665e2b3633c8720f887ba1f8de9e9debb81f15a93df77ed19a5a75321b888bb2` | `02d83f9c2c9c886d7c1169a3983091e7376398778f63242fa461970661b6052e` |
| direct instrumentation APK | `d5c7eacee84ce6c7e09b565a96014cc586380c1f15459ec74aa21f104820fa9b` | `03e80b23df3c6e3f667e6477b942e508b5329195cabfd95951a0cfafe574a356` |
| R8 mapping | `154e41c163b364270cd5ad25a3f76536e291292823e2fff13b1c3719a757d1cb` | `154e41c163b364270cd5ad25a3f76536e291292823e2fff13b1c3719a757d1cb` |
| R8 usage | `f2953518d3553090c98971614a5ddedab2c9648ade9305b688fb9951383176f2` | `f2953518d3553090c98971614a5ddedab2c9648ade9305b688fb9951383176f2` |
| `libfixture_jni.so` arm64-v8a | `9e6d57ef9b23c55a897939852463a2a6c26c84da6277e75aca2954ee5ab64c06` | `9e6d57ef9b23c55a897939852463a2a6c26c84da6277e75aca2954ee5ab64c06` |
| `libfixture_jni.so` x86_64 | `9ab8c614757cc94c115e13b93a87afd8a02141e6237f9c3e4bc65321c4b020b6` | `9ab8c614757cc94c115e13b93a87afd8a02141e6237f9c3e4bc65321c4b020b6` |

The generated APKs are ignored, run-scoped integration artifacts signed only with a one-time non-production fixture identity. They are not product outputs and are not committed.

## Completion gate

M0-05 is not complete at this snapshot. All three repaired environments passed, including independent extracted/direct negative matrices, JUnit XML, per-ABI SO hashes, R8 mapping/usage hashes, verifier peak memory, cold-start metrics and cleanup. Completion still requires freezing this evidence commit and a second independent read-only security review with zero open P0/P1/P2 findings. No PR is created before that review passes, and M1/M2 remain blocked.
