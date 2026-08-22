# ADR 0017: Diagnostic profile package retention boundary

## Status

Accepted

## Context

M3-10 independently proved that one exact profile package preserves the ADR 0016 security and measurement semantics. The signed profile APKs are SHA-256 `a062e099...764b` and `1ce94140...0a10`. Their ephemeral signer, password and deterministic container seed were then destroyed as required. The reviewed files remained only under ignored local `build/m3-10/` storage, so a GitHub-hosted runner could not obtain them. Regeneration would create a different signer, seed and package identity and cannot recover the reviewed package.

## Decision

M3-12 retains the existing bytes without regenerating any APK. A deterministic ZIP named `m3-10-profile-package-v1.zip` contains exactly the six signed/aligned/unsigned profile APKs, `observer.dex`, `derivation-manifest.json`, `preparation-report.json` and an M3-12 member manifest. The package is repository-generated synthetic test material; it is not a product release and contains no private key, keystore, password, seed, token, customer APK/path or plaintext customer DEX.

The provenance root is machine-bound to M3-10 implementation `86ec37475fd7a96b4baf764530baefc3fe3d4cde`, evidence `7a384b321e9afa8df5f683ad1a2b78ba2cb31bd0`, and the all-zero review/readiness record at `ac2d969392556fd9b338399e6cc2e9c22c90daed`. The lock fixes and verifies the review record and canonical profile-lock Git bytes, their SHA-256 values, Git ancestry and the accepted four-APK report SHA-256; descriptive prose cannot substitute for that chain.

The ZIP is published as the sole asset of GitHub prerelease tag `m3-10-profile-package-v1`. The accepted source identity is the conjunction of repository, numeric release ID `374769776`, numeric asset ID `524507375`, asset name, exact length `2184246`, GitHub digest and independently computed archive SHA-256 `21816d2a843bb5c59902224c7bf786d546d52b4a5b2d1168ca0c449a2ca27964`. Every member name, length and SHA-256 is also fixed by `docs/evidence/M3-12/profile-package-retention-lock.json`.

GitHub reports `immutable=false`; this ADR does not claim that repository administrators cannot delete the release or asset. Consumers receive only `contents: read`, address the numeric asset ID, and accept bytes only after validating the complete lock. Replacement creates a different asset ID, byte drift changes the SHA-256, and deletion or loss returns a terminal unavailable result. Every case fails closed and does not authorize regeneration, fallback by tag/name, another release, cache recovery or a second diagnostic eligibility.

Before archive creation and again after remote download, every retained outer member is scanned. Each APK is also parsed as an untrusted ZIP and every safe unique entry name plus decompressed entry byte is scanned for private-key, keystore, credential/token and absolute user-path material. Unsupported or encrypted ZIP policy, traversal, duplicate names, symlinks, malformed offsets, CRC mismatch or expansion limits fail closed. Creator, fetcher and verifier resolve every existing path component and reject symlink/junction escape from `build/m3-12`.

The M3-10 diagnostic workflow may be added only after this contract, archive parser, remote provenance and actual downloaded asset pass independent review with `P0=0/P1=0/P2=0`. It must download asset ID `524507375` through the GitHub API, verify HTTP/API identity, archive size/SHA-256 and all ten ZIP members before emulator creation or APK installation. The unique API 36 diagnostic remains unconsumed until then.

## Consequences

- M3-10 gains a runner-accessible source for the exact reviewed profile bytes without restoring signing material.
- The retained ZIP and APKs remain outside Git; only their lock, provenance and verifier are tracked.
- Availability depends on the GitHub release asset. Deletion or access failure blocks M3-10 rather than weakening identity checks.
- This task adds no production interface, dependency, compatibility claim or signing capability.

## Rejected Alternatives

- Re-run profile preparation: creates a different signer/seed/package identity.
- Commit APKs to Git: violates the repository generated-artifact boundary.
- Use Actions cache or an expiring workflow artifact: neither is a durable, exact source contract.
- Fetch by mutable tag or filename alone: does not bind the numeric asset object.
- Store a keystore, password or seed to make regeneration possible: expands secret retention and violates M3-10.

## Security Impact

The asset exposes only synthetic diagnostic APK bytes already intended for installation in an isolated CI emulator. The public certificate is necessarily present in signed APKs, but no signing secret is retained. The verifier treats the ZIP as untrusted input, rejects malformed bounds, duplicate or unsafe names, unsupported compression, extra/missing members, trailing data, size/hash drift and sensitive-material markers.

## Compatibility Impact

None. No API, ABI, minSdk, product Runtime, Host CLI, container format or supported application claim changes. M3-12 does not run Android or resume M3-05.

## Verification

- Deterministic archive creation from the nine exact existing files.
- Remote re-download byte equality and SHA-256 verification.
- Strict ZIP/member parsing plus lock and archive mutation self-tests.
- Governance, strict HandOff, documentation consistency and zero-product-diff checks.
- Independent read-only review before M3-10 workflow creation.
