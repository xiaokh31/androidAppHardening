---
id: M3-10
title: "ADR 0016 startup attribution diagnostic implementation"
milestone: M3
status: planned
owner_role: runtime-security-agent
depends_on:
  - M3-09
  - M3-11
required_skills:
  - plan-apk-hardening-change
  - implement-runtime-protection
  - validate-protected-apk
  - coordinate-project-handoff
security_sensitive: true
---

## Goal

Implement the real bounded ADR 0016 end-to-end startup attribution diagnostic for the retained failing `kotlin-multidex` bytes. It must identify exactly one eligible owner or return `UNATTRIBUTED`; it may not substitute a rebuilt fixture, repeat an invalid run, weaken a budget or change production security behavior.

## Background

M3-09 accepted the attribution model. M3-11 then fixed the canonical originals to the exact signed baseline/protected APKs measured by PR #63. The first rejected M3-10 candidate rebuilt an `m310Base` fixture, so none of its candidate original APK identities are eligible. M3-09's synthetic validator and M3-11's provenance lock do not themselves implement probes or authorize a diagnostic.

## Inputs

- Accepted ADR 0016, completed M3-09 and merged M3-11 provenance contract.
- `docs/evidence/M3-11/canonical-artifact-lock.json`.
- PR #63 exact head `1c030334d607bc10054b876dd969ea8048725cb3`, run `31931428130`, job `95126754768`, artifact `9260244215`.
- Canonical baseline `f666ea37d4f5dcc96fb994066ab97659a11119a33d637606b5cc0636efdf4c36`, `30022` bytes.
- Canonical protected `f265688bd8eea4f85def8c4edf50aae14e287688523e2ccafdf9ca04e891b658`, `1287876` bytes.
- Product tuple `a7131f59ab69769c3ebe3dcc4d7295b3e11ae84c823701f6985c953803068c4a`.
- Pinned API 36 revision 2 x86_64 image and Emulator 37.1.11.

## Expected Outputs

- A reviewed, test/profile-only measurement mechanism for outer `p0..p15` and protected inner `h0..h8` that starts from the exact canonical pair.
- Exact original baseline/protected files plus separately identified installable profile baseline/protected files; the originals remain byte-for-byte unchanged.
- Byte-level verifier for APK/DEX/signature/manifest/resource/native/security/lifecycle equivalence and exact permitted probe locations.
- Fail-closed diagnostic package with both reversed campaigns, raw observations, calibration, nine-owner reconciliation, cleanup and immutable file manifest.
- Canonical raw diagnostic and separate terminal evidence workflows only after the implementation freeze passes independent review.

## In Scope

- Test/profile-only probes or tracing needed for ADR 0016, if their derivation from the exact originals is technically and cryptographically valid.
- Direct parsing and hashing of the exact original and derived APKs plus Release/distribution surfaces.
- One API 36 x86_64 job and boot with `A=baseline_then_protected`, `B=protected_then_baseline`, and exactly `5+15` starts per mode/campaign.
- Official post-terminal GitHub run/job/artifact enumeration and exact task/product binding.

## Out of Scope

- Rebuilding an original APK from source, even at the same commit.
- Re-signing an original and retaining the same identity claim; recovering or persisting the deleted PR #63 private key.
- Production Runtime optimization, Release observer APIs, environment/file/intent switches or security-control changes.
- M2-10 retry, M3-05 full matrix, API 29, ARM, another fixture, a third campaign, or PR #63 resumption.

## Implementation Decisions

- M3-11's two hashes, sizes, source tuple and product tuple are immutable inputs. Direct byte hashing is mandatory before any derivation or run.
- The originals are signed v3 installation bytes. Any profile artifact has a separate hash and signer identity and may not be called an original.
- Because the ephemeral original private key is absent, the implementation freeze must explicitly prove how profile APKs remain installable and preserve signer/Guard/container semantics. If it cannot, M3-10 returns blocked and no workflow is created.
- Profile generation must be deterministic and post-build. It may alter only exact reviewed probe call sites and signing material strictly required for an independently approved test derivative; every resulting security-semantic change must be enumerated and cannot be normalized away.
- Baseline receives no synthetic Factory. No candidate `m310Base` Activity, manifest entry or keep rule may enter the canonical original comparison.
- Each campaign retains exactly fifteen ordinals after five warmups. P50 is sorted element eight; omissions, replacements, duplicates, reordering and retries are forbidden.
- Nine signed owner contributions reconcile per ordinal before percentiles. Eligibility remains 300 ms reproduction, positive 30 ms minimum, at most 10% cross-campaign variation and at least 50% share in both campaigns; zero or multiple owners produce `UNATTRIBUTED`.
- Canonical workflows are added only after an exact implementation freeze passes independent review `P0=0/P1=0/P2=0`. Invalid evidence consumes eligibility for the tuple and cannot be replaced.

