# ADR 0018: Successor diagnostic execution identity

## Status

Proposed

## Context

ADR 0016 authorized one API 36 x86_64 startup attribution diagnostic for the exact M3-11 product tuple. M3-10 independently froze and reviewed the implementation, but its first-and-only run `32554806537` failed during repository-provenance preflight. The job did not create an AVD, attempt APK installation, retain a benchmark sample or upload an artifact. Terminal evidence run `32554917303` retained that absence. M3-10 is terminally blocked and its original execution identity remains consumed.

The failure exposed a distinction that ADR 0016 did not model: an execution may consume its identity without ever observing the product. Treating every infrastructure failure as freely retryable would enable result shopping, while treating a proven pre-device failure as measurement evidence permanently prevents the already-reviewed diagnostic from answering its question. This ADR defines one narrow successor identity. It does not erase, replace or relabel the M3-10 history.

## Decision

M3-13 is a governance-only contract task. It records the M3-10 terminal history and defines one successor execution identity. M3-13 itself must not create a diagnostic workflow, run Android, start an emulator, install an APK, run KVM or resume M3-05.

The predecessor is eligible for this exception only when raw official GitHub run, job, step and artifact evidence plus the reviewed workflow bytes prove zero AVD creation, zero installation attempt, zero retained samples and zero artifacts, including all of the following:

1. `runAttempt` is exactly `1` and the run/job IDs and head equal the values in `diagnostic-eligibility-lock.json`.
2. Failure occurred in repository-provenance preflight after pinned API 36/Emulator package preparation but before Native preparation, Release build, AVD creation or any device command.
3. No APK install command was attempted, no package was published, no campaign/warmup/retained sample began and no benchmark value exists.
4. The official artifact count is exactly zero and the separate terminal evidence run rejects the missing artifact.
5. The failed run, job, logs and zero-artifact result remain visible in successor evidence. They may not be omitted or described as a measurement result.

If any condition is absent, ambiguous or later contradicted, no successor is eligible. A caller-authored boolean is not evidence.

The official predecessor proof is `docs/evidence/M3-13/predecessor-official-proof.json`. Its canonical compact JSON is 5274 UTF-8 bytes with SHA-256 `b3faa34fcee76adb5223c99ccc854fc3000133244cce5a23c8ff2d9432d0d643`; it records every official job step, run/job/artifact fields and reviewed workflow/runner/verifier/environment-lock byte hashes. The immutable contract identity is the exact 1033-byte UTF-8 `identityPreimage` in `docs/evidence/M3-13/diagnostic-eligibility-lock.json`. Its SHA-256 is `4104670bbe53aaa193740e4e34128051332657bb8dc8c65b57dd133443387faf`. It binds that proof, the consumed predecessor, zero-observation facts, product tuple, successor task key, canonical workflow paths, run limit and no-further-renewal rule.

The later implementation task must create a distinct execution identity. Its exact UTF-8 JSON preimage has the following fields in this order:

1. `schemaVersion` = `1`.
2. `contractIdentitySha256` = `4104670bbe53aaa193740e4e34128051332657bb8dc8c65b57dd133443387faf`.
3. `productTupleSha256` = `883da673d3bced1ec93f11323fe63152c1007112d08c46643976c70397d0b8dd`.
4. `profileArchiveSha256` = `21816d2a843bb5c59902224c7bf786d546d52b4a5b2d1168ca0c449a2ca27964`.
5. `implementationFreezeSha` = the independently reviewed workflow-absent implementation freeze.
6. `diagnosticWorkflowCandidateSha256` and `evidenceWorkflowCandidateSha256` = hashes of non-executable candidate files reviewed at that freeze.
7. `runnerSha256` and `verifierSha256` = reviewed executable source hashes.
8. `environmentLockSha256` and `toolchainLockSha256` = reviewed repository lock hashes.
9. `qualificationEvidenceSha256` = the tracked, pre-device proof that the full Git history contains the required M3-11/M3-12/M3-10 ancestry and objects.

`executionIdentitySha256` is SHA-256 of that exact JSON serialization. All hashes are lowercase 64-hex strings. The execution identity must be fixed in a tracked pre-run ledger and the canonical run name before GitHub can execute the workflow.

The implementation freeze contains no canonical workflow. It contains only non-executable workflow candidates plus the runner, verifier, locks and qualification evidence. An independent read-only review must return `P0=0/P1=0/P2=0`. Only then may one direct-child publication commit copy the candidate bytes unchanged to:

- `.github/workflows/m3-13-startup-attribution.yml`
- `.github/workflows/m3-13-startup-attribution-evidence.yml`

