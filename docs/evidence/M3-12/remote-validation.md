# M3-12 final remote validation

- Timestamp: `2026-08-22T12:34:00+08:00`
- Pull request: `#76`
- Branch: `docs/m3-12-profile-package-retention`
- Exact head: `5a8c3a018cf875bc6c45b29a6bef05094143c58e`
- Reviewed main parent: `28493ca0c572b2af45a107e0e77010f6ebe878c2`
- M3-12 parent: `7347ed8365353d85888f55439d88d5b434202f10`

## Required workflows

| Workflow | Run | Ubuntu | Windows | Result |
|---|---:|---:|---:|---|
| Build | `32551730109` | job `96979477916` PASS | job `96979477820` PASS | PASS |
| Governance | `32551730123` | job `96979478071` PASS | job `96979477957` PASS | PASS |

Both workflows completed against the exact head above. Build verified the pinned Ubuntu and Windows runner/toolchain identities, Native crypto vectors, ASan/UBSan failure injection, full Linux/Windows checks, byte-identical Host reports and all four Native ABIs. Governance validated the project package, pull-request HandOff, negative HandOff tests and Git object database on both platforms.

The Ubuntu Build emitted a non-blocking GitHub annotation that one pinned `actions/upload-artifact` revision targets Node.js 20 and was forced by GitHub to Node.js 24. The job and workflow both completed successfully; this annotation did not change repository code, task inputs or acceptance semantics.

## Scope enforcement

The automatically triggered runs below were cancelled and are not acceptance evidence:

- M0-05 Linux KVM `32551730120`
- M3-02 Fuzz `32551730134`
- Cross-platform equivalence `32551730155`

No profile was regenerated. No device, emulator, KVM, fuzz campaign, equivalence campaign, benchmark, API 36 diagnostic, ARM run or M3-05 action was executed. The unique M3-10 API 36 workflow remains absent. PR #76 remains draft pending explicit ready/expected-head merge authorization.
