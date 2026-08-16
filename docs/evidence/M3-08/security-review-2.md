# M3-08 independent security review 2

- Timestamp: `2026-08-16T12:39:00+08:00`
- Corrected implementation freeze: `3db35f69a00e3a8804461b5aaba60717dc14da74`
- Evidence head: `83929e4008b56e78510df85cd329462969110e01`
- Base: `origin/main@930b759c99f330218dc4404368e9844e80456c82`
- Reviewer mode: independent read-only incremental review
- Result: `FAIL`
- Findings: `P0=0 / P1=1 / P2=0`
- Repository modifications by reviewer: none
- Gradle/KVM/emulator/device/benchmark/network execution: none

## Closed findings

- Actual report-byte hashing, M3-07 validation and raw-sample percentile/delta/budget recomputation are closed.
- A/B delta arithmetic is closed.
- Recursive JSON key/value plus artifact-manifest path scanning is closed.

## Remaining P1

The two source reports do not themselves carry campaign or execution identity, while the artifact manifest was only treated as arbitrary bytes. Different paths containing identical report bytes or historical-job reports could therefore be relabeled by the aggregate object as a current same-job/same-boot A/B pair.

## Required remediation

- Parse a canonical manifest that binds head/run/job/attempt/environment/boot, exact campaign A/B identities and order, both report hashes, and all tested baseline/protected APK hashes.
- Compare manifest identity with explicit executing-job inputs and actual report bytes.
- Reject identical report hashes even when paths differ.
- Add historical-job, reused-report and missing/duplicate APK-binding negative mutations.

The reviewed freeze remains rejected. A new corrected freeze requires a final limited incremental review.
