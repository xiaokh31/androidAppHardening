# M2-10 contract security review 1

- Timestamp: `2026-08-16T22:58:27+08:00`
- Reviewed head: `714947c628bd083ef3cdddd2c427edd90b1a6733`
- Contract commit: `6750a0dd533c2be2b616a22eb1f79e7debbd2d94`
- Reviewer: independent read-only `m2_10_contract_security_review`
- Result: `FAIL`
- Findings: `P0=0`, `P1=3`, `P2=0`
- Files changed by reviewer: `None`

## Findings

1. The diagnostic report did not bind the first and only `runId`, `jobId`, `runAttempt` and `bootIdHashPrefix`, so repeated diagnostics could select a favorable result.
2. The six stage names did not define one-clock, non-overlapping method boundaries that reconcile to the same real Release/R8 first-start Runtime interval.
3. Fifteen retained samples did not define unique partitions or a fixed P50 algorithm, allowing eligibility to vary with grouping.

## Required remediation

- Bind the first and only diagnostic report, manifest and raw samples to exact run/job/attempt/boot identity; invalid, incomplete, timed-out or ineligible evidence blocks the task and cannot be replaced on identical product bytes.
- Define a single in-process monotonic `t0..t6` sequence on the real first protected startup, six adjacent stage differences and exact total reconciliation; reject second-open, Host-only and cross-process substitutions.
- Fix acquisition-order partitions to samples `1..7` and `8..15`, prohibit omission/duplication/reassignment and use nearest-rank P50 with one-based rank `ceil(0.50*n)`.

## Read-only validation

- `node --check tools/governance/validate-project-package.mjs`: exit `0`
- `node tools/governance/validate-project-package.mjs`: exit `0`
- `node .agents/skills/coordinate-project-handoff/scripts/validate-handoff.mjs HandOff.md --strict`: exit `0`
- `git diff --check 7f10a0b84d9680e4b9311e680d0508e7fde512cd..714947c628bd083ef3cdddd2c427edd90b1a6733`: exit `0`
- Environment: Windows NT `10.0.19045.0`, Node.js `v24.12.0`

No Gradle, network, KVM, emulator, physical device or benchmark command ran during the review.
