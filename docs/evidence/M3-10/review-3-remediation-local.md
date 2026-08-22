# M3-10 third-review remediation evidence

- Timestamp: `2026-08-21T13:04:07+08:00`
- Reviewed evidence successor: `9151097ad2b8feb7985e81da46df436e6886223a`
- Remediated implementation freeze: `cbf064301063d64928bf8f0af6820956ecaf3e1f`
- Environment: Windows 10 amd64; Temurin 17.0.19; Gradle 9.5.0; Node.js 24.12.0; project-local ignored Gradle/Android toolchains
- Dynamic scope: no workflow, API 36, KVM, emulator, ARM, benchmark or M3-05 execution

## Independent review 3 result

The third independent read-only review returned `FAIL — P0=0/P1=3/P2=1`. The open findings were an unfixed product tuple at the complete-verifier boundary; caller-controlled lock/current-job inputs; `profileV3Verified` omitted from the true-value gate; and incomplete metadata, probe-adjacency and cleanup failure-injection mutations. Both canonical workflows remained absent and the unique API 36 eligibility was not consumed.

## Remediation

- Every campaign, result, package and GitHub identity now requires the exact M3-11 product tuple `883da673d3bced1ec93f11323fe63152c1007112d08c46643976c70397d0b8dd`; a consistent alternative tuple is rejected.
- The runner no longer accepts profile, Release/tool, environment or current-job lock inputs from its caller. It copies the three reviewed repository locks, and package verification requires byte equality with those tracked files plus their reviewed SHA-256 values. The Release lock names and fixes its exact production-source head.
- Before the first install, the runner directly requests the official GitHub Actions jobs API page for `GITHUB_RUN_ID`, requires one complete page with fewer than 100 jobs, selects exactly one canonical current job, derives its job ID from that object, and archives both raw and normalized bytes. The token is never written or logged.
- The exact profile report now requires `profileV3Verified=true`; a named false-v3 report is rejected.
- The Kotlin independent verifier rejects try start/end/handler, debug address/line/local name/type/signature, parameter-name and all p/h adjacency-family mutations through the same production comparison helpers.
- Cleanup command-result validation is factored into the executed runner path and rejects nonzero/malformed `pm path`, nonzero/inexact uninstall, failed remote listing and residual `m3-10-*` files.

## Fixed hashes

| Item | SHA-256 |
|---|---|
| diagnostic runner | `33241d6d619ab493e32049c2d0b834422da5552af3e31f2715738f4acb044a11` |
| complete verifier | `bc66b0aa37ba568cd67baca6c67f609e62cac8fff15db26051a232ec181b7764` |
| profile-freeze governance validator | `16077801a442f5765a4b716175a0a6703f109585f83f6d3e604a9baa6ab14664` |
| Release/tool lock | `9b84d0005892f8a77c2b4ca5041acc29d563cee0edec2ab7eb17488cbe2caead` |
| Kotlin actual-byte verifier | `ce238bf4d5c6b90694664798817a91facd417b1f0e6b7615be3e8577454095c7` |

## Bounded commands and results

| Command | Result |
|---|---|
| Node syntax for runner, complete verifier and governance validator | PASS |
| `verify-m3-10-startup-attribution.mjs self-test` | PASS; 39 report/result/cleanup/environment/official-page/tracked-lock/tuple/GitHub mutations rejected plus 4 threshold cases |
| `run-m3-10-startup-attribution.mjs --cleanup-self-test` | PASS; 8 command-result failure injections rejected |
| `:host:container:m310MetadataSelfTest --offline --no-daemon --no-configuration-cache` | PASS; 9 metadata mutations and all p/h adjacency families rejected |
| `verify-m3-10-startup-attribution.mjs profile-self-test ... --profile-report ...` | PASS; 17 actual byte/signer/surface/tool/report mutations rejected |
| `verify-m3-10-profile-freeze.mjs --self-test --base-ref 9d3fc3a...` | PASS; workflows absent and production observer absent |
| project governance and `git diff --check` | PASS |

The first metadata command inherited the host Java 8 and stopped before task execution; a second used the wrong offline cache and stopped while resolving already-pinned dexlib2. The accepted bounded command used the project-pinned JDK and existing dependency cache and passed. No dependency was downloaded, and neither failed command produced acceptance evidence.

## Gate

This is still an implementation/evidence candidate. A fourth independent read-only review must return exactly `P0=0/P1=0/P2=0` before either canonical workflow can be added. API 36, ARM and M3-05 remain unexecuted.
