# M1-04 independent security review 2

- Review target: `51c7d31b2bac3910a37b189137055ad4bf9fa4d8`
- Reviewer role: independent read-only security reviewer
- Result: `FAIL`
- Findings: `P0=0`, `P1=1`, `P2=1`

## Findings

### P1 - OOM could interrupt cleanup and retain sensitive copies

The reviewer confirmed that ordinary cleanup callback failures were aggregated,
but found three OOM ownership gaps:

- `CleanupTrackingObserver` appended failures to a growable `ArrayList`, so the
  append itself could OOM and stop later clears.
- `BuildSecrets.create` allocated a pair/list entry after receiving random bytes;
  OOM in that bookkeeping window could leave the new byte array unregistered.
- `KeyPackagingPlanV2` performed several constructor `copyOf` operations without
  a construction-failure owner for copies already made.

Required disposition: use allocation-free first-failure tracking, register random
arrays without per-value allocation, make sensitive-copy construction
transactional, and inject early/middle/late OOM while preserving the primary
failure and suppressing cleanup diagnostics best-effort.

### P2 - this review's dynamic rerun was interrupted

The reviewer statically confirmed the new complete-path tests and both-platform
three-hash CI gate, but its own 512 MiB module run was interrupted on coordinator
instruction before an exit code and the Node consumer was not run. This was an
evidence gap in the review run, not a reported product-code defect. The residual
repository JDK process was identified and terminated by PID; zero Java processes
were confirmed before remediation continued.

## Closed first-review findings

The reviewer statically confirmed closure of atomic publication, immediate limits,
ordinary cleanup callback continuation, boundary/512 MiB/IO/zlib/vector coverage,
tamper inflater assertions, production-random verification and full descriptor
comparison, `R == R_java` rejection, field-level random freshness, and stable
SecureRandom initialization error mapping.

## Read-only proof

The reviewer recorded the same target SHA and an empty tracked/untracked status
before and after its inspection. It changed no file and performed no network,
device or emulator operation.

## Required disposition

Keep remediation within M1-04, freeze a new commit, and run another independent
read-only review with bounded module check and Node consumer before publication.
