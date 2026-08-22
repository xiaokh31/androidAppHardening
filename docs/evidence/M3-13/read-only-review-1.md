# M3-13 independent read-only review 1

- Task: `M3-13`
- Base: `c9399b40884778f027ffbe33f96786197365acb3`
- Implementation freeze: `55997e61a2f734ab3d7ed5f8a44a44064b526ac3`
- Evidence head: `bec3d0ddeccc356c31f69add2e37e197cd127531`
- Reviewer role: independent read-only security/governance reviewer
- Result: `FAIL`
- Findings: `P0=0/P1=3/P2=1`

## Findings

1. `P1`: the future `executionIdentitySha256` included workflow-candidate hashes while the same identity was required inside the workflow run name, creating a cryptographic self-reference that no deterministic candidate could satisfy.
2. `P1`: the normalized predecessor proof was not bound to retained raw official run/job/artifact API response bytes, so its official source and complete step/artifact facts could not be independently re-parsed from the repository.
3. `P1`: M3-05 still treated terminal M3-10 as a formal completion dependency and stated that only M3-10 could select an owner, contradicting the ADR 0018 successor path.
4. `P2`: local evidence omitted explicit duration, JDK/API/ABI applicability fields and an exact sensitive-scan result while HandOff claimed sensitive checks passed.

## Positive verification

The reviewer independently recomputed the then-current proof and preimage hashes, historical workflow/runner/verifier/environment-lock bytes, 17/10 step structure, zero-device inference, 53 mutations, base diff, Governance, strict HandOff and Git object ancestry. No leak was observed. The review modified no file and used no network, Gradle, Android, device, emulator, KVM or benchmark.

## Required remediation

- Keep `executionIdentitySha256` only in a pre-run ledger/artifact manifest and use a non-self-referential canonical run-name identity.
- Retain exact official API response pages with endpoint/byte/hash bindings, parse them in the validator and read reviewed bytes directly from fixed historical Git objects; explicitly exclude unnecessary raw logs.
- Treat M3-10 only as historical input to M3-05 and add future concrete successor/remediation task IDs when those tasks exist.
- Complete evidence environment/duration/applicability and sensitive-scan fields.

No push or draft PR is permitted until a remediation freeze receives a new independent `P0=0/P1=0/P2=0` review.
