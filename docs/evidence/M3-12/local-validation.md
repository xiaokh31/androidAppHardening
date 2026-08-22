# M3-12 local validation

- Task: `M3-12`
- Issue: `#75`
- Branch: `docs/m3-12-profile-package-retention`
- Base: `9d3fc3a4ae17d14f84d223b9dbb5f92016814f1a`
- Implementation freeze: `69b2fa75b0362992a3544188ae843106c66bb347`
- Timestamp: `2026-08-22T09:59:35.7215774+08:00`
- Environment: Windows `10.0.19045.0`; Node.js `v24.12.0`; Git `2.52.0.windows.1`; GitHub CLI `2.96.0`

## Fixed remote identity

- Repository: `xiaokh31/androidAppHardening`
- Release ID: `374769776`
- Tag: `m3-10-profile-package-v1`
- Target commit: `9d3fc3a4ae17d14f84d223b9dbb5f92016814f1a`
- Release flags: `draft=false`, `prerelease=true`, `immutable=false`
- Asset ID: `524507375`
- Asset name: `m3-10-profile-package-v1.zip`
- Asset state/type: `uploaded`, `application/zip`
- Asset size: `2184246` bytes
- Server digest and downloaded SHA-256: `sha256:21816d2a843bb5c59902224c7bf786d546d52b4a5b2d1168ca0c449a2ca27964`
- Exact asset API path: `/repos/xiaokh31/androidAppHardening/releases/assets/524507375`

The authenticated numeric-asset download was written only under ignored `build/m3-12/`. It is byte-equal to the deterministic local archive. GitHub reports `immutable=false`; the acceptance boundary therefore requires the numeric release/asset IDs, exact server digest, archive size/hash and all member hashes. Any deletion, replacement, access failure or drift blocks M3-10 without fallback.

## Archive members

| Member | Bytes | SHA-256 |
| --- | ---: | --- |
| `derivation-manifest.json` | 1161 | `878d092a3cae6f4aa73cb722ea0bb9aa2f1eb32917a19b8c83220502dbdf4de8` |
| `m3-12-manifest.json` | 1620 | `c5f4b45404a6bec5d7915fb6df595d19690022085384592a080c7df454083fd5` |
| `observer.dex` | 4748 | `537b1ba424961d3897d574c10ec155e7b01cfffa313d71a0ade1d0c06e26dc88` |
| `preparation-report.json` | 782 | `bf174be280410dc98ac532a7aab04e3c1a5890a0f5693a5affe55362bec3a698` |
| `profile-baseline-aligned.apk` | 25819 | `8a39bf6e830e18d997ababe767f290bb3ee5489d31cafbb614aa5a625322b7d8` |
| `profile-baseline-unsigned.apk` | 23097 | `423461bc1b900230021d2c950f5d5ce1b10f37911a8d63ea4f84a0b46e93fbe4` |
| `profile-baseline.apk` | 33971 | `a062e0994482b1db417ff710c554364ec80e9f8d5fa84b5745ff5753308b764b` |
| `profile-protected-aligned.apk` | 1279696 | `ffcf606605ed7a13cd9f61aaa11076ff58bbe620308683ac93baa729d0c28c09` |
| `profile-protected-unsigned.apk` | 1252546 | `167c44aa4a15071b762fcec18fd4bfcc55087676577750dc0177f8734dad7b25` |
| `profile-protected.apk` | 1287848 | `1ce941404d8e6105764d041c449a60016312bc9c9671a8f8eb97c4e8b6820a10` |

## Commands and results

All commands ran from the repository root and exited `0`.

```text
node --check tools/validation/create-m3-12-profile-package.mjs
node --check tools/validation/fetch-m3-12-profile-package.mjs
node --check tools/governance/verify-m3-12-profile-retention.mjs
node --check tools/governance/validate-project-package.mjs
node tools/governance/verify-m3-12-profile-retention.mjs --archive build/m3-12/numeric-download-v2/m3-10-profile-package-v1.zip --self-test --base-ref 9d3fc3a4ae17d14f84d223b9dbb5f92016814f1a
node tools/governance/validate-project-package.mjs
node .agents/skills/coordinate-project-handoff/scripts/validate-handoff.mjs HandOff.md --strict
git diff --check 9d3fc3a4ae17d14f84d223b9dbb5f92016814f1a..HEAD
gh api repos/xiaokh31/androidAppHardening/releases/374769776
```

The strict retention verifier reported `entryCount=10`, `lockMutations=17` and `archiveMutations=3`. Project governance reported `37` task cards, `11` core documents and `17` ADRs. The official release API returned exactly one asset whose ID, name, size and digest match the lock.

## Tracked evidence hashes

| File | Bytes | SHA-256 |
| --- | ---: | --- |
| `tools/validation/create-m3-12-profile-package.mjs` | 7545 | `9cb377c91e32afdeba4cfdb3d49bd9aadb88a3838331ea3c78d8768f5a3d8acf` |
| `tools/validation/fetch-m3-12-profile-package.mjs` | 4247 | `04343a1fd76803bc81fbbc3186d2241dff08d7ba5bc213fbd3383063f6635bae` |
| `tools/governance/verify-m3-12-profile-retention.mjs` | 19547 | `abf2b04acf2afaa0f0bb0b1d26f898affed87935b2ea273c140552ce4d9cd13c` |
| `docs/evidence/M3-12/profile-package-retention-lock.json` | 2749 | `6747ae2deb4cffc8750ec06fe972df3f568f59f01780ca3a5bee5df190f9ed36` |
| `docs/evidence/M3-12/release-metadata.json` | 664 | `55d2d7388c46507271f77b88a92c066e74758dc705478c0fa2e237353dffb813` |

## Scope statement

No profile/APK/DEX was regenerated or modified, no signer/private key/keystore/password/seed was retained, no production Runtime/Host/fixture/benchmark/distribution code changed, and no workflow, Gradle build, Android device, emulator, KVM, benchmark, ARM run, API 36 diagnostic or M3-05 action occurred. ZIP/APK/DEX bytes remain ignored and untracked. Independent read-only review is still mandatory before publication of the branch and before any unique API 36 workflow can be created.
