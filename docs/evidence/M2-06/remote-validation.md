# M2-06 remote validation

## Frozen acceptance

- Timestamp: 2026-08-13T15:40:10+08:00
- Production implementation: `ac374ad03bce87ac7068cf124f4721441f79f59f`
- Final validated branch head: `9cbc6b6681b8fe1c4bb45c4cd86eaba6fe0086e7`
- Pull request: [#48](https://github.com/xiaokh31/androidAppHardening/pull/48), merged with expected-head protection as `aa934080d37dd7590034829fbd436c21e69074a3`
- Tracking issue: [#17](https://github.com/xiaokh31/androidAppHardening/issues/17), closed by the merge
- Independent review: PASS, P0 `0`, P1 `0`, P2 `0`
- Validation mode: `pre-cli`

## Exact-head CI

| Workflow | Run | Result | Coverage |
|---|---:|---|---|
| Build | [31671159532](https://github.com/xiaokh31/androidAppHardening/actions/runs/31671159532) | PASS | Ubuntu 24.04 and Windows 2025; Native Host regression, Java policy matrix, lint, four ABI Release AAR and M2-06 report verifier |
| Governance | [31671159537](https://github.com/xiaokh31/androidAppHardening/actions/runs/31671159537) | PASS | Ubuntu 24.04 and Windows 2025 project package and PR governance |
| M0-05 Linux KVM | [31671159539](https://github.com/xiaokh31/androidAppHardening/actions/runs/31671159539) | PASS | API 29 and API 36 x86_64; extracted/direct Release/R8, memory controls, 20 cold starts and forced cleanup |

The three workflows resolve to exact head `6cd2bc221ecfd1ea203813facf94519baa885fca`. The final three commits after the reviewed production implementation only correct test expectations, constrain the approved Runtime caller surface, and make `smaps` validation accept API 36's adjacent-VMA merge while preserving per-mapping capability and byte-coverage checks. A bounded independent review of `ac374ad..6cd2bc2` found no P0, P1 or P2 issue.

The merger-ready test-orchestration successor `9cbc6b6681b8fe1c4bb45c4cd86eaba6fe0086e7` changes no production Runtime or Native code. It separates the real-JDWP probe from the already-passed ordinary mapping/timeout matrix and retains a three-second hard bound while leaving the production 50 ms fail-safe unchanged. Exact-head Build [31677309988](https://github.com/xiaokh31/androidAppHardening/actions/runs/31677309988), Governance [31677309943](https://github.com/xiaokh31/androidAppHardening/actions/runs/31677309943), and API 29/36 KVM [31677309937](https://github.com/xiaokh31/androidAppHardening/actions/runs/31677309937) all passed before expected-head merge.

## Device evidence

| Environment | Variant | Locked bytes | Dumpable | HIGH jitter | `dd` VMA evidence | Cold starts | Result |
|---|---|---:|---|---:|---|---:|---|
| API 29 x86_64 | extracted | 12,288 | false | 34 ms | 2 new VMAs; 12,288 / 12,288 expected bytes | 20 | PASS |
| API 29 x86_64 | direct | 12,288 | false | 33 ms | 2 new VMAs; 12,288 / 12,288 expected bytes | 20 | PASS |
| API 36 x86_64 | extracted | 12,288 | false | 42 ms | 1 merged VMA; 12,288 / 12,288 expected bytes | 20 | PASS |
| API 36 x86_64 | direct | 12,288 | false | 50 ms | 2 new VMAs; 12,288 / 12,288 expected bytes | 20 | PASS |

Every variant also passed non-root instrumentation, ten failure-injection windows, cross-DEX loading, JNI, authenticated metadata, read-only mapping checks, per-mapping `MADV_DONTDUMP`, zero plaintext DEX files and package/file cleanup. Peak PSS remained bounded: API 29 extracted/direct `38,550/30,578` KiB and API 36 extracted/direct `16,005/16,020` KiB. The API 36 extracted case proves that adjacent `dd` VMAs may merge without weakening total byte coverage.

## Artifact integrity

| Artifact | ID | Bytes | SHA-256 |
|---|---:|---:|---|
| Ubuntu M2-06 Build evidence | 9169802973 | 494492 | `0606626d9a50135aeab5f4a6d7f07c06bbbb128c0597803cea13009f40f488dd` |
| Windows M2-06 Build evidence | 9169827412 | 494485 | `0fc75dfef9f258941190cb8e0f4d7e5b8a67e31990a126944c7b66d73462e398` |
| API 29 x86_64 KVM evidence | 9170059737 | 3786551 | `2ee6eb6abe7ec2eca840b151a944c2ed312ec81677086287d6b5ac8699982fe6` |
| API 36 x86_64 KVM evidence | 9170024907 | 3137906 | `ce0e6ae2365ad5cd7ccdf1174963c899f36b922252477e0082bc0e1313939388` |

Merger-ready successor artifacts: Ubuntu M2-06 Build `9172120779`, 494492 bytes, `692f804827196c22e3ec485ecc89a32a85ccf562d6a46b214d651328112e2de5`; Windows M2-06 Build `9172160152`, 494485 bytes, `ea4c985dc5c8084f1a5747b78fc4dfd4452f82cdf961f8d081dbde9f8371b7f5`; API 29 KVM `9172447698`, 3779910 bytes, `aa4383daa8e5a3a74ab539a26810fe772bd71d69c92e5755331beee5679f619d`; API 36 KVM `9172530459`, 3160238 bytes, `88ad50b05881dfeb638b05fa3df6dd11e60a0bba26f6029dbb8d90ed54d134d7`.

Representative retained file hashes:

- Ubuntu Native AAR: `bcbb291543b95f41df8c41602fffd5256d6816c97bda6e425958f9103f4712b0`
- Ubuntu/Windows identical Policy AAR: `a7fdea442c5419ac1c7081dba59261d1fe297de6d51ec40fccb4603a6ea3cad5`
- Four Ubuntu `libah_runtime.so` files: `armeabi-v7a 11c3752f785e555a3a6b153722f929bc9da6a1b0362e817a706a7f92c11d1079`; `arm64-v8a d25a1994d711711588960d53f2cc2db41037582109c7c4575264b015c9269ffc`; `x86 f5a8fb16853d4339c3a3a5df8e60a429e757ac289d64c1e8e5a725e9b8f488a7`; `x86_64 216f68351eeac8496194e1a49c56c93890b33e07134f5f9024862893a326e570`
- API 29 report/commands: `075b248e43499355a1d2f2cedc5ec62a81d28b4e9ea92aafe671714c6cc18ed4` / `4949a28a27fe883c09f60e2dd33de7cba3ccca0ad8efe6bfc3883089ecef8244`
- API 36 report/commands: `eaf2159a2428a77f449496d23d967d3239a195ce15eb104fa449baba072a7514` / `6a8b95235fdb648ae39d171158a29e85481526941e59bb58ead9bea076f801d7`
- API 29 extracted/direct instrumentation: `b1e6cedfac8d6202c28e0c845f5b161d18a5cc2c1b65f692b65bc871d2683cfa` / `4fb0890f7fbcb6642bab2433e926ab9e5be5aeabd09e938507e63969d0f20143`
- API 36 extracted/direct instrumentation: `e51c71e2485d8840e2fca81cdf74077b51cc495df9c597a4d99d6e2379f96528` / `1db2f303e22d002392a9f919095031649b4a059b5a209a8bd0f7bcc245f502b5`

Downloaded copies remain confined to ignored `build/m2-06/remote/6cd2bc2/`. No large program was downloaded to the system drive, and no local emulator or physical device was started.

## Residual risk

M2-06 raises the cost of opportunistic memory dumping; it does not prevent an attacker with root, kernel, injection, debugger or full process-control capability from reading plaintext while ART legitimately uses it. `mlock`, `MADV_DONTDUMP` and `PR_SET_DUMPABLE` are capability-dependent and are reported rather than misrepresented as absolute protection. PR #48 is merged and Issue #17 is closed; no M3/M4 implementation was started as part of this task.