## Public Interfaces

- `node tools/validation/verify-m3-10-startup-attribution.mjs self-test`
- `node tools/validation/verify-m3-10-startup-attribution.mjs surface ...`
- `node tools/validation/verify-m3-10-startup-attribution.mjs package ...`
- `node tools/validation/verify-m3-10-startup-attribution.mjs github-evidence ...`
- `build/m3-10/diagnostic-package/` with fixed schema and manifest.
- No product public or package-private interface changes.

## Security Constraints

- Signer verification, authenticated container, metadata binding, Guard ordering, memory controls, mappings, cleanup and four-ABI behavior remain unchanged.
- No private key, keystore, password, token, complete signer digest, customer path, customer APK or plaintext DEX enters evidence.
- The verifier cannot ignore, normalize or broadly delete manifest, signature, container, DEX method or observer surfaces to manufacture equivalence.
- A missing original, hash drift, signer mismatch, invalid derivative, event mismatch, cleanup failure or sensitive scan blocks before owner selection.

## Compatibility Requirements

- Diagnostic execution is API 36 revision 2 x86_64 only and makes no compatibility claim.
- Original bytes and their minSdk, targetSdk, Release/R8, manifest, resources, native entries, signer and authenticated container identities are directly verified.
- Derived profile differences are exact, minimal and explicit. Different reconstructed product inputs are not compatible substitutes.

## Acceptance Criteria

- M3-11 actual-byte validator passes on both canonical originals before profile creation and again before execution.
- Independent review approves the derivation/signing/security model and returns `P0=0/P1=0/P2=0` before either workflow exists.
- Product Release AARs, fixture APKs, CLI and distribution contain no observer, probe call, diagnostic keep rule or activation surface.
- The verifier accepts one canonical four-APK package and rejects fixed APK/DEX/signature/manifest/resource/native/hash/event/calibration/identity/cleanup mutations.
- The first-and-only diagnostic yields exactly one eligible owner or terminal `UNATTRIBUTED`; invalid execution blocks without replacement.
- Terminal evidence proves exact head, run/job/attempt/boot, task key, canonical product tuple, artifact bytes, pagination and uniqueness.
- README, INDEX, ADR, evidence and HandOff are synchronized; M3-05 remains blocked pending any separately reviewed owner remediation.

## Required Tests

- M3-11 lock plus actual original-byte verification.
- Probe/tracing boundary tests for every required timestamp, order, zero duration and reconciliation.
- Exact four-APK ZIP/DEX/manifest/resource/native/signature/security comparison, including rebuilt-original and same-semantics/different-bytes rejection.
- Two-campaign cardinality/order/P50/P95, owner arithmetic, thresholds and zero/multiple-owner mutations.
- Raw calibration/event, Release pollution, GitHub history/pagination, sensitive output, artifact and cleanup mutations.

## Required Evidence

- Exact commits, canonical source lock/tuple, commands/exits, OS/toolchain and all artifact hashes.
- Four APK files and exact structural/signature/probe manifest; raw A/B calibration, lifecycle/security events and observations.
- Independent review findings and closure.
- Raw official paginated runs/jobs/artifacts bytes and hashes after terminal execution.

## Likely Files

- `tools/validation/verify-m3-10-startup-attribution.mjs`
- `tools/validation/run-m3-10-startup-attribution.mjs`
- profile/test-only source and build configuration selected by the reviewed design
- `.github/workflows/m3-09-startup-attribution.yml`
- `.github/workflows/m3-09-startup-attribution-evidence.yml`
- `docs/evidence/M3-10/`

## Dependencies and Blockers

M3-10 depends on merged M3-11. It is blocked until exact canonical bytes are available and the derivation/signing design passes independent review. M3-05 remains blocked until M3-10 identifies an owner and a separate owner-specific remediation is reviewed and merged.

## Agent Handoff Requirements

Use Issue #70 and one M3-10 implementation PR rebased on merged M3-11. Record canonical input verification, derivation/signing decision, exact implementation/evidence commits, independent review, first-and-only result, terminal API evidence and cleanup. Never report a rebuilt fixture as an original.
