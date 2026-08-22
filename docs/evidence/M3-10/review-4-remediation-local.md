# M3-10 fourth-review remediation evidence

- Timestamp: `2026-08-21T13:14:17+08:00`
- Reviewed evidence successor: `d6f87f4558ab25b25022889cb8052152435a8489`
- Remediated implementation freeze: `86ec37475fd7a96b4baf764530baefc3fe3d4cde`
- Dynamic scope: no workflow, API 36, KVM, emulator, ARM, benchmark or M3-05 execution

## Independent review 4 result

The fourth independent read-only review returned `FAIL — P0=0/P1=1/P2=0`. It confirmed the third-review findings closed but showed that the inner-probe predicate still accepted `target invoke -> non-method instruction -> h2..h5`, did not preserve complete method prototypes, and accepted an unrelated owner/type/value for the `h7` state publication. Both workflows remained absent and API 36 eligibility was not consumed.

## Remediation

- Method tokens now retain defining class, name, all parameter descriptors and return descriptor. Field tokens retain opcode, registers, defining class, name and type.
- `h1` requires the exact verifier invoke immediately after the probe. `h2` and `h4` require exact invoke, exact `MOVE_RESULT_OBJECT`, then probe. `h3` and `h5` require exact void/ignored-result invoke immediately before the probe. `h6` remains immediately before `RETURN_OBJECT`.
- `h7` requires the exact `HardeningBootstrap.State.READY` `SGET_OBJECT`, the exact `Coordinator.state:State` `IPUT_OBJECT`, the same value register, and the probe immediately after the write.
- Named self-tests reject gaps for h1..h7, wrong overloads, wrong h7 owner/type/value/register and the existing p/h entry/exit mutations through the same predicate used for actual APK verification.

## Evidence

| Item | Result |
|---|---|
| actual canonical four-APK Kotlin verifier | PASS; signer prefix `1e21b13e836d` |
| metadata/probe self-test | PASS; 9 metadata fields plus all p/h exact-boundary mutation families |
| complete Node verifier | PASS; 39 mutations plus 4 threshold cases |
| cleanup command-result self-test | PASS; 8 failure injections |
| profile-freeze governance | PASS; workflows absent and production observer absent |
| `git diff --check` | PASS |
| Kotlin actual-byte verifier SHA-256 | `5a594f13bf898c75b76eb29edcdf0f8b4bfc2eace2e45b10f16d22b3bffe71cd` |
| profile-freeze validator SHA-256 | `d3e2cb8377d71d534a620b1ec78d9fb87dc8b7336b60409942f827eb3e5b9a01` |
| fresh four-APK report SHA-256 | `1610f895cb1a3003387a2c7f2e2e1474d6fbbfc523da8fc11c88d6cd283c5b93` |

One combined Gradle invocation omitted the required actual-file environment for `m310VerifyProfiles`; the metadata task passed and the profile task stopped at Gradle property validation before execution. The accepted profile verification was then run separately with every fixed M3-11/profile input and passed. No dependency was downloaded and no failed command is acceptance evidence.

## Gate

A fifth independent read-only review must return exactly `P0=0/P1=0/P2=0` before a canonical workflow successor is permitted. API 36, ARM and M3-05 remain unexecuted.
