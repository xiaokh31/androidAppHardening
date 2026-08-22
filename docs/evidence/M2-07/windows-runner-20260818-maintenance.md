# M2-07 Windows runner image 20260818 maintenance

## Scope

- Issue: [#77](https://github.com/xiaokh31/androidAppHardening/issues/77)
- Branch: `chore/m2-07-windows-runner-20260818`
- Triggering PR: [#76](https://github.com/xiaokh31/androidAppHardening/pull/76)
- Triggering Build: run `32547253855`, Windows job `96967837631`
- Failure boundary: the existing finite allowlist rejected `ImageOS=win25-vs2026`, `ImageVersion=20260818.207.1` before compiler invocation.
- Excluded: Native/Runtime product code, fixtures, device, emulator, KVM, fuzz, equivalence, benchmark, M3-10 workflow and profile regeneration.

## Official immutable manifest review

- Official repository: `actions/runner-images`
- Exact ref: `refs/tags/win25-vs2026/20260818.207`
- Tag type: lightweight commit ref
- Ref commit: `0900c002193dc2d3fade0cd9133ae70e7088eb05`
- Commit timestamp: `2026-08-19T11:02:29Z`
- Commit tree: `4c1d1f41940febb68702c0ffc28fa84723097019`
- Manifest path: `images/windows/Windows2025-VS2026-Readme.md`
- Manifest blob: `4a994e86af753335ce0a1f7839414656048ac8d9`
- Manifest size: `34156` bytes
- Manifest SHA-256: `4cd6067bec8e0eb5ac04e1134fb71f23b99ca296caeab1cc5d30ca4838e218ba`
- Manifest image version: `20260818.207.1`
- OS: Windows Server 2025, OS build `10.0.26100 Build 33296`
- LLVM: `20.1.8`
- Visual Studio Enterprise 2026: `18.9.12112.369`
- `Microsoft.VisualStudio.Component.VC.Tools.x86.x64`: `18.9.12009.112`
- Windows SDK: `10.0.26100.0`
- CMake inventory includes repository-pinned `4.1.2`

The official inventory changes the Visual Studio and x64-tools package versions while retaining the shared LLVM and Windows SDK contract. The machine lock therefore binds VS/x64 tools inside each reviewed runtime record instead of replacing the old images with one global value. Build selects exactly one record by `ImageVersion`, emits its manifest ref, checks its VS/x64 values, and retains exact global checks for `clang-cl 20.1.8`, `cl.exe 19.51.36252`, Windows SDK `10.0.26100.0` and repository CMake/Ninja `4.1.2`. Any old/new mismatch or unknown fifth image fails closed.

The manifest does not state the `cl.exe` banner value. The existing exact `19.51.36252` assertion remains unchanged and must be proven by the exact-head Windows Build; a different value is a blocking toolchain change, not an allowed fallback.

## Required validation

- Node syntax for both modified validators.
- M2-07 archive/source/lock `--self-test`, including per-image VS/x64 fields and Build binding mutations.
- M3-02 fuzz-toolchain static lock validation; no fuzz execution.
- Project Governance, strict HandOff and diff/sensitive checks.
- Independent read-only review with `P0=0/P1=0/P2=0` before publication.
- Exact-head Ubuntu/Windows Build and Governance after draft PR publication.
- Cancel automatically triggered KVM, equivalence and fuzz; they are not acceptance evidence.

No device, emulator, KVM, fuzz, equivalence or benchmark run is required because no Android, Native, fixture or measurement input changes.

## Local candidate validation

- Timestamp: `2026-08-22T10:56:56+08:00`
- Environment: Windows 10 x64; Node.js `24.12.0`; existing verified ignored Mbed TLS archive/source
- Base: `9d3fc3a4ae17d14f84d223b9dbb5f92016814f1a`
- Network use: official `actions/runner-images` metadata and manifest retrieval only; no build-time dependency download

| Command | Exit | Result |
|---|---:|---|
| `node --check tools/validation/verify-m2-07-native-crypto.mjs` | 0 | PASS |
| `node --check tools/validation/verify-m3-02-fuzz-toolchain.mjs` | 0 | PASS |
| `node tools/validation/verify-m2-07-native-crypto.mjs --self-test` | 0 | PASS; archive/source/stamp identity and lock/workflow mutations rejected |
| `node tools/validation/verify-m3-02-fuzz-toolchain.mjs` | 0 | PASS; fourth Windows runtime/ref accepted only in exact order |
| `node tools/governance/validate-project-package.mjs` | 0 | PASS; 36 task cards, 11 core docs, 16 ADRs |
| `node .agents/skills/coordinate-project-handoff/scripts/validate-handoff.mjs HandOff.md --strict --allow-pending-clean` | 0 | PASS |
| `git diff --check` | 0 | PASS |
| scoped secret/path and UTF-8 replacement-character scans | 1 / 1 | PASS; no matches |

Candidate hashes:

| File | SHA-256 |
|---|---|
| `.github/workflows/build.yml` | `01c75e984d53a0c9caa1f6cde20110909c07b896e1517b18a06151335e0f4f0e` |
| `tools/validation/m2-07-native-crypto.json` | `2d1b44f6115e8cd8a7d2ca63aa5887820d44be99977d2d35a366ec4d29862032` |
| `tools/validation/verify-m2-07-native-crypto.mjs` | `d5dc404f241fdafee3cb379b513701d5be7c90650ec9b5503bb5f3fecbd8644c` |
| `tools/validation/m3-02-fuzz-toolchain.json` | `089bef2283e70eb20e22f0ddb3cee2cc026bf3fc919cc10623f58128f3af4666` |
| `tools/validation/verify-m3-02-fuzz-toolchain.mjs` | `b2888fc9ebe49a6529937871ba989e0740eb27adfbda87d08c98730830d6c707` |

The scoped scans use exit `1` to mean no match. No Gradle, Host compilation, Android SDK installation, KVM, emulator, device, fuzz, equivalence or benchmark ran locally.
