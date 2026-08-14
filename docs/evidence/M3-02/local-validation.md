# M3-02 local validation

- Timestamp: `2026-08-14T09:56:33+08:00`
- Branch: `chore/m3-02-tamper-fuzz`
- Base: `ea30f51373003981cdcdae60dda795ba1fefd587`
- Implementation freeze: `e5b329bde53c9cd42ed58c6f9f3eff3c54bd52fc`
- Host: Windows 10.0.19045 x64; Eclipse Temurin `17.0.19+10`; Gradle `9.5.0`; Node.js `24.12.0`
- Device boundary: no local emulator or physical device was started.

## Commands

| Command | Exit | Result |
|---|---:|---|
| repository-local Gradle `:tools:validation:regressionFuzz :tools:validation:tamperTest --offline --no-daemon --console=plain` | 0 | Four JVM seed/regression inputs executed twice; seven structured Native inputs were hash-checked and explicitly deferred to the sanitizer target. All 69 catalog cases and existing production APK/AXML/container negative matrices passed in the final 1m25s run. |
| repository-local Gradle `:tools:validation:tasks :tools:validation:jazzerApkPr -Pm302FuzzSeconds=1 --offline --no-daemon --console=plain` | 0 | Public tasks were discoverable; isolated APK Jazzer smoke completed 298 executions in two wall-clock seconds with no crash and without modifying tracked corpus. |
| repository-local Gradle `:fixtures:android:compileM202DirectDebugAndroidTestJavaWithJavac --offline --no-daemon --console=plain` | 0 | The expanded 10-stage ordinary-exception/OOM instrumentation matrix compiled with the production Runtime/Policy classpath in 35 seconds. |
| NDK 29 `clang++ --target=x86_64-linux-android29 ... -fsyntax-only runtime/native/src/main/cpp/m3_02_container_fuzz.cpp` | 0 | Native fuzz target compiled with C++17 warnings-as-errors syntax checks. |
| `node --check` for all changed/new M3-02 scripts | 0 | Script syntax passed. |
| `node tools/validation/verify-m3-02-fuzz-toolchain.mjs` | 0 | Jazzer/Clang/resource/runner lock and six negative mutations passed. |
| `git diff --check` | 0 | No whitespace errors. |

The first wrapper attempt used the user-level Gradle home and was unable to download in the sandbox; the verified runs above use the repository-local pinned JDK, Gradle distribution, and ignored dependency cache. No dependency or large tool was downloaded to the system drive.

## Local artifacts

| Artifact | Bytes | SHA-256 | Result |
|---|---:|---|---|
| ignored `tools/validation/build/reports/security/m3-02/regression.json` | 234 | `48e8e4039573ca19e87a938fe90292c8d9f2e7519965ab1e84f9694977d03526` | PASS; `runs=2`, `jvm_inputs_executed=4`, `native_inputs_deferred_to_sanitizer=7`, deterministic result hash `efa7bbab...1a27` |
| ignored `tools/validation/build/reports/security/m3-02/tamper.json` | 521 | `fcc94afc52943db54b715508ee98e30bbbf139bbe987fc1a7b7702b9196e01b5` | PASS; 69 cases, catalog SHA-256 `c65eb452...b17a` |
| ignored `tools/validation/build/reports/security/fuzz-summary.json` | 399 | `11a380fdf9d2ddff79ff364b6a8032790152766beab2ab922574521fa8dc2a94` | Host regression/tamper PASS; required PR fuzz, Native sanitizer and API 29/36 device fields remain explicitly pending |

## Pending gates

- The full 600-second JVM/Native target matrix is deliberately deferred to parallel exact-head PR CI; it was not repeated locally.
- API 29/36 x86_64 Runtime tamper execution is pending the same frozen-head KVM workflow.
- Independent read-only security review, publication authorization, unique draft PR, exact-head Build/Governance, merge, README completion update, and post-merge strict HandOff are pending.
