# M3-02 local validation

- Timestamp: `2026-08-14T11:13:07+08:00`
- Branch: `chore/m3-02-tamper-fuzz`
- Base: `ea30f51373003981cdcdae60dda795ba1fefd587`
- Implementation freeze: `90ef2ecf662371c82fed5f3d0fa92dbf9324e9e2`
- Host: Windows 10.0.19045 x64; Eclipse Temurin `17.0.19+10`; Gradle `9.5.0`; Node.js `24.12.0`
- Device boundary: no local emulator or physical device was started.

## Commands

| Command | Exit | Result |
|---|---:|---|
| repository-local offline Gradle `:tools:validation:tamperTest --no-daemon` | 0 | Final targeted run completed in 1m24s: APK inspection, Binary AXML, container, two-pass regression preflight, and 12 exact Host catalog cases passed; 57 Runtime cases remain explicitly pending API 29/36 evidence. |
| repository-local Gradle `:fixtures:android:compileM202DirectDebugAndroidTestJavaWithJavac` and `:fixtures:android:compileM203DirectDebugAndroidTestJavaWithJavac` | 0 | The named per-case Release/R8 instrumentation sources compile against the production Runtime/Policy classpath. |
| `node tools/validation/generate-m3-02-jvm-corpus.mjs --check` | 0 | The tracked 18,508-byte synthetic APK contains no v1 entries or v2/v3 Signing Block; Binary AXML and binary regressions match it. A signed Release APK was separately rejected before any write. |
| `node tools/validation/verify-m3-02-fuzz-toolchain.mjs` | 0 | Jazzer/Clang/resource/runner locks, five-target aggregation negatives, unsigned-corpus boundary, fixed Host stages, and exact named device evidence wiring passed. |
| `node --check` for changed M3-02 scripts; `git diff --check` | 0 | Script syntax and whitespace checks passed. |

The repository-local pinned JDK, Gradle distribution, and ignored dependency cache were used. No dependency or large tool was downloaded to the system drive. The required 600-second fuzz runs are deliberately deferred to one exact-head PR CI matrix.

## Local artifacts

| Artifact | Bytes | SHA-256 | Result |
|---|---:|---|---|
| tracked unsigned `tools/validation/src/fuzz/resources/corpus/apk/valid-m301.apk` | 18,508 | `83a58746f01e4db559926eefc2434f1cb385b0792296a2c3b67b3cbdb498dc5d` | PASS; manifest/classes present, no v1/v2/v3 signature material |
| ignored `tools/validation/build/reports/security/m3-02/regression.json` | 272 | `fe0686b721d393506d8425460e69479a1c583ed996db402550fc0fa4888a19b7` | PASS; `runs=2`, `jvm_inputs_executed=4`, `structured_mutations_executed=6`, `native_inputs_deferred_to_sanitizer=7`, deterministic result `f43f1ef3...e3fd` |
| ignored `tools/validation/build/reports/security/m3-02/tamper.json` | 3,032 | `1a6bfc0215233d28d2ba077242f348dde8edc0f3ff14dfa9c62a13628944d134` | PASS_PENDING_DEVICE; 12 exact Host cases passed, 57 Runtime cases pending, catalog SHA-256 `68d509aa...bf4` |
| ignored `tools/validation/build/reports/security/fuzz-summary.json` | 414 | `2898faf7694095aaf74486f00cf4ad7ce7638d81cd0ac599a4189b81f59daf0d` | PASS_PENDING_REMOTE; five target reports, Native sanitizer and API 29/36 evidence remain remote gates |
| ignored `host/container/build/reports/m1-04/container-self-test.json` | 5,192 | `bb00154215146189a382ebd370ee26e2e95ae67f20a19aa68a629a82ff5bd474` | PASS; 13 production container cases |
| ignored `host/axml/build/reports/m1-03/error-matrix.json` | 2,415 | `b287183d1c2af46cfb9ce4b027e7993ec9721e039f91c3125176a962a2ddd641` | PASS; exact Binary AXML negative evidence |

## Independent review

- The full review rejected the original candidate with `P0=0/P1=3/P2=0`; those findings are archived in `security-review-1.md`.
- A first bounded repair at `9ef5a1e174cc96a6b83b562da390d86aacd75efa` remained blocked by a signed seed and two fail-open evidence bindings.
- The final bounded review of `90ef2ecf662371c82fed5f3d0fa92dbf9324e9e2` passed with `P0=0/P1=0/P2=0`; see `security-review-2.md`.

## Pending gates

- Publication authorization, branch push, the unique Issue #19 draft PR, exact-head Ubuntu/Windows 600-second JVM/Native targets, API 29/36 x86_64 KVM, Build and Governance remain pending.
- README/task status and merge completion will be updated only after all exact-head gates pass and the PR is merged.

## PR #52 bounded RSS correction

- Timestamp: `2026-08-15T02:31:58+08:00`
- Failed run: M3-02 Fuzz `31828524638`, Ubuntu jobs `94858425220` and `94858425349`.
- Root cause: Jazzer instrumented the full third-party runtime classpath and exceeded its unchanged fail-closed `2048 MiB` RSS limit (`2058 MiB` for APK and `2356 MiB` for AXML). Both failures were libFuzzer OOM exits, not parser crashes.
- Correction: coverage and custom-hook instrumentation are restricted to repository-owned `ah.host.**` and `ah.tools.validation.fuzz.**`; the 2 GiB RSS limit, 5-second input timeout, 4 MiB maximum input and 600-second PR duration are unchanged.
- Local verification: immutable toolchain verification exited `0`; repository-local JDK 17/Gradle 9.5.0 offline five-second APK+AXML smoke exited `0`, executed `470476` AXML cases, and reported approximately `550 MiB` AXML RSS. No program was downloaded and no device was started.
