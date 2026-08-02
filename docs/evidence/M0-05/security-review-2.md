# M0-05 second independent security review

## Review identity and result

- Reviewer: independent read-only `m0_05_security_review_2`
- Reviewed commit: `39a30ed1bb5ab80bb13c2ac71968c1599bbb6db4`
- Branch: `spike/m0-05-application-factory-provider-jni-poc`
- Worktree at review: clean
- Result: `FAIL`
- Open findings: P0 `0`, P1 `0`, P2 `3`
- Reviewer mutation: none; no file modification, checkout, commit, push, PR, download, or local emulator use

The review confirmed that all three P1 findings from the first review were closed: component-delegate failure normalization, independent extracted/direct startup-negative matrices in all three environments, and the exact `libpulse0=1:16.1+dfsg1-2ubuntu10.1` pin. It also confirmed target-resume timing, JUnit/R8/SO evidence, cleanup, governance, and strict HandOff validation.

## Findings and required closure

| Priority | Finding | Required closure | Review-3 closure evidence |
|---|---|---|---|
| P2 | The prior duplicate-ABI check used an unsigned temporary ZIP and direct resolver invocation, so it did not prove authenticated startup failure before business JNI. | Exercise a signed valid fixture through signer/config and the complete startup path; prove `AAH-P004` and no new `LOADER_CREATED` or `JNI_LOADED`. | Commit `189a04c5286187ae61575d3a9ec574d62501eacc` adds a signed case-folded duplicate ABI alias. Official `apksigner` verifies it; all six device variants report `authenticated_native_negative=1`; both independent matrices in all three environments report `AAH-P004`, `loader_created=false`. Exact duplicate ZIP names are rejected by official `apksig` before authentication, so the task-card-compatible forged duplicate ABI case is used instead. |
| P2 | The formal summary claimed a `1,440`-byte verifier delta while repaired reports showed `145,312 -> 146,980`, and the M0-04 baseline could not isolate verifier-only cost. | Use a same-variant verifier off/on control, or remove the invalid attribution and correct the measurement. | The report field is renamed `m004_baseline_root_dex_delta`. Review-3 reports show `145,488 -> 147,156`, delta `1,668`, and the formal summary explicitly says the cross-variant value is not attributable solely to the verifier. |
| P2 | The frozen evidence summary omitted required peak-memory and artifact values and contradicted its own freeze/push state. | Record the missing hashes/values and reconcile frozen SHA, remote state, and next action. | The corrected formal summary records verifier peak memory `51,900 / 71,348 / 73,516 KB`, payload DEX, ConfigV2/AHDC, JUnit, R8 mapping/usage, SO and report hashes. HandOff will identify the corrected frozen evidence commit separately from the pushed KVM validation commit. |

## Gate

This second review is a historical failed gate and cannot authorize a PR. Only a third independent read-only review of the corrected frozen evidence may authorize the PR stage, and only when it reports P0/P1/P2 all zero.
