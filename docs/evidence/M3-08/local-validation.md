# M3-08 local validation

- Timestamp: `2026-08-16T12:41:29+08:00`
- Environment: Windows `10.0.19045.0`, Node.js `v24.12.0`
- Branch: `docs/m3-08-startup-performance-stability`
- Final corrected implementation freeze: `7e949e9d58ca0a0202790bff70e6199272c75c7f`
- Base: `origin/main@930b759c99f330218dc4404368e9844e80456c82`
- Superseded freezes: `409a73a6ee471da3d2f9ba56f4ac2c50f6e6b522` (`P1=2/P2=2`, review 1) and `3db35f69a00e3a8804461b5aaba60717dc14da74` (`P1=1`, review 2)

## Commands

| Command | Exit | Result |
|---|---:|---|
| `node --check tools/governance/verify-m3-08-startup-stability-contract.mjs` | 0 | syntax valid |
| `node tools/governance/verify-m3-08-startup-stability-contract.mjs --base-ref origin/main` | 0 | contract synchronized and zero production/fixture/benchmark implementation diff |
| `node tools/governance/verify-m3-08-startup-stability-contract.mjs --self-test` | 0 | 1 forbidden-diff + 45 package negatives + 2 arithmetic positives passed |
| `node tools/governance/validate-project-package.mjs` | 0 | 33 task cards, 11 core docs, 15 ADRs |
| `node .agents/skills/coordinate-project-handoff/scripts/validate-handoff.mjs HandOff.md --strict` | 0 | strict HandOff valid |
| `git diff --check` | 0 | no whitespace errors |

The corrected validator reads and hashes both retained campaign reports, invokes the M3-07 source-report validator, recomputes raw-sample percentiles/deltas/budgets and all 90 aggregate rows, and binds explicit head/run/job/attempt/environment/boot identities. It parses a canonical manifest that binds distinct A/B report bytes and exact order plus six canonical baseline/protected APK identities, hashes the six actual APK files supplied under `--artifact-root`, and recursively rejects sensitive paths and device-serial fields.

No Gradle, KVM, emulator, physical device, APK install, fuzz or benchmark command ran. M3-08 changes governance contracts only. M3-05 PR #63 remains draft and blocked.

## SHA-256

| File | SHA-256 |
|---|---|
| `docs/adr/0015-startup-performance-measurement-stability.md` | `451c9228a087c71e3595fac956af4bbe5299a51693058d799ee4a87752256d36` |
| `docs/tasks/M3-08-startup-performance-stability-contract.md` | `a3ffa8e899705570ee74559801fb8c16cfedad3bb06d37bb6b7fc9ca77ceadeb` |
| `tools/governance/verify-m3-08-startup-stability-contract.mjs` | `ad9f3bba62abb37f5d521d263200e9402706ee48b656d31f2c757023da355338` |
| `docs/evidence/M3-08/security-review-1.md` | `fb2a55f68f62f536db04f7b5d820b7fde6e1ee8f03f00643e75e9017914db362` |
| `docs/evidence/M3-08/security-review-2.md` | `ab209de7f96c9f73e34569d6ec1282b39286f3b01ad4651cd96c954d5d39670c` |
| `docs/evidence/M3-08/security-review-3.md` | `143305183a6c18732b3691ad7e58fdb193e5b72f37f0a750ef622c9a0d90cc2a` |

## Independent review gate

Independent review 3 confirmed review-2's remaining source-identity finding is closed and returned `P0=0/P1=0/P2=0` for final corrected freeze `7e949e9d58ca0a0202790bff70e6199272c75c7f`. The branch may now be published and subjected only to Ubuntu/Windows Build and Governance; device and benchmark workflows remain excluded until the later M3-05 replacement job.
