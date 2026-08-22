# M3-12 review-1 remediation validation

- Task: `M3-12`
- Issue: `#75`
- Branch: `docs/m3-12-profile-package-retention`
- Base: `9d3fc3a4ae17d14f84d223b9dbb5f92016814f1a`
- Rejected implementation/evidence: `69b2fa75b0362992a3544188ae843106c66bb347` / `3c98db78f69f5e980ed1043681ef8d617e3c2b45`
- Review-1 result: `FAIL — P0=0/P1=2/P2=3`
- Remediation freeze: `f651731b42b4a4f6158f3f51f7e299d49f639f17`
- Timestamp: `2026-08-22T10:15:28.1002030+08:00`
- Environment: Windows `10.0.19045.0`; Node.js `v24.12.0`; Git `2.52.0.windows.1`; GitHub CLI `2.96.0`

## Finding closure

1. The lock now fixes M3-10 implementation `86ec37475fd7a96b4baf764530baefc3fe3d4cde`, evidence `7a384b321e9afa8df5f683ad1a2b78ba2cb31bd0`, all-zero review/readiness record `ac2d969392556fd9b338399e6cc2e9c22c90daed`, its record SHA-256, the canonical profile-lock SHA-256 and the accepted four-APK report SHA-256. The verifier reads the exact Git blobs, checks ancestry, hashes and retained-member mapping.
2. One shared scanner now inspects outer bytes plus every safe unique APK ZIP entry name and decompressed byte. It rejects encrypted/unsupported policy, traversal, duplicates, symlinks, malformed offsets, CRC/size drift, expansion excess, private-key/keystore/credential/token and Windows/Unix absolute-user-path patterns.
3. Self-test now executes `24` lock/metadata mutations, `12` actual archive mutations and `10` sensitive/nested-APK mutations through the production predicates.
4. The creator now refuses any archive whose exact final size or SHA-256 differs from the published lock. Two independent outputs and the numeric remote download are byte-identical.
5. Creator/fetcher walk every existing output-parent component and reject links/junctions; verifier requires the input file realpath to remain below the real `build/m3-12` root. All three rejected one real junction escape and the temporary junction/target were removed.

## Creator and remote commands

The following positive commands exited `0` and each produced `2184246` bytes with SHA-256 `21816d2a843bb5c59902224c7bf786d546d52b4a5b2d1168ca0c449a2ca27964`:

```text
node tools/validation/create-m3-12-profile-package.mjs --source build/m3-10/review3-profile/package --output build/m3-12/remediation-a/m3-10-profile-package-v1.zip
node tools/validation/create-m3-12-profile-package.mjs --source build/m3-10/review3-profile/package --output build/m3-12/remediation-b/m3-10-profile-package-v1.zip
$env:GITHUB_TOKEN = (gh auth token); node tools/validation/fetch-m3-12-profile-package.mjs --output build/m3-12/remediation-fetch/m3-10-profile-package-v1.zip
```

The following creator commands exited `1` as required and created no accepted output:

```text
node tools/validation/create-m3-12-profile-package.mjs --source build/m3-10/review3-profile/package --output build/m3-12/remediation-a/m3-10-profile-package-v1.zip
node tools/validation/create-m3-12-profile-package.mjs --source build/m3-12/negative-extra --output build/m3-12/remediation-neg-extra/out.zip
node tools/validation/create-m3-12-profile-package.mjs --source build/m3-12/negative-hash --output build/m3-12/remediation-neg-hash/out.zip
```

The failures were respectively `output already exists`, `source entry set differs`, and `reviewed bytes differ: preparation-report.json`.

## Structural, boundary and governance commands

```text
node --check tools/validation/m3-12-security-scan.mjs
node --check tools/validation/create-m3-12-profile-package.mjs
node --check tools/validation/fetch-m3-12-profile-package.mjs
node --check tools/governance/verify-m3-12-profile-retention.mjs
node --check tools/governance/validate-project-package.mjs
node tools/governance/verify-m3-12-profile-retention.mjs --archive build/m3-12/remediation-fetch/m3-10-profile-package-v1.zip --self-test --base-ref 9d3fc3a4ae17d14f84d223b9dbb5f92016814f1a
node tools/governance/validate-project-package.mjs
node .agents/skills/coordinate-project-handoff/scripts/validate-handoff.mjs HandOff.md --strict --allow-pending-clean
git diff --cached --check
```

All commands above exited `0`. The verifier reported `entryCount=10`, `lockMutations=24`, `archiveMutations=12`, and `sensitiveMutations=10`; governance reported `37` task cards, `11` core documents and `17` ADRs.

With a verified junction at `build/m3-12/remediation-link` targeting a sibling build directory, the creator and fetcher output commands and verifier input command each exited `1` with an explicit link/junction or realpath escape error. The junction and its target were resolved to the expected workspace paths before cleanup; cleanup passed.

## Frozen hashes

| File | Bytes | SHA-256 |
| --- | ---: | --- |
| `tools/validation/m3-12-security-scan.mjs` | 8264 | `062d3d09bda35f418a277efebe408630ed2e32eb241739f2fb1c5298ba87e6ff` |
| `tools/validation/create-m3-12-profile-package.mjs` | 7695 | `e5e98831ebcf15f1ad5d79ec89656ea56c9317584f48056042a094f7ac4e633b` |
| `tools/validation/fetch-m3-12-profile-package.mjs` | 3960 | `1e2ca55eb03753417089882d339a5a416d9c2bf5a0b0e8d0c0f68b761aad97b0` |
| `tools/governance/verify-m3-12-profile-retention.mjs` | 31120 | `92665f7bf0a9d3b52ace7ffd5d4c501959f782da0e8926b6770c23673c1e68cb` |
| `docs/evidence/M3-12/profile-package-retention-lock.json` | 3520 | `2e3d533362962b5cd537da2e6bfeb2d5f1de8f127ab13c616f257327f5783146` |
| `docs/evidence/M3-12/release-metadata.json` | 664 | `55d2d7388c46507271f77b88a92c066e74758dc705478c0fa2e237353dffb813` |

## Scope

No retained member changed: both creator outputs and the post-remediation numeric download have the same published archive SHA-256. No APK/DEX/profile/signer/seed was regenerated, no secret was written or printed, no product/fixture/benchmark/distribution or diagnostic workflow changed, and no Gradle, Android, API 36, KVM, emulator, ARM, benchmark or M3-05 action ran. A second independent read-only review remains mandatory; the unique API 36 workflow is still absent and forbidden.
