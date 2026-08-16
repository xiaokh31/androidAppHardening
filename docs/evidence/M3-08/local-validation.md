# M3-08 local validation

- Timestamp: `2026-08-16T12:30:19+08:00`
- Environment: Windows `10.0.19045.0`, Node.js `v24.12.0`
- Branch: `docs/m3-08-startup-performance-stability`
- Corrected implementation freeze: `3db35f69a00e3a8804461b5aaba60717dc14da74`
- Base: `origin/main@930b759c99f330218dc4404368e9844e80456c82`
- Superseded freeze: `409a73a6ee471da3d2f9ba56f4ac2c50f6e6b522` (`P0=0/P1=2/P2=2`, rejected by independent review 1)

## Commands

| Command | Exit | Result |
|---|---:|---|
| `node --check tools/governance/verify-m3-08-startup-stability-contract.mjs` | 0 | syntax valid |
| `node tools/governance/verify-m3-08-startup-stability-contract.mjs --base-ref origin/main` | 0 | contract synchronized and zero production/fixture/benchmark implementation diff |
| `node tools/governance/verify-m3-08-startup-stability-contract.mjs --self-test` | 0 | 1 forbidden-diff + 37 package negatives + 2 arithmetic positives passed |
| `node tools/governance/validate-project-package.mjs` | 0 | 33 task cards, 11 core docs, 15 ADRs |
| `node .agents/skills/coordinate-project-handoff/scripts/validate-handoff.mjs HandOff.md --strict` | 0 | strict HandOff valid |
| `git diff --check` | 0 | no whitespace errors |

The corrected validator reads and hashes both retained campaign reports and the artifact manifest, invokes the M3-07 source-report validator, recomputes raw-sample percentiles/deltas/budgets and all 90 aggregate rows, binds explicit head/run/job/attempt/environment/boot identities, and recursively rejects sensitive paths and device-serial fields.

No Gradle, KVM, emulator, physical device, APK install, fuzz or benchmark command ran. M3-08 changes governance contracts only. M3-05 PR #63 remains draft and blocked.

## SHA-256

| File | SHA-256 |
|---|---|
| `docs/adr/0015-startup-performance-measurement-stability.md` | `daad95f43df9c0c92fdbc9a077437a236ea22ead768aa45c0b996587579f9878` |
| `docs/tasks/M3-08-startup-performance-stability-contract.md` | `6a82c230781d2059c122319d66312b5774b32f7e974e021e38f583eff2fb745b` |
| `tools/governance/verify-m3-08-startup-stability-contract.mjs` | `bcb39018e2930ee5a494f43fe952841e06ab196fa3c7eea1eb8b7090fef677cb` |
| `docs/evidence/M3-08/security-review-1.md` | `fb2a55f68f62f536db04f7b5d820b7fde6e1ee8f03f00643e75e9017914db362` |

## Pending gate

The same independent read-only reviewer must confirm that all four review-1 findings are closed and return `P0=0/P1=0/P2=0` for corrected freeze `3db35f69a00e3a8804461b5aaba60717dc14da74`. Only then may this branch be published and subjected to Ubuntu/Windows Build and Governance; device and benchmark workflows remain excluded.