The publication commit also adds the pre-run ledger. Its parent must equal `implementationFreezeSha`; its changed paths are limited to those two workflows, the ledger and coordination evidence explicitly approved by the contract validator. The canonical workflow must verify candidate-to-live byte equality before any Android setup.

The M3-13 contract validator deliberately rejects either canonical workflow while this ADR-only task is under review. A later separately authorized implementation task must change that validator only as part of its independently reviewed, workflow-absent implementation freeze: the successor mode must require the exact contract identity, candidate hashes, implementation-freeze parent, ledger and allowed publication paths before accepting the direct-child publication. Merely adding either workflow while retaining the contract-only validator is always invalid.

The successor workflow uses `fetch-depth: 0`. Before Android SDK installation, AVD creation or any device command it must verify required commits exist, prove the fixed ancestry with `git merge-base --is-ancestor`, hash the exact retained profile asset and locks, verify its own workflow/runner/verifier bytes and prove official run uniqueness. A Build/Governance qualification run must exercise the same ancestry predicate on the exact implementation freeze before publication; self-reported ancestry is insufficient.

The successor has task key `M3-13-SUCCESSOR-DIAGNOSTIC-V1`, canonical diagnostic path `.github/workflows/m3-13-startup-attribution.yml`, evidence path `.github/workflows/m3-13-startup-attribution-evidence.yml`, exactly one matching run and `runAttempt=1`. Renaming a path, job, artifact, branch or task key does not create another identity.

The measurement protocol, product tuple, profile package, API 36 revision 2 x86_64 image, Emulator 37.1.11, campaigns, sample counts, clocks, owner arithmetic, thresholds and budgets remain byte- and value-equivalent to ADR 0016. The successor is not a third campaign because the predecessor produced zero device observation; nevertheless both executions remain in the permanent history.

The successor result is terminal:

- one eligible owner permits only a separately scoped owner-remediation task;
- `UNATTRIBUTED` is retained as the final attribution result;
- cancellation, timeout, invalid evidence, preflight failure, device failure, missing artifact or cleanup failure blocks permanently.

No second successor and no further renewal are permitted for this product tuple under ADR 0018. Another ADR may document the terminal outcome, but it may not authorize a third measurement of the same tuple. M3-05 remains blocked until a valid owner is remediated and its own contract permits resumption.

## Consequences

- M3-10 remains blocked; PR #79 cannot become the successor implementation PR and must remain draft.
- M3-13 creates no executable workflow and consumes no device eligibility.
- A later separately authorized implementation task may reuse the reviewed M3-10 algorithm only after rebasing it onto the new identity and qualification contract; it cannot reuse M3-10 run history as success evidence.
- The physical ARM device, API 29, local emulators and the full M3-05 matrix remain out of scope.
- This exception cannot be generalized to a run that created an AVD, installed an APK, began a campaign or produced any measurement/artifact.

## Rejected Alternatives

- Retry M3-10 unchanged: violates ADR 0016 and permits replacement history.
- Rename only the workflow or task key: changes labels without changing the reviewed execution boundary.
- Treat the failed preflight as `UNATTRIBUTED`: no product observation exists from which to compute ownership.
- Run the same protocol on ARM or a physical device: changes the fixed environment and does not answer the retained API 36 x86_64 failure.
- Relax ancestry, provenance, sample, repeatability or performance gates: converts an infrastructure defect into weaker evidence.
- Permit recurring successor ADRs: enables indefinite retries and result selection.

## Security Impact

No production Runtime, Host, signer, container, Guard, memory-control, cleanup or public interface changes. The successor still uses the independently verified profile package and test-only signer boundary. The new preflight is stricter: repository ancestry, object availability and executable bytes are proved before Android setup. No private key, signing password, full signer digest, device serial, user path, plaintext DEX or raw unrestricted log may enter evidence.

## Compatibility Impact

No compatibility claim changes. The only future execution remains API 36 revision 2 x86_64 and cannot be cited as ARM, API 29 or general device support evidence.

## Verification

- `verify-m3-13-diagnostic-identity-contract.mjs` validates the immutable lock, exact serialization/hash, predecessor zero-observation boundary, successor identity schema, one-run/no-renewal rule, blocked dependency text and workflow absence.
- Named mutations change every predecessor identity field, observation count/boolean, product tuple, workflow/task identity, run limit, renewal flag, preimage byte and SHA; all must fail closed.
- Base-to-HEAD verification rejects Runtime, Host, fixture, benchmark, diagnostic workflow, APK, DEX, key or distribution changes.
- Project governance, strict HandOff, UTF-8/link, diff and sensitive scans must pass.
- M3-13 requires independent read-only review with `P0=0/P1=0/P2=0` and exact-head Ubuntu/Windows Build/Governance before merge. No device, KVM or benchmark evidence is accepted for this contract task.
