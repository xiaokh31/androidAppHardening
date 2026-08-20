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
| `tools/validation/verify-m2-07-native-crypto.mjs` | `0503dadb671cd95fc3bfdefe5141ac0e64761191ef70c00258166f5d09949cb0` |
| `tools/validation/m3-02-fuzz-toolchain.json` | `42c2e3572783dba138459df6786f2ce1bb75e79d6d152d36b20a5fd0a4c14628` |
| `tools/validation/verify-m3-02-fuzz-toolchain.mjs` | `88acda559da830b3fbea09aac7262c4bd44f7b2e873769a237482eaabb04dd7a` |

The frozen candidate must remain unpublished until the independent read-only review reports P0=0/P1=0/P2=0. Exact-head Ubuntu/Windows Build/Governance remain mandatory before merge.
