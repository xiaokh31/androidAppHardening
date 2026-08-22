# M3-12 profile package retention provenance

- Repository: `xiaokh31/androidAppHardening`
- Issue: `#75`
- Branch: `docs/m3-12-profile-package-retention`
- Source base: `9d3fc3a4ae17d14f84d223b9dbb5f92016814f1a`
- Release ID: `374769776`
- Tag: `m3-10-profile-package-v1`
- Asset ID: `524507375`
- Asset: `m3-10-profile-package-v1.zip`
- Size: `2184246` bytes
- Archive SHA-256: `21816d2a843bb5c59902224c7bf786d546d52b4a5b2d1168ca0c449a2ca27964`
- GitHub digest: `sha256:21816d2a843bb5c59902224c7bf786d546d52b4a5b2d1168ca0c449a2ca27964`
- Release flags: `draft=false`, `prerelease=true`, `immutable=false`
- Published at: `2026-08-22T01:54:53Z`
- M3-10 implementation/evidence/review chain: `86ec37475fd7a96b4baf764530baefc3fe3d4cde` -> `7a384b321e9afa8df5f683ad1a2b78ba2cb31bd0` -> `ac2d969392556fd9b338399e6cc2e9c22c90daed`
- All-zero review record SHA-256: `43b9ce026161c60b990c6c56d0932c4a0b931fc0edc8612ebbffde848fc68c10`
- Canonical profile-lock SHA-256: `a9e130bb4e66e14443d83ea01ef0d60a95adddefa9dc92a9bdc980e5728dab4b`
- Accepted four-APK report SHA-256: `1610f895cb1a3003387a2c7f2e2e1474d6fbbfc523da8fc11c88d6cd283c5b93`

The source directory contained exactly the nine files fixed by the lock. The deterministic creator added only `m3-12-manifest.json`; it did not transform any source member. A fresh download from the published prerelease was `2184246` bytes and had the same SHA-256 as the local archive (`byte_equal=true`).

The source and archive scan found no private key marker, encrypted-private-key marker, profile password environment name, seed filename, keystore filename, credential/token or absolute Windows/Unix user path. It parsed all six APKs and scanned every ZIP entry name and decompressed entry byte under strict bounds, CRC and expansion limits. The ignored source package remains unchanged. No APK, signer, seed, Runtime, Host, fixture, benchmark or workflow was regenerated or modified, and no Android environment ran.

GitHub reports that the release is not platform-immutable. Acceptance therefore never trusts tag/name alone: it binds numeric release and asset IDs, the API path, server digest, archive size/SHA-256 and every member size/SHA-256. Deletion, replacement, access failure or any drift blocks M3-10 without fallback.
