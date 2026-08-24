# ADR 0019: Terminal diagnostic and v0.1 release disposition

## Status

Proposed

## Context

ADR 0018 allowed exactly one successor execution for the product tuple `883da673d3bced1ec93f11323fe63152c1007112d08c46643976c70397d0b8dd` and explicitly made every successor outcome terminal. M3-14 consumed that entitlement in canonical run `32611656930`, job `97125597267`, `runAttempt=1`. The diagnostic step failed, its upload step was skipped and the official artifact count was zero. Terminal evidence run `32612414400`, job `97127412040`, also failed and retained no artifact. The retained official pages do not prove a narrower internal failure boundary, so no performance owner was selected.

ADR 0018 forbids a retry, replacement result, renamed execution or further renewal for the same tuple. M3-05 cannot resume without a valid owner and owner remediation, while M4-01 requires completed M3-05 performance evidence. A project-level disposition is therefore required; leaving the draft implementation and benchmark PRs indefinitely open would obscure that the current v0.1 release line has no valid path to its release gates.

## Decision

M3-15 is a governance-only terminal disposition task. Its decision code is exactly `STOP_CURRENT_V0_1_RELEASE_LINE`.

The immutable decision input is `docs/evidence/M3-15/terminal-disposition-lock.json`. It binds Draft PR #83 reviewed head `a112e4469699125a32d80e3c652cda7d4b6b7cf1`, execution identity `96837a115f89e3866d56c315928314c9532b4b635859b6f5860c8d1c442e5357`, canonical run `32611656930`, terminal run `32612414400`, their exact jobs, attempts, failed/skipped steps, zero artifact counts and the six retained official-page hashes. M3-15 records those facts; it does not reinterpret either failed step's internal boundary.

For the current v0.1 product tuple:

1. Diagnostic retry, rerun, replacement, renewal, path/name substitution, ARM/API 29/physical-device substitution and a third measurement are forbidden.
2. M3-14 remains terminally blocked and is not merged into `main` as a completed implementation. After this ADR is merged, Draft PR #83 and Issue #82 are closed as terminally blocked without merging the branch.
3. M3-05 remains terminally blocked. After this ADR is merged, Draft PR #63 and Issue #22 are closed without merging their benchmark implementation or evidence.
4. M4-01, M4-02 and M4-03 are not startable. No v0.1 release candidate, release archive, SBOM-backed release decision, checksum set or release-ready compatibility/performance claim may be produced.
5. Existing merged Host, Runtime and validation source remains available as development output. It must not be represented as a completed v0.1 release.

M3-15 itself creates no diagnostic workflow, runs no Android command, starts no device or emulator, downloads no profile asset and changes no Host, Runtime, fixture, benchmark or distribution implementation.

Future work is not an exception to ADR 0018. A restart requires explicit user authorization, a new versioned product baseline and product tuple, a new ADR and task graph, and fresh release requirements. It cannot reuse the current tuple's consumed identity, relabel either failed run, or claim that current M3-05/M4 gates passed.

## Consequences

- The current v0.1 release line ends in a truthful blocked state rather than an unsupported release.
- PR #83 and PR #63 remain reviewable historical records until M3-15 merges, then are closed unmerged with links to this ADR.
- M4 Issues may remain open as unscheduled roadmap records, but no M4 branch or implementation may start under the current tuple.
- No performance budget, signer, container, Runtime, compatibility or security requirement is weakened.
- A future release effort must be visibly distinct and cannot silently inherit missing evidence.

## Rejected Alternatives

- Retry or rename the successor workflow: violates ADR 0018 and enables result selection.
- Use ARM, API 29, a physical device or a local emulator as substitute evidence: changes the fixed environment and identity.
- Merge PR #83 as completed: its terminal result did not satisfy M3-14 acceptance and selected no owner.
- Resume M3-05 without owner remediation: bypasses the fixed performance attribution gate.
- Start M4 and mark performance as waived or unknown: contradicts the product and release requirements.
- Delete the failed branches or evidence: removes auditability without changing the outcome.

## Security Impact

This decision preserves fail-closed evidence and prevents unsupported release claims. It changes no production security control and does not authorize execution of security-sensitive workflows. Closing draft PRs is a governance lifecycle action, not evidence deletion; Git commits, PR discussions and official run records remain auditable.

## Compatibility Impact

No compatibility claim is added or removed from an already released product because v0.1 is not released. Existing M3-04 verified cells remain historical validation evidence, but they cannot substitute for the missing M3-05 performance gate or authorize M4.

## Verification

- `verify-m3-15-terminal-disposition-contract.mjs` validates the exact lock, decision code, terminal run identities, no-retry flags, PR closure order and blocked release claims.
- Named mutations must reject changed run/job/attempt/head/hash/artifact/step values, any enabled retry or release flag, any M3-05/M4 unblocking and any workflow or product implementation change.
- Project Governance, strict HandOff, UTF-8/link, sensitive-data and base-to-HEAD governance-only diff checks must pass.
- An independent read-only review must report `P0=0/P1=0/P2=0` on the frozen local commit before push or draft PR creation. No device, KVM, benchmark or diagnostic run is accepted as M3-15 evidence.
