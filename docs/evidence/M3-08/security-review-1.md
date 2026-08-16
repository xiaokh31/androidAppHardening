# M3-08 independent security review 1

- Timestamp: `2026-08-16T12:25:13+08:00`
- Frozen head: `409a73a6ee471da3d2f9ba56f4ac2c50f6e6b522`
- Base: `origin/main@930b759c99f330218dc4404368e9844e80456c82`
- Reviewer mode: independent read-only
- Result: `FAIL`
- Findings: `P0=0 / P1=2 / P2=2`
- Repository modifications by reviewer: none
- Gradle/KVM/emulator/device/network execution: none

## P1 findings

1. The aggregate validator accepted self-declared campaign report SHA-256 values and did not read the two source reports, invoke the M3-07 validator or recompute their raw-sample statistics and budgets.
2. Exact head, run/job/attempt, environment, boot and artifact identity were not bound to explicit executing-job inputs or actual manifest bytes.

## P2 findings

1. Aggregate `deltaP50`/`deltaP95` rows were not checked against protected minus baseline statistics for each campaign.
2. Sensitive-path scanning did not cover slash-form Windows paths, arbitrary drive paths, UNC paths or macOS user paths.

## Required remediation

- Make the formal CLI require both retained campaign reports and explicit expected execution identities.
- Hash the actual campaign reports and artifact manifest, invoke M3-07 validation, and recompute percentiles, deltas, budgets and all ninety aggregate rows from raw samples.
- Add exact delta arithmetic, negative-delta and 10% boundary tests.
- Recursively scan every retained string/key for device serials and Windows/UNC/macOS/Unix absolute or user paths.

The reviewed freeze is rejected and must not be pushed or used to create a PR. A corrected freeze requires a new independent incremental review.
