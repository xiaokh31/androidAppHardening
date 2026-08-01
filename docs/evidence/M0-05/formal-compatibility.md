# M0-05 formal compatibility evidence

## Scope and status

- Task: `M0-05`
- Validation mode: `pre-cli`
- Branch: `spike/m0-05-application-factory-provider-jni-poc`
- Issue: `#5`
- Frozen implementation commit: `0d8e6f8c13ac871c840fe134d83d1bfc0b69d3a9`
- Frozen validation/workflow commit: `f63a7192eb6e1055a7647d27850ece262c59210a`
- Status at this snapshot: API 29 arm64 physical-device acceptance and API 29/36 x86_64 GitHub Linux/KVM acceptance PASS; independent read-only security review pending.
- Local emulator use: none. The x86_64 workflow owns its emulator lifecycle, has a 35-minute job limit, a 180-second boot limit, a 900-second acceptance-runner limit, and EXIT/INT/TERM cleanup.
- Security boundary: the signer/config binding is a synthetic-fixture PoC check. It is not the production ConfigV2 authentication planned for M1/M2.

## Local implementation gate

Executed on Windows 10 x64 with project-local Temurin `17.0.19+10`, Gradle `9.5.0`, Node.js `24.12.0`, and the already pinned Android toolchain. No tool or emulator was downloaded to the C drive for this run.

```text
gradle --offline --no-daemon --no-configuration-cache --console=plain :runtime:bootstrap:check :fixtures:android:check :tools:validation:check verifyGovernance
node tools/validation/verify-m0-05-apks.mjs <extracted> <direct> <extracted-test> <direct-test> <extracted-mapping> <direct-mapping> <baseline> <fixture-signer-sha256>
apksigner verify --verbose <seven generated M0-05 APKs>
zipalign -c -P 16 4 <seven generated M0-05 APKs>
```

All commands exited `0`. The Gradle gate reported `BUILD SUCCESSFUL`, the ConfigV2 test reported the golden case plus 20 tamper/no-factory cases, governance reported 26 task cards, 11 core documents and 7 ADRs, the APK verifier reported `PASS`, and all seven signed APKs passed signature and alignment verification. The R8 scan reported that signing execution classes were removed; adding the verifier increased the root DEX by 1,440 bytes in each compatibility variant.

## API 29 arm64 physical device

- Timestamp: `2026-08-01T22:44:17+08:00`
- Result: `PASS`
- Environment: Android API 29; `arm64-v8a,armeabi-v7a,armeabi`; 64-bit process; Xiaomi user/release-keys build; `ro.secure=1`; `ro.debuggable=0`; adb shell uid 2000; non-root.
- Device identifier: omitted. The ignored report stores only a SHA-256 digest of the serial.
- Command: `node tools/validation/run-m0-05-device-acceptance.mjs --adb <project-local-adb> --serial <redacted> --platform arm64-api29-physical --cold-starts 20 --command-timeout-ms 60000 --no-factory-apk <ignored-signed-apk> --negative-signed-dir <ignored-dir> --negative-unsigned-dir <ignored-dir> --evidence build/m0-05/device-arm64-api29-physical`
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

Both jobs built extracted/direct Release/R8 fixtures, executed the same functional and negative gates, ran 20 cold starts per variant, collected memory and no-plaintext-DEX evidence, and forcibly cleaned their AVD/emulator state.

