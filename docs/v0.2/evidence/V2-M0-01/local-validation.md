# V2-M0-01 Local Validation

## Scope

- Reviewed content commit: `102cb775b205e1b592934e74a6d5d19e73ad2b3f`
- Reviewed tree: `8c378094f721edd7b9b09beb4bc8fa22a787b93a`
- Baseline commit: `7c838d7051e8eedb1607e57b6e81e7a6f3db4523`
- Baseline tree: `1f8a4c387b4715443df31457fa498c7463b1f23f`
- Branch: `docs/v2-m0-01-versioned-baseline`
- Pull request: `#95`
- Timestamp: `2026-08-24T14:03:25+08:00`
- Environment: `Microsoft Windows 10.0.19045 AMD64`; Node.js `24.12.0`; Git `2.52.0.windows.1`; Eclipse Temurin `17.0.19+10`
- Android, emulator, KVM, device, fuzz, benchmark and APK execution: `not_run`

## Identity Evidence

- Development tuple: `5fb0205d9fc0c2523cd33734145bf23a901303f4f563eef866e5883ca81fd4c2`
- Tuple kind: `development_baseline`
- Releasable: `false`
- Identity policy SHA-256: `cb53c93eab5f64aad0eb4825195ac2d3fb9b54e78f47c65e865b5e9369bcd416`
- Post-freeze policy SHA-256: `8cc5fae5da1de7c8be07deaea27817e469887035f23a8ff8130881a277b14329`
- Source tree identities:
  - root: `1f8a4c387b4715443df31457fa498c7463b1f23f`
  - host: `0785d697f3e406219462c1cd3e14fbcd77d46cca`
  - runtime: `38c07c0954127fb60f990e03974b2f457ab646bf`
  - fixtures: `8d23ea087ce17f0dd935acc5d0516ea1270e69fd`
  - integration_tests: `cf54d499a239fc79727120286cde84b2eb1e6288`
  - tools_validation: `c130a3f03ec4eaafdbc421524433570f73e2deb7`
  - benchmarks: `c8bdf317566f151c942da932a4e2bb514044354e`
  - distribution: `9330b45cdd46cccf803e80f24e3dcf62411887c8`

## Commands and Results

| Command | Exit code | Result |
| --- | ---: | --- |
| `node tools/governance/validate-project-package.mjs` | 0 | 39 task cards, 11 core docs and 19 ADRs valid |
| `node tools/governance/validate-v0-2-package.mjs --base-ref origin/main` | 0 | Nine V2 tasks, tuple, DAG, links and governance-only diff valid |
| `node tools/governance/validate-v0-2-package.mjs --self-test --base-ref origin/main` | 0 | 146 named mutations rejected |
| `node tools/governance/verify-m3-07-high-benchmark-contract.mjs` and `--self-test` | 0 | Historical boundary valid; 30 negative cases rejected |
| `node tools/governance/verify-m3-08-startup-stability-contract.mjs` and `--self-test` | 0 | Historical contract valid; 46 package/diff mutations rejected and arithmetic positives accepted |
| `node tools/governance/verify-m3-09-startup-attribution-contract.mjs` and `--self-test` | 0 | Historical contract valid; 58 named mutations rejected |
| `node tools/governance/verify-m3-11-canonical-artifact-contract.mjs` and `--self-test` | 0 | Historical contract valid; 26 mutations rejected |
| `node tools/governance/verify-m3-13-diagnostic-identity-contract.mjs` and `--self-test` | 0 | Historical terminal identity valid; 75 named mutations rejected |
| `node tools/governance/verify-m3-15-terminal-disposition-contract.mjs` and `--self-test` | 0 | `STOP_CURRENT_V0_1_RELEASE_LINE` retained; 102 named mutations rejected |
| `node tools/governance/test-handoff-validator.mjs` | 0 | Three positive fixtures accepted and 22 negative cases rejected |
| `node .agents/skills/coordinate-project-handoff/scripts/validate-handoff.mjs HandOff.md --strict --allow-pending-clean --allow-pending-branch` | 0 | Schema 2 development state valid for merger preparation |
| `git diff --check` and `git diff --cached --check` | 0 | No whitespace error after final correction |
| `git fsck --full` | 0 | Object database valid; only unreachable validator/test objects reported |
| Changed-file content, secret and path scan | 0 | 44 files checked; no documentation placeholder, user absolute path, replacement character or common secret marker |
| Product-path diff scan against `origin/main` | 0 | No Host, Runtime, fixture, integration-test, benchmark, distribution, Gradle product or APK artifact change |

The obsolete repository-bootstrap-only `--require-governance-only` mode is intentionally not a v0.2 acceptance command because the accepted baseline already contains the v0.1 product implementation. The v0.2 validator instead checks the exact base-to-head changed-path set and rejects any product change in V2-M0-01.

## Result

`PASS`. This evidence validates a governance and release-line planning package only. It does not claim that `v0.2.0` or any Release Candidate has passed Android, performance, compatibility, security or release validation.
