# M2-09 remote validation

## Frozen acceptance

- Validation mode: `pre-cli`
- Production implementation: `9ba6ec28c7d1450c3ca51175f78e3aa2d292331f`
- Test remediation: `dd78179f41c97aab7e3f38c0f571c4e6198f8939`
- Exact validated PR head: `186dfd79ee4f32c749c4ccfdebf5bc82a3476637`
- Pull request: [#60](https://github.com/xiaokh31/androidAppHardening/pull/60)
- Tracking issue: [#59](https://github.com/xiaokh31/androidAppHardening/issues/59)
- Merge commit: `77b3aee7d88eaf4446ae780f20fe6988796609af`
- Independent review: PASS, `P0=0`, `P1=0`, `P2=0`
- Verified at: `2026-08-15T11:52:13+08:00`

## Exact-head gates

| Workflow | Run / job | Result |
|---|---|---|
| Build | `31862011459` / Ubuntu `94956931414` | PASS |
| Build | `31862011459` / Windows `94956931443` | PASS |
| Governance | `31862011393` / Ubuntu `94956931041` | PASS |
| Governance | `31862011393` / Windows `94956930967` | PASS |
| M0-05 Linux KVM | `31862011460` / API 29 `94956931286` | PASS |
| M0-05 Linux KVM | `31862011460` / API 36 `94956931273` | PASS |

All three workflows resolve to exact PR head `186dfd79ee4f32c749c4ccfdebf5bc82a3476637`. Cross-platform equivalence `31862011379` and M3-02 Fuzz `31862011415` were cancelled as unrelated to the bounded Runtime lifecycle fix. No local emulator or physical device ran.

## Device acceptance

API 29 and API 36 x86_64 each passed extracted, direct and no-original-Factory M201 Release/R8 paths. Every instrumentation result reports `configuration_relaunch=true`, `platform_callbacks=6`, main and secondary install counts of `1`, one original Factory callback when present and zero when absent, custom Application, early Provider, multidex, JNI, null metadata, zero plaintext DEX files and successful cleanup. The Android regression executes a real `Activity.recreate()` and requires the relaunch Activity count to reach exactly two without reopening the authenticated process session.

The independent connected/JVM matrix additionally fixes Guard open and original Factory ClassLoader hook counts at `1`, keeps the READY session close count at `0`, and rejects loader mismatch, `NEW`, `INSTALLING` and cached `FAILED` without retry. The production implementation, public API and Release/R8 rules did not change after the all-zero review.

## Artifact integrity

| Artifact | ID | Bytes | SHA-256 |
|---|---:|---:|---|
| API 29 x86_64 KVM evidence | `9241075688` | `3833475` | `b346b53da81e19e1d79935bbb3e6f7a0a3794d5958033f2b11ab05576466a45f` |
| API 36 x86_64 KVM evidence | `9241080524` | `3176178` | `8b4618d3780091896eb6ed2410f41b7e292ec1e4b86c2196fe5863611bd95efd` |

Representative M201 hashes:

- API 29 report / commands: `67528a7e1be297960ebeb0c1caacbdac9f6e00d4aa81a2e0b5ed5a96d91d1be3` / `e6ec843526b16a884304513f2b403475d25c977de741df100d6a796d9c6dd113`
- API 36 report / commands: `dda81aff11c922f84b7163ca85db0837ea82ec386e71179cb1eee488cf77da97` / `778c6272e4fbf3b70a882b86bd97c102a1385e19d0759970fd6e80a241326f22`
- Extracted/direct instrumentation on both APIs: `4d785de160348d687956c168f3e2103f6d27b2e6f7d61c7cbf11b0a498100e95`
- No-original-Factory instrumentation on both APIs: `ab2afc455d1a8fe2aaab2d6b64d6d5ca46d663838d31f6c27ff75fd0458f5680`

Downloaded copies remain under ignored `build/m2-09/remote/186dfd7/` on the project drive. No large program was downloaded to the system drive.

## Post-merge coordination

PR #60 was converted to ready and merged with expected-head protection. The first merge-commit Governance run `31862945190` failed only because the merged HandOff still declared the source branch instead of `main`; project-package validation passed and no production or test check failed. The immediate main coordination commit synchronizes HandOff, README and this evidence, after which normal Build/Governance are the only post-merge gates before M3-04 resumes.
