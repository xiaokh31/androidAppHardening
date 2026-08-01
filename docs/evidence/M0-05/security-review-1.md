# M0-05 independent security review 1

## Review identity

- Reviewer: independent read-only `m0_05_security_review`
- Reviewed commit: `859fe25d15cc7e8670ac621d25d2e0101cf93c9a`
- Review result: `FAIL`
- Open findings at review time: P0 `0`, P1 `3`, P2 `3`
- Repository mutation by reviewer: none

This record preserves the first review result. It must not be reinterpreted as acceptance. A second independent read-only review is required against a newly frozen commit and rerun device evidence after every finding below is closed.

## Findings and required closure evidence

| Priority | Finding | Required closure |
|---|---|---|
| P1 | Five original `AppComponentFactory` component delegates did not normalize null, `RuntimeException`, or `LinkageError` outcomes to stable `AAH-P003` failures. | Cover all five component delegates and preserve an originating cause where one exists. |
| P1 | The external 17-case startup mutation matrix ran only for extracted while reports implied both variants were covered. | Run and report independent extracted and direct matrices on arm64 API 29 and x86_64 API 29/36. |
| P1 | Linux/KVM installed `libpulse0` without an exact approved version. | Pin the package and version in the provenance manifest and verify the installed version. |
| P2 | No forged duplicate ABI/native-entry negative was present. | Add an authenticated duplicate native-directory/entry rejection case. |
| P2 | When `am start -W` transiently reported Launcher, Launcher `TotalTime` values entered target-app statistics. | Use target `TotalTime` only when the reported Activity is the target; otherwise use a documented target-resume elapsed upper bound. |
| P2 | Formal evidence omitted JUnit XML, per-ABI SO hashes, R8 mapping/usage hashes, and verifier peak memory. | Generate, archive, hash, and summarize all four evidence classes. |

## Gate

No PR may be created from this failed review. M0-05 remains in progress, and M1/M2 remain blocked until the repaired frozen SHA passes all three device environments and a second independent review reports zero P0/P1/P2 findings.
