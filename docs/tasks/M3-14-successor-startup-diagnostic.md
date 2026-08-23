---
id: M3-14
title: "ADR 0018 successor startup diagnostic implementation"
milestone: M3
status: planned
owner_role: runtime-security-agent
depends_on:
  - M3-09
  - M3-11
  - M3-12
  - M3-13
required_skills:
  - plan-apk-hardening-change
  - implement-runtime-protection
  - validate-protected-apk
  - coordinate-project-handoff
security_sensitive: true
---

## Goal

Implement the sole ADR 0018 successor startup-attribution diagnostic without retrying M3-10. Freeze and independently review all product bytes, profile derivation, verification, runner, locks and non-executable workflow candidates before publishing or executing either canonical workflow.

## Background

M3-10 run `32554806537` consumed its original identity during provenance preflight before Android setup and produced no artifact. M3-13/ADR 0018 permits exactly one distinct successor because retained official evidence proves zero device observation, but it also fixes `runAttempt=1`, forbids further renewal and requires an independently reviewed workflow-absent implementation freeze. Issue #82 and branch `feat/m3-14-successor-startup-diagnostic` are the only implementation workstream for that successor.

## Inputs

- Contract identity SHA-256 `580560859af80418058a088c6be3f7ab221e0ab37e21d76f19bf9177be35a419`.
- Product tuple SHA-256 `883da673d3bced1ec93f11323fe63152c1007112d08c46643976c70397d0b8dd`.
- M3-12 profile archive: release ID `374769776`, numeric asset ID `524507375`, archive SHA-256 `21816d2a843bb5c59902224c7bf786d546d52b4a5b2d1168ca0c449a2ca27964` and its exact ten-member lock.
- Task key `M3-13-SUCCESSOR-DIAGNOSTIC-V1` and canonical run name `M3-13-SUCCESSOR-DIAGNOSTIC-V1-580560859af80418058a088c6be3f7ab221e0ab37e21d76f19bf9177be35a419-883da673d3bced1ec93f11323fe63152c1007112d08c46643976c70397d0b8dd`.
- Canonical paths `.github/workflows/m3-13-startup-attribution.yml` and `.github/workflows/m3-13-startup-attribution-evidence.yml`.
- API 36 system image revision 2, x86_64 and Emulator `37.1.11`.
- ADR 0016 measurement arithmetic and ADR 0018 execution-identity serialization.

## Expected Outputs

- Deterministic profile-package retrieval and byte-level validation against the M3-11/M3-12 locks; no regeneration or signing material.
- Independently checked profile derivation/equivalence, fixed probe identity and exact signed APK binding.
- Fail-closed API 36 runner with A/B reverse-order campaigns, 5 warmups plus 15 retained samples per mode, raw event/calibration evidence, cleanup proof and sensitive-data scan.
- Terminal verifier binding official run/job/artifact pages, exact package bytes, execution identity, first-and-only history and owner arithmetic.
- Two non-executable workflow-candidate files outside `.github/workflows/`.
- Workflow-absent implementation freeze, local evidence and independent read-only review.
- Only after all-zero review: publish the unchanged workflow-absent freeze, create its unique draft PR, and require exact-head Ubuntu/Windows Build plus Governance to pass. Keep that reviewed/qualified HEAD unchanged until one direct-child publication copies candidate bytes unchanged to the canonical paths, records the pre-run ledger, and consumes exactly one `runAttempt=1` API 36 execution.

## In Scope

- Port the previously reviewed M3-10 diagnostic algorithms onto the ADR 0018 identity and M3-12 retained asset.
- Make product/profile equivalence, probe adjacency, signer binding, security events, package contents, environment, cleanup, GitHub history and terminal evidence independently verifiable.
- Add named mutation tests for every trusted boundary and keep all lengths, ZIP/APK/DEX fields and downloaded bytes bounded.
- Update README, task graph, evidence and HandOff truthfully at each freeze/publication/result boundary.

## Out of Scope

