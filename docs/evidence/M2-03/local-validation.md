# M2-03 local validation

- Base commit: `dec1ef68f69eea26ae1bc6a1132bf26bf39ba0f8`
- Branch: `feat/m2-03-runtime-integrity`
- Environment: Windows 10 x86_64, Temurin JDK `17.0.19+10`, Gradle `9.5.0`, Android SDK with NDK `28.2.13676358`
- Validation mode: `pre-cli`
- Timestamp: `2026-08-10T14:02:45+08:00`

## Passing gates

1. `node tools/governance/validate-project-package.mjs` — exit `0`; 28 task cards, 11 core docs and 9 ADRs accepted.
2. `node .agents/skills/coordinate-project-handoff/scripts/validate-handoff.mjs HandOff.md --strict` — exit `0`.
3. `gradle --no-daemon --offline verifyGovernance verifyM203RuntimeIntegrity :runtime:policy:test :runtime:policy:lint` — exit `0`; the non-empty JVM policy matrix passed 29 cases and the architecture/capability scan passed all 13 checks.
4. `gradle --no-daemon --offline check lint verifyGovernance :runtime:native:assembleRelease` — all implementation, unit, lint, architecture and native build tasks passed; the aggregate invocation exited `1` only because the task card temporarily used the governance-invalid development status `in_progress`. The field was restored to `planned`, and gates 1–3 then passed without exemption.
5. `gradle --no-daemon --offline :fixtures:android:assembleM203ExtractedRelease :fixtures:android:assembleM203DirectRelease :fixtures:android:assembleM203ExtractedDebugAndroidTest :fixtures:android:assembleM203DirectDebugAndroidTest` — exit `0`; both extracted/direct Release/R8 targets and their non-empty device runners compiled.
6. `git diff --check` — exit `0`.

The production source scan SHA-256 is `6300a2d89493287451c9c14e7f12c33343fc14c99b6c74e27392d24df3d2b9da`. It confirms pinned `apksig 9.3.0`, a single production `PayloadRuntime` caller, no product signing/private-key capability, no startup `Context`/`PackageManager` lookup, the frozen Guard ordering and cleanup suppression ownership.

## Local protected fixtures

The target APKs were externally signed only for installation testing with a random two-day PKCS12 certificate generated under ignored `build/m2-03/signing/`. No key, password, certificate or APK is tracked by Git.

- extracted Release/R8: size `911047`, SHA-256 `ca1e5a0653309f8409030713447c6c72f866b0cbcd77461711ebbc4093045c0c`
- direct Release/R8: size `1271495`, SHA-256 `ea64ebdee9565827b0c54f330e7ab5d415484504048e8f01c9231f236af545de`

## Device status

The authorized device is a Xiaomi `sirius`/MI 8 SE, API 29, `arm64-v8a,armeabi-v7a,armeabi`, ADB shell UID 2000 and non-root. The first bounded acceptance attempt stopped at its first normal `adb install` with `INSTALL_FAILED_USER_RESTRICTED: Install canceled by user`; the script then verified package cleanup. No secure setting, root path, UI bypass or retry loop was used. This is an open external acceptance gate, not a product-test pass.

API 29/36 x86_64 acceptance is delegated to the repository GitHub Linux/KVM workflow with a 45-minute job timeout, command timeouts and unconditional package/emulator cleanup. Remote run IDs and final reports are recorded only after the frozen branch is pushed.
