---
id: M3-11
title: "Canonical startup diagnostic artifact contract"
milestone: M3
status: planned
owner_role: qa-governance-agent
depends_on:
  - M3-09
required_skills:
  - plan-apk-hardening-change
  - coordinate-project-handoff
security_sensitive: true
---

## Goal

Fix the immutable origin, byte sizes, SHA-256 values and product tuple of the baseline/protected APKs that a later ADR 0016 diagnostic must observe, using the retained PR #63 failure bytes rather than a newly constructed fixture.

## Background

The first M3-10 candidate rebuilt a new `m310Base` fixture and called it the original. Independent review rejected that substitution because it changed the APK size, manifest and lifecycle surface relative to the bytes that produced the retained M3-05 failure. PR #63 already archived the six APKs actually installed by its first-and-only API 36 A/B run. This task selects only its `kotlin-multidex` pair and fixes their provenance before M3-10 resumes.

## Inputs

- Accepted ADR 0016 and completed M3-09.
- PR #63 exact head `1c030334d607bc10054b876dd969ea8048725cb3`.
- Run `31931428130`, attempt `1`, job `95126754768`, artifact `9260244215`.
- Artifact `m0-05-api-36-x86_64-evidence`, official size `3316848` and SHA-256 `98c5cedce457775e4f4365226647b1bf1d49cb3f824d07ae5f9450c31803d5ae`.
- The two signed `kotlin-multidex` APK files inside that artifact.

## Expected Outputs

- Machine-readable `canonical-artifact-lock.json` with exact official provenance, paths, sizes, APK hashes and tuple serialization.
- ADR 0016 and M3-10 contract language forbidding reconstructed originals or substitute tuples.
- A governance verifier with lock mutation tests and an optional actual-byte verification mode.
- Read-only provenance evidence and an updated HandOff; no APK is committed.

## In Scope

- Read official GitHub run/job/artifact metadata and download the one retained artifact into ignored project storage.
- Hash the two actual APKs and verify their APK signatures with the pinned toolchain.
- Define exact tuple serialization, availability failure semantics and M3-10 dependency.
- Update governance/task/dependency documentation.

## Out of Scope

- Runtime, Host, fixture, benchmark, profile observer, ASM, APK transformation or workflow implementation.
- Rebuilding, resigning or changing either canonical APK.
- KVM, emulator, physical device, benchmark, M3-05 or canonical diagnostic execution.
- Solving the unavailable ephemeral signer-key problem or approving a profile-derivation mechanism.
- Resuming or merging PR #63.

## Implementation Decisions

- The canonical baseline is exactly SHA-256 `f666ea37d4f5dcc96fb994066ab97659a11119a33d637606b5cc0636efdf4c36`, size `30022`.
- The canonical protected APK is exactly SHA-256 `f265688bd8eea4f85def8c4edf50aae14e287688523e2ccafdf9ca04e891b658`, size `1287876`.
- The product tuple is SHA-256 `a7131f59ab69769c3ebe3dcc4d7295b3e11ae84c823701f6985c953803068c4a` over the exact 218-byte UTF-8 JSON fixed in the lock, with no BOM or trailing newline.
- Both originals are the signed v3 installation bytes from the retained failure. Their synthetic private key is not retained and no same-signer derivative claim is made.
- A file with identical semantics but different bytes is not the canonical original. Rebuild, ZIP normalization, re-signing, container regeneration or manifest rewriting changes the tuple.
- Official-artifact expiration or loss of every exact-hash retained copy blocks M3-10. It never authorizes regeneration.
- The rejected M3-10 candidate and its newly built original hashes are historical non-canonical evidence only.

## Public Interfaces

- `docs/evidence/M3-11/canonical-artifact-lock.json`
- `node tools/governance/verify-m3-11-canonical-artifact-contract.mjs`
- `node tools/governance/verify-m3-11-canonical-artifact-contract.mjs --self-test`
- `node tools/governance/verify-m3-11-canonical-artifact-contract.mjs --artifact-root build/m3-11/provenance-artifact`
- No product interface changes.

## Security Constraints

- No private key, keystore, password, token, complete signer digest, customer path, customer APK or plaintext DEX is committed or logged.
- The canonical originals remain read-only. Any derived file uses a separate path and identity.
- A profile mechanism may not weaken signer, Guard, authenticated container, memory, cleanup or lifecycle controls to accommodate unavailable signing material.
- Hash-shaped strings and artifact self-reports do not replace direct byte hashing.

## Compatibility Requirements

- The lock makes no new API/ABI claim. It only identifies the API 36 x86_64 bytes behind an already retained result.
- minSdk, targetSdk, manifest, signer, DEX, native entries and authenticated container semantics are properties to be preserved or explicitly rejected by the later M3-10 verifier.
- No claim is made for API 29 or ARM.

## Acceptance Criteria

- Official source tuple and both actual APK byte hashes match the lock.
- The tuple recomputes exactly and mutation tests reject source, hash, size, path, signer-prefix, retention and tuple changes.
- ADR 0016, M3-10, M3-11, M3-05, README, ROADMAP, PROJECT_PLAN and INDEX agree that M3-10 depends on this lock and may not reconstruct originals.
- Base-to-head diff contains no Runtime, Host, fixture, benchmark or diagnostic workflow implementation.
- Governance, strict HandOff, Node syntax, actual-byte verification and diff checks pass.
- Independent read-only review returns `P0=0/P1=0/P2=0` before merge.

## Required Tests

- Positive lock schema and tuple recomputation.
- Named mutations for source head/run/job/artifact, APK path/size/hash, signer summary, tuple serialization/hash and fail-closed retention.
- Actual-file regular-file, size and SHA-256 checks against the ignored downloaded artifact.
- Documentation consistency, governance, strict HandOff and zero-production-diff checks.

## Required Evidence

- Exact base/head, branch, Issue, timestamp, OS/Node/toolchain and commands with exits.
- Official PR/run/job/attempt/artifact metadata and archive digest.
- Both APK paths, sizes, SHA-256 values, v3/signer-count result and tuple SHA-256.
- Explicit no-build/no-device/no-benchmark/no-workflow statement.
- Independent review conclusion and exact-head Ubuntu/Windows Build/Governance before merge.

## Likely Files

- `docs/adr/0016-end-to-end-startup-attribution-boundary.md`
- `docs/tasks/M3-10-startup-attribution-diagnostic.md`
- `docs/tasks/M3-11-canonical-startup-artifact-contract.md`
- `docs/evidence/M3-11/`
- `tools/governance/verify-m3-11-canonical-artifact-contract.mjs`
- `tools/governance/validate-project-package.mjs`
- `docs/tasks/INDEX.md`
- `docs/ROADMAP.md`
- `docs/PROJECT_PLAN.md`
- `README.md`
- `HandOff.md`

## Dependencies and Blockers

M3-11 depends only on completed M3-09. M3-10 and M3-05 stay blocked. M3-10 may resume only after this contract merges and an implementation freeze proves a valid profile strategy for the exact locked originals; M3-11 does not solve or waive the missing ephemeral signer-key constraint.

## Agent Handoff Requirements

Use branch `docs/m3-11-canonical-startup-artifacts`, Issue #71 and one unique PR. Record the exact lock, actual-byte checks, zero-production diff, independent review and exact-head Ubuntu/Windows Build/Governance. State that no build, benchmark, KVM, emulator, ARM or canonical diagnostic ran and no APK was committed.
