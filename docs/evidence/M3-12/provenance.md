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

The source directory contained exactly the nine files fixed by the lock. The deterministic creator added only `m3-12-manifest.json`; it did not transform any source member. A fresh download from the published prerelease was `2184246` bytes and had the same SHA-256 as the local archive (`byte_equal=true`).

The source and archive scan found no private key marker, encrypted-private-key marker, profile password environment name, seed filename, keystore filename or absolute local user path. The ignored source package remains unchanged. No APK, signer, seed, Runtime, Host, fixture, benchmark or workflow was regenerated or modified, and no Android environment ran.

GitHub reports that the release is not platform-immutable. Acceptance therefore never trusts tag/name alone: it binds numeric release and asset IDs, the API path, server digest, archive size/SHA-256 and every member size/SHA-256. Deletion, replacement, access failure or any drift blocks M3-10 without fallback.