- Retrying or merging M3-10/PR #79.
- Adding either canonical workflow before the independent review reports `P0=0/P1=0/P2=0`.
- More than one successor run, any rerun, replacement result or identity renewal.
- API 29, ARM, physical device, full M3-05 matrix or owner remediation.
- Product Runtime/Host public API changes, security-control removal, budget relaxation, APK rebuilding, profile regeneration or signing-secret recovery.

## Implementation Decisions

- The workflow-absent freeze contains candidates, runner, verifier, locks and evidence but neither canonical workflow path.
- Independent review is read-only and must cover the exact frozen Git object. Any code or contract change after review invalidates the review and requires another frozen review.
- Publication is an exact direct child of the independently reviewed and exact-head Build/Governance-qualified workflow-absent freeze. Before Android setup it proves ancestry, candidate byte hashes, implementation/runner/verifier/lock hashes, qualification evidence and the pre-run execution-identity ledger.
- The diagnostic run name excludes candidate hashes to avoid self-reference; the ledger binds the complete ordered execution identity.
- The workflow checks the complete repository run history for exact path/name/event/branch uniqueness before environment setup, then requires its own run ID and `runAttempt=1`.
- Success, `UNATTRIBUTED`, failure, cancellation, missing/invalid artifact and cleanup failure all consume the entitlement.

## Public Interfaces

- No product interface changes.
- M3-14 validation entry points must be repository tools or test-only Gradle tasks and must not enter release distributions.
- Candidate workflows are data until the reviewed direct-child publication; they are not executable from their candidate paths.

## Security Constraints

- Original canonical APKs and the retained profile archive are read-only, hash-first inputs. Rebuilds or substitute fixtures are rejected.
- No private key, keystore, password, token, device serial, user path, full signer digest, plaintext DEX or customer APK may enter source, logs or artifacts.
- Profile and original APK semantics must match except for the exact reviewed probe transformation. Manifest, signer, container, resources, native members and non-probe DEX instructions remain bound.
- Downloads require fixed HTTPS host/path or numeric asset identity, bounded sizes, exact archive/member hashes and no credential forwarding across redirects.
- Cleanup proves packages, remote files, signing roots and temporary files absent; command failure cannot be interpreted as absence.
- Caller-authored booleans cannot prove signer, security equivalence, environment, cleanup, run identity or terminal artifact facts.

## Compatibility Requirements

- No minSdk, ABI, signer-policy, container-format, public Runtime/Host API or compatibility-claim change.
- The only dynamic target is the fixed API 36 revision 2 x86_64 image with Emulator `37.1.11`.
- ARM, API 29, physical-device and broader M3-05 results cannot substitute for or extend the successor identity.

## Acceptance Criteria

- The implementation freeze is based on `main@960eb9f406eb1a7b7c9b324598fb59936aa1c5b5`, links Issue #82 and contains neither canonical workflow.
- Deterministic profile inputs reproduce the locked package report twice without regenerating or resigning any APK.
- Byte-level verifier rejects product/profile/signer/probe/manifest/DEX/resource/native/security-event drift and release/CLI/distribution contamination.
- Runner/verifier reject sample reorder/omission/duplication, calibration drift, boot/job/run mismatch, cleanup ambiguity, sensitive output, incomplete package and malformed/oversized GitHub evidence.
- ADR 0016 owner arithmetic reconciles every retained ordinal and returns exactly one eligible owner or `UNATTRIBUTED` without tie-breaking or result shopping.
- Local validation, governance, strict HandOff, diff/sensitive/UTF-8 checks and named mutations pass on a clean freeze.
- An independent read-only Agent reports `P0=0/P1=0/P2=0` on that exact freeze before canonical publication.
- That same unchanged workflow-absent freeze is pushed, becomes the unique Issue #82 draft PR head, and passes exact-head Ubuntu/Windows Build plus Governance before publication.
- After publication, the sole API 36 run has `runAttempt=1`; no second diagnostic run is accepted or attempted.

## Required Tests

