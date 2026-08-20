# M2-07 Ubuntu runner image maintenance

## Scope

- Issue: [#73](https://github.com/xiaokh31/androidAppHardening/issues/73)
- Branch: `chore/m2-07-ubuntu-runner-20260816`
- Triggering failure: Build run [`32323762679`](https://github.com/xiaokh31/androidAppHardening/actions/runs/32323762679), Ubuntu job `96290837554`
- Failure boundary: the existing fail-closed allowlist rejected `ImageOS=ubuntu24`, `ImageVersion=20260816.277.1` before JDK setup, dependency installation, compiler invocation or project build.
- Excluded: Runtime/Host product code, APK fixtures, benchmark, KVM execution, emulator, physical device, M3-10 and M3-05.

## Official immutable manifest review

- Official repository: `actions/runner-images`
- Exact ref: `refs/tags/ubuntu24/20260816.277`
- Tag type: lightweight commit ref
- Ref commit: `3b5f596ffecb076aa5f3c3ded95b145f6daeb016`
- Commit timestamp: `2026-08-18T14:31:24Z`
- Commit tree: `121399991e96a26a7786143484d7f71c79a189b5`
- Manifest path: `images/ubuntu/Ubuntu2404-Readme.md`
- Manifest blob: `0023ec0741a8c708f9ba2e2bcfc1ee0d9fcb219c`
- Manifest size: `15740` bytes
- Manifest SHA-256: `50384bd5268bb03ae44ab93d621d9d9f20b30f8f0c8155ed49333a57c31a7d88`
- Manifest image version: `20260816.277.1`
- OS: Ubuntu `24.04.4 LTS`; kernel `6.17.0-1022-azure`
- GNU C/C++ inventory: `12.4.0`, `13.3.0`, `14.2.0`; project runtime assertion remains exact `13.3.0`
- Clang inventory includes `18.1.3`; CMake inventory includes `4.1.2`; Android NDK inventory includes `29.0.14206865`

The reviewed inventory preserves every compiler and Android tool version consumed by the existing M2-07/M3-02 contracts. The maintenance adds only the exact runtime/ref pair, reconciles the M2-07 machine lock with the already reviewed `20260810.271.1` mapping, and binds the same fourth Ubuntu image in Build, KVM, cross-platform equivalence and the M3-02 fuzz lock. It does not accept a range, `latest`, a fifth image or an alternative manifest.

## Required validation

- `node --check tools/validation/verify-m2-07-native-crypto.mjs`
- `node tools/validation/verify-m2-07-native-crypto.mjs --self-test`
- `node --check tools/validation/verify-m3-02-fuzz-toolchain.mjs`
- `node tools/validation/verify-m3-02-fuzz-toolchain.mjs`
- `node tools/governance/validate-project-package.mjs`
- `node .agents/skills/coordinate-project-handoff/scripts/validate-handoff.mjs HandOff.md --strict`
- `git diff --check`
- exact-head Ubuntu/Windows Build and Governance after independent read-only review

No KVM, emulator, device, benchmark, equivalence or fuzz campaign is required because their implementation and fixture surfaces are unchanged. KVM and equivalence workflow text receive only the exact image allowlist entry and are covered statically; the M3-02 workflow consumes the updated machine lock. KVM, equivalence and fuzz runs must be cancelled as out of scope when the branch is published.

## Local candidate validation

Validation ran on Windows 10 x64 at `2026-08-20T11:20:57+08:00`, from base `b51bba625a71489845e77847edf42d135a36afe6`. No dependency or tool download occurred; the existing ignored Mbed TLS archive and verified source tree were reused.

- Node syntax for both modified validators: PASS, exit `0`
- M2-07 archive/source/lock self-test: PASS, exit `0`; `7099934` bytes, archive SHA-256 `3359a349e23db3d5536fcee032ae7b2ecbfc08972fab643089b5cbf2a375c98c`, source tree SHA-256 `7c4ba6554fed6eb67c201054bc75b124fcdc0649e2f56cd762746e01a25d2140`, negative self-test PASS
- M3-02 runner/fuzz-toolchain lock and negative mutations: PASS, exit `0`
- Project package Governance: PASS, exit `0`; `36` task cards, `11` core docs, `16` ADRs
- strict HandOff validation: PASS, exit `0`
- `git diff --check`: PASS, exit `0`

Candidate hashes:

| File | SHA-256 |
|---|---|
| `tools/validation/m2-07-native-crypto.json` | `7e5dc0d9c166ed4b2c285af581479f42b3978c69e3874c64726823bf322084a6` |
| `tools/validation/verify-m2-07-native-crypto.mjs` | `0968b1aad6550ed552b04f622532efa998e3fc37227405653bb433b9b666ab5e` |
| `tools/validation/m3-02-fuzz-toolchain.json` | `42c2e3572783dba138459df6786f2ce1bb75e79d6d152d36b20a5fd0a4c14628` |
| `tools/validation/verify-m3-02-fuzz-toolchain.mjs` | `88acda559da830b3fbea09aac7262c4bd44f7b2e873769a237482eaabb04dd7a` |

## Independent review 1 and bounded remediation

Initial freeze `e8ed50a89c52fb8e66516ab6c4a4775c6fac1124` was rejected with `P0=0/P1=2/P2=0`:

1. Cross-platform equivalence accepted a version list without binding each runtime to its reviewed manifest ref.
2. The authoritative provenance text still described a two-image contract that contradicted the four-image locks.

The bounded remediation closes only those findings. Both equivalence Ubuntu gates now contain the same ordered four-entry runtime/ref mapping and emit the selected ref. The M2-07 validator parses Build, KVM and both equivalence mappings, requires their exact order, checks both equivalence ref outputs, and rejects mapping removal, addition, reordering and ref drift. The provenance text now contains one unambiguous four-image current contract and rejects a fifth image.

## Independent review 2

The complete second read-only review of frozen remediation `da37f47958522986fd25086368dc5598193e4906` passed with `P0=0/P1=0/P2=0` and no findings. It independently confirmed:

- both equivalence Ubuntu gates use the same ordered four-entry runtime/ref mapping and emit the selected ref;
- the M2-07 validator binds the exact Build/KVM/equivalence mappings and rejects removal, addition, reordering and ref drift;
- the authoritative provenance contract is unambiguous and consistent with ADR 0009 plus both machine locks;
- official commit/tree/blob/size/SHA evidence is internally consistent;
- base-to-HEAD remains limited to the 11 M2-07 maintenance files with no product, dependency, dynamic-download or sensitive-information expansion.

Both validator syntax checks, M2-07 `--self-test`, M3-02 validation, Governance, strict HandOff, base-to-HEAD diff check and a bounded PowerShell positive/unknown mapping probe passed. The reviewer did not modify files, use the network, download anything, or run Gradle, fuzz, KVM, emulator, device or benchmark.

## PR #74 initial exact-head validation

The user authorized publication of `chore/m2-07-ubuntu-runner-20260816` and creation of the unique Issue #73 draft PR. PR [#74](https://github.com/xiaokh31/androidAppHardening/pull/74) was created with exact head `81a1e5b6f9467d4ec1ae6b880c4be27024dde488`.

Required workflows:

| Workflow | Run | Ubuntu job | Windows job | Result |
|---|---:|---:|---:|---|
| Build | `32329949789` | `96308543192` | `96308543113` | PASS / PASS |
| Governance | `32329949870` | `96308543316` | `96308543265` | PASS / PASS |

Automatically triggered runs outside this maintenance scope were cancelled without retry: M0-05 Linux KVM `32329949743`, Cross-platform equivalence `32329949714`, and M3-02 Fuzz `32329949699`. Their cancellation is not acceptance evidence and does not replace either required workflow. No local Gradle, KVM, emulator, device, fuzz or benchmark ran.

## PR #74 final exact-head validation and merge

The authorized CI/HandOff successor `2f48d5eae74dc753ff8b3370852ee23f2989e402` preserved the reviewed implementation and passed the required exact-head workflows:

| Workflow | Run | Ubuntu job | Windows job | Result |
|---|---:|---:|---:|---|
| Build | `32330793427` | `96310880045` | `96310879906` | PASS / PASS |
| Governance | `32330793521` | `96310880206` | `96310880007` | PASS / PASS |

Automatically triggered KVM `32330793407`, equivalence `32330793430`, and fuzz `32330793461` were cancelled as out of scope. After those required checks passed, the user separately authorized ready/merge. PR #74 was converted to ready and merged with expected-head protection as `77e5148c5aa035fd450adffe9a09111d6b67f973`; Issue #73 closed.

## Post-merge main validation

Local `main` was fast-forwarded to merge commit `77e5148c5aa035fd450adffe9a09111d6b67f973`. Build run [`32333998709`](https://github.com/xiaokh31/androidAppHardening/actions/runs/32333998709) passed Ubuntu job `96319873758` and Windows job `96319873408`. The automatically triggered M3-02 Fuzz run `32333998695` was cancelled because it remains outside this maintenance scope.

The first main Governance run [`32333998706`](https://github.com/xiaokh31/androidAppHardening/actions/runs/32333998706) failed only because the merged HandOff still declared source branch `chore/m2-07-ubuntu-runner-20260816`; both jobs reported the same strict-validation mismatch before any product or toolchain finding. This post-merge documentation-only coordination commit changes that declaration to `main`, records the merge and public completion state, and does not alter any reviewed lock, workflow, validator, Runtime, Host, APK, benchmark or device input.
