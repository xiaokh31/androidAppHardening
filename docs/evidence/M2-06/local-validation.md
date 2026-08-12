# M2-06 local validation

- Implementation SHA: `9839c8de321c82ddd12745006d6aca16f49ac370`
- Base SHA: `644825e95d3338160df19021389d5ba8bd125eb1`
- Branch: `feat/m2-06-memory-dump-controls`
- Host: Windows, PowerShell
- Toolchain: Temurin JDK 17.0.19+10, Gradle 9.5.0, Node.js 24.12.0, repository-pinned Android SDK/NDK
- Validation mode: `pre-cli`

## Commands

All commands exited `0` unless the explicitly documented host-compiler boundary says otherwise.

1. `node --check tools/validation/verify-m2-06-memory-controls.mjs` and `node --check tools/validation/run-m2-02-device-acceptance.mjs`.
2. `node tools/validation/verify-m2-06-memory-controls.mjs build/m2-06/memory-controls.json`; the in-memory 2 MiB lock-budget mutation was rejected.
3. Repository-local offline Gradle invocation for `:runtime:native:assembleRelease`, `:runtime:policy:memoryControlsSelfTest`, and both M202 extracted/direct debug androidTest APKs. Result: `BUILD SUCCESSFUL`, 182 tasks, 33 seconds; policy matrix: 11 cases.
4. Repository-local offline Gradle invocation for `:runtime:policy:assembleRelease`. Result: `BUILD SUCCESSFUL`, 36 tasks, 25 seconds.
5. `node tools/validation/verify-m2-04-four-abi-runtime.mjs --self-test --report=build/m2-06/native-runtime.json`; all four ABI ELF/JNI and mutation checks passed with the seventh approved memory-profile JNI export.
6. `node tools/governance/validate-project-package.mjs`; result: 28 task cards, 11 core docs and 11 ADRs accepted.
7. `node .agents/skills/coordinate-project-handoff/scripts/validate-handoff.mjs HandOff.md --strict` and `git diff --check`; both passed before freeze.

The new Native M2-06 object files compile with the local MSVC toolchain. The complete Windows Host target remains unsuitable as a local execution oracle because the already pinned Mbed TLS configuration triggers its pre-existing zero-sized PK-structure `C2229` failure under MSVC; the repository CI uses its fixed clang-cl path for that Host target. Exact-head Ubuntu CI must therefore execute the complete Native Host test once. No local emulator or physical device was started.

## Artifact hashes

| Artifact | Bytes | SHA-256 |
|---|---:|---|
| `runtime/native/build/outputs/aar/native-release.aar` | 456912 | `c7c0f0e958570c44c12b92c10b8ae37ffca018070e1a6b52171e675e1c62c738` |
| `runtime/policy/build/outputs/aar/policy-release.aar` | 36474 | `a7fdea442c5419ac1c7081dba59261d1fe297de6d51ec40fccb4603a6ea3cad5` |
| `build/m2-06/native-runtime.json` | 5632 | `152ad981bbfcf36e33e364d4b42b2c1ba5d58ebdc12271c62701e66edfd4367f` |
| `build/m2-06/memory-controls.json` | 733 | `1486c6d1054e5a35d3e14a6e0ab83ecda477c5ac4a8386a45b44f88a25112f5d` |
| `docs/evidence/M2-06/memory-protection-report-sample.json` | 418 | `6d6c75de4248b657e3b6e4385b30969e0a66511d1bb069d1dac58273d7e2155c` |
| extracted Release/R8 target APK | 431096 | `4949bed3301b9faa7029c49d4e6051753fabcc9c2e20d05c720cc12e608b305b` |
| direct Release/R8 target APK | 1122276 | `178c23508c30c3d9fbaf8de5509497f3eecee39f8bc6c3c467ab515cb838c444` |
| extracted androidTest APK | 143210 | `fa6f896d365f6dbb8314a8ace946d00106a5353081513c2d6ceb51b00d2e0ede` |
| direct androidTest APK | 143206 | `c2308f7233bd8f7f3fca6c0ce823aa1e3babc410b27520aea60106c957976fce` |

## Remaining gates

- Independent read-only security review of the frozen implementation SHA.
- One exact-head Ubuntu/Windows Build and bounded API 29/36 x86_64 KVM run with forced cleanup.
- Evidence-only reconciliation, README update, unique Issue #17 PR, and post-merge strict HandOff. These controls remain cost controls and do not prevent root, kernel, injection, or process-control extraction.