- Positive exact M3-11/M3-12/ADR 0018 identity and package verification.
- Deterministic profile verification, full method prototype/probe ordinal/adjacency mutations and non-probe DEX metadata/instruction mutations.
- Signer, manifest, container, ZIP/APK member set, resource/native, release-surface and sensitive-data mutations.
- Environment, boot, run/job/attempt, sample order/count/hash, calibration, owner arithmetic, thresholds, cleanup and OOM/oversize mutations.
- Official GitHub pagination, unique run/job/artifact, exact downloaded package member/hash/size and reviewed-code ancestry mutations.
- Workflow-presence negative before all-zero review; exact candidate-copy/direct-child/ledger checks after publication.
- Project governance, strict HandOff and base-to-HEAD diff gates.

## Required Evidence

- Base/head, branch, Issue #82, changed files, commands/exits, OS, Node/JDK/Gradle/Android toolchain versions, timestamps and hashes.
- Exact profile release/asset/archive/member identities and the four APK/package verifier report hashes.
- Named mutation inventory and exact pass/fail counts.
- Independent read-only review report bound to the frozen commit.
- If and only if review is all zero: exact-freeze draft PR and Ubuntu/Windows Build/Governance evidence; only after those pass, publication commit, execution identity/ledger hashes, official API 36 run/job/artifact metadata and terminal evidence.
- README/task/HandOff update and later owner-remediation decision; `UNATTRIBUTED` or any invalid execution leaves M3-05 blocked.

## Likely Files

- `docs/tasks/M3-14-successor-startup-diagnostic.md`
- `docs/evidence/M3-14/**`
- `tools/validation/m3-14/**`
- `tools/validation/run-m3-14-startup-attribution.mjs`
- `tools/validation/verify-m3-14-startup-attribution.mjs`
- `tools/validation/collect-m3-14-github-evidence.mjs`
- non-executable workflow candidates under `tools/validation/m3-14/workflow-candidates/`
- `host/container/src/test/**` and test-only Gradle verification tasks
- `.github/workflows/m3-13-startup-attribution.yml` and `.github/workflows/m3-13-startup-attribution-evidence.yml` only in the post-review publication child
- `README.md`, `docs/tasks/INDEX.md`, `docs/PROJECT_PLAN.md`, `docs/ROADMAP.md`, `docs/TEST_STRATEGY.md`, `HandOff.md`

## Dependencies and Blockers

M3-14 depends on completed M3-09, M3-11, M3-12 and M3-13. Its implementation freeze is blocked from canonical workflow publication until an independent read-only review returns all zero and that unchanged workflow-absent head passes exact-head Ubuntu/Windows Build plus Governance through the unique draft PR. M3-05 remains blocked until the sole valid successor result selects one eligible owner and a separate owner-remediation task completes; an invalid, failed or `UNATTRIBUTED` result does not authorize M3-05.

## Terminal Outcome

The reviewed publication consumed the only successor entitlement in GitHub Actions run `32611656930` at exact head `9fe48737d97853d1566cc2e642009d8ff1b8ab52`, `runAttempt=1`. Official API pages prove that setup through exact Release-surface build succeeded, the monolithic diagnostic step failed, its upload step was skipped and the run has zero artifacts; they do not support a narrower claim about where execution stopped inside the failed step. Direct-child terminal request `b0771d4853e0a7de7fb9db802cac719e34c67229` triggered terminal evidence run `32612414400`; its setup steps 2-5 succeeded, collection step 6 failed, upload step 7 was skipped and artifact count is zero. The retained pages do not prove the internal boundary of step 6. This is a failed/invalid terminal outcome under ADR 0018, so M3-14 is blocked, no retry or renewal is permitted, no owner remediation is selected, and M3-05 remains blocked.

## Agent Handoff Requirements

Use branch `feat/m3-14-successor-startup-diagnostic`, Issue #82 and one PR. Distinguish implementation freeze, all-zero review, unchanged exact-freeze draft-PR CI qualification, workflow publication, run consumption and terminal evidence as separate states. Explicitly state whether either canonical workflow exists and whether the one-time execution entitlement has been consumed.
