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

## Validation Flow

1. Record input hash, task ID, branch, commit, OS, JDK, SDK, Build Tools, NDK, and tool versions.
2. Run the original fixture to establish expected behavior.
3. Run the sole business command, `android-app-hardening protect --input <apk> --output <unsigned-apk> --report <json>`; confirm the original input hash is unchanged.
4. Use the task's pinned Gradle verification entry points and standard Android tools to confirm the output has no valid v1/v2/v3/v4 signature or `.idsig`, has valid ZIP structure, and passes required 4-byte and 16 KiB native-library alignment checks. Do not invent additional public CLI commands.
5. Confirm original root `classes*.dex` entries are absent and common static inspection exposes only the allowed shell surface.
6. Sign a copy only inside the integration-test harness with its ephemeral certificate; install and exercise Application, factory, Provider, Service, receiver, multidex, process, and JNI behavior required by the task.
7. Repeat with a wrong certificate and mutations to payload, configuration, manifest, shell DEX, and runtime SO. Require the documented blocking result.
8. Exercise supported API and ABI cases. Verify x86 is not rejected solely because it is x86.
9. Measure size, startup, and memory when the task defines budgets.

## Evidence

Return exact commands, exit codes, environment, hashes, artifact IDs, observed behavior, and limitations. Do not publish decompiled output or edit root `HandOff.md`.
