# M2-07 Windows runner image maintenance

## Scope

- Issue: [#50](https://github.com/xiaokh31/androidAppHardening/issues/50)
- Branch: `fix/m2-07-windows-runner-lock`
- Triggering failure: Build run [`31754337214`](https://github.com/xiaokh31/androidAppHardening/actions/runs/31754337214), Windows job `94626822969`
- Failure boundary: the existing fail-closed allowlist rejected `ImageOS=win25-vs2026`, `ImageVersion=20260810.198.2` before compiler invocation.
- Excluded: Native crypto implementation, Android Runtime, fixtures, KVM, emulator and physical-device testing.

## Official immutable manifest review

- Official repository: `actions/runner-images`
- Exact ref: `refs/tags/win25-vs2026/20260810.198`
- Ref commit: `9669462631cac120f4f558e7dadd31a14d1f1a41`
- Commit timestamp: `2026-08-11T10:56:58Z`
- Commit tree: `5bcdb777940f5179cf808aa06c95ab3f6d8d9217`
- Manifest path: `images/windows/Windows2025-VS2026-Readme.md`
- Manifest blob: `e5e0527a4cc19153e7e8daf98780ff18e7062ac1`
- Manifest size: `33783` bytes
- Manifest image version: `20260810.198.2`
- LLVM: `20.1.8`
- Visual Studio Enterprise 2026: `18.8.12023.21`
- `Microsoft.VisualStudio.Component.VC.Tools.x86.x64`: `18.8.11901.359`
- Windows SDK: `10.0.26100.0`

The reviewed inventory matches the existing M2-07 compiler and SDK contract. The lock adds only the exact runtime/ref pair. CI retains runtime checks for `clang-cl 20.1.8`, Visual Studio `18.8.12023.21`, x64 tools `18.8.11901.359` and `cl.exe 19.51.36252`; any unknown image, version drift or reordered mapping remains rejected.

## Required validation

- `node tools/validation/verify-m2-07-native-crypto.mjs --self-test`: exit `0`; archive/source identity, verified stamp and expanded mutation matrix PASS.
- `node tools/governance/validate-project-package.mjs`: exit `0`; 28 task cards, 11 core docs and 11 ADRs PASS.
- `node --check tools/validation/verify-m2-07-native-crypto.mjs`: exit `0`.
- `git diff --check`: exit `0`.
- `node .agents/skills/coordinate-project-handoff/scripts/validate-handoff.mjs HandOff.md --strict --allow-pending-clean`: exit `0` before commit; exact strict validation is required after commit.
- Ubuntu/Windows Build and Governance on the exact PR head

Local validation timestamp: `2026-08-14T07:46:43+08:00`, Windows 10 x64, Node.js from the repository-pinned environment.

- Machine lock SHA-256: `ee83042ed3e6d175b27bf2b5e31a2a9b80f1775dd1fd5d9c96f54774d4a31288`
- Validator SHA-256: `ed5028bfcbf5da1237fb01cc2e8f0478d1325b3dc4b6ae2af85c181ac8b2b5cc`

No KVM or device run is required because no Android, Native, workflow KVM or fixture file changes.

## Exact-head pull-request validation

Implementation head `43e523e0ff7bb1dbf70135affc546d18414b73e8` is published as draft PR [#51](https://github.com/xiaokh31/androidAppHardening/pull/51), targeting `main` and closing Issue #50.

- Build run [`31755188947`](https://github.com/xiaokh31/androidAppHardening/actions/runs/31755188947): Ubuntu job `94629334867` PASS; Windows job `94629334875` PASS.
- Governance run [`31755188999`](https://github.com/xiaokh31/androidAppHardening/actions/runs/31755188999): Ubuntu job `94629334988` PASS; Windows job `94629335066` PASS.
- The Windows job accepted only exact runtime `20260810.198.2`, passed the M2-07 Host crypto vectors and pinned entry-point checks, then completed the full Windows regression and four-ABI verification.
- Automatically triggered KVM run `31755188964` was cancelled immediately by `/root` because this maintenance changes no Android/KVM surface and the user explicitly excluded repeated KVM/device execution. Its cancelled jobs are not presented as acceptance evidence.

PR #51 remains draft and mergeable. Independent read-only review and explicit ready/merge direction remain separate future gates.
