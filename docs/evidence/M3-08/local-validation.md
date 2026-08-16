# M3-08 local validation

- Timestamp: `2026-08-16T12:08:43+08:00`
- Environment: Windows `10.0.19045.0`, Node.js `v24.12.0`
- Branch: `docs/m3-08-startup-performance-stability`
- Implementation freeze: `c523c8d8826b24e4fd2595294e08119833d23464`
- Base: `origin/main@930b759c99f330218dc4404368e9844e80456c82`

## Commands

| Command | Exit | Result |
|---|---:|---|
| `node --check tools/governance/verify-m3-08-startup-stability-contract.mjs` | 0 | syntax valid |
| `node tools/governance/verify-m3-08-startup-stability-contract.mjs` | 0 | contract synchronized |
| `node tools/governance/verify-m3-08-startup-stability-contract.mjs --self-test` | 0 | 1 forbidden-diff and 20 report mutations rejected |
| `node tools/governance/verify-m3-08-startup-stability-contract.mjs --base-ref origin/main` | 0 | zero production/fixture/benchmark implementation diff |
| `node tools/governance/validate-project-package.mjs` | 0 | 33 task cards, 11 core docs, 15 ADRs |
| `node .agents/skills/coordinate-project-handoff/scripts/validate-handoff.mjs HandOff.md --strict` | 0 | strict HandOff valid |
| `git diff --check` | 0 | no whitespace errors |

No Gradle, KVM, emulator, physical device, APK install, fuzz or benchmark command ran. M3-08 changes governance contracts only. M3-05 PR #63 remains draft and blocked.

## SHA-256

| File | SHA-256 |
|---|---|
| `docs/adr/0015-startup-performance-measurement-stability.md` | `5d644dd613b1affd635fc24070a538a00aec79ba01454262780646dfd9c6f460` |
| `docs/tasks/M3-08-startup-performance-stability-contract.md` | `e7d55345086aaf730dc4ef420de1f8802d335ecfcabdc0ccea132ba6504e2f95` |
| `tools/governance/verify-m3-08-startup-stability-contract.mjs` | `d00f81d711daa45eee73edea1603f421ddc18d8e17718fd368df02933c19c086` |

## Pending gate

An independent read-only security review must return `P0=0/P1=0/P2=0` before this branch is pushed or a draft PR is created. After review, only Ubuntu/Windows Build and Governance are in scope; device and benchmark workflows remain excluded.
