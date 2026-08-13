# M2-06 local validation

- Final reviewed implementation SHA: `ac374ad03bce87ac7068cf124f4721441f79f59f`
- Initial rejected implementation SHA: `9839c8de321c82ddd12745006d6aca16f49ac370`
- Base SHA: `644825e95d3338160df19021389d5ba8bd125eb1`
- Branch: `feat/m2-06-memory-dump-controls`
- Host: Windows, PowerShell
- Toolchain: Temurin JDK 17.0.19+10, Gradle 9.5.0, Node.js 24.12.0, repository-pinned Android SDK/NDK
- Validation mode: `pre-cli`

## Commands

All commands exited `0` unless the explicitly documented host-compiler boundary says otherwise.

1. `node --check tools/validation/verify-m2-06-memory-controls.mjs` and `node --check tools/validation/run-m2-02-device-acceptance.mjs`.
2. `node tools/validation/verify-m2-06-memory-controls.mjs build/m2-06/memory-controls.json`; the in-memory 2 MiB lock-budget mutation was rejected.
3. Repository-local offline Gradle invocation for `:runtime:native:assembleRelease`, `:runtime:policy:memoryControlsSelfTest`, and both M202 extracted/direct debug androidTest APKs. Result: `BUILD SUCCESSFUL`, 182 tasks, 33 seconds; policy matrix: 11 cases. After the independent review fix, a bounded four-ABI `:runtime:native:assembleRelease` rerun passed in 26 seconds and both extracted/direct Release/R8 targets were rebuilt in 38 seconds.
4. Repository-local offline Gradle invocation for `:runtime:policy:assembleRelease`. Result: `BUILD SUCCESSFUL`, 36 tasks, 25 seconds.
5. `node tools/validation/verify-m2-04-four-abi-runtime.mjs --self-test --report=build/m2-06/native-runtime.json`; all four ABI ELF/JNI and mutation checks passed with the seventh approved memory-profile JNI export.
6. `node tools/governance/validate-project-package.mjs`; result: 28 task cards, 11 core docs and 11 ADRs accepted.
7. `node .agents/skills/coordinate-project-handoff/scripts/validate-handoff.mjs HandOff.md --strict` and `git diff --check`; both passed before freeze.

The new Native M2-06 object files compile with the local MSVC toolchain. The complete Windows Host target remains unsuitable as a local execution oracle because the already pinned Mbed TLS configuration triggers its pre-existing zero-sized PK-structure `C2229` failure under MSVC; the repository CI uses its fixed clang-cl path for that Host target. Exact-head Ubuntu CI must therefore execute the complete Native Host test once. No local emulator or physical device was started.

## Artifact hashes

| Artifact | Bytes | SHA-256 |
|---|---:|---|
| `runtime/native/build/outputs/aar/native-release.aar` | 457622 | `d17e0b9f57a84fb118ddc6b83978361a788d77c6194b6d070bf969efa033c22e` |
| `runtime/policy/build/outputs/aar/policy-release.aar` | 36474 | `a7fdea442c5419ac1c7081dba59261d1fe297de6d51ec40fccb4603a6ea3cad5` |
| `build/m2-06/native-runtime.json` | 5632 | `863fc86380f72b461f10af76be68bd076356dc93fad197cae53c54a0433f1b86` |
| `build/m2-06/memory-controls.json` | 733 | `0dfe7ff26f4cdc00e5d694bb1722d4ede73f7fc10069e20300d863db81472c80` |
| `docs/evidence/M2-06/memory-protection-report-sample.json` | 418 | `6d6c75de4248b657e3b6e4385b30969e0a66511d1bb069d1dac58273d7e2155c` |
| extracted Release/R8 target APK | 431724 | `c983eda9232d09727a96ecd9e003052225b8d0b7a86e557dc03e29ed4d532d93` |
| direct Release/R8 target APK | 1122276 | `39c504129d7293fe9f0e5d9f8b394f8227eb85389f25290768c3b74756a331e3` |
| extracted androidTest APK | 143210 | `fa6f896d365f6dbb8314a8ace946d00106a5353081513c2d6ceb51b00d2e0ede` |
| direct androidTest APK | 143206 | `c2308f7233bd8f7f3fca6c0ce823aa1e3babc410b27520aea60106c957976fce` |

## Remaining gates

- One exact-head Ubuntu/Windows Build and bounded API 29/36 x86_64 KVM run with forced cleanup.
- Evidence-only reconciliation, README update, unique Issue #17 PR, and post-merge strict HandOff. These controls remain cost controls and do not prevent root, kernel, injection, or process-control extraction.
