# M0-04 local Windows API 34 smoke evidence

## Gate conclusion

- task_id: `M0-04`
- validation_mode: `pre-cli`
- formal_gate: `BLOCKED`
- smoke_result: `PASS`
- timestamp: `2026-07-31T12:18:09+08:00`
- source_branch: `spike/m0-04-classloader-poc`
- base_commit: `978d357a8f0203ee90ebcfff6ede64c09bf6135e`
- implementation_commit: `1fc855c71bbd16141b419b928ffdad4f998ad3d6`
- issue: [#4](https://github.com/xiaokh31/androidAppHardening/issues/4)

The repository implementation, static APK contract, instrumentation test and
20-run cold-start smoke test pass on the locally available API 34 x86_64 AVD.
This is non-acceptance evidence only. M0-04 remains blocked because the required
API 29 and API 36 x86_64 emulator images are not installed and the repository
does not pin an allowed system-image package revision, checksum and source.
No unpinned system image was downloaded.

## Environment

- host: `Windows 10 10.0 amd64`
- Java: `Eclipse Temurin 17.0.19+10`
- Gradle: `9.5.0`
- Node.js: `24.12.0`
- adb: `C:\Environment\Android\SDK\platform-tools\adb.exe`
- AVD: `m0_04_api34`
- API: `34`
- ABI: `x86_64`
- fingerprint:
  `google/sdk_gphone64_x86_64/emu64xa:14/UE1A.230829.036.A2/11596452:userdebug/dev-keys`
- test shell identity:
  `uid=2000(shell) gid=2000(shell) context=u:r:shell:s0`
- acceptance images present: API 29 `no`; API 36 `no`

The temporary API 34 AVD and all generated APK, DEX and test-result files live
under ignored `build/` directories and are not repository artifacts.

## Build and repository checks

Command:

```powershell
$env:JAVA_HOME=(Resolve-Path '.toolchains\jdk\jdk-17.0.19+10').Path
.\gradlew.bat --no-daemon check lint verifyGovernance :runtime:native:assemble :fixtures:android:assembleClassloaderPocDebugAndroidTest
```

- exit_code: `0`
- result: `PASS; 270 tasks completed and the configuration cache was stored`

Command:

```powershell
$env:JAVA_HOME=(Resolve-Path '.toolchains\jdk\jdk-17.0.19+10').Path
.\gradlew.bat --no-daemon :fixtures:android:assembleClassloaderPocDebug
```

- exit_code: `0`
- result: `PASS; payload DEX and target APK rebuilt with the pinned JDK`

Command:

```powershell
node tools\validation\verify-m0-04-apk.mjs fixtures\android\build\outputs\apk\classloaderPoc\debug\android-classloaderPoc-debug.apk fixtures\android\build\generated\m0-04\classloaderPocDebug\assets\ah\poc\classes.dex fixtures\android\build\outputs\apk\androidTest\classloaderPoc\debug\android-classloaderPoc-debug-androidTest.apk
```

- exit_code: `0`
- result: `PASS`
- APK contract: exactly `assets/ah/poc/classes.dex`; ZIP method `STORED`;
  encrypted `false`; data descriptor `false`
- source policy: no forbidden `Context`, `AssetManager`, `DexClassLoader`,
  hidden API, `pathList`, or framework-internal reflection use

## Instrumentation smoke

Command:

```powershell
$env:JAVA_HOME=(Resolve-Path '.toolchains\jdk\jdk-17.0.19+10').Path
$env:ANDROID_SERIAL='emulator-5554'
.\gradlew.bat --no-daemon :fixtures:android:connectedClassloaderPocDebugAndroidTest
```

- exit_code: `0`
- tests: `1`
- failures: `0`
- errors: `0`
- observed sequence:
  `FACTORY_ENTER<LOADER_CREATED<APPLICATION_CREATED<ACTIVITY_CREATED`
- returned loader: `dalvik.system.InMemoryDexClassLoader`
- loader identity: `194768020`
- payload-only marker: `M0-04-IN-MEMORY`
- negative payload cases: `3`
- forbidden plaintext files: `0`
- result: `PASS on API 34 smoke device only`

The negative cases cover missing payload, corrupt payload and empty buffer. Each
path reports stable failure code `AAH-P001` and does not fall back to the parent
loader.

## Final artifact hashes

| Artifact | SHA-256 |
|---|---|
| `android-classloaderPoc-debug.apk` | `4c0dc83351511de4728baa27c36858f5cd98f2faa7d0fb389fd3857d278bba7c` |
| generated `assets/ah/poc/classes.dex` | `77fdfdb6e35a0f09747c09c28b245a289cdc5126af0c7e2a719581548318cda1` |
| `android-classloaderPoc-debug-androidTest.apk` | `069ce8488eb842361821488085d376da58517f13f683a827f17bd6431e5a846d` |
| instrumentation XML | `3e20103db4a3aa76cca314a58cc221cab0f6372212c12c22d2dced4be5f7146d` |
| instrumentation `test-results.log` | `334409e148064146fd99249bef5325d00b1e5550959a16aa61e06d36022d5088` |

## Final 20-run cold-start smoke

Commands:

```powershell
& 'C:\Environment\Android\SDK\platform-tools\adb.exe' -s emulator-5554 install -r fixtures\android\build\outputs\apk\classloaderPoc\debug\android-classloaderPoc-debug.apk
node tools\validation\run-m0-04-cold-start.mjs --adb C:\Environment\Android\SDK\platform-tools\adb.exe --serial emulator-5554 --iterations 20
```

- install exit_code: `0`
- cold-start exit_code: `0`
- successful starts: `20/20`
- total-time range: `381-620 ms`
- forbidden process-log findings: `0`
- forbidden private/external `.dex`, `.jar`, `.odex` or payload-hash files: `0`
- result: `PASS on API 34 smoke device only`

## Blocker and exact unblock condition

The formal task gate requires the same connected instrumentation test and 20
cold starts on unrooted API 29 and API 36 x86_64 emulators. Neither image exists
in the local SDK. Downloading an arbitrary latest image would violate the
repository rule against unpinned tools and downloads.

The project coordinator must first pin the allowed
`system-images;android-29;...;x86_64` and
`system-images;android-36;...;x86_64` package revisions, checksums and download
source, then provision both images. The blocker is cleared only when each image
independently records:

1. API, ABI, fingerprint and non-root state;
2. connected instrumentation exit `0`;
3. the required event order and loader identity assertions;
4. all three `AAH-P001` negative paths;
5. 20/20 cold starts with zero forbidden logs and zero forbidden files; and
6. immutable APK, payload DEX and test-report SHA-256 values.

M0-05 must not start before those two acceptance runs pass and M0-04 receives an
explicit `PASS` gate decision.