- GitHub Actions run: [#30706455270](https://github.com/xiaokh31/androidAppHardening/actions/runs/30706455270)
- Validated commit: `f63a7192eb6e1055a7647d27850ece262c59210a`
- API 29 job: `91386314437`, `success`
- API 36 job: `91386314472`, `success`
- Run conclusion: `success`
- Local emulator use: none

| Environment / variant | Instrumentation and functional matrix | Independent startup negatives | Plaintext DEX | Cold starts | p50 | p95 | Peak PSS |
|---|---:|---:|---:|---:|---:|---:|---:|
| API 29 x86_64 extracted | PASS | 17/17 PASS | 0 | 20/20 | 810 ms | 1,017 ms | 60,030 KB |
| API 29 x86_64 direct | PASS | covered by the same 17-case matrix | 0 | 20/20 | 796 ms | 1,012 ms | 69,401 KB |
| API 36 x86_64 extracted | PASS | 17/17 PASS | 0 | 20/20 | 1,353 ms | 1,840 ms | 14,633 KB |
| API 36 x86_64 direct | PASS | covered by the same 17-case matrix | 0 | 20/20 | 1,215 ms | 1,593 ms | 14,703 KB |

The API 29 environment reported Android 10/API 29, `x86_64,x86`, a 64-bit process, non-root adb shell, `ro.secure=1`, and a userdebug/test-keys system image. The API 36 environment reported Android 16/API 36, `x86_64`, a 64-bit process, non-root adb shell, `ro.secure=1`, and a userdebug/test-keys system image. Both no-original-factory cases passed, both device runners reported cleanup PASS, and the workflow's EXIT/INT/TERM trap completed.

### Linux/KVM evidence hashes

| Evidence | API 29 SHA-256 | API 36 SHA-256 |
|---|---|---|
| Device report | `ceb1a572b149260bbb7c7b3fac808f73bf3f6ffb96dc2448262c41e6dd6f4519` | `ce5ffc1815a671b21a8e11fe978cd84eb821a2edfa17813fe7fd1f01e3b65a6f` |
| Redacted command log | `b1a1c437bda799e36cf56adf79c4a7b47abaad53bcdbe46767ba22b72f2d50b0` | `a43154b988d5c6c29ad0049ab26694934197ff103a871ef48e49c9051949a582` |
| Startup-negative report | `2db4b2b41c7858e3861c27c02ccbe06576664bb49733a1e21bb0b3da551c07c5` | `e71cf28d94e72d39c0f4a1d705c6040d5291cb0f8d42e70cb44289c5f915e9d3` |
| Startup-negative command log | `803a7139e3d5977d7f01cca1df8da71ad9de8429e0ade499bec913390ba546e1` | `4c926441414ddb0d78fe219c47f8ffe1ed6f9f0fcaf29c8134feb0eb38a7818c` |
| Static report | `cabc9f1e8fa6c4f4f76d4f4b5c3d3af815147eb561caf3dfd73934bcc50ffa60` | `d22ed75861debca69bf75c844ce8c6e802880e3ffa0a2fd208b154ad630718c9` |

| Generated artifact | API 29 SHA-256 | API 36 SHA-256 |
|---|---|---|
| extracted Release/R8 APK | `d6ddb1e424e055ceb8cfcc84667f2c33661dc2e8cc076d547a0d3c388fa8af29` | `89c880b6291aef5fd0c5411e723fd84220a82daa7671af8fde57ad77fb213a02` |
| direct Release/R8 APK | `6b0ccf2070e85a199b4c50bddea05e9b0cc098e1dcfc820789b9848fcf2c2b3d` | `f74d98addd73d91b1c7d7d9ae639c694eab9850bef7686341da45017d2ee5db0` |
| extracted instrumentation APK | `7ad7e5d213eec21bd5e1189e982ef0fc69db47ac57eec4840c40a1e8c06a09d9` | `c6b32e1215ec077a0197f97e620766805b17e07aa6f6fe19986c1dfa2377b086` |
| direct instrumentation APK | `55b8c37caaae406d9c86bde8de4033909dddad814ebd7229edf93eee62d8360d` | `65d9128b43134971c8a7fac9d10b0f5ba1dbcb5f704c05a28fa1060e115761f1` |
| no-original-factory APK | `b9cb97e2f38223e28563829d0c9b37fb87bd5da9b7c58eec92619b985ea7c883` | `1ef0acfd57273be3ae0bdab480727652ceeaae50ea3260b2ba952c21d929d142` |

The generated APKs are ignored, run-scoped integration artifacts signed only with a one-time non-production fixture identity. They are not product outputs and are not committed.

## Completion gate

M0-05 is not complete at this snapshot. The three required device environments have passed and the frozen evidence is reconciled here. Completion still requires an independent read-only `m0_05_security_review` to report zero open P0/P1/P2 findings. No PR is created before that review passes, and M1/M2 remain blocked.
