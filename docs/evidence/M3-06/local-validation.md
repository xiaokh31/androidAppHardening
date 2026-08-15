# M3-06 local validation

- Task: `M3-06`
- Issue: `#56`
- Branch: `docs/m3-06-api-abi-validation-contract`
- Base commit: `1a2c2d85be62502913066b301c1083b05de37d00`
- Implementation commit: `ef8785951a6bfe26cd54d48b687faf890ee8b039`
- Timestamp: `2026-08-15T09:06:04+08:00`
- Environment: Windows `10.0.19045.0`, Node.js `v24.12.0`
- Validation mode: `governance-only`

## Results

| Command | Exit | Result |
| --- | ---: | --- |
| `node tools/governance/validate-project-package.mjs` | 0 | `30 task cards, 11 core docs, 11 ADRs` |
| `node .agents/skills/coordinate-project-handoff/scripts/validate-handoff.mjs HandOff.md --strict --allow-pending-clean` | 0 | HandOff schema, branch, ancestry and pending-clean declaration passed |
| `git diff --check` | 0 | No whitespace error |
| scoped changed-file check | 0 | Only governance docs and `tools/governance/validate-project-package.mjs` changed |
| UTF-8 replacement-character scan | 1 | Expected no-match; no replacement character found |
| stale blanket API/ABI claim scan | 1 | Expected no-match; removed the prohibited automatic-support phrases |

No Gradle, APK installation, physical device, emulator, KVM, fuzz, benchmark, network download, production source, fixture source, Runtime binary, or Host executable validation was run. Those paths are outside this contract-only task.

## Contract hashes

| File | SHA-256 |
| --- | --- |
| `docs/adr/0012-api-abi-validation-claim-boundary.md` | `0568add20e85cd5d3b412fbf84d865de89b5d78c3ccff9d68a56e91b4055e4c1` |
| `docs/tasks/M3-06-api-abi-validation-claim-contract.md` | `98c4461eeeacd885ca479308bec4f9b74ab7c5428b2499fdaf26fa1ffb3aee05` |
| `docs/tasks/M3-04-api-and-abi-matrix.md` | `d0277f954db12e42460c7119f70df1e667a206e07fb82a7bbe95fef7612504b1` |

## Scope proof

- The implementation commit contains 15 files: governance documentation plus the fixed governance task registry.
- The paused M3-04 blocker remains isolated on local commit `72a5fce85bbee5b0f1888028049f096487febb7e`; no M3-04 implementation was cherry-picked into this branch.
- M3-05 remains unstarted.
