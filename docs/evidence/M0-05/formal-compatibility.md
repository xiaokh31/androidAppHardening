# M0-05 formal compatibility evidence

## Scope and status

- Task: `M0-05`
- Validation mode: `pre-cli`
- Branch: `spike/m0-05-application-factory-provider-jni-poc`
- Issue: `#5`
- Review-remediation implementation commit: `789d37e9fa321b54ee19bf4af1382e589f2942d4`
- KVM validation commit: `587e7f2c7ab9ba44296891fb3d2668e4bd54998c`
- Status at this snapshot: repaired API 29/36 x86_64 GitHub Linux/KVM acceptance PASS; repaired API 29 arm64 physical-device acceptance pending manual device unlock; second independent read-only security review pending.
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

> Historical evidence only: the first independent review rejected this run as final acceptance because the direct variant did not execute its own 17-case mutation matrix and the evidence classes were incomplete. The repaired commit must be rerun after the physical device is manually unlocked.

- Timestamp: `2026-08-01T22:44:17+08:00`
- Result: `PASS`
- Environment: Android API 29; `arm64-v8a,armeabi-v7a,armeabi`; 64-bit process; Xiaomi user/release-keys build; `ro.secure=1`; `ro.debuggable=0`; adb shell uid 2000; non-root.
- Device identifier: omitted. The ignored report stores only a SHA-256 digest of the serial.
- Command: `node tools/validation/run-m0-05-device-acceptance.mjs --adb <project-local-adb> --serial <redacted> --platform arm64-api29-physical --cold-starts 20 --command-timeout-ms 60000 --no-factory-apk <ignored-signed-apk> --extracted-negative-signed-dir <ignored-dir> --extracted-negative-unsigned-dir <ignored-dir> --direct-negative-signed-dir <ignored-dir> --direct-negative-unsigned-dir <ignored-dir> --evidence build/m0-05/device-arm64-api29-physical`
- Exit code: `0`
- Raw ignored report: `build/m0-05/device-arm64-api29-physical/report.json`
- Raw report SHA-256: `833ae034e7c99389a398bce2acdd24b17bb300f98374292c7da5988c9496731f`
- Redacted command log SHA-256: `2c0ab50114aefc8ebe16f9eab6c5f81c530a22ae547ded5db41796d06d08166d`

| Variant | Instrumentation | Lifecycle/factory | Cross-DEX | JNI | Signer/config/metadata | Startup negatives | Plaintext DEX | Cold starts | p50 | p95 | Peak PSS |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| extracted (`extractNativeLibs=true`) | PASS | PASS | PASS | PASS | PASS | 17/17 PASS | 0 | 20/20 | 277 ms | 437 ms | 49,289 KB |
| direct (`extractNativeLibs=false`) | PASS | PASS | PASS | PASS | PASS | covered by the same authenticated matrix | 0 | 20/20 | 271 ms | 292 ms | 52,156 KB |

The no-original-factory case also passed: no original-factory events occurred, all six original component counts were zero, and the provisional and final loader identities were equal. Cleanup completed and the runner did not own or start an emulator.

### Frozen arm64 artifacts

| Artifact | SHA-256 |
|---|---|
| extracted Release/R8 APK | `ce978151ee329daab7bbc7db86f01e287c63f8652b53cb9815cc45a9fe6a87a0` |
| direct Release/R8 APK | `cb5ff8cbb524fff159d4926e2231f87f90064e0b60918000c083bd83058f6c44` |
| extracted instrumentation APK | `1a1787f4062a347026667ea474f2f8566cb661746eca501a1156d23d5fd56197` |
| direct instrumentation APK | `4d0839e6d051dffe4ebdaecafbebc95336080304c586e4edeeeb8563c320f345` |
| no-original-factory APK | `0aaaa0193327d182483b3d9bf46cb983f01561c3dff6b8b85c4e10f63f9a095b` |
| AHDC payload | `3e644b308186f92bc90f59ce6bab8e3c33845b97a840f5e14f73fae534d6a7ec` |
| payload DEX 1 | `ed56309f63de448b8c7ebf45ae53a98a1937616658afc56aaa6a25f009681426` |
| payload DEX 2 | `06200c4901642a01d3d7e2ab5a3e23e9b863f015d528731eb29823b7c858f819` |
| ConfigV2 | `929c4b316ec1c1ef53bc2ccc3a112c77d7c2bb292f30e7ed9dad494c26f0d455` |

The ignored one-time fixture signer digest is recorded in the raw report/config verification output, but no keystore, certificate, password, private key, device path, or plaintext DEX is committed.

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

M0-05 is not complete at this snapshot. The repaired API 29/36 x86_64 environments passed, including exact `libpulse0=1:16.1+dfsg1-2ubuntu10.1`, JUnit XML, per-ABI SO hashes, R8 mapping/usage hashes, and verifier peak memory of 67,488/73,268 KB. Completion still requires the repaired API 29 arm64 physical run and a second independent read-only security review with zero open P0/P1/P2 findings. No PR is created before that review passes, and M1/M2 remain blocked.
