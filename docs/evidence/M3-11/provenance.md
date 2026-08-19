# M3-11 canonical startup artifact provenance

- Task: `M3-11`, Issue `#71`
- Branch: `docs/m3-11-canonical-startup-artifacts`
- Base: `main@3458338e7886ac3fba8383bac47a0b655ca44533`
- Evidence timestamp: `2026-08-19T03:23:01Z`
- Dynamic execution: none; no Gradle build, benchmark, KVM, emulator, ARM or canonical diagnostic workflow ran

## Official source

The canonical pair is the pair actually installed and measured by PR #63's first-and-only ADR 0015 API 36 A/B failure. It is not a locally rebuilt M3-01 or M3-10 fixture.

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
| baseline | `benchmarks/android/build/reports/performance/apks/kotlin-multidex-baseline.apk` | 30022 | `f666ea37d4f5dcc96fb994066ab97659a11119a33d637606b5cc0636efdf4c36` |
| protected | `benchmarks/android/build/reports/performance/apks/kotlin-multidex-protected.apk` | 1287876 | `f265688bd8eea4f85def8c4edf50aae14e287688523e2ccafdf9ca04e891b658` |

Both files pass pinned build-tools `36.1.0` `apksigner verify --verbose --print-certs`, use APK Signature Scheme v3, have one signer, and share the synthetic signer certificate SHA-256 prefix `0696de7d3f22`. The full signer digest is deliberately not copied into governance evidence.

The canonical tuple input is the 218-byte UTF-8 JSON below, without BOM or trailing newline:

```json
{"schemaVersion":1,"fixtureId":"kotlin-multidex","baselineSha256":"f666ea37d4f5dcc96fb994066ab97659a11119a33d637606b5cc0636efdf4c36","protectedSha256":"f265688bd8eea4f85def8c4edf50aae14e287688523e2ccafdf9ca04e891b658"}
```

Its SHA-256 product tuple is `a7131f59ab69769c3ebe3dcc4d7295b3e11ae84c823701f6985c953803068c4a`.

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
| `node tools/governance/verify-m3-11-canonical-artifact-contract.mjs --self-test --artifact-root build/m3-11/provenance-artifact` | 0 | 12/12 mutations rejected and both actual files rehashed |
| `node tools/governance/verify-m3-07-high-benchmark-contract.mjs [--self-test]` | 0 | existing HIGH contract preserved |
| `node tools/governance/verify-m3-08-startup-stability-contract.mjs [--self-test]` | 0 | existing stability contract preserved after dependency update |
| `node tools/governance/verify-m3-09-startup-attribution-contract.mjs [--self-test]` | 0 | 58/58 mutations rejected after canonical dependency update |
| `node tools/governance/validate-project-package.mjs` | 0 | 36 task cards, 11 core docs and 16 ADRs |
| `node .agents/skills/coordinate-project-handoff/scripts/validate-handoff.mjs HandOff.md --strict` | 0 | strict active-task handoff PASS |
| `git diff --check` | 0 | PASS |

This provenance record fixes inputs only. It does not approve the rejected M3-10 candidate, create either canonical workflow, consume the unique API 36 diagnostic, resume M3-05, or authorize ARM.
