# M3-11 canonical startup artifact provenance

- Task: `M3-11`, Issue `#71`
- Branch: `docs/m3-11-canonical-startup-artifacts`
- Base: `main@3458338e7886ac3fba8383bac47a0b655ca44533`
- Rejected initial freeze/evidence: `baaabb6f35b022b5d02bf1d2d17650e2b07ae84b` / `8f7a5f5e4cc006bc565ab0e69c2c917d9b87d41c`
- Replacement implementation freeze: `f16f7d4808925030f0cd7c74df89d91ae3b713df`
- Final independently reviewed implementation head: `a5397888ff7eeb9571f64d06dfc10e8edef7f37c`
- Evidence timestamp: `2026-08-19T03:55:35Z`
- Dynamic execution: none; no Gradle build, benchmark, KVM, emulator, ARM or canonical diagnostic workflow ran

## Official source

The canonical pair is the pair actually installed and measured by PR #63's first-and-only ADR 0015 API 36 A/B failure. The artifact manifest, both campaign reports and repeatability aggregate prove the selected `java-single-dex` pair produced the application P50 budget failures. It is not a locally rebuilt M3-01 or M3-10 fixture.

- PR: `#63`
- Exact head: `1c030334d607bc10054b876dd969ea8048725cb3`
- Workflow/run: `M0-05 Linux KVM` / `31931428130`
- Event/attempt/conclusion: `push` / `1` / `failure`
- Job: `95126754768`, `API 36 x86_64 (Linux/KVM)`
- Artifact: `9260244215`, `m0-05-api-36-x86_64-evidence`
- Official artifact size/digest: `3316848` bytes / `98c5cedce457775e4f4365226647b1bf1d49cb3f824d07ae5f9450c31803d5ae`
- Boot ID hash prefix retained by the report: `a3cf719802bc`

The GitHub run and artifact APIs were read directly. The artifact was downloaded once into the ignored repository-relative directory `build/m3-11/provenance-artifact/`; no artifact byte is added to Git.

## Canonical immutable pair

| Role | Repository-relative path inside artifact | Bytes | SHA-256 |
|---|---|---:|---|
| baseline | `benchmarks/android/build/reports/performance/apks/java-single-dex-baseline.apk` | 29962 | `4607d3289e1fc3bd95282ab47791ec810a5d2d3ac0a69fc0f91388901e412dcf` |
| protected | `benchmarks/android/build/reports/performance/apks/java-single-dex-protected.apk` | 1287876 | `1eb159d7f0149a943fb2e1c4d8467f283d1cfbbfad670628402cfb0cd23390d9` |

Both files pass pinned build-tools `36.1.0` `apksigner verify --verbose --print-certs`, use APK Signature Scheme v3, have one signer, and share the synthetic signer certificate SHA-256 prefix `0696de7d3f22`. The full signer digest is deliberately not copied into governance evidence.

The canonical tuple input is the 218-byte UTF-8 JSON below, without BOM or trailing newline:

```json
{"schemaVersion":1,"fixtureId":"java-single-dex","baselineSha256":"4607d3289e1fc3bd95282ab47791ec810a5d2d3ac0a69fc0f91388901e412dcf","protectedSha256":"1eb159d7f0149a943fb2e1c4d8467f283d1cfbbfad670628402cfb0cd23390d9"}
```

Its SHA-256 product tuple is `883da673d3bced1ec93f11323fe63152c1007112d08c46643976c70397d0b8dd`.

## Failure mapping

The actual artifact files bind the selected pair to the retained rejection:

- `benchmark-artifact-manifest.json`: SHA-256 `d2166e07f5e959a9868c0da4ddd05a19e40f961559bec4367c8e8c00fba56089`.
- `benchmark-repeatability.json`: SHA-256 `81b0982e4c5b6ae5a34d71218df6602cd44706d879c3909400a2809e5e4f55d8`.
- campaign A report: SHA-256 `f7528353cb5a3b4c8114546d4dcd53ab1e3efd7420e210abe7eb51067a8ddd2b`.
- campaign B report: SHA-256 `6845d3c9d7eba0d84aefe0d05da485e87f754f5fe63e7a57ba6807159d9a0979`.
- `java-single-dex/processToApplicationOnCreateMs/deltaP50`: campaign A `331 ms`, campaign B `432 ms`; both exceed `300 ms`.
- Cross-campaign variation is `0.30513595166163143`, above limit `0.1`, with `pass=false`. The result is therefore not described as stable.
- Interactive delta P50 is `168/376 ms`; `432 ms` is not an interactive endpoint.

## Fail-closed boundary

