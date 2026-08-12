# M2-05 independent read-only security review

## Scope

- Base: `029f7af5a183b18704e088bcde89ab1e80f6a278`
- Initial review head: `4f2ecc33efdb06292627aefc34b8a9b3552170a9`
- Final reviewed implementation: `9e37ed3b254066cf21e905920a9e409c35bf6609`
- Reviewer mode: independent read-only static review; no repository writes, downloads,
  Gradle execution, emulator or device execution by the reviewer.

## Final result

- P0: 0
- P1: 0
- P2: 0
- Result: PASS

The initial review found two P1 issues and one evidence P2. The implementation
remediation added a monotonic Native deadline with injectable reader/clock seams,
open/read/unreadable/partial-read/forced-timeout coverage, and a Java timeout path
that returns all five signals as `UNAVAILABLE/0`. It also added real read-only
mapping fixtures that exercise `/proc/self/maps -> JNI -> EnvironmentRiskEngine`,
real JDWP attachment, API 29 x86 execution, and Release/R8 facade/JNI coverage.

The final incremental review required the Release/R8 fixture to prove JNI rather
than merely accept an `UNAVAILABLE` report. The final implementation extracts the
real target `libah_runtime.so`, maps two test aliases, and requires
`INSTRUMENTATION_MAPPING == DETECTED` with score `80`. The Windows Host build also
keeps Linux-only clock constants and functions behind `__linux__`. The reviewer
confirmed these changes close all findings without a new P0, P1 or P2.

CI and authorized physical-device results remain implementation evidence and are
recorded separately; they were not inferred by the read-only reviewer.

## Final bounded incremental review

- Exact head: `a59345862e7a7ca164fbbc69ed6447efc9f5ddba`
- Scope: only the final `/proc/self/maps` bound correction after the full review
- Result: PASS; P0 `0`, P1 `0`, P2 `0`; merge authorized by the reviewer

The bounded follow-up confirmed that raising the mapping read limit from 512 KiB to 2 MiB retains the 50 ms deadline, no-throw allocation, buffer clearing and over-limit `UNAVAILABLE` semantics. The Host regression proves aliases after 600 KiB are detected and `2 MiB + 1` is rejected. The reviewer also verified exact-head Build `31616216280`, Governance `31616216704` and API 29/36 KVM `31616216412` were successful, without writing files or rerunning tests.
