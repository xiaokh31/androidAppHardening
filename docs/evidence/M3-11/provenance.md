# M3-11 canonical startup artifact provenance

- Task: `M3-11`, Issue `#71`
- Branch: `docs/m3-11-canonical-startup-artifacts`
- Base: `main@3458338e7886ac3fba8383bac47a0b655ca44533`
- Implementation freeze: `baaabb6f35b022b5d02bf1d2d17650e2b07ae84b`
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

The committed governance-only base diff contains 17 files and no Runtime, Host, fixture, benchmark or canonical diagnostic workflow implementation. Key frozen file hashes are:

| File | SHA-256 |
|---|---|
| `docs/evidence/M3-11/canonical-artifact-lock.json` | `0af157ca7c08123d303ec337a81cb9b6b76971ad09746c97faa6b4f4fa03249a` |
| `docs/tasks/M3-11-canonical-startup-artifact-contract.md` | `804e28415102fc4cc2d874a37238958b04857ec3248165f63914728c41c88336` |
| `docs/tasks/M3-10-startup-attribution-diagnostic.md` | `d22ad9fed3ceb219c889517cfe51ee19bfc014a6204123df0eb0b361c8d57f60` |
| `tools/governance/verify-m3-11-canonical-artifact-contract.mjs` | `853a9f8523bbdd5e89ef65ecc76fcd61624eb22eb13b6ec1cffd6b71f0721f6e` |
| `docs/adr/0016-end-to-end-startup-attribution-boundary.md` | `377b1c48f0572f2b519f002aa80c271b6fdd3a7dcc51f3ae2c0f57c7f44987c5` |

This provenance record fixes inputs only. It does not approve the rejected M3-10 candidate, create either canonical workflow, consume the unique API 36 diagnostic, resume M3-05, or authorize ARM.
