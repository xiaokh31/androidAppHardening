# M1-05 independent ZIP/APK security review 2

- Frozen commit: `55b951269201f37aada6945b13c0716531616b92`
- Tree: `f07594a4af7a7a8f94c57042338ec70bea45ae2b`
- Base: `d32abe1d68d41910d72c90c3f9fc3d2831972756`
- Reviewer: independent read-only `m1_05_security_review_2`
- Result: **FAIL**
- Findings: `P0=0`, `P1=3`, `P2=1`

The reviewer changed no tracked file and performed no network, device, emulator,
push, or Git mutation. The repository-local offline `:host:repacker:test`
completed with exit `0` in about 34 seconds. The frozen commit is invalidated.

## Findings

### P1-1: two sensitive copies remained outside transactional cleanup

`bytesPlan()` created a ConfigV2 or Runtime payload before any owner could clear
it if digest/contract/plan construction failed. Runtime verification also used
`ByteArrayOutputStream.toByteArray()`, leaving the stream's internal `R_native`
copy uncleared even when the returned array was wiped.

Required repair: keep the payload under a nullable owner through transfer into
the prepared entry list; materialize Runtime directly into one preallocated,
clearable array; inject payload-plan and verifier-materialization OOM.

### P1-2: fallible operations remained after atomic publication

After the move, observer, path/identity/hash checks, input hashing, and archive
close could still fail. Best-effort rollback could expose or permanently leave
an output.

Required repair: complete every observer, hash, identity check, and handle close
before publication, then make the atomic no-replace operation the final fallible
step.

### P1-3: candidate identity and no-clobber publication were not proven

Candidate identity was captured only after verification and Java NIO
`ATOMIC_MOVE` does not specify no-replace behavior when the target appears in a
race. A missing Java `fileKey` also degraded to a size-only comparison.

Required repair: capture candidate identity before verifier access and recheck
it immediately before publication; fail closed without a platform identity;
use fixed Windows/Linux atomic no-replace primitives and test candidate/target
races.

### P2-1: topology and identity mutation evidence was incomplete

The matrix lacked local/APK-Signing-Block gaps, container/candidate/output races,
and recorded the Windows parent swap as successful without executing it.

Required repair: add explicit gap and all file-identity mutations, and execute or
truthfully mark platform-dependent cases.

## Confirmed closed

- all early failures consume and clear the one-shot key plan;
- publication is after key-plan cleanup;
- Runtime materializer result and pending arrays are cleared;
- malicious entry names are sanitized;
- the official verifier checks the exact unsigned reason;
- descriptor, overlap, and the principal content mutations are present.

The reviewer confirmed exact start HEAD/tree and clean status. Its final status
command was externally interrupted after the ignored build-only test run, so the
coordinator separately rechecked the tracked worktree before remediation.
