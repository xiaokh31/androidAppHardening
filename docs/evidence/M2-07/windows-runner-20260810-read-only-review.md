# M2-07 Windows Runner Lock Independent Read-Only Review

- Review status: `PASS`
- Findings: `P0=0`, `P1=0`, `P2=0`
- Reviewer: `/root/m2_07_runner_lock_review`
- Reviewed base: `fde701a5bb60942d1bf79e47abb19fff75ad6565`
- Frozen implementation/evidence input: `67049c62986e1def03a48665bd3413ec4b5667d9`
- Review timestamp: `2026-08-14T08:22:40+08:00`

## Scope and conclusion

The reviewer inspected the complete `main...HEAD` diff and confirmed it contains exactly six files. The accepted implementation change adds only the exact Windows hosted-runner mapping `20260810.198.2 -> win25-vs2026/20260810.198` and updates `ci_toolchains.reviewed_at`. It does not change workflows, Runtime, Native, fixtures, Gradle, KVM behavior, compiler/SDK versions, or compatibility ranges.

The machine lock and validator agree on all three accepted Windows runtime/ref entries and on LLVM `20.1.8`, Visual Studio `18.8.12023.21`, selected x64 tools `18.8.11901.359`, and `cl.exe 19.51.36252`. Deep structural comparison and the self-test reject review-date, image, manifest-ref, removal, addition, ordering, and toolchain-field mutations. Unknown or duplicate runtime values remain fail-closed.

The official `actions/runner-images` ref was independently verified at commit `9669462631cac120f4f558e7dadd31a14d1f1a41`, tree `5bcdb777940f5179cf808aa06c95ab3f6d8d9217`, and manifest blob `e5e0527a4cc19153e7e8daf98780ff18e7062ac1` of size `33783` bytes. The immutable manifest supports image `20260810.198.2` and the locked LLVM, Visual Studio, x64 tools, and Windows SDK `10.0.26100.0` inventory.

Draft PR #51 was independently confirmed OPEN, MERGEABLE, based on `main`, and headed by the frozen input. Exact-head Build `31755950097` passed on Ubuntu and Windows; exact-head Governance `31755950100` passed on Ubuntu and Windows. KVM run `31755950095` was cancelled, its API 29/36 jobs were skipped, and it is not acceptance evidence. No physical device, emulator, KVM, or full Gradle matrix was run by the reviewer.

## Verification

| Command or evidence | Result |
| --- | --- |
| `node --check tools/validation/verify-m2-07-native-crypto.mjs` | exit `0` |
| `node tools/validation/verify-m2-07-native-crypto.mjs --self-test` | exit `0` |
| `node tools/governance/validate-project-package.mjs` | exit `0` |
| `node .agents/skills/coordinate-project-handoff/scripts/validate-handoff.mjs HandOff.md --strict` | exit `0` |
| `git diff --check` and clean-worktree inspection | exit `0` |
| GitHub Build `31755950097` and Governance `31755950100` | all four jobs `SUCCESS` |

- Validator SHA-256: `ed5028bfcbf5da1237fb01cc2e8f0478d1325b3dc4b6ae2af85c181ac8b2b5cc`
- Machine-lock SHA-256: `ee83042ed3e6d175b27bf2b5e31a2a9b80f1775dd1fd5d9c96f54774d4a31288`

## Evidence-only successor boundary

This record and the corresponding root `HandOff.md` update form an evidence-only successor. They do not invalidate the independent conclusion because they do not modify the reviewed lock, validator, ADR, provenance source, workflow, Runtime, Native, fixture, Gradle, or KVM files. Any later change to those reviewed inputs invalidates inheritance and requires a fresh independent review.
