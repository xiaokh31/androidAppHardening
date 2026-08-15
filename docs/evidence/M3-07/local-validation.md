# M3-07 local validation

- Implementation freeze: `90f754ea185a8633acd585d181ee108db016209d`
- Base: `3584b379f6abd1ba85726831aa1f68a2fac4183b`
- Branch: `docs/m3-07-high-benchmark-contract`
- Timestamp: `2026-08-15T23:01:28+08:00`
- OS: Windows, PowerShell
- Node.js: `v24.12.0`
- Git: `2.52.0.windows.1`

## Commands

| Command | Exit | Result |
| --- | ---: | --- |
| `node --check tools/governance/verify-m3-07-high-benchmark-contract.mjs` | 0 | syntax valid |
| `node tools/governance/verify-m3-07-high-benchmark-contract.mjs --self-test` | 0 | 10 production-surface and 20 serialized-report negatives rejected |
| `node tools/governance/verify-m3-07-high-benchmark-contract.mjs --base-ref 3584b379f6abd1ba85726831aa1f68a2fac4183b` | 0 | HIGH boundary and zero production diff accepted |
| `node tools/governance/validate-project-package.mjs` | 0 | 32 task cards, 11 core docs, 14 ADRs |
| `node .agents/skills/coordinate-project-handoff/scripts/validate-handoff.mjs HandOff.md --strict` | 0 | strict HandOff accepted |
| `git diff --check HEAD^ HEAD` | 0 | no whitespace errors |

The production-surface negatives cover Runtime main and Release, Native manifest, Host main, Android fixture source and keep rules, Android benchmark main, distribution configuration, Gradle environment access and preferences. The report negatives cover missing or invalid mode/risk fields, 29/31 samples, non-finite or wrong types, false claim labels, process/handle/lookup/cleanup failures, jitter count/type, wall-time bound, Host null contract, Host sample count and percentile mismatch.

## Frozen hashes

| File | SHA-256 |
| --- | --- |
| `tools/governance/verify-m3-07-high-benchmark-contract.mjs` | `7bbd807ac243164f778a638345d751547cf6fd5338c60c88cc83345a0124234d` |
| `docs/adr/0014-test-only-high-benchmark-boundary.md` | `cd66aeff3aadfa31376e486d4129faab301345450f97f5981a9bd6f9189d1c7b` |
| `docs/tasks/M3-07-test-only-high-benchmark-contract.md` | `a315bbb921ab761b1693970342b3d8a1216f4bd15efd556b468371563d2fdbf0` |

No Gradle, benchmark, device, emulator, KVM or physical-device matrix was run for this governance-only task.
