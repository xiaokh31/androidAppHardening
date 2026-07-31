---
name: validate-protected-apk
description: Validate an authorized protected APK and its reports against repository acceptance criteria. Use after host or runtime changes, before merging security-sensitive work, or when investigating packaging, signature, alignment, startup, ABI, size, tamper, environment-detection, or compatibility regressions.
---

# Validate Protected APK

## Safety

- Validate only project-generated fixtures or APKs with explicit authorization.
- Work in ignored build or artifact directories. Never commit APKs, DEX output, decompiled source, private keys, or customer paths.
- Do not download tools automatically. Use versions pinned in `docs/TOOLCHAIN_AND_PROVENANCE.md`, or report a missing-tool blocker.
- Product modules must remain incapable of signing. Integration tests may generate an ephemeral non-production certificate in ignored build output solely to install a protected fixture.

## Validation Mode

Select exactly one mode before running commands and record it in the evidence:

- `pre-cli`: use when the current task neither implements nor depends on M1-06 and the release CLI does not yet exist. Run only the task card's pinned Gradle/module/PoC entry points. M0-03 validates the toolchain and module graph; M0-04/M0-05 validate their Android PoC flavors; M1-05 validates its internal assembler/repacker harness; M2 tasks use their test-only integration driver. Do not invent, stub, or prematurely expose a product CLI.
- `full-flow`: use only for M1-06 or a task whose satisfied dependency set includes M1-06 and whose built distribution actually provides `android-app-hardening`. The sole business command is `android-app-hardening protect --input <apk> --output <unsigned-apk> --report <json>`.

If the task requires an artifact that its dependencies cannot produce in the selected mode, stop with a structured blocker. Do not silently run a later task's interface.

## Validation Flow

1. Record the selected mode, input hash, task ID, branch, commit, OS, JDK, SDK, Build Tools, NDK, and tool versions.
2. Run the original synthetic fixture to establish expected behavior.
3. In `pre-cli` mode, run the exact task-card Gradle/module/PoC entry points and identify the internal test harness used. In `full-flow` mode, run the sole business command above. In both modes confirm the original input hash is unchanged.
4. When the current task produces an APK, use its pinned Gradle verification entry points and standard Android tools to confirm the output has no valid v1/v2/v3/v4 signature or `.idsig`, has valid ZIP structure, and passes required 4-byte and 16 KiB native-library alignment checks. Do not invent additional public CLI commands.
5. When the current task transforms DEX, confirm original root `classes*.dex` entries are absent and common static inspection exposes only the allowed shell surface.
6. When installation is in scope, sign only test copies inside the integration-test harness with one ephemeral certificate; use that same certificate for the signed input and protected output, then exercise the Application, factory, Provider, Service, receiver, multidex, process, and JNI behavior required by the task.
7. When tamper handling is in scope, repeat with a wrong certificate and mutations to payload, configuration, manifest, shell DEX, and Runtime SO. Require the documented blocking result.
8. Exercise the API and ABI cases owned by the task. Verify x86 is not rejected solely because it is x86.
9. Measure size, startup, and memory only when the task defines those metrics and budgets.

## Evidence

Return the selected validation mode, why it was applicable, exact commands, exit codes, environment, hashes, artifact IDs, observed behavior, and limitations. Do not publish decompiled output or edit root `HandOff.md`.
