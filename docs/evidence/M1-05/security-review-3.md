# M1-05 independent ZIP/APK security review 3

- Frozen commit: `1febc2da91d62ba3163cdab022955c51be88759a`
- Tree: `baecb08ce49128a4ae68c56c1d30c3f50312b965`
- Base: `d32abe1d68d41910d72c90c3f9fc3d2831972756`
- Reviewer: independent read-only `m1_05_security_review_3`
- Result: **FAIL**
- Findings: `P0=0`, `P1=1`, `P2=0`
- Completed: `2026-08-06T11:13:11+08:00`

The reviewer confirmed the exact frozen HEAD/tree and a clean worktree, changed
no tracked file, and performed no network, device, emulator, push, or Git
mutation. Repository-local offline `:host:repacker:test` passed in 47.8 seconds;
Governance, strict HandOff, diff, UTF-8, sensitive-data, and all six deterministic
report-hash gates passed.

## P1: direct Native dependency lacked an acceptable vulnerability review

The Host distribution newly depended on JNA/JNA Platform `5.6.0`. Although its
version, lock state, artifact hashes, Maven Central source, and dual license were
recorded, the branch evidence did not document maintenance and known-vulnerability
checks for this Native dependency as required by `docs/DEVELOPMENT.md`.

Required repair: select a current maintained fixed version after authoritative
advisory review; update the catalog, all affected locks, dependency verification,
provenance and third-party notice; record source/date/range/conclusion; rerun local
and Ubuntu/Windows gates; freeze a new SHA and repeat independent review.

## Confirmed closed

- `OwnedBytesPlan` and the single clearable Runtime verifier buffer close the
  sensitive-copy ownership windows.
- Key-plan cleanup, hashes, identity checks, and input handle close precede
  publication; native no-replace move is the final fallible operation.
- Windows file ID and Linux file identity fail closed.
- Candidate/container/input/parent identities, local and Signing-Block-shaped
  gaps, output races, and real Windows parent replacement are covered.
- Windows uses `MoveFileExW` without replace; Linux uses
  `renameat2(RENAME_NOREPLACE)`.
- Input remains read-only, output remains unsigned, public errors are sanitized,
  and no plaintext business DEX entry is published.

The frozen commit is invalidated. Publication remains blocked until a new clean
commit closes the dependency-security finding and a fresh independent review
reports `P0=0`, `P1=0`, and `P2=0`.
