# M0-05 API 29 arm64 metadata blocker

## Status

- Result: `BLOCKED`
- Validation mode: `pre-cli`
- Implementation commit: `d58a277681443a5e79b770a3e9162ae54006138d`
- Evidence-preparation base: `45f29740cd2abfb8054ae9d3a6af2ff2f89f9cf1`
- Timestamp: `2026-08-01T13:17:48+08:00`
- Environment ID: `arm64-api29-physical-01`

The authorized project fixture reached a real API 29 arm64 non-root device, but the Framework `ApplicationInfo` passed to `AppComponentFactory.instantiateClassLoader` had a null `metaData` Bundle. The bootstrap failed closed with `AAH-P009` before `LOADER_CREATED`. This is the explicit M0-05 blocker condition; no `Context`, `PackageManager`, `ActivityThread`, `LoadedApk`, reflection, or hidden-API fallback was added.

## Environment qualification

- Android API: `29`
- ABI list: `arm64-v8a, armeabi-v7a, armeabi`
- Build class: Android 10 user/release-keys
- adb identity: `uid=2000(shell)`
- `ro.debuggable=0`
- `ro.secure=1`
- Root conclusion: non-root production build

The device serial is intentionally omitted from committed evidence.

## Build evidence

- Command: `gradlew.bat --offline --no-daemon --no-configuration-cache :fixtures:android:assembleCompatExtractedDebugAndroidTest :fixtures:android:assembleCompatDirectDebugAndroidTest :fixtures:android:assembleCompatExtractedRelease :fixtures:android:assembleCompatDirectRelease`
- Exit code: `0`
- Duration: `45.8 seconds`
- Environment: Windows 10 `10.0.19045` x64; Temurin/OpenJDK `17.0.19+10`; Gradle `9.5.0`; Android build-tools `36.1.0`; NDK `29.0.14206865`; CMake `4.1.2`

| Artifact | Bytes | SHA-256 |
|---|---:|---|
| extracted Release/R8 fixture | 171322 | `b56df9d26dc9c4bea786307f2706cbbce13f54055bcc45fd7132be91f39a53d4` |
| direct Release/R8 fixture | 226598 | `4d5f840fc62b7ef2deda9472a78583691ee63b2d198f2e1085f449e17086a3f8` |
| extracted instrumentation fixture | 121380 | `2768156881b6061a7b7221c58cbba1d3fb1a6615819c2ce6e9c7d79017f7f827` |
| direct instrumentation fixture | 121380 | `e8463939a79a3762511f5ac378c9ca8a331faeba548ceaaa41b9d99717fd9c47` |

Release APK hashes differ from the earlier snapshot because the fixture-only signing step is not reproducible; byte sizes and the unsigned payload content remain covered by the static validator. These signed APKs are ignored test artifacts and are not product outputs.

## Manifest cross-check

- Command: `aapt2 dump xmltree <extracted-release.apk> --file AndroidManifest.xml`
- Exit code: `0`
- Result: the packaged binary Manifest contains `ah.runtime.bootstrap.ShellAppComponentFactory` and all seven typed `ah.runtime.*` metadata entries with the required string, boolean, and integer forms.

This proves the metadata was packaged. It does not override the real callback observation.

## On-device failure

1. The extracted Release/R8 target APK installed successfully.
2. Its instrumentation APK installed successfully.
3. The target process entered `ShellAppComponentFactory.instantiateClassLoader`.
4. `EARLY_SIGNER_VERIFIED` was recorded, so `ApplicationInfo.sourceDir` and the pinned `apksig` verifier were usable.
5. Metadata validation then failed with `AAH-P009: Framework metadata Bundle is absent`.
6. Android reported `Process crashed` and `INSTRUMENTATION_CODE: 0`; expected `OK (1 test)` / `INSTRUMENTATION_CODE: -1` was absent.
7. `LOADER_CREATED` and all business lifecycle/JNI events were not reached.

The direct variant was not run because the failure is before native library path selection and the task card requires an immediate blocked handoff for this exact condition.

## Bounded normal-launch cross-check

- The target-only APK installed successfully.
- A 60-second bounded `am start -W` cross-check did not return successfully and was terminated by the command timeout.
- The package was then uninstalled; a final package query found no `ah.fixtures.android.m005.*` package.
- No emulator or qemu process was started.

## Gate conclusion

M0-05 Acceptance Criterion 12 and the task's `Dependencies and Blockers` rule fail on `arm64-api29-physical-01`. ADR-0003 requires the real callback to expose the typed metadata Bundle and explicitly rejects a startup fallback through `PackageManager` or hidden Framework state.

`M0-05 = BLOCKED`. The smallest next action is an architecture decision: either terminate this v0.1 startup design, narrow the compatibility claim through an approved ADR/task revision, or define a different public, authenticated early-configuration channel before implementation resumes. M1/M2 remain blocked. The independent security review must not start against this failing compatibility gate.

GitHub x86_64 KVM validation was not started. GitHub Actions cannot execute the local-only M0-05 commit without first pushing the fixed branch, while the approved sequence prohibited push until after device acceptance and independent review. The arm64 architecture blocker now supersedes that sequencing issue.
