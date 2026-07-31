# M0-05 implementation snapshot

## Status

- Result: `IN_PROGRESS_NOT_ACCEPTED`
- Implementation commit: `d58a277681443a5e79b770a3e9162ae54006138d`
- Validation mode: `pre-cli`
- Timestamp: `2026-08-01T01:19:15+08:00`
- Issue: [#5](https://github.com/xiaokh31/androidAppHardening/issues/5)
- Branch: `spike/m0-05-application-factory-provider-jni-poc`

The static implementation slice is complete enough for device testing, but M0-05 is not accepted or done. Neither required x86_64 emulator completed boot within the user-approved bounded window, and no API 29+ arm64 non-root environment is available. No on-device compatibility claim is made from build-only evidence.

## Implemented slice

- Pinned Android `apksig` 9.3.0 early verification using only Framework `ApplicationInfo.sourceDir`, minimum checked platform 29, exactly one current signer, and DER certificate SHA-256.
- Seven typed `ah.runtime.*` metadata keys validated before opening the payload.
- Unique STORED `assets/ah/runtime/payload.ahdc` entry with no encryption or data descriptor and two bounded in-memory DEX files.
- API 29 three-argument `InMemoryDexClassLoader` with a public-API native search path derived from `ApplicationInfo`, process bitness, supported ABI arrays, and a bounded APK ZIP listing.
- Original `AppComponentFactory` construction and five component delegates, no-factory platform fallback, `AAH-P002` factory failure, and `AAH-P003` delegated failure with the original cause retained.
- Combined custom Application, eager Provider, Activity, Service, Receiver, second-DEX-only API, and fixed JNI marker fixture.
- `extractNativeLibs=true` and `false` Release/R8 variants for `arm64-v8a` and `x86_64`.
- Bounded PowerShell device runner with hidden emulator launch, per-command timeout, `finally` uninstall/kill, owned-process PID cleanup, and post-cleanup adb/process checks.

## Passing verification

### Build, R8, lint, and check

- Command: `gradlew.bat --offline --no-daemon --no-configuration-cache :fixtures:android:assembleCompatExtractedDebugAndroidTest :fixtures:android:assembleCompatDirectDebugAndroidTest :fixtures:android:assembleCompatExtractedRelease :fixtures:android:assembleCompatDirectRelease`
- Exit code: `0`
- Command: `gradlew.bat --offline --no-daemon --no-configuration-cache :runtime:bootstrap:check :runtime:bootstrap:lint :fixtures:android:check :fixtures:android:lint`
- Exit code: `0`
- Environment: Windows 10 `10.0.19045` x64; Temurin/OpenJDK `17.0.19+10`; Gradle `9.5.0`; Android build-tools `36.1.0`; NDK `29.0.14206865`; CMake `4.1.2`; Node.js `24.12.0`.

### APK and source-policy validator

- Command: `node tools/validation/verify-m0-05-apks.mjs <extracted-release.apk> <direct-release.apk> <extracted-test.apk> <direct-test.apk> <extracted-mapping.txt> <direct-mapping.txt>`
- Exit code: `0`
- Result: one STORED payload per APK; two bounded DEX files; `SecondaryApi` defined only by DEX 2; no payload implementation in root DEX; exact native ABI set; extracted/direct SO packaging modes match; no forbidden callback dependencies or project references to `com.android.apksig.internal.*`; signer verification precedes metadata validation and payload read.
- R8 result: `ApkVerifier` path retained; `ApkSigner` and `ApkSignerEngine` signing execution paths removed.

| Artifact | Bytes | SHA-256 |
|---|---:|---|
| extracted Release APK | 171322 | `fd8ddb1ff8a6d4b594d3f31e141293ac76f8e3c5a5e29a9ea08e1e434244a588` |
| direct Release APK | 226598 | `493c5b6d3621484a7d9433230aca7e5e026a276b829e36221699e9869063abb5` |
| extracted instrumentation APK | 121380 | `2768156881b6061a7b7221c58cbba1d3fb1a6615819c2ce6e9c7d79017f7f827` |
| direct instrumentation APK | 121380 | `e8463939a79a3762511f5ac378c9ca8a331faeba548ceaaa41b9d99717fd9c47` |
| AHDC payload | 5856 | `427e3399d57d43478c28f7970c39f63c5200ac27c73cfe3e72586b553420e757` |
| primary payload DEX | 4912 | `a5174bd174d12a21efbbd8582864b019fca0f6df893de6881ebc18e4ab22bdde` |
| secondary payload DEX | 928 | `06200c4901642a01d3d7e2ab5a3e23e9b863f015d528731eb29823b7c858f819` |

### Standard Android tooling

- `apksigner verify --verbose` exited `0` for both Release APKs; v2 verified and signer count was `1`.
- `zipalign -c -P 16 4` exited `0` for both Release APKs.
- `node tools/governance/validate-project-package.mjs`, strict HandOff with the development-only pending-clean allowance, and `git diff --check` exited `0` before the implementation commit.

## Bounded device attempts

No device test passed because the emulator never reached `sys.boot_completed=1`; application install or instrumentation was never attempted.

| Environment | Mode | Bound | Result |
|---|---|---:|---|
| API 29 rev8 x86_64 | cold boot, no snapshot | 75 seconds | timeout, automatic cleanup PASS |
| API 29 rev8 x86_64 | read existing `default_boot`, no snapshot save | 45 seconds | timeout, automatic cleanup PASS; no further API 29 retries |
| API 36 rev2 x86_64 | read existing `default_boot`, no snapshot save | 45 seconds | timeout, automatic cleanup PASS |

After every attempt, project adb listed only the pre-existing `20a24423 unauthorized` physical device and no `emulator`, `qemu-system-x86_64`, or `qemu-system-aarch64` process remained. The runner was amended to retain emulator stdout/stderr on future runs; no additional emulator run was made after that change.

## Remaining acceptance work

- Obtain a reliably booted API 29 rev8 x86_64 session and run both Release/R8 variants.
- Obtain a reliably booted API 36 rev2 x86_64 session and run both Release/R8 variants.
- Provide at least one API 29+ arm64 non-root environment and run both variants.
- Complete the multi-current-signer, re-signer, damaged signing block, payload ZIP mutation, JNI ABI/path mutation, 20-run cold-start, peak-memory, and plaintext-payload scans on real devices.
- Record post-start `SigningInfo` equality, typed metadata, lifecycle events/counts, JNI results, fingerprints, root status, p50/p95 timing, and peak memory from those device runs.
- Freeze a device-validated commit and obtain the pre-designated independent read-only security review before opening the sole PR.

## Gate conclusion

`M0-05 = IN_PROGRESS`. Static feasibility is positive, but the compatibility gate remains unproven. M1/M2 production work must remain blocked until all required device environments and the independent review pass.
