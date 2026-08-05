# M1-04 local security scan

Timestamp: `2026-08-06T06:03:17+08:00`

Scope:

- `host/container/src/main/kotlin/`
- `host/container/src/test/kotlin/`
- `docs/specs/AHDC_V2.md`
- `.github/workflows/build.yml`
- `tools/validation/verify-ahdc-v2-vector.mjs`

Results:

- no private-key PEM marker, keystore path, password assignment, passphrase
  assignment, fixed content key, customer path, or credential was found;
- no UTF-8 replacement character was found;
- no claim of absolute, unbreakable, or impossible-to-extract protection was found;
- product code accepts no private key, keystore, alias, signing password, user key,
  passphrase, network KMS, token, or signing tool;
- input APK access is read-only and output is a new unsigned container artifact;
- no `AHDC` v1 or `ConfigV1` parser, fallback, or compatibility branch exists;
- no full DEX, compressed DEX, payload-sized array, or materialized chunk-object
  table exists in product code;
- exception messages expose only stable error codes and field labels, not paths,
  package contents, DEX bytes, key material, shares, or nonces.
- the checked-in Node consumer contains no key literal; it consumes only the
  generated deterministic synthetic vector under ignored `build/` output.

Review of the cryptographic sequence confirmed standard JCA AES-256-GCM and
HMAC-SHA-256, RFC 5869 extract/expand, independent manifest/record domains,
canonical 96-bit chunk nonces, complete metadata MAC coverage, complete ConfigV2
digest binding, and authenticate-before-inflate ordering.

The first and second independent reviews are archived as `security-review-1.md`
and `security-review-2.md`, both `FAIL`. The second review's OOM ownership finding
drove allocation-free first-error tracking and transactional sensitive-copy
construction. A new independent review must target the next frozen commit before
publication.
