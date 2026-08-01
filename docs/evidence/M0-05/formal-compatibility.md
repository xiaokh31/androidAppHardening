# M0-05 formal compatibility evidence

## Scope and status

- Task: `M0-05`
- Validation mode: `pre-cli`
- Branch: `spike/m0-05-application-factory-provider-jni-poc`
- Issue: `#5`
- Frozen implementation commit: `0d8e6f8c13ac871c840fe134d83d1bfc0b69d3a9`
- Status at this snapshot: API 29 arm64 physical-device acceptance PASS; API 29/36 x86_64 GitHub Linux/KVM acceptance pending the authorized verification-only branch push.
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
| API 29 x86_64 | system image revision 8; Emulator 37.1.11 | Pending authorized verification-only push |
| API 36 x86_64 | system image revision 2; Emulator 37.1.11 | Pending authorized verification-only push |

Both jobs build extracted/direct Release/R8 fixtures, execute the same functional and negative gates, run 20 cold starts per variant, collect memory and no-plaintext-DEX evidence, and forcibly clean their AVD/emulator state. The resulting run URL, artifact hashes and job conclusions will be added to this file before independent review.

## Completion gate

M0-05 is not complete at this snapshot. Completion still requires both KVM jobs to pass on the frozen validation commit, evidence artifacts to be reconciled here, and an independent read-only `m0_05_security_review` to report zero open P0/P1/P2 findings. No PR is created before that review passes, and M1/M2 remain blocked.