- These signed APKs are the immutable originals. M3-10 may copy and inspect them but may not rebuild, normalize, repackage or replace either original and still claim the same tuple.
- The ephemeral PR #63 signing private key was correctly absent from the artifact and must not be recovered or reconstructed. This task does not claim that a same-signer instrumented derivative is currently possible.
- M3-10 remains blocked until its independently reviewed design proves an installable profile derivation from these exact originals without changing their claimed provenance or security semantics. A different fixture build, signer, authenticated container or manifest is a different product tuple and requires a new ADR decision; it cannot replace the first-and-only diagnostic eligibility.
- If the official artifact expires and no exact-hash retained copy is available, validation fails closed. Rebuilding byte-similar APKs is forbidden.

## Read-only verification performed

| Command | Exit | Result |
|---|---:|---|
| `gh run view 31931428130 --repo xiaokh31/androidAppHardening --json ...` | 0 | official head/run/job/attempt/status matched |
| `gh api repos/xiaokh31/androidAppHardening/actions/runs/31931428130/artifacts` | 0 | one unexpired artifact; ID, size and digest matched |
| `gh run download 31931428130 --name m0-05-api-36-x86_64-evidence --dir build/m3-11/provenance-artifact` | 0 | official artifact downloaded to ignored project storage |
| `Get-FileHash -Algorithm SHA256 <canonical APK>` | 0 | both size/hash pairs matched the artifact manifest |
| pinned `apksigner verify --verbose --print-certs <canonical APK>` | 0 | both APKs valid v3, one shared synthetic signer |
| `node --check tools/governance/verify-m3-11-canonical-artifact-contract.mjs` | 0 | syntax PASS |
| `node tools/governance/verify-m3-11-canonical-artifact-contract.mjs --self-test --artifact-root build/m3-11/provenance-artifact --base-ref 3458338e7886ac3fba8383bac47a0b655ca44533` | 0 | actual two APKs plus manifest/repeatability/A/B reports matched; 26 named lock/evidence/path mutations rejected; 19-file governance-only committed diff |
| `node tools/governance/verify-m3-07-high-benchmark-contract.mjs [--self-test]` | 0 | existing HIGH contract preserved |
| `node tools/governance/verify-m3-08-startup-stability-contract.mjs [--self-test]` | 0 | existing stability contract preserved after dependency update |
| `node tools/governance/verify-m3-09-startup-attribution-contract.mjs [--self-test]` | 0 | 58/58 mutations rejected after canonical dependency update |
| `node tools/governance/validate-project-package.mjs` | 0 | 36 task cards, 11 core docs and 16 ADRs |
| `node .agents/skills/coordinate-project-handoff/scripts/validate-handoff.mjs HandOff.md --strict` | 0 | strict active-task handoff PASS |
| `git diff --check` | 0 | PASS |

Published reviewed head `14bf68a0b2d80b7086bb060141f81224b2d4aca4` passed Build run `32214654539` on Ubuntu job `95953827223` and Windows job `95953827229`, plus Governance run `32214654687` on Ubuntu job `95953782107` and Windows job `95953782046`. The automatically triggered equivalence/fuzz workflows were cancelled as out of scope and are not M3-11 evidence.

The initial governance-only base diff contained 17 files and no Runtime, Host, fixture, benchmark or canonical diagnostic workflow implementation, but independent review rejected its semantic selection with `P0=0/P1=2/P2=2`. The replacement freeze contains 19 governance/evidence files and still contains no Runtime, Host, fixture, benchmark or canonical diagnostic workflow implementation. Its current hashes are:

| File | SHA-256 |
|---|---|
| `docs/evidence/M3-11/canonical-artifact-lock.json` | `8b87bf1fae35fbdc5e89217b9806cbeecb67de8c54b85aa604365d6054ee7163` |
| `docs/tasks/M3-11-canonical-startup-artifact-contract.md` | `5e506bd66bbbdbdf988a0971e27b6a83e0959db7a9b0a41cf21e8a96c4a4e006` |
| `docs/tasks/M3-10-startup-attribution-diagnostic.md` | `75191f125e07db23e9367a8f249de4b2831b61ec2d4d3ed087a9564d3357e3f3` |
| `tools/governance/verify-m3-11-canonical-artifact-contract.mjs` | `829fc3203300f4b383e2acc264ee7e3bf5c3d408bf3c2d9f5e9663ee901bea0d` |
| `docs/adr/0016-end-to-end-startup-attribution-boundary.md` | `cd12665446fde02ab110391c809283e81247f2cdc31eaa34763f40a6dfc2bad9` |

This provenance record fixes inputs only. It does not approve the rejected M3-10 candidate, create either canonical workflow, consume the unique API 36 diagnostic, resume M3-05, or authorize ARM. Final implementation head `a5397888ff7eeb9571f64d06dfc10e8edef7f37c` passed independent review with `P0=0/P1=0/P2=0`; branch publication and draft PR #72 therefore satisfy the user's all-zero condition.
