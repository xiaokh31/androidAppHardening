# M2-05 remote validation

## Frozen acceptance

- Timestamp: 2026-08-13T00:24:06+08:00
- Branch head: `a59345862e7a7ca164fbbc69ed6447efc9f5ddba`
- Merge commit: `815eb55f87bb37e50f00eb91293e930a950d60ac`
- Pull request: [#47](https://github.com/xiaokh31/androidAppHardening/pull/47)
- Tracking issue: [#16](https://github.com/xiaokh31/androidAppHardening/issues/16), closed by merge
- Independent review: PASS, P0 `0`, P1 `0`, P2 `0`

## Exact-head CI

| Workflow | Run | Result | Coverage |
|---|---:|---|---|
| Build | [31616216280](https://github.com/xiaokh31/androidAppHardening/actions/runs/31616216280) | PASS | Ubuntu 24.04 and Windows 2025; Host Native regression, Java policy matrix, lint, four ABI Release and report verifier |
| Governance | [31616216704](https://github.com/xiaokh31/androidAppHardening/actions/runs/31616216704) | PASS | Ubuntu 24.04 and Windows 2025 PR governance |
| M0-05 Linux KVM | [31616216412](https://github.com/xiaokh31/androidAppHardening/actions/runs/31616216412) | PASS | API 29 and API 36 x86_64; bounded device acceptance and cleanup |

## Device evidence

| Environment | Ordinary policy | Real JDWP | Release/R8 | Result |
|---|---|---|---|---|
| API 29 x86_64 | 1,000 evaluations, max 11,319 us; mapping score 80 | `pid_seen=true`, forward ready, JDWP detected, HIGH/DEGRADE | extracted/direct both `risk_r8_jni=true` | PASS |
| API 29 x86 | 11 cases, max 13,065 us; mapping score 80 | covered by API 29 x86_64 real transport | Native/policy connected path | PASS |
| API 36 x86_64 | 1,000 evaluations, max 23,036 us; mapping score 80 | `pid_seen=true`, forward ready, JDWP detected, HIGH/DEGRADE | extracted/direct both `risk_r8_jni=true` | PASS |
| API 29 ARM64 physical user build | direct Native parser/current-process probe passed and cleaned | not executed | policy APK rejected before tests by OEM `INSTALL_FAILED_USER_RESTRICTED` | Native PASS; policy instrumentation not executed |

The JDWP-attached diagnostic evaluation is intentionally not used for the ordinary 50 ms budget assertion because debugger suspension dominates elapsed time. The ordinary 1,000-evaluation reports remain below 50 ms. Environment scores never produce DENY and do not replace signer, container or metadata integrity failures.

## Artifact integrity

| Artifact | ID | Bytes | SHA-256 |
|---|---:|---:|---|
| `m0-05-api-29-x86_64-evidence` | 9149640594 | 469762 | `e0897de1fb3a805cc5996a683221f82840dd565ef5899fbf704ccd434c3ce309` |
| `m0-05-api-36-x86_64-evidence` | 9149691289 | 465165 | `6b10806a5f66431ad0fc45f65ed9c70f3f5a150169ef6d9dd11d20a3e82774ff` |

Representative retained file hashes:

- API 29 policy report: `39555a9ecbe30884917a298760b03e2d52afbe41ad56294b6f47df9367c5003c`
- API 29 Release/R8 report: `b7c097c1908919d35129b06c9cf57e5ff7ea29e26e1677cbbb8eabb418bc7f92`
- API 36 policy report: `ae6d5b08903f4b1113e391090f166cf6709edcd77ab97f242dbef80d82150f30`
- API 36 Release/R8 report: `649b80b7c5270a75d0c1f4df347430a1f96a3dc45c7f01e6daf4df7f8688ea6e`
- Common extracted/direct instrumentation summary: `3d3e6e16c5ebc79c51080db59067592f87a69025f6711c9e847b36cf45d8aade`

Downloaded copies are confined to ignored `build/m2-05/remote/a593458/`; no large tool or artifact was written to the system drive and no local emulator was started.
